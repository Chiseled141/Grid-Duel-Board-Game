package onitama.client.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import javax.swing.JComponent;

/** Identity-free face-down card: it receives no card ID, definition, or tooltip. */
public final class CardBackPanel extends JComponent {
    /** Creates a shared back without accepting any hidden information. */
    public CardBackPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(61, 80));
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (DeckDrawOverlay.isMasked(this)) return;
        Graphics2D g = UiKit.nice(graphics);
        Image back = CardArt.backFace();
        g.setColor(Theme.INK);
        g.fillRoundRect(2, 3, getWidth() - 3, getHeight() - 3, 7, 7);
        if (back != null) g.drawImage(back, 0, 0, getWidth() - 3, getHeight() - 4, null);
        g.dispose();
    }
}
