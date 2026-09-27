package onitama.net;

import java.io.Serial;

/**
 * S→C. Acknowledges {@link CreateMatchRequest} and carries the room code of
 * the freshly opened lobby, which the host shares with the opponent.
 */
public record MatchCreated(String roomCode) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
