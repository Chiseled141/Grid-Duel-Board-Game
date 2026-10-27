package deckduel.client.ui;

import java.awt.*;
import javax.swing.*;

/** Menu rows on iron tiles, matched to the match table's plates; the selected row is aged gold. */
final class MenuSettingsOption extends JButton {
    private enum Kind { CHOICE, TOGGLE, ACTION }
    private final Kind kind;
    private final String detail;
    // Fixed caption of a toggle; its button text carries the On/Off state.
    private final String label;

    private MenuSettingsOption(String title, String detail, Kind kind, Runnable action) {
        super(title);
        this.kind = kind; this.detail = detail; this.label = title;
        setFont(Theme.normal(15f));
        setPreferredSize(new Dimension(330, 56));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
        setRolloverEnabled(true); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleDescription(detail);
        if (action != null) addActionListener(event -> action.run());
    }

    static JButton choice(String title, String detail, Runnable action) {
        return new MenuSettingsOption(title, detail, Kind.CHOICE, action);
    }

    static JButton route(String title, String detail, Runnable action) {
        var button = new MenuSettingsOption(title, detail, Kind.ACTION, action);
        button.putClientProperty(MenuNavigation.ROUTE, Boolean.TRUE);
        return button;
    }

    /** An On/Off switch captioned {@code label}; callers keep its text as "label: On/Off". */
    static JButton toggle(String label, String detail) {
        return new MenuSettingsOption(label, detail, Kind.TOGGLE, null);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = UiKit.nice(graphics);
        try {
            boolean managed = Boolean.TRUE.equals(getClientProperty(MenuNavigation.MANAGED));
            boolean selected = managed ? Boolean.TRUE.equals(getClientProperty(MenuNavigation.ACTIVE))
                    : isSelected() && kind != Kind.TOGGLE;
            boolean hover = isEnabled() && (managed ? selected : getModel().isRollover() || isFocusOwner());
            if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.6f));
            // The selected or keyboard-active row is an aged-gold plate; the rest are iron.
            UiKit.paintTile(g, getWidth(), getHeight(), selected, hover);
            if (kind == Kind.TOGGLE) paintToggle(g, hover);
            else paintChoice(g, selected, hover);
        } finally { g.dispose(); }
    }

    private void paintChoice(Graphics2D g, boolean selected, boolean hover) {
        int x = kind == Kind.ACTION ? 16 : 40;
        if (kind != Kind.ACTION) {
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(selected ? Theme.INK : Theme.BRASS);
            g.drawOval(14, 21, 13, 13);
            if (selected) g.fillOval(18, 25, 5, 5);
        }
        g.setFont(getFont());
        g.setColor(selected ? Theme.INK : hover ? Theme.BRASS_LIGHT : Theme.CREAM);
        g.drawString(getText(), x, 25);
        g.setFont(Theme.normal(11f));
        g.setColor(selected ? new Color(0x4A3418) : Theme.FADED);
        g.drawString(detail, x, 42);
    }

    /** The caption, its detail, and a switch on the right: lit gold when on, a dark slot when off. */
    private void paintToggle(Graphics2D g, boolean hover) {
        g.setFont(getFont());
        g.setColor(hover ? Theme.BRASS_LIGHT : Theme.CREAM);
        g.drawString(label, 16, 25);
        g.setFont(Theme.normal(11f));
        g.setColor(Theme.FADED);
        g.drawString(detail, 16, 42);
        Graphics2D track = (Graphics2D) g.create();
        try {
            track.translate(getWidth() - 58, 17);
            if (isSelected()) UiKit.paintTile(track, 42, 22, true, false);
            else UiKit.paintSlot(track, 42, 22, hover);
            track.setPaint(new GradientPaint(0, 3, isSelected() ? Theme.CREAM : Theme.FADED, 0, 19,
                    isSelected() ? Theme.BRASS : new Color(0x5A524B)));
            track.fill(UiKit.chamfer(isSelected() ? 23 : 3, 3, 16, 16, 3));
        } finally { track.dispose(); }
    }

}
