package deckduel.release;

import deckduel.launcher.Hashes;
import deckduel.launcher.PackageArchive;
import deckduel.launcher.PackageManifest;
import deckduel.launcher.PlatformId;
import deckduel.launcher.ReleaseVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local release tooling ({@code java -jar deck-duel.jar release ...}):
 * <ul>
 * <li>{@code keygen --private-key FILE [--public-key FILE]}: creates an Ed25519 signing key outside
 * any source tree and prints (or writes) the public key to package into clients.</li>
 * <li>{@code platform}: prints this computer's platform id, for example {@code mac-aarch64}.</li>
 * <li>{@code package --version V --platform P --app deck-duel.jar --launcher deck-duel-launcher.jar --out DIR}:
 * builds {@code deck-duel-V-P.zip}, refusing a game jar whose version or JavaFX natives do not match.</li>
 * <li>{@code sign --private-key FILE --minimum-version M --minimum-protocol N --out DIR PACKAGE...}:
 * writes {@code latest.properties} and its signature {@code latest.properties.sig}, with package
 * addresses relative to the metadata, ready to upload side by side.</li>
 * </ul>
 * It never uploads anything.
 */
public final class ReleaseTool {
    private ReleaseTool() { }

    /** Runs one command and returns the process exit code. */
    public static int run(String[] args) {
        if (args.length == 0) { System.err.println("Usage: release keygen|package|sign ..."); return 2; }
        try {
            List<String> files = new ArrayList<>();
            Map<String, String> options = options(args, files);
            switch (args[0]) {
                case "keygen" -> {
                    String publicKey = keygen(Path.of(required(options, "private-key")));
                    if (options.containsKey("public-key")) Files.writeString(Path.of(options.get("public-key")), publicKey + "\n");
                }
                case "platform" -> System.out.println(PlatformId.current());
                case "package" -> packageRelease(required(options, "version"), required(options, "platform"),
                        Path.of(required(options, "app")), Path.of(required(options, "launcher")), Path.of(required(options, "out")));
                case "sign" -> sign(Path.of(required(options, "private-key")), required(options, "minimum-version"),
                        Integer.parseInt(required(options, "minimum-protocol")), Path.of(required(options, "out")),
                        files.stream().map(Path::of).toList());
                default -> throw new IllegalArgumentException("Unknown release command " + args[0]);
            }
            return 0;
        } catch (Exception failure) {
            System.err.println("release " + args[0] + ": " + failure.getMessage());
            return 1;
        }
    }

    /** Writes a new private key (PKCS#8, Base64, owner-only) and prints the public key. */
    static String keygen(Path privateKey) throws Exception {
        refuseSourceTree(privateKey);
        if (Files.exists(privateKey)) throw new IOException(privateKey + " already exists; refusing to overwrite a signing key");
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(privateKey.toAbsolutePath().getParent());
        Files.writeString(privateKey, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n");
        try {
            Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // Windows keeps the file private to the user profile through its own permissions.
        }
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        System.out.println("Private key: " + privateKey.toAbsolutePath() + " (keep it out of the repository and client packages)");
        System.out.println("Public key for -Ddeckduel.update.publicKey: " + publicKey);
        return publicKey;
    }

    /** Refuses to place a private key in a folder that belongs to a source tree. */
    static void refuseSourceTree(Path file) throws IOException {
        for (Path folder = file.toAbsolutePath().getParent(); folder != null; folder = folder.getParent())
            if (Files.exists(folder.resolve(".git")) || Files.exists(folder.resolve("pom.xml")))
                throw new IOException("Refusing to write a signing key inside the source tree " + folder);
    }

    /** Builds a platform package and returns its path. */
    static Path packageRelease(String version, String platform, Path app, Path launcher, Path out) throws IOException {
        ReleaseVersion.parse(version);
        if (!PlatformId.valid(platform)) throw new IOException("Bad platform " + platform);
        checkGameJar(app, version, platform);
        Map<String, String> files = new LinkedHashMap<>();
        files.put(PackageManifest.APP_JAR, Hashes.sha256(app));
        files.put(PackageManifest.LAUNCHER_JAR, Hashes.sha256(launcher));
        PackageManifest manifest = new PackageManifest(version, platform, files);
        Files.createDirectories(out);
        Path zip = out.resolve("deck-duel-" + version + "-" + platform + ".zip");
        writePackage(manifest, Map.of(PackageManifest.APP_JAR, app, PackageManifest.LAUNCHER_JAR, launcher), zip);
        // Read the package back exactly as a client would before announcing it.
        Path check = Files.createTempDirectory("deck-duel-package-check");
        try {
            PackageArchive.extract(zip, check.resolve("unpacked"), PackageArchive.DEFAULT_MAX_BYTES);
        } finally {
            PackageArchive.deleteTree(check);
        }
        System.out.println(zip + "  " + Files.size(zip) + " bytes  sha256 " + Hashes.sha256(zip));
        return zip;
    }

    /** Writes a package zip: the manifest, then the listed files. */
    static void writePackage(PackageManifest manifest, Map<String, Path> files, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(PackageManifest.NAME));
            out.write(manifest.bytes());
            out.closeEntry();
            for (String name : manifest.files().keySet()) {
                out.putNextEntry(new ZipEntry(name));
                Files.copy(files.get(name), out);
                out.closeEntry();
            }
        }
    }

    /** The game jar must carry this version and this platform's JavaFX natives, and no stray copies. */
    private static void checkGameJar(Path app, String version, String platform) throws IOException {
        try (JarFile jar = new JarFile(app.toFile())) {
            Properties stamped = new Properties();
            ZipEntry versionEntry = jar.getEntry("deckduel/version.properties");
            if (versionEntry == null) throw new IOException(app + " is not a Deck Duel game jar");
            try (InputStream in = jar.getInputStream(versionEntry)) { stamped.load(in); }
            if (!version.equals(stamped.getProperty("version")))
                throw new IOException(app + " is version " + stamped.getProperty("version") + ", not " + version
                        + "; build it with -Ddeckduel.version=" + version);
            String natives = NativeLibraries.platformOf(jar).orElseThrow(() -> new IOException(app + " has no JavaFX native libraries"));
            boolean universal = natives.endsWith("-universal") && platform.startsWith(natives.substring(0, natives.indexOf('-')));
            if (!natives.equals(platform) && !universal)
                throw new IOException(app + " carries JavaFX natives for " + natives + ", not " + platform
                        + "; build on that platform or with -Djavafx.platform");
            var copies = jar.stream().map(ZipEntry::getName).filter(name -> name.matches(".* \\d+(\\.[^/]*)?")).limit(3).toList();
            if (!copies.isEmpty()) throw new IOException(app + " contains stray file copies such as '" + copies.get(0)
                    + "' (iCloud duplicates); rebuild with mvn clean package");
        }
    }

    /** Writes signed metadata for the given packages, which must share one version. */
    static void sign(Path privateKey, String minimumVersion, int minimumProtocol, Path out, List<Path> packages) throws Exception {
        if (packages.isEmpty()) throw new IOException("Name at least one package to sign");
        ReleaseVersion.parse(minimumVersion);
        String version = null;
        StringBuilder text = new StringBuilder("format=1\n");
        StringBuilder entries = new StringBuilder();
        for (Path zip : packages) {
            PackageManifest manifest = PackageArchive.manifest(zip);
            if (version != null && !version.equals(manifest.version())) throw new IOException("Packages of different versions");
            version = manifest.version();
            String prefix = "package." + manifest.platform() + ".";
            entries.append(prefix).append("url=").append(zip.getFileName()).append('\n')
                    .append(prefix).append("size=").append(Files.size(zip)).append('\n')
                    .append(prefix).append("sha256=").append(Hashes.sha256(zip)).append('\n');
        }
        if (ReleaseVersion.parse(minimumVersion).newerThan(ReleaseVersion.parse(version)))
            throw new IOException("The minimum version is newer than the release");
        text.append("version=").append(version).append('\n').append("minimumVersion=").append(minimumVersion).append('\n')
                .append("minimumProtocol=").append(minimumProtocol).append('\n').append(entries);
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        PrivateKey key = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(privateKey).trim())));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(body);
        Files.createDirectories(out);
        Files.write(out.resolve("latest.properties"), body);
        Files.writeString(out.resolve("latest.properties.sig"), Base64.getEncoder().encodeToString(signer.sign()) + "\n");
        for (Path zip : packages) {
            Path target = out.resolve(zip.getFileName());
            if (!Files.exists(target) || !Files.isSameFile(zip, target)) Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Signed " + version + " for " + packages.size() + " package(s) into " + out);
    }

    private static Map<String, String> options(String[] args, List<String> files) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + args[i]);
                options.put(args[i].substring(2), args[++i]);
            } else files.add(args[i]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) throw new IllegalArgumentException("Missing --" + name);
        return value;
    }
}
