package onitama.net;

import java.io.Serial;

/**
 * S→C. An attempted move was illegal or out of turn; carries a
 * human-readable reason. The game state is never changed on rejection.
 */
public record MoveRejected(String reason) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
