package onitama.client.ui;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.Timer;

/** A compact overlapping hand; hovered cards come forward without covering the board. */
public final class CardHandPanel extends JLayeredPane {
    private List<? extends JComponent> cards = List.of();
    private boolean reducedMotion = false;
    private Timer reflowTimer;
    private JComponent departingFace;
    private int departingIndex, departingCount;
    private float departureProgress;
    private Timer departureTimer;
    private float reflowProgress = 1f;
    private final java.util.Map<JComponent, Integer> startXMap = new java.util.HashMap<>();

    /** Creates a transparent hand with room for the hover lift. */
    public CardHandPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(720, 226));
        setMinimumSize(new Dimension(280, 226));
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        if (reducedMotion) cancelAnimations();

    }

    /** Paints an own-card copy above the live hand without adding an input-catching child. */
    public void animateDeparture(JComponent face, int index, int count) {
        cancelDeparture();
        if (reducedMotion) return;
        departingFace = face;
        departingIndex = index;
        departingCount = count;
        departureProgress = 0f;
        long start = System.nanoTime();
        departureTimer = new Timer(16, e -> {
            departureProgress = Math.min(1f, (System.nanoTime() - start) / 200_000_000f);
            if (departureProgress >= 1f) finishDeparture();
            repaint();
        });
        departureTimer.start();
        repaint();
    }

    /** Completes the current visual transition, for example before another local interaction. */
    public void finishDeparture() {
        cancelDeparture();
    }

    /** Discards the old outgoing-card copy while preserving a newly started hand reflow. */
    public void cancelDeparture() {
        if (departureTimer != null) { departureTimer.stop(); departureTimer = null; }
        departingFace = null;
        repaint();
    }

    /** Snaps reflow and discards outgoing-card effects on lifecycle interruption. */
    public void cancelAnimations() {
        cancelDeparture();
        if (reflowTimer != null) { reflowTimer.stop(); reflowTimer = null; }
        reflowProgress = 1f;
        startXMap.clear();
        doLayout();
        repaint();
    }

    @Override public void removeNotify() {
        cancelAnimations();
        super.removeNotify();
    }

    @Override protected void paintChildren(java.awt.Graphics graphics) {
        super.paintChildren(graphics);
        if (departingFace == null) return;
        int height = Math.min(210, Math.max(1, getHeight() - 16));
        int width = 176;
        double step = departingCount <= 1 ? 0 : Math.min(144,
                Math.max(1, (getWidth() - width - 24.0) / (departingCount - 1)));
        double x = (getWidth() - width - step * (departingCount - 1)) / 2 + departingIndex * step;
        departingFace.setSize(width, height);
        if (departingFace instanceof CardPanel movement)
            movement.setCardScale(Math.min(.76, (height - 12) / 244.0));
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.translate(x, 4 - 12 * departureProgress);
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive(1f - departureProgress));
        departingFace.paint(g);
        g.dispose();
    }

    private boolean isCardSelected(JComponent card) {
        if (card instanceof CardPanel cp) {
            return cp.isSelected();
        }
        if (card instanceof SkillCardPanel scp) {
            return scp.isSelected();
        }
        return false;
    }

    /** Updates the z-order layers so selected cards sit above unselected ones. */
    public void updateLayers() {
        for (int i = 0; i < cards.size(); i++) {
            JComponent card = cards.get(i);
            setLayer(card, isCardSelected(card) ? 80 : i);
        }
    }

    /** Replaces the hand only when card identities change, preserving normal hover state. */
    public void setCards(List<? extends JComponent> next) {
        if (reflowTimer != null) {
            reflowTimer.stop();
            reflowTimer = null;
        }
        List<? extends JComponent> oldCards = this.cards;
        startXMap.clear();
        for (JComponent c : oldCards) {
            if (c.getWidth() > 0) {
                startXMap.put(c, c.getX());
            }
        }
        removeAll();
        cards = List.copyOf(next);
        for (int i = 0; i < cards.size(); i++) {
            JComponent card = cards.get(i);
            final int layer = i;
            add(card, Integer.valueOf(isCardSelected(card) ? 80 : layer));
            Object previous = card.getClientProperty("handHoverListener");
            if (previous instanceof java.awt.event.MouseListener listener) card.removeMouseListener(listener);
            MouseAdapter hoverListener = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setLayer(card, 100);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setLayer(card, isCardSelected(card) ? 80 : layer);
                }
            };
            card.putClientProperty("handHoverListener", hoverListener);
            card.addMouseListener(hoverListener);
        }

        boolean shouldAnimate = !reducedMotion && isShowing() && !startXMap.isEmpty();
        if (shouldAnimate) {
            startReflowAnimation();
        } else {
            reflowProgress = 1f;
            revalidate();
            repaint();
        }
    }

    private void startReflowAnimation() {
        if (reflowTimer != null) {
            reflowTimer.stop();
        }
        long start = System.currentTimeMillis();
        reflowProgress = 0f;
        reflowTimer = new Timer(16, event -> {
            float p = Math.min(1f, (System.currentTimeMillis() - start) / 160f);
            reflowProgress = p;
            doLayout();
            repaint();
            if (p >= 1f) {
                ((Timer) event.getSource()).stop();
                reflowTimer = null;
                startXMap.clear();
            }
        });
        reflowTimer.start();
    }

    @Override
    public void doLayout() {
        if (cards.isEmpty()) return;
        int availableW = getWidth();
        int width = 176;
        if (cards.size() > 5 && availableW < 750) {
            width = Math.min(176, Math.max(130, (availableW - 30) / (cards.size() - 1) + 40));
        }
        int height = Math.min(210, Math.max(1, getHeight() - 16));
        double step = cards.size() == 1 ? 0
                : Math.min(144, Math.max(1, (availableW - width - 24.0) / (cards.size() - 1)));
        double span = width + step * (cards.size() - 1);
        double start = (availableW - span) / 2;
        double mid = (cards.size() - 1) / 2.0;
        for (int i = 0; i < cards.size(); i++) {
            int arc = (int) (8 * Math.pow((i - mid) / Math.max(1, mid), 2));
            JComponent card = cards.get(i);
            if (card instanceof CardPanel movement) {
                movement.setCardScale(Math.min(.76, (height - 12) / 244.0));
            }
            int targetX = (int) (start + i * step);
            int finalX = targetX;
            if (reflowProgress < 1f && startXMap.containsKey(card)) {
                int prevX = startXMap.get(card);
                double eased = 1 - (1 - reflowProgress) * (1 - reflowProgress);
                finalX = (int) (prevX + (targetX - prevX) * eased);
            }
            card.setBounds(finalX, 4 + arc, width, height);
        }
    }

    /** No overlapping child is opaque; the complete hand must repaint in layer order. */
    @Override public boolean isOptimizedDrawingEnabled() { return false; }
}
