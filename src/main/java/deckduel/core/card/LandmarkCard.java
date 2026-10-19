package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * One of a player's two chosen Landmarks in their separate Landmark area. It persists for the
 * whole match; {@code sabotaged} suppresses its effect until the current round ends.
 */
public record LandmarkCard(CardInstance card, boolean sabotaged) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires a Landmark card. */
    public LandmarkCard {
        Objects.requireNonNull(card, "card");
        if (card.def().kind() != CardKind.LANDMARK) throw new IllegalArgumentException("Not a Landmark");
    }

    /** Whether its effect currently applies. */
    public boolean active() { return !sabotaged; }

    /** Shortcut to the printed definition. */
    public CardDef def() { return card.def(); }
}
