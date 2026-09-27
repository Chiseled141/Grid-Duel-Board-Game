package onitama.net;

import java.io.Serial;

/**
 * C→S. Asks to join the open match with the given room code.
 */
public record JoinMatchRequest(String roomCode) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
