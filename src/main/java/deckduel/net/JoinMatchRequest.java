package deckduel.net;

import java.io.Serial;

/** C→S. Joins the hosted room with the given code. */
public record JoinMatchRequest(String roomCode) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
