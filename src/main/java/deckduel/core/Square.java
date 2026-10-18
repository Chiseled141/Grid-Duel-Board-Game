package deckduel.core;

import java.io.Serial;
import java.io.Serializable;

/**
 * Immutable canonical board coordinate. Each engine validates its own bounds;
 * Deck Duel uses x/y = 0..6, with y = 0 as Blue's home row.
 */
public record Square(int x, int y) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Returns the square reached from this one by applying the given offset. */
    public Square plus(Offset offset) {
        return new Square(x + offset.dx(), y + offset.dy());
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
