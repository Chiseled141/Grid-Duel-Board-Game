package deckduel.core.card;

import java.io.Serial;

/** Completes Secret Edict without ending the player's turn. */
public record KeepEdictCard(long cardId) implements Move {
    @Serial private static final long serialVersionUID = 1L;
}
