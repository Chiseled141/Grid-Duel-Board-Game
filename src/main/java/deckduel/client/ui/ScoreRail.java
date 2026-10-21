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
import javafx.scene.text.Font;

/**
 * The stacked card-duel score rail, in JavaFX: a quiet dark panel with your score, the opponent's
 * score, a short breakdown and the round counters. Values come from the server's view, never from UI
 * arithmetic. A change is told in steps: as each scoring chip from the table lands, its well
 * (Units, Combos or the opponent's score) ticks toward the accepted value with a flash, and your
 * round score climbs with them; then the score settles on the accepted value with a gentle
 * punch and the change shown beside it. Used on the JavaFX thread.
 */
public final class ScoreRail extends Region {
    // A quiet dark palette: one warm accent for your score and formation, muted labels.
    private static final Color PANEL = Color.web("#1F1B18"), SURFACE = Color.web("#2B2622"), EDGE = Color.rgb(255, 240, 210, .09);
    private static final Color TEXT = Color.web("#F4EBDD"), MUTED = Color.web("#9C9285"), ACCENT = Color.web("#E9B95C");
    private static final Color BLUE = Color.web("#5D9BB8"), RED = Color.web("#D06A55");
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);
    /** The final count, the score's punch, and how long a well flashes when a chip lands. */
    private static final long COUNT_NANOS = 800_000_000L, PULSE_NANOS = 1_100_000_000L, HIT_NANOS = 320_000_000L;
    static final String WAITING = "Waiting for match scores";

    /**
     * Presentation-only scores and round counters supplied by the server's view; {@code formation}
     * names your row's formation with its bonus and multiplier, or is null without one.
     */
    public record Display(long score, long opponentScore, long unitPoints, long comboPoints,
                   long matchTotal, int playsRemaining, int round, String formation) {
        /** Rejects invalid counters before replacing the displayed snapshot. */
        public Display {
            if (playsRemaining < 0 || playsRemaining > 10 || round < 1 || round > 3)
                throw new IllegalArgumentException("Invalid round or remaining plays");
        }
    }

    /** Where a scoring chip from the table lands; a formation chip lands on the formation line. */
    public enum Pocket { UNITS, COMBOS, FORMATION, OPPONENT }

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
                + (next.formation() == null ? "" : ". Formation " + next.formation())
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
            case UNITS -> new Point2D(205, 255);
            case COMBOS -> new Point2D(205, 299);
            case FORMATION -> new Point2D(190, 346);
            case OPPONENT -> new Point2D(205, 155);
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
            case FORMATION -> 0;
            case OPPONENT -> display.opponentScore();
        };
    }

    private long from(Pocket pocket) {
        return switch (pocket) {
            case UNITS -> fromUnits;
            case COMBOS -> fromCombos;
            case FORMATION -> 0;
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
        g.translate((getWidth() - 260 * scale) / 2, 0);
        g.scale(scale, scale);
        drawRail(g, now, since);
    }

    private void drawRail(GraphicsContext g, long now, double since) {
        g.setFill(Color.rgb(0, 0, 0, .35));
        g.fillRoundRect(2, 5, 252, 535, 18, 18);
        g.setFill(PANEL);
        g.fillRoundRect(0, 0, 252, 532, 18, 18);
        g.setStroke(EDGE);
        g.setLineWidth(1);
        g.strokeRoundRect(.5, .5, 251, 531, 18, 18);
        caption(g, "YOUR SCORE", LEFT, 38);
        drawScore(g, now, since);
        drawLead(g, now);
        divider(g, 192);
        caption(g, "BREAKDOWN", LEFT, 222);
        row(g, 236, "Units", BLUE, display == null ? "—" : number(shown(Pocket.UNITS, now)), hit(Pocket.UNITS, now));
        row(g, 280, "Combos", RED, display == null ? "—" : number(shown(Pocket.COMBOS, now)), hit(Pocket.COMBOS, now));
        drawFormation(g, now);
        divider(g, 386);
        stat(g, LEFT, 404, "PLAYS LEFT", display == null ? "—" : String.valueOf(display.playsRemaining()));
        stat(g, 134, 404, "ROUND", display == null ? "—" : display.round() + " / 3");
        g.setFill(SURFACE);
        g.fillRoundRect(LEFT - 6, 470, RIGHT - LEFT + 12, 44, 12, 12);
        caption(g, "MATCH TOTAL", LEFT + 6, 497);
        value(g, display == null ? "—" : number(display.matchTotal()), RIGHT - 6, 500, FxKit.display(22), TEXT);
    }

    /** Your round score, large: it nudges up as each of your chips lands, then settles with a gentle punch. */
    private void drawScore(GraphicsContext g, long now, double since) {
        if (display == null) { left(g, "—", LEFT, 100, FxKit.display(54), TEXT); return; }
        double nudge = .05 * Math.max(hit(Pocket.UNITS, now), hit(Pocket.COMBOS, now));
        double punch = since < 0 ? 0 : emphasis * .1 * Math.exp(-since * 6) * Math.cos(since * 20);
        Color colour = since >= 0 || nudge > 0 ? (gain < 0 ? FxKit.CORAL : ACCENT) : TEXT;
        Font font = FxKit.display(54 * (1 + punch + nudge));
        String score = number(shownScore(now));
        left(g, score, LEFT, 100, font, colour);
        if (since < 0 || gain == 0) return;
        // The change fades in beside the score, then out.
        g.setGlobalAlpha(Math.min(1, since / .12) * (since > .85 ? Math.max(0, 1 - (since - .85) / .25) : 1));
        left(g, (gain > 0 ? "+" : "−") + NUMBERS.format(Math.abs(gain)), LEFT + FxKit.width(score, font) + 10, 96,
                FxKit.bold(16), gain < 0 ? FxKit.CORAL : ACCENT);
        g.setGlobalAlpha(1);
    }

    /** The opponent's score, and a slim bar of your share of both scores. */
    private void drawLead(GraphicsContext g, long now) {
        long mine = display == null ? 0 : shownScore(now), theirs = display == null ? 0 : shown(Pocket.OPPONENT, now);
        double share = mine + theirs <= 0 ? .5 : Math.max(0, Math.min(1, mine / (double) (mine + theirs)));
        double width = RIGHT - LEFT;
        g.setFill(SURFACE);
        g.fillRoundRect(LEFT, 120, width, 6, 6, 6);
        g.setFill(ACCENT);
        g.fillRoundRect(LEFT, 120, Math.max(6, width * share), 6, 6, 6);
        double hit = display == null ? 0 : hit(Pocket.OPPONENT, now);
        caption(g, "OPPONENT", LEFT, 160);
        value(g, display == null ? "—" : number(theirs), RIGHT, 163, FxKit.display(22 * (1 + .15 * hit)), hit > 0 ? ACCENT : TEXT);
    }

    /** The row's formation as a gold pill, or a quiet note when the row has none. */
    private void drawFormation(GraphicsContext g, long now) {
        String formation = display == null ? null : display.formation();
        double hit = display == null ? 0 : hit(Pocket.FORMATION, now);
        if (formation == null) {
            g.setFill(SURFACE);
            g.fillRoundRect(LEFT - 6, 324, RIGHT - LEFT + 12, 44, 12, 12);
            left(g, "No formation", LEFT + 6, 351, FxKit.normal(14), MUTED);
            return;
        }
        int split = formation.indexOf("  +");
        String name = split < 0 ? formation : formation.substring(0, split), bonus = split < 0 ? "" : formation.substring(split).trim();
        g.setFill(ACCENT.deriveColor(0, 1, 1, .16 + .2 * hit));
        g.fillRoundRect(LEFT - 6, 324, RIGHT - LEFT + 12, 44, 12, 12);
        g.setStroke(ACCENT.deriveColor(0, 1, 1, .45 + .4 * hit));
        g.strokeRoundRect(LEFT - 5.5, 324.5, RIGHT - LEFT + 11, 43, 12, 12);
        left(g, FxKit.fit(name, FxKit.bold(15), 120), LEFT + 6, 351, FxKit.bold(15), ACCENT);
        value(g, bonus, RIGHT - 6, 351, FxKit.bold(15), TEXT);
    }

    // ---------------------------------------------------------------- drawing helpers

    /** Left and right edges of the content. */
    private static final double LEFT = 22, RIGHT = 230;

    /** One breakdown line: a colour dot, its name, and its value; {@code hit} lights it as a chip lands. */
    private static void row(GraphicsContext g, double top, String name, Color dot, String value, double hit) {
        g.setFill(SURFACE.interpolate(dot, .25 * hit));
        g.fillRoundRect(LEFT - 6, top, RIGHT - LEFT + 12, 38, 12, 12);
        g.setFill(dot);
        g.fillOval(LEFT + 4, top + 15, 8, 8);
        left(g, name, LEFT + 20, top + 25, FxKit.normal(15), TEXT);
        value(g, value, RIGHT - 6, top + 27, FxKit.display(20 * (1 + .15 * hit)), TEXT);
    }

    /** A small labelled number in the footer. */
    private static void stat(GraphicsContext g, double x, double top, String label, String value) {
        caption(g, label, x, top + 14);
        left(g, value, x, top + 46, FxKit.display(24), TEXT);
    }

    private static void divider(GraphicsContext g, double y) {
        g.setFill(EDGE);
        g.fillRect(LEFT, y, RIGHT - LEFT, 1);
    }

    /** A small muted capital label. */
    private static void caption(GraphicsContext g, String text, double x, double y) {
        left(g, text, x, y, FxKit.bold(10.5), MUTED);
    }

    private static String number(long value) { return NUMBERS.format(value); }

    /** Text from {@code x}; full values remain accessible. */
    private static void left(GraphicsContext g, String text, double x, double y, Font font, Color color) {
        g.setFont(font);
        g.setFill(color);
        g.fillText(text, x, y);
    }

    /** Text ending at {@code right}, shrinking to stay inside the panel. */
    private static void value(GraphicsContext g, String text, double right, double y, Font font, Color color) {
        while (FxKit.width(text, font) > 130 && font.getSize() > 9) font = new Font(font.getName(), font.getSize() - 1);
        left(g, text, right - FxKit.width(text, font), y, font, color);
    }
}
