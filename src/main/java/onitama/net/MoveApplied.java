package onitama.net;

import onitama.core.GameState;
import onitama.core.Move;

import java.io.Serial;

/**
 * S→C. Broadcast to both players after every half-move: the full
 * authoritative state and the move that produced it (null after a pass).
 */
public record MoveApplied(GameState state, Move lastMove) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
