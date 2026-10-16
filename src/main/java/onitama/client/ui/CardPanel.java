package onitama.client.ui;

import onitama.core.Card;
import onitama.core.PlayerColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;

/** A printed medieval movement diagram with state overlays and inspect-on-demand details. */
public final class CardPanel extends JComponent {

    private static final int W = 200;
    private static final int H = 240;
    /** Hover animation length (§11: 150–250ms). */
    private static final int HOVER_MS = 180;

    private final Card card;
    private final PlayerColor faceColor;
    private final PlayerColor viewer;
    private final boolean dimmed;
    private boolean selected;

    private float hover; // 0..1 tween for lift/tilt
    private double cardScale = 1.0;
    private Timer tween;

    private boolean reducedMotion = false;

    /** Scales the whole face to fit the hand or the public opponent row. */
    public void setCardScale(double cardScale) {
        if (this.cardScale == cardScale) return;
        this.cardScale = cardScale;
        setPreferredSize(new Dimension((int) ((W + 12) * cardScale),
                (int) ((H + 14) * cardScale)));
        // The enclosing FlowLayout/GridLayout slots must re-layout to the new
        // size before the next click, or presses land on stale bounds.
        revalidate();
        repaint();
    }

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param faceColor the player color whose artwork variant to use
     * @param dimmed true for the opponent's cards (muted, no interaction)
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor faceColor, boolean dimmed, Runnable onSelect) {
        this(card, faceColor, faceColor, dimmed, onSelect);
    }

    /** Shows a public card with its destinations aligned to the viewer's board. */
    public CardPanel(Card card, PlayerColor faceColor, PlayerColor viewer, boolean dimmed, Runnable onSelect) {
        this.card = card;
        this.faceColor = faceColor;
        this.viewer = viewer;
        this.dimmed = dimmed;
        setPreferredSize(new Dimension((int) ((W + 12) * cardScale),
                (int) ((H + 14) * cardScale)));
        setOpaque(false);
        setToolTipText("<html><body style='width:220px'><b>" + CardArt.nameFor(card).toUpperCase()
                + "</b><br>" + movementDescription()
                + "<br><i>" + CardArt.flavorFor(card.id())
                + (faceColor != viewer ? "</i> (opponent's card)" : "</i>") + "</body></html>");
        CardDetails.bind(this);
        {
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    // Press, not click: selection registers instantly and a
                    // press+release that ends outside the card still counts,
                    // so rapid card→piece tapping never loses an input.
                    if (onSelect != null) onSelect.run();
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    startTween(1f);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    startTween(0f);
                }
            });
        }
    }

    public Card card() {
        return card;
    }

    public boolean isSelected() {
        return selected;
    }

    /** Applies the preference to this existing card immediately. */
    public void setReducedMotion(boolean reducedMotion) {
        if (this.reducedMotion == reducedMotion) return;
        this.reducedMotion = reducedMotion;
        if (reducedMotion) cancelAnimations();
    }

    /** Stops hover animation when this presentation is discarded. */
    public void cancelAnimations() {
        if (tween != null) { tween.stop(); tween = null; }
        hover = 0f;
        repaint();
    }

    @Override public void removeNotify() {
        cancelAnimations();
        super.removeNotify();
    }


    /** Marks this card as the currently selected one. */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /** Tweens the hover lift/tilt toward the target (1 = lifted). */
    private void startTween(float target) {
        if (reducedMotion) {
            hover = target;
            repaint();
            return;
        }
        if (tween != null) {
            tween.stop();
        }
        float from = hover;
        long start = System.currentTimeMillis();
        tween = new Timer(16, event -> {
            float progress = Math.min(1f, (System.currentTimeMillis() - start) / (float) HOVER_MS);
            hover = from + (target - from) * progress;
            repaint();
            if (progress >= 1f) {
                ((Timer) event.getSource()).stop();
            }
        });
        tween.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (DeckDrawOverlay.isMasked(this) || CardPlayOverlay.isMasked(this)) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean interactive = !dimmed;
        // A selected card jumps out: higher lift, bigger scale, straight (no
        // tilt). Unselected cards keep the gentle 4px hover lift.
        double lift = interactive ? (selected ? 8 : 4 * hover) : 0;
        double tilt = Math.toRadians(-1.5 * hover * (interactive && !selected ? 1 : 0));
        double fit = Math.min((getWidth() - 12.0) / W, (getHeight() - 16.0) / H);
        double scale = (selected ? 1.04 : 1.0 + 0.02 * hover) * Math.max(.05, fit);
        g.translate(getWidth() / 2.0, getHeight() / 2.0);
        g.rotate(tilt);
        g.scale(scale, scale);
        g.translate(-W / 2.0, -H / 2.0 - lift);
        if (selected && interactive) {
            paintSelectionGlow(g);
        }
        paintCard(g);
        g.dispose();
    }

    /** A feathered gold halo painted behind a selected card (45% alpha). */
    private void paintSelectionGlow(Graphics2D g) {
        float radius = (float) (Math.max(W, H) / 2.0 + 8);
        var gradient = new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Float(W / 2f, H / 2f), radius,
                new float[]{0.65f, 1f},
                new Color[]{Theme.withAlpha(Theme.AMBER, 115),
                        Theme.withAlpha(Theme.AMBER, 0)});
        g.setPaint(gradient);
        g.fill(new java.awt.geom.Ellipse2D.Double(
                W / 2.0 - (W + 16) / 2.0, H / 2.0 - (H + 12) / 2.0, W + 16, H + 12));
    }

    private void paintCard(Graphics2D g) {
        int x0 = 0;
        int y0 = 0;
        int shadow = selected || hover > 0.2f ? Theme.SHADOW_OFFSET + 2 : Theme.SHADOW_OFFSET;

        if (!dimmed) {
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(x0 + shadow, y0 + shadow, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        }

        Image face = CardArt.movementFace(card, faceColor, viewer);
        if (face != null) {
            var clip = new java.awt.geom.RoundRectangle2D.Double(x0, y0, W, H,
                    Theme.RADIUS_MD, Theme.RADIUS_MD);
            g.setClip(clip);
            g.drawImage(face, x0, y0, W, H, null);
            g.setClip(null);
            g.setStroke(new BasicStroke(selected ? 3.5f : 2f));
            g.setColor(selected ? Theme.AMBER : Theme.INK);
            g.draw(clip);
            if (selected) {
                // Double frame on selection: gold outer ring + ink inner line.
                g.setColor(Theme.INK);
                g.setStroke(new BasicStroke(2f));
                g.draw(new java.awt.geom.RoundRectangle2D.Double(x0 + 4, y0 + 4,
                        W - 8, H - 8, Theme.RADIUS_MD - 4, Theme.RADIUS_MD - 4));
            }
        }

        if (dimmed) {
            g.setColor(Theme.withAlpha(Theme.BG, 70));
            g.fillRoundRect(x0, y0, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        }
    }

    /** Exact landing vectors expressed in the same screen orientation as the diagram. */
    private String movementDescription() {
        int direction = faceColor == viewer ? 1 : -1;
        List<String> moves = new java.util.ArrayList<>();
        for (onitama.core.Offset offset : card.offsets()) {
            int dx = direction * offset.dx(), dy = direction * offset.dy();
            List<String> parts = new java.util.ArrayList<>();
            if (dy != 0) parts.add((dy > 0 ? "up " : "down ") + Math.abs(dy));
            if (dx != 0) parts.add((dx > 0 ? "right " : "left ") + Math.abs(dx));
            moves.add(String.join(" + ", parts));
        }
        return String.join("; ", moves);
    }
}
