using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Management;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

/// <summary>Starts a local Deck Duel server and opens the game client from one double-click.</summary>
internal static class CardsLauncher
{
    private const int Port = 5555;
    private const string Host = "127.0.0.1";
    private const string JarRelative = @"target\deck-duel.jar";
    private const string DbRelative = @"data\deck-duel.db";
    private const string LogRelative = @"data\server.log";
    private const string MarkerRelative = @"data\.cards-server.pid";
    private const string SessionsRelative = @"data\.cards-launchers";
    private const string MutexName = @"Local\DeckDuelCardsLauncher";
    private static string sessionFile;

    [STAThread]
    private static int Main(string[] args)
    {
        string root = Path.GetDirectoryName(Application.ExecutablePath);
        try
        {
            string jar = Path.Combine(root, JarRelative);
            if (!File.Exists(jar))
            {
                ShowError("Deck Duel is not built.\n\nRun \"mvn package\" in:\n" + root
                    + "\n\nExpected file:\n" + jar);
                return 1;
            }

            EnsureLocalDevelopmentBuild(jar);
            Directory.CreateDirectory(Path.Combine(root, "data"));
            string java = FindJava();
            if (java == null)
            {
                ShowError("Java 17 or newer is required.\n\nInstall a JDK 17+ and set JAVA_HOME,"
                    + " or put java.exe on PATH.");
                return 1;
            }

            RegisterSession(root);
            try
            {
                EnsureServer(root, java, jar);
                LaunchClient(root, java, jar);
            }
            finally
            {
                StopLauncherServer(root);
            }
            return 0;
        }
        catch (Exception ex)
        {
            ShowError("Cards could not start Deck Duel.\n\n" + ex.Message);
            return 1;
        }
    }

    /// <summary>Registers this launcher so another cleanup cannot stop the server under it.</summary>
    private static void RegisterSession(string root)
    {
        string directory = Path.Combine(root, SessionsRelative);
        Directory.CreateDirectory(directory);

        using (Mutex mutex = new Mutex(false, MutexName))
        {
            bool locked = false;
            try
            {
                try { locked = mutex.WaitOne(TimeSpan.FromSeconds(30)); }
                catch (AbandonedMutexException) { locked = true; }
                if (!locked) throw new InvalidOperationException("Another launcher is starting the local server.");

                using (Process self = Process.GetCurrentProcess())
                {
                    sessionFile = Path.Combine(directory, self.Id + ".session");
                    File.WriteAllText(sessionFile, self.StartTime.Ticks.ToString());
                }
            }
            finally
            {
                if (locked) mutex.ReleaseMutex();
            }
        }
    }

    /// <summary>Refuses a jar packaged for a production server or a release channel.</summary>
    private static void EnsureLocalDevelopmentBuild(string jar)
    {
        string host;
        string metadataUrl;
        using (ZipArchive archive = ZipFile.OpenRead(jar))
        {
            host = JarProperty(archive, "deckduel/connection.properties", "host");
            metadataUrl = JarProperty(archive, "deckduel/update.properties", "metadataUrl");
        }

        if (IsMeaningful(host) || IsMeaningful(metadataUrl))
        {
            throw new InvalidOperationException(
                "This jar is packaged for a production server or a release channel.\n\n"
                + "Cards.exe is only for the default local development build (mvn package).\n"
                + "Use the installed Deck Duel launcher for a release installation.");
        }
    }

    /// <summary>True for a configured property; Maven placeholders count as empty, like the client.</summary>
    private static bool IsMeaningful(string value)
    {
        if (string.IsNullOrEmpty(value)) return false;
        return !(value.StartsWith("${") && value.EndsWith("}"));
    }

    /// <summary>Reads one key from a properties file inside the jar; null when absent.</summary>
    private static string JarProperty(ZipArchive archive, string entryName, string key)
    {
        ZipArchiveEntry entry = archive.GetEntry(entryName);
        if (entry == null) return null;
        using (Stream stream = entry.Open())
        using (StreamReader reader = new StreamReader(stream))
        {
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                line = line.Trim();
                if (line.Length == 0 || line.StartsWith("#")) continue;
                int equals = line.IndexOf('=');
                if (equals < 0) continue;
                if (line.Substring(0, equals).Trim() == key) return line.Substring(equals + 1).Trim();
            }
        }
        return null;
    }

    /// <summary>Returns the first Java 17+ executable from JAVA_HOME and PATH, or null.</summary>
    private static string FindJava()
    {
        string home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(home))
        {
            string candidate = Path.Combine(home, @"bin\java.exe");
            if (File.Exists(candidate) && JavaMajor(candidate) >= 17) return candidate;
        }

        string path = Environment.GetEnvironmentVariable("PATH");
        if (path != null)
        {
            string[] entries = path.Split(';');
            foreach (string entry in entries)
            {
                if (entry.Trim().Length == 0) continue;
                string candidate = Path.Combine(entry.Trim(), "java.exe");
                if (File.Exists(candidate) && JavaMajor(candidate) >= 17) return candidate;
            }
        }
        return null;
    }

    /// <summary>Parses the major version from <c>java -version</c> output; 0 when unknown.</summary>
    private static int JavaMajor(string javaExe)
    {
        try
        {
            ProcessStartInfo info = new ProcessStartInfo(javaExe, "-version");
            info.UseShellExecute = false;
            info.CreateNoWindow = true;
            info.RedirectStandardError = true;
            info.RedirectStandardOutput = true;
            using (Process process = Process.Start(info))
            {
                if (process == null) return 0;
                string text = process.StandardError.ReadToEnd() + process.StandardOutput.ReadToEnd();
                process.WaitForExit(5000);
                Match match = Regex.Match(text, "version \"(\\d+)(?:\\.(\\d+))?");
                if (!match.Success) return 0;
                int major = int.Parse(match.Groups[1].Value);
                if (major == 1 && match.Groups[2].Success) major = int.Parse(match.Groups[2].Value);
                return major;
            }
        }
        catch
        {
            return 0;
        }
    }

    /// <summary>Starts the server once, under a mutex that prevents two launchers racing.</summary>
    private static void EnsureServer(string root, string java, string jar)
    {
        if (PortOpen()) return;

        using (Mutex mutex = new Mutex(false, MutexName))
        {
            bool locked = false;
            try
            {
                try { locked = mutex.WaitOne(TimeSpan.FromSeconds(30)); }
                catch (AbandonedMutexException) { locked = true; }
                if (!locked) throw new InvalidOperationException("Another launcher is starting the local server.");
                if (PortOpen()) return;
                StartServer(root, java, jar);
            }
            finally
            {
                if (locked) mutex.ReleaseMutex();
            }
        }
    }

    /// <summary>Starts the hidden server, writes its marker, and waits for the port to open.</summary>
    private static void StartServer(string root, string java, string jar)
    {
        string logPath = Path.Combine(root, LogRelative);
        string dbPath = Path.Combine(root, DbRelative);
        Directory.CreateDirectory(Path.GetDirectoryName(dbPath));
        Directory.CreateDirectory(Path.GetDirectoryName(logPath));

        string inner = "\"" + java + "\" -jar \"" + jar + "\" server --port " + Port
            + " --db \"" + dbPath + "\" >> \"" + logPath + "\" 2>&1";
        string shell = Environment.GetEnvironmentVariable("COMSPEC");
        if (string.IsNullOrEmpty(shell)) shell = "cmd.exe";
        ProcessStartInfo info = new ProcessStartInfo(shell, "/d /s /c \"" + inner + "\"");
        info.WorkingDirectory = root;
        info.UseShellExecute = false;
        info.CreateNoWindow = true;
        info.WindowStyle = ProcessWindowStyle.Hidden;

        Process server = Process.Start(info);
        if (server == null) throw new InvalidOperationException("The local server could not be started.");
        File.WriteAllText(Path.Combine(root, MarkerRelative), server.Id.ToString());

        DateTime deadline = DateTime.UtcNow.AddSeconds(20);
        while (DateTime.UtcNow < deadline)
        {
            if (PortOpen()) return;
            if (server.HasExited)
            {
                DeleteMarker(root);
                throw new InvalidOperationException("The local server exited.\n\n" + Tail(logPath, 10));
            }
            Thread.Sleep(200);
        }

        KillTree(server.Id);
        DeleteMarker(root);
        throw new InvalidOperationException("The local server did not open port " + Port
            + " within 20 seconds.\n\n" + Tail(logPath, 10));
    }

    /// <summary>Opens the client against the local server and waits for it to close.</summary>
    private static void LaunchClient(string root, string java, string jar)
    {
        string javaw = Path.Combine(Path.GetDirectoryName(java), "javaw.exe");
        if (!File.Exists(javaw)) javaw = java;

        ProcessStartInfo info = new ProcessStartInfo(javaw,
            "-jar \"" + jar + "\" client --host " + Host + " --port " + Port);
        info.WorkingDirectory = root;
        info.UseShellExecute = false;
        info.CreateNoWindow = true;
        using (Process client = Process.Start(info))
        {
            if (client == null) throw new InvalidOperationException("The game client could not be started.");
            client.WaitForExit();
        }
    }

    /// <summary>Removes this session and stops the server when no client or launcher remains.</summary>
    private static void StopLauncherServer(string root)
    {
        DeleteOwnSession();

        string markerPath = Path.Combine(root, MarkerRelative);
        if (File.Exists(markerPath)) Thread.Sleep(1000);

        bool locked = false;
        try
        {
            using (Mutex mutex = new Mutex(false, MutexName))
            {
                try
                {
                    try { locked = mutex.WaitOne(TimeSpan.FromSeconds(30)); }
                    catch (AbandonedMutexException) { locked = true; }
                    if (!locked) return;

                    DeleteStaleSessions(root);
                    if (AnySession(root)) return;

                    for (int i = 0; i < 10 && OtherClientRunning(); i++) Thread.Sleep(500);
                    if (OtherClientRunning()) return;
                    if (!File.Exists(markerPath)) return;

                    string value;
                    try { value = File.ReadAllText(markerPath).Trim(); }
                    catch { return; }

                    int pid;
                    if (!int.TryParse(value, out pid) || !IsDeckDuelServer(pid))
                    {
                        TryDelete(markerPath);
                        return;
                    }

                    if (KillTree(pid) || !IsDeckDuelServer(pid)) TryDelete(markerPath);
                }
                finally
                {
                    if (locked) mutex.ReleaseMutex();
                }
            }
        }
        catch
        {
            // Cleanup must never turn a finished game into a startup error.
        }
    }

    /// <summary>Deletes this launcher's session marker, if it still exists.</summary>
    private static void DeleteOwnSession()
    {
        if (sessionFile != null) TryDelete(sessionFile);
    }

    /// <summary>Deletes session markers whose launcher process is no longer running.</summary>
    private static void DeleteStaleSessions(string root)
    {
        string directory = Path.Combine(root, SessionsRelative);
        if (!Directory.Exists(directory)) return;

        foreach (string file in Directory.GetFiles(directory, "*.session"))
        {
            int pid;
            long started;
            bool alive = false;
            if (int.TryParse(Path.GetFileNameWithoutExtension(file), out pid)
                && long.TryParse(ReadText(file), out started))
            {
                try
                {
                    using (Process process = Process.GetProcessById(pid))
                    {
                        if (!process.HasExited)
                        {
                            try { alive = process.StartTime.Ticks == started; }
                            catch { alive = true; }
                        }
                    }
                }
                catch
                {
                    alive = false;
                }
            }
            if (!alive) TryDelete(file);
        }
    }

    /// <summary>True when another Cards launcher has an active session.</summary>
    private static bool AnySession(string root)
    {
        string directory = Path.Combine(root, SessionsRelative);
        if (!Directory.Exists(directory)) return false;
        foreach (string file in Directory.GetFiles(directory, "*.session"))
        {
            if (sessionFile == null
                || !string.Equals(file, sessionFile, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>True while another Deck Duel client process is running.</summary>
    private static bool OtherClientRunning()
    {
        try
        {
            using (ManagementObjectSearcher searcher = new ManagementObjectSearcher(
                "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name = 'javaw.exe' OR Name = 'java.exe'"))
            using (ManagementObjectCollection processes = searcher.Get())
            {
                foreach (ManagementObject process in processes)
                {
                    if (HasDeckDuelRole(process["CommandLine"] as string, "client")) return true;
                }
            }
        }
        catch
        {
            return true;
        }
        return false;
    }

    /// <summary>True when the process command line is this project's Deck Duel server.</summary>
    private static bool IsDeckDuelServer(int pid)
    {
        try
        {
            using (ManagementObjectSearcher searcher = new ManagementObjectSearcher(
                "SELECT CommandLine FROM Win32_Process WHERE ProcessId = " + pid))
            using (ManagementObjectCollection processes = searcher.Get())
            {
                foreach (ManagementObject process in processes)
                {
                    if (HasDeckDuelRole(process["CommandLine"] as string, "server")) return true;
                }
            }
        }
        catch
        {
            return false;
        }
        return false;
    }

    /// <summary>Matches the role token directly after a deck-duel.jar argument.</summary>
    private static bool HasDeckDuelRole(string command, string role)
    {
        if (string.IsNullOrEmpty(command)) return false;
        string pattern = "-jar\\s+(?:\"[^\"]*deck-duel\\.jar\"|[^\\s\"]*deck-duel\\.jar)\\s+"
            + role + "(?=\\s|$)";
        return Regex.IsMatch(command, pattern, RegexOptions.IgnoreCase);
    }

    /// <summary>Kills a process and its children (cmd.exe and the Java server it runs).</summary>
    private static bool KillTree(int pid)
    {
        try
        {
            ProcessStartInfo info = new ProcessStartInfo("taskkill", "/PID " + pid + " /T /F");
            info.UseShellExecute = false;
            info.CreateNoWindow = true;
            using (Process killer = Process.Start(info))
            {
                if (killer == null) return false;
                if (!killer.WaitForExit(5000)) return false;
                return killer.ExitCode == 0;
            }
        }
        catch
        {
            return false;
        }
    }

    private static string ReadText(string path)
    {
        try { return File.ReadAllText(path).Trim(); }
        catch { return null; }
    }

    private static void TryDelete(string path)
    {
        try { File.Delete(path); }
        catch { }
    }

    private static bool PortOpen()
    {
        TcpClient client = new TcpClient();
        try
        {
            IAsyncResult result = client.BeginConnect(Host, Port, null, null);
            if (!result.AsyncWaitHandle.WaitOne(300)) return false;
            client.EndConnect(result);
            return true;
        }
        catch
        {
            return false;
        }
        finally
        {
            client.Close();
        }
    }

    private static void DeleteMarker(string root)
    {
        try { File.Delete(Path.Combine(root, MarkerRelative)); }
        catch { }
    }

    /// <summary>Returns the last <paramref name="lines"/> lines of a log, or a placeholder.</summary>
    private static string Tail(string path, int lines)
    {
        try
        {
            using (FileStream stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (StreamReader reader = new StreamReader(stream))
            {
                string[] all = reader.ReadToEnd().Split(new string[] { Environment.NewLine }, StringSplitOptions.None);
                int start = Math.Max(0, all.Length - lines);
                StringBuilder text = new StringBuilder();
                for (int i = start; i < all.Length; i++) text.AppendLine(all[i]);
                return text.ToString().Trim();
            }
        }
        catch
        {
            return "(no server log is available)";
        }
    }

    private static void ShowError(string message)
    {
        MessageBox.Show(message, "Cards", MessageBoxButtons.OK, MessageBoxIcon.Error);
    }
}
