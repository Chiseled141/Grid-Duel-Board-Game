package deckduel.net;

import java.io.Serial;
import java.util.Objects;

/**
 * S→C. Response to an ActionRequest carrying acceptance/rejection receipt and current snapshot.
 */
public record ActionReply(String matchId, long sequence, ActionReceipt receipt,
                          MatchSync current) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ActionReply {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Objects.requireNonNull(receipt, "receipt must not be null");
        Objects.requireNonNull(current, "current must not be null");
    }
}
