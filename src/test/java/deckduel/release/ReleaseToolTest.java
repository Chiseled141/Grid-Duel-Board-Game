package deckduel.release;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.update.ReleaseMetadata;
import deckduel.client.update.UpdateConfig;
import deckduel.launcher.PackageArchive;
import deckduel.launcher.TestPackages;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Release tooling: keys stay out of source trees, packages match their platform, metadata verifies. */
class ReleaseToolTest {
    @TempDir Path temporary;

    /** A game-like jar stamped with {@code version}, carrying a glass library with the given header. */
    private Path gameJar(String version, String library, byte[] header, String extra) throws IOException {
        Path jar = temporary.resolve("game-" + version + "-" + library + (extra == null ? "" : "-x") + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("deckduel/version.properties"));
            out.write(("version=" + version + "\n").getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry(library));
            out.write(header);
            if (extra != null) out.putNextEntry(new ZipEntry(extra));
        }
        return jar;
    }

    private static byte[] machO(int cpu) {
        byte[] header = new byte[64];
        int[] magic = {0xCF, 0xFA, 0xED, 0xFE};
        for (int i = 0; i < 4; i++) header[i] = (byte) magic[i];
        for (int i = 0; i < 4; i++) header[4 + i] = (byte) (cpu >> (8 * i));
        return header;
    }

    private static byte[] pe(int machine) {
        byte[] header = new byte[256];
        header[0] = 'M'; header[1] = 'Z';
        header[0x3C] = (byte) 0x80;
        header[0x80] = 'P'; header[0x81] = 'E';
        header[0x84] = (byte) machine; header[0x85] = (byte) (machine >> 8);
        return header;
    }

    private static byte[] elf(int machine) {
        byte[] header = new byte[64];
        header[0] = 0x7F; header[1] = 'E'; header[2] = 'L'; header[3] = 'F';
        header[18] = (byte) machine; header[19] = (byte) (machine >> 8);
        return header;
    }

    @Test void nativeHeadersNameTheirPlatform() {
        assertEquals("aarch64", NativeLibraries.architecture(machO(0x0100000C)));
        assertEquals("x64", NativeLibraries.architecture(machO(0x01000007)));
        assertEquals("x64", NativeLibraries.architecture(pe(0x8664)));
        assertEquals("aarch64", NativeLibraries.architecture(pe(0xAA64)));
        assertEquals("x64", NativeLibraries.architecture(elf(0x3E)));
        assertEquals("aarch64", NativeLibraries.architecture(elf(0xB7)));
        assertEquals("unknown", NativeLibraries.architecture(new byte[] {1, 2, 3}));
    }

    @Test void packagingRefusesTheWrongVersionNativesOrStrayCopies() throws Exception {
        var packages = new TestPackages(temporary.resolve("build"));
        Path launcher = packages.launcherJar(), out = temporary.resolve("dist");
        Path macArm = gameJar("1.1.0", "libglass.dylib", machO(0x0100000C), null);
        Path zip = ReleaseTool.packageRelease("1.1.0", "mac-aarch64", macArm, launcher, out);
        assertEquals("mac-aarch64", PackageArchive.manifest(zip).platform());
        assertTrue(assertThrows(IOException.class, () -> ReleaseTool.packageRelease("1.1.0", "windows-x64", macArm, launcher, out))
                .getMessage().contains("natives for mac-aarch64"));
        assertTrue(assertThrows(IOException.class, () -> ReleaseTool.packageRelease("1.2.0", "mac-aarch64", macArm, launcher, out))
                .getMessage().contains("-Ddeckduel.version=1.2.0"));
        Path windows = gameJar("1.1.0", "glass.dll", pe(0x8664), null);
        assertEquals("windows-x64", PackageArchive.manifest(ReleaseTool.packageRelease("1.1.0", "windows-x64", windows, launcher, out)).platform());
        Path linux = gameJar("1.1.0", "libglass.so", elf(0xB7), null);
        assertEquals("linux-aarch64", PackageArchive.manifest(ReleaseTool.packageRelease("1.1.0", "linux-aarch64", linux, launcher, out)).platform());
        Path icloud = gameJar("1.1.0", "libglass.dylib", machO(0x0100000C), "assets/card-back 2.png");
        assertTrue(assertThrows(IOException.class, () -> ReleaseTool.packageRelease("1.1.0", "mac-aarch64", icloud, launcher, out))
                .getMessage().contains("iCloud"));
    }

    @Test void signingKeysAreRefusedInsideASourceTreeAndSignedMetadataVerifies() throws Exception {
        Path repo = temporary.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        assertThrows(IOException.class, () -> ReleaseTool.keygen(repo.resolve("keys/release.key")));
        Path privateKey = temporary.resolve("secrets/release.key");
        String publicKey = ReleaseTool.keygen(privateKey);
        assertThrows(IOException.class, () -> ReleaseTool.keygen(privateKey), "an existing key is never overwritten");
        if (privateKey.getFileSystem().supportedFileAttributeViews().contains("posix"))
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(privateKey)));

        var packages = new TestPackages(temporary.resolve("build"));
        Path zip = packages.packageFor("1.2.0", "linux-x64", packages.gameJar());
        Path site = temporary.resolve("site");
        ReleaseTool.sign(privateKey, "1.1.0", 15, site, List.of(zip));
        byte[] body = Files.readAllBytes(site.resolve("latest.properties"));
        byte[] signature = Files.readAllBytes(site.resolve("latest.properties.sig"));
        URI base = URI.create("https://bucket.s3.eu-west-1.amazonaws.com/stable/latest.properties");
        ReleaseMetadata metadata = ReleaseMetadata.verify(body, signature, UpdateConfig.publicKey(publicKey), base);
        assertEquals("1.2.0", metadata.version());
        assertEquals("1.1.0", metadata.minimumVersion());
        assertEquals(URI.create("https://bucket.s3.eu-west-1.amazonaws.com/stable/deck-duel-1.2.0-linux-x64.zip"),
                metadata.packages().get("linux-x64").url());
        assertTrue(Files.exists(site.resolve(zip.getFileName())), "the package sits beside its metadata for upload");
        assertFalse(new String(body, StandardCharsets.UTF_8).contains(Files.readString(privateKey).trim()), "no private key in metadata");
        assertThrows(IOException.class, () -> ReleaseTool.sign(privateKey, "1.3.0", 15, site, List.of(zip)), "minimum above the release");
        assertEquals(1, ReleaseTool.run(new String[] {"sign"}), "missing options fail cleanly");
    }

    @Test void launcherClassesNeedOnlyTheJdk() throws IOException {
        // The launcher jar holds only deckduel.launcher; it must not reach into the game's packages.
        try (var sources = Files.list(Path.of("src/main/java/deckduel/launcher"))) {
            for (Path source : sources.toList())
                for (String line : Files.readAllLines(source))
                    if (line.startsWith("import deckduel.")) fail(source.getFileName() + " imports " + line);
        }
    }
}
