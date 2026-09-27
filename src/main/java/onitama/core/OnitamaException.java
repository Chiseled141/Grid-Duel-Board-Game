package onitama.core;

/**
 * Base class of the project's exception hierarchy. Unchecked, so call sites
 * that cannot recover stay readable; every subclass describes one concrete
 * failure domain (illegal move, protocol misuse, authentication, persistence).
 */
public class OnitamaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with a human-readable message. */
    public OnitamaException(String message) {
        super(message);
    }

    /** Creates an exception with a message and an underlying cause. */
    public OnitamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
