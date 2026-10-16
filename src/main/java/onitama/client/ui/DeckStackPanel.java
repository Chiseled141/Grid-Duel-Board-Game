package onitama.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** Decorative draw pile shared by movement and skill-card flights, without counters. */
public final class DeckStackPanel extends JComponent {
    private BufferedImage back;

    /** Creates a neutral pile used only as a shared draw-animation origin. */
    public DeckStackPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(100, 100));
        setName("drawPile");
    }

    /** Local rectangle of the top card, also valid for the final draw from an empty deck. */
    public Rectangle topBounds() {
        int width = Math.max(1, Math.min(getWidth() - 20, (int) ((getHeight() - 16) * (5.0 / 6))));
        int height = Math.max(1, (int) (width * (6.0 / 5)));
        return new Rectangle((getWidth() - width) / 2 - 3, 8, width, height);
    }

    /** Caches the identity-free shared back for flight rendering. */
    public BufferedImage backImage() {
        if (back == null) {
            back = new BufferedImage(400, 480, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = back.createGraphics();
            try {
                g.setColor(Theme.INK);
                g.fillRoundRect(0, 0, 400, 480, 8, 8);
                var art = CardArt.backFace();
                if (art != null) g.drawImage(art, 3, 3, 394, 474, null);
            } finally { g.dispose(); }
        }
        return back;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        try {
            Rectangle r = topBounds();
            for (int layer = 3; layer > 0; layer--) {
                g.setColor(new Color(37, 30, 25));
                g.fillRoundRect(r.x + layer * 2, r.y + layer * 2, r.width, r.height, 5, 5);
                g.setColor(new Color(209, 181, 125));
                g.drawRoundRect(r.x + layer * 2, r.y + layer * 2, r.width, r.height, 5, 5);
            }
            g.drawImage(backImage(), r.x, r.y, r.width, r.height, null);
        } finally { g.dispose(); }
    }

}
