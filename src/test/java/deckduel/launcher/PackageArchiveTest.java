package deckduel.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unsafe or damaged packages are refused and leave nothing behind. */
class PackageArchiveTest {
    @TempDir Path temporary;
    private TestPackages packages;
    private byte[] manifest, game, launcher;

    @BeforeEach void build() throws IOException {
        packages = new TestPackages(temporary);
        Path zip = packages.packageFor("1.1.0");
        manifest = PackageArchive.manifest(zip).bytes();
        game = Files.readAllBytes(packages.gameJar());
        launcher = Files.readAllBytes(packages.launcherJar());
    }

    private Path zip(String name, List<Map.Entry<String, byte[]>> entries) throws IOException {
        Path zip = temporary.resolve(name + ".zip");
        TestPackages.writeZip(zip, entries);
        return zip;
    }

    private void refused(String why, List<Map.Entry<String, byte[]>> entries, String message) throws IOException {
        Path target = temporary.resolve("out-" + why);
        IOException failure = assertThrows(IOException.class, () -> PackageArchive.extract(zip(why, entries), target, 1 << 26), why);
        assertTrue(failure.getMessage().contains(message), why + ": " + failure.getMessage());
        assertFalse(Files.exists(target), why + " leaves no folder behind");
        assertFalse(Files.exists(temporary.resolve("evil")), why + " wrote nothing outside");
    }

    @Test void aValidPackageExtractsAndVerifies() throws IOException {
        Path target = temporary.resolve("ok");
        PackageManifest read = PackageArchive.extract(packages.packageFor("1.1.0"), target, 1 << 26);
        assertEquals("1.1.0", read.version());
        assertArrayEquals(game, Files.readAllBytes(target.resolve(PackageManifest.APP_JAR)));
        assertThrows(IOException.class, () -> PackageArchive.extract(packages.packageFor("1.1.0"), target, 1 << 26), "never over an existing folder");
    }

    @Test void pathTricksAreRefused() throws IOException {
        for (String evil : List.of("../evil", "/evil", "sub/evil", "..\\evil", "C:evil", ".hidden", "evil/"))
            refused("name" + evil.hashCode(), List.of(Map.entry(PackageManifest.NAME, manifest), Map.entry(evil, new byte[] {1}),
                    Map.entry(PackageManifest.APP_JAR, game), Map.entry(PackageManifest.LAUNCHER_JAR, launcher)), "Unsafe");
    }

    @Test void unlistedDuplicateMissingAndChangedFilesAreRefused() throws IOException {
        refused("unlisted", List.of(Map.entry(PackageManifest.NAME, manifest), Map.entry("extra.sh", new byte[] {1}),
                Map.entry(PackageManifest.APP_JAR, game), Map.entry(PackageManifest.LAUNCHER_JAR, launcher)), "Unexpected");
        refused("missing", List.of(Map.entry(PackageManifest.NAME, manifest), Map.entry(PackageManifest.APP_JAR, game)), "missing");
        byte[] changed = game.clone();
        changed[changed.length / 2] ^= 1;
        refused("changed", List.of(Map.entry(PackageManifest.NAME, manifest), Map.entry(PackageManifest.APP_JAR, changed),
                Map.entry(PackageManifest.LAUNCHER_JAR, launcher)), "Checksum");
        refused("nomanifest", List.of(Map.entry(PackageManifest.APP_JAR, game), Map.entry(PackageManifest.LAUNCHER_JAR, launcher)), "no release");
        // ZipOutputStream refuses duplicate names, so the duplicate is written by hand-joining two archives' entries.
        Path dup = temporary.resolve("dup.zip");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(dup))) {
            for (var entry : List.of(Map.entry(PackageManifest.NAME, manifest), Map.entry(PackageManifest.APP_JAR, game),
                    Map.entry(PackageManifest.LAUNCHER_JAR, launcher))) {
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
            out.putNextEntry(new java.util.zip.ZipEntry(PackageManifest.APP_JAR.toUpperCase()));
            out.closeEntry();
        }
        assertThrows(IOException.class, () -> PackageArchive.extract(dup, temporary.resolve("out-dup"), 1 << 26));
    }

    @Test void packagesThatExpandTooFarAreRefused() throws IOException {
        Path target = temporary.resolve("bomb");
        IOException failure = assertThrows(IOException.class,
                () -> PackageArchive.extract(packages.packageFor("1.1.0"), target, game.length / 2));
        assertTrue(failure.getMessage().contains("size limit"));
        assertFalse(Files.exists(target));
    }

    @Test void malformedManifestsAreRefused() throws IOException {
        byte[] badVersion = new String(manifest, StandardCharsets.UTF_8).replace("version=1.1.0", "version=latest").getBytes(StandardCharsets.UTF_8);
        refused("badversion", List.of(Map.entry(PackageManifest.NAME, badVersion), Map.entry(PackageManifest.APP_JAR, game),
                Map.entry(PackageManifest.LAUNCHER_JAR, launcher)), "Invalid package manifest");
        byte[] huge = new byte[20_000];
        java.util.Arrays.fill(huge, (byte) '#');
        refused("hugemanifest", List.of(Map.entry(PackageManifest.NAME, huge)), "too large");
    }
}
