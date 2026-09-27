package onitama.net;

import java.io.Serial;

/**
 * C→S. Re-attaches a fresh connection to a previously authenticated session
 * using the secret reconnect token issued at login. On success the server
 * resumes the match and sends the current {@link MoveApplied}-style state.
 */
public record ReconnectRequest(String reconnectToken) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
