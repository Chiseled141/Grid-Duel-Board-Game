package deckduel.core.card;

import java.io.Serial;

/** The card a player keeps from those their Reconnaissance revealed. */
public record KeepCard(long cardId) implements Move {
    @Serial private static final long serialVersionUID = 1L;
}
