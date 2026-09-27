package onitama.net;

import java.io.Serial;
import java.util.List;

/**
 * S→C. The top players by Elo, ordered best first.
 */
public record LeaderboardResponse(List<UserProfile> topPlayers) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
