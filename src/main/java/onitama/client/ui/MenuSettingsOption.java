package onitama.client.ui;

import onitama.core.PlayerColor;
import java.awt.*;
import javax.swing.*;

/** Compact settings controls that keep previews and selection visible over the scenery. */
final class MenuSettingsOption extends JButton {
    private enum Kind { PIECE, CHOICE, TOGGLE, ACTION }
    private final Kind kind;
    private final int style;
    private final String detail;
    private static final Color IVORY = new Color(255, 238, 200);
    private static final Color DARK = new Color(25, 26, 23);

    private MenuSettingsOption(String title, String detail, Kind kind, int style, Runnable action) {
        super(title);
        this.kind = kind; this.style = style; this.detail = detail;
        setFont(Theme.normal(15f));
        setPreferredSize(new Dimension(kind == Kind.PIECE ? 104 : 330, kind == Kind.PIECE ? 104 : 56));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
        setRolloverEnabled(true); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleDescription(detail);
        if (action != null) addActionListener(event -> action.run());
    }

    static JButton piece(String title, int style, Runnable action) {
        return new MenuSettingsOption(title, "Preview of " + title, Kind.PIECE, style, action);
    }

    static JButton choice(String title, String detail, Runnable action) {
        return new MenuSettingsOption(title, detail, Kind.CHOICE, 0, action);
    }

    static JButton route(String title, String detail, Runnable action) {
        var button = new MenuSettingsOption(title, detail, Kind.ACTION, 0, action);
        button.putClientProperty(MenuNavigation.ROUTE, Boolean.TRUE);
        return button;
    }

    static JButton motion() {
        return new MenuSettingsOption("Reduced motion: Off", "Limit background and card animation.", Kind.TOGGLE, 0, null);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = UiKit.nice(graphics);
        try {
            boolean managed = Boolean.TRUE.equals(getClientProperty(MenuNavigation.MANAGED));
            boolean selected = managed ? Boolean.TRUE.equals(getClientProperty(MenuNavigation.ACTIVE))
                    : isSelected() && kind != Kind.TOGGLE;
            boolean hover = isEnabled() && (managed ? selected : getModel().isRollover() || isFocusOwner());
            Color fill = managed ? new Color(255, 216, 143) : IVORY;
            if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.75f));
            int width = getWidth(), height = getHeight();
            g.setColor(selected ? fill : new Color(22, 28, 27, hover ? 190 : 145));
            g.fillRoundRect(1, 1, width - 2, height - 2, 8, 8);
            g.setColor(selected && isFocusOwner() ? DARK
                    : selected || hover ? IVORY : new Color(255, 238, 200, 75));
            g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g.drawRoundRect(1, 1, width - 3, height - 3, 8, 8);
            g.setColor(selected ? DARK : IVORY);
            if (kind == Kind.PIECE) paintPiece(g);
            else if (kind == Kind.TOGGLE) paintToggle(g);
            else paintChoice(g, selected);
        } finally { g.dispose(); }
    }

    private void paintPiece(Graphics2D g) {
        g.drawImage(MedievalPieces.image(PlayerColor.BLUE, style), (getWidth() - 51) / 2, 9, 51, 51, null);
        g.setFont(Theme.normal(12f));
        String[] words = getText().split(" ", 2);
        centered(g, words[0], 77); centered(g, words[1], 92);
        if (isSelected()) {
            g.setColor(DARK); g.fillOval(getWidth() - 20, 8, 12, 12);
            g.setColor(IVORY); g.setStroke(new BasicStroke(1.4f));
            g.drawLine(getWidth() - 17, 14, getWidth() - 15, 16);
            g.drawLine(getWidth() - 15, 16, getWidth() - 11, 12);
        }
    }

    private void paintChoice(Graphics2D g, boolean selected) {
        int x = kind == Kind.ACTION ? 14 : 38;
        g.setStroke(new BasicStroke(1.3f));
        if (kind != Kind.ACTION) {
            g.drawOval(13, 21, 13, 13);
            if (selected) g.fillOval(17, 25, 5, 5);
        }
        g.setFont(getFont()); g.drawString(getText(), x, 24);
        g.setFont(Theme.normal(11f)); g.setColor(selected ? new Color(85, 78, 62) : new Color(224, 215, 194));
        g.drawString(detail, x, 42);
    }

    private void paintToggle(Graphics2D g) {
        g.setFont(getFont()); g.drawString("Reduced motion", 13, 24);
        g.setFont(Theme.normal(11f)); g.setColor(new Color(224, 215, 194));
        g.drawString(detail, 13, 42);
        int x = getWidth() - 55;
        g.setColor(isSelected() ? IVORY : new Color(255, 238, 200, 80));
        g.fillRoundRect(x, 17, 41, 22, 22, 22);
        g.setColor(isSelected() ? DARK : IVORY);
        g.fillOval(x + (isSelected() ? 22 : 3), 20, 16, 16);
    }

    private void centered(Graphics2D g, String text, int baseline) {
        g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }
}
