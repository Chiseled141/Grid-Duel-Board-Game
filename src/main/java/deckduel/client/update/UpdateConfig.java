package deckduel.client.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Where release metadata is published and the public key that must have signed it, packaged in
 * {@code /deckduel/update.properties} at build time ({@code -Ddeckduel.update.url},
 * {@code -Ddeckduel.update.publicKey}). Both empty means no release channel is configured.
 * Only HTTPS is accepted, except plain HTTP to this computer for local development.
 */
public record UpdateConfig(URI metadataUrl, PublicKey publicKey) {

    /** The packaged configuration, empty when none is set; throws when it is set but invalid. */
    public static Optional<UpdateConfig> packaged() {
        Properties properties = new Properties();
        try (InputStream in = UpdateConfig.class.getResourceAsStream("/deckduel/update.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException unreadable) {
            throw new IllegalStateException("The packaged update configuration cannot be read", unreadable);
        }
        return from(properties);
    }

    /** Reads {@code metadataUrl} and {@code publicKey}; empty when both are blank. */
    public static Optional<UpdateConfig> from(Properties properties) {
        String url = unfiltered(properties.getProperty("metadataUrl")), key = unfiltered(properties.getProperty("publicKey"));
        if (url.isEmpty() && key.isEmpty()) return Optional.empty();
        if (url.isEmpty() || key.isEmpty()) throw new IllegalStateException("The update configuration needs both an address and a public key");
        return Optional.of(new UpdateConfig(checkedUrl(URI.create(url)), publicKey(key)));
    }

    /** Accepts HTTPS anywhere, and HTTP only to this computer. */
    static URI checkedUrl(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        if (scheme.equals("https") || scheme.equals("http") && loopback) return url;
        throw new IllegalStateException("Update addresses must use HTTPS: " + url);
    }

    /** Decodes a Base64 X.509 Ed25519 public key. */
    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())));
        } catch (Exception invalid) {
            throw new IllegalStateException("The update public key is not a valid Ed25519 key", invalid);
        }
    }

    private static String unfiltered(String value) {
        // A build without the release properties leaves Maven's placeholders in place.
        return value == null || value.contains("${") ? "" : value.trim();
    }
}
