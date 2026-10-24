package deckduel.core.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives a row's live score from the Units currently on it (§5.3). Nothing is banked here:
 * removing, replacing or disabling a card changes the next calculation immediately.
 */
public final class Scoring {
    /** Pair bonus for a shared nation only, or a shared class only. */
    public static final int SINGLE_MATCH = 3;
    /** Pair bonus when a nation and a class are both shared; replaces the two single bonuses. */
    public static final int DOUBLE_MATCH = 9;
    /** Nations in the Unit library. */
    public static final int NATIONS = 7;
    /**
     * Base combo bonus while all seven nations are active on the row. PROVISIONAL value accepted
     * for implementation (AGENTS.md §5.3, §5.6); added after the formation multiplier, never multiplied.
     */
    public static final int ALL_NATIONS_BONUS = 15;

    private Scoring() { }

    /** The base combo bonus of two Units' printed tags: 0, 3 or 9. */
    public static int pairBonus(CardDef a, CardDef b) {
        boolean nation = a.nation().equals(b.nation()), type = a.unitClass().equals(b.unitClass());
        if (nation && type) return DOUBLE_MATCH;
        return nation || type ? SINGLE_MATCH : 0;
    }

    /** Scores one row: Unit points, pair combos, its best formation and the seven-nation bonus. */
    public static ScoreBreakdown score(Row row) {
        long unitPoints = 0;
        for (RowCard unit : row.cards()) if (!unit.has(Suppression.POINTS)) unitPoints += unit.def().points();
        List<PairBonus> pairs = pairs(row.cards());
        long comboPoints = pairs.stream().mapToLong(PairBonus::points).sum();
        List<String> effects = new ArrayList<>();
        Formation formation = Formation.best(row);
        if (formation != null)
            effects.add(formation.title() + ": +" + formation.bonus() + " ×" + formation.multiplier());
        long bonus = 0;
        if (allNations(row)) {
            bonus = ALL_NATIONS_BONUS;
            effects.add("All seven nations: +" + ALL_NATIONS_BONUS);
        }
        return new ScoreBreakdown(unitPoints, comboPoints, formation, bonus, pairs, effects);
    }

    /** Scores the current row with public ownership and accepted round counters. */
    public static ScoreBreakdown score(Row row, KingStatus kings) {
        ScoreBreakdown base = score(row);
        return new ScoreBreakdown(base.units(), base.combos(), base.formation(), base.bonus(), base.pairs(),
                base.effects(), KingScoring.bonuses(row, kings));
    }

    /** Whether every nation is represented by an active nationality tag on the row. */
    public static boolean allNations(Row row) {
        Set<String> nations = new HashSet<>();
        for (RowCard unit : row.cards()) if (!unit.has(Suppression.TAGS)) nations.add(unit.def().nation());
        return nations.size() >= NATIONS;
    }

    /** Every scoring pair of Units with active tags, once each. */
    private static List<PairBonus> pairs(List<RowCard> units) {
        List<PairBonus> pairs = new ArrayList<>();
        for (int i = 0; i < units.size(); i++)
            for (int j = i + 1; j < units.size(); j++) {
                RowCard a = units.get(i), b = units.get(j);
                if (a.has(Suppression.TAGS) || b.has(Suppression.TAGS)) continue;
                int points = pairBonus(a.def(), b.def());
                if (points > 0) pairs.add(new PairBonus(a.slot(), b.slot(), points));
            }
        return pairs;
    }
}
