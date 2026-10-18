package deckduel.net;

import java.io.Serial;
import java.util.Objects;
import deckduel.core.duel.GameAction;

/**
 * C→S. Gameplay action request tagged with recipient-specific sequence and expected revision.
 */
public record ActionRequest(String matchId, long sequence, long expectedRevision,
                            GameAction action) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ActionRequest {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }
}
