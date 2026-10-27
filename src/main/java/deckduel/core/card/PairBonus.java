package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;

/** One scoring pair of active Units by row space, with its bonus. */
public record PairBonus(int slotA, int slotB, int points) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
