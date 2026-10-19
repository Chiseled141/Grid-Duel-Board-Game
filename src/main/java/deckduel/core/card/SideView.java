package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Public information about one player: Unit row, Landmark area, live score, counters and banked
 * match total. While Landmarks are being chosen the opponent's picks stay hidden ({@code ready}
 * only says whether they have chosen).
 */
public record SideView(Row row, List<LandmarkCard> landmarks, ScoreBreakdown score, int playsUsed, int drawsUsed,
                       int handCount, int deckCount, int discardCount, long matchTotal, boolean ready) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires the row and score and copies the Landmarks. */
    public SideView {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(score, "score");
        landmarks = List.copyOf(landmarks);
    }

    /** Card plays left this round. */
    public int playsRemaining() { return Rules.PLAYS_PER_ROUND - playsUsed; }

    /** Manual draws left this round. */
    public int drawsRemaining() { return Rules.MANUAL_DRAWS - drawsUsed; }
}
