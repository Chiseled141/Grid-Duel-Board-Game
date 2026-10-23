package deckduel.net;

import java.io.Serial;

/**
 * S→C, instead of {@link ProtocolReady}: this client is older than the server supports. Sent
 * before login, so no credentials are exchanged; the server then closes the connection.
 */
public record UpdateRequired(String minimumVersion, String text) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}
