package deckduel.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The launcher and updater helper, a separate small program ({@code deck-duel-launcher.jar}):
 * <ul>
 * <li>{@code launch [--root R]}: starts the active version's game, rolling back a version that
 * never started properly, then exits.</li>
 * <li>{@code activate --root R --version V --wait-pid P}: run by the game after it has downloaded
 * and verified V. Waits for the game (process P) to close, verifies V's files again, activates
 * it and starts the game. If anything fails, the previous version stays active and starts.</li>
 * <li>{@code install --root R --package ZIP}: sets up an installation from a package, for
 * development and tests; players get their first copy from an installer that does the same.</li>
 * </ul>
 * It runs from the new version's folder during an update, so no file it replaces is in use.
 * No credentials are ever passed to it or written by it.
 */
public final class LauncherMain {
    private static final Logger LOG = Logger.getLogger(LauncherMain.class.getName());
    /** How long the helper waits for the game to close before giving up on the update. */
    static final long CLOSE_TIMEOUT_SECONDS = 120;

    private LauncherMain() { }

    /** Runs one command; exits with 0 on success and 1 on failure. */
    public static void main(String[] args) {
        String command = args.length > 0 && !args[0].startsWith("--") ? args[0] : "launch";
        Map<String, String> options = options(args, args.length > 0 && !args[0].startsWith("--") ? 1 : 0);
        InstallLayout layout = new InstallLayout(options.containsKey("root") ? Path.of(options.get("root")) : InstallLayout.defaultRoot());
        logTo(layout);
        try {
            switch (command) {
                case "launch" -> launch(layout);
                case "activate" -> activate(layout, options.get("version"), Long.parseLong(options.get("wait-pid")));
                case "install" -> install(layout, Path.of(options.get("package")));
                default -> throw new IllegalArgumentException("Unknown launcher command " + command);
            }
        } catch (Exception failure) {
            LOG.log(Level.SEVERE, "launcher command " + command + " failed", failure);
            System.exit(1);
        }
    }

    /** Starts the active (or rolled-back) version's game and returns its process. */
    static Process launch(InstallLayout layout) throws IOException {
        String version = layout.chooseForLaunch();
        Path jar = layout.versionDir(version).resolve(PackageManifest.APP_JAR);
        Files.createDirectories(layout.logs());
        ProcessBuilder game = new ProcessBuilder(List.of(java(), "-jar", jar.toString(), "client",
                "--install-root", layout.root().toString()));
        game.redirectErrorStream(true);
        game.redirectOutput(ProcessBuilder.Redirect.appendTo(layout.logs().resolve("client.log").toFile()));
        LOG.info(() -> "starting Deck Duel " + version);
        return game.start();
    }

    /**
     * Waits for the old game to close, then activates {@code version} and starts it. If the game
     * does not close in time nothing changes and no second copy starts.
     */
    static void activate(InstallLayout layout, String version, long gamePid) throws IOException, InterruptedException {
        Optional<ProcessHandle> game = ProcessHandle.of(gamePid);
        if (game.isPresent()) {
            try {
                game.get().onExit().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException stillOpen) {
                LOG.warning(() -> "the game did not close; " + version + " was not activated");
                return;
            }
        }
        try {
            layout.activate(version);
        } catch (IOException failed) {
            // The previous version stays active and starts instead.
            LOG.log(Level.SEVERE, "could not activate " + version, failed);
        }
        launch(layout);
    }

    /** Installs and activates a package; for setting up development and test installations. */
    static void install(InstallLayout layout, Path zip) throws IOException {
        PackageManifest manifest = layout.installPackage(zip);
        layout.activate(manifest.version());
        System.out.println("Installed Deck Duel " + manifest.version() + " (" + manifest.platform() + ") in " + layout.root());
    }

    /** The Java runtime running this helper, used for the game as well. */
    static String java() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    private static Map<String, String> options(String[] args, int from) {
        Map<String, String> options = new HashMap<>();
        for (int i = from; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Expected --option value");
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static void logTo(InstallLayout layout) {
        try {
            Files.createDirectories(layout.logs());
            FileHandler file = new FileHandler(layout.logs().resolve("launcher.log").toString(), true);
            file.setFormatter(new SimpleFormatter());
            Logger.getLogger("deckduel.launcher").addHandler(file);
        } catch (IOException unavailable) {
            LOG.log(Level.FINE, "launcher log unavailable", unavailable);
        }
    }
}
