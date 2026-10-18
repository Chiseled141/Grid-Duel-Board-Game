package deckduel.db;

import deckduel.core.DeckDuelException;

import java.io.Serial;

/**
 * Thrown when database access fails (open, query, constraint). The message is
 * safe to log with full detail and show in shortened form to users.
 */
public class PersistenceException extends DeckDuelException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message and the underlying SQL cause. */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
