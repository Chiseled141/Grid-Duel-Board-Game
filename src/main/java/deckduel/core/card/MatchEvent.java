package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;

/** One public log line; the actor is null for round and match announcements. */
public record MatchEvent(Seat actor, String text) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
