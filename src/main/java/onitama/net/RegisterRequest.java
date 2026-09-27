package onitama.net;

import java.io.Serial;

/**
 * C→S. Asks the server to create a new account and log it in.
 */
public record RegisterRequest(String username, String password) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
