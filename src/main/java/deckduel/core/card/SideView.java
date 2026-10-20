package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Public information about one player: Unit row, live score, counters and banked match total. */
public record SideView(Row row, ScoreBreakdown score, int playsUsed, int drawsUsed, int handCount, int deckCount,
                       int discardCount, long matchTotal) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires the row and score. */
    public SideView {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(score, "score");
    }

    /** Card plays left this round. */
    public int playsRemaining() { return Rules.PLAYS_PER_ROUND - playsUsed; }

    /** Manual draws left this round. */
    public int drawsRemaining() { return Rules.MANUAL_DRAWS - drawsUsed; }
}
