package onitama.client.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.Piece;
import onitama.core.duel.PlacedPiece;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Intermediate movement frames, completion, orientation and reduced-motion cleanup. */
class BoardMovementAnimationTest {
    private Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private int[] frame(BoardPanel board) {
        var image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); board.paint(g); g.dispose();
        return image.getRGB(0, 0, 500, 500, null, 0, 500);
    }
    @ParameterizedTest @EnumSource(PlayerColor.class)
    void bothOrientationsRenderTravelThenSettleWithoutChangingState(PlayerColor viewer) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clock = new AtomicLong(); var complete = new AtomicInteger();
            var board = new BoardPanel(clock::get);
            try {
                var from = new Square(1, 1); var to = new Square(4, 3);
                int moverId = viewer == PlayerColor.BLUE ? 1 : 7;
                int enemyId = viewer == PlayerColor.BLUE ? 7 : 1;
                var pieces = List.of(new PlacedPiece(new Piece(moverId, viewer), to),
                        new PlacedPiece(new Piece(enemyId, viewer.opponent()), new Square(5, 5)));
                board.setSize(500, 500); board.setPieces(pieces, viewer);
                board.animateMoves(List.of(new BoardPanel.MovingPiece(moverId, from, to, viewer)), complete::incrementAndGet);
                var start = frame(board);
                clock.set(240); var middle = frame(board);
                assertFalse(java.util.Arrays.equals(start, middle), "piece visibly travels between squares");
                assertFalse(((List<?>) field(board, "movingPieces")).isEmpty());
                assertEquals(0, complete.get());
                clock.set(500);
                var tick = BoardPanel.class.getDeclaredMethod("onAnimTick"); tick.setAccessible(true); tick.invoke(board);
                var end = frame(board);
                assertFalse(java.util.Arrays.equals(middle, end));
                assertEquals(1, complete.get());
                assertEquals(pieces, field(board, "pieces"));
                assertTrue(((List<?>) field(board, "movingPieces")).isEmpty());
                assertFalse(((Timer) field(board, "animationTimer")).isRunning());
                tick.invoke(board); assertEquals(1, complete.get());
                board.setReducedMotion(true);
                board.animateMoves(List.of(new BoardPanel.MovingPiece(moverId, from, to, viewer)), complete::incrementAndGet);
                assertTrue(((List<?>) field(board, "movingPieces")).isEmpty());
                assertEquals(2, complete.get());
            } catch (Exception exception) { throw new AssertionError(exception); }
            finally { board.removeNotify(); }
        });
    }
}
