package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * One printed card. A Unit has 1–6 printed points, exactly one nation and exactly one class, and
 * no ability. Landmarks and Spells are neutral: no nation, class or printed points, and exactly
 * one effect of the matching family.
 */
public record CardDef(String id, String name, CardKind kind, int points,
                      List<String> regions, List<String> classes, Effect effect) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the card against the approved card-family rules. */
    public CardDef {
        if (id == null || id.isBlank() || name == null || name.isBlank())
            throw new IllegalArgumentException("Card needs an id and a name");
        Objects.requireNonNull(kind, "kind");
        regions = List.copyOf(regions);
        classes = List.copyOf(classes);
        if (kind == CardKind.UNIT) {
            if (points < 1 || points > 6) throw new IllegalArgumentException(id + ": Unit points must be 1–6");
            if (regions.size() != 1 || classes.size() != 1)
                throw new IllegalArgumentException(id + ": a Unit has exactly one nation and one class");
            if (effect != null) throw new IllegalArgumentException(id + ": Units have no ability");
        } else {
            if (points != 0) throw new IllegalArgumentException(id + ": only Units have printed points");
            if (!regions.isEmpty() || !classes.isEmpty()) throw new IllegalArgumentException(id + ": Landmarks and Spells are neutral");
            if (effect == null || effect.ongoing() != (kind == CardKind.LANDMARK))
                throw new IllegalArgumentException(id + ": " + kind + " needs one matching effect");
        }
    }

    /** A Unit's nation. */
    public String nation() { return regions.get(0); }

    /** A Unit's class. */
    public String unitClass() { return classes.get(0); }

    /** Whether the card occupies a Unit-row space. */
    public boolean occupiesSpace() { return kind == CardKind.UNIT; }

    /** Player-facing summary of what the card does. */
    public String text() {
        if (kind == CardKind.UNIT) return points + " points · " + nation() + " · " + unitClass();
        return effect.text();
    }
}
