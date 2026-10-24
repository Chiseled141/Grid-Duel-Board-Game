package deckduel.client.ui;

import deckduel.client.ScoringSounds;
import deckduel.client.ui.Staging.Tone;
import deckduel.core.card.CardDef;
import deckduel.core.card.Suppression;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import javafx.scene.shape.Polygon;
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
    /** Warm candlelight, ember orange and table dust: the light sources of a room lit by fire. */
    private static final Color CANDLE = Color.web("#FFE7A8"), EMBER = Color.web("#FFB347"), DUST = Color.web("#E2CEA8");

    private final Pane layer;
    private final ScoringSounds sounds;
    private final List<Run> runs = new ArrayList<>();
    private double pixelScale = 1;
    /** This frame's table shake, summed from every shake step playing; see {@link #shake}. */
    private double shakeX, shakeY;

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
                { Staging.Beat beat = beats.get(played++); sounds.play(new ScoringSounds.Cue(beat.kind(), beat.step())); }
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

    /** True while a notification toast (round banner or formation callout) is on screen. */
    boolean noticeShowing() {
        long now = System.nanoTime();
        return runs.stream().anyMatch(run -> {
            double elapsed = run.elapsed(now);
            return run.steps.stream().anyMatch(step -> step.at() <= elapsed && elapsed < step.end()
                    && (step.effect() instanceof Staging.Banner || step.effect() instanceof Staging.Callout));
        });
    }

    boolean hiddenInHand(long cardId) {
        long now = System.nanoTime();
        return runs.stream().anyMatch(run -> run.staging.hiddenInHand(cardId, run.elapsed(now)));
    }

    /** How far to shake the table's cards and effects this frame, in pixels: {x, y}. */
    double[] shake() { return new double[] {shakeX, shakeY}; }

    /** Advances every sequence to {@code now} and plays the cues that have come due; false once all have finished. */
    boolean tick(long now) {
        shakeX = 0;
        shakeY = 0;
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
        else if (effect instanceof Staging.Tear t) image(t.card(), t.marks(), t.at());
        else if (effect instanceof Staging.Sigil g) { image(g.card(), g.was(), g.at()); image(g.card(), g.marks(), g.at()); }
        else if (effect instanceof Staging.Burn b) image(b.card(), List.of(), b.at());
        else if (effect instanceof Staging.Spiral sp) image(sp.card(), List.of(), larger(sp.from(), sp.into()));
        else if (effect instanceof Staging.Peek pk) image(null, List.of(), pk.pile());
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
        if (effect instanceof Staging.Chop c) return chop(c);
        if (effect instanceof Staging.Shake k) return shake(k);
        if (effect instanceof Staging.Dissolve d) return dissolve(d);
        if (effect instanceof Staging.Ring r) return ring(r);
        if (effect instanceof Staging.Burst b) return burst(b);
        if (effect instanceof Staging.Shatter s) return shatter(s);
        if (effect instanceof Staging.Tear t) return tear(t);
        if (effect instanceof Staging.Chains c) return chains(c);
        if (effect instanceof Staging.Impact i) return impact(i);
        if (effect instanceof Staging.Shine s) return shine(s);
        if (effect instanceof Staging.Lash l) return lash(l);
        if (effect instanceof Staging.Sigil g) return sigil(g);
        if (effect instanceof Staging.Beam b) return beam(b);
        if (effect instanceof Staging.Burn b) return burn(b);
        if (effect instanceof Staging.Tether t) return tether(t);
        if (effect instanceof Staging.Wellspring w) return wellspring(w);
        if (effect instanceof Staging.Vortex v) return vortex(v);
        if (effect instanceof Staging.Spiral sp) return spiral(sp);
        if (effect instanceof Staging.Peek pk) return peek(pk);
        if (effect instanceof Staging.Eye e) return eye(e);
        if (effect instanceof Staging.Link l) return link(l);
        if (effect instanceof Staging.Chip c) return chip(c);
        if (effect instanceof Staging.Banner b) return banner(b);
        if (effect instanceof Staging.Callout c) return callout(c);
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

    /**
     * A Spell held aloft and charging: the table dims, motes of light in the Spell's colour are
     * drawn in toward the card, and a warm glow swells behind it: the anticipation before its effect.
     */
    private Live charge(Staging.Charge c) {
        Color colour = colour(c.tone(), 1);
        double cx = c.at().getCenterX(), cy = c.at().getCenterY();
        double radius = Math.max(c.at().width, c.at().height) * .68;
        var dim = new javafx.scene.shape.Rectangle(-40, -40, layer.getWidth() + 80, layer.getHeight() + 80);
        dim.setFill(Color.rgb(8, 5, 2, .38));
        Circle halo = new Circle(cx, cy, radius * 1.3);
        halo.setBlendMode(BlendMode.ADD);
        Random random = new Random(c.spell().id().hashCode());
        double[][] motes = new double[26][3];
        Group drawn = new Group();
        drawn.setBlendMode(BlendMode.ADD);
        for (double[] m : motes) {
            m[0] = random.nextDouble() * Math.PI * 2; m[1] = radius * (1.2 + random.nextDouble() * .7); m[2] = random.nextDouble() * .5;
            drawn.getChildren().add(new Circle(1.2 + random.nextDouble() * 1.6, VfxKit.HOT.interpolate(colour, .5)));
        }
        CardNode card = new CardNode(FxKit.face(c.spell(), List.of(), c.at().width, c.at().height, true, pixelScale),
                c.at().width, c.at().height);
        card.setEffect(new DropShadow(BlurType.GAUSSIAN, colour.deriveColor(0, 1, 1.1, .85), 26, .25, 0, 0));
        return new Live() {
            { node.getChildren().addAll(dim, halo, drawn, card); }
            @Override void update(double p) {
                double pulse = .5 + .5 * Math.sin(p * Math.PI * 5);
                halo.setFill(new RadialGradient(0, 0, cx, cy, radius * 1.3, false, CycleMethod.NO_CYCLE,
                        new Stop(0, colour(c.tone(), .3 + .15 * pulse)), new Stop(1, colour(c.tone(), 0))));
                for (int i = 0; i < motes.length; i++) {
                    // Each mote spirals in toward the card and vanishes into it.
                    double age = (p + motes[i][2]) % 1, r = motes[i][1] * (1 - age), a = motes[i][0] + age * 1.4;
                    Node mote = drawn.getChildren().get(i);
                    mote.setTranslateX(cx + Math.cos(a) * r);
                    mote.setTranslateY(cy + Math.sin(a) * r);
                    mote.setOpacity(Math.sin(Math.PI * age));
                }
                card.pose(cx, cy - 4 * Math.sin(p * Math.PI * 2), Math.sin(p * Math.PI * 2) * .03, 1, 0);
            }
        };
    }

    /**
     * A war axe chops the card. It appears beside it and winds back, then swings down fast with a
     * hard smear of light behind its blade, bites and holds for a beat (the hit-stop) under a burst
     * of light, a shockwave and a spray of sparks thrown along the swing, recoils, rests and fades.
     * It comes from whichever side has more room, so it never swings in from off the table.
     */
    private Live chop(Staging.Chop c) {
        Rectangle at = c.at();
        double hitAt = PlayAnimator.CHOP_HIT / (double) PlayAnimator.CHOP, stop = .07;
        double cx = at.getCenterX(), cy = at.getCenterY(), reach = at.height * 1.05;
        int side = cx > layer.getWidth() * .6 ? -1 : 1;
        // The grip sits beside the card; the blade bites the card's middle.
        double from = side > 0 ? 20 : 160, impact = from + 180, raised = impact + 100 * side, back = raised + 25 * side;
        double gx = cx + Math.cos(Math.toRadians(from)) * reach, gy = cy + Math.sin(Math.toRadians(from)) * reach;
        ImageView axe = VfxSprites.axe(reach);
        Group blade = new Group(axe);
        // Swinging the other way, the blade is turned over so its edge still leads.
        blade.getTransforms().add(new Scale(1, side, 0, 0));
        Rotate swing = new Rotate(raised, 0, 0);
        Group arm = new Group(blade);
        arm.getTransforms().setAll(new javafx.scene.transform.Translate(gx, gy), swing);
        arm.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .65), 12, .2, 5, 10));
        Color heat = colour(Tone.EMBER, 1);
        VfxKit.Smear smear = new VfxKit.Smear(gx, gy, reach * .9, reach * .32, heat);
        double heading = impact - 90 * side;
        VfxKit.Flash flash = new VfxKit.Flash(cx, cy, at.width * .7, heat, 6, heading);
        VfxKit.Shockwave wave = new VfxKit.Shockwave(cx, cy, at.width * 1.4, .45, heat);
        VfxKit.Sparks sparks = new VfxKit.Sparks(cx, cy, heading, 70, 26, at.width * 1.6, heat, Math.round(cx));
        return new Live() {
            { node.getChildren().addAll(smear, arm, wave, flash, sparks); }
            @Override void update(double p) {
                double windUp = hitAt * .6, angle;
                if (p < windUp) angle = raised + (back - raised) * easeOutCubic(p / windUp);
                else if (p < hitAt) { double q = (p - windUp) / (hitAt - windUp); angle = back + (impact - back) * q * q * q; }
                else if (p < hitAt + stop) angle = impact;
                else angle = impact + 7 * side * Math.sin(Math.min(1, (p - hitAt - stop) / .12) * Math.PI);
                swing.setAngle(angle);
                arm.setOpacity(p < .06 ? p / .06 : p > .82 ? 1 - (p - .82) / .18 : 1);
                // The smear trails the blade through the fast part of the swing and burns out after the bite.
                boolean swinging = p >= windUp;
                smear.setVisible(swinging && p < hitAt + stop + .1);
                if (swinging) smear.sweep(Math.min(back, angle + 70 * side * Math.min(1, (p - windUp) / (hitAt - windUp))) , angle);
                smear.setOpacity(p < hitAt + stop ? 1 : 1 - (p - hitAt - stop) / .1);
                double since = Math.max(0, (p - hitAt) / (1 - hitAt));
                for (Node light : List.of(flash, wave, sparks)) light.setVisible(p >= hitAt);
                flash.at(Math.min(1, since * 2.2));
                wave.at(Math.min(1, since * 1.8));
                sparks.at(Math.min(1, since * 1.4));
            }
        };
    }

    /** The table shaken for a heavy blow: a fast, fading tremor added to every shake playing. */
    private Live shake(Staging.Shake k) {
        return new Live() {
            @Override void update(double p) {
                double amplitude = k.strength() * Math.pow(1 - p, 2);
                shakeX += amplitude * Math.sin(p * 95);
                shakeY += amplitude * .7 * Math.cos(p * 77);
            }
        };
    }

    /** A Spell spent: the card chars, fades and rises as embers drift off it. */
    private Live dissolve(Staging.Dissolve d) {
        Random random = new Random(d.seed());
        CardNode card = card(d.spell(), List.of(), d.at());
        Group embers = new Group();
        embers.setBlendMode(BlendMode.ADD);
        double[][] motion = new double[22][3];
        for (double[] m : motion) {
            m[0] = random.nextDouble(); m[1] = random.nextDouble(); m[2] = .5 + random.nextDouble();
            embers.getChildren().add(new Circle(1, m[2] > 1 ? EMBER : colour(d.tone(), 1).interpolate(EMBER, .5)));
        }
        return new Live() {
            { node.getChildren().addAll(card, embers); }
            @Override void update(double p) {
                card.pose(d.at().getCenterX(), d.at().getCenterY() - 18 * p, 0, 1 - .1 * p, 0);
                card.setOpacity(1 - p);
                card.shade.setBrightness(-.45 * p);
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
        // A shockwave of dust tinted by the Spell, not a neon hoop.
        ring.setStroke(colour(r.tone(), 1).interpolate(DUST, .45));
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
        // Sparks burn ember-hot, only tinted by the Spell's colour.
        Color colour = colour(b.tone(), 1).interpolate(EMBER, .45);
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

    /**
     * A chopped card falls apart: it splits in two along a slanting cut whose edges glow like hot
     * iron, the halves slide apart along the cut, tumble and fall, and embers drift off the cut.
     */
    private Live shatter(Staging.Shatter s) {
        Rectangle at = s.at();
        Image image = FxKit.face(s.card(), s.marks(), at.width, at.height, false, 2);
        double cx = at.getCenterX(), cy = at.getCenterY();
        // The cut slants one way or the other, through the card's middle.
        double slant = Math.toRadians(s.seed() % 2 == 0 ? 62 : 118), ux = Math.cos(slant), uy = Math.sin(slant);
        double far = Math.hypot(at.width, at.height);
        double ax = cx - ux * far, ay = cy - uy * far, bx = cx + ux * far, by = cy + uy * far;
        Group[] halves = new Group[2];
        for (int k = 0; k < 2; k++) {
            ImageView face = new ImageView(image);
            face.setFitWidth(at.width);
            face.setFitHeight(at.height);
            face.setX(at.x);
            face.setY(at.y);
            double nx = -uy * far * (k == 0 ? 1 : -1), ny = ux * far * (k == 0 ? 1 : -1);
            face.setClip(new Polygon(ax, ay, bx, by, bx + nx, by + ny, ax + nx, ay + ny));
            halves[k] = new Group(face);
            halves[k].setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .5), 10, 0, 0, 5));
        }
        Line cut = new Line(cx - ux * at.height * .6, cy - uy * at.height * .6, cx + ux * at.height * .6, cy + uy * at.height * .6);
        cut.setStroke(VfxKit.HOT);
        cut.setStrokeWidth(3);
        cut.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.web("#FF8A3D"), 14, .6, 0, 0));
        cut.setBlendMode(BlendMode.ADD);
        VfxKit.Sparks embers = new VfxKit.Sparks(cx, cy, -90, 140, 18, at.height * .8, EMBER, s.seed());
        return new Live() {
            { node.getChildren().addAll(halves); node.getChildren().addAll(cut, embers); }
            @Override void update(double p) {
                double apart = easeOutCubic(Math.min(1, p * 1.5));
                for (int k = 0; k < 2; k++) {
                    double sign = k == 0 ? 1 : -1;
                    // Each half slides along the cut, away from the other, then drops and turns.
                    halves[k].setTranslateX(sign * (-uy * 16 * apart + ux * 22 * apart) + sign * 10 * p);
                    halves[k].setTranslateY(sign * (ux * 16 * apart + uy * 22 * apart) + 160 * p * p);
                    halves[k].setRotate(sign * 24 * p * p);
                    halves[k].setOpacity(p < .55 ? 1 : 1 - (p - .55) / .45);
                }
                cut.setOpacity(Math.max(0, 1 - p * 3));
                embers.at(p);
            }
        };
    }

    /**
     * A card torn in two: it strains and shakes while a glowing rip runs down it, then the halves
     * part, each spinning away with a pale torn edge, as paper scraps fly out and everything fades.
     */
    private Live tear(Staging.Tear t) {
        Rectangle at = t.at();
        Image image = image(t.card(), t.marks(), at);
        Random random = new Random(t.seed());
        int cuts = 14;
        double slant = (random.nextDouble() - .5) * at.width * .25;
        List<Double> rip = new ArrayList<>();
        for (int i = 0; i <= cuts; i++) {
            double f = i / (double) cuts;
            rip.add(at.getCenterX() + slant * (f - .5) + (i == 0 || i == cuts ? 0 : (random.nextDouble() - .5) * at.width * .14));
            rip.add(at.y - 2 + (at.height + 4) * f);
        }
        Group left = half(image, at, rip, at.x - 2), right = half(image, at, rip, at.getMaxX() + 2);
        Rotate leftTurn = new Rotate(0, at.x + at.width * .25, at.getCenterY()), rightTurn = new Rotate(0, at.x + at.width * .75, at.getCenterY());
        left.getTransforms().add(leftTurn);
        right.getTransforms().add(rightTurn);
        Polyline glow = new Polyline();
        glow.setStroke(Color.rgb(255, 244, 214));
        glow.setStrokeWidth(3);
        glow.setStrokeLineCap(StrokeLineCap.ROUND);
        glow.setEffect(new DropShadow(BlurType.GAUSSIAN, colour(Tone.EMBER, 1), 16, .5, 0, 0));
        glow.setBlendMode(BlendMode.ADD);
        double[][] scraps = new double[18][5];
        Group bits = new Group();
        for (double[] s : scraps) {
            int point = random.nextInt(rip.size() / 2);
            s[0] = rip.get(point * 2); s[1] = rip.get(point * 2 + 1);
            s[2] = (random.nextBoolean() ? 1 : -1) * (30 + random.nextDouble() * 70); s[3] = -40 - random.nextDouble() * 80;
            s[4] = (random.nextDouble() - .5) * 720;
            var scrap = new javafx.scene.shape.Rectangle(-3, -2, 3 + random.nextDouble() * 5, 2 + random.nextDouble() * 4);
            scrap.setFill(Color.rgb(236, 224, 196));
            bits.getChildren().add(scrap);
        }
        return new Live() {
            { node.getChildren().addAll(left, right, glow, bits); }
            @Override void update(double p) {
                double strain = Math.min(1, p / .3), open = Math.max(0, (p - .3) / .7);
                double shake = p < .3 ? Math.sin(p * 60) * 1.5 * strain : 0;
                double part = 1 - Math.pow(1 - open, 2), fall = open * open;
                left.setTranslateX(shake - 46 * part);
                right.setTranslateX(shake + 46 * part);
                left.setTranslateY(26 * part + 170 * fall);
                right.setTranslateY(14 * part + 190 * fall);
                leftTurn.setAngle(-20 * part);
                rightTurn.setAngle(24 * part);
                double fade = Math.max(0, 1 - Math.max(0, (p - .55) / .45));
                left.setOpacity(fade);
                right.setOpacity(fade);
                // The rip glows down the card as it strains, then flares out as the halves part.
                int shown = (int) Math.round(strain * cuts) + 1;
                glow.getPoints().setAll(rip.subList(0, Math.min(rip.size(), shown * 2)));
                glow.setTranslateX(shake);
                glow.setOpacity(p < .3 ? .55 + .45 * strain : Math.max(0, 1 - open * 4));
                for (int i = 0; i < scraps.length; i++) {
                    Node scrap = bits.getChildren().get(i);
                    double[] s = scraps[i];
                    scrap.setVisible(open > 0 && open < 1);
                    scrap.setTranslateX(s[0] + s[2] * open);
                    scrap.setTranslateY(s[1] + s[3] * open + 260 * open * open);
                    scrap.setRotate(s[4] * open);
                    scrap.setOpacity(1 - open);
                }
            }
        };
    }

    /** One side of a torn card: its face clipped along the rip, with a pale ragged paper edge. */
    private static Group half(Image image, Rectangle at, List<Double> rip, double side) {
        ImageView face = new ImageView(image);
        face.setFitWidth(at.width + CardFace.SHADOW_X);
        face.setFitHeight(at.height + CardFace.SHADOW_Y);
        // Placed by x/y, not layout, so the clip below shares the table coordinates of the rip.
        face.setX(at.x);
        face.setY(at.y);
        var outline = new javafx.scene.shape.Polygon();
        outline.getPoints().addAll(rip);
        outline.getPoints().addAll(side, at.getMaxY() + 2, side, at.y - 2.0);
        face.setClip(outline);
        Polyline edge = new Polyline();
        edge.getPoints().setAll(rip);
        edge.setStroke(Color.rgb(240, 230, 205));
        edge.setStrokeWidth(2.2);
        edge.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        Group half = new Group(face, edge);
        half.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .45), 10, 0, 0, 4));
        return half;
    }

    /** A disable taking hold: the card shudders while a grey veil sweeps down and chains cross it. */
    private Live chains(Staging.Chains c) {
        Rectangle at = c.at();
        CardNode card = card(c.card(), c.marks(), at);
        var veil = new javafx.scene.shape.Rectangle(at.x, at.y, at.width, at.height);
        veil.setArcWidth(9);
        veil.setArcHeight(9);
        veil.setFill(Color.rgb(20, 16, 14, .5));
        var veilClip = new javafx.scene.shape.Rectangle(at.x - 12, at.y - 4, at.width + 24, 0);
        veil.setClip(veilClip);
        Group links = new Group();
        double slope = Math.toDegrees(Math.atan2(at.height, at.width));
        for (int i = 0; i < 9; i++)
            for (int side = 0; side < 2; side++) {
                double t = i / 8.0, y = at.y + at.height * t;
                double x = side == 0 ? at.x + at.width * t : at.getMaxX() - at.width * t;
                // Iron links alternate flat and edge-on along each diagonal, as a real chain's do.
                ImageView link = VfxSprites.chainLink(i % 2 == 1);
                link.setTranslateX(x);
                link.setTranslateY(y);
                link.setRotate(side == 0 ? slope : -slope);
                links.getChildren().add(link);
            }
        links.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .6), 6, 0, 0, 2));
        // The chains lock with a flash and a spray of sparks where they cross.
        VfxKit.Flash lock = new VfxKit.Flash(at.getCenterX(), at.getCenterY(), at.width * .45, colour(Tone.STEEL, 1), 4, 45);
        VfxKit.Sparks clank = new VfxKit.Sparks(at.getCenterX(), at.getCenterY(), 0, 360, 14, at.width * .6, EMBER, at.x);
        return new Live() {
            { node.getChildren().addAll(card, veil, links, lock, clank); }
            @Override void update(double p) {
                double shudder = Math.sin(p * Math.PI * 5) * 2 * (1 - p);
                card.pose(at.getCenterX() + shudder, at.getCenterY(), 0, 1, 0);
                veil.setTranslateX(shudder);
                veilClip.setHeight((at.height + 8) * Math.min(1, p * 2.2));
                double drawn = Math.max(0, Math.min(1, (p - .25) / .5));
                for (int i = 0; i < links.getChildren().size(); i++)
                    links.getChildren().get(i).setVisible(i / 2 < 9 * drawn);
                links.setTranslateX(shudder);
                double locked = Math.max(0, (p - .75) / .25);
                lock.setVisible(p >= .75);
                clank.setVisible(p >= .75);
                lock.at(locked);
                clank.at(locked);
                links.setOpacity(Math.min(1, 1.6 - p) * .94);
            }
        };
    }

    /** A diagonal light sweeping across a card as its disables are cleared. */
    private Live shine(Staging.Shine s) {
        Rectangle at = s.at();
        var band = new javafx.scene.shape.Rectangle(-at.width * .3, at.y - at.height, at.width * .6, at.height * 3);
        band.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255, 226, 160, 0)),
                new Stop(.5, Color.rgb(255, 232, 180, .67)), new Stop(1, Color.rgb(255, 226, 160, 0))));
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

    /**
     * A pair link: a crackling filament of light leaps from one card to the other with a spark
     * where it starts and a flash where it lands, flickers for a beat, then its bonus. A +9 pair's
     * filament is gold, thicker and lands harder.
     */
    private Live link(Staging.Link l) {
        boolean gold = l.tone() == Tone.GOLD;
        Color colour = gold ? Color.web("#FFC85A") : Color.web("#FF9A4A");
        double ax = l.a().getX(), ay = l.a().getY(), bx = l.b().getX(), by = l.b().getY();
        double cx = (ax + bx) / 2;
        VfxKit.Filament arc = new VfxKit.Filament(ax, ay, bx, by, l.sag(), colour, gold ? 2.2 : 1.5, Math.round(ax * 7 + bx));
        VfxKit.Flash start = new VfxKit.Flash(ax, ay, gold ? 16 : 11, colour, 4, 45);
        VfxKit.Flash land = new VfxKit.Flash(bx, by, gold ? 26 : 16, colour, gold ? 6 : 4, 0);
        Text bonus = popText(l.bonus() == null ? "" : l.bonus(), colour, 17);
        return new Live() {
            { node.getChildren().addAll(arc, start, land, bonus); }
            @Override void update(double p) {
                double reach = easeOutCubic(Math.min(1, p / .3));
                arc.at(reach, (int) (p * 40));
                arc.setOpacity(p < .7 ? 1 : 1 - (p - .7) / .3);
                start.at(Math.min(1, p / .35));
                land.setVisible(p >= .3);
                land.at(Math.min(1, Math.max(0, (p - .3) / .4)));
                bonus.setVisible(p >= .45 && l.bonus() != null);
                if (p >= .45) pop(bonus, cx, ay + l.sag() * .6 + 16, (p - .45) / .55);
            }
        };
    }

    /**
     * A struck coin in the score rail's enamel and brass: it pops up where the points were earned,
     * hangs for a beat, then is flung in an accelerating arc to its place on the rail with a short
     * trail of sparks. A label too wide for a coin (a formation's bonus and multiplier) rides a
     * clipped-corner tag instead.
     */
    private Live chip(Staging.Chip c) {
        double radius = (c.big() ? 25 : 20) * c.scale();
        Color enamel = enamel(c.tone());
        Text label = new Text(c.text());
        label.setFont(FxKit.bold((c.big() ? 18 : 15) * c.scale()));
        label.setFill(c.tone() == Tone.GOLD ? TablePlaque.INK : TablePlaque.CREAM);
        label.setEffect(new DropShadow(BlurType.GAUSSIAN, c.tone() == Tone.GOLD ? Color.rgb(255, 240, 200, .5) : Color.rgb(10, 6, 2, .9),
                2, .6, 0, 1));
        label.setX(-label.getLayoutBounds().getWidth() / 2);
        label.setY(label.getLayoutBounds().getHeight() * .3);
        double width = label.getLayoutBounds().getWidth();
        Node edge;
        Group token;
        if (width > radius * 1.45) {
            double w = width + 30, h = radius * 1.6;
            edge = chamfer(-w / 2 + 3, -h / 2 + 3, w - 6, h - 6, 4);
            ((Polygon) edge).setFill(enamelFace(enamel));
            ((Polygon) edge).setStroke(TablePlaque.BRASS_DARK);
            token = new Group(brass(chamfer(-w / 2, -h / 2, w, h, 7)), edge, label);
        } else {
            // The milled rim is the only part that turns in flight, so the number stays readable.
            Circle milled = new Circle(radius - 1.8);
            milled.setFill(null);
            milled.setStroke(TablePlaque.BRASS_DARK);
            milled.setStrokeWidth(2.4);
            milled.getStrokeDashArray().setAll(1.4, 2.2);
            Circle face = new Circle(radius * .72, enamelFace(enamel));
            face.setStroke(TablePlaque.BRASS_DARK);
            face.setStrokeWidth(1.2);
            edge = milled;
            token = new Group(brass(new Circle(radius)), milled, face, label);
        }
        token.setEffect(new DropShadow(BlurType.GAUSSIAN, c.big() ? TablePlaque.BRASS.deriveColor(0, 1, 1, .7) : Color.rgb(0, 0, 0, .65),
                c.big() ? 22 : 9, .25, 0, 3));
        // Sparks struck off the coin while it flies.
        Circle[] trail = new Circle[5];
        Group streak = new Group();
        streak.setBlendMode(BlendMode.ADD);
        for (int i = 0; i < trail.length; i++) {
            trail[i] = new Circle(radius * (.34 - i * .05), TablePlaque.BRASS_LIGHT.deriveColor(0, 1, 1, .7 - i * .12));
            trail[i].setEffect(new GaussianBlur(3));
            streak.getChildren().add(trail[i]);
        }
        double ax = c.from().getX(), ay = c.from().getY(), bx = c.to().getX(), by = c.to().getY();
        // The arc rises above both ends, so a chip from the lower row still sweeps up and over.
        double cx = (ax + bx) / 2, cy = Math.min(ay, by) - 90;
        return new Live() {
            { node.getChildren().addAll(streak, token); }
            @Override void update(double p) {
                double flight = Math.max(0, (p - .35) / .65);
                for (int i = 0; i < trail.length; i++) {
                    double e = Math.pow(Math.max(0, flight - (i + 1) * .045), 2);
                    trail[i].setCenterX(arcX(e));
                    trail[i].setCenterY(arcY(e));
                    trail[i].setVisible(flight > (i + 1) * .045);
                }
                double e = flight * flight;
                double popped = p < .2 ? Math.max(.01, easeOutBack(p / .2)) : 1;
                double hover = p < .35 ? -14 * easeOutCubic(Math.min(1, p / .2)) : -14 * (1 - e);
                double size = popped * (1 - .25 * e) * (c.big() && p < .35 ? 1 + .08 * Math.sin(p * 60) : 1);
                token.setTranslateX(arcX(e));
                token.setTranslateY(arcY(e) + hover);
                token.setScaleX(size);
                token.setScaleY(size);
                if (edge instanceof Circle) edge.setRotate(540 * flight);
            }

            private double arcX(double e) { return (1 - e) * (1 - e) * ax + 2 * (1 - e) * e * cx + e * e * bx; }
            private double arcY(double e) { return (1 - e) * (1 - e) * ay + 2 * (1 - e) * e * cy + e * e * by; }
        };
    }

    /** The rail's enamel for a chip's tone: blue Unit points, red combos, gold, or plain iron. */
    private static Color enamel(Tone tone) {
        return switch (tone) {
            case AZURE -> TablePlaque.UNITS;
            case CRIMSON -> TablePlaque.COMBOS;
            case GOLD -> TablePlaque.GOLD;
            case ASH -> Color.web("#4A423D");
            default -> TablePlaque.IRON;
        };
    }

    private static RadialGradient enamelFace(Color enamel) {
        return new RadialGradient(0, 0, .35, .3, .85, true, CycleMethod.NO_CYCLE,
                new Stop(0, enamel.deriveColor(0, 1, 1.35, 1)), new Stop(1, enamel.deriveColor(0, 1, .55, 1)));
    }

    /** {@code shape} filled as worn brass with a dark outline, like the rail's trim. */
    private static <T extends javafx.scene.shape.Shape> T brass(T shape) {
        shape.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, TablePlaque.BRASS_LIGHT), new Stop(.55, TablePlaque.BRASS), new Stop(1, TablePlaque.BRASS_DARK)));
        shape.setStroke(TablePlaque.WOOD_LINE);
        shape.setStrokeWidth(1.4);
        return shape;
    }

    /** A rectangle with its corners cut at 45 degrees, the node twin of {@link TablePlaque#clipped}. */
    private static Polygon chamfer(double x, double y, double w, double h, double cut) {
        return new Polygon(x + cut, y, x + w - cut, y, x + w, y + cut, x + w, y + h - cut,
                x + w - cut, y + h, x + cut, y + h, x, y + h - cut, x, y + cut);
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

    /** A round heading on a small plaque in the gap between the rows. */
    private Live banner(Staging.Banner b) {
        return toast(toast(b.title(), null, b.tone(), Math.min(26, b.size() * .56)), b.x(), b.y());
    }

    /**
     * A new formation, as the same toast with its bonus in a badge, above its row. A multiplier is
     * then stamped beside it: a big gold "×2" drops from above, lands with a burst of light and
     * sparks while the table shakes, and stays until the toast leaves.
     */
    private Live callout(Staging.Callout c) {
        Group card = toast(c.title(), c.bonus(), c.tone(), 19);
        // The callout's anchor is its bottom edge, so it rests on the row's top edge.
        double y = c.at().getY() - card.getLayoutBounds().getHeight() / 2;
        Live plaque = toast(card, c.at().getX(), y);
        if (c.multiplier() <= 1) return plaque;
        Text stamp = new Text("×" + c.multiplier());
        stamp.setFont(FxKit.display(52));
        stamp.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#FFF1C4")), new Stop(.5, Color.web("#F2C35E")), new Stop(1, Color.web("#B8862E"))));
        stamp.setStroke(TablePlaque.WOOD_LINE);
        stamp.setStrokeWidth(2);
        stamp.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(20, 10, 4, .85), 10, .35, 0, 4));
        double sx = c.at().getX() + card.getLayoutBounds().getWidth() / 2 + 14 + stamp.getLayoutBounds().getWidth() / 2;
        stamp.setX(-stamp.getLayoutBounds().getWidth() / 2);
        stamp.setY(stamp.getLayoutBounds().getHeight() * .3);
        Group mark = new Group(stamp);
        mark.setTranslateX(sx);
        mark.setTranslateY(y);
        Color gold = colour(Tone.GOLD, 1);
        VfxKit.Flash flash = new VfxKit.Flash(sx, y, 70, gold, 8, 20);
        VfxKit.Shockwave wave = new VfxKit.Shockwave(sx, y, 150, .45, gold);
        VfxKit.Sparks sparks = new VfxKit.Sparks(sx, y, -90, 360, 24, 120, gold, Math.round(sx));
        double slam = PlayAnimator.SLAM_AT, drop = .07;
        return new Live() {
            { node.getChildren().addAll(plaque.node, wave, flash, sparks, mark); }
            @Override void update(double p) {
                plaque.update(p);
                // It falls in large and tilted, lands at its size on the stamp, then gives a small rebound.
                double fall = Math.max(0, Math.min(1, (p - slam + drop) / drop));
                double since = Math.max(0, (p - slam) / (1 - slam));
                double size = p < slam ? 2.4 - 1.4 * fall * fall : 1 + .12 * Math.sin(Math.min(1, since * 6) * Math.PI) * (1 - since);
                mark.setVisible(p >= slam - drop);
                mark.setScaleX(size);
                mark.setScaleY(size);
                mark.setRotate(p < slam ? -16 + 10 * fall : -6);
                mark.setOpacity(p < slam ? fall : p > .86 ? 1 - (p - .86) / .14 : 1);
                for (Node light : List.of(flash, wave, sparks)) light.setVisible(p >= slam);
                flash.at(Math.min(1, since * 3));
                wave.at(Math.min(1, since * 2.5));
                sparks.at(Math.min(1, since * 2));
            }
        };
    }

    /**
     * A notice in the table's object language ({@link TablePlaque}): a small iron plaque with
     * clipped corners and a brass inlay, the title in cream, and an
     * optional value on a cloth ribbon tag. Centred on (0, 0).
     */
    private static Group toast(String title, String badge, Tone tone, double size) {
        javafx.scene.text.Font heading = FxKit.display(size), value = FxKit.bold(size * .82);
        double pad = 22, gap = 14, headingWidth = FxKit.width(title, heading);
        double badgeWidth = badge == null ? 0 : FxKit.width(badge, value) + 20;
        double height = size * 1.3 + 18;
        // The ribbon's folded tails need room inside the plaque on both sides of the tag.
        double tail = size * 1.25 * .6;
        double width = pad + headingWidth + (badge == null ? 0 : gap + tail + badgeWidth + tail) + pad;
        var canvas = new javafx.scene.canvas.Canvas(width + 4, height + 4);
        var g = canvas.getGraphicsContext2D();
        TablePlaque.board(g, 2, 2, width, height, 8);
        double baseline = height / 2 + size * .36 + 2;
        g.setFont(heading);
        g.setFill(TablePlaque.CREAM);
        g.fillText(title, 2 + pad, baseline);
        if (badge != null) {
            double tagX = 2 + pad + headingWidth + gap + tail, tagH = size * 1.25, tagY = 2 + (height - tagH) / 2 - 2;
            // Gold-toned moments (a multiplier) get the blood-red ribbon; quieter ones a dark iron one.
            TablePlaque.ribbon(g, tagX, tagY, badgeWidth, tagH, tone == Tone.GOLD ? TablePlaque.RIBBON : Color.web("#3C3634"));
            g.setFont(value);
            g.setFill(TablePlaque.BRASS_LIGHT);
            g.fillText(badge, tagX + 10, tagY + tagH / 2 + size * .3);
        }
        canvas.setLayoutX(-(width + 4) / 2);
        canvas.setLayoutY(-(height + 4) / 2);
        canvas.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .5), 14, 0, 0, 6));
        return new Group(canvas);
    }

    /** Shows a toast at (x, y): it slides up into place as it fades in, holds, then fades away. */
    private Live toast(Group card, double x, double y) {
        card.setTranslateX(x);
        return new Live() {
            { node.getChildren().add(card); }
            @Override void update(double p) {
                double in = Math.min(1, p / .16), eased = 1 - Math.pow(1 - in, 3);
                double out = p < .86 ? 0 : (p - .86) / .14;
                card.setTranslateY(y + 14 * (1 - eased) - 6 * out);
                card.setOpacity(Math.min(eased, 1 - out));
            }
        };
    }

    /** Falling gold leaf and scraps of banner red over the whole table, for a victory. */
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
                    flake.setFill(f[3] > 4.2 ? TablePlaque.RIBBON.deriveColor(0, 1, 1.3, 1) : f[3] > 2 ? TablePlaque.BRASS_LIGHT : TablePlaque.GOLD);
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

    // ---------------------------------------------------------------- Spell signatures

    /** A chain flung out link by link, writhing as it flies and pulling straight as it arrives. */
    private Live lash(Staging.Lash l) {
        double dx = l.to().getX() - l.from().getX(), dy = l.to().getY() - l.from().getY(), length = Math.hypot(dx, dy);
        double angle = Math.toDegrees(Math.atan2(dy, dx)), nx = -dy / Math.max(1, length), ny = dx / Math.max(1, length);
        int count = Math.max(6, (int) (length / 15));
        Group links = new Group();
        for (int i = 0; i < count; i++) {
            // Iron links alternate flat and edge-on, as a real chain's do.
            ImageView link = VfxSprites.chainLink(i % 2 == 1);
            link.setRotate(angle);
            links.getChildren().add(link);
        }
        links.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .6), 6, .1, 0, 3));
        return new Live() {
            { node.getChildren().add(links); }
            @Override void update(double p) {
                double reach = Math.min(1, p * p * 1.25);
                for (int i = 0; i < count; i++) {
                    double t = (i + .5) / count * reach, wave = Math.sin(t * Math.PI * 3 - p * 9) * 10 * (1 - p);
                    Node link = links.getChildren().get(i);
                    link.setTranslateX(l.from().getX() + dx * t + nx * wave);
                    link.setTranslateY(l.from().getY() + dy * t + ny * wave);
                }
                links.setOpacity(p < .85 ? 1 : 1 - (p - .85) / .15);
            }
        };
    }

    /**
     * A painted seal of binding fades in over the card in violet light, turning and closing in, then
     * slams down onto it: a burst of light, a shockwave and sparks; the card shudders and its colour
     * drains for a moment as its new disable marks appear, and the seal burns into it and fades.
     */
    private Live sigil(Staging.Sigil g) {
        Rectangle at = g.at();
        double cx = at.getCenterX(), cy = at.getCenterY(), stampAt = g.stamp() / (double) PlayAnimator.SEAL;
        CardNode was = card(g.card(), g.was(), at), now = card(g.card(), g.marks(), at);
        Color colour = colour(Tone.ARCANE, 1);
        ImageView seal = VfxSprites.bindingSigil(at.width * 1.1);
        seal.setTranslateX(cx);
        seal.setTranslateY(cy);
        VfxKit.Flash flash = new VfxKit.Flash(cx, cy, at.width * .6, colour, 8, 0);
        VfxKit.Shockwave wave = new VfxKit.Shockwave(cx, cy, at.width * 1.3, .5, colour);
        VfxKit.Sparks sparks = new VfxKit.Sparks(cx, cy, 0, 360, 22, at.width * .9, colour, Math.round(cx));
        return new Live() {
            { node.getChildren().addAll(was, now, seal, wave, flash, sparks); }
            @Override void update(double p) {
                boolean stamped = p >= stampAt;
                double since = stamped ? (p - stampAt) / (1 - stampAt) : 0;
                double shudder = stamped ? Math.sin(since * Math.PI * 5) * 1.5 * (1 - since) : 0;
                was.setVisible(!stamped);
                now.setVisible(stamped);
                (stamped ? now : was).pose(cx + shudder, cy, 0, 1 + (stamped ? CardMotion.punch(since * 400, .04, 70, 24) : 0), 0);
                // The colour drains at the stamp and returns, so the real card takes over without a jump.
                now.shade.setSaturation(-.75 * Math.sin(Math.PI * since));
                double fall = Math.min(1, p / stampAt);
                double size = stamped ? 1 : 1.7 - .7 * fall * fall * fall;
                seal.setScaleX(size);
                seal.setScaleY(size);
                seal.setRotate(stamped ? 0 : -120 * (1 - fall));
                seal.setTranslateX(cx + shudder);
                seal.setOpacity(stamped ? (since < .35 ? 1 : 1 - (since - .35) / .65) : Math.min(1, p / (stampAt * .25)));
                for (Node light : List.of(flash, wave, sparks)) light.setVisible(stamped);
                flash.at(Math.min(1, since * 2.5));
                wave.at(Math.min(1, since * 2));
                sparks.at(Math.min(1, since * 1.5));
            }
        };
    }

    /** A shaft of warm candlelight falls onto the card, gold motes rise through it, and it narrows away. */
    private Live beam(Staging.Beam b) {
        Rectangle at = b.at();
        double cx = at.getCenterX(), width = at.width * .8;
        var shaft = new javafx.scene.shape.Rectangle(-width / 2, 0, width, 1);
        shaft.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255, 226, 160, 0)),
                new Stop(.5, Color.rgb(255, 226, 160, .75)), new Stop(1, Color.rgb(255, 226, 160, 0))));
        shaft.setEffect(new GaussianBlur(10));
        Ellipse pool = new Ellipse(0, 0, width * .8, width * .22);
        pool.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 226, 160, .8)), new Stop(1, Color.rgb(255, 226, 160, 0))));
        Random random = new Random(b.seed());
        double[][] motes = new double[18][3];
        Group rising = new Group();
        for (double[] m : motes) {
            m[0] = (random.nextDouble() - .5) * width * .8; m[1] = random.nextDouble() * .6; m[2] = 1.5 + random.nextDouble() * 2;
            rising.getChildren().add(new Circle(m[2], CANDLE));
        }
        Group light = new Group(shaft, pool, rising);
        light.setBlendMode(BlendMode.ADD);
        light.setTranslateX(cx);
        return new Live() {
            { node.getChildren().add(light); }
            @Override void update(double p) {
                double down = easeOutCubic(Math.min(1, p / .33)), narrow = p < .6 ? 1 : 1 - (p - .6) / .4;
                shaft.setHeight(Math.max(1, at.getMaxY() * down));
                shaft.setScaleX(Math.max(.05, narrow));
                pool.setCenterY(at.getMaxY() - 8);
                pool.setOpacity(down * narrow);
                for (int i = 0; i < motes.length; i++) {
                    double age = (p - motes[i][1]) / .4;
                    Node mote = rising.getChildren().get(i);
                    mote.setVisible(age > 0 && age < 1 && p > .25);
                    mote.setTranslateX(motes[i][0] * narrow);
                    mote.setTranslateY(at.getMaxY() - at.height * 1.2 * Math.max(0, age));
                    mote.setOpacity(1 - Math.max(0, age));
                }
            }
        };
    }

    /** The card catches at its bottom edge; a glowing ragged front climbs it, leaving nothing, and embers rise. */
    private Live burn(Staging.Burn b) {
        Rectangle at = b.at();
        CardNode card = card(b.card(), List.of(), at);
        card.pose(at.getCenterX(), at.getCenterY(), 0, 1, 0);
        var clip = new javafx.scene.shape.Rectangle(at.x - 4, at.y - 4, at.width + CardFace.SHADOW_X + 8, at.height + 8);
        var scorch = new javafx.scene.shape.Rectangle(at.x, 0, at.width, 18);
        scorch.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(40, 18, 6, 0)),
                new Stop(1, Color.rgb(40, 18, 6, .9))));
        Group paper = new Group(card, scorch);
        paper.setClip(clip);
        Random random = new Random(b.seed());
        double[] jag = new double[13];
        for (int i = 0; i < jag.length; i++) jag[i] = (random.nextDouble() - .5) * 9;
        // A charred band with a thin glowing edge, and flame tongues licking up from it.
        Polyline ash = new Polyline(), front = new Polyline();
        ash.setStroke(Color.rgb(28, 14, 6, .9));
        ash.setStrokeWidth(7);
        front.setStroke(Color.web("#FFB347"));
        front.setStrokeWidth(2);
        for (Polyline line : List.of(ash, front)) line.setStrokeLineCap(StrokeLineCap.ROUND);
        front.setEffect(new DropShadow(BlurType.GAUSSIAN, colour(Tone.EMBER, 1), 8, .4, 0, 0));
        ImageView[] tongues = new ImageView[7];
        Group flames = new Group();
        flames.setBlendMode(BlendMode.ADD);
        for (int i = 0; i < tongues.length; i++) flames.getChildren().add(tongues[i] = VfxSprites.flame(26));
        VfxKit.Smoke smoke = new VfxKit.Smoke(at.x, at.getMaxY(), at.width, 14, b.seed());
        double[][] embers = new double[28][3];
        Group sparks = new Group();
        for (double[] e : embers) {
            e[0] = at.x + random.nextDouble() * at.width; e[1] = random.nextDouble() * .85; e[2] = (random.nextDouble() - .5) * 30;
            sparks.getChildren().add(new Circle(1.5 + random.nextDouble() * 1.8, colour(Tone.EMBER, 1).brighter()));
        }
        sparks.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().addAll(smoke, paper, ash, front, flames, sparks); }
            @Override void update(double p) {
                double edge = edgeAt(Math.min(1, p / .85));
                clip.setHeight(Math.max(0, edge - clip.getY()));
                scorch.setY(edge - 18);
                List<Double> points = new ArrayList<>();
                for (int i = 0; i < jag.length; i++) {
                    points.add(at.x + at.width * i / (jag.length - 1.0));
                    points.add(edge + jag[i] * (1 + .3 * Math.sin(p * 30 + i)));
                }
                front.getPoints().setAll(points);
                ash.getPoints().setAll(points);
                smoke.setTranslateY(edge - at.getMaxY());
                smoke.at(p);
                double fade = p < .85 ? 1 : 1 - (p - .85) / .15;
                for (Node line : List.of(ash, front, flames)) line.setOpacity(fade);
                for (int i = 0; i < tongues.length; i++) {
                    double flicker = .55 + .45 * Math.abs(Math.sin(p * 38 + i * 1.7));
                    tongues[i].setScaleY(flicker);
                    tongues[i].setTranslateX(at.x + at.width * (i + .5) / tongues.length);
                    tongues[i].setTranslateY(edge + jag[i * 2] - 13 * flicker);
                }
                for (int i = 0; i < embers.length; i++) {
                    double age = (p - embers[i][1]) / .3;
                    Node ember = sparks.getChildren().get(i);
                    ember.setVisible(age > 0 && age < 1);
                    ember.setTranslateX(embers[i][0] + embers[i][2] * age);
                    ember.setTranslateY(edgeAt(embers[i][1] / .85) - 70 * age);
                    ember.setOpacity(1 - Math.max(0, age));
                }
            }

            /** Where the burning edge is once {@code q} of the card has burnt. */
            private double edgeAt(double q) { return at.getMaxY() + 6 - (at.height + 14) * easeInOutCubic(Math.min(1, q)); }
        };
    }

    /**
     * A brass wire with a hook on its end arcs out to a hand and hooks a card with a glint; then it
     * snaps taut and fades as the stolen card is pulled across.
     */
    private Live tether(Staging.Tether t) {
        double hookAt = PlayAnimator.HOOK / (double) PlayAnimator.TETHER;
        double ax = t.from().getX(), ay = t.from().getY(), bx = t.hook().getX(), by = t.hook().getY();
        // A brass wire over a dark groove; a glint flashes where its hook bites the card.
        Polyline groove = new Polyline(), thread = new Polyline();
        groove.setStroke(Color.rgb(15, 13, 12, .75));
        groove.setStrokeWidth(5);
        thread.setStroke(TablePlaque.BRASS_LIGHT);
        thread.setStrokeWidth(2.2);
        for (Polyline line : List.of(groove, thread)) line.setStrokeLineCap(StrokeLineCap.ROUND);
        thread.setEffect(new DropShadow(BlurType.GAUSSIAN, TablePlaque.BRASS.deriveColor(0, 1, 1, .6), 8, .3, 0, 0));
        Line across = new Line(-16, 0, 16, 0), down = new Line(0, -16, 0, 16);
        for (Line arm : List.of(across, down)) { arm.setStroke(CANDLE); arm.setStrokeWidth(2); arm.setBlendMode(BlendMode.ADD); }
        Group glint = new Group(across, down);
        glint.setEffect(new DropShadow(BlurType.GAUSSIAN, TablePlaque.BRASS, 12, .4, 0, 0));
        glint.setTranslateX(bx);
        glint.setTranslateY(by);
        Group tip = new Group(VfxSprites.hook(36));
        tip.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .6), 6, 0, 0, 2));
        Rotate heading = new Rotate();
        tip.getTransforms().add(heading);
        return new Live() {
            { node.getChildren().addAll(groove, thread, tip, glint); }
            @Override void update(double p) {
                boolean hooked = p >= hookAt;
                double reach = hooked ? 1 : easeOutCubic(p / hookAt), since = hooked ? (p - hookAt) / (1 - hookAt) : 0;
                // A loose arc while it flies; it pulls taut once the card is hooked.
                double sag = 60 * (1 - easeOutCubic(Math.min(1, since * 2.5)));
                double cx = (ax + bx) / 2, cy = (ay + by) / 2 + sag;
                List<Double> points = new ArrayList<>();
                for (int i = 0; i <= 24; i++) {
                    double q = reach * i / 24;
                    points.add((1 - q) * (1 - q) * ax + 2 * (1 - q) * q * cx + q * q * bx);
                    points.add((1 - q) * (1 - q) * ay + 2 * (1 - q) * q * cy + q * q * by);
                }
                thread.getPoints().setAll(points);
                groove.getPoints().setAll(points);
                // The hook rides the wire's tip, pointing the way the wire runs there.
                double dx = 2 * (1 - reach) * (cx - ax) + 2 * reach * (bx - cx), dy = 2 * (1 - reach) * (cy - ay) + 2 * reach * (by - cy);
                tip.setTranslateX(points.get(points.size() - 2));
                tip.setTranslateY(points.get(points.size() - 1));
                heading.setAngle(Math.toDegrees(Math.atan2(dy, dx)) - 90);
                double fade = since < .5 ? 1 : 1 - (since - .5) / .5;
                thread.setOpacity(fade);
                groove.setOpacity(fade);
                tip.setOpacity(fade);
                glint.setVisible(hooked && since < .45);
                double size = .4 + 1.2 * Math.sin(Math.PI * Math.min(1, since / .45));
                glint.setScaleX(size);
                glint.setScaleY(size);
                glint.setRotate(90 * since);
            }
        };
    }

    /** A brass ripple at the deck and gold motes thrown up from it, arcing out and falling back. */
    private Live wellspring(Staging.Wellspring w) {
        Rectangle pile = w.pile();
        double cx = pile.getCenterX(), cy = pile.getCenterY();
        // Warm gold, only tinted by the Spell's colour: the deck gives up its cards like a lit hearth.
        Color colour = colour(w.tone(), 1).interpolate(CANDLE, .7);
        Ellipse pulse = new Ellipse(cx, cy, 1, 1);
        pulse.setFill(null);
        pulse.setStroke(TablePlaque.BRASS_LIGHT);
        pulse.setStrokeWidth(2.5);
        Circle glow = new Circle(cx, cy, pile.height * .8);
        glow.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, colour.deriveColor(0, 1, 1, .55)), new Stop(1, colour.deriveColor(0, 1, 1, 0))));
        Random random = new Random(w.seed());
        double[][] motes = new double[30][3];
        Group fountain = new Group();
        for (double[] m : motes) {
            m[0] = (random.nextDouble() - .5) * 120; m[1] = 140 + random.nextDouble() * 110; m[2] = random.nextDouble() * .35;
            fountain.getChildren().add(new Circle(1.6 + random.nextDouble() * 2, random.nextBoolean() ? CANDLE : TablePlaque.BRASS_LIGHT));
        }
        Group all = new Group(glow, pulse, fountain);
        all.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().add(all); }
            @Override void update(double p) {
                double e = easeOutCubic(Math.min(1, p / .5));
                pulse.setRadiusX(pile.width * (.6 + 1.1 * e));
                pulse.setRadiusY(pile.height * (.6 + .9 * e));
                pulse.setOpacity(.9 * (1 - Math.min(1, p / .5)));
                glow.setOpacity(Math.sin(Math.PI * p));
                for (int i = 0; i < motes.length; i++) {
                    double age = (p - motes[i][2]) / .65;
                    Node mote = fountain.getChildren().get(i);
                    mote.setVisible(age > 0 && age < 1);
                    mote.setTranslateX(cx + motes[i][0] * age);
                    // Thrown up, then pulled down: a fountain's arc.
                    mote.setTranslateY(cy - motes[i][1] * age + 220 * age * age);
                    mote.setOpacity(1 - Math.max(0, age));
                }
            }
        };
    }

    /**
     * A vortex of light: a painted swirl turns round a dark well under a turning sigil, faster as it
     * closes, as cards are swallowed and given back.
     */
    private Live vortex(Staging.Vortex v) {
        Rectangle at = v.at();
        double radius = Math.max(at.width, at.height) * .7, cx = at.getCenterX(), cy = at.getCenterY();
        Color colour = colour(v.tone(), 1);
        Circle core = new Circle(cx, cy, radius * .7);
        core.setFill(new RadialGradient(0, 0, cx, cy, radius * .7, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(4, 4, 6, .95)), new Stop(.6, Color.rgb(10, 12, 18, .7)), new Stop(1, Color.rgb(10, 12, 18, 0))));
        VfxKit.Sigil ring = new VfxKit.Sigil(radius, colour, 11);
        ring.setTranslateX(cx);
        ring.setTranslateY(cy);
        // Faint, so the painted swirl leads.
        ring.setOpacity(.4);
        ImageView swirl = VfxSprites.vortex(radius * 2.1);
        swirl.setTranslateX(cx);
        swirl.setTranslateY(cy);
        return new Live() {
            { node.getChildren().addAll(core, ring, swirl); }
            @Override void update(double p) {
                double open = p < .2 ? easeOutBack(p / .2) : p > .8 ? 1 - easeInOutCubic((p - .8) / .2) : 1;
                ring.draw(Math.min(1, p / .3));
                ring.setRotate(-(200 * p + 260 * p * p));
                ring.setScaleX(Math.max(.01, open));
                ring.setScaleY(Math.max(.01, open));
                core.setScaleX(Math.max(.01, open));
                core.setScaleY(Math.max(.01, open));
                // The swirl turns faster as it closes, drawing the cards in.
                swirl.setRotate(220 * p + 300 * p * p);
                swirl.setScaleX(Math.max(.01, open));
                swirl.setScaleY(Math.max(.01, open));
                swirl.setOpacity(Math.min(1, open));
            }
        };
    }

    /** A card drawn into the vortex: it circles inward, spinning, and shrinks to nothing at the core. */
    private Live spiral(Staging.Spiral sp) {
        Rectangle size = larger(sp.from(), sp.into());
        CardNode card = card(sp.card(), List.of(), size);
        double cx = sp.into().getCenterX(), cy = sp.into().getCenterY();
        double fx = sp.from().getCenterX() - cx, fy = sp.from().getCenterY() - cy;
        double start = Math.atan2(fy, fx), distance = Math.hypot(fx, fy), from = sp.from().getWidth() / size.getWidth();
        return new Live() {
            { node.getChildren().add(card); }
            @Override void update(double p) {
                double e = easeInOutCubic(p), a = start - e * Math.PI * 1.6, r = distance * Math.pow(1 - e, 1.4);
                double grown = from + (.75 - from) * Math.min(1, e * 2), shrink = e < .5 ? 1 : 1 - (e - .5) * 2;
                card.pose(cx + Math.cos(a) * r, cy + Math.sin(a) * r, e * Math.PI * 3, Math.max(.01, grown * shrink), 0);
                card.shade.setBrightness(-.5 * e);
                card.setOpacity(p < .8 ? 1 : 1 - (p - .8) / .2);
            }
        };
    }

    /** Face-down cards rising from the deck into a small fan, lit as if by a candle while looked over, then sinking back. */
    private Live peek(Staging.Peek pk) {
        Rectangle pile = pk.pile();
        int count = pk.count();
        CardNode[] backs = new CardNode[count];
        for (int i = 0; i < count; i++) {
            backs[i] = card(null, List.of(), pile);
            backs[i].setEffect(new DropShadow(BlurType.GAUSSIAN, CANDLE.deriveColor(0, 1, 1, .8), 14, .3, 0, 0));
        }
        return new Live() {
            { node.getChildren().addAll(backs); }
            @Override void update(double p) {
                for (int i = 0; i < count; i++) {
                    double offset = i - (count - 1) / 2.0, q = Math.max(0, p - i * .04);
                    double up = q < .3 ? Math.max(0, easeOutBack(q / .3)) : q < .75 ? 1 : 1 - easeInOutCubic(Math.min(1, (q - .75) / .25));
                    double bob = q >= .3 && q < .75 ? Math.sin((q - .3) * Math.PI * 4 + i) * 2 : 0;
                    backs[i].pose(pile.getCenterX() + offset * pile.width * .72 * up,
                            pile.getCenterY() - pile.height * .95 * up + bob, offset * .2 * up, 1 + .12 * up, 0);
                }
            }
        };
    }

    /**
     * The painted eye of light opens beside the spied hand as if a lid were lifting, breathes, and
     * sweeps a soft beam of sight back and forth across the cards, lighting where it lands; then
     * it closes.
     */
    private Live eye(Staging.Eye e) {
        double w = e.width(), ex = e.x(), ey = e.y();
        Rectangle hand = e.hand();
        // Toward the hand, which is above the eye when you spy and below it when you are spied on.
        double handY = ey < hand.getCenterY() ? hand.y + hand.height * .3 : hand.getMaxY() - hand.height * .3;
        Color sight = colour(Tone.TEAL, 1).interpolate(CANDLE, .35);
        ImageView eye = VfxSprites.spyEye(w);
        Ellipse halo = new Ellipse(0, 0, w * .62, w * .3);
        halo.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, sight.deriveColor(0, 1, 1, .4)), new Stop(1, sight.deriveColor(0, 1, 1, 0))));
        halo.setBlendMode(BlendMode.ADD);
        Group lookout = new Group(halo, eye);
        lookout.setTranslateX(ex);
        lookout.setTranslateY(ey);
        Polygon beam = new Polygon();
        beam.setFill(new LinearGradient(0, ey, 0, handY, false, CycleMethod.NO_CYCLE,
                new Stop(0, sight.deriveColor(0, 1, 1, .5)), new Stop(1, sight.deriveColor(0, 1, 1, .08))));
        beam.setBlendMode(BlendMode.ADD);
        beam.setEffect(new GaussianBlur(8));
        double poolW = Math.min(hand.width * .2, w * .5), poolH = hand.height * .5;
        Ellipse pool = new Ellipse(0, handY, poolW, poolH);
        pool.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, sight.deriveColor(0, 1, 1, .35)), new Stop(1, sight.deriveColor(0, 1, 1, 0))));
        pool.setBlendMode(BlendMode.ADD);
        return new Live() {
            { node.getChildren().addAll(beam, pool, lookout); }
            @Override void update(double p) {
                double open = p < .18 ? Math.max(0, easeOutBack(p / .18)) : p > .84 ? 1 - easeInOutCubic((p - .84) / .16) : 1;
                double breathe = Math.sin(p * Math.PI * 6);
                eye.setScaleY(Math.max(.02, open));
                eye.setScaleX(1 + .025 * breathe);
                halo.setOpacity(open * (.75 + .25 * breathe));
                lookout.setOpacity(Math.min(1, p / .05));
                // The gaze crosses the hand and back while the eye is open, then returns to the middle.
                double look = p < .2 || p > .82 ? 0 : Math.sin((p - .2) / .62 * Math.PI * 2);
                double tx = hand.getCenterX() + look * hand.width * .4, near = w * .05;
                beam.getPoints().setAll(ex - near, ey, ex + near, ey, tx + poolW * .7, handY, tx - poolW * .7, handY);
                double shine = Math.max(0, Math.min(1, (p - .14) / .1)) * (p > .84 ? 1 - (p - .84) / .16 : 1);
                beam.setOpacity(shine);
                pool.setTranslateX(tx);
                pool.setOpacity(shine);
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
