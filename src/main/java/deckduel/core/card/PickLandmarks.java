package deckduel.core.card;

import java.io.Serial;
import java.util.List;

/** The two Landmarks a player keeps from their five offers, by card instance id. */
public record PickLandmarks(List<Long> cardIds) implements Move {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies the ids. */
    public PickLandmarks { cardIds = List.copyOf(cardIds); }
}
