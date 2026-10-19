package deckduel.net;

import java.io.Serial;

/** C→S. Opens a hosted room; the server answers with {@link MatchCreated}. */
public record CreateMatchRequest() implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
