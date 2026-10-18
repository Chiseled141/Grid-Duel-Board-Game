package deckduel.net;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import deckduel.core.duel.PublicEvent;

/**
 * S→C. Broadcasted update carrying the updated match snapshot and any public events.
 */
public record GameUpdate(MatchSync current, List<PublicEvent> events) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public GameUpdate {
        Objects.requireNonNull(current, "current must not be null");
        events = events == null ? List.of() : List.copyOf(events);
    }
}
