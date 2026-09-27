package onitama.net;

import java.io.Serial;

/**
 * C→S. Authenticates an existing account.
 */
public record LoginRequest(String username, String password) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
