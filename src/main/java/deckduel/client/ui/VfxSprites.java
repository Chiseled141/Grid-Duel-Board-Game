package deckduel.client.ui;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

/**
 * Small material sprites for the table's effects, painted once from code and cached: iron chain
 * links, a war axe, a flame tongue and a brass rivet: the solid things the table's light effects
 * act on. Each sprite is painted at twice its size, so it stays crisp when the table is scaled up. JavaFX
 * Application Thread only.
 */
final class VfxSprites {
    private static final double DENSITY = 2;
    private static final Map<String, Image> CACHE = new HashMap<>();

    private VfxSprites() { }

    /** One iron chain link, seen flat ({@code edge} false) or edge-on, its long side horizontal. */
    static ImageView chainLink(boolean edge) {
        return view(edge ? "link-edge" : "link-flat", 22, edge ? 8 : 14, (g, w, h) -> {
            if (edge) {
                g.setFill(TablePlaque.WOOD_LINE);
                g.fillRoundRect(0, 1, w, h - 2, h - 2, h - 2);
                g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#A69C90")), new Stop(.5, Color.web("#5A524B")), new Stop(1, Color.web("#2B2725"))));
                g.fillRoundRect(1, 2, w - 2, h - 4, h - 4, h - 4);
                return;
            }
            g.setLineCap(StrokeLineCap.ROUND);
            g.setStroke(TablePlaque.WOOD_LINE);
            g.setLineWidth(5.4);
            g.strokeOval(3, 3, w - 6, h - 6);
            g.setStroke(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#9C9287")), new Stop(.55, Color.web("#5A524B")), new Stop(1, Color.web("#2E2A27"))));
            g.setLineWidth(3.2);
            g.strokeOval(3, 3, w - 6, h - 6);
            // A worn highlight along the upper curve, as on forged iron.
            g.setStroke(Color.web("#D8CFC2", .75));
            g.setLineWidth(1);
            g.strokeArc(4, 3.2, w - 8, h - 7, 35, 110, ArcType.OPEN);
        });
    }

    /** A flame tongue, white-gold at its root and red at its tip; its base sits at the bottom centre. */
    static ImageView flame(double height) {
        return view("flame-" + Math.round(height), height * .5, height, (g, w, h) -> {
            g.setFill(new RadialGradient(0, 0, .5, .82, .75, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FFF4C2")), new Stop(.3, Color.web("#FFC04D")),
                    new Stop(.65, Color.web("#E0561F", .9)), new Stop(1, Color.web("#8E1E10", 0))));
            g.beginPath();
            g.moveTo(w / 2, 0);
            g.bezierCurveTo(w * .62, h * .3, w, h * .55, w * .9, h * .8);
            g.bezierCurveTo(w * .8, h, w * .2, h, w * .1, h * .8);
            g.bezierCurveTo(0, h * .55, w * .38, h * .3, w / 2, 0);
            g.closePath();
            g.fill();
        });
    }

    /**
     * A bearded war axe lying along +x, {@code reach} from the grip to the middle of its head,
     * the blade's cutting edge facing up (-y). The view's origin is the grip, so a rotation about
     * the origin swings it as a hand would.
     */
    static ImageView axe(double reach) {
        double length = reach / .86, w = length * 1.1 + 8, h = length * .52, grip = 6, handle = h * .8;
        ImageView view = view("axe-" + Math.round(reach), w, h, (g, width, height) -> {
            double t = length * .055, head = grip + length * .86;
            // The haft: dark ash wood with a leather-wrapped grip and an iron pommel.
            g.setFill(TablePlaque.WOOD_LINE);
            g.fillRoundRect(grip - 4, handle - t / 2 - 1, length + 6, t + 2, t, t);
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#9A6A3A")), new Stop(.5, Color.web("#6B4423")), new Stop(1, Color.web("#3E2512"))));
            g.fillRoundRect(grip - 3, handle - t / 2, length + 4, t, t, t);
            g.setFill(Color.web("#2A1A10"));
            for (int band = 0; band < 4; band++) g.fillRect(grip + 4 + band * t * 1.3, handle - t / 2, t * .55, t);
            g.setFill(Color.web("#5A524B"));
            g.fillOval(grip - 6, handle - t * .75, t * 1.5, t * 1.5);
            // The blade: a bearded crescent of dark iron with a bright ground edge along the top.
            double neckLeft = head - length * .07, neckRight = head + length * .05, top = h * .04;
            double tipLeft = head - length * .26, tipRight = head + length * .2;
            g.beginPath();
            g.moveTo(neckLeft, handle - t * .6);
            g.quadraticCurveTo(head - length * .08, h * .38, tipLeft, top + h * .06);
            g.quadraticCurveTo(head - length * .04, -h * .05, tipRight, top + h * .1);
            g.quadraticCurveTo(head + length * .06, h * .36, neckRight, handle - t * .6);
            g.closePath();
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#B9B1A6")), new Stop(.35, Color.web("#6E665F")), new Stop(1, Color.web("#2E2A27"))));
            g.fill();
            g.setStroke(TablePlaque.WOOD_LINE);
            g.setLineWidth(1.6);
            g.stroke();
            g.beginPath();
            g.moveTo(tipLeft + 2, top + h * .08);
            g.quadraticCurveTo(head - length * .04, -h * .02, tipRight - 2, top + h * .12);
            g.setStroke(Color.web("#F2EDE4", .9));
            g.setLineWidth(2);
            g.stroke();
            // The iron socket round the haft, held by a brass rivet.
            g.setFill(TablePlaque.WOOD_LINE);
            g.fillRect(neckLeft - 1, handle - t * 1.1 - 1, neckRight - neckLeft + 2, t * 2.2 + 2);
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#8A8178")), new Stop(1, Color.web("#3A3431"))));
            g.fillRect(neckLeft, handle - t * 1.1, neckRight - neckLeft, t * 2.2);
            g.setFill(TablePlaque.BRASS_LIGHT);
            g.fillOval((neckLeft + neckRight) / 2 - t * .35, handle - t * .35, t * .7, t * .7);
        });
        view.setX(-grip);
        view.setY(-handle);
        return view;
    }

    /** A domed brass rivet, {@code size} across. */
    static ImageView rivet(double size) {
        return view("rivet-" + Math.round(size), size, size, (g, w, h) -> {
            g.setFill(TablePlaque.WOOD_LINE);
            g.fillOval(0, 0, w, h);
            g.setFill(new RadialGradient(0, 0, .35, .3, .75, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#F4DE9E")), new Stop(.5, TablePlaque.BRASS), new Stop(1, TablePlaque.BRASS_DARK)));
            g.fillOval(1, 1, w - 2, h - 2);
        });
    }

    private interface Painter { void paint(GraphicsContext g, double w, double h); }

    /** A view of the cached sprite {@code key}, centred on its origin and shown at {@code w} by {@code h}. */
    private static ImageView view(String key, double w, double h, Painter painter) {
        Image image = CACHE.computeIfAbsent(key, k -> {
            Canvas canvas = new Canvas(Math.ceil(w * DENSITY), Math.ceil(h * DENSITY));
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.scale(DENSITY, DENSITY);
            painter.paint(g, w, h);
            SnapshotParameters transparent = new SnapshotParameters();
            transparent.setFill(Color.TRANSPARENT);
            return canvas.snapshot(transparent, null);
        });
        ImageView view = new ImageView(image);
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.setSmooth(true);
        view.setX(-w / 2);
        view.setY(-h / 2);
        return view;
    }
}
