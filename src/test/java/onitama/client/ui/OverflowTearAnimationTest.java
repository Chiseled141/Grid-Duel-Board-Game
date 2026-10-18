package onitama.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import onitama.core.PlayerColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Overflow cards tear visibly, respect private faces, and disappear cleanly. */
class OverflowTearAnimationTest {
    private BufferedImage image(Color color) {
        var image = new BufferedImage(80, 120, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0, 0, 80, 120); graphics.dispose();
        return image;
    }
    private int[] frame(DeckDrawOverlay overlay) {
        var image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); overlay.paint(graphics); graphics.dispose();
        return image.getRGB(0, 0, 500, 500, null, 0, 500);
    }

    @Test void tornFragmentsAnimateAndExpireWithoutInterceptingInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clock = new AtomicLong(); var overlay = new DeckDrawOverlay(clock::get); overlay.setSize(500, 500);
            try {
                var back = image(Color.BLUE); var face = image(Color.RED);
                for (boolean own : List.of(true, false)) {
                    clock.set(0);
                    var flight = DeckDrawOverlay.Flight.torn("burn", back, own ? face : null,
                            () -> new Rectangle(20, 350, 80, 120), () -> new Rectangle(210, 150, 80, 120), 0);
                    assertEquals(own, flight.face() != null);
                    overlay.play(List.of(flight));
                    clock.set(550_000_000); var first = frame(overlay);
                    clock.set(780_000_000); var torn = frame(overlay);
                    assertFalse(java.util.Arrays.equals(first, torn), "jagged pieces visibly separate and fade");
                    assertTrue(java.util.Arrays.stream(torn).anyMatch(pixel -> (pixel >>> 24) > 0));
                    assertFalse(overlay.contains(230, 170));
                    clock.set(1_050_000_000); overlay.tick();
                    assertFalse(overlay.isFlyingTo("burn"));
                    assertTrue(java.util.Arrays.stream(frame(overlay)).allMatch(pixel -> (pixel >>> 24) == 0));
                    clock.set(0); overlay.play(List.of(flight)); overlay.setReducedMotion(true);
                    assertFalse(overlay.isFlyingTo("burn")); overlay.setReducedMotion(false);
                }
            } finally { overlay.removeNotify(); }
        });
    }
}
