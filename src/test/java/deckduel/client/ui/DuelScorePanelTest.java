package deckduel.client.ui;

import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelScorePanelTest {
    private DuelScorePanel.Display score(long score, int round) {
        return new DuelScorePanel.Display(score, 18, 9, 27, 0, 7, round, "No active multiplier");
    }

    @Test void acceptedScoreIsImmediatelyAccessibleEvenDuringFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DuelScorePanel();
            panel.update(score(15, 1), false);
            assertFalse(panel.isAnimating());
            panel.update(score(36, 1), false);
            assertTrue(panel.isAnimating());
            assertTrue(panel.getAccessibleContext().getAccessibleDescription().contains("Your score 36."));
            panel.update(score(12, 1), false);
            assertTrue(panel.getAccessibleContext().getAccessibleDescription().contains("Your score 12."));
            panel.stopFeedback();
        });
    }

    @Test void lifecycleAndReducedMotionClearFeedbackAndOldMatchData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DuelScorePanel();
            panel.update(score(15, 1), false);
            panel.update(score(36, 1), false);
            panel.update(score(36, 1), true);
            assertFalse(panel.isAnimating());
            panel.update(score(40, 1), false);
            panel.update(score(0, 2), false);
            assertFalse(panel.isAnimating(), "A new round is not a negative scoring event");
            panel.update(score(10, 2), false);
            panel.removeNotify();
            assertFalse(panel.isAnimating());
            panel.reset();
            assertNull(panel.getToolTipText());
            assertEquals("Waiting for match scores", panel.getAccessibleContext().getAccessibleDescription());
        });
    }

    @Test void breakdownDescribesScopedEffectsWithoutRecalculatingTheServerTotal() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DuelScorePanel();
            panel.update(new DuelScorePanel.Display(63, 18, 9, 27, 40, 3, 2, "Combo points ×2"), true);
            assertTrue(panel.getToolTipText().contains("Your score 63."));
            assertTrue(panel.getToolTipText().contains("Combo points ×2"));
            for (int width : new int[]{196, 260, 360}) {
                panel.setSize(width, 620);
                var image = new BufferedImage(width, 620, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
            }
            panel.update(new DuelScorePanel.Display(Long.MAX_VALUE, 0, 9, 27, 0, 0, 3,
                    "A long effect description that remains available in full to assistive technology"), true);
            assertTrue(panel.getToolTipText().contains(String.valueOf(Long.MAX_VALUE)));
        });
    }
}
