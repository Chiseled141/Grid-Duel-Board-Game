package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import org.junit.jupiter.api.Test;

class ScoreRailTest {
    private static ScoreRail.Display score(long score, int round) {
        return new ScoreRail.Display(score, 18, 9, 27, 0, 7, round);
    }

    @Test void acceptedScoreIsImmediatelyAccessibleEvenDuringFeedback() throws Exception {
        FxTesting.run(() -> {
            var rail = new ScoreRail();
            rail.update(score(15, 1), false, 0);
            assertFalse(rail.isAnimating());
            rail.update(score(36, 1), false, 0);
            assertTrue(rail.isAnimating());
            assertTrue(rail.description().contains("Your score 36."));
            rail.update(score(12, 1), false, 0);
            assertTrue(rail.description().contains("Your score 12."));
            rail.stopFeedback();
        });
    }

    @Test void lifecycleAndReducedMotionClearFeedbackAndOldMatchData() throws Exception {
        FxTesting.run(() -> {
            var rail = new ScoreRail();
            rail.update(score(15, 1), false, 0);
            rail.update(score(36, 1), false, 0);
            rail.update(score(36, 1), true, 0);
            assertFalse(rail.isAnimating());
            rail.update(score(40, 1), false, 0);
            rail.update(score(0, 2), false, 0);
            assertFalse(rail.isAnimating(), "A new round is not a negative scoring event");
            rail.reset();
            assertEquals(ScoreRail.WAITING, rail.description());
        });
    }

    @Test void breakdownDisplaysServerTotalsAndCountersAtDifferentSizes() throws Exception {
        FxTesting.run(() -> {
            var rail = new ScoreRail();
            new Scene(rail);
            rail.update(new ScoreRail.Display(63, 18, 9, 27, 40, 3, 2), true, 0);
            assertTrue(rail.description().contains("Your score 63."));
            assertTrue(rail.description().contains("Unit points 9, combo points 27."));
            assertTrue(rail.description().contains("Plays remaining 3. Match total 40"));
            for (int width : new int[]{196, 260, 360}) {
                rail.resize(width, 550);
                rail.layout();
                assertNotNull(rail.snapshot(null, null), "the rail draws at width " + width);
            }
            rail.update(new ScoreRail.Display(Long.MAX_VALUE, 0, 9, 27, 0, 0, 3), true, 0);
            assertTrue(rail.description().contains(String.valueOf(Long.MAX_VALUE)));
            assertThrows(IllegalArgumentException.class, () -> new ScoreRail.Display(0, 0, 0, 0, 0, 11, 1));
        });
    }
}
