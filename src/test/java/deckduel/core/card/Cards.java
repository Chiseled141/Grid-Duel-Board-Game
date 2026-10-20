package deckduel.core.card;

import java.util.ArrayList;
import java.util.List;

/** Test-only card fixtures; these are not catalog content. */
final class Cards {
    static final List<String> NATIONS = List.of("N1", "N2", "N3", "N4", "N5", "N6", "N7");
    static final List<String> CLASSES = List.of("Footmen", "Pikemen", "Bowmen", "Riders", "Knights");
    private static long nextId = 1;
    private Cards() { }

    static CardDef unit(String id, int points, String nation, String unitClass) {
        return new CardDef(id, id, CardKind.UNIT, points, List.of(nation), List.of(unitClass), null);
    }
    static CardDef spell(String id, Effect effect) {
        return new CardDef(id, id, CardKind.SPELL, 0, List.of(), List.of(), effect);
    }
    static CardInstance copy(CardDef def) { return new CardInstance(nextId++, def); }

    /** A row with the given cards in spaces 0, 1, 2, … */
    static Row row(CardDef... defs) {
        Row row = Row.EMPTY;
        for (int i = 0; i < defs.length; i++) row = row.place(i, copy(defs[i]));
        return row;
    }

    /** The test Unit grid: one Unit of each class in each nation, printing 1–5 by class. */
    static List<CardDef> units() {
        List<CardDef> units = new ArrayList<>();
        for (String nation : NATIONS)
            for (int c = 0; c < CLASSES.size(); c++) units.add(unit(nation + "-" + CLASSES.get(c), c + 1, nation, CLASSES.get(c)));
        return units;
    }

    /** {@code count} copies of a card, as a deck. */
    static List<CardDef> copies(CardDef card, int count) { return java.util.Collections.nCopies(count, card); }

    /** A seed whose match is started by {@code starter}. */
    static long seedStartedBy(Seat starter) {
        for (long seed = 1; ; seed++) if ((new java.util.Random(seed).nextBoolean() ? Seat.FIRST : Seat.SECOND) == starter) return seed;
    }

    /** Opens a match; round one is dealt at once. */
    static MatchState started(List<CardDef> first, List<CardDef> second, long seed) {
        return MatchState.start(first, second, seed);
    }
}
