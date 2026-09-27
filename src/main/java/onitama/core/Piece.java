package onitama.core;

import java.io.Serial;
import java.io.Serializable;

/**
 * One piece on the board: its color and whether it is the Master. Students and
 * Masters move identically; the type only matters for the win conditions.
 */
public record Piece(PlayerColor color, boolean master) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return (master ? "M" : "S") + color;
    }
}
