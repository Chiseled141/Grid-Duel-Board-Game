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
 * Component kit shared by every screen: jade and gold pill buttons, sticker
 * panels, heading banners and small drawn motifs. Everything is plain Swing painted with
 * Graphics2D — no images, no extra libraries.
 */
public final class UiKit {

    /** Hover animation length in ms (§11). */
    public static final int HOVER_MS = 180;
    /** Hard-shadow offset in pixels (never blurred). */
    private static final int SHADOW = 4;

    private UiKit() {
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    /** Lightens a color slightly for hover states. */
    public static Color lighten(Color color) {
        // A restrained tint keeps ivory labels readable on the jade hover fill.
        return new Color(color.getRed() + (int) ((255 - color.getRed()) * .12),
                color.getGreen() + (int) ((255 - color.getGreen()) * .12),
                color.getBlue() + (int) ((255 - color.getBlue()) * .12));
    }

    /** Coordinated action colors for the painted mountain theme. */
    public enum Pill {
        /** Gold fill, ink text — the primary action. */
        GOLD(Theme.AMBER, Theme.INK, Theme.INK, false),
        /** Warm parchment, brass border and dark lettering for help on the campaign table. */
        PARCHMENT(new Color(237, 222, 186), new Color(143, 107, 54), Theme.INK, false),
        /** Deep jade fill, gold border and text — secondary action on dark. */
        GOLD_OUTLINE(Theme.BG, Theme.AMBER, Theme.AMBER, true),
        /** Jade fill, mint border and ivory text — neutral action on dark. */
        CREAM_OUTLINE(Theme.JADE, Theme.P1_LIGHT, Theme.CREAM, true),
        /** Vermilion fill, coral border and ivory text — destructive action. */
        DANGER(Theme.VERMILION, Theme.CORAL, Theme.CREAM, false);

        final Color fill;
        final Color border;
        final Color text;
        final boolean outline;

        Pill(Color fill, Color border, Color text, boolean outline) {
            this.fill = fill;
            this.border = border;
            this.text = text;
            this.outline = outline;
        }
    }

    /**
     * A pill button: fully rounded, uppercase display font, and a hard
     * charcoal shadow that the button "sinks" into when pressed and lifts
     * slightly on hover. The variant can be changed after construction.
     */
    public static class PillButton extends JButton {
        private Pill variant;

        public PillButton(String text, Pill variant) {
            super(text.toUpperCase());
            this.variant = variant;
            setRolloverEnabled(true);
            refit();
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

        public void setVariant(Pill variant) {
            this.variant = variant;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            boolean enabled = isEnabled();
            boolean pressed = enabled && getModel().isPressed();
            boolean hover = enabled && getModel().isRollover();
            int shadow = pressed ? 1 : hover ? SHADOW + 2 : SHADOW;
            int w = getWidth() - shadow;
            int h = getHeight() - shadow;
            int arc = Math.min(h, 22);
            Color fill = enabled ? variant.fill : Theme.DISABLED;
            Color border = enabled ? variant.border : Theme.DISABLED.darker();
            if (!pressed) {
                g.setColor(Theme.SHADOW);
                g.fillRoundRect(shadow, shadow, w, h, arc, arc);
            }
            int sink = pressed ? 2 : 0;
            g.setColor(hover ? lighten(fill) : fill);
            g.fillRoundRect(sink, sink, w, h, arc, arc);
            g.setColor(border);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(sink, sink, w - 1, h - 1, arc, arc);
            g.dispose();
            setForeground(enabled ? variant.text : Theme.INK);
            super.paintComponent(graphics);
        }
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

    /** A smaller pill for compact cards like the lobby settings. */
    public static PillButton miniPill(String text, Pill variant) {
        PillButton button = pill(text, variant);
        button.setFont(Theme.display(9f));
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        // +36 (not the minimum +30): macOS antialiases slightly wider than
        // the raw metrics, and a clipped tab label reads as a bug.
        button.setPreferredSize(new java.awt.Dimension(
                metrics.stringWidth(text.toUpperCase()) + 36,
                metrics.getHeight() + 16 + SHADOW));
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4 + SHADOW, 10 + SHADOW));
        return button;
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    /**
     * A cream "sticker": rounded rectangle with an ink outline and a hard
     * shadow, like a printed card laid on the table. Content sits inside
     * the given padding.
     */
    public static JPanel sticker(int padding) {
        return new RoundedPanel(Theme.CREAM, 18, padding, true);
    }

    /** A dark rounded surface for lists and side panels (no shadow). */
    public static JPanel surface(int padding) {
        return new RoundedPanel(Theme.SURFACE, 14, padding, false);
    }

    /** Rounded panel base doing the sticker painting; children layout in insets. */
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

    /** Bold cream label. */
    public static JLabel boldLabel(String text, float size) {
        return styled(text, Theme.bold(size), Theme.CREAM);
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
     * Sound toggle for the header: a drawn speaker with sound waves (on) or
     * a slash through it (off). The caller controls playback and persists the choice.
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
                setPreferredSize(new java.awt.Dimension(30, 26));
                addItemListener(event -> repaint());
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2 - 5;
                int cy = getHeight() / 2;
                boolean hover = getModel().isRollover() || isFocusOwner();
                g.setColor(hover ? Theme.AMBER : isSelected()
                        ? new Color(255, 238, 200) : new Color(218, 214, 201));
                g.fillRect(cx - 6, cy - 3, 4, 6);
                g.fillPolygon(new int[]{cx - 2, cx + 3, cx + 3, cx - 2},
                        new int[]{cy - 3, cy - 7, cy + 7, cy + 3}, 4);
                g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                if (isSelected()) {
                    g.drawArc(cx + 4, cy - 5, 6, 10, -60, 120);
                    g.drawArc(cx + 6, cy - 8, 10, 16, -60, 120);
                } else {
                    g.drawLine(cx + 5, cy - 6, cx + 13, cy + 6);
                }
                g.dispose();
            }
        };
        return toggle;
    }

    // ------------------------------------------------------------------
    // Field borders
    // ------------------------------------------------------------------

    /** Rounded stroke border for text fields (cream fill, ink outline). */
    public static javax.swing.border.Border fieldBorder() {
        return new javax.swing.border.Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.INK);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 12, 12);
                g2.dispose();
            }

            @Override
            public java.awt.Insets getBorderInsets(Component c) {
                return new java.awt.Insets(6, 10, 6, 10);
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------
    // Motifs
    // ------------------------------------------------------------------

    /** Draws a gold 8-point starburst (title flourish, master pieces). */
    public static void starburst(Graphics2D g, int cx, int cy, int outerRadius) {
        starburst(g, cx, cy, outerRadius, Theme.AMBER);
    }

    /** Starburst in an arbitrary tint (leaderboard medals). */
    public static void starburst(Graphics2D g, int cx, int cy, int outerRadius, Color fill) {
        int innerRadius = outerRadius / 2;
        int points = 8;
        int total = points * 2;
        int[] x = new int[total];
        int[] y = new int[total];
        for (int i = 0; i < total; i++) {
            double angle = Math.PI * i / points - Math.PI / 2;
            int radius = i % 2 == 0 ? outerRadius : innerRadius;
            x[i] = (int) Math.round(cx + radius * Math.cos(angle));
            y[i] = (int) Math.round(cy + radius * Math.sin(angle));
        }
        g.setColor(fill);
        g.fillPolygon(x, y, total);
        g.setColor(Theme.INK);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.drawPolygon(x, y, total);
    }

    // ------------------------------------------------------------------
    // Themed dialogs (game over, help) — no stock JOptionPane chrome
    // ------------------------------------------------------------------

    /**
     * The painted root panel for an undecorated themed dialog: a rounded
     * sticker with ink border and hard shadow on a transparent background.
     * {@link #setAppear} tweens a 0.9→1.0 scale-in of the children.
     */
    public static class DialogSurface extends JPanel {
        private final Color fill;
        private final int radius;
        private float appear = 1f;

        /** @param fill surface color, @param radius corner radius in px */
        public DialogSurface(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
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
            int shadow = Theme.SHADOW_OFFSET;
            int w = getWidth() - shadow;
            int h = getHeight() - shadow;
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(shadow, shadow, w, h, radius, radius);
            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, radius, radius);
            g.setColor(Theme.INK);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
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

    /**
     * An undecorated modal dialog showing the given surface, centered over
     * {@code owner}, closable with ESC. Callers dispose it from their own
     * buttons. Returns before it is visible; call {@code setVisible(true)}.
     */
    public static javax.swing.JDialog undecoratedDialog(java.awt.Window owner,
                                                        JComponent surface,
                                                        int width, int height) {
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setContentPane(surface);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(owner);
        javax.swing.JRootPane rootPane = dialog.getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                        "closeDialog");
        rootPane.getActionMap().put("closeDialog",
                new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent event) {
                        dialog.dispose();
                    }
                });
        return dialog;
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

    /** A small drawn close (✕) button for overlay dialogs; runs {@code onClose}. */
    public static JButton closeButton(Runnable onClose) {        JButton close = new JButton(new javax.swing.Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = nice(g);
                g2.setColor(Theme.MUTED_INK);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawLine(x + 4, y + 4, x + 14, y + 14);
                g2.drawLine(x + 14, y + 4, x + 4, y + 14);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 18;
            }

            @Override
            public int getIconHeight() {
                return 18;
            }
        });
        close.setToolTipText("Close");
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        close.setPreferredSize(new java.awt.Dimension(24, 24));
        close.addActionListener(event -> onClose.run());
        return close;
    }

    /**
     * Themed yes/no confirmation replacing the stock JOptionPane: a cream
     * DialogSurface with the question in ink, an outline No pill and a
     * {@code yesVariant}-colored Yes pill of equal width. Blocks the EDT
     * like JOptionPane; ESC, ✕ and No all close it as "No". The appear
     * tween keeps animating while it waits (Swing timers stay live).
     */
    public static boolean confirmDialog(Component parent, String title, String message,
                                        String yesText, Pill yesVariant) {
        Window owner = parent instanceof Window window ? window
                : SwingUtilities.getWindowAncestor(parent);
        DialogSurface surface = new DialogSurface(Theme.CREAM, 18);
        surface.setLayout(new BorderLayout(0, 14));
        surface.setBorder(BorderFactory.createEmptyBorder(20, 22, 20 + Theme.SHADOW_OFFSET, 22));

        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setContentPane(surface);
        dialog.setSize(360, 180);
        dialog.setLocationRelativeTo(parent);
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

        JLabel question = new JLabel(message, SwingConstants.CENTER);
        question.setFont(Theme.bold(15f));
        question.setForeground(Theme.INK);
        surface.add(question, BorderLayout.CENTER);

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
        surface.add(buttons, BorderLayout.SOUTH);

        playAppearTween(surface);
        dialog.setVisible(true); // blocks here until closed
        return accepted[0];
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
