package deckduel.client.update;

import deckduel.client.startup.UpdateRestarter;
import deckduel.launcher.InstallLayout;
import deckduel.launcher.PackageManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Starts the new version's launcher as a separate process that waits for this game to close,
 * activates the staged version and starts it. It receives only the installation folder, the
 * version and this process id: never an account name or password.
 */
public final class HelperRestarter implements UpdateRestarter {
    private final InstallLayout layout;

    public HelperRestarter(InstallLayout layout) { this.layout = layout; }

    @Override public void handOver(String version) throws IOException {
        Path helper = layout.versionDir(version).resolve(PackageManifest.LAUNCHER_JAR);
        if (!Files.isRegularFile(helper)) throw new IOException("The new version's updater is missing; try the update again.");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
        Files.createDirectories(layout.logs());
        new ProcessBuilder(List.of(java, "-jar", helper.toString(), "activate", "--root", layout.root().toString(),
                "--version", version, "--wait-pid", String.valueOf(ProcessHandle.current().pid())))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(layout.logs().resolve("updater.log").toFile()))
                .start();
    }
}
