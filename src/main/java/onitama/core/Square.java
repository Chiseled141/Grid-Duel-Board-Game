package onitama.core;

import java.io.Serial;
import java.io.Serializable;

/**
 * Immutable coordinate on the 5x5 board. {@code x} is the column 0-4
 * (left to right), {@code y} is the row 0-4 (y = 0 is Blue's home row).
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
