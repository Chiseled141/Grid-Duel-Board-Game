package onitama.net;

import java.io.Serial;
import java.util.Objects;
import onitama.core.duel.PlayerView;

/**
 * S→C. Authoritative match synchronization conveying recipient-specific view, paused state, and sequence.
 */
public record MatchSync(String matchId, String roomCode, PlayerView view,
                         boolean paused, long nextSequence) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public MatchSync {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Objects.requireNonNull(roomCode, "roomCode must not be null");
        Objects.requireNonNull(view, "view must not be null");
    }
}
