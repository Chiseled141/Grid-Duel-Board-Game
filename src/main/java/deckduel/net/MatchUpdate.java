package deckduel.net;

import deckduel.core.card.PlayerView;
import java.io.Serial;

/**
 * S→C. The recipient's complete view of their match plus session status. It is sent after every
 * accepted change and on reconnect, so the client never has to merge partial updates.
 * {@code game} counts rematches within the same session.
 */
public record MatchUpdate(String matchId, int game, String yourName, String opponentName, boolean practice,
                          boolean opponentConnected, boolean opponentLeft, boolean rematchRequested,
                          boolean opponentWantsRematch, PlayerView view) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
