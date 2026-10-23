package deckduel.client.startup;

import java.net.URI;
import java.util.Objects;

/** One downloadable update package from signed release metadata: where it is, its size and SHA-256. */
public record ReleasePackage(String version, String platform, URI url, long size, String sha256) {
    public ReleasePackage {
        Objects.requireNonNull(version);
        Objects.requireNonNull(platform);
        Objects.requireNonNull(url);
        if (size <= 0) throw new IllegalArgumentException("A package has a positive size");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Bad SHA-256");
    }
}
