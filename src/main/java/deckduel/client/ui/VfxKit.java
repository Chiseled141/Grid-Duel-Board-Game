package deckduel.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;

/**
 * Reusable pieces of light for the table's effects, after common 2D game-VFX practice: a hard
 * weapon smear, sparks streaked along their motion, a burst of light with sharp glints, a thin
 * fast shockwave, a thin-line sigil that draws itself, a crackling energy filament and smoke.
 * Light is additive with a white-hot core and a coloured falloff, so it reads as light rather
 * than as a flat shape. Each piece is posed from progress 0-1 by its owning effect. JavaFX
 * Application Thread only.
 */
final class VfxKit {
    /** The white-hot core of every light, warmed to match candlelight. */
    static final Color HOT = Color.web("#FFF6DC");

    private VfxKit() { }

    private static Color ramp(Color colour, double p) { return HOT.interpolate(colour, Math.min(1, p * 1.6)); }

    /** Sparks thrown in a cone, each drawn as a streak along its motion that cools from white to {@code colour}. */
    static final class Sparks extends Group {
        private final double x, y, reach;
        private final double[][] sparks;
        private final Color colour;

        /** {@code heading} and {@code spread} in screen degrees; {@code reach} is how far the fastest spark flies. */
        Sparks(double x, double y, double heading, double spread, int count, double reach, Color colour, long seed) {
            this.x = x; this.y = y; this.reach = reach; this.colour = colour;
            Random random = new Random(seed);
            sparks = new double[count][3];
            for (double[] spark : sparks) {
                double angle = Math.toRadians(heading + (random.nextDouble() - .5) * spread);
                spark[0] = Math.cos(angle); spark[1] = Math.sin(angle); spark[2] = .35 + random.nextDouble() * .65;
                Line streak = new Line();
                streak.setStrokeLineCap(StrokeLineCap.ROUND);
                getChildren().add(streak);
            }
            setBlendMode(BlendMode.ADD);
        }

        void at(double p) {
            double travel = 1 - Math.pow(1 - p, 3), speed = Math.pow(1 - p, 2);
            for (int i = 0; i < sparks.length; i++) {
                double[] s = sparks[i];
                double hx = x + s[0] * reach * s[2] * travel, hy = y + s[1] * reach * s[2] * travel + 30 * p * p;
                // The streak's length follows the spark's speed: long while fast, a dot as it slows.
                double tail = 2 + reach * s[2] * .22 * speed;
                Line streak = (Line) getChildren().get(i);
                streak.setStartX(hx - s[0] * tail);
                streak.setStartY(hy - s[1] * tail);
                streak.setEndX(hx);
                streak.setEndY(hy);
                streak.setStroke(ramp(colour, p));
                streak.setStrokeWidth(.6 + 2 * (1 - p) * s[2]);
                streak.setOpacity(1 - p * p);
            }
        }
    }

    /** A burst of light: a hot core with a coloured falloff and sharp glints that flare and fade. */
    static final class Flash extends Group {
        private final Circle core;
        private final Group glints = new Group();
        private final double size;

        Flash(double x, double y, double size, Color colour, int rays, double turn) {
            this.size = size;
            core = new Circle(x, y, size);
            core.setFill(new RadialGradient(0, 0, x, y, size, false, CycleMethod.NO_CYCLE,
                    new Stop(0, HOT), new Stop(.25, colour.deriveColor(0, 1, 1.2, .9)), new Stop(1, colour.deriveColor(0, 1, 1, 0))));
            for (int i = 0; i < rays; i++) {
                double reach = size * (i % 2 == 0 ? 1.9 : 1.1), wide = size * .07;
                Polygon ray = new Polygon(0, -wide, reach, 0, 0, wide, -reach, 0);
                ray.setFill(HOT);
                ray.setRotate(turn + 180.0 * i / rays);
                glints.getChildren().add(ray);
            }
            glints.setTranslateX(x);
            glints.setTranslateY(y);
            glints.setEffect(new DropShadow(BlurType.GAUSSIAN, colour, 10, .4, 0, 0));
            getChildren().addAll(core, glints);
            setBlendMode(BlendMode.ADD);
        }

        void at(double p) {
            double grow = .55 + .65 * (1 - Math.pow(1 - Math.min(1, p * 2.5), 3));
            core.setRadius(size * grow);
            glints.setScaleX(.4 + grow);
            glints.setScaleY(.4 + grow);
            setOpacity(Math.pow(1 - p, 2));
        }
    }

    /** A thin bright ring racing outward over a faint wide one; {@code flatten} lays it on the table. */
    static final class Shockwave extends Group {
        private final Ellipse thin = new Ellipse(), wide = new Ellipse();
        private final double radius, flatten;
        private final Color colour;

        Shockwave(double x, double y, double radius, double flatten, Color colour) {
            this.radius = radius; this.flatten = flatten; this.colour = colour;
            for (Ellipse ring : List.of(thin, wide)) {
                ring.setCenterX(x);
                ring.setCenterY(y);
                ring.setFill(null);
            }
            wide.setEffect(new GaussianBlur(6));
            getChildren().addAll(wide, thin);
            setBlendMode(BlendMode.ADD);
        }

        void at(double p) {
            double r = Math.max(1, radius * (1 - Math.pow(1 - p, 3)));
            for (Ellipse ring : List.of(thin, wide)) { ring.setRadiusX(r); ring.setRadiusY(r * flatten); }
            thin.setStroke(ramp(colour, p));
            thin.setStrokeWidth(.6 + 2.4 * (1 - p));
            wide.setStroke(colour.deriveColor(0, 1, 1, .35));
            wide.setStrokeWidth(14 * (1 - p));
            setOpacity(1 - p);
        }
    }

    /**
     * A weapon smear: a crescent swept along a swing, thin at its tail and full at the blade, with a
     * white-hot leading edge. Angles are screen degrees about the swing's pivot.
     */
    static final class Smear extends Group {
        private final Polygon body = new Polygon();
        private final Polyline edge = new Polyline();
        private final double cx, cy, radius, thickness;

        Smear(double cx, double cy, double radius, double thickness, Color colour) {
            this.cx = cx; this.cy = cy; this.radius = radius; this.thickness = thickness;
            body.setFill(colour.deriveColor(0, 1, 1, .55));
            body.setEffect(new GaussianBlur(2.5));
            edge.setStroke(HOT);
            edge.setStrokeWidth(2.2);
            edge.setStrokeLineCap(StrokeLineCap.ROUND);
            edge.setEffect(new DropShadow(BlurType.GAUSSIAN, colour, 12, .5, 0, 0));
            getChildren().addAll(body, edge);
            setBlendMode(BlendMode.ADD);
        }

        /** Sweeps from {@code tail} to {@code head}, the blade's leading edge on the {@code outer} side. */
        void sweep(double tail, double head) {
            List<Double> outer = new ArrayList<>(), inner = new ArrayList<>();
            int steps = 18;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps, a = Math.toRadians(tail + (head - tail) * t), w = thickness * Math.pow(t, 1.6);
                outer.add(cx + Math.cos(a) * (radius + w * .3)); outer.add(cy + Math.sin(a) * (radius + w * .3));
                inner.add(0, cy + Math.sin(a) * (radius - w)); inner.add(0, cx + Math.cos(a) * (radius - w));
            }
            List<Double> shape = new ArrayList<>(outer);
            shape.addAll(inner);
            body.getPoints().setAll(shape);
            edge.getPoints().setAll(outer);
        }
    }

    /**
     * A thin-line sigil: two rings, a band of rune strokes and an inscribed star, drawing itself
     * stroke by stroke in light. Centred on its origin.
     */
    static final class Sigil extends Group {
        private final Arc outer, inner;
        private final Group runes = new Group();
        private final Polygon star = new Polygon();

        Sigil(double radius, Color colour, long seed) {
            outer = ring(radius, colour, 1.8);
            inner = ring(radius * .8, colour, 1);
            Random random = new Random(seed);
            int count = 28;
            for (int i = 0; i < count; i++) {
                // Each rune is a short stroke or two between the rings, at its own slant.
                double a = Math.PI * 2 * i / count, r0 = radius * .83, r1 = radius * (.88 + random.nextDouble() * .09);
                Line stroke = new Line(Math.cos(a) * r0, Math.sin(a) * r0, Math.cos(a + .05) * r1, Math.sin(a + .05) * r1);
                stroke.setStroke(HOT);
                stroke.setStrokeWidth(1.2);
                runes.getChildren().add(stroke);
                if (random.nextBoolean()) {
                    Line bar = new Line(Math.cos(a - .04) * r1, Math.sin(a - .04) * r1, Math.cos(a + .07) * r1, Math.sin(a + .07) * r1);
                    bar.setStroke(colour.brighter());
                    bar.setStrokeWidth(1);
                    runes.getChildren().add(bar);
                }
            }
            for (int k = 0; k < 7; k++) {
                double a = Math.PI * 2 * (k * 3 % 7) / 7 - Math.PI / 2;
                star.getPoints().addAll(Math.cos(a) * radius * .74, Math.sin(a) * radius * .74);
            }
            star.setFill(null);
            star.setStroke(colour.brighter());
            star.setStrokeWidth(1);
            getChildren().addAll(outer, inner, runes, star);
            setEffect(new DropShadow(BlurType.GAUSSIAN, colour, 12, .45, 0, 0));
            setBlendMode(BlendMode.ADD);
        }

        private static Arc ring(double r, Color colour, double width) {
            Arc arc = new Arc(0, 0, r, r, 90, 0);
            arc.setType(ArcType.OPEN);
            arc.setFill(null);
            arc.setStroke(HOT.interpolate(colour, .4));
            arc.setStrokeWidth(width);
            return arc;
        }

        /** How much is drawn, 0-1: rings first, then runes, then the star. */
        void draw(double d) {
            outer.setLength(-360 * Math.min(1, d * 1.6));
            inner.setLength(360 * Math.min(1, Math.max(0, d * 1.6 - .2)));
            List<Node> strokes = runes.getChildren();
            for (int i = 0; i < strokes.size(); i++) strokes.get(i).setVisible(i < strokes.size() * (d - .3) / .45);
            star.setOpacity(Math.max(0, Math.min(1, (d - .7) / .3)));
        }
    }

    /** A crackling filament of energy from one point to another, jittering as it flickers. */
    static final class Filament extends Group {
        private final Polyline core = new Polyline(), glow = new Polyline();
        private final double ax, ay, bx, by, sag, jitter;
        private final long seed;

        Filament(double ax, double ay, double bx, double by, double sag, Color colour, double width, long seed) {
            this.ax = ax; this.ay = ay; this.bx = bx; this.by = by; this.sag = sag; this.seed = seed;
            this.jitter = Math.max(3, Math.hypot(bx - ax, by - ay) * .03);
            glow.setStroke(colour.deriveColor(0, 1, 1, .55));
            glow.setStrokeWidth(width * 3.2);
            glow.setEffect(new GaussianBlur(5));
            core.setStroke(HOT);
            core.setStrokeWidth(width);
            for (Polyline line : List.of(core, glow)) line.setStrokeLineCap(StrokeLineCap.ROUND);
            getChildren().addAll(glow, core);
            setBlendMode(BlendMode.ADD);
        }

        /** Draws it out to {@code reach} (0-1) of the way; {@code flicker} picks this frame's crackle. */
        void at(double reach, int flicker) {
            Random random = new Random(seed * 31 + flicker);
            double cx = (ax + bx) / 2, cy = (ay + by) / 2 + sag;
            List<Double> points = new ArrayList<>();
            int steps = 22;
            for (int i = 0; i <= steps; i++) {
                double t = reach * i / steps, edge = Math.sin(Math.PI * i / steps);
                double x = (1 - t) * (1 - t) * ax + 2 * (1 - t) * t * cx + t * t * bx;
                double y = (1 - t) * (1 - t) * ay + 2 * (1 - t) * t * cy + t * t * by;
                points.add(x + (random.nextDouble() - .5) * jitter * edge);
                points.add(y + (random.nextDouble() - .5) * jitter * 2 * edge);
            }
            core.getPoints().setAll(points);
            glow.getPoints().setAll(points);
        }
    }

    /** Dark smoke puffs that swell and drift up from a line, thinning as they go. */
    static final class Smoke extends Group {
        private final double[][] puffs;
        private final double x, y, width;

        Smoke(double x, double y, double width, int count, long seed) {
            this.x = x; this.y = y; this.width = width;
            Random random = new Random(seed);
            puffs = new double[count][3];
            for (double[] puff : puffs) {
                puff[0] = random.nextDouble(); puff[1] = random.nextDouble() * .4; puff[2] = 6 + random.nextDouble() * 8;
                Circle circle = new Circle(1, Color.rgb(24, 19, 16, .55));
                circle.setEffect(new GaussianBlur(7));
                getChildren().add(circle);
            }
        }

        void at(double p) {
            for (int i = 0; i < puffs.length; i++) {
                double[] puff = puffs[i];
                double age = Math.max(0, (p - puff[1]) / (1 - puff[1]));
                Circle circle = (Circle) getChildren().get(i);
                circle.setVisible(age > 0);
                circle.setCenterX(x + puff[0] * width + Math.sin(age * 4 + i) * 6);
                circle.setCenterY(y - 60 * age);
                circle.setRadius(puff[2] * (.5 + 1.4 * age));
                circle.setOpacity(Math.sin(Math.PI * age));
            }
        }
    }
}
