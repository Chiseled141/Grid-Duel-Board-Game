package deckduel.net;

import java.io.Serial;

/** C→S. Join the automatic match queue, or cancel a pending search. */
public record FindMatchRequest(boolean searching) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
