package deckduel.core;

import java.io.Serial;

/**
 * Base class of the project's exception hierarchy. Unchecked, so call sites
 * that cannot recover stay readable; every subclass describes one concrete
 * failure domain (authentication or persistence).
 */
public class DeckDuelException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a human-readable message. */
    public DeckDuelException(String message) {
        super(message);
    }

    /** Creates an exception with a message and an underlying cause. */
    public DeckDuelException(String message, Throwable cause) {
        super(message, cause);
    }
}
