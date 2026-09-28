package onitama.client.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The retro-pop component kit shared by every screen: pill buttons with hard
 * charcoal shadows (the fireship.dev recipe), sticker panels, heading
 * banners and small drawn motifs. Everything is plain Swing painted with
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

    /** Pill variants matching fireship.dev's button styles. */
    public enum Pill {
        /** Gold fill, ink text — the primary action. */
        GOLD(Theme.AMBER, Theme.INK, Theme.INK, false),
        /** Coal fill, gold border and text — secondary action on dark. */
        GOLD_OUTLINE(Theme.BG, Theme.AMBER, Theme.AMBER, true),
        /** Coal fill, cream border and text — neutral action on dark. */
        CREAM_OUTLINE(Theme.BG, Theme.CREAM, Theme.CREAM, true),
        /** Coal fill, red border and text — destructive action. */
        DANGER(Theme.BG, Theme.CORAL, Theme.CORAL, true);

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
     * Creates a pill button: fully rounded, uppercase display font, and a
     * hard charcoal shadow that the button "sinks" into when pressed.
     */
    public static JButton pill(String text, Pill variant) {
        JButton button = new JButton(text.toUpperCase()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - SHADOW;
                int h = getHeight() - SHADOW;
                int arc = h;
                if (!getModel().isPressed()) {
                    g.setColor(Theme.SHADOW);
                    g.fillRoundRect(SHADOW, SHADOW, w, h, arc, arc);
                }
                int sink = getModel().isPressed() ? 2 : 0;
                g.setColor(variant.fill);
                g.fillRoundRect(sink, sink, w, h, arc, arc);
                g.setColor(variant.border);
                g.setStroke(new java.awt.BasicStroke(2f));
                g.drawRoundRect(sink, sink, w - 1, h - 1, arc, arc);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
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

    /** Bold ink label — for text sitting on cream stickers. */
    public static JLabel inkLabel(String text, float size) {
        return styled(text, Theme.bold(size), Theme.INK);
    }

    /** A small component that paints the gold starburst flourish. */
    public static JComponent flourish(int size) {
        return new JComponent() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(size + SHADOW, size + SHADOW);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = nice(graphics);
                starburst(g, size / 2, size / 2, size / 2 - 1);
                g.dispose();
            }
        };
    }

    private static JLabel styled(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * A heading banner: cream box, ink uppercase display text, hard shadow.
     * Sizes itself to its text, so {@code setText} refreshes cleanly after a
     * {@code revalidate()}.
     */
    public static class HeadingLabel extends JLabel {
        public HeadingLabel(float size) {
            setFont(Theme.display(size));
            setForeground(Theme.INK);
            setHorizontalAlignment(CENTER);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics metrics = getFontMetrics(getFont());
            int boxW = metrics.stringWidth(getText()) + 32;
            int boxH = metrics.getHeight() + 8;
            int arc = 10;
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(3, 3, boxW, boxH, arc, arc);
            g.setColor(Theme.CREAM);
            g.fillRoundRect(0, 0, boxW, boxH, arc, arc);
            g.setColor(Theme.INK);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(0, 0, boxW - 1, boxH - 1, arc, arc);
            g.setFont(getFont());
            g.setColor(getForeground());
            g.drawString(getText(), 16, metrics.getAscent() + 4);
            g.dispose();
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            return new java.awt.Dimension(
                    metrics.stringWidth(getText()) + 36 + SHADOW,
                    metrics.getHeight() + 12 + SHADOW);
        }
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
        g.setColor(Theme.AMBER);
        g.fillPolygon(x, y, total);
        g.setColor(Theme.INK);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.drawPolygon(x, y, total);
    }

    /**
     * Draws a flat pawn silhouette — the real Onitama pieces are pawns
     * (short students, taller masters), so the board shows pawn profiles
     * instead of flat discs. Centered at {@code cx}, standing on
     * {@code baseY}; filled with the player color, outlined in ink.
     */
    public static void drawPawn(Graphics2D g, int cx, int baseY, int height,
                                Color fill, boolean master) {
        int baseW = Math.max(10, (int) (height * 0.62));
        int baseH = Math.max(4, (int) (height * 0.16));
        int headR = Math.max(4, (int) (height * 0.18));
        int neckW = Math.max(5, (int) (height * 0.18));
        int collarH = Math.max(4, (int) (height * 0.10));
        int headCy = baseY - height + headR;

        // Stem (neck) from head down to the base.
        int stemTop = headCy + headR / 2;
        g.setColor(fill);
        g.fillRoundRect(cx - neckW / 2, stemTop, neckW, baseY - baseH - stemTop + 2,
                neckW, neckW);
        g.setColor(Theme.INK);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.drawRoundRect(cx - neckW / 2, stemTop, neckW, baseY - baseH - stemTop + 2,
                neckW, neckW);

        // Base.
        g.setColor(fill);
        g.fillRoundRect(cx - baseW / 2, baseY - baseH, baseW, baseH, baseH, baseH);
        g.setColor(Theme.INK);
        g.drawRoundRect(cx - baseW / 2, baseY - baseH, baseW, baseH, baseH, baseH);

        // Collar ring where the stem meets the base.
        g.setColor(fill);
        g.fillOval(cx - baseW / 2 + 1, baseY - baseH - collarH / 2, baseW - 2, collarH);
        g.setColor(Theme.INK);
        g.drawOval(cx - baseW / 2 + 1, baseY - baseH - collarH / 2, baseW - 2, collarH);

        // Head.
        g.setColor(fill);
        g.fillOval(cx - headR, headCy - headR, 2 * headR, 2 * headR);
        g.setColor(Theme.INK);
        g.drawOval(cx - headR, headCy - headR, 2 * headR, 2 * headR);

        // Masters wear the gold starburst on their head.
        if (master) {
            starburst(g, cx, headCy, Math.max(5, headR - 2));
        }
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

    static RoundRectangle2D roundRect(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }
}
