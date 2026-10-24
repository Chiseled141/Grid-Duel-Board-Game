package deckduel.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Versioned folders, atomic activation, verification, rollback and pruning, in one process. */
class InstallLayoutTest {
    @TempDir Path temporary;
    private TestPackages packages;
    private InstallLayout layout;

    @BeforeEach void build() {
        packages = new TestPackages(temporary.resolve("build"));
        layout = new InstallLayout(temporary.resolve("install"));
    }

    private void installAndActivate(String version) throws IOException {
        layout.activate(layout.installPackage(packages.packageFor(version)).version());
    }

    @Test void activationKeepsThePreviousVersionAndRefreshesTheLauncher() throws IOException {
        assertTrue(layout.pointer().isEmpty());
        installAndActivate("1.0.0");
        assertEquals(new InstallLayout.Pointer("1.0.0", null, 0, null), layout.pointer().orElseThrow());
        assertTrue(Files.isRegularFile(layout.stableLauncher()));
        installAndActivate("1.1.0");
        assertEquals(new InstallLayout.Pointer("1.1.0", "1.0.0", 0, null), layout.pointer().orElseThrow());
        installAndActivate("1.2.0");
        assertEquals("1.1.0", layout.pointer().orElseThrow().previous());
        assertFalse(Files.exists(layout.versionDir("1.0.0")), "older versions are pruned");
        try (var left = Files.list(layout.root())) {
            assertTrue(left.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "no temporary pointer files remain");
        }
    }

    @Test void aVerifiedCopyIsReusedAndADamagedOrHalfWrittenOneIsReplaced() throws IOException {
        Path zip = packages.packageFor("1.1.0");
        layout.installPackage(zip);
        Path jar = layout.versionDir("1.1.0").resolve(PackageManifest.APP_JAR);
        long written = Files.getLastModifiedTime(jar).toMillis();
        layout.installPackage(zip);
        assertEquals(written, Files.getLastModifiedTime(jar).toMillis(), "a verified copy is kept");
        TestPackages.corrupt(jar);
        assertThrows(IOException.class, () -> layout.verify("1.1.0"));
        Files.createDirectories(layout.versions().resolve("1.1.0.incoming"));
        layout.installPackage(zip);
        layout.verify("1.1.0");
        assertFalse(Files.exists(layout.versions().resolve("1.1.0.incoming")), "an interrupted extraction is cleared");
    }

    @Test void aTamperedVersionIsNeverActivated() throws IOException {
        installAndActivate("1.0.0");
        layout.installPackage(packages.packageFor("1.1.0"));
        TestPackages.corrupt(layout.versionDir("1.1.0").resolve(PackageManifest.LAUNCHER_JAR));
        assertThrows(IOException.class, () -> layout.activate("1.1.0"));
        assertEquals("1.0.0", layout.pointer().orElseThrow().active());
    }

    @Test void aVersionThatNeverConfirmsAStartIsRolledBack() throws IOException {
        installAndActivate("1.0.0");
        layout.confirm("1.0.0");
        installAndActivate("1.1.0");
        assertEquals("1.1.0", layout.chooseForLaunch());
        assertEquals("1.1.0", layout.chooseForLaunch());
        assertEquals("1.0.0", layout.chooseForLaunch(), "the third unconfirmed launch rolls back");
        assertEquals(new InstallLayout.Pointer("1.0.0", null, 0, "1.1.0"), layout.pointer().orElseThrow());
        assertEquals("1.0.0", layout.chooseForLaunch(), "a confirmed version stays");
    }

    @Test void aConfirmedVersionIsNeverRolledBack() throws IOException {
        installAndActivate("1.0.0");
        installAndActivate("1.1.0");
        layout.confirm("1.1.0");
        for (int launch = 0; launch < 5; launch++) assertEquals("1.1.0", layout.chooseForLaunch());
    }

    @Test void launchingWithoutAnInstallationExplainsWhy() {
        IOException failure = assertThrows(IOException.class, () -> layout.chooseForLaunch());
        assertTrue(failure.getMessage().contains("not installed"));
    }
}
