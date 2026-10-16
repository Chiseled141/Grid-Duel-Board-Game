package onitama.client.ui;

import java.awt.*;
import javax.swing.*;

/** Shared floating controls and translucent ink surfaces for the match scenery. */
final class MatchStyle {
    static final Color SURFACE = new Color(18, 25, 27, 165);
    static final Color TEXT = new Color(255, 248, 232);
    static final Color GOLD = new Color(255, 211, 133);
    private MatchStyle() { }

    /** Quiet parchment and a thin brass keyline, shared by the match-side documents. */
    static void parchment(Graphics2D g, int width, int height) {
        g.setColor(new Color(16, 10, 5, 90)); g.fillRoundRect(3, 4, width - 3, height - 4, 5, 5);
        g.setPaint(new GradientPaint(0, 0, new Color(242, 230, 199), width, height, new Color(225, 208, 167)));
        g.fillRoundRect(0, 0, width - 3, height - 4, 5, 5);
        g.setColor(new Color(138, 106, 58)); g.drawRoundRect(0, 0, width - 4, height - 5, 5, 5);
        g.setColor(new Color(255, 247, 222, 160)); g.drawRect(4, 4, width - 12, height - 13);
    }

    static JLabel label(String text, float size) {
        return new JLabel(text) {
            { setFont(Theme.normal(size)); setForeground(TEXT); setOpaque(false); }
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics);
                g.setFont(getFont());
                int width = g.getFontMetrics().stringWidth(getText());
                int x = getHorizontalAlignment() == SwingConstants.CENTER ? (getWidth() - width) / 2 : 0;
                text(g, getText(), x, (getHeight() - g.getFontMetrics().getHeight()) / 2
                        + g.getFontMetrics().getAscent(), getForeground());
                g.dispose();
            }
        };
    }

    static JButton action(String text) {
        return new JButton(text) {
            {
                setFont(Theme.normal(15)); setOpaque(false); setContentAreaFilled(false);
                setBorderPainted(false); setFocusPainted(false); setRolloverEnabled(true);
                setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics);
                g.setFont(getFont());
                boolean active = isEnabled() && (getModel().isRollover() || isFocusOwner() || isSelected());
                Color color = !isEnabled() ? new Color(167, 169, 162) : active ? GOLD : TEXT;
                int x = (getWidth() - g.getFontMetrics().stringWidth(getText())) / 2;
                int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                text(g, getText(), x, y, color);
                if (active) { g.setColor(color); g.drawLine(x, y + 4, getWidth() - x, y + 4); }
                g.dispose();
            }
        };
    }

    static void text(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 170)); g.drawString(text, x + 1, y + 2);
        g.setColor(color); g.drawString(text, x, y);
    }
}
