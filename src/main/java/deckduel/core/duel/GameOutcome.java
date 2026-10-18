package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import deckduel.core.PlayerColor;

/**
 * Terminal match outcome. {@code winner} is null if and only if {@code reason} is DRAW.
 */
public record GameOutcome(PlayerColor winner, EndReason reason) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public GameOutcome {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason == EndReason.DRAW && winner != null) {
            throw new IllegalArgumentException("winner must be null on draw");
        }
        if (reason != EndReason.DRAW && winner == null) {
            throw new IllegalArgumentException("winner must not be null on " + reason);
        }
    }
}
