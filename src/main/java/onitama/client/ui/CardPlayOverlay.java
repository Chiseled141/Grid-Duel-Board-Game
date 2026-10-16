package onitama.client.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** EDT-owned drag, raised-card, return and accepted-play visuals above the complete match. */
final class CardPlayOverlay extends JComponent {
    enum State { IDLE, DRAGGING, CARD_SELECTED, RETURNING, PLAYING }
    static final int THRESHOLD = 38;
    private final Runnable stateChanged;
    private final javax.swing.Timer timer = new javax.swing.Timer(16, event -> tick());
    private final java.util.function.LongSupplier clock;
    private final ArrayDeque<Play> plays = new ArrayDeque<>();
    private Lift lift;
    private long started;
    private boolean reducedMotion;
    private State state = State.IDLE;

    CardPlayOverlay(Runnable stateChanged) { this(stateChanged, System::nanoTime); }
    CardPlayOverlay(Runnable stateChanged, java.util.function.LongSupplier clock) {
        this.stateChanged = stateChanged; this.clock = clock;
        setOpaque(false); setFocusable(false); timer.setCoalesce(true);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    State state() { return state; }
    boolean busy() { return !plays.isEmpty(); }
    static boolean isMasked(JComponent card) { return Boolean.TRUE.equals(card.getClientProperty("handDrawMask")); }

    void bind(JComponent card, String key, BooleanSupplier allowed, Runnable select) {
        bind(card, key, () -> false, allowed, select);
    }

    void bind(JComponent card, String key, BooleanSupplier clickToSelect, BooleanSupplier allowed, Runnable select) {
        card.putClientProperty("handDrawKey", key);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        var listener = new MouseAdapter() {
            private Point pressed;
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || busy() || !allowed.getAsBoolean()
                        || selected(card) || DeckDrawOverlay.isMasked(card)) return;
                if (lift != null && selected(lift.card) && !clickToSelect.getAsBoolean()) return;
                if (lift != null) finishLift();
                Rectangle slot = bounds(card);
                if (slot == null) return;
                pressed = SwingUtilities.convertPoint(card, event.getPoint(), CardPlayOverlay.this);
                lift = new Lift(card, key, snapshot(card), slot, new Rectangle(slot));
                state = State.DRAGGING;
                card.putClientProperty("handDrawMask", true);
                repaintScene();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (pressed == null || lift == null || lift.card != card || state != State.DRAGGING) return;
                Point point = SwingUtilities.convertPoint(card, event.getPoint(), CardPlayOverlay.this);
                lift.pull = Math.max(0, pressed.y - point.y);
                lift.position.x = Math.max(0, Math.min(getWidth() - lift.slot.width, lift.slot.x + point.x - pressed.x));
                // The hand reserves space above its cards, so drawing never covers board squares.
                lift.position.y = lift.slot.y - Math.min(56, lift.pull);
                repaintScene();
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || pressed == null) return;
                Point released = SwingUtilities.convertPoint(card, event.getPoint(), CardPlayOverlay.this);
                boolean click = clickToSelect.getAsBoolean() && pressed.distance(released) <= 6
                        && card.contains(event.getPoint());
                pressed = null;
                if (lift == null || lift.card != card || state != State.DRAGGING) return;
                if ((lift.pull >= THRESHOLD || click) && allowed.getAsBoolean()) {
                    lift.position = new Rectangle(lift.slot.x, lift.slot.y - 52, lift.slot.width, lift.slot.height);
                    state = State.CARD_SELECTED;
                    select.run();
                    if (!selected(card)) returnLift();
                } else returnLift();
                repaintScene();
            }
        };
        card.addMouseListener(listener); card.addMouseMotionListener(listener);
    }

    void syncSelection(List<? extends JComponent> cards, boolean pending) {
        if (busy() || state == State.DRAGGING) return;
        JComponent selected = cards.stream().filter(CardPlayOverlay::selected).findFirst().orElse(null);
        if (selected == null) {
            if (lift != null && state == State.CARD_SELECTED && !pending) returnLift();
        } else if (lift == null || lift.card != selected) {
            finishLift();
            Rectangle slot = bounds(selected);
            if (slot == null) return;
            lift = new Lift(selected, String.valueOf(selected.getClientProperty("handDrawKey")), snapshot(selected),
                    slot, new Rectangle(slot.x, slot.y - 52, slot.width, slot.height));
            selected.putClientProperty("handDrawMask", true);
            state = State.CARD_SELECTED;
        } else if (state == State.RETURNING) {
            state = State.CARD_SELECTED; timer.stop();
        }
    }

    private static boolean selected(JComponent card) {
        return card instanceof CardPanel c ? c.isSelected() : card instanceof SkillCardPanel s && s.isSelected();
    }

    private BufferedImage snapshot(JComponent card) {
        Object mask = card.getClientProperty("handDrawMask");
        card.putClientProperty("handDrawMask", false);
        try { return DeckDrawOverlay.snapshot(card); }
        finally { card.putClientProperty("handDrawMask", mask); }
    }

    private Rectangle bounds(JComponent card) {
        if (card.getParent() == null || card.getWidth() <= 0 || getParent() == null) return null;
        return SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), this);
    }

    private void returnLift() {
        if (lift == null) return;
        if (reducedMotion) { finishLift(); stateChanged.run(); return; }
        lift.returnFrom = new Rectangle(lift.position);
        started = clock.getAsLong(); state = State.RETURNING; timer.start();
    }

    void returnSelection() { returnLift(); }

    void play(String key, BufferedImage face, BufferedImage concealedStart, Rectangle source, PlayedCardArea area) {
        if (lift != null && key.equals(lift.key)) source = new Rectangle(lift.position);
        finishLift();
        if (source == null || face == null) { area.showFace(face); return; }
        if (reducedMotion) { area.showFace(face); area.arriving(false); return; }
        if (plays.size() >= 8) { cancel(); }
        plays.add(new Play(face, concealedStart, source, area));
        if (plays.size() == 1) {
            area.showFace(face); area.arriving(true);
            started = clock.getAsLong(); state = State.PLAYING;
        }
        timer.start(); stateChanged.run(); repaintScene();
    }

    void setReducedMotion(boolean value) {
        reducedMotion = value;
        if (value && busy()) { for (Play play : plays) { play.area.showFace(play.face); play.area.arriving(false); } plays.clear(); }
        if (value && state == State.RETURNING) finishLift();
        if (value) { timer.stop(); if (state == State.PLAYING) state = State.IDLE; }
    }

    void cancel() {
        timer.stop(); finishLift();
        for (Play play : plays) { play.area.showFace(play.face); play.area.arriving(false); }
        plays.clear(); state = State.IDLE; repaintScene();
    }

    private void finishLift() {
        if (lift != null) lift.card.putClientProperty("handDrawMask", false);
        lift = null;
        if (!busy()) { state = State.IDLE; timer.stop(); }
        repaintScene();
    }

    void tick() {
        double elapsed = (clock.getAsLong() - started) / 1_000_000.0;
        if (state == State.RETURNING && lift != null) {
            double p = Math.min(1, elapsed / 200), eased = 1 - Math.pow(1 - p, 3);
            lift.position = interpolate(lift.returnFrom, lift.slot, eased);
            if (p >= 1) { finishLift(); stateChanged.run(); }
        } else if (busy() && elapsed >= 540) {
            Play completed = plays.remove();
            completed.area.showFace(completed.face); completed.area.arriving(false);
            if (!plays.isEmpty()) {
                Play next = plays.peek();
                next.area.showFace(next.face); next.area.arriving(true);
            }
            started = clock.getAsLong();
            if (plays.isEmpty()) { timer.stop(); state = State.IDLE; stateChanged.run(); }
        }
        repaintScene();
    }

    @Override public boolean contains(int x, int y) { return busy(); }
    @Override public void removeNotify() { cancel(); super.removeNotify(); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = UiKit.nice(graphics);
        try {
            if (lift != null) paintCard(g, lift.face, lift.position, state == State.CARD_SELECTED);
            if (busy()) {
                Play play = plays.peek();
                Rectangle target = SwingUtilities.convertRectangle(play.area, play.area.cardBounds(), this);
                double p = Math.max(0, Math.min(1, (clock.getAsLong() - started) / 540_000_000.0));
                Rectangle position = interpolate(play.source, target, 1 - Math.pow(1 - p, 3));
                BufferedImage face = play.back != null && p < .4 ? play.back : play.face;
                paintCard(g, face, position, false);
            }
        } finally { g.dispose(); }
    }

    private void paintCard(Graphics2D g, BufferedImage image, Rectangle bounds, boolean selected) {
        g.setColor(new Color(0, 0, 0, 100));
        g.fillRoundRect(bounds.x + 5, bounds.y + 8, bounds.width, bounds.height, 10, 10);
        g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
        if (selected) {
            g.setColor(MatchStyle.GOLD); g.setStroke(new BasicStroke(2));
            g.drawRoundRect(bounds.x + 3, bounds.y + 3, bounds.width - 6, bounds.height - 6, 9, 9);
        }
    }

    private static Rectangle interpolate(Rectangle from, Rectangle to, double p) {
        return new Rectangle((int) (from.x + (to.x - from.x) * p), (int) (from.y + (to.y - from.y) * p),
                (int) (from.width + (to.width - from.width) * p), (int) (from.height + (to.height - from.height) * p));
    }
    private void repaintScene() { if (getParent() != null) getParent().repaint(); else repaint(); }
    private static final class Lift {
        final JComponent card; final String key; final BufferedImage face; final Rectangle slot;
        Rectangle position, returnFrom; int pull;
        Lift(JComponent card, String key, BufferedImage face, Rectangle slot, Rectangle position) {
            this.card = card; this.key = key; this.face = face; this.slot = slot; this.position = position;
        }
    }
    private record Play(BufferedImage face, BufferedImage back, Rectangle source, PlayedCardArea area) { }
}
