package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;

/** One King's current flat contribution, added after the formation multiplier. */
public record KingBonus(King king, int points) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
