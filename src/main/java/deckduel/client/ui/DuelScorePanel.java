package deckduel.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.accessibility.AccessibleContext;

/** Stacked card-duel score rail; values come from the view, never from UI arithmetic. */
public final class DuelScorePanel extends JComponent {
    private static final Color LEATHER = new Color(0x352B24);
    private static final Color WELL = new Color(0x201D19);
    private static final Color BRASS = new Color(0xAD8954);
    private static final Color BLUE = new Color(0x385D6D);
    private static final Color RED = new Color(0x8B4236);
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);
    private Display display;
    private long changedAt;
    private double gain;
    private float pulse;
    private final Timer animation = new Timer(16, event -> tick());

    /** Presentation-only values; effects describe their real scope without implying a global multiplier. */
    public record Display(long score, long opponentScore, long unitPoints, long comboPoints,
                          long matchTotal, int playsRemaining, int round, String effects) {
        /** Rejects invalid counters before replacing the displayed snapshot. */
        public Display {
            if (playsRemaining < 0 || playsRemaining > 10 || round < 1 || round > 3)
                throw new IllegalArgumentException("Invalid round or remaining plays");
            Objects.requireNonNull(effects, "effects");
        }
    }

    /** Builds an uninitialized rail, suitable for a new or loading match. */
    public DuelScorePanel() {
        setOpaque(false);
        setName("duelScorePanel");
        setPreferredSize(new Dimension(260, 620));
        setMinimumSize(new Dimension(196, 468));
        getAccessibleContext().setAccessibleName("Card duel score");
        getAccessibleContext().setAccessibleDescription("Waiting for match scores");
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() { };
        return accessibleContext;
    }

    /** Applies an accepted view on the EDT; feedback never delays the authoritative displayed score. */
    public void update(Display next, boolean reducedMotion) {
        Objects.requireNonNull(next, "next");
        boolean sameRound = display != null && display.round() == next.round();
        boolean changed = sameRound && display.score() != next.score();
        if (!sameRound || reducedMotion) stopFeedback();
        if (changed && !reducedMotion) {
            gain = (double) next.score() - display.score();
            changedAt = System.nanoTime();
            pulse = 1;
            animation.restart();
        }
        display = next;
        String description = "Round " + next.round() + " of 3. Your score " + next.score()
                + ". Opponent " + next.opponentScore() + ". Unit points " + next.unitPoints()
                + ", combo points " + next.comboPoints() + ". " + next.effects()
                + ". Plays remaining " + next.playsRemaining() + ". Match total " + next.matchTotal();
        getAccessibleContext().setAccessibleDescription(description);
        setToolTipText(description);
        repaint();
    }

    /** Cancels cosmetic feedback when leaving, pausing, or resetting the match. */
    public void stopFeedback() {
        animation.stop();
        pulse = 0;
        repaint();
    }

    /** Clears the old match before another match or reconnect snapshot is attached. */
    public void reset() {
        stopFeedback();
        display = null;
        getAccessibleContext().setAccessibleDescription("Waiting for match scores");
        setToolTipText(null);
        repaint();
    }

    boolean isAnimating() { return animation.isRunning(); }

    private void tick() {
        pulse = Math.max(0, 1 - (System.nanoTime() - changedAt) / 650_000_000f);
        if (pulse == 0) animation.stop();
        repaint();
    }

    @Override public void removeNotify() {
        stopFeedback();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = UiKit.nice(graphics);
        try {
            double scale = Math.min(getWidth() / 260.0, getHeight() / 620.0);
            if (scale <= 0) return;
            g.translate((getWidth() - 260 * scale) / 2, 0);
            g.scale(scale, scale);
            paintRail(g);
        } finally { g.dispose(); }
    }

    private void paintRail(Graphics2D g) {
        g.setColor(Theme.SHADOW);
        g.fillRoundRect(4, 6, 252, 610, 14, 14);
        g.setColor(LEATHER);
        g.fillRoundRect(0, 0, 252, 610, 14, 14);
        g.setColor(BRASS);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(5, 5, 242, 600, 10, 10);
        label(g, "DECK DUEL", 14, 33, 224, 19, BRASS);
        well(g, 14, 48, 224, 74, WELL);
        label(g, "OPPONENT'S ROUND SCORE", 23, 68, 206, 10, Theme.MUTED);
        label(g, display == null ? "—" : number(display.opponentScore()), 23, 108, 206, 34, Theme.CREAM);
        well(g, 14, 135, 224, 116, WELL);
        label(g, "YOUR ROUND SCORE", 23, 158, 206, 12, BRASS);
        Color scoreColor = pulse > 0 ? Theme.AMBER : Theme.CREAM;
        label(g, display == null ? "—" : number(display.score()), 23, 218, 206, 56 + pulse * 4, scoreColor);
        label(g, pulse == 0 ? "LIVE TABLE VALUE" : signedGain(), 23, 239, 206, 12, scoreColor);
        label(g, "SCORE BREAKDOWN", 14, 280, 224, 12, Theme.CREAM);
        well(g, 14, 293, 99, 88, BLUE);
        well(g, 139, 293, 99, 88, RED);
        label(g, "UNITS", 19, 315, 89, 11, Theme.CREAM);
        label(g, "COMBOS", 144, 315, 89, 11, Theme.CREAM);
        label(g, display == null ? "—" : number(display.unitPoints()), 19, 362, 89, 35, Theme.CREAM);
        label(g, display == null ? "—" : number(display.comboPoints()), 144, 362, 89, 35, Theme.CREAM);
        label(g, "+", 114, 348, 24, 24, Theme.CREAM);
        well(g, 14, 394, 224, 57, WELL);
        label(g, "LANDMARKS & EFFECTS", 21, 414, 210, 10, BRASS);
        label(g, display == null ? "—" : display.effects(), 21, 437, 210, 14, Theme.CREAM);
        paintCounters(g);
    }

    private void paintCounters(Graphics2D g) {
        well(g, 14, 465, 107, 64, WELL);
        well(g, 131, 465, 107, 64, WELL);
        label(g, "PLAYS LEFT", 20, 485, 95, 10, Theme.MUTED);
        label(g, "ROUND", 137, 485, 95, 10, Theme.MUTED);
        label(g, display == null ? "—" : String.valueOf(display.playsRemaining()), 20, 517, 95, 28, Theme.CREAM);
        label(g, display == null ? "—" : display.round() + " / 3", 137, 517, 95, 26, Theme.CREAM);
        well(g, 14, 542, 224, 50, WELL);
        label(g, "MATCH TOTAL", 22, 562, 110, 11, BRASS);
        label(g, display == null ? "—" : number(display.matchTotal()), 137, 578, 91, 28, Theme.CREAM);
    }

    private static void well(Graphics2D g, int x, int y, int w, int h, Color color) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(x, y + 3, w, h, 7, 7);
        g.setColor(color);
        g.fillRoundRect(x, y, w, h - 2, 7, 7);
        g.setColor(new Color(255, 239, 198, 28));
        g.drawRoundRect(x, y, w, h - 2, 7, 7);
    }

    private static String number(long value) { return NUMBERS.format(value); }

    private String signedGain() {
        return (gain > 0 ? "+" : "") + NUMBERS.format(gain) + " POINTS";
    }

    private static void label(Graphics2D g, String text, int x, int y, int width, float size, Color color) {
        g.setFont(Theme.bold(size));
        while (g.getFontMetrics().stringWidth(text) > width && g.getFont().getSize2D() > 8)
            g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - 1));
        // Effect descriptions can be long; full text remains available through accessibility and the tooltip.
        String fitted = text;
        while (g.getFontMetrics().stringWidth(fitted) > width && fitted.length() > 1)
            fitted = fitted.substring(0, fitted.length() - 2) + "…";
        int left = x + (width - g.getFontMetrics().stringWidth(fitted)) / 2;
        g.setColor(Theme.SHADOW);
        g.drawString(fitted, left + 1, y + 2);
        g.setColor(color);
        g.drawString(fitted, left, y);
    }
}
