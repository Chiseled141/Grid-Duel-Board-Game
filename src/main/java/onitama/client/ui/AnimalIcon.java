package onitama.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Original stylized animal icons for the movement cards — flat "sticker"
 * heads drawn from simple geometry, one per animal, tinted with the card's
 * accent color. All icons paint in a normalized 100x100 box (y grows down),
 * scaled to whatever size the caller needs.
 */
public final class AnimalIcon {

    private AnimalIcon() {
    }

    /**
     * Paints the animal icon for a card id into a square of the given size.
     * Unknown ids fall back to the cobra mark.
     */
    public static void paint(Graphics2D g0, String cardId, int x, int y, int size,
                             Color accent, Color ink, Color cream) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        double scale = size / 100.0;
        g.scale(scale, scale);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (cardId) {
            case "tiger" -> tiger(g, accent, ink, cream);
            case "dragon" -> dragon(g, accent, ink, cream);
            case "frog" -> frog(g, accent, ink, cream);
            case "rabbit" -> rabbit(g, accent, ink, cream);
            case "crab" -> crab(g, accent, ink, cream);
            case "elephant" -> elephant(g, accent, ink, cream);
            case "goose" -> goose(g, accent, ink, cream);
            case "rooster" -> rooster(g, accent, ink, cream);
            case "monkey" -> monkey(g, accent, ink, cream);
            case "mantis" -> mantis(g, accent, ink, cream);
            case "horse" -> horse(g, accent, ink, cream);
            case "ox" -> ox(g, accent, ink, cream);
            case "crane" -> crane(g, accent, ink, cream);
            case "boar" -> boar(g, accent, ink, cream);
            case "eel" -> eel(g, accent, ink, cream);
            default -> cobra(g, accent, ink, cream);
        }
        g.dispose();
    }

    // ----- shape helpers -------------------------------------------------

    private static void disc(Graphics2D g, double cx, double cy, double r, Color fill) {
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(Theme.INK);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static void box(Graphics2D g, double x, double y, double w, double h,
                            double arc, Color fill) {
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        g.setColor(Theme.INK);
        g.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
    }

    private static void tri(Graphics2D g, double x1, double y1, double x2, double y2,
                            double x3, double y3, Color fill) {
        var polygon = new java.awt.Polygon(
                new int[]{(int) x1, (int) x2, (int) x3},
                new int[]{(int) y1, (int) y2, (int) y3}, 3);
        g.setColor(fill);
        g.fillPolygon(polygon);
        g.setColor(Theme.INK);
        g.drawPolygon(polygon);
    }

    private static void eye(Graphics2D g, double cx, double cy, Color cream) {
        disc(g, cx, cy, 5, cream);
        g.setColor(Theme.INK);
        disc(g, cx, cy, 2.2, Theme.INK);
    }

    // ----- the sixteen animals -------------------------------------------

    private static void tiger(Graphics2D g, Color accent, Color ink, Color cream) {
        tri(g, 24, 30, 40, 16, 42, 32, accent);
        tri(g, 76, 30, 60, 16, 58, 32, accent);
        disc(g, 50, 55, 30, accent);
        g.setColor(ink);
        g.setStroke(new BasicStroke(3.5f));
        g.drawLine(50, 30, 50, 40);
        g.drawLine(28, 52, 36, 52);
        g.drawLine(72, 52, 64, 52);
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(38, 60, 24, 18));
        eye(g, 40, 48, cream);
        eye(g, 60, 48, cream);
        g.setColor(ink);
        g.fill(new Ellipse2D.Double(46, 64, 8, 5));
    }

    private static void dragon(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(22, 78, 40, 30, 66, 46));
        tri(g, 58, 28, 66, 10, 76, 30, accent);
        disc(g, 70, 42, 16, accent);
        tri(g, 82, 40, 96, 46, 82, 52, cream);
        tri(g, 62, 20, 66, 8, 72, 22, cream);
        eye(g, 68, 38, cream);
    }

    private static void frog(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(14, 44, 72, 40));
        g.setColor(ink);
        g.draw(new Ellipse2D.Double(14, 44, 72, 40));
        disc(g, 30, 34, 12, accent);
        disc(g, 70, 34, 12, accent);
        eye(g, 30, 34, cream);
        eye(g, 70, 34, cream);
        g.setColor(ink);
        g.setStroke(new BasicStroke(3f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(34, 66, 50, 76, 66, 66));
    }

    private static void rabbit(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(28, 8, 16, 40));
        g.fill(new Ellipse2D.Double(56, 8, 16, 40));
        g.setColor(ink);
        g.draw(new Ellipse2D.Double(28, 8, 16, 40));
        g.draw(new Ellipse2D.Double(56, 8, 16, 40));
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(32, 14, 8, 28));
        g.fill(new Ellipse2D.Double(60, 14, 8, 28));
        disc(g, 50, 62, 26, accent);
        eye(g, 41, 56, cream);
        eye(g, 59, 56, cream);
        tri(g, 46, 68, 54, 68, 50, 74, cream);
    }

    private static void crab(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setStroke(new BasicStroke(4f));
        g.setColor(accent);
        g.drawLine(30, 66, 16, 80);
        g.drawLine(50, 72, 50, 88);
        g.drawLine(70, 66, 84, 80);
        g.setColor(ink);
        g.drawLine(30, 66, 16, 80);
        g.drawLine(50, 72, 50, 88);
        g.drawLine(70, 66, 84, 80);
        disc(g, 50, 52, 28, accent);
        disc(g, 16, 44, 13, accent);
        disc(g, 84, 44, 13, accent);
        tri(g, 8, 38, 20, 44, 10, 52, cream);
        tri(g, 92, 38, 80, 44, 90, 52, cream);
        g.setColor(ink);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(44, 40, 44, 30);
        g.drawLine(56, 40, 56, 30);
        eye(g, 44, 28, cream);
        eye(g, 56, 28, cream);
    }

    private static void elephant(Graphics2D g, Color accent, Color ink, Color cream) {
        disc(g, 22, 48, 17, accent);
        disc(g, 78, 48, 17, accent);
        disc(g, 50, 46, 26, accent);
        g.setColor(accent);
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(50, 58, 48, 74, 58, 86));
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(50, 58, 48, 74, 58, 86));
        tri(g, 42, 66, 40, 76, 34, 70, cream);
        eye(g, 38, 44, cream);
        eye(g, 62, 44, cream);
    }

    private static void goose(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(46, 68, 50, 46, 60, 30));
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(46, 68, 50, 46, 60, 30));
        disc(g, 62, 26, 12, accent);
        tri(g, 72, 22, 86, 28, 72, 32, cream);
        eye(g, 62, 24, cream);
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(24, 62, 44, 26));
        g.setColor(ink);
        g.draw(new Ellipse2D.Double(24, 62, 44, 26));
    }

    private static void rooster(Graphics2D g, Color accent, Color ink, Color cream) {
        disc(g, 46, 34, 8, accent);
        disc(g, 56, 30, 8, accent);
        disc(g, 64, 36, 8, accent);
        disc(g, 50, 52, 22, accent);
        tri(g, 70, 50, 88, 56, 70, 62, cream);
        disc(g, 68, 66, 6, cream);
        eye(g, 52, 48, cream);
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(34, 66, 34, 22));
        g.setColor(ink);
        g.draw(new Ellipse2D.Double(34, 66, 34, 22));
    }

    private static void monkey(Graphics2D g, Color accent, Color ink, Color cream) {
        disc(g, 24, 48, 10, accent);
        disc(g, 76, 48, 10, accent);
        disc(g, 50, 52, 27, accent);
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(32, 44, 36, 32));
        eye(g, 42, 48, cream);
        eye(g, 58, 48, cream);
        g.setColor(ink);
        g.setStroke(new BasicStroke(3f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(42, 62, 50, 68, 58, 62));
    }

    private static void mantis(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setStroke(new BasicStroke(3.5f));
        g.drawLine(46, 34, 30, 10);
        g.drawLine(56, 34, 70, 10);
        var head = new java.awt.geom.Path2D.Double();
        head.moveTo(36, 44);
        head.lineTo(64, 44);
        head.lineTo(50, 74);
        head.closePath();
        g.setColor(accent);
        g.fill(head);
        g.setColor(ink);
        g.draw(head);
        disc(g, 42, 46, 5, cream);
        disc(g, 58, 46, 5, cream);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(50, 74, 50, 92);
    }

    private static void horse(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(36, 22, 30, 52, 16, 30));
        g.setColor(ink);
        g.draw(new RoundRectangle2D.Double(36, 22, 30, 52, 16, 30));
        tri(g, 36, 24, 30, 8, 46, 18, accent);
        tri(g, 62, 24, 70, 10, 52, 16, accent);
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(40, 56, 22, 16));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(30, 20, 34, 60);
        eye(g, 44, 36, cream);
        eye(g, 58, 36, cream);
    }

    private static void ox(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(26, 34, 16, 16, 34, 14));
        g.draw(new java.awt.geom.QuadCurve2D.Double(74, 34, 84, 16, 66, 14));
        g.setStroke(new BasicStroke(2.5f));
        disc(g, 50, 54, 27, accent);
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(34, 58, 32, 20));
        eye(g, 40, 48, cream);
        eye(g, 60, 48, cream);
        g.setColor(ink);
        g.fill(new Ellipse2D.Double(42, 66, 6, 4));
        g.fill(new Ellipse2D.Double(52, 66, 6, 4));
    }

    private static void crane(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(44, 70, 48, 48, 54, 34));
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(44, 70, 48, 48, 54, 34));
        disc(g, 56, 30, 14, accent);
        tri(g, 68, 26, 88, 32, 68, 36, cream);
        disc(g, 56, 16, 5, Theme.AMBER);
        eye(g, 58, 28, cream);
    }

    private static void boar(Graphics2D g, Color accent, Color ink, Color cream) {
        tri(g, 28, 30, 40, 14, 44, 30, accent);
        tri(g, 72, 30, 60, 14, 56, 30, accent);
        disc(g, 50, 52, 28, accent);
        g.setColor(cream);
        g.fill(new Ellipse2D.Double(36, 56, 28, 22));
        g.setColor(ink);
        g.fill(new Ellipse2D.Double(44, 64, 5, 6));
        g.fill(new Ellipse2D.Double(52, 64, 5, 6));
        tri(g, 30, 70, 38, 76, 30, 82, cream);
        tri(g, 70, 70, 62, 76, 70, 82, cream);
        eye(g, 36, 46, cream);
        eye(g, 64, 46, cream);
    }

    private static void eel(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Path2D.Double());
        var body = new java.awt.geom.Path2D.Double();
        body.moveTo(16, 62);
        body.quadTo(38, 34, 54, 52);
        body.quadTo(70, 70, 86, 50);
        g.draw(body);
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(body);
        disc(g, 86, 50, 11, accent);
        eye(g, 88, 47, cream);
        tri(g, 44, 32, 54, 26, 58, 40, cream);
    }

    private static void cobra(Graphics2D g, Color accent, Color ink, Color cream) {
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(22, 40, 56, 42));
        g.setColor(ink);
        g.draw(new Ellipse2D.Double(22, 40, 56, 42));
        disc(g, 50, 34, 15, accent);
        eye(g, 44, 30, cream);
        eye(g, 56, 30, cream);
        g.setColor(cream);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(36, 52, 64, 52);
        g.drawLine(42, 60, 58, 60);
    }
}
