package onitama.net;

import java.io.Serial;

/**
 * C→S. Opens a new lobby; the server answers with a 5-character room code
 * via {@link MatchStart} once an opponent joins.
 */
public record CreateMatchRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
