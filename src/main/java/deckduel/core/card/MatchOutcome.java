package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;

/** How a finished match ended; a null winner is a draw on equal match totals. */
public record MatchOutcome(Seat winner, boolean forfeit) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates that a forfeit always has a winner. */
    public MatchOutcome {
        if (forfeit && winner == null) throw new IllegalArgumentException("A forfeit has a winner");
    }
}
