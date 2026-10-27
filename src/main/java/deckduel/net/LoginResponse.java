package deckduel.net;

import java.io.Serial;

/** Account result; when the account holds a match seat, a {@link MatchUpdate} follows it. */
public record LoginResponse(boolean ok, String errorText, UserProfile profile) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
