package deckduel.launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Real artifacts for update tests: a fake game jar compiled from source, a real launcher jar
 * built from the compiled launcher classes, and update packages holding both.
 *
 * <p>The fake game, started as {@code client --install-root R}, appends a line to
 * {@code R/started-<version>.txt} (its pid and arguments), then behaves as {@code R/behaviour-<version>}
 * says: {@code ok} (default) confirms a proper start and exits; {@code crash} exits with 3 without
 * confirming; {@code wait} confirms and stays open until {@code R/stop} exists, like a running game.
 */
public final class TestPackages {
    private static final String FAKE_GAME = """
            import java.nio.file.*;
            public class FakeGame {
                public static void main(String[] args) throws Exception {
                    Path jar = Path.of(FakeGame.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    String version = jar.getParent().getFileName().toString();
                    Path root = Path.of(args[args.length - 1]);
                    Files.writeString(root.resolve("started-" + version + ".txt"),
                            ProcessHandle.current().pid() + " " + String.join(" ", args) + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    Path behaviour = root.resolve("behaviour-" + version);
                    String mode = Files.exists(behaviour) ? Files.readString(behaviour).trim() : "ok";
                    if (mode.equals("crash")) System.exit(3);
                    Path ok = jar.getParent().resolve(".started-ok");
                    if (!Files.exists(ok)) Files.createFile(ok);
                    if (mode.equals("wait")) while (!Files.exists(root.resolve("stop"))) Thread.sleep(50);
                }
            }
            """;

    private final Path folder;
    private Path gameJar, launcherJar;

    /** Builds artifacts inside {@code folder}. */
    public TestPackages(Path folder) { this.folder = folder; }

    /** The fake game jar, compiled once. */
    public synchronized Path gameJar() throws IOException {
        if (gameJar != null) return gameJar;
        Path source = folder.resolve("src/FakeGame.java"), classes = folder.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, FAKE_GAME);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString()) != 0)
            throw new IOException("could not compile the fake game");
        gameJar = folder.resolve("fake-game.jar");
        writeJar(gameJar, "FakeGame", Map.of("FakeGame.class", classes.resolve("FakeGame.class")));
        return gameJar;
    }

    /** A real launcher jar from the compiled {@code deckduel.launcher} classes. */
    public synchronized Path launcherJar() throws IOException {
        if (launcherJar != null) return launcherJar;
        Path classes;
        try {
            classes = Path.of(InstallLayout.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException impossible) {
            throw new IOException(impossible);
        }
        Map<String, Path> entries = new TreeMap<>();
        try (var files = Files.walk(classes.resolve("deckduel/launcher"))) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                entries.put(classes.relativize(file).toString().replace('\\', '/'), file);
        }
        Files.createDirectories(folder);
        launcherJar = folder.resolve("launcher.jar");
        writeJar(launcherJar, "deckduel.launcher.LauncherMain", entries);
        return launcherJar;
    }

    /** An update package for {@code version} on this platform holding the fake game and the launcher. */
    public Path packageFor(String version) throws IOException {
        return packageFor(version, PlatformId.current(), gameJar());
    }

    /** An update package with a chosen platform and game jar. */
    public Path packageFor(String version, String platform, Path game) throws IOException {
        Map<String, String> hashes = Map.of(PackageManifest.APP_JAR, Hashes.sha256(game),
                PackageManifest.LAUNCHER_JAR, Hashes.sha256(launcherJar()));
        PackageManifest manifest = new PackageManifest(version, platform, hashes);
        Path zip = folder.resolve("deck-duel-" + version + "-" + platform + ".zip");
        writeZip(zip, List.of(Map.entry(PackageManifest.NAME, manifest.bytes()),
                Map.entry(PackageManifest.APP_JAR, Files.readAllBytes(game)),
                Map.entry(PackageManifest.LAUNCHER_JAR, Files.readAllBytes(launcherJar()))));
        return zip;
    }

    /** Writes a zip with exactly these entries, in order (duplicates and odd names allowed, for attack tests). */
    public static void writeZip(Path zip, List<Map.Entry<String, byte[]>> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var entry : entries) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    private static void writeJar(Path jar, String mainClass, Map<String, Path> entries) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                Files.copy(entry.getValue(), out);
                out.closeEntry();
            }
        }
    }

    /** Runs a real JVM process ({@code java} plus {@code args}) and waits for it, returning its exit code. */
    public static int java(Path log, List<String> args, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = start(log, args);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("process timed out: " + args);
        }
        return process.exitValue();
    }

    /** Starts a real JVM process ({@code java} plus {@code args}), its output appended to {@code log}. */
    public static Process start(Path log, List<String> args) throws IOException {
        List<String> command = new ArrayList<>(List.of(LauncherMain.java()));
        command.addAll(args);
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
    }

    /** Waits until {@code file} exists and holds at least {@code lines} lines. */
    public static List<String> awaitLines(Path file, int lines, long timeoutMillis) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file)) {
                List<String> read = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (read.size() >= lines) return read;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for " + lines + " line(s) in " + file);
    }

    /** Overwrites one byte in the middle of a file, as tampering or disk damage would. */
    public static void corrupt(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 2] ^= 0x5A;
        try (OutputStream out = Files.newOutputStream(file)) { out.write(bytes); }
    }
}
