package deckduel.core.card;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Balatro-style row formation, like a poker hand made of nations and classes (approved by the
 * user on 2026-10-21; values provisional for balance). Only a row's best formation counts: its
 * {@code bonus} joins the Unit and pair points and the sum is multiplied by {@code multiplier}.
 * Units with disabled tags do not count toward a formation. Declared from weakest to strongest;
 * every nation formation has a class twin of equal value.
 */
public enum Formation {
    /** Three Units of one class. */
    COMPANY("Company", 10, 1),
    /** Three Units of one nation. */
    RETINUE("Retinue", 10, 1),
    /** Two Units each of two different classes. */
    TWO_COMPANIES("Two Companies", 15, 1),
    /** Two Units each of two different nations. */
    TWO_BANNERS("Two Banners", 15, 1),
    /** Two Units each of three different classes. */
    THREE_COMPANIES("Three Companies", 25, 1),
    /** Two Units each of three different nations. */
    THREE_BANNERS("Three Banners", 25, 1),
    /** Three Units of one class and two of another. */
    FULL_REGIMENT("Full Regiment", 20, 2),
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
        Formation[] all = values();
        for (int i = all.length - 1; i >= 0; i--) if (all[i].madeBy(nations, classes)) return all[i];
        return null;
    }

    /** Whether groups of these sizes (largest first) make this formation. */
    private boolean madeBy(List<Integer> nations, List<Integer> classes) {
        return switch (this) {
            case COMPANY -> at(classes, 0) >= 3;
            case RETINUE -> at(nations, 0) >= 3;
            case TWO_COMPANIES -> at(classes, 1) >= 2;
            case TWO_BANNERS -> at(nations, 1) >= 2;
            case THREE_COMPANIES -> at(classes, 2) >= 2;
            case THREE_BANNERS -> at(nations, 2) >= 2;
            case FULL_REGIMENT -> at(classes, 0) >= 3 && at(classes, 1) >= 2;
            case FULL_MUSTER -> at(nations, 0) >= 3 && at(nations, 1) >= 2;
            case REGIMENT -> at(classes, 0) >= 4;
            case ROYAL_LINE -> classes.size() >= Catalog.CLASSES;
            case BANNER_HOST -> at(nations, 0) >= 5;
        };
    }

    /** The {@code index}th largest group's size, or 0 when there are fewer groups. */
    private static int at(List<Integer> sizes, int index) { return index < sizes.size() ? sizes.get(index) : 0; }

    /** How many times each distinct value occurs, largest first. */
    private static List<Integer> sizes(Collection<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) counts.merge(value, 1, Integer::sum);
        return counts.values().stream().sorted((a, b) -> b - a).toList();
    }
}
