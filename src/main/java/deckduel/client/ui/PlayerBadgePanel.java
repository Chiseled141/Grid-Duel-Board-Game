package deckduel.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;
import deckduel.core.PlayerColor;

/** Compact public player identity, heraldic shield, mana and remaining pieces. */
public final class PlayerBadgePanel extends JComponent {
    private final boolean own;
    private String name = "";
    private PlayerColor color = PlayerColor.BLUE;
    private int mana, cap = 5, reserved, alive = 7;
    private int score, target = 8;
    private boolean active, paused, initialized;
    private float flash;
    private long flashStart;
    private final Timer timer = new Timer(20, event -> {
        flash = Math.max(0, 1 - (System.nanoTime() - flashStart) / 350_000_000f);
        if (flash == 0) ((Timer) event.getSource()).stop();
        repaint();
    });

    /** Creates either the local player's or opponent's public badge. */
    public PlayerBadgePanel(boolean own) {
        this.own = own;
        setOpaque(false);
        setPreferredSize(new Dimension(228, 128));
        setMinimumSize(new Dimension(180, 128));
    }

    /** Uses a permitted view; reserved mana is supplied only for the local player. */
    public void update(String name, PlayerColor color, int mana, int cap, int reserved,
                       int alive, boolean active, boolean paused, boolean reducedMotion) {
        boolean changed = initialized && this.mana != mana;
        this.name = name;
        this.color = color;
        this.mana = mana;
        this.cap = cap;
        this.reserved = reserved;
        this.alive = alive;
        this.active = active;
        this.paused = paused;
        initialized = true;
        if (reducedMotion || paused) cancelAnimation();
        else if (changed) { flashStart = System.nanoTime(); flash = 1; timer.restart(); }
        setToolTipText(name + " - " + color + " - Mana " + mana + "/" + cap
                + (reserved > 0 ? " (" + reserved + " reserved for Foresight)" : ""));
        repaint();
    }

    /** Shows public progress toward the match's score target. */
    public void updateScore(int score, int target) {
        this.score = score; this.target = target;
        repaint();
    }

    /** Stops transient feedback on lifecycle changes. */
    public void cancelAnimation() { timer.stop(); flash = 0; repaint(); }

    /** Resets feedback history when reconnecting or entering a new match. */
    public void reset() { cancelAnimation(); initialized = false; }

    @Override public void removeNotify() { cancelAnimation(); super.removeNotify(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        try {
            MatchStyle.parchment(g, getWidth(), getHeight());
            Color accent = color == PlayerColor.BLUE ? new Color(48, 78, 91) : new Color(130, 57, 44);
            g.setColor(active && !paused ? accent : new Color(142, 119, 79));
            g.fillRect(0, 10, 3, Math.max(1, getHeight() - 20));
            g.setFont(Theme.bold(9));
            g.drawString((own ? "YOU" : "OPPONENT") + " · " + color, 14, 23);
            paintShield(g, getWidth() - 32, 12, accent);
            g.setFont(Theme.cardTitle(16));
            while (g.getFontMetrics().stringWidth(name) > getWidth() - 28 && g.getFont().getSize2D() > 11)
                g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - 1));
            g.setColor(Theme.INK);
            g.drawString(fit(g, name, getWidth() - 28), 14, 49);
            g.setFont(Theme.normal(10));
            g.setColor(new Color(108, 87, 59));
            g.drawString(alive + " pieces", 14, 69);
            String state = paused ? "Paused" : active ? "To play" : "Waiting";
            g.setColor(active && !paused ? accent : new Color(108, 87, 59));
            g.drawString(state, getWidth() - 14 - g.getFontMetrics().stringWidth(state), 69);
            g.setColor(new Color(156, 129, 84, 110));
            g.drawLine(14, 88, getWidth() - 14, 88);
            g.setColor(accent); g.setFont(Theme.bold(10));
            g.drawString("SCORE  " + score + " / " + target, 14, 83);
            g.setFont(Theme.bold(9));
            g.setColor(Theme.INK);
            g.drawString("MANA", 14, 101);
            String amount = mana + " / " + cap + (reserved > 0 ? " · " + reserved + " held" : "");
            g.setFont(Theme.bold(11));
            g.drawString(amount, getWidth() - 14 - g.getFontMetrics().stringWidth(amount), 101);
            paintMana(g, accent);
        } finally { g.dispose(); }
    }

    private void paintMana(Graphics2D g, Color accent) {
        int segments = Math.min(10, Math.max(1, cap));
        int width = Math.max(1, getWidth() - 28), gap = 3;
        double segment = (width - gap * (segments - 1)) / (double) segments;
        double available = Math.max(0, mana - reserved) / (double) Math.max(1, cap) * segments;
        double total = Math.max(0, mana) / (double) Math.max(1, cap) * segments;
        for (int i = 0; i < segments; i++) {
            int x = 14 + (int) (i * (segment + gap));
            int w = Math.max(1, (int) segment);
            g.setColor(new Color(201, 185, 148)); g.fillRect(x, 111, w, 7);
            double fill = Math.max(0, Math.min(1, available - i));
            if (fill > 0) { g.setColor(flash > 0 ? accent.brighter() : accent); g.fillRect(x, 111, (int) Math.ceil(w * fill), 7); }
            if (reserved > 0 && total > i && available < i + 1) {
                g.setColor(new Color(159, 116, 41)); g.drawRect(x, 111, w - 1, 6);
            }
        }
    }

    private void paintShield(Graphics2D g, int x, int y, Color accent) {
        var shield = new java.awt.geom.Path2D.Double();
        shield.moveTo(x, y); shield.lineTo(x + 17, y); shield.lineTo(x + 16, y + 11);
        shield.quadTo(x + 13, y + 17, x + 8, y + 20);
        shield.quadTo(x + 2, y + 16, x + 1, y + 11); shield.closePath();
        g.setColor(accent); g.fill(shield);
        g.setColor(new Color(244, 230, 193));
        g.fillRect(x + 7, y + 3, 3, 11); g.fillRect(x + 4, y + 6, 9, 3);
        g.setColor(new Color(105, 78, 41)); g.setStroke(new BasicStroke(1)); g.draw(shield);
    }

    private String fit(Graphics2D g, String value, int available) {
        if (g.getFontMetrics().stringWidth(value) <= available) return value;
        int end = value.length();
        while (end > 0 && g.getFontMetrics().stringWidth(value.substring(0, end) + "…") > available) end--;
        return value.substring(0, end) + "…";
    }
}
