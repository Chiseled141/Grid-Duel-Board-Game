package onitama.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;
import onitama.core.PlayerColor;

/** Compact public player identity, abstract crest, mana and remaining pieces. */
public final class PlayerBadgePanel extends JComponent {
    private final boolean own;
    private String name = "";
    private PlayerColor color = PlayerColor.BLUE;
    private int mana, cap = 5, reserved, alive = 6;
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
        setPreferredSize(new Dimension(270, 132));
        setMinimumSize(new Dimension(180, 132));
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

    /** Stops transient feedback on lifecycle changes. */
    public void cancelAnimation() { timer.stop(); flash = 0; repaint(); }

    /** Resets feedback history when reconnecting or entering a new match. */
    public void reset() { cancelAnimation(); initialized = false; }

    @Override public void removeNotify() { cancelAnimation(); super.removeNotify(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        try {
            Color accent = Theme.playerColor(color);
            g.setColor(Theme.SURFACE);
            g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
            g.setColor(active && !paused ? accent : Theme.OUTLINE);
            g.setStroke(new BasicStroke(active && !paused ? 2.5f : 1f));
            g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 13, 13);
            // An abstract compass crest avoids human portraits and needs no external asset.
            g.setColor(accent);
            g.drawOval(14, 19, 48, 48);
            g.fillPolygon(new int[]{38, 52, 38, 24}, new int[]{25, 43, 61, 43}, 4);
            g.setColor(Theme.SURFACE);
            g.fillOval(33, 38, 10, 10);
            g.setFont(Theme.normal(9));
            g.setColor(Theme.CREAM);
            g.drawString((own ? "YOU" : "OPPONENT") + " / " + color, 76, 24);
            g.setFont(Theme.bold(13));
            g.setColor(accent);
            g.drawString(fit(g, name, getWidth() - 90), 76, 45);
            g.setFont(Theme.normal(10));
            g.setColor(Theme.CREAM);
            g.drawString(paused ? "MATCH PAUSED" : active ? "PLAYING" : "WAITING", 76, 63);
            g.setFont(Theme.bold(12));
            g.setColor(flash > 0 ? Color.WHITE : accent);
            g.drawString("MANA  " + mana + "/" + cap, 16, 88);
            g.setFont(Theme.normal(9));
            String detail = reserved > 0 ? reserved + " RESERVED" : alive + " PIECES";
            g.setColor(Theme.CREAM);
            g.drawString(detail, getWidth() - 16 - g.getFontMetrics().stringWidth(detail), 88);
            int width = Math.max(1, getWidth() - 32);
            g.setColor(Theme.OUTLINE);
            g.fillRoundRect(16, 101, width, 10, 6, 6);
            g.setColor(accent);
            g.fillRoundRect(16, 101, (int) (width * Math.max(0, Math.min(1, mana / (double) Math.max(1, cap)))), 10, 6, 6);
        } finally { g.dispose(); }
    }

    private String fit(Graphics2D g, String value, int available) {
        if (g.getFontMetrics().stringWidth(value) <= available) return value;
        int end = value.length();
        while (end > 0 && g.getFontMetrics().stringWidth(value.substring(0, end) + "...") > available) end--;
        return value.substring(0, end) + "...";
    }
}
