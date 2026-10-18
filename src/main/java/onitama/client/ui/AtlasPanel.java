package onitama.client.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Local historical atlas: inspect seven factions without changing matches or borders. */
public final class AtlasPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(AtlasPanel.class.getName());
    private static final Color PAPER = new Color(0xEBD9AE), INK = new Color(0x392E24);
    private final MapCanvas map = new MapCanvas();
    private final JLabel country = label("", 30), status = label("", 11), seat = label("", 13);
    private final JTextArea description = new JTextArea();
    private final FlagCanvas flagCanvas = new FlagCanvas();
    private final JLabel flagTitle = label("Loading banner…", 11);
    private final JButton flagSource = button("Source & license", this::showFlagSource);
    private java.util.Map<String, AtlasFlag> flags = java.util.Map.of();
    private final JLabel zoomLabel = label("100%", 12);
    private final Runnable back;
    private AtlasRegion selected = AtlasRegion.ALL.get(0);
    private BufferedImage artwork;

    /** Builds an offline atlas and loads its bundled illustration away from the EDT. */
    public AtlasPanel(Runnable backAction) {
        back = backAction;
        setName("historicalAtlas");
        setLayout(new BorderLayout(0, 0)); setBackground(INK);
        add(header(), BorderLayout.NORTH);
        add(map, BorderLayout.CENTER);
        add(inspector(), BorderLayout.EAST);
        JLabel footer = label("ILLUSTRATED REFERENCE  ·  1444  ·  Borders and heraldry are indicative.", 11);
        footer.setForeground(PAPER); footer.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        add(footer, BorderLayout.SOUTH);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("BACK_SPACE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { back.run(); }
        });
        select(selected);
        new SwingWorker<AtlasArtwork, Void>() {
            @Override protected AtlasArtwork doInBackground() throws IOException {
                var resource = AtlasPanel.class.getResource("/assets/atlas/carpathian-1444-reference.png");
                if (resource == null) throw new IOException("Missing atlas illustration");
                BufferedImage image = ImageIO.read(resource);
                if (image == null) throw new IOException("Unreadable atlas illustration");
                java.util.Map<String, AtlasFlag> loadedFlags;
                try { loadedFlags = AtlasFlag.load(); }
                catch (IOException exception) {
                    LOG.log(Level.WARNING, "Could not load atlas banners", exception);
                    loadedFlags = java.util.Map.of();
                }
                return new AtlasArtwork(image, loadedFlags);
            }
            @Override protected void done() {
                try {
                    AtlasArtwork loaded = get(); artwork = loaded.map(); flags = loaded.flags();
                    select(selected); map.repaint();
                }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                catch (ExecutionException exception) {
                    LOG.log(Level.WARNING, "Could not load atlas", exception.getCause());
                    map.loadFailed = true; map.repaint();
                }
            }
        }.execute();
    }

    private JPanel header() {
        JPanel bar = new JPanel(new BorderLayout(20, 0)); bar.setBackground(INK);
        // The frame owns a floating music control in the upper-right corner.
        bar.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 82));
        JButton backButton = button("‹  Back", back); backButton.setName("atlasBack");
        bar.add(backButton, BorderLayout.WEST);
        JLabel title = label("ATLAS   /   CARPATHIAN BASIN   /   1444", 19); title.setForeground(PAPER);
        bar.add(title, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); controls.setOpaque(false);
        controls.add(button("−", () -> map.zoomAt(.8, map.getWidth() / 2., map.getHeight() / 2.)));
        zoomLabel.setForeground(PAPER); controls.add(zoomLabel);
        controls.add(button("+", () -> map.zoomAt(1.25, map.getWidth() / 2., map.getHeight() / 2.)));
        controls.add(button("Reset", () -> { map.zoom = 1; map.panX = map.panY = 0; map.updateZoom(); }));
        bar.add(controls, BorderLayout.EAST); return bar;
    }

    private JPanel inspector() {
        JPanel side = new JPanel(); side.setBackground(PAPER); side.setPreferredSize(new Dimension(310, 600));
        side.setBorder(BorderFactory.createEmptyBorder(20, 22, 16, 22));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(label("THE SEVEN FACTIONS", 12)); side.add(Box.createVerticalStrut(12));
        JPanel choices = new JPanel(new GridLayout(0, 2, 6, 6)); choices.setOpaque(false);
        for (AtlasRegion region : AtlasRegion.ALL) {
            JButton choice = button(region.name(), () -> select(region)); choice.setName("atlas" + region.name());
            choice.setFont(Theme.normal(12)); choice.setForeground(PAPER);
            choices.add(choice);
        }
        choices.setAlignmentX(LEFT_ALIGNMENT);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132)); side.add(choices);
        side.add(Box.createVerticalStrut(16)); flagCanvas.setAlignmentX(LEFT_ALIGNMENT);
        flagCanvas.setName("atlasFlag");
        flagCanvas.setPreferredSize(new Dimension(266, 130));
        flagCanvas.setMinimumSize(new Dimension(266, 130));
        flagCanvas.setMaximumSize(new Dimension(266, 130)); side.add(flagCanvas);
        flagTitle.setName("atlasFlagTitle");
        flagTitle.setPreferredSize(new Dimension(266, 34));
        flagTitle.setMaximumSize(new Dimension(266, 34)); side.add(flagTitle);
        flagSource.setAlignmentX(LEFT_ALIGNMENT); flagSource.setName("atlasFlagSource");
        flagSource.setPreferredSize(new Dimension(180, 32));
        flagSource.setMinimumSize(new Dimension(180, 32));
        flagSource.setMaximumSize(new Dimension(180, 32));
        side.add(flagSource);
        side.add(Box.createVerticalStrut(8)); country.setName("atlasCountry"); side.add(country);
        status.setMaximumSize(new Dimension(266, 40)); status.setVerticalAlignment(SwingConstants.TOP); side.add(status);
        side.add(Box.createVerticalStrut(10)); side.add(seat); side.add(Box.createVerticalStrut(12));
        description.setEditable(false); description.setFocusable(false); description.setLineWrap(true);
        description.setWrapStyleWord(true); description.setOpaque(false); description.setForeground(INK);
        description.setFont(Theme.normal(14)); description.setAlignmentX(LEFT_ALIGNMENT);
        JScrollPane scroll = new JScrollPane(description); scroll.setBorder(null); scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false); scroll.setAlignmentX(LEFT_ALIGNMENT);
        side.add(scroll); side.add(Box.createVerticalStrut(12));
        side.add(label("GAME IDENTITY", 11));
        JLabel pending = label("<html>Passive & cards: not assigned yet.<br>Historical borders stay fixed.</html>", 12);
        pending.setBorder(BorderFactory.createEmptyBorder(6, 0, 12, 0)); side.add(pending);
        side.add(label("Drag to explore · Scroll to zoom", 11)); return side;
    }

    private void select(AtlasRegion region) {
        selected = region; country.setText(region.name());
        status.setText("<html>" + region.status() + "</html>"); seat.setText(region.seat());
        description.setText(region.description()); description.setCaretPosition(0);
        AtlasFlag flag = flags.get(AtlasFlag.key(region));
        flagCanvas.flag = flag; flagCanvas.repaint();
        flagTitle.setText("<html>" + (flag == null ? "Banner unavailable" : flag.title()) + "</html>");
        flagTitle.setToolTipText(flag == null ? null : flag.context());
        flagSource.setEnabled(flag != null);
        revalidate(); map.repaint();
    }

    private void showFlagSource() {
        AtlasFlag flag = flags.get(AtlasFlag.key(selected));
        if (flag == null) return;
        JTextArea text = new JTextArea(flag.attribution(), 16, 52);
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setFont(Theme.normal(14)); text.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(text),
            selected.name() + " — banner source", JOptionPane.PLAIN_MESSAGE);
    }

    private record AtlasArtwork(BufferedImage map, java.util.Map<String, AtlasFlag> flags) { }

    private static final class FlagCanvas extends JComponent {
        private AtlasFlag flag;

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (flag == null) return;
            BufferedImage image = flag.image();
            double scale = Math.min((getWidth() - 12.) / image.getWidth(), (getHeight() - 12.) / image.getHeight());
            int width = (int) Math.round(image.getWidth() * scale), height = (int) Math.round(image.getHeight() * scale);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
            g.dispose();
        }
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text); label.setForeground(INK);
        label.setFont(size >= 19 ? new Font(Font.SERIF, Font.BOLD, size) : Theme.normal(size));
        label.setAlignmentX(LEFT_ALIGNMENT); return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(getModel().isRollover() ? new Color(0x594631) : INK);
                graphics.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false); button.setOpaque(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x8D7954)), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        button.setFont(Theme.normal(12));
        button.setPreferredSize(new Dimension(button.getFontMetrics(button.getFont()).stringWidth(text) + 40, 32));
        button.setBackground(INK); button.setForeground(PAPER); button.setFocusPainted(true);
        button.addActionListener(event -> action.run()); return button;
    }

    private final class MapCanvas extends JPanel {
        // Coordinates retain the supplied illustration's native system; the old legend is cropped away.
        private static final int WIDTH = 1255, HEIGHT = 1024;
        private double zoom = 1, panX, panY;
        private Point press, previous;
        private boolean dragged, loadFailed;
        private AtlasRegion hovered;

        MapCanvas() {
            setName("atlasMap"); setBackground(new Color(0x30291E));
            setToolTipText("Drag to explore; scroll to zoom; click a faction to inspect.");
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) { hover(event.getPoint()); }
                @Override public void mouseExited(MouseEvent event) { hovered = null; repaint(); }
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    press = previous = event.getPoint(); dragged = false;
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (previous == null) return;
                    if (press.distance(event.getPoint()) > 4) dragged = true;
                    if (dragged) {
                        panX += event.getX() - previous.x; panY += event.getY() - previous.y;
                        clampPan(); repaint();
                    }
                    previous = event.getPoint();
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (press != null && !dragged) {
                        AtlasRegion region = regionAt(event.getPoint()); if (region != null) select(region);
                    }
                    press = previous = null; hover(event.getPoint());
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    zoomAt(Math.pow(1.12, -event.getPreciseWheelRotation()), event.getX(), event.getY());
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) { clampPan(); repaint(); }
            });
        }

        private double scale() { return Math.max(.001, Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT)) * zoom; }
        private AffineTransform transform() {
            double scale = scale();
            AffineTransform transform = AffineTransform.getTranslateInstance(
                (getWidth() - WIDTH * scale) / 2 + panX, (getHeight() - HEIGHT * scale) / 2 + panY);
            transform.scale(scale, scale); return transform;
        }
        private AtlasRegion regionAt(Point point) {
            try {
                Point2D nativePoint = transform().inverseTransform(point, null);
                return AtlasRegion.at(nativePoint.getX(), nativePoint.getY());
            } catch (NoninvertibleTransformException exception) { return null; }
        }
        private void hover(Point point) {
            hovered = regionAt(point); setCursor(Cursor.getPredefinedCursor(hovered == null ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR));
            setToolTipText(hovered == null ? "Drag to explore · Scroll to zoom" : hovered.name() + " — click to inspect"); repaint();
        }
        private void zoomAt(double factor, double x, double y) {
            double oldScale = scale(), next = Math.max(1, Math.min(4, zoom * factor));
            double ratio = next / zoom;
            panX = (x - getWidth() / 2.) * (1 - ratio) + panX * ratio;
            panY = (y - getHeight() / 2.) * (1 - ratio) + panY * ratio;
            zoom = next;
            if (oldScale > 0) clampPan();
            updateZoom();
        }
        private void clampPan() {
            double maxX = Math.max(0, (WIDTH * scale() - getWidth()) / 2.);
            double maxY = Math.max(0, (HEIGHT * scale() - getHeight()) / 2.);
            panX = Math.max(-maxX, Math.min(maxX, panX)); panY = Math.max(-maxY, Math.min(maxY, panY));
        }
        private void updateZoom() { zoomLabel.setText(Math.round(zoom * 100) + "%"); repaint(); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (artwork == null) {
                g.setColor(PAPER); g.setFont(Theme.normal(16));
                g.drawString(loadFailed ? "Atlas unavailable. Use Back to return." : "Unfolding the atlas…", 30, 50);
            } else {
                g.transform(transform()); g.clipRect(0, 0, WIDTH, HEIGHT);
                g.drawImage(artwork, 0, 0, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT, null);
                highlight(g, selected, false);
                if (hovered != null && hovered != selected) highlight(g, hovered, true);
            }
            g.dispose();
        }
        private void highlight(Graphics2D g, AtlasRegion region, boolean hover) {
            g.setColor(new Color(255, 230, 150, hover ? 55 : 25)); g.fill(region.area());
            g.setColor(new Color(255, 234, 163, hover ? 230 : 170));
            g.setStroke(new BasicStroke((float) (2 / scale()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(region.area());
        }
    }
}
