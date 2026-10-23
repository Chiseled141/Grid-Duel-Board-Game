package deckduel.client.update;

import deckduel.client.startup.ReleasePackage;
import deckduel.client.startup.UpdateCheck;
import deckduel.launcher.PlatformId;
import deckduel.launcher.ReleaseVersion;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Signed release metadata, a small properties file published next to its Ed25519 signature:
 * <pre>
 * format=1
 * version=1.1.0
 * minimumVersion=1.0.0            older clients must update before playing
 * minimumProtocol=16              clients speaking an older protocol must update
 * package.mac-aarch64.url=deck-duel-1.1.0-mac-aarch64.zip    absolute, or relative to the metadata
 * package.mac-aarch64.size=118700000
 * package.mac-aarch64.sha256=…
 * </pre>
 * Nothing in it is read before the signature over its exact bytes has been verified.
 */
public record ReleaseMetadata(String version, String minimumVersion, int minimumProtocol, Map<String, ReleasePackage> packages) {

    /** Verifies {@code signature} (Base64 text) over {@code body}, then parses it. */
    public static ReleaseMetadata verify(byte[] body, byte[] signature, PublicKey key, URI base) throws IOException {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(body);
            byte[] decoded = Base64.getDecoder().decode(new String(signature, StandardCharsets.US_ASCII).trim());
            if (!verifier.verify(decoded)) throw new IOException("The release information is not signed by Deck Duel's release key");
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw new IOException("The release information has an invalid signature", invalid);
        }
        return parse(new String(body, StandardCharsets.UTF_8), base);
    }

    private static ReleaseMetadata parse(String text, URI base) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        try {
            if (!"1".equals(properties.getProperty("format"))) throw new IllegalArgumentException("unknown format");
            String version = ReleaseVersion.parse(properties.getProperty("version", "")).toString();
            String minimum = ReleaseVersion.parse(properties.getProperty("minimumVersion", "")).toString();
            int protocol = Integer.parseInt(properties.getProperty("minimumProtocol", ""));
            Map<String, ReleasePackage> packages = new TreeMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("package.") || !key.endsWith(".url")) continue;
                String platform = key.substring("package.".length(), key.length() - ".url".length());
                if (!PlatformId.valid(platform)) throw new IllegalArgumentException("bad platform " + platform);
                URI url = UpdateConfig.checkedUrl(base.resolve(properties.getProperty(key)));
                long size = Long.parseLong(properties.getProperty("package." + platform + ".size", ""));
                String sha256 = properties.getProperty("package." + platform + ".sha256", "");
                packages.put(platform, new ReleasePackage(version, platform, url, size, sha256));
            }
            return new ReleaseMetadata(version, minimum, protocol, packages);
        } catch (RuntimeException invalid) {
            throw new IOException("The release information is malformed: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Compares this release with the installed client: required when the client is older than
     * {@code minimumVersion} or speaks an older protocol, optional when this release is newer,
     * otherwise up to date. Older metadata never offers a downgrade.
     */
    public UpdateCheck decide(String installedVersion, int protocol, String platform) throws IOException {
        ReleaseVersion installed;
        try {
            installed = ReleaseVersion.parse(installedVersion);
        } catch (IllegalArgumentException development) {
            throw new IOException("This build (" + installedVersion + ") is not a release, so it cannot be updated");
        }
        boolean outdated = installed.compareTo(ReleaseVersion.parse(minimumVersion)) < 0 || protocol < minimumProtocol;
        if (!ReleaseVersion.parse(version).newerThan(installed)) {
            if (outdated) throw new IOException("The release information requires a newer version than its latest release");
            return UpdateCheck.upToDate(installedVersion);
        }
        return new UpdateCheck(outdated ? UpdateCheck.Status.REQUIRED : UpdateCheck.Status.OPTIONAL, version, packages.get(platform));
    }
}
