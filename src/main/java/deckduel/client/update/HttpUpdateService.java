package deckduel.client.update;

import deckduel.client.startup.UpdateCheck;
import deckduel.client.startup.UpdateService;
import java.io.IOException;
import java.net.URI;

/**
 * Reads signed release metadata over HTTPS (for example straight from an S3 bucket, with no
 * credentials) and decides whether this client must, may or need not update. The signature,
 * fetched from {@code <metadata>.sig}, is verified before anything in the metadata is used.
 */
public final class HttpUpdateService implements UpdateService {
    static final int MAX_METADATA_BYTES = 64 * 1024, MAX_SIGNATURE_BYTES = 1024;

    private final UpdateConfig config;
    private final int protocol;
    private final String platform;

    /** Checks for {@code platform} on behalf of a client speaking {@code protocol}. */
    public HttpUpdateService(UpdateConfig config, int protocol, String platform) {
        this.config = config;
        this.protocol = protocol;
        this.platform = platform;
    }

    @Override public UpdateCheck check(String installedVersion) throws IOException {
        URI metadata = config.metadataUrl();
        byte[] body = Http.fetch(metadata, MAX_METADATA_BYTES);
        byte[] signature = Http.fetch(URI.create(metadata + ".sig"), MAX_SIGNATURE_BYTES);
        return ReleaseMetadata.verify(body, signature, config.publicKey(), metadata).decide(installedVersion, protocol, platform);
    }
}
