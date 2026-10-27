package deckduel.net;

import deckduel.core.card.PlayerView;
import java.io.Serial;

/**
 * S→C. The recipient's complete view of their match plus session status. It is sent after every
 * accepted change and on reconnect, so the client never has to merge partial updates.
 * {@code game} counts rematches within the same session.
 * {@code closingRoundView} is the recipient's final accepted round state before bank/deal, or
 * null on ordinary updates. Its score and King counters are computed by the server.
 */
public record MatchUpdate(String matchId, int game, String yourName, String opponentName, boolean practice,
                          boolean opponentConnected, boolean opponentLeft, boolean rematchRequested,
                          boolean opponentWantsRematch, PlayerView view, long kingChoiceDeadlineMillis,
                          PlayerView closingRoundView) implements Message {
    @Serial private static final long serialVersionUID = 1L;

    /** Snapshot without a just-completed round (also used by replay-derived views). */
    public MatchUpdate(String matchId, int game, String yourName, String opponentName, boolean practice,
                       boolean opponentConnected, boolean opponentLeft, boolean rematchRequested,
                       boolean opponentWantsRematch, PlayerView view, long kingChoiceDeadlineMillis) {
        this(matchId, game, yourName, opponentName, practice, opponentConnected, opponentLeft, rematchRequested,
                opponentWantsRematch, view, kingChoiceDeadlineMillis, null);
    }

    /** Session snapshot without a live King deadline (for replay-derived views and fixtures). */
    public MatchUpdate(String matchId, int game, String yourName, String opponentName, boolean practice,
                       boolean opponentConnected, boolean opponentLeft, boolean rematchRequested,
                       boolean opponentWantsRematch, PlayerView view) {
        this(matchId, game, yourName, opponentName, practice, opponentConnected, opponentLeft, rematchRequested,
                opponentWantsRematch, view, 0);
    }
}
