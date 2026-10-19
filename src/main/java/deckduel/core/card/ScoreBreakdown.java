package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * A row's live score: printed Unit points (after Unit multipliers), combo points (after combo
 * multipliers) and flat Landmark bonuses. The pairs and effect lines explain the result.
 */
public record ScoreBreakdown(long units, long combos, long bonus, List<PairBonus> pairs,
                             List<String> effects) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies the explanation lists. */
    public ScoreBreakdown {
        pairs = List.copyOf(pairs);
        effects = List.copyOf(effects);
    }

    /** The row's total score. */
    public long total() { return units + combos + bonus; }
}
