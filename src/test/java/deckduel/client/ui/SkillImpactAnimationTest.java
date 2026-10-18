package deckduel.client.ui;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import deckduel.core.PlayerColor;
import deckduel.core.Square;
import deckduel.core.duel.SkillCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies visible skill effects, viewer rotation, expiry and accessibility cleanup. */
class SkillImpactAnimationTest {
    private int[] frame(BoardPanel board) {
        var image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        board.paint(graphics); graphics.dispose();
        return image.getRGB(0, 0, 500, 500, null, 0, 500);
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void acceptedEffectsRenderAndClearForBothViewers(PlayerColor viewer) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clock = new AtomicLong();
            var board = new BoardPanel(clock::get);
            board.setSize(500, 500);
            board.setPieces(List.of(), viewer);
            try {
                var baseline = frame(board);
                var destination = new Square(1, 2);
                for (var category : SkillCategory.values()) {
                    for (String skillId : List.of("strike", "pierce", "crossfire")) {
                        clock.set(0);
                        board.playSkillImpact(skillId, viewer, category, List.of(new BoardPanel.MovingPiece(1,
                                new Square(1, 0), destination, viewer)), List.of(destination));
                        clock.set(260);
                        var middle = frame(board);
                        assertEquals(category == SkillCategory.RESOURCE, Arrays.equals(baseline, middle));
                        if (category != SkillCategory.RESOURCE) {
                            int x = board.originX() + board.displayX(destination.x()) * board.cell() + board.cell()/2;
                            int y = board.originY() + board.displayY(destination.y()) * board.cell() + board.cell()/2;
                            boolean changed = false;
                            for (int dy = -20; dy <= 20; dy++) for (int dx = -20; dx <= 20; dx++) {
                                int index = (y+dy)*500+x+dx;
                                changed |= baseline[index] != middle[index];
                            }
                            assertTrue(changed, "effect follows the canonical target under viewer rotation");
                        }
                        board.cancelAnimations();
                        assertArrayEquals(baseline, frame(board), "cancellation clears a visible weapon immediately");
                        clock.set(0);
                        board.playSkillImpact(skillId, viewer, category, List.of(), List.of(destination));
                        clock.set(600);
                        assertArrayEquals(baseline, frame(board));
                        board.cancelAnimations();
                        assertArrayEquals(baseline, frame(board));
                    }
                }
                clock.set(0);
                board.playSkillImpact("strike", viewer, SkillCategory.ATTACK, List.of(), List.of(destination));
                board.setReducedMotion(true);
                board.playSkillImpact("pierce", viewer, SkillCategory.ATTACK, List.of(), List.of(destination));
                clock.set(800);
                assertArrayEquals(baseline, frame(board));
            } finally { board.removeNotify(); }
        });
    }
}
