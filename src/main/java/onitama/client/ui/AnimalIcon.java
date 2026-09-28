package onitama.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Animal spirits for the movement cards, drawn in a single coherent
 * ink-illustration style: flowing tapered brush strokes in ink, cream eye
 * accents, one small accent seal detail per animal. All icons paint in a
 * normalized 100x100 box (y grows down), scaled to the requested size.
 * Original artwork — no copied assets.
 */
public final class AnimalIcon {

    private AnimalIcon() {
    }

    /**
     * Paints the animal spirit for a card id into a square of the given
     * size. {@code body} is the ink color, {@code accent} the tiny seal
     * detail, {@code cream} the eye/secondary accents.
     */
    public static void paint(Graphics2D g0, String cardId, int x, int y, int size,
                             Color body, Color accent, Color cream) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        double scale = size / 100.0;
        g.scale(scale, scale);
        g.setColor(body);
        switch (cardId) {
            case "tiger" -> tiger(g, body, accent, cream);
            case "dragon" -> dragon(g, body, accent, cream);
            case "frog" -> frog(g, body, accent, cream);
            case "rabbit" -> rabbit(g, body, accent, cream);
            case "crab" -> crab(g, body, accent, cream);
            case "elephant" -> elephant(g, body, accent, cream);
            case "goose" -> goose(g, body, accent, cream);
            case "rooster" -> rooster(g, body, accent, cream);
            case "monkey" -> monkey(g, body, accent, cream);
            case "mantis" -> mantis(g, body, accent, cream);
            case "horse" -> horse(g, body, accent, cream);
            case "ox" -> ox(g, body, accent, cream);
            case "crane" -> crane(g, body, accent, cream);
            case "boar" -> boar(g, body, accent, cream);
            case "eel" -> eel(g, body, accent, cream);
            default -> cobra(g, body, accent, cream);
        }
        g.dispose();
    }

    // ----- brush helpers --------------------------------------------------

    /** A tapered quad-curve brush stroke from (x1,y1) to (x2,y2). */
    private static void brush(Graphics2D g, double x1, double y1, double cx, double cy,
                              double x2, double y2, double wStart, double wEnd) {
        for (int i = 0; i < 6; i++) {
            double t0 = i / 6.0;
            double t1 = (i + 1) / 6.0;
            double w = wStart + (wEnd - wStart) * (i + 0.5) / 6.0;
            g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.drawLine((int) px(x1, cx, x2, t0), (int) py(y1, cy, y2, t0),
                    (int) px(x1, cx, x2, t1), (int) py(y1, cy, y2, t1));
        }
    }

    private static double px(double x1, double cx, double x2, double t) {
        return (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * cx + t * t * x2;
    }

    private static double py(double y1, double cy, double y2, double t) {
        return (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * cy + t * t * y2;
    }

    private static void disc(Graphics2D g, Color fill, double cx, double cy, double r) {
        g.setColor(fill);
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static void eye(Graphics2D g, Color cream, double cx, double cy) {
        disc(g, cream, cx, cy, 4);
        dot(g, Color.BLACK, cx, cy, 1.8);
    }

    private static void dot(Graphics2D g, Color fill, double cx, double cy, double r) {
        g.setColor(fill);
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** The small red seal stamp, like on a traditional ink painting. */
    private static void seal(Graphics2D g, Color accent, double cx, double cy) {
        g.setColor(accent);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(cx - 5, cy - 5, 10, 10, 3, 3));
    }

    private static void tri(Graphics2D g, double x1, double y1, double x2, double y2,
                            double x3, double y3, Color fill) {
        var polygon = new java.awt.Polygon(
                new int[]{(int) x1, (int) x2, (int) x3},
                new int[]{(int) y1, (int) y2, (int) y3}, 3);
        g.setColor(fill);
        g.fillPolygon(polygon);
        g.drawPolygon(polygon);
    }

    // ----- the sixteen animal spirits -------------------------------------

    private static void tiger(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 22, 52, 45, 30, 72, 44, 15, 9);            // arched back
        brush(g, 72, 44, 84, 40, 86, 50, 9, 6);             // head
        brush(g, 36, 58, 34, 76, 38, 86, 7, 4);             // foreleg
        brush(g, 22, 52, 12, 62, 20, 76, 6, 3);             // tail
        tri(g, 78, 34, 84, 28, 88, 38, body);               // ear
        eye(g, cream, 84, 44);
        seal(g, accent, 44, 48);
    }

    private static void dragon(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 14, 66, 38, 36, 60, 52, 13, 6);            // spine wave
        brush(g, 60, 52, 74, 58, 88, 42, 8, 5);             // to the head
        disc(g, body, 86, 40, 8);                           // head
        tri(g, 84, 30, 88, 20, 94, 32, body);               // horn
        brush(g, 90, 44, 96, 48, 90, 54, 3, 1.5);           // whisker
        eye(g, cream, 88, 38);
        seal(g, accent, 36, 52);
    }

    private static void frog(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 46, 66, 24);                          // crouched body
        brush(g, 60, 74, 74, 82, 84, 88, 7, 4);             // hind leg
        brush(g, 34, 56, 28, 40, 32, 30, 6, 3);             // foreleg
        disc(g, body, 34, 32, 7);                           // eye bump
        disc(g, body, 56, 32, 7);
        eye(g, cream, 34, 32);
        eye(g, cream, 56, 32);
        seal(g, accent, 70, 56);
    }

    private static void rabbit(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 40, 82, 44, 58, 60, 46, 16, 10);           // sitting body
        disc(g, body, 62, 42, 13);                          // head
        brush(g, 66, 32, 74, 12, 70, 4, 7, 3);              // ear
        brush(g, 58, 30, 60, 10, 52, 4, 7, 3);              // ear
        eye(g, cream, 64, 40);
        seal(g, accent, 44, 70);
    }

    private static void crab(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 32, 62, 18, 74, 12, 86, 5, 3);             // legs
        brush(g, 44, 68, 42, 82, 46, 90, 5, 3);
        brush(g, 66, 62, 80, 76, 88, 84, 5, 3);
        disc(g, body, 50, 56, 26);                          // shell
        brush(g, 30, 44, 18, 40, 10, 46, 8, 5);             // claw arm
        disc(g, body, 10, 50, 9);                           // claw
        brush(g, 70, 44, 82, 40, 90, 46, 8, 5);
        disc(g, body, 90, 50, 9);
        g.setColor(cream);
        g.fill(new java.awt.geom.Rectangle2D.Double(5, 48, 6, 4));
        g.fill(new java.awt.geom.Rectangle2D.Double(89, 48, 6, 4));
        eye(g, cream, 42, 46);
        eye(g, cream, 58, 46);
        seal(g, accent, 50, 68);
    }

    private static void elephant(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 42, 42, 20);                          // head
        brush(g, 46, 56, 42, 74, 56, 86, 9, 5);             // trunk
        disc(g, body, 20, 44, 13);                          // ear
        brush(g, 44, 70, 38, 78, 32, 74, 4, 2);             // tusk
        eye(g, cream, 38, 40);
        seal(g, accent, 60, 40);
    }

    private static void goose(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 42, 72, 17);                          // body
        brush(g, 46, 60, 56, 44, 62, 30, 8, 5);             // neck
        disc(g, body, 64, 26, 8);                           // head
        tri(g, 70, 22, 84, 28, 70, 32, body);               // beak
        eye(g, cream, 65, 24);
        brush(g, 40, 66, 26, 58, 22, 48, 5, 3);             // wing hint
        seal(g, accent, 56, 66);
    }

    private static void rooster(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 44, 66, 18);                          // body
        brush(g, 34, 62, 18, 48, 26, 34, 8, 4);             // flowing tail
        brush(g, 40, 58, 24, 40, 34, 30, 6, 3);
        brush(g, 48, 54, 56, 44, 62, 34, 8, 5);             // neck
        disc(g, body, 64, 30, 9);                           // head
        brush(g, 58, 20, 64, 16, 70, 20, 4, 2);             // comb
        tri(g, 72, 28, 84, 32, 72, 36, body);               // beak
        eye(g, cream, 65, 28);
        seal(g, accent, 40, 74);
    }

    private static void monkey(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 30, 62, 48, 40, 68, 58, 13, 8);            // hunched back
        disc(g, body, 50, 36, 12);                          // head
        brush(g, 30, 64, 20, 76, 26, 86, 6, 3);             // arm
        brush(g, 66, 62, 74, 76, 68, 88, 6, 3);             // leg
        eye(g, cream, 45, 34);
        eye(g, cream, 55, 34);
        seal(g, accent, 58, 72);
    }

    private static void mantis(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 28, 76, 44, 58, 56, 44, 6, 4);             // abdomen
        brush(g, 56, 44, 62, 34, 60, 26, 8, 5);             // thorax
        var head = new java.awt.geom.Path2D.Double();
        head.moveTo(52, 26);
        head.lineTo(68, 26);
        head.lineTo(58, 10);
        head.closePath();
        g.setColor(body);
        g.fill(head);
        g.setStroke(new BasicStroke(2f));
        g.draw(head);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(58, 12, 48, 2);                          // antennae
        g.drawLine(60, 12, 70, 4);
        brush(g, 60, 34, 76, 26, 82, 34, 4, 2);             // forelegs
        brush(g, 58, 38, 70, 38, 76, 46, 4, 2);
        eye(g, cream, 56, 20);
        seal(g, accent, 40, 70);
    }

    private static void horse(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 26, 74, 44, 62, 66, 58, 14, 9);            // body
        brush(g, 62, 56, 70, 38, 78, 26, 10, 5);            // neck
        var head = new java.awt.geom.Path2D.Double();
        head.moveTo(74, 22);
        head.lineTo(92, 30);
        head.lineTo(72, 40);
        head.closePath();
        g.setColor(body);
        g.fill(head);
        g.setStroke(new BasicStroke(2f));
        g.draw(head);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(70, 38, 64, 58);                         // mane hint
        brush(g, 34, 76, 32, 86, 36, 92, 5, 3);             // legs
        brush(g, 56, 64, 58, 80, 62, 90, 5, 3);
        brush(g, 24, 72, 14, 78, 10, 88, 5, 2);             // tail
        eye(g, cream, 78, 28);
        seal(g, accent, 46, 66);
    }

    private static void ox(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 22, 62, 48, 48, 74, 60, 16, 10);           // massive body
        disc(g, body, 80, 56, 12);                          // head
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.QuadCurve2D.Double(74, 48, 78, 32, 90, 30));
        g.draw(new java.awt.geom.QuadCurve2D.Double(86, 52, 94, 44, 90, 36));
        brush(g, 32, 68, 30, 82, 36, 90, 5, 3);             // legs
        brush(g, 60, 68, 62, 82, 68, 90, 5, 3);
        brush(g, 22, 64, 14, 74, 18, 84, 5, 2);             // tail
        eye(g, cream, 82, 54);
        seal(g, accent, 48, 54);
    }

    private static void crane(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 46, 88, 46, 62, 46, 56, 3.5, 2.5);         // legs
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(52, 84, 46, 62);
        disc(g, body, 48, 50, 14);                          // body
        brush(g, 52, 44, 64, 32, 62, 22, 7, 4);             // neck
        disc(g, body, 64, 20, 7);                           // head
        tri(g, 70, 16, 88, 22, 70, 26, body);               // beak
        brush(g, 56, 44, 72, 50, 80, 62, 6, 3);             // wing hint
        eye(g, cream, 66, 18);
        seal(g, accent, 44, 46);
    }

    private static void boar(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 44, 62, 22);                          // stocky body
        brush(g, 60, 56, 72, 54, 80, 58, 9, 6);             // head
        disc(g, body, 82, 58, 8);                           // snout
        brush(g, 74, 66, 78, 74, 74, 80, 3, 1.5);           // tusk
        brush(g, 36, 78, 34, 88, 40, 92, 5, 3);             // legs
        brush(g, 54, 78, 56, 88, 62, 92, 5, 3);
        brush(g, 30, 50, 22, 40, 28, 34, 5, 2);             // dorsal ridge
        eye(g, cream, 74, 54);
        seal(g, accent, 40, 56);
    }

    private static void eel(Graphics2D g, Color body, Color accent, Color cream) {
        brush(g, 12, 58, 34, 38, 56, 56, 12, 7);            // wave
        brush(g, 56, 56, 72, 62, 84, 48, 7, 4);             // to the head
        var head = new java.awt.geom.Path2D.Double();
        head.moveTo(80, 42);
        head.lineTo(96, 40);
        head.lineTo(84, 56);
        head.closePath();
        g.setColor(body);
        g.fill(head);
        g.setStroke(new BasicStroke(2f));
        g.draw(head);
        tri(g, 42, 34, 54, 26, 58, 42, body);               // fin
        eye(g, cream, 84, 45);
        seal(g, accent, 30, 52);
    }

    private static void cobra(Graphics2D g, Color body, Color accent, Color cream) {
        disc(g, body, 50, 76, 16);                          // coil
        brush(g, 44, 66, 48, 52, 50, 44, 8, 5);             // raised neck
        g.setColor(body);
        g.fill(new java.awt.geom.Ellipse2D.Double(30, 34, 40, 26)); // hood
        g.setStroke(new BasicStroke(2f));
        g.draw(new java.awt.geom.Ellipse2D.Double(30, 34, 40, 26));
        disc(g, body, 50, 28, 9);                           // head
        eye(g, cream, 45, 26);
        eye(g, cream, 55, 26);
        g.setColor(cream);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(40, 46, 60, 46);                         // hood marking
        seal(g, accent, 62, 70);
    }
}
