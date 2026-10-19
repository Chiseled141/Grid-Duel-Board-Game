package deckduel.core.card;

import java.io.Serial;

/** A manual draw: one card from the player's own deck, without using or ending the turn. */
public record DrawCard() implements Move {
    @Serial private static final long serialVersionUID = 1L;
}
