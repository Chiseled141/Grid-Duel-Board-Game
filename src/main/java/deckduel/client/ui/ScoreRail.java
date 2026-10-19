package deckduel.client.ui;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import javafx.animation.AnimationTimer;
import javafx.scene.AccessibleRole;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The stacked card-duel score rail, in JavaFX. Values come from the server's view, never from UI
 * arithmetic; a change counts up and pulses after the table's trace. Used on the JavaFX thread.
 */
public final class ScoreRail extends Region {
    private static final Color LEATHER = Color.web("#352B24"), WELL = Color.web("#201D19"), BRASS = Color.web("#AD8954");
    private static final Color BLUE = Color.web("#385D6D"), RED = Color.web("#8B4236");
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final long COUNT_NANOS = 800_000_000L, PULSE_NANOS = 1_100_000_000L;
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

    private final Canvas canvas = new Canvas();
    private Display display;
    private long changedAt, countedFrom;
    private double gain, pulse, emphasis;
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
        Objects.requireNonNull(next, "next");
        boolean sameRound = display != null && display.round() == next.round();
        boolean changed = sameRound && display.score() != next.score();
        long shownBefore = display == null ? 0 : shownScore(System.nanoTime());
        if (!sameRound || reducedMotion) stopFeedback();
        if (changed && !reducedMotion) {
            gain = (double) next.score() - display.score();
            countedFrom = shownBefore;
            changedAt = System.nanoTime() + delayMillis * 1_000_000L;
            emphasis = .4 + .6 * Math.min(1, Math.abs(gain) / 40);
            pulse = 0;
            animating = true;
            animation.start();
        }
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
        pulse = 0;
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

    private void tick(long now) {
        long elapsed = now - changedAt;
        pulse = elapsed < 0 ? 0 : Math.max(0, 1 - elapsed / (double) PULSE_NANOS) * emphasis;
        if (elapsed > Math.max(COUNT_NANOS, PULSE_NANOS)) { pulse = 0; animating = false; animation.stop(); }
        draw();
    }

    /** The painted score: counting toward the accepted value while feedback runs. */
    private long shownScore(long now) {
        if (!animating) return display.score();
        long elapsed = now - changedAt;
        if (elapsed >= COUNT_NANOS) return display.score();
        double progress = Math.max(0, elapsed / (double) COUNT_NANOS), eased = 1 - Math.pow(1 - progress, 3);
        return Math.round(countedFrom + (display.score() - countedFrom) * eased);
    }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double scale = Math.min(getWidth() / 260.0, getHeight() / 550.0);
        if (scale <= 0) return;
        g.translate((getWidth() - 260 * scale) / 2, 0);
        g.scale(scale, scale);
        drawRail(g);
    }

    private void drawRail(GraphicsContext g) {
        g.setFill(FxKit.SHADOW);
        g.fillRoundRect(4, 6, 252, 540, 14, 14);
        g.setFill(LEATHER);
        g.fillRoundRect(0, 0, 252, 540, 14, 14);
        g.setStroke(BRASS);
        g.setLineWidth(1.5);
        g.strokeRoundRect(5, 5, 242, 530, 10, 10);
        label(g, "DECK DUEL", 14, 33, 224, 19, BRASS);
        well(g, 14, 48, 224, 74, WELL);
        label(g, "OPPONENT'S ROUND SCORE", 23, 68, 206, 10, FxKit.MUTED);
        label(g, display == null ? "—" : number(display.opponentScore()), 23, 108, 206, 34, FxKit.CREAM);
        well(g, 14, 135, 224, 116, WELL);
        label(g, "YOUR ROUND SCORE", 23, 158, 206, 12, BRASS);
        Color scoreColor = pulse > 0 ? FxKit.AMBER : FxKit.CREAM;
        label(g, display == null ? "—" : number(shownScore(System.nanoTime())), 23, 218, 206, 56 + pulse * 6, scoreColor);
        label(g, pulse == 0 ? "LIVE TABLE VALUE" : signedGain(), 23, 239, 206, 12, scoreColor);
        label(g, "SCORE BREAKDOWN", 14, 280, 224, 12, FxKit.CREAM);
        well(g, 14, 293, 99, 88, BLUE);
        well(g, 139, 293, 99, 88, RED);
        label(g, "UNITS", 19, 315, 89, 11, FxKit.CREAM);
        label(g, "COMBOS", 144, 315, 89, 11, FxKit.CREAM);
        label(g, display == null ? "—" : number(display.unitPoints()), 19, 362, 89, 35, FxKit.CREAM);
        label(g, display == null ? "—" : number(display.comboPoints()), 144, 362, 89, 35, FxKit.CREAM);
        label(g, "+", 114, 348, 24, 24, FxKit.CREAM);
        well(g, 14, 394, 107, 64, WELL);
        well(g, 131, 394, 107, 64, WELL);
        label(g, "PLAYS LEFT", 20, 414, 95, 10, FxKit.MUTED);
        label(g, "ROUND", 137, 414, 95, 10, FxKit.MUTED);
        label(g, display == null ? "—" : String.valueOf(display.playsRemaining()), 20, 446, 95, 28, FxKit.CREAM);
        label(g, display == null ? "—" : display.round() + " / 3", 137, 446, 95, 26, FxKit.CREAM);
        well(g, 14, 471, 224, 50, WELL);
        label(g, "MATCH TOTAL", 22, 491, 110, 11, BRASS);
        label(g, display == null ? "—" : number(display.matchTotal()), 137, 507, 91, 28, FxKit.CREAM);
    }

    private static void well(GraphicsContext g, double x, double y, double w, double h, Color color) {
        g.setFill(Color.rgb(0, 0, 0, .35));
        g.fillRoundRect(x, y + 3, w, h, 7, 7);
        g.setFill(color);
        g.fillRoundRect(x, y, w, h - 2, 7, 7);
        g.setStroke(Color.rgb(255, 239, 198, .11));
        g.setLineWidth(1);
        g.strokeRoundRect(x, y, w, h - 2, 7, 7);
    }

    private static String number(long value) { return NUMBERS.format(value); }

    private String signedGain() { return (gain > 0 ? "+" : "") + NUMBERS.format(gain) + " POINTS"; }

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
