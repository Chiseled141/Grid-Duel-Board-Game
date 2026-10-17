package onitama.client.ui;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPetalsTest {
    @Test void petalsLandStayAndLiftAwayWithLongQuietIntervals() {
        var anchor = new Point2D.Double(900, 300);
        for (int index = 0; index < 3; index++) {
            double start = 7 + index * .65;
            assertEquals(0, MenuPetals.pose(start - .01, index, anchor).opacity());
            var approach = MenuPetals.pose(start + 2, index, anchor);
            assertTrue(approach.x() < anchor.x && approach.y() < anchor.y);
            for (double time : new double[] {3.5, 4.5, 5.99}) {
                var landed = MenuPetals.pose(start + time, index, anchor);
                assertEquals(anchor.x, landed.x()); assertEquals(anchor.y, landed.y());
                assertEquals(1, landed.opacity());
            }
            var departing = MenuPetals.pose(start + 7.5, index, anchor);
            assertTrue(departing.x() > anchor.x && departing.y() < anchor.y);
            assertEquals(0, MenuPetals.pose(start + 9, index, anchor).opacity(), .00001);
            var looped = MenuPetals.pose(start + 4.5 + MenuPetals.PERIOD_SECONDS, index, anchor);
            assertEquals(anchor.x, looped.x()); assertEquals(anchor.y, looped.y());
            assertEquals(MenuPetals.pose(start + 4.5, index, anchor).angle(), looped.angle(), .00001);
            for (double boundary : new double[] {3.5, 6}) {
                var before = MenuPetals.pose(start + boundary - .0001, index, anchor);
                var after = MenuPetals.pose(start + boundary, index, anchor);
                assertTrue(Point2D.distance(before.x(), before.y(), after.x(), after.y()) < .01);
            }
        }
        for (int second = 18; second < 36; second++) for (int index = 0; index < 3; index++)
            assertEquals(0, MenuPetals.pose(second, index, anchor).opacity());
    }

    @Test void landingTracksTheActualLetterIncludingHoverAndResizeWithoutTakingClicks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = new JPanel(null); canvas.setSize(1280, 720);
            var button = new JButton("Play"); button.setFont(Theme.display(34));
            button.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
            button.setBounds(900, 280, 330, 65); canvas.add(button);
            Point2D first = MenuPetals.anchor(button, canvas, 0);
            button.getModel().setRollover(true);
            Point2D hovered = MenuPetals.anchor(button, canvas, 0);
            assertEquals(first.getX(), hovered.getX());
            button.setLocation(600, 200);
            Point2D resized = MenuPetals.anchor(button, canvas, 0);
            assertEquals(hovered.getX() - 300, resized.getX());
            assertEquals(hovered.getY() - 80, resized.getY());
            var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            MenuPetals.paint(g, canvas, java.util.List.of(button), 11); g.dispose();
            assertNotEquals(0, image.getRGB((int) resized.getX(), (int) resized.getY()));
            assertSame(button, SwingUtilities.getDeepestComponentAt(canvas, 650, 220));
            assertEquals(1, canvas.getComponentCount(), "painting adds no mouse-intercepting layer");
        });
    }
}
