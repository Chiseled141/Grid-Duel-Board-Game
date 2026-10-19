package deckduel.core.card;

import java.io.Serial;

/** Gives up one of the ten round plays without playing, drawing or discarding a card. */
public record SkipTurn() implements Move {
    @Serial private static final long serialVersionUID = 1L;
}
