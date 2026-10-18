package deckduel.net;

import java.io.Serial;

/**
 * C→S. Offers or accepts a rematch after a finished game. When both players
 * have sent it, the server deals a fresh game and sends
 * {@link RematchAccept} followed by {@link MatchSync}.
 */
public record RematchRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
