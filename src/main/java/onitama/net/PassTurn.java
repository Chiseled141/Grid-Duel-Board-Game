package onitama.net;

import java.io.Serial;

/**
 * C→S. Passes the turn, discarding the named hand card into the card cycle.
 * Only accepted when the player truly has no legal move — the server
 * verifies this with the rules engine.
 */
public record PassTurn(String cardIdToDiscard) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
