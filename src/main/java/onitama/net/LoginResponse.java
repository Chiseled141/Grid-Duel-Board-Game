package onitama.net;

import java.io.Serial;

/**
 * S→C. Result of a login or registration. On success carries the user's
 * profile and a random reconnect token the client can use to resume its
 * session after a dropped connection.
 */
public record LoginResponse(boolean ok, String errorText, UserProfile profile,
                            String reconnectToken) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
