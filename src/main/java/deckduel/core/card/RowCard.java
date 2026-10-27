package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A Unit in a row space, with any suppressions applied to it this round. */
public record RowCard(int slot, CardInstance card, List<Suppression> suppressed) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the space and copies the suppressions. */
    public RowCard {
        if (slot < 0 || slot >= Row.SIZE) throw new IllegalArgumentException("Invalid row space");
        Objects.requireNonNull(card, "card");
        if (!card.def().occupiesSpace()) throw new IllegalArgumentException("Only Units occupy row spaces");
        suppressed = List.copyOf(suppressed);
    }

    /** A freshly played card with nothing suppressed. */
    public static RowCard placed(int slot, CardInstance card) { return new RowCard(slot, card, List.of()); }

    /** Whether the given part of this card is suppressed. */
    public boolean has(Suppression suppression) { return suppressed.contains(suppression); }

    /** Returns this card with one more suppression. */
    public RowCard with(Suppression suppression) {
        if (has(suppression)) return this;
        List<Suppression> next = new ArrayList<>(suppressed);
        next.add(suppression);
        return new RowCard(slot, card, next);
    }

    /** Returns this card with every suppression cleared. */
    public RowCard cleared() { return new RowCard(slot, card, List.of()); }

    /** Shortcut to the printed definition. */
    public CardDef def() { return card.def(); }
}
