package deckduel.client.ui;

import deckduel.client.ScoringSounds;
import deckduel.client.ui.Staging.Tone;
import deckduel.core.card.CardDef;
import deckduel.core.card.Suppression;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

/**
 * Performs a {@link Staging} as JavaFX nodes over the authoritative view. Each step becomes a
 * small node group when its time comes, is posed every frame from its progress (cards follow
 * {@link CardMotion}'s spring physics and turn over about their vertical axis under the table's
 * perspective camera) and is removed when it ends. Nothing here changes game state; a newer
 * update plays alongside any sequence still running, and only a new game clears them. Used on the JavaFX Application Thread only.
 */
final class TableEffects {
    private static final Color WHITE = Color.rgb(255, 250, 235);

    private final Pane layer;
    private final ScoringSounds sounds;
    private final List<Run> runs = new ArrayList<>();
    private double pixelScale = 1;

    /** One step on screen: its nodes, posed from progress 0–1. */
    private abstract static class Live {
        final Group node = new Group();
        abstract void update(double p);
    }

    /** One sequence playing on its own clock, in its own node group above the earlier ones. */
    private final class Run {
        final Staging staging;
        final List<Staging.Step> steps;
        final List<Staging.Beat> beats;
        final Live[] lives;
        final Group node = new Group();
        final long start = System.nanoTime();
        int played;

        Run(Staging staging) {
            this.staging = staging;
            steps = staging.steps();
            beats = staging.beats();
            lives = new Live[steps.size()];
        }

        double elapsed(long now) { return (now - start) / 1e6 / Staging.TEMPO; }

        /** Advances to {@code now}; false once everything has been shown and heard. */
        boolean tick(long now) {
            double t = elapsed(now);
            while (played < beats.size() && beats.get(played).at() <= t)
                sounds.play(new ScoringSounds.Cue(beats.get(played++).kind(), 0));
            for (int i = 0; i < steps.size(); i++) {
                Staging.Step step = steps.get(i);
                boolean active = t >= step.at() && t < step.end();
                if (active && lives[i] == null) {
                    lives[i] = create(step.effect(), step.duration());
                    // Steps added later draw above earlier ones, as they were planned.
                    node.getChildren().add(insertionIndex(i), lives[i].node);
                }
                if (lives[i] == null) continue;
                if (active) lives[i].update((t - step.at()) / step.duration());
                else { node.getChildren().remove(lives[i].node); lives[i] = null; }
            }
            return t < staging.length() || played < beats.size();
        }

        private int insertionIndex(int step) {
            int index = 0;
            for (int i = 0; i < step; i++) if (lives[i] != null) index++;
            return index;
        }
    }

    /** Draws into {@code layer}, which should sit under a perspective camera, and plays cues through {@code sounds}. */
    TableEffects(Pane layer, ScoringSounds sounds) {
        this.layer = layer;
        this.sounds = sounds;
        layer.setMouseTransparent(true);
    }

    /**
     * Starts {@code next} alongside anything still playing, so a quick reply never cuts a card off
     * mid-flight. Card images are prepared before its clock starts; converting one on a later
     * frame would stall it and make the motion skip ahead.
     */
    void perform(Staging next, double pixels) {
        pixelScale = pixels;
        for (Staging.Step step : next.steps()) prepare(step.effect());
        Run run = new Run(next);
        runs.add(run);
        layer.getChildren().add(run.node);
    }

    /** Stops every sequence and removes its nodes, as when a new game starts. */
    void clear() {
        layer.getChildren().clear();
        runs.clear();
    }

    boolean running() { return !runs.isEmpty(); }

    boolean hiddenOnRow(long cardId) {
        long now = System.nanoTime();
        return runs.stream().anyMatch(run -> run.staging.hiddenOnRow(cardId, run.elapsed(now)));
    }

    boolean hiddenInHand(long cardId) {
        long now = System.nanoTime();
        return runs.stream().anyMatch(run -> run.staging.hiddenInHand(cardId, run.elapsed(now)));
    }

    /** The camera jolt now: a decaying random offset, strongest for the biggest moments. */
    Point2D shake(long now) {
        double amplitude = 0;
        for (Run run : runs) amplitude = Math.max(amplitude, run.staging.shakeAmplitude(run.elapsed(now)));
        if (amplitude < .3) return Point2D.ZERO;
        Random random = new Random(now / 16_000_000L);
        return new Point2D((random.nextDouble() * 2 - 1) * amplitude, (random.nextDouble() * 2 - 1) * amplitude);
    }

    /** Advances every sequence to {@code now} and plays the cues that have come due; false once all have finished. */
    boolean tick(long now) {
        for (var it = runs.iterator(); it.hasNext();) {
            Run run = it.next();
            if (run.tick(now)) continue;
            layer.getChildren().remove(run.node);
            it.remove();
        }
        return !runs.isEmpty();
    }

    // ---------------------------------------------------------------- cards

    private void prepare(Staging.Effect effect) {
        if (effect instanceof Staging.Flight f) {
            image(f.face(), List.of(), larger(f.from(), f.to()));
            image(null, List.of(), larger(f.from(), f.to()));
        } else if (effect instanceof Staging.Eject e) image(e.card(), e.marks(), larger(e.from(), e.pile()));
        else if (effect instanceof Staging.Hold h) image(h.card(), h.marks(), h.at());
        else if (effect instanceof Staging.Landing l) image(l.card(), l.marks(), l.at());
        else if (effect instanceof Staging.Charge c) FxKit.face(c.spell(), List.of(), c.at().width, c.at().height, true, pixelScale);
        else if (effect instanceof Staging.Dissolve d) image(d.spell(), List.of(), d.at());
        else if (effect instanceof Staging.Chains c) image(c.card(), c.marks(), c.at());
        else if (effect instanceof Staging.Cracks c) image(c.card(), c.marks(), c.at());
    }

    private Image image(CardDef face, List<Suppression> marks, Rectangle size) {
        return face == null ? FxKit.back(size.width, size.height, pixelScale)
                : FxKit.face(face, marks, size.width, size.height, false, pixelScale);
    }

    private static Rectangle larger(Rectangle a, Rectangle b) { return a.width >= b.width ? a : b; }

    /**
     * A card image centred on its origin, posed by translation, lean, a turn about its vertical
     * axis and a uniform scale. The sprite's baked shadow margin sits right of and below the card.
     */
    private static final class CardNode extends Group {
        final ImageView view = new ImageView();
        final Rotate spin = new Rotate(0, Rotate.Z_AXIS), turn = new Rotate(0, Rotate.Y_AXIS);
        final Scale scale = new Scale(1, 1);
        final ColorAdjust shade = new ColorAdjust();

        CardNode(Image image, double width, double height) {
            view.setImage(image);
            view.setSmooth(true);
            view.setFitWidth(width + CardFace.SHADOW_X);
            view.setFitHeight(height + CardFace.SHADOW_Y);
            view.setLayoutX(-width / 2);
            view.setLayoutY(-height / 2);
            view.setEffect(shade);
            getChildren().add(view);
            getTransforms().setAll(spin, turn, scale);
        }

        void pose(double x, double y, double angle, double size, double turnDegrees) {
            setTranslateX(x);
            setTranslateY(y);
            spin.setAngle(Math.toDegrees(angle));
            turn.setAngle(turnDegrees);
            scale.setX(size);
            scale.setY(size);
        }
    }

    private CardNode card(CardDef face, List<Suppression> marks, Rectangle size) {
        return new CardNode(image(face, marks, size), size.width, size.height);
    }

    /** A soft shadow cast on the table by a lifted card. */
    private static javafx.scene.shape.Rectangle shadow(Rectangle size) {
        var shadow = new javafx.scene.shape.Rectangle(-size.width / 2.0, -size.height / 2.0, size.width, size.height);
        shadow.setArcWidth(16);
        shadow.setArcHeight(16);
        shadow.setFill(Color.BLACK);
        shadow.setEffect(new GaussianBlur(8));
        return shadow;
    }

    /**
     * A card moving along {@code motion} (animation milliseconds to pose): the face, or its back
     * while turned away, with a shadow that drifts further and softer the higher it is lifted.
     */
    private Live travel(CardDef face, List<Suppression> marks, Rectangle from, Rectangle to, int duration, boolean fadeOut,
                        java.util.function.DoubleFunction<CardMotion.Pose> motion) {
        Rectangle size = larger(from, to);
        CardNode front = face == null ? null : card(face, marks, size), back = card(null, List.of(), size);
        var shadow = shadow(size);
        GaussianBlur blur = (GaussianBlur) shadow.getEffect();
        return new Live() {
            { node.getChildren().addAll(shadow, back); if (front != null) node.getChildren().add(front); }
            @Override void update(double p) {
                CardMotion.Pose pose = motion.apply(p * duration);
                double size2 = (from.getWidth() + (to.getWidth() - from.getWidth()) * pose.progress()) / size.getWidth();
                double scale = size2 * pose.scale(), degrees = Math.toDegrees(pose.turn());
                boolean faceShown = front != null && pose.faceUp();
                CardNode shown = faceShown ? front : back;
                if (front != null) front.setVisible(faceShown);
                back.setVisible(!faceShown);
                // The back is turned half a revolution further, so it never shows mirrored.
                shown.pose(pose.x(), pose.y(), pose.angle(), scale, faceShown || front == null ? degrees : degrees - 180);
                shown.shade.setBrightness(-pose.shade());
                double drop = 4 + 22 * pose.height();
                shadow.setTranslateX(pose.x() + drop * .45);
                shadow.setTranslateY(pose.y() + drop);
                shadow.setRotate(Math.toDegrees(pose.angle()));
                shadow.setScaleX(scale * Math.max(.05, Math.abs(Math.cos(pose.turn()))));
                shadow.setScaleY(scale);
                blur.setRadius(6 + 16 * pose.height());
                shadow.setOpacity(.42 - .14 * pose.height());
                node.setOpacity(fadeOut && p > .7 ? 1 - (p - .7) / .3 : 1);
            }
        };
    }

    // ---------------------------------------------------------------- effects

    private Live create(Staging.Effect effect, int duration) {
        if (effect instanceof Staging.Hold h) return hold(h);
        if (effect instanceof Staging.Flight f) return travel(f.face(), List.of(), f.from(), f.to(), duration, false,
                t -> f.glide() ? CardMotion.glide(f.from(), f.to(), t, duration, f.flip(), f.angle())
                        : CardMotion.travel(f.from(), f.to(), t, duration, f.flip()));
        if (effect instanceof Staging.Landing l) return landing(l, duration);
        if (effect instanceof Staging.Eject e) return travel(e.card(), e.marks(), e.from(), e.pile(), duration, true,
                t -> CardMotion.travel(e.from(), e.pile(), t, duration, false));
        if (effect instanceof Staging.Charge c) return charge(c);
        if (effect instanceof Staging.Bolt b) return bolt(b);
        if (effect instanceof Staging.Dissolve d) return dissolve(d);
        if (effect instanceof Staging.Ring r) return ring(r);
        if (effect instanceof Staging.Burst b) return burst(b);
        if (effect instanceof Staging.Shatter s) return shatter(s);
        if (effect instanceof Staging.Chains c) return chains(c);
        if (effect instanceof Staging.Hammer h) return hammer(h, duration);
        if (effect instanceof Staging.Impact i) return impact(i);
        if (effect instanceof Staging.Cracks c) return cracks(c);
        if (effect instanceof Staging.Shine s) return shine(s);
        if (effect instanceof Staging.Link l) return link(l);
        if (effect instanceof Staging.Banner b) return banner(b);
        if (effect instanceof Staging.Confetti c) return confetti(c);
        throw new IllegalArgumentException("Unknown effect " + effect);
    }

    private Live hold(Staging.Hold h) {
        CardNode card = card(h.card(), h.marks(), h.at());
        card.pose(h.at().getCenterX(), h.at().getCenterY(), 0, 1, 0);
        return new Live() {
            { node.getChildren().add(card); }
            @Override void update(double p) { }
        };
    }

    /** Settling on the table: a soft scale punch and a faint ring of dust, no cartoon squash. */
    private Live landing(Staging.Landing l, int duration) {
        CardNode card = card(l.card(), l.marks(), l.at());
        return new Live() {
            { node.getChildren().add(card); }
            @Override void update(double p) {
                double punch = CardMotion.punch(p * duration, .05, 70, 24);
                card.pose(l.at().getCenterX(), l.at().getCenterY(), 0, 1 + punch, 0);
            }
        };
    }

    /** A card meeting the table: one soft ring spreads flat across the tabletop and a little dust puffs out. */
    private Live impact(Staging.Impact i) {
        Rectangle at = i.at();
        double cx = at.getCenterX(), floor = at.getMaxY() - 6;
        Ellipse wave = new Ellipse(cx, floor, 1, 1);
        wave.setFill(null);
        wave.setStroke(colour(i.tone(), 1));
        Random random = new Random(i.seed());
        // Dust: {start x, outward speed, size}, thrown from under the bottom edge.
        double[][] dust = new double[8][3];
        Group puffs = new Group();
        for (double[] d : dust) {
            d[0] = at.x + random.nextDouble() * at.width;
            d[1] = (25 + random.nextDouble() * 45) * Math.signum(d[0] - cx + 1e-6);
            d[2] = 3 + random.nextDouble() * 4;
            Circle puff = new Circle(1, Color.rgb(226, 206, 168, .6));
            puff.setEffect(new GaussianBlur(3));
            puffs.getChildren().add(puff);
        }
        return new Live() {
            { node.getChildren().addAll(puffs, wave); }
            @Override void update(double p) {
                double e = easeOutCubic(p), r = at.width * (.55 + .6 * e);
                wave.setRadiusX(r);
                wave.setRadiusY(r * .3);
                wave.setStrokeWidth(Math.max(.5, 2 * (1 - p)));
                wave.setOpacity(.7 * (1 - p));
                for (int k = 0; k < dust.length; k++) {
                    Circle puff = (Circle) puffs.getChildren().get(k);
                    puff.setCenterX(dust[k][0] + dust[k][1] * e);
                    puff.setCenterY(floor - 8 * e);
                    puff.setRadius(dust[k][2] * (.6 + e));
                    puff.setOpacity(.6 * (1 - p));
                }
            }
        };
    }

    /** A Spell held aloft and charging: the table dims, a halo pulses and a ring of runes turns. */
    private Live charge(Staging.Charge c) {
        Color colour = colour(c.tone(), 1);
        double cx = c.at().getCenterX(), cy = c.at().getCenterY();
        var dim = new javafx.scene.shape.Rectangle(-40, -40, layer.getWidth() + 80, layer.getHeight() + 80);
        dim.setFill(Color.rgb(10, 6, 2, .31));
        Circle halo = new Circle(cx, cy, 1);
        halo.setBlendMode(BlendMode.ADD);
        Circle runes = new Circle(cx, cy, 1);
        runes.setFill(null);
        runes.setStroke(colour.brighter().deriveColor(0, 1, 1, .8));
        runes.setStrokeWidth(2);
        runes.setStrokeLineCap(StrokeLineCap.ROUND);
        runes.getStrokeDashArray().setAll(9.0, 7.0);
        CardNode card = new CardNode(FxKit.face(c.spell(), List.of(), c.at().width, c.at().height, true, pixelScale),
                c.at().width, c.at().height);
        card.setEffect(new DropShadow(BlurType.GAUSSIAN, colour, 34, .32, 0, 0));
        return new Live() {
            { node.getChildren().addAll(dim, halo, runes, card); }
            @Override void update(double p) {
                double pulse = .5 + .5 * Math.sin(p * Math.PI * 5);
                double radius = Math.max(c.at().width, c.at().height) * (.62 + .05 * pulse);
                halo.setRadius(radius * 1.3);
                halo.setFill(new RadialGradient(0, 0, cx, cy, radius * 1.3, false, CycleMethod.NO_CYCLE,
                        new Stop(0, colour(c.tone(), (120 + 60 * pulse) / 255)), new Stop(1, colour(c.tone(), 0))));
                runes.setRadius(radius);
                runes.setStrokeDashOffset(-p * 120);
                card.pose(cx, cy - 4 * Math.sin(p * Math.PI * 2), Math.sin(p * Math.PI * 2) * .03, 1, 0);
            }
        };
    }

    /** A glowing bolt with a fading trail, arcing from a Spell to its target. */
    private Live bolt(Staging.Bolt b) {
        Circle[] trail = new Circle[7];
        Circle glow = new Circle(22, colour(b.tone(), .55));
        glow.setEffect(new GaussianBlur(18));
        return new Live() {
            {
                node.setBlendMode(BlendMode.ADD);
                node.getChildren().add(glow);
                for (int i = trail.length - 1; i >= 0; i--) {
                    trail[i] = new Circle(11 - i * 1.3, i == 0 ? Color.WHITE : colour(b.tone(), (220 - i * 28) / 255.0));
                    node.getChildren().add(trail[i]);
                }
            }
            @Override void update(double p) {
                double e = p * p * p;
                for (int i = 0; i < trail.length; i++) {
                    double q = Math.max(0, e - i * .045);
                    trail[i].setCenterX(b.from().getX() + (b.to().getX() - b.from().getX()) * q);
                    trail[i].setCenterY(b.from().getY() + (b.to().getY() - b.from().getY()) * q - Math.sin(Math.PI * q) * 30);
                }
                glow.setCenterX(trail[0].getCenterX());
                glow.setCenterY(trail[0].getCenterY());
            }
        };
    }

    /** A Spell spent: the card fades and rises as embers drift off it. */
    private Live dissolve(Staging.Dissolve d) {
        Random random = new Random(d.seed());
        CardNode card = card(d.spell(), List.of(), d.at());
        Group embers = new Group();
        embers.setBlendMode(BlendMode.ADD);
        double[][] motion = new double[22][3];
        for (double[] m : motion) {
            m[0] = random.nextDouble(); m[1] = random.nextDouble(); m[2] = .5 + random.nextDouble();
            embers.getChildren().add(new Circle(1, colour(d.tone(), 1).brighter()));
        }
        return new Live() {
            { node.getChildren().addAll(card, embers); }
            @Override void update(double p) {
                card.pose(d.at().getCenterX(), d.at().getCenterY() - 18 * p, 0, 1 - .1 * p, 0);
                card.setOpacity(1 - p);
                for (int i = 0; i < motion.length; i++) {
                    Circle ember = (Circle) embers.getChildren().get(i);
                    ember.setCenterX(d.at().x + motion[i][0] * d.at().width);
                    ember.setCenterY(d.at().y + motion[i][1] * d.at().height - 70 * p * motion[i][2]);
                    ember.setRadius(Math.max(.01, 3.2 * (1 - p) * motion[i][2]));
                    ember.setOpacity(.86 * (1 - p));
                }
            }
        };
    }

    /** An expanding ring, for impacts. */
    private Live ring(Staging.Ring r) {
        Ellipse ring = new Ellipse(r.x(), r.y(), 1, 1);
        ring.setFill(null);
        ring.setStroke(colour(r.tone(), 1));
        ring.setStrokeWidth(3);
        ring.setEffect(new DropShadow(BlurType.GAUSSIAN, colour(r.tone(), .8), 12, .3, 0, 0));
        return new Live() {
            { node.getChildren().add(ring); }
            @Override void update(double p) {
                double radius = Math.max(1, r.radius() * easeOutCubic(p));
                ring.setRadiusX(radius);
                ring.setRadiusY(radius * .35);
                ring.setOpacity(.86 * (1 - p));
            }
        };
    }

    /** Sparks thrown out from a point under a little gravity. */
    private Live burst(Staging.Burst b) {
        Random random = new Random(b.seed());
        double[][] sparks = new double[b.count()][3];
        Color colour = colour(b.tone(), 1);
        return new Live() {
            {
                node.setBlendMode(BlendMode.ADD);
                for (double[] spark : sparks) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    spark[0] = Math.cos(angle); spark[1] = Math.sin(angle); spark[2] = .4 + random.nextDouble() * .6;
                    node.getChildren().add(new Circle(1, colour));
                }
            }
            @Override void update(double p) {
                double e = easeOutCubic(p);
                for (int i = 0; i < sparks.length; i++) {
                    Circle spark = (Circle) node.getChildren().get(i);
                    spark.setCenterX(b.x() + sparks[i][0] * b.reach() * sparks[i][2] * e);
                    spark.setCenterY(b.y() + sparks[i][1] * b.reach() * sparks[i][2] * e + 40 * p * p);
                    spark.setRadius(3.4 * (1 - p) + .6);
                    spark.setOpacity(.94 * (1 - p));
                }
            }
        };
    }

    /** A card blown apart: its face cut into shards that fly outward and fall, after a white flash. */
    private Live shatter(Staging.Shatter s) {
        Rectangle at = s.at();
        Image image = FxKit.face(s.card(), s.marks(), at.width, at.height, false, 2);
        int cols = 3, rows = 4;
        double w = at.width / (double) cols, h = at.height / (double) rows;
        Random random = new Random(s.seed());
        double[][] motion = new double[cols * rows][3];
        ImageView[] shards = new ImageView[cols * rows];
        for (int c = 0; c < cols; c++)
            for (int r = 0; r < rows; r++) {
                double[] m = motion[c * rows + r];
                m[0] = random.nextDouble() * 2 - 1; m[1] = -random.nextDouble(); m[2] = random.nextDouble() * 2 - 1;
                ImageView shard = new ImageView(image);
                shard.setViewport(new Rectangle2D(c * w * 2, r * h * 2, w * 2, h * 2));
                shard.setFitWidth(w);
                shard.setFitHeight(h);
                shard.setLayoutX(-w / 2);
                shard.setLayoutY(-h / 2);
                shards[c * rows + r] = shard;
            }
        var flash = new javafx.scene.shape.Rectangle(at.x - 4, at.y - 4, at.width + 8, at.height + 8);
        flash.setArcWidth(12);
        flash.setArcHeight(12);
        flash.setFill(WHITE);
        flash.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().addAll(shards); node.getChildren().add(flash); }
            @Override void update(double p) {
                for (int c = 0; c < cols; c++)
                    for (int r = 0; r < rows; r++) {
                        double[] m = motion[c * rows + r];
                        ImageView shard = shards[c * rows + r];
                        shard.setTranslateX(at.x + c * w + w / 2 + (c - (cols - 1) / 2.0) * 55 * p + m[0] * 50 * p);
                        shard.setTranslateY(at.y + r * h + h / 2 + (r - (rows - 1) / 2.0) * 30 * p + (m[1] * 90 + 260 * p) * p);
                        shard.setRotate(Math.toDegrees(m[2] * 2.4 * p));
                        shard.setOpacity(Math.max(0, 1 - p * 1.1));
                    }
                flash.setOpacity(p < .18 ? .9 * (1 - p / .18) : 0);
            }
        };
    }

    /** A disable taking hold: the card shudders while a grey veil sweeps down and chains cross it. */
    private Live chains(Staging.Chains c) {
        Rectangle at = c.at();
        CardNode card = card(c.card(), c.marks(), at);
        var veil = new javafx.scene.shape.Rectangle(at.x, at.y, at.width, at.height);
        veil.setArcWidth(9);
        veil.setArcHeight(9);
        veil.setFill(Color.rgb(60, 60, 72, .43));
        var veilClip = new javafx.scene.shape.Rectangle(at.x - 12, at.y - 4, at.width + 24, 0);
        veil.setClip(veilClip);
        Group links = new Group();
        for (int i = 0; i < 9; i++)
            for (int side = 0; side < 2; side++) {
                double t = i / 8.0, y = at.y + at.height * t;
                double x = side == 0 ? at.x + at.width * t : at.getMaxX() - at.width * t;
                // Each link inked dark, then lit, so the chain reads over any artwork.
                for (boolean lit : new boolean[]{false, true}) {
                    Ellipse link = new Ellipse(x, y, 8, 5);
                    link.setFill(null);
                    link.setStroke(lit ? Color.rgb(222, 222, 236) : Color.rgb(30, 30, 38));
                    link.setStrokeWidth(lit ? 2.4 : 5);
                    links.getChildren().add(link);
                }
            }
        links.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .6), 6, 0, 0, 2));
        return new Live() {
            { node.getChildren().addAll(card, veil, links); }
            @Override void update(double p) {
                double shudder = Math.sin(p * Math.PI * 9) * 5 * (1 - p);
                card.pose(at.getCenterX() + shudder, at.getCenterY(), 0, 1, 0);
                veil.setTranslateX(shudder);
                veilClip.setHeight((at.height + 8) * Math.min(1, p * 2.2));
                double drawn = Math.max(0, Math.min(1, (p - .25) / .5));
                for (int i = 0; i < links.getChildren().size(); i++)
                    links.getChildren().get(i).setVisible(i / 4 < 9 * drawn);
                links.setTranslateX(shudder);
                links.setOpacity(Math.min(1, 1.6 - p) * .94);
            }
        };
    }

    /**
     * A war hammer over a card: it appears, winds up slowly, swings down fast (with a faint trail
     * of its own path) to strike the card's centre at {@code strike}, rebounds a little and fades.
     */
    private Live hammer(Staging.Hammer h, int duration) {
        Rectangle at = h.at();
        double length = at.height * 1.05, headW = at.height * .5, headH = at.height * .22;
        // The grip sits right of and a little below the card, so the head lands on its centre.
        double reach = length + headH * .3, dx = .96, dy = .28, norm = Math.hypot(dx, dy);
        double gripX = at.getCenterX() + reach * dx / norm, gripY = at.getCenterY() + reach * dy / norm;
        double impact = Math.toDegrees(Math.atan2(-dx, dy)), windUp = impact + 78;
        double strikeAt = h.strike() / (double) duration;
        Group[] copies = new Group[3];
        // Turned about the grip, as a hammer swings from the hand; setRotate would pivot on the bounds' centre.
        Rotate[] swings = new Rotate[copies.length];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = hammerShape(length, headW, headH);
            swings[i] = new Rotate(0, 0, 0);
            copies[i].getTransforms().add(swings[i]);
            copies[i].setTranslateX(gripX);
            copies[i].setTranslateY(gripY);
            copies[i].setOpacity(i == 0 ? 1 : .22 / i);
        }
        copies[0].setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .6), 18, .1, 8, 14));
        Circle flash = new Circle(at.getCenterX(), at.getCenterY(), at.width * .45, Color.rgb(255, 244, 214));
        flash.setEffect(new GaussianBlur(24));
        flash.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().addAll(copies[2], copies[1], copies[0], flash); }
            @Override void update(double p) {
                for (int i = 0; i < copies.length; i++) {
                    // Trailing copies show where the head was a moment ago, only while it swings fast.
                    double q = Math.max(0, p - i * .025);
                    swings[i].setAngle(angle(q));
                    copies[i].setVisible(i == 0 || q > strikeAt * .62 && q < strikeAt);
                }
                double in = Math.min(1, p / (strikeAt * .25)), out = p < strikeAt + .1 ? 1 : 1 - (p - strikeAt - .1) / (.9 - strikeAt);
                copies[0].setOpacity(Math.max(0, Math.min(in, out)));
                double since = (p - strikeAt) / (1 - strikeAt);
                flash.setOpacity(since < 0 || since > .25 ? 0 : .9 * (1 - since / .25));
            }

            /** Wind up (eased out), swing (accelerating into the blow), then a damped rebound. */
            private double angle(double p) {
                double lift = .62 * strikeAt;
                if (p < lift) return impact + (windUp - impact) * easeOutCubic(p / lift);
                if (p < strikeAt) { double q = (p - lift) / (strikeAt - lift); return windUp + (impact - windUp) * q * q * q; }
                double t = (p - strikeAt) * duration;
                return impact + 14 * Math.exp(-t / 90) * Math.sin(t / 40);
            }
        };
    }

    /** A hammer drawn with its grip at the origin and its head straight up. */
    private static Group hammerShape(double length, double headW, double headH) {
        double handleW = Math.max(6, headH * .34);
        var handle = new javafx.scene.shape.Rectangle(-handleW / 2, -length, handleW, length);
        handle.setArcWidth(handleW);
        handle.setArcHeight(handleW);
        handle.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#5A3A1E")),
                new Stop(.45, Color.web("#A0703F")), new Stop(1, Color.web("#4A2E16"))));
        var grip = new javafx.scene.shape.Rectangle(-handleW * .65, -length * .3, handleW * 1.3, length * .3);
        grip.setArcWidth(handleW);
        grip.setArcHeight(handleW);
        grip.setFill(Color.web("#3B2618"));
        Group wraps = new Group();
        for (int i = 1; i < 6; i++) {
            Line wrap = new Line(-handleW * .65, -length * .05 * i, handleW * .65, -length * .05 * i - handleW * .4);
            wrap.setStroke(Color.web("#6B4A2E"));
            wrap.setStrokeWidth(1.4);
            wraps.getChildren().add(wrap);
        }
        double headY = -length - headH * .55;
        var head = new javafx.scene.shape.Rectangle(-headW / 2, headY, headW, headH);
        head.setArcWidth(headH * .3);
        head.setArcHeight(headH * .3);
        head.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#A9ADB3")),
                new Stop(.35, Color.web("#5E6268")), new Stop(1, Color.web("#2B2D31"))));
        head.setStroke(Color.web("#1C1D20"));
        head.setStrokeWidth(1.5);
        // The striking face, lit along its edge.
        var face = new javafx.scene.shape.Rectangle(-headW / 2 - headH * .12, headY - headH * .08, headH * .3, headH * 1.16);
        face.setArcWidth(headH * .15);
        face.setArcHeight(headH * .15);
        face.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#D9DCE0")),
                new Stop(1, Color.web("#6A6E74"))));
        var band = new javafx.scene.shape.Rectangle(-handleW * .8, headY + headH - 2, handleW * 1.6, headH * .3);
        band.setFill(Color.web("#3A3C40"));
        return new Group(handle, grip, wraps, band, head, face);
    }

    /** Cracks running out from the point of the blow while the card shudders, before it breaks. */
    private Live cracks(Staging.Cracks c) {
        Rectangle at = c.at();
        CardNode card = card(c.card(), c.marks(), at);
        Random random = new Random(c.seed());
        double cx = at.getCenterX(), cy = at.getCenterY(), reach = Math.hypot(at.width, at.height) * .6;
        List<double[]> branches = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double heading = i * Math.PI * 2 / 7 + random.nextDouble() * .6;
            double[] points = new double[12];
            double x = cx, y = cy;
            for (int k = 0; k < 6; k++) {
                heading += (random.nextDouble() - .5) * .9;
                double step = reach / 6 * (.7 + random.nextDouble() * .6);
                x += Math.cos(heading) * step;
                y += Math.sin(heading) * step;
                points[k * 2] = x;
                points[k * 2 + 1] = y;
            }
            branches.add(points);
        }
        Group fissures = new Group();
        List<Polyline[]> lines = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) {
            Polyline dark = new Polyline(), light = new Polyline();
            dark.setStroke(Color.rgb(20, 12, 6, .9));
            dark.setStrokeWidth(3.2);
            light.setStroke(Color.rgb(255, 226, 170, .95));
            light.setStrokeWidth(1.1);
            for (Polyline line : new Polyline[]{dark, light}) line.setStrokeLineCap(StrokeLineCap.ROUND);
            fissures.getChildren().addAll(dark, light);
            lines.add(new Polyline[]{dark, light});
        }
        var clip = new javafx.scene.shape.Rectangle(at.x, at.y, at.width, at.height);
        clip.setArcWidth(9);
        clip.setArcHeight(9);
        fissures.setClip(clip);
        fissures.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 190, 110, .6), 6, .2, 0, 0));
        return new Live() {
            { node.getChildren().addAll(card, fissures); }
            @Override void update(double p) {
                double shudder = Math.sin(p * Math.PI * 11) * 4 * (1 - p), grown = easeOutCubic(Math.min(1, p / .7));
                card.pose(cx + shudder, cy, Math.sin(p * Math.PI * 7) * .02 * (1 - p), 1, 0);
                fissures.setTranslateX(shudder);
                for (int i = 0; i < branches.size(); i++) {
                    double[] points = branches.get(i);
                    List<Double> drawn = new ArrayList<>(List.of(cx, cy));
                    double segments = grown * 6;
                    for (int k = 0; k < 6 && k < segments; k++) {
                        double px = k == 0 ? cx : points[k * 2 - 2], py = k == 0 ? cy : points[k * 2 - 1];
                        double part = Math.min(1, segments - k);
                        drawn.add(px + (points[k * 2] - px) * part);
                        drawn.add(py + (points[k * 2 + 1] - py) * part);
                    }
                    for (Polyline line : lines.get(i)) line.getPoints().setAll(drawn);
                }
            }
        };
    }

    /** A diagonal light sweeping across a card, as a Landmark wakes. */
    private Live shine(Staging.Shine s) {
        Rectangle at = s.at();
        var band = new javafx.scene.shape.Rectangle(-at.width * .3, at.y - at.height, at.width * .6, at.height * 3);
        band.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255, 248, 225, 0)),
                new Stop(.5, Color.rgb(255, 248, 225, .67)), new Stop(1, Color.rgb(255, 248, 225, 0))));
        band.setRotate(-20);
        var clip = new javafx.scene.shape.Rectangle(at.x, at.y, at.width, at.height);
        clip.setArcWidth(9);
        clip.setArcHeight(9);
        Group sweep = new Group(band);
        sweep.setClip(clip);
        sweep.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().add(sweep); }
            @Override void update(double p) { band.setTranslateX(at.x - at.width * .4 + at.width * 1.8 * easeInOutCubic(p)); }
        };
    }

    /** A pair link drawing itself from one card to the other with a spark at its head, then its bonus. */
    private Live link(Staging.Link l) {
        Color colour = colour(l.tone(), 1);
        Polyline line = new Polyline();
        line.setStroke(colour);
        line.setStrokeWidth(3);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        line.setEffect(new DropShadow(BlurType.GAUSSIAN, colour(l.tone(), .7), 10, .25, 0, 0));
        Circle head = new Circle(4, Color.WHITE);
        head.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.WHITE, 12, .5, 0, 0));
        Text bonus = popText(l.bonus(), colour, 17);
        double ax = l.a().getX(), ay = l.a().getY(), bx = l.b().getX(), by = l.b().getY();
        double cx = (ax + bx) / 2, cy = ay + l.sag();
        return new Live() {
            { node.getChildren().addAll(line, head, bonus); }
            @Override void update(double p) {
                double e = easeOutCubic(Math.min(1, p / .45));
                List<Double> points = new ArrayList<>();
                for (int i = 0; i <= 24; i++) {
                    double t = e * i / 24;
                    points.add((1 - t) * (1 - t) * ax + 2 * (1 - t) * t * cx + t * t * bx);
                    points.add((1 - t) * (1 - t) * ay + 2 * (1 - t) * t * cy + t * t * by);
                }
                line.getPoints().setAll(points);
                line.setOpacity(p < .8 ? .94 : .94 * (1 - p) / .2);
                head.setVisible(e < 1);
                head.setCenterX(points.get(points.size() - 2));
                head.setCenterY(points.get(points.size() - 1));
                bonus.setVisible(p >= .45);
                if (p >= .45) pop(bonus, cx, ay + l.sag() * .6 + 16, (p - .45) / .55);
            }
        };
    }

    private static Text popText(String text, Color colour, double size) {
        Text label = new Text(text);
        label.setFont(FxKit.display(size));
        label.setFill(colour);
        label.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 20, 12, .85), 6, .4, 1.5, 2));
        return label;
    }

    /** A number popping in with a springy overshoot, floating up and fading. */
    private static void pop(Text label, double x, double y, double p) {
        double scale = Math.max(.01, p < .3 ? easeOutBack(p / .3) : 1);
        label.setX(x - label.getLayoutBounds().getWidth() / 2);
        label.setY(y - 26 * p);
        label.setScaleX(scale);
        label.setScaleY(scale);
        label.setOpacity(p < .7 ? 1 : 1 - (p - .7) / .3);
    }

    /** A large centred title, sweeping in and out: round changes and combo call-outs. */
    private Live banner(Staging.Banner b) {
        double size = b.size();
        Color colour = colour(b.tone(), 1);
        Text title = new Text(b.title());
        title.setFont(FxKit.decorative(size));
        title.setFill(colour);
        title.setEffect(new DropShadow(BlurType.GAUSSIAN, colour(b.tone(), .55), 18, .2, 0, 0));
        double width = title.getLayoutBounds().getWidth();
        title.setX(-width / 2);
        boolean sub = b.subtitle() != null;
        var plate = new javafx.scene.shape.Rectangle(-width / 2 - 28, -size * .95, width + 56, size * (sub ? 1.95 : 1.35));
        plate.setArcWidth(14);
        plate.setArcHeight(14);
        plate.setFill(Color.rgb(20, 12, 6, .67));
        plate.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .5), 20, 0, 0, 6));
        Line top = rule(width, -size * .82, colour), bottom = rule(width, size * (sub ? .9 : .32), colour);
        Group content = new Group(plate, top, bottom, title);
        if (sub) {
            Text subtitle = new Text(b.subtitle());
            subtitle.setFont(FxKit.bold(size * .42));
            subtitle.setFill(Color.web("#F4E6C6"));
            subtitle.setX(-subtitle.getLayoutBounds().getWidth() / 2);
            subtitle.setY(size * .62);
            content.getChildren().add(subtitle);
        }
        content.setTranslateX(b.x());
        content.setTranslateY(b.y());
        return new Live() {
            { node.getChildren().add(content); }
            @Override void update(double p) {
                double in = Math.max(.01, easeOutBack(Math.min(1, p / .22)));
                content.setScaleX(in);
                content.setScaleY(in);
                content.setOpacity(Math.max(0, p < .8 ? Math.min(1, p / .12) : 1 - (p - .8) / .2));
            }
        };
    }

    private static Line rule(double width, double y, Color colour) {
        Line line = new Line(-width / 2 - 18, y, width / 2 + 18, y);
        line.setStroke(colour.deriveColor(0, 1, 1, .78));
        line.setStrokeWidth(1.5);
        return line;
    }

    /** Falling gold flakes over the whole table, for a victory. */
    private Live confetti(Staging.Confetti c) {
        Random random = new Random(c.seed());
        Rectangle area = c.area();
        double[][] flakes = new double[90][4];
        for (double[] f : flakes) {
            f[0] = area.x + random.nextDouble() * area.width; f[1] = -random.nextDouble() * area.height * .5;
            f[2] = .6 + random.nextDouble(); f[3] = random.nextDouble() * 6;
        }
        return new Live() {
            {
                for (double[] f : flakes) {
                    var flake = new javafx.scene.shape.Rectangle(-3.5, -2, 7, 4);
                    flake.setFill(f[3] > 3 ? Color.web("#F4C766") : Color.web("#FFF1CF"));
                    node.getChildren().add(flake);
                }
            }
            @Override void update(double p) {
                for (int i = 0; i < flakes.length; i++) {
                    double[] f = flakes[i];
                    Node flake = node.getChildren().get(i);
                    flake.setTranslateX(f[0] + Math.sin(p * 9 + f[3]) * 18);
                    flake.setTranslateY(f[1] + area.height * 1.2 * p * f[2]);
                    flake.setRotate(Math.toDegrees(p * 8 + f[3]));
                    // Flakes turn in the air: their width shrinks and grows as they tumble.
                    flake.setScaleX(Math.cos(p * 14 + f[3] * 2));
                    flake.setOpacity(.9 * (1 - p * .7));
                }
            }
        };
    }

    // ---------------------------------------------------------------- easing and colour

    static double easeOutCubic(double p) { return 1 - Math.pow(1 - p, 3); }
    static double easeInOutCubic(double p) { return p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2; }
    /** Overshoots then settles, the springy "bounce" of a number popping. */
    static double easeOutBack(double p) { double c = 1.9; return 1 + (c + 1) * Math.pow(p - 1, 3) + c * Math.pow(p - 1, 2); }

    static Color colour(Tone tone, double alpha) { return FxKit.rgb(tone.rgb, Math.max(0, Math.min(1, alpha))); }
}
