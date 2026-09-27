package onitama.net;

import onitama.core.Square;

import java.io.Serial;

/**
 * C→S. Attempts to play the hand card {@code cardId}, moving the piece on
 * {@code from} to {@code to}. The server validates the move; on success both
 * players receive {@link MoveApplied}, otherwise the mover receives
 * {@link MoveRejected} and the state is unchanged.
 */
public record MoveRequest(String cardId, Square from, Square to) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
