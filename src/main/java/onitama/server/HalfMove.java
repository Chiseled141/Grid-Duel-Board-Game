package onitama.server;

import onitama.core.Square;

import java.io.Serializable;

/**
 * One recorded half-move of a match, in the format also used by replay
 * files. A pass has {@code from == null && to == null} but still names the
 * discarded card.
 */
public record HalfMove(int number, String cardId, Square from, Square to)
        implements Serializable {

    /** Returns true if this entry records a pass rather than a move. */
    public boolean isPass() {
        return from == null;
    }
}
