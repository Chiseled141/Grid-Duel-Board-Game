package onitama.core;

import java.io.Serial;
import java.io.Serializable;

/**
 * One movement offset of a card, relative to the moving piece. Offsets are
 * stored in Blue's native orientation: {@code +x} is right and {@code +y} is
 * forward (toward Red's home row). The same physical card used by Red is
 * rotated 180 degrees.
 */
public record Offset(int dx, int dy) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Returns this offset rotated by 180 degrees, i.e. the same physical
     * movement seen from the opposite side of the board.
     */
    public Offset rotate180() {
        return new Offset(-dx, -dy);
    }
}
