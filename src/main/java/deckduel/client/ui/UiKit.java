package deckduel.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;

/**
 * Component kit shared by every screen: iron and gold plate buttons, iron
 * plates, heading banners and small drawn motifs. Everything is plain Swing painted with
 * Graphics2D — no images, no extra libraries.
 */
public final class UiKit {

    /** Hard-shadow offset in pixels (never blurred). */
    private static final int SHADOW = 4;

    private UiKit() {
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    /** Button looks matched to the match table's iron plates (see {@code TablePlaque}). */
    public enum Pill {
        /** Aged gold, ink text: the primary action. */
        GOLD(Theme.BRASS_LIGHT, Theme.BRASS_DARK, Theme.BRASS_LIGHT, Theme.INK),
        /** Blackened iron with gold trim and gold text: a secondary action. */
        GOLD_OUTLINE(new Color(0x4A423D), Theme.IRON_DARK, Theme.BRASS, Theme.BRASS_LIGHT),
        /** Blackened iron with gold trim and cream text: a neutral action. */
        CREAM_OUTLINE(new Color(0x4A423D), Theme.IRON_DARK, Theme.BRASS, Theme.CREAM);

        final Color top;
        final Color bottom;
        final Color trim;
        final Color text;

        Pill(Color top, Color bottom, Color trim, Color text) {
            this.top = top;
            this.bottom = bottom;
            this.trim = trim;
            this.text = text;
        }
    }

    /**
     * A table button: a clipped-corner plate with a vertical gradient, an inner gold hairline
     * and a soft shadow; it brightens on hover and sinks a pixel when pressed.
     */
    public static class PillButton extends JButton {
        private static final Color OFF_TOP = new Color(0x2E2A28), OFF_BOTTOM = new Color(0x1E1B1A);
        private Pill variant;

        public PillButton(String text, Pill variant) {
            super(text.toUpperCase());
            this.variant = variant;
            setRolloverEnabled(true);
            refit();
        }

        /** Changes the look, as for the current choice of a pair of toggle-like pills. */
        public void setVariant(Pill next) {
            variant = next;
            repaint();
        }

        /** Keeps the button sized to its label so long text never truncates. */
        private void refit() {
            if (getFont() == null || getText() == null) {
                return;
            }
            FontMetrics metrics = getFontMetrics(getFont());
            setPreferredSize(new java.awt.Dimension(
                    metrics.stringWidth(getText()) + 62,
                    metrics.getHeight() + 24 + SHADOW));
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            refit();
        }

        @Override
        public void setFont(Font font) {
            super.setFont(font);
            refit();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = nice(graphics);
            boolean pressed = getModel().isPressed();
            boolean hover = getModel().isRollover();
            int w = getWidth() - SHADOW;
            int h = getHeight() - SHADOW;
            int sink = pressed ? 1 : 0;
            if (!pressed) {
                g.setColor(new Color(0, 0, 0, 90));
                g.fill(chamfer(1, 3, w, h, 5));
            }
            g.translate(0, sink);
            g.setColor(Theme.WOOD_LINE);
            g.fill(chamfer(0, 0, w, h, 5));
            boolean enabled = isEnabled();
            hover &= enabled;
            // Disabled is plain dark iron with readable faded lettering, never a washed-out gold.
            Color top = !enabled ? OFF_TOP : hover ? brighter(variant.top, 1.18f) : variant.top;
            Color bottom = !enabled ? OFF_BOTTOM : hover ? brighter(variant.bottom, 1.3f) : variant.bottom;
            g.setPaint(new java.awt.GradientPaint(0, 0, top, 0, h, bottom));
            g.fill(chamfer(1, 1, w - 2, h - 2, 4));
            g.setColor(!enabled ? Theme.withAlpha(Theme.BRASS, 70) : Theme.withAlpha(variant.trim, hover ? 240 : 170));
            g.setStroke(new java.awt.BasicStroke(1f));
            g.draw(chamfer(2.5, 2.5, w - 5, h - 5, 3));
            if (!enabled) {
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(Theme.FADED);
                g.drawString(getText(), (w - metrics.stringWidth(getText())) / 2,
                        (h - metrics.getHeight()) / 2 + metrics.getAscent());
                g.dispose();
                return;
            }
            g.dispose();
            setForeground(hover && variant != Pill.GOLD ? Theme.BRASS_LIGHT : variant.text);
            super.paintComponent(graphics);
        }
    }

    /** {@code colour} lightened by {@code factor} in HSB brightness, for hover states. */
    private static Color brighter(Color colour, float factor) {
        float[] hsb = Color.RGBtoHSB(colour.getRed(), colour.getGreen(), colour.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1], Math.min(1f, hsb[2] * factor));
    }

    /**
     * A menu tile of {@code w} by {@code h} at the origin: a blackened iron plate with a gold
     * hairline that brightens while {@code lit}, or an aged-gold plate when {@code gold}.
     */
    static void paintTile(Graphics2D g, int w, int h, boolean gold, boolean lit) {
        g.setColor(Theme.WOOD_LINE);
        g.fill(chamfer(0, 0, w, h, 5));
        Color top = gold ? Theme.BRASS_LIGHT : new Color(0x3A3431);
        Color bottom = gold ? Theme.BRASS_DARK : Theme.IRON_DARK;
        if (lit) { top = brighter(top, 1.15f); bottom = brighter(bottom, 1.3f); }
        g.setPaint(new java.awt.GradientPaint(0, 0, top, 0, h, bottom));
        g.fill(chamfer(1, 1, w - 2, h - 2, 4));
        g.setColor(gold ? Theme.withAlpha(Theme.BRASS_LIGHT, 200) : Theme.withAlpha(Theme.BRASS, lit ? 235 : 95));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.draw(chamfer(2.5, 2.5, w - 5, h - 5, 3));
    }

    /** A dark slot of {@code w} by {@code h} sunk into a plate, its gold hairline bright while {@code lit}. */
    static void paintSlot(Graphics2D g, int w, int h, boolean lit) {
        g.setColor(Theme.WOOD_LINE);
        g.fill(chamfer(0, 0, w, h, 4));
        g.setColor(Theme.SLOT);
        g.fill(chamfer(1.5, 1.5, w - 3, h - 3, 3));
        g.setColor(Theme.withAlpha(Theme.BRASS, lit ? 230 : 100));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.draw(chamfer(2.5, 2.5, w - 5, h - 5, 3));
    }

    /** A rectangle with its four corners cut at 45 degrees, the shape of the table's plates. */
    static java.awt.geom.Path2D chamfer(double x, double y, double w, double h, double cut) {
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(x + cut, y);
        path.lineTo(x + w - cut, y);
        path.lineTo(x + w, y + cut);
        path.lineTo(x + w, y + h - cut);
        path.lineTo(x + w - cut, y + h);
        path.lineTo(x + cut, y + h);
        path.lineTo(x, y + h - cut);
        path.lineTo(x, y + cut);
        path.closePath();
        return path;
    }

    public static PillButton pill(String text, Pill variant) {
        PillButton button = new PillButton(text, variant);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(8, 22, 8 + SHADOW, 22 + SHADOW));
        button.setForeground(variant.text);
        button.setFont(Theme.display(14f));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        // Size explicitly from the real font metrics: layouts otherwise shrink
        // pills below their text width and macOS clips the label ("REGIST...").
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        String label = text.toUpperCase();
        button.setPreferredSize(new java.awt.Dimension(
                metrics.stringWidth(label) + 62, metrics.getHeight() + 24 + SHADOW));
        return button;
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    /**
     * An iron plate like the match table's score rail: blackened iron with clipped corners, a
     * soft light from above, a vignette, worn gold trim and four studs, on a soft shadow.
     * Content sits inside the given padding.
     */
    public static JPanel plate(int padding) {
        return new IronPlate(padding);
    }

    /** A dark rounded surface for lists and side panels (no shadow). */
    public static JPanel surface(int padding) {
        return new RoundedPanel(Theme.SURFACE, 14, padding, false);
    }

    private static final int PLATE_CUT = 14;

    /** Paints an iron plate of {@code w} by {@code h} at the origin, its soft shadow below and right. */
    static void paintPlate(Graphics2D g, int w, int h) {
        // A soft shadow built from a few widening translucent outlines.
        for (int i = SHADOW; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 30));
            g.fill(chamfer(i, i + 1, w, h, PLATE_CUT));
        }
        g.setColor(Theme.WOOD_LINE);
        g.fill(chamfer(0, 0, w, h, PLATE_CUT));
        var inner = chamfer(2, 2, w - 4, h - 4, PLATE_CUT - 1);
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, h, new float[] {0f, .35f, 1f},
                new Color[] {new Color(0x3A3431), Theme.IRON, Theme.IRON_DARK}));
        g.fill(inner);
        g.setClip(inner);
        g.setPaint(new java.awt.RadialGradientPaint(w / 2f, h * .35f, Math.max(w, h) * .75f,
                new float[] {.55f, 1f}, new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 115)}));
        g.fillRect(0, 0, w, h);
        g.setClip(null);
        g.setColor(Theme.withAlpha(Theme.BRASS, 140));
        g.setStroke(new java.awt.BasicStroke(1.2f));
        g.draw(chamfer(6.5, 6.5, w - 13, h - 13, PLATE_CUT - 4));
        for (int[] stud : new int[][] {{11, 11}, {w - 11, 11}, {11, h - 11}, {w - 11, h - 11}}) {
            g.setPaint(new java.awt.RadialGradientPaint(stud[0] - .8f, stud[1] - .8f, 3f,
                new float[] {0f, 1f}, new Color[] {Theme.BRASS_LIGHT, Theme.BRASS_DARK}));
            g.fill(new java.awt.geom.Ellipse2D.Double(stud[0] - 2.6, stud[1] - 2.6, 5.2, 5.2));
        }
    }

    /** The painted iron plate behind {@link #plate}. */
    static class IronPlate extends JPanel {

        IronPlate(int padding) {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(padding, padding, padding + SHADOW, padding + SHADOW));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = nice(graphics);
            paintPlate(g, getWidth() - SHADOW, getHeight() - SHADOW);
            g.dispose();
        }
    }

    /** Rounded panel base for {@link #surface}; children layout in insets. */
    static class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;
        private final boolean shadow;

        RoundedPanel(Color fill, int radius, int padding, boolean shadow) {
            this.fill = fill;
            this.radius = radius;
            this.shadow = shadow;
            setOpaque(false);
            int extra = shadow ? SHADOW : 0;
            setBorder(BorderFactory.createEmptyBorder(padding, padding,
                    padding + extra, padding + extra));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - (shadow ? SHADOW : 0);
            int h = getHeight() - (shadow ? SHADOW : 0);
            if (shadow) {
                g.setColor(Theme.SHADOW);
                g.fillRoundRect(SHADOW, SHADOW, w, h, radius, radius);
            }
            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, radius, radius);
            g.setColor(Theme.INK);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Labels
    // ------------------------------------------------------------------

    /** Plain label in cream body text. */
    public static JLabel label(String text, float size) {
        return styled(text, Theme.normal(size), Theme.CREAM);
    }

    private static JLabel styled(String text, Font font, Color color) {
        JLabel label = new JLabel(text) {
            @Override public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension size = super.getPreferredSize();
                if (getText() == null || getFont() == null || getClientProperty("html") != null) return size;
                // Native Swing measurement and offscreen/font rasterization can round
                // Outfit advances differently. Reserve the larger width plus ink overshoot.
                var context = new java.awt.font.FontRenderContext(null, false, false);
                int inkWidth = (int) Math.ceil(getFont().getStringBounds(getText(), context).getWidth());
                var insets = getInsets();
                return new java.awt.Dimension(Math.max(size.width, inkWidth + insets.left + insets.right) + 6,
                        size.height + 4);
            }

            @Override public java.awt.Dimension getMaximumSize() {
                return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
            }
        };
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** Tiny uppercase caption (stat labels, helper text). */
    public static JLabel caption(String text, Color color) {
        return styled(text.toUpperCase(), Theme.normal(9f), color);
    }

    /**
     * Music toggle on an iron tile, with brass notes and a slash while muted.
     * The caller controls playback and persists the choice.
     */
    public static JToggleButton soundToggle() {
        JToggleButton toggle = new JToggleButton() {
            {
                setSelected(true);
                setRolloverEnabled(true);
                setOpaque(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                setPreferredSize(new java.awt.Dimension(44, 40));
                addItemListener(event -> repaint());
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = nice(graphics);
                boolean hover = getModel().isRollover() || isFocusOwner();
                paintTile(g, getWidth(), getHeight(), false, hover);
                if (getModel().isPressed()) g.translate(0, 1);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g.setColor(hover ? Theme.CREAM : isSelected() ? Theme.BRASS_LIGHT : Theme.FADED);
                g.fillOval(cx - 10, cy + 3, 7, 5);
                g.fillOval(cx + 2, cy, 7, 5);
                g.fillRect(cx - 5, cy - 7, 2, 13);
                g.fillRect(cx + 7, cy - 10, 2, 13);
                g.fillPolygon(new int[]{cx - 5, cx + 9, cx + 9, cx - 5},
                        new int[]{cy - 7, cy - 10, cy - 6, cy - 3}, 4);
                g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                if (!isSelected()) g.drawLine(cx - 11, cy - 11, cx + 11, cy + 11);
                g.dispose();
            }
        };
        return toggle;
    }

    // ------------------------------------------------------------------
    // Field borders
    // ------------------------------------------------------------------

    /**
     * The border of a text field sunk into an iron plate: a dark rim with a gold hairline that
     * brightens while the field has focus. The field itself is filled with {@link Theme#SLOT}.
     */
    public static javax.swing.border.Border fieldBorder() {
        return new javax.swing.border.Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Graphics2D g2 = nice(g);
                g2.setColor(Theme.WOOD_LINE);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawRect(x + 1, y + 1, w - 2, h - 2);
                g2.setColor(Theme.withAlpha(Theme.BRASS, c.isFocusOwner() ? 230 : 110));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRect(x + 2, y + 2, w - 5, h - 5);
                g2.dispose();
            }

            @Override
            public java.awt.Insets getBorderInsets(Component c) {
                return new java.awt.Insets(8, 12, 8, 12);
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------
    // Themed dialogs (game over, help) — no stock JOptionPane chrome
    // ------------------------------------------------------------------

    /**
     * The painted root panel for an undecorated themed dialog: an iron plate like
     * {@link #plate} on a transparent background. {@link #setAppear} tweens a 0.9→1.0 scale-in
     * of the children.
     */
    public static class DialogSurface extends JPanel {
        private float appear = 1f;

        /** An empty surface; children are laid out inside its border. */
        public DialogSurface() {
            setOpaque(false);
        }

        /** Sets the scale-in progress (0..1); 1 is fully shown. */
        public void setAppear(float appear) {
            this.appear = appear;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = nice(graphics);
            paintPlate(g, getWidth() - SHADOW, getHeight() - SHADOW);
            g.dispose();
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            double scale = 0.9 + 0.1 * appear;
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            g.translate(cx, cy);
            g.scale(scale, scale);
            g.translate(-cx, -cy);
            super.paintChildren(g);
            g.dispose();
        }
    }


    /** Runs a 200ms ease-out scale-in tween (0.9 → 1.0) for dialog surfaces. */
    public static void playAppearTween(DialogSurface surface) {
        surface.setAppear(0f);
        long start = System.currentTimeMillis();
        javax.swing.Timer tween = new javax.swing.Timer(30, event -> {
            float progress = Math.min(1f,
                    (System.currentTimeMillis() - start) / 200f);
            float eased = 1 - (1 - progress) * (1 - progress);
            surface.setAppear(0.9f + 0.1f * eased);
            if (progress >= 1f) {
                ((javax.swing.Timer) event.getSource()).stop();
            }
        });
        tween.start();
    }

    /**
     * Themed yes/no confirmation replacing the stock JOptionPane: an iron
     * DialogSurface with the question in cream, an outline No pill and a
     * {@code yesVariant}-colored Yes pill of equal width. Blocks the EDT
     * like JOptionPane; ESC, ✕ and No all close it as "No". The appear
     * tween keeps animating while it waits (Swing timers stay live).
     */
    public static boolean confirmDialog(Component parent, String title, String message,
                                        String yesText, Pill yesVariant) {
        JDialog dialog = themedDialog(parent, title, message);
        // Equal-width pill pair on one row; No keeps the dialog open-state.
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        JButton no = pill("No", Pill.CREAM_OUTLINE);
        no.addActionListener(event -> dialog.dispose());
        buttons.add(no);
        final boolean[] accepted = {false};
        JButton yes = pill(yesText, yesVariant);
        yes.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        buttons.add(yes);
        show(dialog, buttons);
        return accepted[0];
    }

    /**
     * Themed notice replacing the stock JOptionPane message box: an iron DialogSurface with
     * {@code message} in cream and one gold OK pill. Blocks the EDT like JOptionPane; Enter,
     * ESC and OK all close it.
     */
    public static void noticeDialog(Component parent, String title, String message) {
        JDialog dialog = themedDialog(parent, title, message);
        JButton ok = pill("OK", Pill.GOLD);
        ok.addActionListener(event -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(ok);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        show(dialog, buttons);
    }

    /** An undecorated modal dialog on an iron surface with {@code message} centred; ESC closes it. */
    private static JDialog themedDialog(Component parent, String title, String message) {
        Window owner = parent instanceof Window window ? window
                : SwingUtilities.getWindowAncestor(parent);
        DialogSurface surface = new DialogSurface();
        surface.setLayout(new BorderLayout(0, 14));
        surface.setBorder(BorderFactory.createEmptyBorder(24, 26, 24 + SHADOW, 26 + SHADOW));
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setContentPane(surface);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                        "closeDialog");
        dialog.getRootPane().getActionMap().put("closeDialog",
                new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent event) {
                        dialog.dispose();
                    }
                });
        JLabel text = new JLabel(message, SwingConstants.CENTER);
        text.putClientProperty("html.disable", Boolean.TRUE);
        text.setFont(Theme.bold(15f));
        text.setForeground(Theme.CREAM);
        surface.add(text, BorderLayout.CENTER);
        return dialog;
    }

    /** Adds {@code buttons}, sizes the dialog to fit (at least 360 by 180) and blocks until it closes. */
    private static void show(JDialog dialog, JPanel buttons) {
        DialogSurface surface = (DialogSurface) dialog.getContentPane();
        surface.add(buttons, BorderLayout.SOUTH);
        java.awt.Dimension natural = surface.getPreferredSize();
        dialog.setSize(Math.max(360, natural.width), Math.max(180, natural.height));
        dialog.setLocationRelativeTo(dialog.getOwner());
        playAppearTween(surface);
        dialog.setVisible(true); // blocks here until closed
    }

    // ------------------------------------------------------------------
    // Dialog & menu theme (UIManager defaults for JOptionPane, menus, chooser)
    // ------------------------------------------------------------------

    /** Makes stock dialogs, menus and file choosers match the dark theme. */
    public static void installSystemTheme() {
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("OptionPane.background", Theme.BG);
        UIManager.put("OptionPane.messageForeground", Theme.CREAM);
        UIManager.put("Label.foreground", Theme.CREAM);
        UIManager.put("MenuBar.background", Theme.BG);
        UIManager.put("MenuBar.foreground", Theme.CREAM);
        UIManager.put("Menu.foreground", Theme.CREAM);
        UIManager.put("Menu.background", Theme.BG);
        UIManager.put("MenuItem.foreground", Theme.CREAM);
        UIManager.put("MenuItem.background", Theme.BG);
        UIManager.put("MenuItem.selectionForeground", Theme.INK);
        UIManager.put("MenuItem.selectionBackground", Theme.AMBER);
        UIManager.put("Menu.selectionForeground", Theme.INK);
        UIManager.put("Menu.selectionBackground", Theme.AMBER);
        UIManager.put("TextField.background", Theme.SURFACE);
        UIManager.put("TextField.foreground", Theme.CREAM);
        UIManager.put("List.background", Theme.SURFACE);
        UIManager.put("List.foreground", Theme.CREAM);
        UIManager.put("ScrollBar.track", Theme.BG);
        UIManager.put("ScrollBar.thumb", Theme.SURFACE);
    }

    /** Convenience: rounded clipping for a component's graphics context. */
    static Graphics2D nice(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

}
