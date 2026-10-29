<#
.SYNOPSIS
Builds the Deck Duel Windows installer with a bundled Java runtime. Run on Windows x64.

.DESCRIPTION
Builds and tests the client from a copy of this repository with the production game server and
the update channel packaged in, then writes to dist\client-<Version>\:
  deck-duel-<Version>-windows-x64.zip   the update package (upload it to the release channel)
  DeckDuel-Setup-<Version>.exe          the installer players download and run once
The installer carries a Java runtime linked from -JdkHome (launcher\runtime-modules.txt)
and installs per user, without administrator rights. Nothing is signed or uploaded here; signing
the release metadata and uploading are manual steps (docs/README.md#windows-installer).

Requires: a JDK 17+ for Windows x64 (Temurin 21 recommended), Maven on PATH, Inno Setup 6.3+.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File launcher\windows\build-installer.ps1 -Version 1.0.0 `
    -ServerHost <GAME_HOST> -ServerPort 5555 `
    -UpdateUrl https://<RELEASE_BUCKET>.s3.<region>.amazonaws.com/stable/latest.properties `
    -PublicKeyFile $HOME\deck-duel-keys\release.pub -JdkHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot'
#>
param(
    [Parameter(Mandatory = $true)] [string] $Version,
    [Parameter(Mandatory = $true)] [string] $ServerHost,
    [Parameter(Mandatory = $true)] [ValidateRange(1, 65535)] [int] $ServerPort,
    # Optional: without both, the game has no release channel and reports "Updates not set up".
    [string] $UpdateUrl = '',
    [string] $PublicKeyFile = '',
    [string] $JdkHome = $env:JAVA_HOME,
    [string] $InnoSetupCompiler = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Native programs report failure only through their exit code.
function Invoke-Checked([string] $Program, [string[]] $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}

function Find-InnoSetup([string] $Given) {
    if ($Given) { return $Given }
    $candidates = @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
                    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe")
    foreach ($candidate in $candidates) { if (Test-Path $candidate) { return $candidate } }
    throw 'Inno Setup 6 was not found. Install it (winget install JRSoftware.InnoSetup) or pass -InnoSetupCompiler.'
}

function Read-ZipEntry([string] $Zip, [string] $Entry) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Zip)
    try {
        $reader = New-Object System.IO.StreamReader($archive.GetEntry($Entry).Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}

if ($env:OS -ne 'Windows_NT' -or $env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Run this on Windows x64: the installer bundles that platform''s Java and JavaFX.' }
if ($Version -notmatch '^\d{1,6}\.\d{1,6}\.\d{1,6}(-[0-9A-Za-z.-]{1,40})?$') { throw "Not a release version: $Version" }
if ([bool]$UpdateUrl -ne [bool]$PublicKeyFile) { throw 'Pass both -UpdateUrl and -PublicKeyFile, or neither.' }
if ($UpdateUrl -and $UpdateUrl -notlike 'https://*') { throw 'The update address must use https://' }
if (-not $JdkHome -or -not (Test-Path "$JdkHome\bin\jlink.exe")) { throw 'Pass -JdkHome (or set JAVA_HOME) to a JDK 17+ for Windows x64 that has bin\jlink.exe.' }
$iscc = Find-InnoSetup $InnoSetupCompiler
$publicKey = if ($PublicKeyFile) { (Get-Content -Raw $PublicKeyFile) -replace '\s', '' } else { '' }
$repo = (Resolve-Path "$PSScriptRoot\..\..").Path
$out = Join-Path $repo "dist\client-$Version"
$build = Join-Path $env:TEMP ('deck-duel-build-' + [guid]::NewGuid())
$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"
$java = "$JdkHome\bin\java.exe"

try {
    # Build from a copy, so stale target\ and dist\ files in a synced folder never reach a release;
    # local databases and logs (data\, logs\) stay behind.
    robocopy $repo $build /E /XD target dist .git data logs /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Copying the repository failed (robocopy $LASTEXITCODE)" }
    Push-Location $build
    try {
        Invoke-Checked 'mvn' @('-q', 'clean', 'verify', "-Ddeckduel.version=$Version", "-Ddeckduel.server.host=$ServerHost",
            "-Ddeckduel.server.port=$ServerPort", "-Ddeckduel.update.url=$UpdateUrl", "-Ddeckduel.update.publicKey=$publicKey")
        $platform = "$(& $java -cp target\classes deckduel.Main release platform | Select-Object -Last 1)".Trim()
        if ($LASTEXITCODE -ne 0 -or $platform -ne 'windows-x64'){ throw "This JDK reports platform $platform, not windows-x64" }
        New-Item -ItemType Directory -Force $out | Out-Null
        Invoke-Checked $java @('-cp', 'target\classes', 'deckduel.Main', 'release', 'package', '--version', $Version,
            '--platform', $platform, '--app', 'target\deck-duel.jar', '--launcher', 'target\deck-duel-launcher.jar', '--out', $out)
        Write-Host (Read-ZipEntry 'target\deck-duel.jar' 'deckduel/connection.properties').Trim()
        Write-Host (Read-ZipEntry 'target\deck-duel.jar' 'deckduel/update.properties').Trim()

        # The runtime: the listed modules that this JDK has (jdk.crypto.ec is gone from newer JDKs).
        $available = & $java --list-modules | ForEach-Object { ($_ -split '@')[0] }
        $modules = Get-Content "$repo\launcher\runtime-modules.txt" | Where-Object { $_ -and -not $_.StartsWith('#') } |
            Where-Object { $available -contains $_ }
        $staging = Join-Path $build 'installer'
        Invoke-Checked "$JdkHome\bin\jlink.exe" @('--add-modules', ($modules -join ','), '--strip-debug', '--no-man-pages',
            '--no-header-files', '--output', "$staging\runtime")
        $package = "deck-duel-$Version-windows-x64.zip"
        Copy-Item "$out\$package", 'target\deck-duel-launcher.jar' $staging

        # The bundled runtime must set up the package and load the game's classes.
        $check = Join-Path $build 'install-check'
        Invoke-Checked "$staging\runtime\bin\java.exe" @('-jar', "$staging\deck-duel-launcher.jar", 'setup', '--root', $check, '--package', "$staging\$package")
        Invoke-Checked "$staging\runtime\bin\java.exe" @('-cp', "$check\versions\$Version\deck-duel.jar", 'deckduel.Main', 'release', 'platform')

        Invoke-Checked $iscc @('/Q', "/DAppVersion=$Version", "/DNumericVersion=$($Version -replace '-.*$', '')",
            "/DStaging=$staging", "/DPackageName=$package", "/O$out", "$repo\launcher\windows\DeckDuel.iss")
    } finally { Pop-Location }
} finally {
    Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
}
Get-FileHash -Algorithm SHA256 "$out\deck-duel-$Version-windows-x64.zip", "$out\DeckDuel-Setup-$Version.exe" |
    ForEach-Object { Write-Host "$($_.Hash.ToLower())  $(Split-Path -Leaf $_.Path)  $((Get-Item $_.Path).Length) bytes" }
Write-Host "Built $out\DeckDuel-Setup-$Version.exe"
