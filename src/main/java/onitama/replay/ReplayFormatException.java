package onitama.replay;

import onitama.core.OnitamaException;

/**
 * Thrown when a replay file cannot be parsed or fails validation against the
 * rules engine (a corrupted or foreign file). Carries a user-facing message.
 */
public class ReplayFormatException extends OnitamaException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception describing why the replay file is invalid. */
    public ReplayFormatException(String message) {
        super(message);
    }
}
