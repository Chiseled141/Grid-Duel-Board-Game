package deckduel.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The update hand-over with real separate JVMs: the launcher jar installs and starts the game,
 * the helper waits for a running game to close, activates the new version and restarts it, and
 * a version that is tampered with or never starts properly gives way to the previous one.
 */
class UpdateProcessTest {
    @TempDir Path temporary;
    private TestPackages packages;
    private InstallLayout layout;
    private Path root, log;
    // Preferences and match data live outside the installation; updates must not touch them.
    private Path userData;

    @BeforeEach void install() throws Exception {
        packages = new TestPackages(temporary.resolve("build"));
        root = temporary.resolve("install");
        layout = new InstallLayout(root);
        log = temporary.resolve("processes.log");
        userData = temporary.resolve("home/.deckduel/client.properties");
        Files.createDirectories(userData.getParent());
        Files.writeString(userData, "soundEnabled=false\nhost=127.0.0.1\n");
        int installed = TestPackages.java(log, List.of("-jar", packages.launcherJar().toString(), "install",
                "--root", root.toString(), "--package", packages.packageFor("1.0.0").toString()), 60);
        assertEquals(0, installed, "the launcher installs a package");
    }

    private void launch() throws Exception {
        assertEquals(0, TestPackages.java(log, List.of("-jar", layout.stableLauncher().toString(), "launch", "--root", root.toString()), 60));
    }

    private Path started(String version) { return root.resolve("started-" + version + ".txt"); }

    @Test void theHelperWaitsForTheGameToCloseThenActivatesAndRestartsTheNewVersion() throws Exception {
        Files.writeString(root.resolve("behaviour-1.0.0"), "wait");
        launch();
        String running = TestPackages.awaitLines(started("1.0.0"), 1, 20_000).get(0);
        long gamePid = Long.parseLong(running.split(" ")[0]);
        assertEquals("client --install-root " + root, running.substring(running.indexOf(' ') + 1), "no account details are passed");
        assertTrue(ProcessHandle.of(gamePid).map(ProcessHandle::isAlive).orElse(false));

        // The running game has downloaded and staged 1.1.0, then starts the helper from the new folder.
        layout.installPackage(packages.packageFor("1.1.0"));
        Process helper = TestPackages.start(log, List.of("-jar", layout.versionDir("1.1.0").resolve(PackageManifest.LAUNCHER_JAR).toString(),
                "activate", "--root", root.toString(), "--version", "1.1.0", "--wait-pid", String.valueOf(gamePid)));
        Thread.sleep(1_500);
        assertTrue(helper.isAlive(), "the helper waits while the game is open");
        assertEquals("1.0.0", layout.pointer().orElseThrow().active(), "nothing changes while the game is open");

        Files.createFile(root.resolve("stop"));
        assertTrue(helper.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, helper.exitValue());
        String restarted = TestPackages.awaitLines(started("1.1.0"), 1, 20_000).get(0);
        assertTrue(restarted.endsWith("client --install-root " + root));
        assertEquals(new InstallLayout.Pointer("1.1.0", "1.0.0", 1, null), layout.pointer().orElseThrow());
        TestPackages.awaitLines(started("1.1.0"), 1, 1_000);
        long deadline = System.currentTimeMillis() + 10_000;
        while (!layout.confirmed("1.1.0") && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertTrue(layout.confirmed("1.1.0"), "the new version confirmed its start");
        assertEquals("soundEnabled=false\nhost=127.0.0.1\n", Files.readString(userData), "preferences are untouched");
        assertTrue(Files.isDirectory(layout.versionDir("1.0.0")), "the previous version stays for rollback");
    }

    @Test void aTamperedStagedVersionIsRefusedAndTheOldVersionStartsAgain() throws Exception {
        layout.installPackage(packages.packageFor("1.1.0"));
        TestPackages.corrupt(layout.versionDir("1.1.0").resolve(PackageManifest.APP_JAR));
        // The game already closed (an id that no longer exists), so the helper proceeds at once.
        int exit = TestPackages.java(log, List.of("-jar", layout.versionDir("1.1.0").resolve(PackageManifest.LAUNCHER_JAR).toString(),
                "activate", "--root", root.toString(), "--version", "1.1.0", "--wait-pid", "999999999"), 60);
        assertEquals(0, exit);
        assertEquals("1.0.0", layout.pointer().orElseThrow().active());
        TestPackages.awaitLines(started("1.0.0"), 1, 20_000);
        assertFalse(Files.exists(started("1.1.0")), "the tampered version never ran");
        assertTrue(Files.readString(root.resolve("logs/launcher.log")).contains("could not activate 1.1.0"));
    }

    @Test void aVersionThatKeepsCrashingIsRolledBackByTheLauncher() throws Exception {
        launch();
        TestPackages.awaitLines(started("1.0.0"), 1, 20_000);
        long deadline = System.currentTimeMillis() + 10_000;
        while (!layout.confirmed("1.0.0") && System.currentTimeMillis() < deadline) Thread.sleep(50);
        Files.writeString(root.resolve("behaviour-1.2.0"), "crash");
        layout.activate(layout.installPackage(packages.packageFor("1.2.0")).version());
        launch();
        TestPackages.awaitLines(started("1.2.0"), 1, 20_000);
        launch();
        TestPackages.awaitLines(started("1.2.0"), 2, 20_000);
        launch();
        TestPackages.awaitLines(started("1.0.0"), 2, 20_000);
        InstallLayout.Pointer pointer = layout.pointer().orElseThrow();
        assertEquals("1.0.0", pointer.active());
        assertEquals("1.2.0", pointer.failed());
        assertEquals(2, Files.readAllLines(started("1.2.0")).size(), "the crashing version was tried twice, no more");
    }
}
