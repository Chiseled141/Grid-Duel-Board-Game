package deckduel.client.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

/**
 * The match table's object language for score and notice surfaces, matched to the painted dark
 * fantasy card art: blackened iron plates with clipped corners and worn gold trim, aged-gold
 * nameplates, recessed vellum plates and slots, and blood-red cloth ribbons lettered in gold.
 * Light is soft and painted (gradients and vignettes), never flat. Shared by the score rail and
 * the table's toasts. Draws on a JavaFX canvas; JavaFX thread only.
 */
final class TablePlaque {
    static final Color IRON = Color.web("#2B2725"), IRON_DARK = Color.web("#1A1716"), WOOD_LINE = Color.web("#0F0D0C");
    static final Color BRASS = Color.web("#A9843F"), BRASS_DARK = Color.web("#5E4520"), BRASS_LIGHT = Color.web("#D9B56A");
    static final Color PARCHMENT = Color.web("#D8C8A0"), PARCHMENT_EDGE = Color.web("#8F7A55"), INK = Color.web("#1E140C");
    static final Color CREAM = Color.web("#EDE3CF"), FADED = Color.web("#B4A890");
    /** Colours with one job each: Unit points, combo points, and worn gold for formations and your lead. */
    static final Color UNITS = Color.web("#6286A0"), COMBOS = Color.web("#9A2C24"), GOLD = Color.web("#C49A45");
    /** The blood-red cloth of formation ribbons, lettered in gold. */
    static final Color RIBBON = Color.web("#7E1F1B");

    private TablePlaque() { }

    /** Labels use the game's plain sans-serif in small capitals, for easy reading. */
    static Font label() { return FxKit.bold(11.5); }

    /** An iron plate: clipped corners, a soft painted light from above, a vignette, worn gold trim and studs. */
    static void board(GraphicsContext g, double x, double y, double w, double h, double cut) {
        g.setFill(WOOD_LINE);
        clipped(g, x, y, w, h, cut);
        g.fill();
        g.setFill(new LinearGradient(0, y, 0, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#3A3431")), new Stop(.35, IRON), new Stop(1, IRON_DARK)));
        clipped(g, x + 2, y + 2, w - 4, h - 4, cut - 1);
        g.fill();
        g.save();
        clipped(g, x + 2, y + 2, w - 4, h - 4, cut - 1);
        g.clip();
        // A dark vignette and a few soft scuffs, so the plate reads as worn metal, not a flat fill.
        g.setFill(new RadialGradient(0, 0, x + w / 2, y + h * .35, Math.max(w, h) * .75, false, CycleMethod.NO_CYCLE,
                new Stop(.55, Color.rgb(0, 0, 0, 0)), new Stop(1, Color.rgb(0, 0, 0, .45))));
        g.fillRect(x, y, w, h);
        g.setFill(Color.rgb(255, 235, 200, .03));
        for (double scuff = y + 17; scuff < y + h; scuff += 23) g.fillRect(x + (scuff * 13 % 41), scuff, w * .4, 1);
        g.restore();
        g.setStroke(BRASS.deriveColor(0, 1, 1, .55));
        g.setLineWidth(1.2);
        clipped(g, x + 6.5, y + 6.5, w - 13, h - 13, Math.max(2, cut - 4));
        g.stroke();
        for (double[] stud : new double[][] {{x + 11, y + 11}, {x + w - 11, y + 11}, {x + 11, y + h - 11}, {x + w - 11, y + h - 11}})
            stud(g, stud[0], stud[1]);
    }

    /** An aged-gold nameplate with an engraved label centred on it. */
    static void nameplate(GraphicsContext g, double x, double y, double w, double h, String label) {
        g.setFill(BRASS_DARK);
        clipped(g, x, y, w, h, 4);
        g.fill();
        g.setFill(new LinearGradient(0, y, 0, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0, BRASS_LIGHT), new Stop(.5, BRASS), new Stop(1, BRASS_DARK)));
        clipped(g, x + 1, y + 1, w - 2, h - 2, 3);
        g.fill();
        engraved(g, label, x + w / 2, y + h / 2 + 4, label(), INK, true);
    }

    /** A recessed vellum plate, darkening toward its edges like old paper. */
    static void parchment(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(PARCHMENT_EDGE);
        clipped(g, x, y, w, h, 3);
        g.fill();
        g.setFill(new RadialGradient(0, 0, x + w / 2, y + h / 2, w * .62, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#E4D6B2")), new Stop(.7, PARCHMENT), new Stop(1, Color.web("#B39C70"))));
        clipped(g, x + 1.5, y + 2, w - 3, h - 3.5, 2);
        g.fill();
        g.setFill(Color.rgb(40, 25, 10, .25));
        g.fillRect(x + 3, y + 2, w - 6, 2);
    }

    /** A dark slot sunk into the plate, for a secondary number; {@code glow} warms it as a chip lands. */
    static void slot(GraphicsContext g, double x, double y, double w, double h, Color glow, double hit) {
        Color base = Color.web("#141110").interpolate(glow, .4 * hit);
        g.setFill(new LinearGradient(0, y, 0, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0, base.darker()), new Stop(1, base)));
        clipped(g, x, y, w, h, 3);
        g.fill();
        g.setFill(Color.rgb(255, 235, 200, .06));
        g.fillRect(x + 3, y + h - 1.5, w - 6, 1);
    }

    /** A cloth ribbon with notched swallowtail ends folded behind, shaded like the painted capes. */
    static void ribbon(GraphicsContext g, double x, double y, double w, double h, Color cloth) {
        double notch = h * .38, tail = h * .6;
        g.setFill(cloth.deriveColor(0, 1, .55, 1));
        g.fillPolygon(new double[] {x - tail, x + 6, x + 6, x - tail, x - tail + notch},
                new double[] {y + 5, y + 5, y + h + 5, y + h + 5, y + h / 2 + 5}, 5);
        g.fillPolygon(new double[] {x + w + tail, x + w - 6, x + w - 6, x + w + tail, x + w + tail - notch},
                new double[] {y + 5, y + 5, y + h + 5, y + h + 5, y + h / 2 + 5}, 5);
        g.setFill(new LinearGradient(0, y, 0, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0, cloth.deriveColor(0, 1, 1.35, 1)), new Stop(.45, cloth), new Stop(1, cloth.deriveColor(0, 1, .6, 1))));
        g.fillRect(x, y, w, h);
        g.setStroke(BRASS.deriveColor(0, 1, 1, .8));
        g.setLineWidth(1);
        g.strokeLine(x, y + 2.5, x + w, y + 2.5);
        g.strokeLine(x, y + h - 2.5, x + w, y + h - 2.5);
    }

    /** A round enamel token in a colour with one job, set in worn gold. */
    static void token(GraphicsContext g, double cx, double cy, double r, Color colour) {
        g.setFill(BRASS_DARK);
        g.fillOval(cx - r - 2, cy - r - 2, 2 * r + 4, 2 * r + 4);
        g.setFill(BRASS);
        g.fillOval(cx - r - 1, cy - r - 1, 2 * r + 2, 2 * r + 2);
        g.setFill(new RadialGradient(0, 0, cx - r * .3, cy - r * .35, r * 1.3, false, CycleMethod.NO_CYCLE,
                new Stop(0, colour.deriveColor(0, 1, 1.4, 1)), new Stop(1, colour.deriveColor(0, 1, .55, 1))));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
    }

    /** A fine gold line across the plate, fading toward both ends. */
    static void groove(GraphicsContext g, double x, double y, double w) {
        g.setFill(new LinearGradient(x, 0, x + w, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(169, 132, 63, 0)), new Stop(.5, BRASS.deriveColor(0, 1, 1, .7)), new Stop(1, Color.rgb(169, 132, 63, 0))));
        g.fillRect(x, y, w, 1.2);
    }

    /** Text with a faint lighter edge below it, as if cut into the surface. */
    static void engraved(GraphicsContext g, String text, double x, double y, Font font, Color colour, boolean centred) {
        g.setFont(font);
        double left = centred ? x - FxKit.width(text, font) / 2 : x;
        g.setFill(Color.rgb(255, 240, 210, .14));
        g.fillText(text, left, y + 1);
        g.setFill(colour);
        g.fillText(text, left, y);
    }

    private static void stud(GraphicsContext g, double cx, double cy) {
        g.setFill(new RadialGradient(0, 0, cx - .8, cy - .8, 3, false, CycleMethod.NO_CYCLE,
                new Stop(0, BRASS_LIGHT), new Stop(1, BRASS_DARK)));
        g.fillOval(cx - 2.6, cy - 2.6, 5.2, 5.2);
    }

    /** How a button reads: the main action in aged gold, a secondary one in iron, or unavailable. */
    enum Look { PRIMARY, PLAIN, OFF }

    /** A table button: a clipped-corner plate in its {@code look}, lighter while {@code lit}, with a centred label. */
    static void button(GraphicsContext g, double x, double y, double w, double h, Look look, boolean lit, String label, Font font) {
        Color top = switch (look) {
            case PRIMARY -> BRASS_LIGHT;
            case PLAIN -> Color.web("#4A423D");
            case OFF -> Color.web("#2E2A28");
        };
        Color bottom = switch (look) {
            case PRIMARY -> BRASS_DARK;
            case PLAIN -> IRON_DARK;
            case OFF -> Color.web("#1E1B1A");
        };
        if (lit) { top = top.deriveColor(0, 1, 1.18, 1); bottom = bottom.deriveColor(0, 1, 1.3, 1); }
        g.setFill(WOOD_LINE);
        clipped(g, x, y, w, h, 5);
        g.fill();
        g.setFill(new LinearGradient(0, y, 0, y + h, false, CycleMethod.NO_CYCLE, new Stop(0, top), new Stop(1, bottom)));
        clipped(g, x + 1, y + 1, w - 2, h - 2, 4);
        g.fill();
        if (look != Look.OFF) {
            g.setStroke(look == Look.PRIMARY ? BRASS_LIGHT.deriveColor(0, 1, 1.1, .8) : BRASS.deriveColor(0, 1, 1, lit ? .95 : .6));
            g.setLineWidth(1);
            clipped(g, x + 2.5, y + 2.5, w - 5, h - 5, 3);
            g.stroke();
        }
        g.setFont(font);
        g.setFill(switch (look) {
            case PRIMARY -> INK;
            case PLAIN -> lit ? BRASS_LIGHT : CREAM;
            case OFF -> FADED.deriveColor(0, 1, .7, 1);
        });
        g.fillText(label, x + (w - FxKit.width(label, font)) / 2, y + h / 2 + font.getSize() * .36);
    }

    /** A rectangle path with its four corners cut at 45 degrees. */
    static void clipped(GraphicsContext g, double x, double y, double w, double h, double cut) {
        g.beginPath();
        g.moveTo(x + cut, y);
        g.lineTo(x + w - cut, y);
        g.lineTo(x + w, y + cut);
        g.lineTo(x + w, y + h - cut);
        g.lineTo(x + w - cut, y + h);
        g.lineTo(x + cut, y + h);
        g.lineTo(x, y + h - cut);
        g.lineTo(x, y + cut);
        g.closePath();
    }
}
