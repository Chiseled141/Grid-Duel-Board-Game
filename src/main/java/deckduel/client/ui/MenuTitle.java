package deckduel.client.ui;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.util.Map;

/** The home menu's title, painted into the upper sky in the match table's brass, iron and blood-red ribbon. */
final class MenuTitle {
    private static final Font TITLE = Theme.decorative(57);
    private MenuTitle() { }

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
            lettering(g);
            ribbon(g, "A Card-Based Strategy Board Game", 280, 126);
        } finally { g.dispose(); }
    }

    /** The name struck in brass like the match table's trim: dark iron outline, lit from above. */
    private static void lettering(Graphics2D g) {
        Font title = TITLE.deriveFont(54f).deriveFont(Map.of(TextAttribute.TRACKING, .02f));
        TextLayout lettering = new TextLayout("Deck Duel", title, g.getFontRenderContext());
        Rectangle2D ink = lettering.getBounds();
        Shape shape = lettering.getOutline(AffineTransform.getTranslateInstance(280 - ink.getCenterX(), 100));
        Rectangle2D box = shape.getBounds2D();
        g.translate(0, 3); g.setColor(new Color(0, 0, 0, 120)); g.fill(shape); g.translate(0, -3);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.WOOD_LINE); g.draw(shape);
        g.setPaint(new LinearGradientPaint(0, (float) box.getMinY(), 0, (float) box.getMaxY(), new float[] {0f, .45f, .55f, 1f},
                new Color[] {new Color(0xF4DE9E), Theme.BRASS_LIGHT, Theme.BRASS, Theme.BRASS_DARK}));
        g.fill(shape);
        // A thin lit edge along the top of each letter, as on the rail's studs.
        g.setClip(shape);
        g.translate(0, 1.4); g.setColor(new Color(40, 25, 10, 110));
        g.setStroke(new BasicStroke(1.4f)); g.draw(shape); g.translate(0, -1.4);
        g.setClip(null);
    }

    /** A blood-red swallowtail ribbon with gold edges, lettered in lit brass. */
    private static void ribbon(Graphics2D g, String text, int cx, int y) {
        Font font = Theme.bold(13f).deriveFont(Map.of(TextAttribute.TRACKING, .08f));
        TextLayout line = new TextLayout(text.toUpperCase(java.util.Locale.ROOT), font, g.getFontRenderContext());
        double w = line.getAdvance() + 44, h = 26, x = cx - w / 2, notch = 9, tail = 18;
        Color cloth = new Color(0x7E1F1B);
        g.setColor(cloth.darker().darker());
        for (int side : new int[] {-1, 1}) {
            double edge = side < 0 ? x + 8 : x + w - 8, end = side < 0 ? x - tail : x + w + tail;
            // Each tail is folded behind the band, so it sits a little lower and darker.
            var fold = new Path2D.Double();
            fold.moveTo(edge, y + 5); fold.lineTo(end, y + 5); fold.lineTo(end - side * notch, y + 5 + h / 2);
            fold.lineTo(end, y + 5 + h); fold.lineTo(edge, y + 5 + h); fold.closePath();
            g.fill(fold);
        }
        g.setPaint(new GradientPaint(0, y, new Color(0xA8352E), 0, (float) (y + h), new Color(0x4E1210)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(169, 132, 63, 210)); g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x, y + 2.5, x + w, y + 2.5));
        g.draw(new Line2D.Double(x, y + h - 2.5, x + w, y + h - 2.5));
        float textX = (float) (cx - line.getBounds().getCenterX()), textY = (float) (y + h / 2 - line.getBounds().getCenterY());
        g.setColor(new Color(20, 6, 4, 180)); line.draw(g, textX, textY + 1);
        g.setColor(Theme.BRASS_LIGHT); line.draw(g, textX, textY);
    }

    private static void ornament(Graphics2D g, int x, int y) {
        // Fading gold rules either side, like the grooves on the table's iron plates.
        for (int side : new int[] {-1, 1}) {
            float from = x + side * 22, to = x + side * 120;
            g.setPaint(new GradientPaint(from, 0, new Color(217, 181, 106, 230), to, 0, new Color(217, 181, 106, 0)));
            g.fill(new Rectangle2D.Double(Math.min(from, to), y - .6, Math.abs(to - from), 1.4));
        }
        g.setColor(Theme.BRASS_LIGHT);
        diamond(g, x - 104, y, 2); diamond(g, x + 104, y, 2);
        // Two small overlapping cards tie the ornament to the card-driven duel.
        var left = new AffineTransform(); left.translate(x - 5, y); left.rotate(-.2);
        var right = new AffineTransform(); right.translate(x + 5, y); right.rotate(.2);
        g.setStroke(new BasicStroke(1.2f));
        for (var card : new AffineTransform[] {left, right}) {
            Shape shape = card.createTransformedShape(new Rectangle2D.Double(-6, -9, 12, 18));
            g.setColor(Theme.IRON); g.fill(shape);
            g.setColor(Theme.BRASS_LIGHT); g.draw(shape);
        }
    }
    private static void diamond(Graphics2D g, int x, int y, int radius) {
        g.fill(new Polygon(new int[]{x, x + radius, x, x - radius}, new int[]{y - radius, y, y + radius, y}, 4));
    }
}
