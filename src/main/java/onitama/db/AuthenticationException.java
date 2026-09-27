package onitama.db;

import onitama.core.OnitamaException;

/**
 * Thrown when registration or login fails (bad credentials, malformed
 * username, duplicate account). The message is safe to show to the user.
 */
public class AuthenticationException extends OnitamaException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with a user-facing reason. */
    public AuthenticationException(String message) {
        super(message);
    }
}
