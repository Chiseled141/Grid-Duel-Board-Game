package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * The How to Play guide. The quick guide is a short list of steps, each a one-line tip over a
 * clip recorded from a real match ({@link GuideSteps}). Reads only bundled files and sends nothing to the server. Reduced motion shows each clip's first frame.
 */
final class HowToPlayPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(HowToPlayPanel.class.getName());
    private final ClientModel model;
    private final List<StepTile> steps = new ArrayList<>();
    private final JLabel eyebrow = new JLabel(), heading = new JLabel(), tip = new JLabel();
    private final StepDots dots = new StepDots();
    private final ClipView clip = new ClipView();
    private final JButton previous = UiKit.pill("\u2039 Previous", UiKit.Pill.GOLD_OUTLINE);
    private final JButton next = UiKit.pill("Next \u203a", UiKit.Pill.GOLD);
    private int index, seen;

    HowToPlayPanel(ClientModel model) {
        this.model = model;
        setName("howToPlay"); setLayout(new BorderLayout(28, 18)); setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 22, 24));
        getAccessibleContext().setAccessibleName("How to Play");
        add(header(), BorderLayout.NORTH);
        add(stepList(), BorderLayout.WEST);
        add(stage(), BorderLayout.CENTER);
        LobbyPanel.bindBack(this, model::leaveToLobby);
        bind("LEFT", "guide.previous", () -> previous.doClick(0));
        bind("RIGHT", "guide.next", () -> next.doClick(0));
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (screen == Screen.HOW_TO_PLAY) show(index); else clip.show(null, false);
            }
        });
        show(0);
    }

    /** The castle hall behind a dark veil, darker at the edges, so the plates and text stay readable. */
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = UiKit.nice(graphics);
        try {
            BattlefieldMenuPanel.paintCover(g, AssetStore.optional("loading-castle-hall.png"), getWidth(), getHeight(), this);
            g.setColor(new Color(12, 10, 9, 190));
            g.fillRect(0, 0, getWidth(), getHeight());
            float radius = Math.max(getWidth(), getHeight()) * .75f;
            g.setPaint(new RadialGradientPaint(getWidth() / 2f, getHeight() / 2f, radius, new float[] {.4f, 1f},
                    new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
            g.fillRect(0, 0, getWidth(), getHeight());
        } finally { g.dispose(); }
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout(18, 0)); header.setOpaque(false);
        JButton back = LobbyPanel.smallButton("Back", model::leaveToLobby); back.setName("guideBack");
        JLabel title = new JLabel("How to Play"); title.setFont(Theme.display(28)); title.setForeground(Theme.BRASS_LIGHT);
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); west.setOpaque(false); west.add(back);
        header.add(west, BorderLayout.WEST); header.add(title, BorderLayout.CENTER);
        // The frame's music button remains in the top-right corner above this page.
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 68));
        return header;
    }

    /** An iron plate down the left with one numbered tile per step, so any step is one click away. */
    private JPanel stepList() {
        JPanel plate = UiKit.plate(18);
        plate.setLayout(new BorderLayout(0, 14));
        JLabel caption = new JLabel("QUICK GUIDE"); caption.setFont(Theme.bold(13)); caption.setForeground(Theme.AMBER);
        plate.add(caption, BorderLayout.NORTH);
        JPanel list = new JPanel(); list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (int i = 0; i < GuideSteps.STEPS.size(); i++) {
            int at = i;
            StepTile step = new StepTile(i + 1, GuideSteps.STEPS.get(i).title());
            step.setName("guideStep" + i);
            step.addActionListener(event -> show(at));
            steps.add(step);
            list.add(step); list.add(Box.createVerticalStrut(8));
        }
        plate.add(list, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>Arrow keys change the step.<br>Esc returns to the menu.</html>");
        hint.setFont(Theme.normal(14)); hint.setForeground(Theme.FADED);
        plate.add(hint, BorderLayout.SOUTH);
        plate.setPreferredSize(new Dimension(300, 0));
        return plate;
    }

    private JPanel stage() {
        JPanel stage = new JPanel(new BorderLayout(0, 16)); stage.setOpaque(false);
        eyebrow.setName("guideEyebrow"); eyebrow.setFont(Theme.bold(14)); eyebrow.setForeground(Theme.AMBER);
        heading.setName("guideHeading"); heading.setFont(Theme.display(34)); heading.setForeground(Theme.BRASS_LIGHT);
        tip.setName("guideTip"); tip.setFont(Theme.normal(21)); tip.setForeground(Theme.CREAM);
        for (JLabel label : new JLabel[] {eyebrow, heading, tip}) label.putClientProperty("html.disable", Boolean.TRUE);
        JPanel words = new JPanel(); words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(eyebrow); words.add(Box.createVerticalStrut(2)); words.add(heading); words.add(Box.createVerticalStrut(4)); words.add(tip);
        stage.add(words, BorderLayout.NORTH);
        clip.setName("guideClip");
        stage.add(clip, BorderLayout.CENTER);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 0)); nav.setOpaque(false);
        previous.setName("guideStepPrevious"); next.setName("guideStepNext");
        dots.setName("guideCount");
        previous.addActionListener(event -> show(index - 1));
        next.addActionListener(event -> show(index + 1));
        nav.add(previous); nav.add(dots); nav.add(next);
        stage.add(nav, BorderLayout.SOUTH);
        return stage;
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { if (isShowing()) action.run(); }
        });
    }

    /** Shows step {@code at}: its title, tip and clip, and which tiles are current or already seen. */
    private void show(int at) {
        index = Math.max(0, Math.min(GuideSteps.STEPS.size() - 1, at));
        seen = Math.max(seen, index);
        GuideSteps.Step step = GuideSteps.STEPS.get(index);
        eyebrow.setText("STEP " + (index + 1) + " OF " + GuideSteps.STEPS.size());
        heading.setText(step.title());
        tip.setText(step.text());
        dots.show(index, GuideSteps.STEPS.size());
        previous.setEnabled(index > 0);
        next.setEnabled(index < GuideSteps.STEPS.size() - 1);
        for (int i = 0; i < steps.size(); i++) steps.get(i).mark(i == index, i <= seen);
        if (model.screen() == Screen.HOW_TO_PLAY) clip.show(step.resource(), !model.reducedMotion());
        repaint();
    }

    /** The current step's title; for tests. */
    String currentTitle() { return heading.getText(); }

    /**
     * Draws a bundled clip on an iron plate, shrunk to fit but never enlarged beyond one clip pixel
     * per screen pixel (the clips are recorded at 2x for HiDPI); a still frame when not animating.
     */
    private static final class ClipView extends JComponent {
        private Image image;

        void show(String resource, boolean animate) {
            image = null;
            var url = resource == null ? null : HowToPlayPanel.class.getResource(resource);
            if (url != null) {
                if (animate) image = new ImageIcon(url).getImage();
                else try {
                    BufferedImage still = ImageIO.read(url);
                    image = still;
                } catch (IOException unreadable) {
                    LOG.log(Level.WARNING, "could not read guide clip " + resource, unreadable);
                }
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                UiKit.paintPlate(g, getWidth() - 4, getHeight() - 4);
                if (image == null) return;
                int iw = image.getWidth(this), ih = image.getHeight(this);
                if (iw <= 0 || ih <= 0) return;
                int pad = 18;
                double fit = Math.min((getWidth() - 4 - 2 * pad) / (double) iw, (getHeight() - 4 - 2 * pad) / (double) ih);
                double scale = Math.min(fit, 1 / g.getTransform().getScaleX());
                int w = (int) (iw * scale), h = (int) (ih * scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, (getWidth() - 4 - w) / 2, (getHeight() - 4 - h) / 2, w, h, this);
            } finally { g.dispose(); }
        }
    }

    /**
     * One step in the side list: a numbered brass medallion and the step's title on an iron tile.
     * The current step is a gold tile; steps already seen keep a filled medallion.
     */
    private static final class StepTile extends JButton {
        private final int number;
        private boolean current, visited;

        StepTile(int number, String title) {
            super(title);
            this.number = number;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.bold(16));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            setPreferredSize(new Dimension(260, 52));
            getAccessibleContext().setAccessibleName("Step " + number + ": " + title);
        }

        void mark(boolean current, boolean visited) {
            this.current = current; this.visited = visited;
            setSelected(current);
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                boolean lit = getModel().isRollover() || isFocusOwner();
                UiKit.paintTile(g, getWidth(), getHeight(), current, lit && !current);
                int size = 30, x = 12, y = (getHeight() - size) / 2;
                g.setColor(current ? Theme.INK : visited ? Theme.BRASS : Theme.IRON_DARK);
                g.fillOval(x, y, size, size);
                g.setStroke(new BasicStroke(1.5f));
                g.setColor(current ? Theme.withAlpha(Theme.CREAM, 200) : Theme.BRASS_LIGHT);
                g.drawOval(x, y, size, size);
                g.setFont(Theme.bold(15));
                String digit = String.valueOf(number);
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(current ? Theme.BRASS_LIGHT : visited ? Theme.INK : Theme.BRASS_LIGHT);
                g.drawString(digit, x + (size - metrics.stringWidth(digit)) / 2, y + (size + metrics.getAscent() - metrics.getDescent()) / 2);
                g.setFont(getFont());
                metrics = g.getFontMetrics();
                g.setColor(current ? Theme.INK : lit ? Theme.CREAM : Theme.MUTED);
                g.drawString(getText(), x + size + 14, (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
            } finally { g.dispose(); }
        }
    }

    /** One dot per step between Previous and Next; the current one is a wider brass bar. */
    private static final class StepDots extends JLabel {
        private int current, total;

        StepDots() { setPreferredSize(new Dimension(160, 20)); }

        void show(int current, int total) {
            this.current = current; this.total = total;
            getAccessibleContext().setAccessibleName("Step " + (current + 1) + " of " + total);
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                int dot = 8, wide = 26, gap = 9;
                int width = (total - 1) * (dot + gap) + wide;
                int x = (getWidth() - width) / 2, y = (getHeight() - dot) / 2;
                for (int i = 0; i < total; i++) {
                    int w = i == current ? wide : dot;
                    g.setColor(i == current ? Theme.BRASS_LIGHT : i < current ? Theme.BRASS : Theme.withAlpha(Theme.FADED, 90));
                    g.fillRoundRect(x, y, w, dot, dot, dot);
                    x += w + gap;
                }
            } finally { g.dispose(); }
        }
    }
}
