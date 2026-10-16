package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import onitama.core.PlayerColor;

/**
 * A piece on the 7x7 board. Pieces have a stable ID and a color, with identical abilities.
 */
public record Piece(int id, PlayerColor color) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Piece {
        Objects.requireNonNull(color, "color must not be null");
        int firstId = color == PlayerColor.BLUE ? 0 : 6;
        if (id < firstId || id >= firstId + 6) {
            throw new IllegalArgumentException("piece id does not belong to " + color + ": " + id);
        }
    }

    @Override
    public String toString() {
        return (color == PlayerColor.BLUE ? "B" : "R") + id;
    }
}
