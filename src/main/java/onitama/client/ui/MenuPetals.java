package onitama.client.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.*;

/** Three small windborne petals, painted over existing controls without a hit-testing layer. */
final class MenuPetals {
    static final double PERIOD_SECONDS = 36;
    private static final Color[] COLORS = {new Color(255, 239, 202),
            new Color(239, 209, 164), new Color(250, 226, 207)};

    record Pose(double x, double y, double angle, float opacity) { }

    private MenuPetals() { }

    static Pose pose(double elapsed, int index, Point2D anchor) {
        double time = elapsed % PERIOD_SECONDS - 7 - index * .65;
        if (time < 0 || time >= 9) return new Pose(anchor.getX(), anchor.getY(), 0, 0);
        double x, y, angle;
        if (time < 3.5) {
            double u = time / 3.5, remaining = 1 - u;
            // A flattening descent meets the letter with zero vertical velocity.
            x = -200 * remaining * remaining + 14 * Math.sin(u * Math.PI * 2) * remaining;
            y = -125 * remaining * remaining + 11 * Math.sin(u * Math.PI * 3) * remaining * remaining;
            angle = .22 + remaining * Math.sin(time * 3 + index) * 1.1;
        } else if (time < 6) {
            x = 0; y = 0; angle = .22 + .025 * Math.sin((time - 3.5) * Math.PI);
        } else {
            double u = (time - 6) / 3;
            x = 320 * u * u;
            y = -105 * u * u + 9 * Math.sin(u * Math.PI * 2) * u;
            angle = .22 + u * 4;
        }
        float opacity = (float) Math.min(1, Math.min(time / .65, (9 - time) / .7));
        return new Pose(anchor.getX() + x, anchor.getY() + y, angle, opacity);
    }

    static Point2D anchor(JButton button, JComponent canvas, int index) {
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        int baseline = (button.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        var glyphs = button.getFont().createGlyphVector(metrics.getFontRenderContext(), button.getText());
        int letter = Math.min(glyphs.getNumGlyphs() - 1, index + 1);
        var bounds = glyphs.getGlyphVisualBounds(letter).getBounds2D();
        Point origin = SwingUtilities.convertPoint(button, button.getInsets().left, baseline, canvas);
        return new Point2D.Double(origin.x + bounds.getCenterX(), origin.y + bounds.getMinY() - 2);
    }

    static void paint(Graphics graphics, JComponent canvas, List<JButton> buttons, double elapsed) {
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            if (!button.isVisible() || button.getWidth() <= 0) continue;
            Pose pose = pose(elapsed, index, anchor(button, canvas, index));
            if (pose.opacity() <= 0) continue;
            Graphics2D g = UiKit.nice(graphics);
            try {
                g.setComposite(AlphaComposite.SrcOver.derive(pose.opacity()));
                g.translate(pose.x(), pose.y()); g.rotate(pose.angle());
                var petal = new Path2D.Double();
                petal.moveTo(-5, 0); petal.curveTo(-3, -5, 3, -5, 6, -1);
                petal.curveTo(3, 3, -2, 3, -5, 0); petal.closePath();
                g.setColor(new Color(42, 29, 18, 80)); g.translate(0, 1); g.fill(petal); g.translate(0, -1);
                g.setColor(COLORS[index % COLORS.length]); g.fill(petal);
                g.setStroke(new BasicStroke(.6f)); g.setColor(new Color(164, 119, 77, 115));
                g.drawLine(-3, 0, 4, -1);
            } finally { g.dispose(); }
        }
    }
}
