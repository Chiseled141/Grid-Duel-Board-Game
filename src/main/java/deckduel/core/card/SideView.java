package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Public information about one player: Unit row, live score, counters and banked match total. */
public record SideView(Row row, ScoreBreakdown score, int playsUsed, int drawsUsed, int handCount, int deckCount,
                       int discardCount, long matchTotal, KingStatus kings) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires the row and score. */
    public SideView {
        Objects.requireNonNull(kings, "kings");
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(score, "score");
    }

    /** Public board fixture without Kings. */
    public SideView(Row row, ScoreBreakdown score, int playsUsed, int drawsUsed, int handCount, int deckCount,
                    int discardCount, long matchTotal) {
        this(row, score, playsUsed, drawsUsed, handCount, deckCount, discardCount, matchTotal, KingStatus.EMPTY);
    }

    /** Card plays left this round. */
    public int playsRemaining() { return Rules.PLAYS_PER_ROUND - playsUsed; }

    /** Card swaps left this round; the record keeps its original draw field name for stored replays. */
    public int drawsRemaining() { return Rules.SWAPS_PER_ROUND - drawsUsed; }
}
