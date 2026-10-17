package onitama.client.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import onitama.client.ClientSettings;
import onitama.core.PlayerColor;
import onitama.core.duel.RulesConfig;

/** Match-entry artwork, real resource-loading progress and rotating live-rule tips. */
public final class LoadingPanel extends JPanel implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(LoadingPanel.class.getName());
    static final List<String> BACKGROUNDS = List.of("loading/castle-passage.png",
            "loading/campaign-map.png", "loading/fortress-courtyard.png");
    private final ClientSettings settings;
    private final Runnable onEnter;
    private final RulesConfig rules;
    private final Image[] images = new Image[3];
    private final JPanel column = new JPanel();
    private final JLabel hint = new HintLabel();
    private final JLabel status = new JLabel("Preparing the game…");
    private final JLabel percentage = new JLabel("0%");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton enter = UiKit.pill("Enter Match", UiKit.Pill.GOLD);
    private final Timer scenes = new Timer(9000, event -> nextBackground());
    private final Timer hints = new Timer(7000, event -> nextHint());
    private List<String> tips = List.of();
    private int backgroundIndex, tipIndex;
    private boolean ready, entered, closed, settled;
    private long generation;
    private SwingWorker<Void, Update> worker;
    private record Step(String title, Runnable action) { }
    private record Update(int percent, String title, int background, Image image, List<String> tips) { }

    /** Starts preloading off the EDT; entry remains unavailable until all steps finish. */
    public LoadingPanel(ClientSettings settings, RulesConfig rules, Runnable onEnter) {
        this(settings, rules, onEnter, java.util.concurrent.ThreadLocalRandom.current().nextInt(BACKGROUNDS.size()));
    }

    LoadingPanel(ClientSettings settings, RulesConfig rules, Runnable onEnter, int firstBackground) {
        if (firstBackground < 0 || firstBackground >= BACKGROUNDS.size()) throw new IllegalArgumentException("invalid scene");
        backgroundIndex = firstBackground;
        this.settings = settings; this.onEnter = onEnter; this.rules = java.util.Objects.requireNonNull(rules);
        setName("matchLoading"); setLayout(new BorderLayout()); setBackground(new Color(35, 31, 24));
        column.setOpaque(false); column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        hint.setName("loadingHint");
        hint.setForeground(Theme.CREAM); hint.setFont(Theme.normal(16));
        hint.setAlignmentX(.5f); hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        hint.setPreferredSize(new Dimension(760, 70));
        JLabel caption = new JLabel("FIELD NOTES", SwingConstants.CENTER);
        caption.setForeground(new Color(218, 187, 125)); caption.setFont(Theme.bold(10));
        caption.setAlignmentX(.5f);
        column.add(caption); column.add(Box.createVerticalStrut(7)); column.add(hint);
        column.add(Box.createVerticalStrut(14));
        progress.setName("matchProgress"); progress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
        progress.setBorderPainted(false); progress.setForeground(new Color(207, 167, 86));
        progress.setBackground(new Color(225, 207, 165, 42));
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        progress.setPreferredSize(new Dimension(760, 5)); column.add(progress);
        JPanel labels = new JPanel(new BorderLayout()); labels.setOpaque(false);
        labels.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        for (JLabel label : List.of(status, percentage)) {
            label.setForeground(new Color(229, 216, 185)); label.setFont(Theme.normal(12));
        }
        labels.add(status, BorderLayout.WEST); labels.add(percentage, BorderLayout.EAST);
        column.add(labels); column.add(Box.createVerticalStrut(12));
        JPanel buttonSlot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); buttonSlot.setOpaque(false);
        buttonSlot.setPreferredSize(new Dimension(260, 48));
        buttonSlot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        enter.setName("enterGame"); enter.setVisible(false); enter.setEnabled(false);
        enter.addActionListener(event -> enterGame()); buttonSlot.add(enter); column.add(buttonSlot);
        JPanel footer = new JPanel(new GridBagLayout()); footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(20, 32, 30, 32)); footer.add(column);
        add(footer, BorderLayout.SOUTH);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) updateTimers();
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "enterGame");
        getActionMap().put("enterGame", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { enterGame(); }
        });
        startLoading();
    }

    private void startLoading() {
        final long run = ++generation; settled = false;
        ready = false; enter.setVisible(false); enter.setEnabled(false);
        progress.setValue(0); percentage.setText("0%"); status.setText("Preparing the game…");
        worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                publish(new Update(0, "Preparing scenery…", -1, null, tips(rules)));
                List<Step> steps = new ArrayList<>();
                int first = backgroundIndex;
                for (int index = 0; index < BACKGROUNDS.size(); index++) {
                    final int slot = (first + index) % BACKGROUNDS.size();
                    steps.add(new Step("Preparing scenery…", () -> {
                        Image image = AssetStore.optional(BACKGROUNDS.get(slot));
                        publish(new Update(-1, "", slot, image, List.of()));
                    }));
                }
                steps.add(new Step("Preparing the board…", () -> GamePanel.preloadBackground().join()));
                for (var card : rules.movementCards()) steps.add(new Step("Preparing movement cards…", () -> {
                    for (var owner : PlayerColor.values()) for (var viewer : PlayerColor.values())
                        CardArt.movementFace(card, owner, viewer);
                }));
                for (var skill : rules.skills()) steps.add(new Step("Preparing skill cards…", () -> CardArt.skillFace(skill)));
                for (var color : PlayerColor.values()) for (int style = 1; style <= 3; style++) {
                    final int selectedStyle = style;
                    steps.add(new Step("Preparing playing pieces…", () -> MedievalPieces.image(color, selectedStyle)));
                }
                steps.add(new Step("Finishing preparations…", CardArt::backFace));
                int completed = 0;
                for (Step step : steps) {
                    if (isCancelled()) return null;
                    step.action().run();
                    publish(new Update(++completed * 100 / steps.size(), step.title(), -1, null, List.of()));
                }
                return null;
            }
            @Override protected void process(List<Update> updates) {
                if (closed || run != generation) return;
                for (Update update : updates) {
                    if (update.background() >= 0) images[update.background()] = update.image();
                    if (!update.tips().isEmpty()) { tips = update.tips(); updateHint(); }
                    if (!settled && update.percent() >= 0) {
                        progress.setValue(update.percent()); percentage.setText(update.percent() + "%");
                        status.setText(update.title());
                    }
                }
                repaint();
            }
            @Override protected void done() {
                if (closed || isCancelled() || run != generation) return;
                settled = true;
                try {
                    get(); ready = true; progress.setValue(100); percentage.setText("100%");
                    status.setText("Ready when you are"); enter.setText("ENTER MATCH");
                } catch (CancellationException exception) { return;
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return;
                } catch (ExecutionException exception) {
                    LOG.log(Level.WARNING, "Match preparation failed", exception.getCause());
                    status.setText("Unable to finish loading. Please retry."); enter.setText("RETRY");
                }
                enter.setVisible(true); enter.setEnabled(true); revalidate(); repaint();
            }
        };
        worker.execute();
    }

    private void enterGame() {
        if (closed || entered || !enter.isEnabled()) return;
        if (!ready) { startLoading(); return; }
        entered = true; enter.setEnabled(false); onEnter.run();
    }

    static List<String> tips(RulesConfig rules) {
        return List.of("Every enemy eliminated earns 1 point. Reach " + rules.scoreToWin() + " points or eliminate every enemy to win.",
                "Gold squares award +1 point at your turn’s start. Hold them through your opponent’s turn.",
                "Blue squares give +1 bonus mana at your turn’s start, up to " + rules.manaCap() + " mana in total.",
                "Movement cards jump over pieces. Capture an enemy by landing on its square.",
                "You can play one skill per turn. Successfully used skills are spent permanently.",
                "Both movement hands are public. Inspect your opponent’s cards before choosing a destination.",
                "Skipping movement keeps your movement cards. You can still play a skill afterward.",
                "Bonus squares are mirrored for both players. Contest them to stop your opponent earning rewards.");
    }

    private void updateHint() {
        if (tips.isEmpty()) return;
        hint.setText(tips.get(tipIndex));
    }

    /** Draws centered lines using actual font metrics, independent of Swing HTML scaling. */
    private static final class HintLabel extends JLabel {
        HintLabel() { super("Preparing your next battle…"); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont()); g.setColor(getForeground());
            List<String> lines = wrapHint(getText(), g.getFontMetrics(), Math.max(1, getWidth() - 24));
            FontMetrics metrics = g.getFontMetrics();
            int y = (getHeight() - lines.size() * metrics.getHeight()) / 2 + metrics.getAscent();
            for (String line : lines) {
                g.drawString(line, (getWidth() - metrics.stringWidth(line)) / 2, y);
                y += metrics.getHeight();
            }
            g.dispose();
        }
    }

    static List<String> wrapHint(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>(); String line = "";
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(next) > width) {
                lines.add(line); line = word;
            } else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    void nextBackground() {
        if (closed || settings.reducedMotion()) return;
        for (int count = 1; count <= images.length; count++) {
            int next = (backgroundIndex + count) % images.length;
            if (images[next] != null) { backgroundIndex = next; repaint(); return; }
        }
    }

    void nextHint() { if (!tips.isEmpty() && !closed) { tipIndex = (tipIndex + 1) % tips.size(); updateHint(); } }
    boolean isReady() { return ready; }
    int backgroundIndex() { return backgroundIndex; }

    private void updateTimers() {
        if (!closed && isShowing()) { hints.start(); if (!settings.reducedMotion()) scenes.start(); }
        else { hints.stop(); scenes.stop(); }
    }

    @Override public void doLayout() {
        column.setPreferredSize(new Dimension(Math.max(240, Math.min(760, getWidth() - 64)), 231));
        updateHint(); super.doLayout();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        BattlefieldMenuPanel.paintCover(graphics, images[backgroundIndex], getWidth(), getHeight(), this);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, getHeight() * .42f, new Color(15, 17, 17, 0),
                0, getHeight(), new Color(15, 17, 17, 238)));
        g.fillRect(0, 0, getWidth(), getHeight()); g.dispose();
    }

    /** Cancels preparation callbacks and timers when the loading screen is left or disposed. */
    @Override public void close() {
        closed = true; hints.stop(); scenes.stop();
        if (worker != null) worker.cancel(true);
    }
}
