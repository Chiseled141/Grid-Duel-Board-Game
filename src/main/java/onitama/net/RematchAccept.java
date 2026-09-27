package onitama.net;

import java.io.Serial;

/**
 * S→C. Tells a player that the opponent has offered or accepted a rematch.
 * The new game itself is announced with {@link MatchStart}.
 */
public record RematchAccept() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
