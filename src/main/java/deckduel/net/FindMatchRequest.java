package deckduel.net;

import java.io.Serial;

/** C→S. Joins the automatic match queue, or cancels a pending search. The server supplies every deck. */
public record FindMatchRequest(boolean searching) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
