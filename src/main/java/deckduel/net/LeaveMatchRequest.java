package deckduel.net;

import java.io.Serial;

/** C→S. Leaves the current match; during play this forfeits it. */
public record LeaveMatchRequest() implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
