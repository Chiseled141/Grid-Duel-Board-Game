package deckduel.net;

import java.io.Serial;

/** S→C. A move was rejected and changed nothing; the reason is safe to show. */
public record ActionRejected(long requestId, String reason) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
