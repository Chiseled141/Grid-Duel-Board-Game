package onitama.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UT06 — Elo update math with K = 32: winner/loser/draw for equal and unequal
 * ratings, and the zero-sum property (the two new ratings always sum to the
 * two old ones).
 */
class EloTest {

    @Test
    void equalRatingsBlueWins() {
        Elo.Result result = Elo.update(1000, 1000, PlayerColor.BLUE);
        assertEquals(1016, result.blue());
        assertEquals(984, result.red());
    }

    @Test
    void equalRatingsRedWins() {
        Elo.Result result = Elo.update(1000, 1000, PlayerColor.RED);
        assertEquals(984, result.blue());
        assertEquals(1016, result.red());
    }

    @Test
    void equalRatingsDrawChangesNothing() {
        Elo.Result result = Elo.update(1000, 1000, null);
        assertEquals(1000, result.blue());
        assertEquals(1000, result.red());
    }

    @Test
    void strongerPlayerWinsAsExpected() {
        // Expected score of 1200 vs 1000 is ~0.7597; a win adds ~7.69 points.
        Elo.Result result = Elo.update(1200, 1000, PlayerColor.BLUE);
        assertEquals(1208, result.blue());
        assertEquals(992, result.red());
    }

    @Test
    void strongerPlayerOnlyDraws() {
        // A draw with expected 0.7597 costs the stronger player ~8.31 points.
        Elo.Result result = Elo.update(1200, 1000, null);
        assertEquals(1192, result.blue());
        assertEquals(1008, result.red());
    }

    @Test
    void updateIsZeroSum() {
        int[][] pairs = {{1000, 1000}, {1200, 1000}, {1400, 800}, {999, 1001}};
        for (int[] pair : pairs) {
            for (PlayerColor winner : new PlayerColor[]{PlayerColor.BLUE, PlayerColor.RED, null}) {
                Elo.Result result = Elo.update(pair[0], pair[1], winner);
                assertEquals(pair[0] + pair[1], result.blue() + result.red(),
                        "points are conserved for " + pair[0] + "/" + pair[1] + " winner=" + winner);
            }
        }
    }
}
