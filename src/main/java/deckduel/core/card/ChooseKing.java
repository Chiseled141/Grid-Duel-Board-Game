package deckduel.core.card;

import java.io.Serial;

/** Confirms a King from this round's shared offer, independently of the normal turn. */
public record ChooseKing(King king) implements Move {
    @Serial private static final long serialVersionUID = 1L;
}
