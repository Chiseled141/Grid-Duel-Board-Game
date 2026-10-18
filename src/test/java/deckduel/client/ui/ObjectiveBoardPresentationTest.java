package deckduel.client.ui;

import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.SwingUtilities;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.Square;
import deckduel.core.duel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Objective art and explanations follow the actual match squares in both orientations. */
class ObjectiveBoardPresentationTest {
    private BufferedImage paint(BoardPanel board) {
        var image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); board.paint(graphics); graphics.dispose(); return image;
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void exactlyFourHighlightedTilesAndHoverExplanationsFollowTheView(PlayerColor viewer) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = RulesEngine.viewFor(RulesEngine.newGame(RulesConfigLoader.load(null), 7), viewer);
            var board = new BoardPanel(); board.setSize(600, 600); board.setView(view);
            var plain = new BoardPanel(); plain.setSize(600, 600); plain.setPieces(view.pieces(), viewer);
            var markedImage = paint(board); var plainImage = paint(plain);
            int changed = 0;
            for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++) {
                Square square = new Square(x, y);
                int px = board.originX() + board.displayX(x) * board.cell();
                int py = board.originY() + board.displayY(y) * board.cell();
                boolean different = false;
                for (int dy = 0; dy < board.cell(); dy++) for (int dx = 0; dx < board.cell(); dx++)
                    different |= markedImage.getRGB(px + dx, py + dy) != plainImage.getRGB(px + dx, py + dy);
                boolean scoring = view.objectives().scoring().contains(square);
                boolean mana = view.objectives().mana().contains(square);
                assertEquals(scoring || mana, different, "only actual objective tiles change");
                if (different) {
                    changed++;
                    // The stone near the tile edge is tinted, rather than carrying a center medallion.
                    int sampleX = px + board.cell() / 10, sampleY = py + board.cell() / 2;
                    assertNotEquals(markedImage.getRGB(sampleX, sampleY), plainImage.getRGB(sampleX, sampleY));
                }
                var hover = new MouseEvent(board, MouseEvent.MOUSE_MOVED, 0, 0,
                        px + board.cell() / 2, py + board.cell() / 2, 0, false);
                String tip = board.getToolTipText(hover);
                if (scoring) assertTrue(tip.contains("+1 point") && tip.contains("turn start"));
                else if (mana) assertTrue(tip.contains("+1 bonus mana") && tip.contains("mana cap"));
                else assertNull(tip);
            }
            assertEquals(4, changed);
            // The tile highlight remains visible around an occupying piece.
            Square occupied = view.objectives().scoring().get(0);
            var piece = new PlacedPiece(new Piece(0, PlayerColor.BLUE), occupied);
            board.setPieces(List.of(piece), viewer); plain.setPieces(List.of(piece), viewer);
            markedImage = paint(board); plainImage = paint(plain);
            int px = board.originX() + board.displayX(occupied.x()) * board.cell();
            int py = board.originY() + board.displayY(occupied.y()) * board.cell();
            int offset = (int) (board.cell() * .14);
            assertNotEquals(markedImage.getRGB(px + offset, py + offset), plainImage.getRGB(px + offset, py + offset));
            board.setView(null);
            assertNull(board.getToolTipText(new MouseEvent(board, MouseEvent.MOUSE_MOVED, 0, 0,
                    px + board.cell() / 2, py + board.cell() / 2, 0, false)));
        });
    }
}
