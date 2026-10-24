package deckduel.client.startup;

import java.io.IOException;

/** The server refused this client before login because it is older than the server supports. */
public final class UpdateRequiredException extends IOException {
    private final String minimumVersion;

    public UpdateRequiredException(String minimumVersion, String text) {
        super(text);
        this.minimumVersion = minimumVersion;
    }

    /** The oldest client version the server accepts. */
    public String minimumVersion() { return minimumVersion; }
}
