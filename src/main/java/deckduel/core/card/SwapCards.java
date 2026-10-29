package deckduel.core.card;

import java.io.Serial;
import java.util.List;

/**
 * A swap: discard the chosen hand cards, then draw as many replacements from the player's own
 * deck, without using or ending the turn. Each swapped card spends one of the round's
 * {@link Rules#SWAPS_PER_ROUND}.
 */
public record SwapCards(List<Long> cardIds) implements Move {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies the chosen card ids. */
    public SwapCards { cardIds = List.copyOf(cardIds); }
}
