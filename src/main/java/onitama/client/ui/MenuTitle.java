package onitama.client.ui;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resolution-independent title ornament painted into the home menu's upper sky. */
final class MenuTitle {
    private static final Font TITLE = loadTitleFont();
    private MenuTitle() { }

    private static Font loadTitleFont() {
        try (InputStream stream = MenuTitle.class.getResourceAsStream("/fonts/cinzel-decorative-bold.ttf")) {
            if (stream == null) throw new IllegalStateException("Missing title font");
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception exception) {
            Logger.getLogger(MenuTitle.class.getName()).log(Level.WARNING, "Could not load title font", exception);
            return new Font(Font.SERIF, Font.BOLD, 57);
        }
    }

    static Rectangle bounds(int width, int height) {
        double scale = Math.min(width / 1280.0, height / 800.0);
        int w = (int) Math.round(560 * scale), h = (int) Math.round(168 * scale);
        return new Rectangle((int) Math.round(width * .55 - w / 2.0),
                (int) Math.round(height * .025), w, h);
    }

    static void paint(Graphics2D parent, int width, int height) {
        Rectangle bounds = bounds(width, height);
        if (bounds.width <= 0 || bounds.height <= 0) return;
        var g = (Graphics2D) parent.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.translate(bounds.x, bounds.y); g.scale(bounds.width / 560.0, bounds.height / 168.0);
            ornament(g, 280, 20);
            Font title = TITLE.deriveFont(52f).deriveFont(Map.of(TextAttribute.TRACKING, .015f));
            TextLayout lettering = new TextLayout("Deck Duel", title, g.getFontRenderContext());
            Rectangle2D ink = lettering.getBounds();
            Shape shape = lettering.getOutline(AffineTransform.getTranslateInstance(280 - ink.getCenterX(), 97));
            g.translate(0, 2); g.setColor(new Color(40, 37, 31, 125)); g.fill(shape); g.translate(0, -2);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(69, 58, 44, 210)); g.draw(shape);
            g.setPaint(new GradientPaint(0, 45, new Color(255, 235, 174), 0, 101, new Color(235, 199, 122)));
            g.fill(shape);
            g.setColor(new Color(255, 232, 184, 180)); g.setStroke(new BasicStroke(.8f));
            g.drawLine(125, 117, 263, 117); g.drawLine(297, 117, 435, 117);
            diamond(g, 280, 117, 3);
            Font subtitle = new Font(Font.SERIF, Font.PLAIN, 15).deriveFont(Map.of(TextAttribute.TRACKING, .06f));
            TextLayout line = new TextLayout("A Card-Based Strategy Board Game", subtitle, g.getFontRenderContext());
            float x = (float) (280 - line.getBounds().getCenterX());
            g.setColor(new Color(25, 28, 27, 190)); line.draw(g, x + 1, 146);
            g.setColor(new Color(255, 242, 211)); line.draw(g, x, 145);
        } finally { g.dispose(); }
    }

    private static void ornament(Graphics2D g, int x, int y) {
        g.setColor(new Color(255, 232, 184, 200)); g.setStroke(new BasicStroke(1f));
        g.drawLine(x - 85, y, x - 24, y); g.drawLine(x + 24, y, x + 85, y);
        diamond(g, x - 91, y, 2); diamond(g, x + 91, y, 2);
        // Two small overlapping cards tie the ornament to the card-driven duel.
        var left = new AffineTransform(); left.translate(x - 5, y); left.rotate(-.2);
        var right = new AffineTransform(); right.translate(x + 5, y); right.rotate(.2);
        g.draw(left.createTransformedShape(new Rectangle2D.Double(-6, -9, 12, 18)));
        g.draw(right.createTransformedShape(new Rectangle2D.Double(-6, -9, 12, 18)));
    }

    private static void diamond(Graphics2D g, int x, int y, int radius) {
        g.fill(new Polygon(new int[]{x, x + radius, x, x - radius}, new int[]{y - radius, y, y + radius, y}, 4));
    }
}
