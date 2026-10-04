package onitama.net;

import java.io.Serial;

/**
 * C→S. The player resigns the current match; the opponent wins by FORFEIT.
 * Added beyond the brief's original message table because the game screen
 * requires a resign button.
 */
public record ResignRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
