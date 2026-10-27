package deckduel.net;

import java.io.Serial;

/**
 * C→S. Closes the room this connection is hosting, so nobody can join it any more. Sent when an
 * update must be installed before another match; the server answers with the open-room list.
 */
public record CancelRoomRequest() implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
