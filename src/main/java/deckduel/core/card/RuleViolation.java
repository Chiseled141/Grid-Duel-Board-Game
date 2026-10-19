package deckduel.core.card;

import deckduel.core.DeckDuelException;
import java.io.Serial;

/** A rejected action or deck; the message is safe to show to the player. */
public final class RuleViolation extends DeckDuelException {
    @Serial private static final long serialVersionUID = 1L;

    /** Creates a violation with a player-facing reason. */
    public RuleViolation(String message) { super(message); }
}
