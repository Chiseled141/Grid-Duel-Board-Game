package deckduel.net;

import java.io.Serial;

/** Account result; the shell has no match state or reconnect token. */
public record LoginResponse(boolean ok, String errorText, UserProfile profile) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
