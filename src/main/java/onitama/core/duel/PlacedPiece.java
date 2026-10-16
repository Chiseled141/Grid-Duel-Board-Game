package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import onitama.core.Square;

/**
 * An occupied board position associating a piece with its coordinate square.
 */
public record PlacedPiece(Piece piece, Square square) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public PlacedPiece {
        Objects.requireNonNull(piece, "piece must not be null");
        Objects.requireNonNull(square, "square must not be null");
        if (!Board.isInside(square)) {
            throw new IllegalArgumentException("piece outside board: " + square);
        }
    }
}
