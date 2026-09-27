package onitama.net;

import java.io.Serial;

/**
 * C→S. Requests the current list of open matches for the lobby browser.
 */
public record ListMatchesRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
