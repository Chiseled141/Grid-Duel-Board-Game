package deckduel.client.ui;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleRole;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

/**
 * The stacked card-duel score rail, in JavaFX. Values come from the server's view, never from UI
 * arithmetic. A change is told in steps: as each scoring chip from the table lands, its well
 * (Units, Combos or the opponent's score) ticks toward the accepted value with a flash, and your
 * round score climbs with them; then the score settles on the accepted value with a springy
 * punch, a glow and, for a gain, a burst of rays. Used on the JavaFX thread.
 */
public final class ScoreRail extends Region {
    private static final Color LEATHER = Color.web("#352B24"), WELL = Color.web("#201D19"), BRASS = Color.web("#AD8954");
    private static final Color BLUE = Color.web("#385D6D"), RED = Color.web("#8B4236"), FLASH = Color.web("#FFF1CF");
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);
    /** The final count, the score's punch, and how long a well flashes when a chip lands. */
    private static final long COUNT_NANOS = 800_000_000L, PULSE_NANOS = 1_100_000_000L, HIT_NANOS = 320_000_000L;
    static final String WAITING = "Waiting for match scores";

    /** Presentation-only scores and round counters supplied by the server's view. */
    public record Display(long score, long opponentScore, long unitPoints, long comboPoints,
                   long matchTotal, int playsRemaining, int round) {
        /** Rejects invalid counters before replacing the displayed snapshot. */
        public Display {
            if (playsRemaining < 0 || playsRemaining > 10 || round < 1 || round > 3)
                throw new IllegalArgumentException("Invalid round or remaining plays");
        }
    }

    /** Where a scoring chip from the table lands. */
    public enum Pocket { UNITS, COMBOS, OPPONENT }

    /** A chip worth {@code points} landing in {@code pocket} {@code atMillis} after the update. */
    public record Tally(int atMillis, Pocket pocket, long points) { }

    private final Canvas canvas = new Canvas();
    private Display display;
    /** The values shown when the running change began, and when it began. */
    private long fromScore, fromUnits, fromCombos, fromOpponent, startedAt;
    private List<Tally> tallies = List.of();
    private long finishAt;
    private double gain, emphasis;
    private boolean animating;
    private final AnimationTimer animation = new AnimationTimer() {
        @Override public void handle(long now) { tick(now); }
    };

    /** Builds an empty rail, suitable for a new or loading match. */
    public ScoreRail() {
        setId("duelScorePanel");
        getChildren().add(canvas);
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(WAITING);
        setMinSize(196, 410);
        setPrefSize(260, 550);
    }

    /** Applies an accepted view; the painted score counts up to it after {@code delayMillis}. Bigger changes pulse harder. */
    public void update(Display next, boolean reducedMotion, int delayMillis) {
        update(next, reducedMotion, delayMillis, List.of());
    }

    /**
     * Applies an accepted view, told in steps: each of {@code chips} moves its well toward the new
     * value as it lands, and the score settles on it after {@code delayMillis}. The accepted
     * values are shown in full once the change has been told, whatever the chips added up to.
     */
    public void update(Display next, boolean reducedMotion, int delayMillis, List<Tally> chips) {
        Objects.requireNonNull(next, "next");
        boolean sameRound = display != null && display.round() == next.round();
        boolean changed = sameRound && (display.score() != next.score() || display.opponentScore() != next.opponentScore());
        long now = System.nanoTime();
        if (changed && !reducedMotion) {
            // A change arriving mid-count carries on from what is on screen, so nothing jumps back.
            fromScore = shownScore(now); fromUnits = shown(Pocket.UNITS, now);
            fromCombos = shown(Pocket.COMBOS, now); fromOpponent = shown(Pocket.OPPONENT, now);
            startedAt = now;
            tallies = List.copyOf(chips);
            finishAt = now + delayMillis * 1_000_000L;
            gain = (double) next.score() - display.score();
            emphasis = .4 + .6 * Math.min(1, Math.abs(gain) / 40);
            animating = true;
            animation.start();
        } else if (!sameRound || reducedMotion) stopFeedback();
        display = next;
        String description = "Round " + next.round() + " of 3. Your score " + next.score()
                + ". Opponent " + next.opponentScore() + ". Unit points " + next.unitPoints()
                + ", combo points " + next.comboPoints()
                + ". Plays remaining " + next.playsRemaining() + ". Match total " + next.matchTotal();
        setAccessibleText(description);
        draw();
    }

    /** Cancels cosmetic feedback when leaving or resetting the match. */
    public void stopFeedback() {
        animation.stop();
        animating = false;
        draw();
    }

    /** Clears the old match before another match or reconnect snapshot is attached. */
    public void reset() {
        stopFeedback();
        display = null;
        setAccessibleText(WAITING);
        draw();
    }

    boolean isAnimating() { return animating; }

    /** The full description for tests and assistive technology. */
    String description() { return getAccessibleText(); }

    /** Where chips for {@code pocket} land: the middle of its number, in this rail's own coordinates. */
    Point2D pocket(Pocket pocket) {
        double scale = scale();
        Point2D at = switch (pocket) {
            case UNITS -> new Point2D(63.5, 350);
            case COMBOS -> new Point2D(188.5, 350);
            case OPPONENT -> new Point2D(126, 96);
        };
        return new Point2D((getWidth() - 260 * scale) / 2 + at.getX() * scale, at.getY() * scale);
    }

    private void tick(long now) {
        if (now - finishAt > Math.max(COUNT_NANOS, PULSE_NANOS)) { animating = false; animation.stop(); }
        draw();
    }

    // ---------------------------------------------------------------- the told values

    private long target(Pocket pocket) {
        return switch (pocket) {
            case UNITS -> display.unitPoints();
            case COMBOS -> display.comboPoints();
            case OPPONENT -> display.opponentScore();
        };
    }

    private long from(Pocket pocket) {
        return switch (pocket) {
            case UNITS -> fromUnits;
            case COMBOS -> fromCombos;
            case OPPONENT -> fromOpponent;
        };
    }

    /**
     * A well's value at {@code now}: before the finish it moves toward the accepted value in
     * proportion to the chips landed so far (when they point the same way); then it settles.
     */
    private long shown(Pocket pocket, long now) {
        if (display == null) return 0;
        long target = target(pocket);
        if (!animating) return target;
        long told = told(pocket, now);
        if (now < finishAt) return told;
        double settle = Math.min(1, (now - finishAt) / (double) HIT_NANOS);
        return Math.round(told + (target - told) * settle);
    }

    /** What the chips landed by {@code now} (capped at the finish) have told of {@code pocket}'s change. */
    private long told(Pocket pocket, long now) {
        long from = from(pocket), delta = target(pocket) - from, total = 0, landed = 0;
        for (Tally tally : tallies) {
            if (tally.pocket() != pocket) continue;
            total += tally.points();
            if (landedBy(tally, Math.min(now, finishAt))) landed += tally.points();
        }
        if (total == 0 || Long.signum(total) != Long.signum(delta)) return from;
        return from + Math.round(delta * Math.min(1, landed / (double) total));
    }

    private boolean landedBy(Tally tally, long now) { return startedAt + tally.atMillis() * 1_000_000L <= now; }

    /** The painted score: climbing with the Units and Combos wells, then counting to the accepted value. */
    private long shownScore(long now) {
        if (!animating) return display.score();
        long climbed = fromScore + told(Pocket.UNITS, now) - fromUnits + told(Pocket.COMBOS, now) - fromCombos;
        if (now < finishAt) return climbed;
        long elapsed = now - finishAt;
        if (elapsed >= COUNT_NANOS) return display.score();
        double eased = 1 - Math.pow(1 - elapsed / (double) COUNT_NANOS, 3);
        return Math.round(climbed + (display.score() - climbed) * eased);
    }

    /** How freshly a chip landed in {@code pocket}: 1 at the moment it lands, fading to 0. */
    private double hit(Pocket pocket, long now) {
        if (!animating) return 0;
        double strongest = 0;
        for (Tally tally : tallies) {
            long since = now - (startedAt + tally.atMillis() * 1_000_000L);
            if (tally.pocket() == pocket && since >= 0 && since < HIT_NANOS) strongest = Math.max(strongest, 1 - since / (double) HIT_NANOS);
        }
        return strongest;
    }

    /** Seconds since the score settled, or -1 before then or once the punch has died away. */
    private double sinceFinish(long now) {
        if (!animating || now < finishAt || now - finishAt > PULSE_NANOS) return -1;
        return (now - finishAt) / 1e9;
    }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private double scale() { return Math.min(getWidth() / 260.0, getHeight() / 550.0); }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double scale = scale();
        if (scale <= 0) return;
        long now = System.nanoTime();
        double since = display == null ? -1 : sinceFinish(now);
        // A big change jolts the rail for a moment as it settles.
        double jolt = since < 0 ? 0 : emphasis * 3 * Math.exp(-since * 9) * Math.sin(since * 70);
        g.translate((getWidth() - 260 * scale) / 2 + jolt, 0);
        g.scale(scale, scale);
        drawRail(g, now, since);
    }

    private void drawRail(GraphicsContext g, long now, double since) {
        g.setFill(FxKit.SHADOW);
        g.fillRoundRect(4, 6, 252, 540, 14, 14);
        g.setFill(LEATHER);
        g.fillRoundRect(0, 0, 252, 540, 14, 14);
        g.setStroke(BRASS);
        g.setLineWidth(1.5);
        g.strokeRoundRect(5, 5, 242, 530, 10, 10);
        label(g, "DECK DUEL", 14, 33, 224, 19, BRASS);
        double opponentHit = display == null ? 0 : hit(Pocket.OPPONENT, now);
        well(g, 14, 48, 224, 74, WELL, opponentHit);
        label(g, "OPPONENT'S ROUND SCORE", 23, 68, 206, 10, FxKit.MUTED);
        label(g, display == null ? "—" : number(shown(Pocket.OPPONENT, now)), 23, 108, 206, 34 * (1 + .22 * opponentHit), FxKit.CREAM);
        well(g, 14, 135, 224, 116, WELL, 0);
        label(g, "YOUR ROUND SCORE", 23, 158, 206, 12, BRASS);
        drawScore(g, now, since);
        label(g, "SCORE BREAKDOWN", 14, 280, 224, 12, FxKit.CREAM);
        double unitsHit = display == null ? 0 : hit(Pocket.UNITS, now), combosHit = display == null ? 0 : hit(Pocket.COMBOS, now);
        well(g, 14, 293, 99, 88, BLUE, unitsHit);
        well(g, 139, 293, 99, 88, RED, combosHit);
        label(g, "UNITS", 19, 315, 89, 11, FxKit.CREAM);
        label(g, "COMBOS", 144, 315, 89, 11, FxKit.CREAM);
        label(g, display == null ? "—" : number(shown(Pocket.UNITS, now)), 19, 362, 89, 35 * (1 + .3 * unitsHit), FxKit.CREAM);
        label(g, display == null ? "—" : number(shown(Pocket.COMBOS, now)), 144, 362, 89, 35 * (1 + .3 * combosHit), FxKit.CREAM);
        label(g, "+", 114, 348, 24, 24, FxKit.CREAM);
        well(g, 14, 394, 107, 64, WELL, 0);
        well(g, 131, 394, 107, 64, WELL, 0);
        label(g, "PLAYS LEFT", 20, 414, 95, 10, FxKit.MUTED);
        label(g, "ROUND", 137, 414, 95, 10, FxKit.MUTED);
        label(g, display == null ? "—" : String.valueOf(display.playsRemaining()), 20, 446, 95, 28, FxKit.CREAM);
        label(g, display == null ? "—" : display.round() + " / 3", 137, 446, 95, 26, FxKit.CREAM);
        well(g, 14, 471, 224, 50, WELL, 0);
        label(g, "MATCH TOTAL", 22, 491, 110, 11, BRASS);
        label(g, display == null ? "—" : number(display.matchTotal()), 137, 507, 91, 28, FxKit.CREAM);
    }

    /**
     * Your round score: it nudges up as each of your chips lands, then on settling it punches
     * (a damped spring), glows, throws rays for a gain, and shows the change in a pill.
     */
    private void drawScore(GraphicsContext g, long now, double since) {
        if (display == null) { label(g, "—", 23, 218, 206, 56, FxKit.CREAM); label(g, "LIVE TABLE VALUE", 23, 239, 206, 12, FxKit.CREAM); return; }
        boolean loss = gain < 0;
        Color accent = loss ? FxKit.CORAL : FxKit.AMBER;
        double nudge = .07 * Math.max(hit(Pocket.UNITS, now), hit(Pocket.COMBOS, now));
        double punch = since < 0 ? 0 : emphasis * .2 * Math.exp(-since * 5) * Math.cos(since * 22);
        double glow = since < 0 ? nudge * 4 : Math.max(0, 1 - since / (PULSE_NANOS / 1e9)) * emphasis;
        double cx = 126, cy = 200;
        if (glow > 0) {
            g.setFill(new RadialGradient(0, 0, cx, cy, 95, false, CycleMethod.NO_CYCLE,
                    new Stop(0, accent.deriveColor(0, 1, 1, .55 * Math.min(1, glow))), new Stop(1, accent.deriveColor(0, 1, 1, 0))));
            g.fillRect(14, 135, 224, 114);
        }
        if (since >= 0 && since < .5 && !loss) {
            double grow = 1 - Math.pow(1 - since / .5, 3), fade = 1 - since / .5;
            g.setStroke(accent.deriveColor(0, 1, 1, .8 * fade * emphasis));
            g.setLineWidth(2.5);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12 + .2, inner = 34 + 30 * grow, outer = inner + 14 + 10 * emphasis;
                g.strokeLine(cx + Math.cos(a) * inner * 1.6, cy + Math.sin(a) * inner * .7,
                        cx + Math.cos(a) * outer * 1.6, cy + Math.sin(a) * outer * .7);
            }
        }
        Color colour = since >= 0 || nudge > 0 ? accent : FxKit.CREAM;
        label(g, number(shownScore(now)), 23, 218, 206, 56 * (1 + punch + nudge), colour);
        if (since < 0) { label(g, "LIVE TABLE VALUE", 23, 239, 206, 12, FxKit.CREAM); return; }
        String change = (gain > 0 ? "+" : "") + NUMBERS.format(gain) + " POINTS";
        double width = FxKit.width(change, FxKit.bold(12)) + 20;
        g.setGlobalAlpha(Math.min(1, since / .12) * (since > .85 ? Math.max(0, 1 - (since - .85) / .25) : 1));
        g.setFill(accent);
        g.fillRoundRect(cx - width / 2, 229, width, 18, 18, 18);
        label(g, change, cx - width / 2, 242, width, 12, Color.web("#2A1A0E"));
        g.setGlobalAlpha(1);
    }

    /** A recessed panel; {@code hit} flashes it brighter with a lit rim as a chip lands in it. */
    private static void well(GraphicsContext g, double x, double y, double w, double h, Color color, double hit) {
        g.setFill(Color.rgb(0, 0, 0, .35));
        g.fillRoundRect(x, y + 3, w, h, 7, 7);
        g.setFill(color.interpolate(FLASH, .38 * hit));
        g.fillRoundRect(x, y, w, h - 2, 7, 7);
        g.setStroke(hit > 0 ? FxKit.AMBER.deriveColor(0, 1, 1, .4 + .6 * hit) : Color.rgb(255, 239, 198, .11));
        g.setLineWidth(1 + 2 * hit);
        g.strokeRoundRect(x, y, w, h - 2, 7, 7);
    }

    private static String number(long value) { return NUMBERS.format(value); }

    /** Centred text that shrinks, then shortens, to fit; full values remain accessible. */
    private static void label(GraphicsContext g, String text, double x, double y, double width, double size, Color color) {
        Font font = FxKit.bold(size);
        while (FxKit.width(text, font) > width && font.getSize() > 8) font = FxKit.bold(font.getSize() - 1);
        String fitted = FxKit.fit(text, font, width);
        double left = x + (width - FxKit.width(fitted, font)) / 2;
        g.setFont(font);
        g.setFill(FxKit.SHADOW);
        g.fillText(fitted, left + 1, y + 2);
        g.setFill(color);
        g.fillText(fitted, left, y);
    }
}
