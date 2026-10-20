package deckduel.core.card;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Balatro-style row formation, like a poker hand made of nations and classes (approved by the
 * user on 2026-10-21; values provisional for balance). Only a row's best formation counts: its
 * {@code bonus} joins the Unit and pair points and the sum is multiplied by {@code multiplier}.
 * Units with disabled tags do not count toward a formation. Declared from weakest to strongest.
 */
public enum Formation {
    /** Three Units of one class. */
    COMPANY("Company", 10, 1),
    /** Three Units of one nation. */
    RETINUE("Retinue", 10, 1),
    /** Two Units each of two different nations. */
    TWO_BANNERS("Two Banners", 15, 1),
    /** Three Units of one nation and two of another. */
    FULL_MUSTER("Full Muster", 20, 2),
    /** Four Units of one class. */
    REGIMENT("Regiment", 25, 2),
    /** One Unit of each of the five classes. */
    ROYAL_LINE("Royal Line", 30, 2),
    /** All five Units of one nation. */
    BANNER_HOST("Banner Host", 40, 3);

    private final String title;
    private final int bonus, multiplier;

    Formation(String title, int bonus, int multiplier) {
        this.title = title;
        this.bonus = bonus;
        this.multiplier = multiplier;
    }

    /** Player-facing name. */
    public String title() { return title; }
    /** Points added before the multiplier. */
    public int bonus() { return bonus; }
    /** What the row's points are multiplied by. */
    public int multiplier() { return multiplier; }

    /** The strongest formation the row's tagged Units make, or null when they make none. */
    public static Formation best(Row row) {
        List<RowCard> tagged = row.cards().stream().filter(unit -> !unit.has(Suppression.TAGS)).toList();
        List<Integer> nations = sizes(tagged.stream().map(unit -> unit.def().nation()).toList());
        List<Integer> classes = sizes(tagged.stream().map(unit -> unit.def().unitClass()).toList());
        int topNation = nations.isEmpty() ? 0 : nations.get(0), nextNation = nations.size() < 2 ? 0 : nations.get(1);
        int topClass = classes.isEmpty() ? 0 : classes.get(0);
        if (topNation >= 5) return BANNER_HOST;
        if (classes.size() >= Catalog.CLASSES) return ROYAL_LINE;
        if (topClass >= 4) return REGIMENT;
        if (topNation >= 3 && nextNation >= 2) return FULL_MUSTER;
        if (topNation >= 2 && nextNation >= 2) return TWO_BANNERS;
        if (topNation >= 3) return RETINUE;
        if (topClass >= 3) return COMPANY;
        return null;
    }

    /** How many times each distinct value occurs, largest first. */
    private static List<Integer> sizes(Collection<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) counts.merge(value, 1, Integer::sum);
        return counts.values().stream().sorted((a, b) -> b - a).toList();
    }
}
