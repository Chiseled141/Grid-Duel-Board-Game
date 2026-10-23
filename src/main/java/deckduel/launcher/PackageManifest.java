package deckduel.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The {@code release.properties} inside an update package and an installed version folder:
 * the version, the platform and the SHA-256 of every file the package may contain. Only these
 * files are extracted; the package's own hash is signed in the release metadata, so the manifest
 * is trusted once that hash has been checked.
 */
public record PackageManifest(String version, String platform, Map<String, String> files) {
    /** The manifest's name in a package and in an installed version folder. */
    public static final String NAME = "release.properties";
    /** The game jar, started by the launcher. */
    public static final String APP_JAR = "deck-duel.jar";
    /** The launcher and updater helper, kept small and free of game dependencies. */
    public static final String LAUNCHER_JAR = "deck-duel-launcher.jar";

    public PackageManifest {
        ReleaseVersion.parse(version);
        if (!PlatformId.valid(platform)) throw new IllegalArgumentException("Bad platform " + platform);
        files = Collections.unmodifiableMap(new TreeMap<>(files));
        if (!files.containsKey(APP_JAR) || !files.containsKey(LAUNCHER_JAR))
            throw new IllegalArgumentException("A package needs " + APP_JAR + " and " + LAUNCHER_JAR);
        for (var file : files.entrySet()) {
            if (!file.getKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}") || file.getKey().equals(NAME))
                throw new IllegalArgumentException("Bad file name " + file.getKey());
            if (!Hashes.valid(file.getValue())) throw new IllegalArgumentException("Bad hash for " + file.getKey());
        }
    }

    /** Reads a manifest, rejecting anything malformed. */
    public static PackageManifest read(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        if (!"1".equals(properties.getProperty("format"))) throw new IOException("Unknown package format");
        Map<String, String> files = new TreeMap<>();
        for (String key : properties.stringPropertyNames())
            if (key.startsWith("file.")) files.put(key.substring(5), properties.getProperty(key));
        try {
            return new PackageManifest(properties.getProperty("version", ""), properties.getProperty("platform", ""), files);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid package manifest: " + invalid.getMessage(), invalid);
        }
    }

    /** The manifest as UTF-8 properties text. */
    public byte[] bytes() {
        StringWriter text = new StringWriter();
        text.write("format=1\nversion=" + version + "\nplatform=" + platform + "\n");
        files.forEach((name, hash) -> text.write("file." + name + "=" + hash + "\n"));
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
}
