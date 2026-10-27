package deckduel.net;

import java.io.Serial;

/** C→S. Asks for a rematch after a finished match; it starts once both players ask. */
public record RematchRequest() implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
