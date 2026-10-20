package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * A row's live score: printed Unit points, pair combo points, the row's best {@link Formation}
 * (null when it has none) and a flat {@code bonus} added after the formation's multiplier. The
 * pairs and effect lines explain the result.
 */
public record ScoreBreakdown(long units, long combos, Formation formation, long bonus, List<PairBonus> pairs,
                             List<String> effects) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies the explanation lists. */
    public ScoreBreakdown {
        pairs = List.copyOf(pairs);
        effects = List.copyOf(effects);
    }

    /** The formation's bonus points, or 0 without one. */
    public long formationBonus() { return formation == null ? 0 : formation.bonus(); }

    /** The formation's multiplier, or 1 without one. */
    public long multiplier() { return formation == null ? 1 : formation.multiplier(); }

    /** The row's total score: (units + combos + formation bonus) × multiplier + flat bonus. */
    public long total() { return (units + combos + formationBonus()) * multiplier() + bonus; }
}
