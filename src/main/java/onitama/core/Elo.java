package onitama.core;

/**
 * Standard two-player Elo rating update, K factor 32, zero-sum. Implemented as
 * a pure function in {@code core} so it can be unit-tested and reused by the
 * server without touching the database.
 */
public final class Elo {

    /** The K factor: how strongly a single game moves a rating. */
    public static final int K_FACTOR = 32;

    /** The two ratings after a game, keyed by color. */
    public record Result(int blue, int red) {
    }

    private Elo() {
    }

    /**
     * Computes the new ratings after a finished game.
     *
     * @param blueRating rating of Blue before the game
     * @param redRating  rating of Red before the game
     * @param winner     the winning color, or {@code null} for a draw
     * @return both new ratings, rounded to the nearest integer; the update is
     *         exactly zero-sum (one rounded delta is negated for the opponent,
     *         so rounding can never drift the rating sum)
     */
    public static Result update(int blueRating, int redRating, PlayerColor winner) {
        double expectedBlue = expectedScore(blueRating, redRating);
        double scoreBlue = winner == null ? 0.5 : winner == PlayerColor.BLUE ? 1.0 : 0.0;
        // One rounded delta, negated for Red. Rounding both deltas
        // independently could be off by one (Math.round(-15.5) == -15, not
        // -16), which would create or destroy a rating point.
        int delta = (int) Math.round(K_FACTOR * (scoreBlue - expectedBlue));
        return new Result(blueRating + delta, redRating - delta);
    }

    /** The expected score of Blue against Red: 0.5 for equal ratings. */
    private static double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
    }
}
