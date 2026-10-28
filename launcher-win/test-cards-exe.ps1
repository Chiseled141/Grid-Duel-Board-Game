# Integration checks for Cards.exe. Stops only Deck Duel processes for this checkout.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root 'Cards.exe'
$jar = Join-Path $root 'target\deck-duel.jar'
$marker = Join-Path $root 'data\.cards-server.pid'
$port = 5555
$failures = 0
$listener = $null

Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if ($Condition) { Write-Host "PASS  $Message" }
    else { Write-Host "FAIL  $Message"; $script:failures++ }
}

function Test-Port {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(300)) { return $false }
        $client.EndConnect($ar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-Port {
    param([bool]$Open, [int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if ((Test-Port $port) -eq $Open) { return $true }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Get-DeckDuelProcesses {
    param([string]$Role)
    $jarPattern = [regex]::Escape($jar)
    $pattern = '-jar\s+(?:"' + $jarPattern + '"|' + $jarPattern + ')\s+' + $Role + '(?=\s|$)'
    Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
        Where-Object {
            $_.CommandLine -and
            ($null -eq $_.ExecutablePath -or (
                $_.ExecutablePath -notlike '*\javapath\*' -and
                $_.ExecutablePath -notlike '*\java8path\*')) -and
            $_.CommandLine -match $pattern
        }
}

function Wait-Clients {
    param([int]$Count, [int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if ((@(Get-DeckDuelProcesses 'client')).Count -ge $Count) { return $true }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Stop-DeckDuelClients {
    Get-DeckDuelProcesses 'client' | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Stop-All {
    Stop-DeckDuelClients
    Get-DeckDuelProcesses 'server' | ForEach-Object {
        & taskkill.exe /PID $_.ProcessId /T /F 2>&1 | Out-Null
    }
    Get-Process Cards -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -eq $exe } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Remove-Item $marker -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $root 'data\.cards-launchers') -Recurse -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

function Start-Cards {
    Start-Process -FilePath $exe -WorkingDirectory $root -PassThru
}

function Get-WindowText {
    param([System.Windows.Automation.AutomationElement]$Element)
    $parts = New-Object System.Collections.Generic.List[string]
    $stack = New-Object System.Collections.Stack
    $stack.Push($Element)
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    while ($stack.Count -gt 0) {
        $current = $stack.Pop()
        if ($current.Current.Name) { $parts.Add($current.Current.Name) }
        $child = $walker.GetFirstChild($current)
        while ($child) {
            $stack.Push($child)
            $child = $walker.GetNextSibling($child)
        }
    }
    return ($parts -join "`n")
}

function Get-DialogText {
    param([System.Diagnostics.Process]$CardsProcess, [int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $CardsProcess.Refresh()
        if (-not $CardsProcess.HasExited) {
            $condition = New-Object System.Windows.Automation.PropertyCondition(
                [System.Windows.Automation.AutomationElement]::ProcessIdProperty, $CardsProcess.Id)
            $window = [System.Windows.Automation.AutomationElement]::RootElement.FindFirst(
                [System.Windows.Automation.TreeScope]::Children, $condition)
            if ($null -ne $window) { return (Get-WindowText $window) }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Start-CardsWithEnvironment {
    param([hashtable]$Environment)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.WorkingDirectory = $root
    $psi.UseShellExecute = $false
    foreach ($key in $Environment.Keys) {
        if ($null -eq $Environment[$key]) { [void]$psi.EnvironmentVariables.Remove($key) }
        else { $psi.EnvironmentVariables[$key] = $Environment[$key] }
    }
    return [System.Diagnostics.Process]::Start($psi)
}

function New-GuardJar {
    param([string]$Directory, [string]$Connection, [string]$Update)
    if (Test-Path $Directory) { Remove-Item $Directory -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Join-Path $Directory 'target') | Out-Null
    Copy-Item $exe (Join-Path $Directory 'Cards.exe')

    $archive = [System.IO.Compression.ZipFile]::Open(
        (Join-Path $Directory 'target\deck-duel.jar'), 'Create')
    foreach ($pair in @(@('deckduel/connection.properties', $Connection),
                        @('deckduel/update.properties', $Update))) {
        $writer = New-Object System.IO.StreamWriter($archive.CreateEntry($pair[0]).Open())
        $writer.Write($pair[1])
        $writer.Close()
    }
    $archive.Dispose()
}

function Test-GuardRefused {
    param([string]$Name, [string]$Connection, [string]$Update)
    $directory = Join-Path $env:TEMP $Name
    try {
        New-GuardJar $directory $Connection $Update
        $process = Start-Process -FilePath (Join-Path $directory 'Cards.exe') `
            -WorkingDirectory $directory -PassThru
        $text = Get-DialogText $process 10
        Assert-True ($null -ne $text) "$Name dialog opens"
        Assert-True ($text -and $text.Contains('production server or a release channel')) `
            "$Name dialog names the restriction"
        Assert-True (-not (Test-Path (Join-Path $directory 'data'))) "$Name creates no local data"
        Assert-True (-not (Test-Port $port)) "$Name starts no server"
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } finally {
        Start-Sleep -Milliseconds 500
        Remove-Item $directory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

try {
    Write-Host 'Deck Duel Cards.exe integration checks'
    Stop-All

    if (-not (Test-Path $exe)) { Write-Host "FAIL  Cards.exe not found at $exe"; exit 1 }
    if (-not (Test-Path $jar)) { Write-Host 'FAIL  target\deck-duel.jar not found; run mvn package'; exit 1 }
    Assert-True (Wait-Port $false 10) 'port 5555 free before tests'

    Write-Host "`n1. Fresh launch"
    $launcher = Start-Cards
    Assert-True (Wait-Port $true 25) 'server listens on 5555'
    Assert-True (Test-Path $marker) 'launcher writes the server marker'
    Assert-True (Wait-Clients 1 25) 'client process starts'
    Start-Sleep -Seconds 3
    Assert-True (Test-Port $port) 'server stays up while the client runs'
    Stop-DeckDuelClients
    Assert-True (Wait-Port $false 15) 'server stops after the last client exits'
    Assert-True (-not (Test-Path $marker)) 'launcher removes the marker'

    Write-Host "`n2. Two clients"
    $first = Start-Cards
    Assert-True (Wait-Clients 1 25) 'first client starts'
    Start-Sleep -Seconds 2
    $second = Start-Cards
    Assert-True (Wait-Clients 2 25) 'second client starts'
    $clients = @(Get-DeckDuelProcesses 'client' | Sort-Object CreationDate)
    Stop-Process -Id $clients[0].ProcessId -Force
    Start-Sleep -Seconds 3
    Assert-True (Test-Port $port) 'server survives the first client exit'
    Assert-True ((@(Get-DeckDuelProcesses 'client')).Count -eq 1) 'second client still runs'
    Stop-DeckDuelClients
    Assert-True (Wait-Port $false 15) 'server stops after the last client exits'
    Assert-True (-not (Test-Path $marker)) 'launcher removes the marker'

    Write-Host "`n3. Manual server is left alone"
    Remove-Item $marker -ErrorAction SilentlyContinue
    $java = (Get-Command java).Source
    $manualLog = Join-Path $root 'data\test-manual.log'
    $manualErr = Join-Path $root 'data\test-manual.err.log'
    $manual = Start-Process -FilePath $java -ArgumentList @('-jar', $jar, 'server', '--port', '5555', '--db', 'data\test-manual.db') -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $manualLog -RedirectStandardError $manualErr
    Assert-True (Wait-Port $true 20) 'manual server listens'
    $launcher = Start-Cards
    Assert-True (Wait-Clients 1 25) 'client starts against the manual server'
    Stop-DeckDuelClients
    Start-Sleep -Seconds 3
    Assert-True (Test-Port $port) 'manual server still listens after the client exits'
    Assert-True (-not (Test-Path $marker)) 'no marker written for a manual server'
    $manual.Refresh()
    Assert-True (-not $manual.HasExited) 'manual server process is alive'
    & taskkill.exe /PID $manual.Id /T /F 2>&1 | Out-Null
    $manual.WaitForExit(10000) | Out-Null
    Assert-True (Wait-Port $false 10) 'manual server port closes'

    Write-Host "`n4. Unrelated port owner is left alone"
    Remove-Item $marker -ErrorAction SilentlyContinue
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
    $listener.ExclusiveAddressUse = $false
    $listener.Start()
    Assert-True (Test-Port $port) 'test listener owns 5555'
    $launcher = Start-Cards
    Assert-True (Wait-Clients 1 30) 'client starts while 5555 is owned by another program'
    Stop-DeckDuelClients
    Start-Sleep -Seconds 3
    Assert-True (Test-Port $port) 'unrelated listener still listens'
    Assert-True (-not (Test-Path $marker)) 'no marker written for an unrelated listener'
    $listener.Stop()
    $listener = $null
    Assert-True (Wait-Port $false 10) 'test listener releases 5555'

    Write-Host "`n5. Missing jar shows the build dialog"
    $backup = "$jar.bak"
    Move-Item $jar $backup
    try {
        $process = Start-Cards
        $text = Get-DialogText $process 10
        Assert-True ($null -ne $text) 'missing-jar dialog opens'
        Assert-True ($text -and $text.Contains('mvn package')) 'missing-jar dialog names mvn package'
        Assert-True (-not (Test-Port $port)) 'no server starts without the jar'
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } finally {
        Move-Item $backup $jar
    }

    Write-Host "`n6. Java 8 only shows the Java dialog"
    $java8 = 'C:\Program Files\Java\jre1.8.0_511\bin'
    if (Test-Path (Join-Path $java8 'java.exe')) {
        $process = Start-CardsWithEnvironment @{ 'JAVA_HOME' = $null; 'PATH' = $java8 }
        $text = Get-DialogText $process 10
        Assert-True ($null -ne $text) 'Java 17 dialog opens'
        Assert-True ($text -and $text.Contains('Java 17 or newer')) 'Java 17 dialog names the requirement'
        Assert-True (-not (Test-Port $port)) 'no server starts on Java 8'
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue

        Write-Host "`n6b. Java 8 before Java 17 on PATH is skipped"
        $process = Start-CardsWithEnvironment @{
            'JAVA_HOME' = $null; 'PATH' = "$java8;$env:PATH"
        }
        Assert-True (Wait-Port $true 25) 'server starts with Java 8 first on PATH'
        Assert-True (Wait-Clients 1 25) 'client starts with Java 8 first on PATH'
        Stop-DeckDuelClients
        Assert-True (Wait-Port $false 15) 'launcher stops the server after that client exits'
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host 'SKIP  Java 8 runtime not installed'
    }

    Write-Host "`n7. Production and release-channel jars are refused"
    Test-GuardRefused 'cards-guard-host' "host=example.com`nport=443`n" "metadataUrl=`npublicKey=`n"
    Test-GuardRefused 'cards-guard-release' "host=`nport=`n" `
        "metadataUrl=https://example.com/latest.properties`npublicKey=test`n"

    Write-Host "`n8. Missing Java shows the Java dialog"
    $emptyPath = Join-Path $env:TEMP 'cards-no-java'
    New-Item -ItemType Directory -Force -Path $emptyPath | Out-Null
    $process = Start-CardsWithEnvironment @{ 'JAVA_HOME' = $null; 'PATH' = $emptyPath }
    $text = Get-DialogText $process 10
    Assert-True ($null -ne $text) 'missing-Java dialog opens'
    Assert-True ($text -and $text.Contains('Java 17 or newer')) 'missing-Java dialog names the requirement'
    Assert-True (-not (Test-Port $port)) 'no server starts without Java'
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue

    Write-Host "`n9. A live sibling session keeps the server"
    $sessionDir = Join-Path $root 'data\.cards-launchers'
    New-Item -ItemType Directory -Force -Path $sessionDir | Out-Null
    $sessionFile = Join-Path $sessionDir "$PID.session"
    Set-Content -Path $sessionFile -Value (Get-Process -Id $PID).StartTime.Ticks
    $process = Start-Cards
    Assert-True (Wait-Port $true 25) 'server starts for the session check'
    Assert-True (Wait-Clients 1 25) 'client starts for the session check'
    Stop-DeckDuelClients
    Start-Sleep -Seconds 3
    Assert-True (Test-Port $port) 'server survives while a sibling session is live'
    Assert-True (Test-Path $marker) 'marker is kept for the live sibling session'

    Remove-Item $sessionFile -Force
    $process = Start-Cards
    Assert-True (Wait-Clients 1 25) 'client reuses the protected server'
    Stop-DeckDuelClients
    Assert-True (Wait-Port $false 15) 'server stops after the sibling session ends'
    Assert-True (-not (Test-Path $marker)) 'marker is removed after the sibling session ends'
    Assert-True (-not (Test-Path $sessionFile)) 'sibling session file stays removed'

    Write-Host "`n10. A server that exits shows the log dialog"
    $exitGuard = Join-Path $env:TEMP 'cards-server-exit'
    try {
        New-GuardJar $exitGuard "host=`nport=`n" "metadataUrl=`npublicKey=`n"
        $process = Start-Process -FilePath (Join-Path $exitGuard 'Cards.exe') `
            -WorkingDirectory $exitGuard -PassThru
        $text = Get-DialogText $process 15
        Assert-True ($null -ne $text) 'server-exit dialog opens'
        Assert-True ($text -and $text.Contains('local server exited')) `
            'server-exit dialog explains the failure'
        Assert-True (-not (Test-Path (Join-Path $exitGuard 'data\.cards-server.pid'))) `
            'server-exit removes the marker'
        Assert-True (-not (Test-Port $port)) 'no server remains after the failure'
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } finally {
        Start-Sleep -Milliseconds 500
        Remove-Item $exitGuard -Recurse -Force -ErrorAction SilentlyContinue
    }
}
finally {
    if ($null -ne $listener) { try { $listener.Stop() } catch { } }
    Stop-All
    Remove-Item (Join-Path $env:TEMP 'cards-no-java') -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $root 'data\test-manual.db') -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $root 'data\test-manual.log') -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $root 'data\test-manual.err.log') -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
    Write-Host "`n$failures check(s) failed."
    exit 1
}
Write-Host "`nAll checks passed."
exit 0
