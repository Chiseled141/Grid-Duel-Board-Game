package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import deckduel.core.Square;

/**
 * Planned relocation of a piece to a destination square.
 */
public record Relocation(int pieceId, Square to) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Relocation {
        Objects.requireNonNull(to, "to must not be null");
    }
}
