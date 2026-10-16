package onitama.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** Shared deck indicator; exposes only its public count and a face-down flight origin. */
public final class DeckStackPanel extends JComponent {
    private final String title;
    private final Color accent;
    private int count;
    private BufferedImage back;

    /** Creates a movement or skill deck indicator without hidden card data. */
    public DeckStackPanel(boolean movement) {
        title = movement ? "MOVEMENT" : "SKILL";
        accent = movement ? Theme.JADE : Theme.GOLD_INK;
        setOpaque(false);
        setPreferredSize(new Dimension(120, 200));
        setToolTipText(movement ? "Shared deck. Played cards return, shuffle, then draw."
                : "Shared deck. Skills are spent once; draw a replacement while cards remain.");
    }

    /** Updates the authoritative count immediately, independently of visual flights. */
    public void setCount(int value) { count = Math.max(0, value); repaint(); }

    /** Returns the public number of remaining cards. */
    public int count() { return count; }

    /** Local rectangle of the top card, also valid for the final draw from an empty deck. */
    public Rectangle topBounds() {
        int height = Math.max(1, Math.min(144, getHeight() - 54));
        int width = Math.max(1, Math.min(getWidth() - 20, (int) (height * .71)));
        return new Rectangle((getWidth() - width) / 2 - 3, 8, width, height);
    }

    /** Caches the identity-free shared back for flight rendering. */
    public BufferedImage backImage() {
        if (back == null) {
            back = new BufferedImage(142, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = back.createGraphics();
            try {
                g.setColor(Theme.INK);
                g.fillRoundRect(0, 0, 142, 200, 8, 8);
                var art = AssetStore.optional("skills/card-back.png");
                if (art != null) g.drawImage(art, 3, 3, 136, 194, null);
            } finally { g.dispose(); }
        }
        return back;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        try {
            Rectangle r = topBounds();
            if (count > 0) {
                for (int layer = 3; layer > 0; layer--) {
                    g.setColor(Theme.INK);
                    g.fillRoundRect(r.x + layer * 2, r.y + layer * 3, r.width, r.height, 6, 6);
                    g.setColor(Theme.CREAM);
                    g.drawRoundRect(r.x + layer * 2, r.y + layer * 3, r.width, r.height, 6, 6);
                }
                g.drawImage(backImage(), r.x, r.y, r.width, r.height, null);
            } else {
                g.setColor(new Color(30, 26, 24, 35));
                g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
                g.setColor(Theme.MUTED);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            }
            // Keep public labels legible over saturated landscape details.
            g.setColor(Theme.CREAM);
            g.fillRoundRect(3, r.y + r.height + 13, Math.max(1, getWidth() - 6), 35, 6, 6);
            g.setFont(Theme.bold(11));
            g.setColor(Theme.INK);
            centered(g, title, r.y + r.height + 27);
            g.setFont(Theme.bold(12));
            g.setColor(count == 0 ? Theme.MUTED_INK : accent);
            centered(g, count == 0 ? "0 / EMPTY" : count + " CARDS", r.y + r.height + 44);
        } finally { g.dispose(); }
    }

    private void centered(Graphics2D g, String text, int y) {
        g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, y);
    }
}
