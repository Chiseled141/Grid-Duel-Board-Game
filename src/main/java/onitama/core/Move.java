package onitama.core;

import java.io.Serial;
import java.io.Serializable;

/**
 * A half-move: move the piece on {@code from} to {@code to} using the card
 * identified by {@code cardId}. The moving color is the player whose turn it
 * is when the move is applied.
 */
public record Move(Square from, Square to, String cardId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return cardId + " " + from + "-" + to;
    }
}
