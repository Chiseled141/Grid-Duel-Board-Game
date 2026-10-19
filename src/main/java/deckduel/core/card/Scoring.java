package deckduel.core.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives a row's live score from the Units currently on it and the owner's active Landmarks
 * (§5.3–5.4). Nothing is banked here: removing, replacing or disabling a card, or sabotaging a
 * Landmark, changes the next calculation immediately.
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
     * for implementation (AGENTS.md §5.3, §5.6); added after pair bonuses and never multiplied.
     */
    public static final int ALL_NATIONS_BONUS = 15;

    private Scoring() { }

    /** The base combo bonus of two Units' printed tags: 0, 3 or 9. */
    public static int pairBonus(CardDef a, CardDef b) {
        boolean nation = a.nation().equals(b.nation()), type = a.unitClass().equals(b.unitClass());
        if (nation && type) return DOUBLE_MATCH;
        return nation || type ? SINGLE_MATCH : 0;
    }

    /** Scores a row on its own, as when its owner has no Landmarks. */
    public static ScoreBreakdown score(Row row) { return score(row, List.of()); }

    /** Scores one row with its owner's Landmarks; sabotaged Landmarks contribute nothing. */
    public static ScoreBreakdown score(Row row, List<LandmarkCard> landmarks) {
        List<Effect> active = landmarks.stream().filter(LandmarkCard::active).map(landmark -> landmark.def().effect()).toList();
        long unitPoints = 0;
        for (RowCard unit : row.cards()) unitPoints += printedPoints(unit, active);
        List<PairBonus> pairs = pairs(row.cards(), active);
        long comboPoints = pairs.stream().mapToLong(PairBonus::points).sum();
        List<String> effects = new ArrayList<>();
        if (allNations(row)) {
            comboPoints += ALL_NATIONS_BONUS;
            effects.add("All seven nations: +" + ALL_NATIONS_BONUS);
        }
        long bonus = 0;
        for (LandmarkCard landmark : landmarks) {
            String name = landmark.def().name();
            if (!landmark.active()) { effects.add(name + ": sabotaged this round"); continue; }
            if (landmark.def().effect() instanceof Effect.EmptySpaceBonus empty) {
                long gained = (long) empty.points() * row.emptySpaces();
                bonus += gained;
                effects.add(name + ": +" + gained);
            } else effects.add(name + ": " + summary(landmark.def().effect()));
        }
        return new ScoreBreakdown(unitPoints, comboPoints, bonus, pairs, effects);
    }

    /** Whether every nation is represented by an active nationality tag on the row. */
    public static boolean allNations(Row row) {
        Set<String> nations = new HashSet<>();
        for (RowCard unit : row.cards()) if (!unit.has(Suppression.TAGS)) nations.add(unit.def().nation());
        return nations.size() >= NATIONS;
    }

    /** A Unit's printed points with any Landmark increases; zero while its points are disabled. */
    private static long printedPoints(RowCard unit, List<Effect> active) {
        if (unit.has(Suppression.POINTS)) return 0;
        long points = unit.def().points();
        for (Effect effect : active)
            if (effect instanceof Effect.WeakUnitBonus weak && unit.def().points() <= weak.maxPrinted()) points += weak.points();
        return points;
    }

    /** Every scoring pair once, each raised by every active Landmark whose shared tag the pair has. */
    private static List<PairBonus> pairs(List<RowCard> units, List<Effect> active) {
        List<PairBonus> pairs = new ArrayList<>();
        for (int i = 0; i < units.size(); i++)
            for (int j = i + 1; j < units.size(); j++) {
                RowCard a = units.get(i), b = units.get(j);
                if (a.has(Suppression.TAGS) || b.has(Suppression.TAGS)) continue;
                int points = pairBonus(a.def(), b.def());
                if (points == 0) continue;
                for (Effect effect : active)
                    if (effect instanceof Effect.SharedTagBonus shared && shares(a.def(), b.def(), shared.type())) points += shared.points();
                pairs.add(new PairBonus(a.slot(), b.slot(), points));
            }
        return pairs;
    }

    private static boolean shares(CardDef a, CardDef b, TagType type) {
        return type == TagType.REGION ? a.nation().equals(b.nation()) : a.unitClass().equals(b.unitClass());
    }

    /** A short line for the score breakdown; effects that are not points say what they do. */
    private static String summary(Effect effect) {
        if (effect instanceof Effect.SharedTagBonus shared)
            return "+" + shared.points() + " per " + (shared.type() == TagType.REGION ? "nation" : "class") + " pair";
        if (effect instanceof Effect.WeakUnitBonus weak) return "+" + weak.points() + " to Units printing ≤" + weak.maxPrinted();
        if (effect instanceof Effect.FirstSpellDraw draw) return "first Spell draws " + draw.cards();
        if (effect instanceof Effect.DeckTopView) return "you see your deck's top card";
        return effect.text();
    }
}
