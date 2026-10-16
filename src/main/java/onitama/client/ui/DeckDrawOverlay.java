package onitama.client.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Paint-only card flights above the play surface; never mutates the authoritative hand. */
public final class DeckDrawOverlay extends JComponent {
    /** Immutable flight description; endpoint suppliers return overlay-local rectangles on the EDT. */
    public record Flight(String key, BufferedImage back, BufferedImage face,
                         Supplier<Rectangle> source, Supplier<Rectangle> destination,
                         int delayMs, int durationMs) {
        public Flight {
            Objects.requireNonNull(key);
            Objects.requireNonNull(back);
            Objects.requireNonNull(source);
            Objects.requireNonNull(destination);
            if (delayMs < 0 || delayMs > 1000 || durationMs < 120 || durationMs > 2000)
                throw new IllegalArgumentException("Invalid visual timing");
        }

        /** Own skill or public movement: starts face down, reveals near the destination. */
        public static Flight reveal(String key, BufferedImage back, BufferedImage permittedFace,
                                    Supplier<Rectangle> source, Supplier<Rectangle> destination, int delayMs) {
            return new Flight(key, back, Objects.requireNonNull(permittedFace), source, destination, delayMs, 420);
        }

        /** Opponent skill: accepts only the shared back, never an identity or face image. */
        public static Flight concealed(String key, BufferedImage back,
                                       Supplier<Rectangle> source, Supplier<Rectangle> destination, int delayMs) {
            return new Flight(key, back, null, source, destination, delayMs, 420);
        }
    }

    private final LongSupplier clock;
    private final Timer timer;
    private List<Flight> flights = List.of();
    private long startedNanos;
    private boolean reducedMotion;

    /** Creates one coalescing Swing timer; all lifecycle methods must run on the EDT. */
    public DeckDrawOverlay() { this(System::nanoTime); }

    // Clock injection lets checks advance to exact visual frames without sleeps.
    DeckDrawOverlay(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
        setOpaque(false);
        setFocusable(false);
        timer = new Timer(16, event -> tick());
        timer.setCoalesce(true);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { cancel(); }
        });
    }

    /** Replaces the bounded batch. Invalid/unlaid-out endpoints snap to the real hand instead. */
    public void play(List<Flight> next) {
        requireEdt();
        Objects.requireNonNull(next);
        if (next.size() > 8) throw new IllegalArgumentException("At most eight visual flights per batch");
        var keys = new HashSet<String>();
        for (Flight flight : next) {
            Objects.requireNonNull(flight);
            if (!keys.add(flight.key())) throw new IllegalArgumentException("Duplicate target key");
        }
        cancel();
        if (reducedMotion || next.isEmpty()) return;
        if (next.stream().anyMatch(f -> !valid(f.source().get()) || !valid(f.destination().get()))) return;
        flights = List.copyOf(next);
        startedNanos = clock.getAsLong();
        timer.start();
        repaintScene();
    }

    /** Allows painting the real target card only after its flight ends; does not affect input/state. */
    public boolean isFlyingTo(String key) {
        requireEdt();
        return flights.stream().anyMatch(f -> f.key().equals(key));
    }

    /** Binds a paint-only mask; the live card keeps its bounds and event handlers. */
    public void bind(JComponent card, String key) {
        card.putClientProperty("deckFlightMask", (java.util.function.BooleanSupplier) () -> isFlyingTo(key));
    }

    /** Used by card painters to avoid drawing the destination twice during a flight. */
    public static boolean isMasked(JComponent card) {
        Object mask = card.getClientProperty("deckFlightMask");
        return mask instanceof java.util.function.BooleanSupplier supplier && supplier.getAsBoolean();
    }

    /** Applies immediately; turning motion back on never replays an old batch. */
    public void setReducedMotion(boolean value) {
        requireEdt();
        reducedMotion = value;
        if (value) cancel();
    }

    /** Drops every flight and timer. No completion callback can submit actions or survive reset. */
    public void cancel() {
        requireEdt();
        timer.stop();
        flights = List.of();
        repaintScene();
    }

    @Override public void removeNotify() {
        cancel();
        super.removeNotify();
    }

    /** Mouse hit testing passes through to cards, the board and buttons underneath. */
    @Override public boolean contains(int x, int y) { return false; }

    void tick() {
        requireEdt();
        if (flights.stream().anyMatch(f -> !valid(f.source().get()) || !valid(f.destination().get()))) {
            cancel();
            return;
        }
        double elapsed = elapsedMs();
        flights = flights.stream().filter(f -> elapsed < f.delayMs() + f.durationMs()).toList();
        if (flights.isEmpty()) timer.stop();
        repaintScene();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double elapsed = elapsedMs();
            for (Flight flight : flights) {
                double p = (elapsed - flight.delayMs()) / flight.durationMs();
                if (p < 0 || p >= 1) continue;
                Rectangle from = flight.source().get();
                Rectangle to = flight.destination().get();
                if (valid(from) && valid(to)) paintFlight(g, flight, from, to, p);
            }
        } finally { g.dispose(); }
    }

    private void paintFlight(Graphics2D original, Flight flight, Rectangle from, Rectangle to, double p) {
        double eased = 1 - Math.pow(1 - p, 3);
        double distance = Math.hypot(to.getCenterX() - from.getCenterX(), to.getCenterY() - from.getCenterY());
        double arc = Math.min(56, distance * .12) * Math.sin(Math.PI * p);
        double x = mix(from.getCenterX(), to.getCenterX(), eased);
        double y = mix(from.getCenterY(), to.getCenterY(), eased) - arc;
        int width = Math.max(1, (int) Math.round(mix(from.width, to.width, eased)));
        int height = Math.max(1, (int) Math.round(mix(from.height, to.height, eased)));
        double flip = flight.face() == null ? 0 : Math.max(0, Math.min(1, (p - .60) / .28));
        double squeeze = flight.face() == null ? 1 : Math.max(.025, Math.abs(Math.cos(Math.PI * flip)));
        BufferedImage picture = flip >= .5 && flight.face() != null ? flight.face() : flight.back();
        Graphics2D g = (Graphics2D) original.create();
        try {
            g.translate(x, y);
            g.rotate(Math.toRadians(-7 * Math.sin(Math.PI * p)));
            g.scale(squeeze, 1);
            g.setComposite(AlphaComposite.SrcOver.derive(.24f));
            g.setColor(Color.BLACK);
            g.fillRoundRect(-width / 2 + 4, -height / 2 + 6, width, height, 10, 10);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(picture, -width / 2, -height / 2, width, height, null);
        } finally { g.dispose(); }
    }

    private double elapsedMs() { return (clock.getAsLong() - startedNanos) / 1_000_000.0; }
    private static double mix(double a, double b, double amount) { return a + (b - a) * amount; }
    private static boolean valid(Rectangle r) { return r != null && r.width > 0 && r.height > 0; }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Use the Swing EDT");
    }
    private void repaintScene() {
        if (getParent() != null) getParent().repaint();
        else repaint();
    }

    /** Re-evaluates coordinates as hand layout changes; detached or hidden targets yield null. */
    public static Supplier<Rectangle> boundsOf(JComponent component, DeckDrawOverlay overlay) {
        return () -> {
            requireEdt();
            if (component.getParent() == null || overlay.getParent() == null || !component.isVisible()) return null;
            if (!SwingUtilities.isDescendingFrom(component, overlay.getParent())) return null;
            for (java.awt.Component parent = component; parent != overlay.getParent(); parent = parent.getParent())
                if (!parent.isVisible()) return null;
            return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), overlay);
        };
    }

    /** Captures an already laid-out, permitted face once; never call inside a frame paint loop. */
    public static BufferedImage snapshot(JComponent component) {
        requireEdt();
        if (component.getWidth() <= 0 || component.getHeight() <= 0)
            throw new IllegalArgumentException("Layout the card before taking its snapshot");
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    /** Wraps the whole play surface so a flight can cross from a deck to either hand. */
    public static JLayeredPane wrap(JComponent scene, JComponent overlay) {
        requireEdt();
        var layers = new JLayeredPane() {
            @Override public void doLayout() {
                scene.setBounds(0, 0, getWidth(), getHeight());
                overlay.setBounds(0, 0, getWidth(), getHeight());
                scene.doLayout();
            }
            @Override public Dimension getPreferredSize() { return scene.getPreferredSize(); }
            @Override public Dimension getMinimumSize() { return scene.getMinimumSize(); }
            @Override public boolean isOptimizedDrawingEnabled() { return false; }
        };
        layers.add(scene, JLayeredPane.DEFAULT_LAYER);
        layers.add(overlay, JLayeredPane.DRAG_LAYER);
        return layers;
    }
}
