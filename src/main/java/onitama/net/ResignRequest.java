package onitama.net;

import java.io.Serial;

/**
 * C→S. The player resigns the current match; the opponent wins by FORFEIT.
 * Added beyond the brief's message table because the game screen requires a
 * resign button (recorded in docs/DESIGN_DECISIONS.md).
 */
public record ResignRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
