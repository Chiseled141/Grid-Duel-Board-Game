package onitama.net;

import java.io.Serial;
import java.io.Serializable;

/**
 * One entry of the lobby browser: an open match and the user who created it.
 */
public record MatchSummary(String roomCode, String hostUsername) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
