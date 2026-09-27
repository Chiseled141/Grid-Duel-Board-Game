package onitama.net;

import java.io.Serial;

/**
 * C→S. Requests the leaderboard (top players by Elo).
 */
public record LeaderboardRequest() implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
