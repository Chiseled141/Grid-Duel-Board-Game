package onitama.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** Latest public skill beside the board; a visual reference, never a gameplay discard pile. */
final class PlayedCardArea extends JComponent {
    private final String caption;
    private BufferedImage face;
    private boolean arriving;
    private String details;
    private final CardDetails inspection;

    PlayedCardArea(String caption) {
        this.caption = caption;
        setOpaque(false);
        inspection = CardDetails.bind(this);
    }

    void arriving(boolean value) { arriving = value; if (value) inspection.hide(); repaint(); }
    void showFace(BufferedImage value) { showCard(value, null); }
    void showCard(BufferedImage value, String text) {
        inspection.hide();
        face = value; details = text; repaint();
    }
    void clear() { inspection.hide(); face = null; details = null; arriving = false; repaint(); }

    @Override public String getToolTipText() { return face == null || arriving ? null : details; }

    Rectangle cardBounds() {
        int width = Math.max(1, getWidth() - 6), height = Math.max(1, getHeight() - 28);
        if (face != null) {
            double scale = Math.min((double) width / face.getWidth(), (double) height / face.getHeight());
            width = Math.max(1, (int) (face.getWidth() * scale));
            height = Math.max(1, (int) (face.getHeight() * scale));
        }
        return new Rectangle((getWidth() - width) / 2, 24, width, height);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = UiKit.nice(graphics);
        try {
            g.setFont(Theme.normal(10));
            while (g.getFontMetrics().stringWidth(caption) > getWidth() - 6 && g.getFont().getSize2D() > 7)
                g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - .5f));
            MatchStyle.text(g, caption, 3, 14, MatchStyle.TEXT);
            Rectangle bounds = cardBounds();
            if (face != null && !arriving) g.drawImage(face, bounds.x, bounds.y, bounds.width, bounds.height, null);
            else if (face == null) {
                g.setColor(new Color(255, 232, 184, 45));
                g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 8, 8);
            }
        } finally { g.dispose(); }
    }
}
