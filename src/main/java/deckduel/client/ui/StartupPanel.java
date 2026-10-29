package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.startup.StartupFlow;
import deckduel.client.startup.UpdateFlow;
import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * The launch screen before login: an iron plate on the animated valley, like the login card,
 * that shows the update check, any offered update and its install progress, then the server
 * connection, with Update, Continue, Retry, Play offline or Quit where the current step allows them. It only
 * renders {@link StartupFlow} states and forwards button presses; all checks run elsewhere.
 */
final class StartupPanel extends BattlefieldMenuPanel {
    private static final int TEXT_WIDTH = 460;

    private final String version;
    // Whether a verified update closes and restarts the game by itself (a real installation).
    private final boolean restarts;
    private final JLabel heading = centered(Theme.display(24f), Theme.BRASS_LIGHT);
    private final JLabel detail = centered(Theme.normal(13f), Theme.CREAM);
    private final JLabel failure = centered(Theme.bold(11f), Theme.CORAL);
    private final ProgressBar bar;
    private final JButton continueButton = UiKit.pill("Continue", UiKit.Pill.GOLD_OUTLINE);
    private final JButton quitButton = UiKit.pill("Quit", UiKit.Pill.GOLD_OUTLINE);
    private final JButton offlineButton = UiKit.pill("Play offline", UiKit.Pill.GOLD_OUTLINE);
    private final JButton retryButton = UiKit.pill("Retry", UiKit.Pill.GOLD);
    private final JButton updateButton = UiKit.pill("Update", UiKit.Pill.GOLD);
    private final JLabel footer = centered(Theme.normal(10f), Theme.withAlpha(Theme.FADED, 150));

    /**
     * @param fixture the local test fixture's name, labelled on screen, or null for real services
     * @param restarts whether a verified update restarts the game by itself
     * @param quit closes the client through the frame's save-and-close path
     */
    StartupPanel(ClientModel model, ClientSettings settings, StartupFlow flow, String version,
                 String fixture, boolean restarts, Runnable quit) {
        super(model, Screen.STARTUP, settings);
        this.version = version;
        this.restarts = restarts;
        bar = new ProgressBar(model::reducedMotion);
        setLayout(new GridBagLayout());
        getAccessibleContext().setAccessibleName("Deck Duel startup");
        updateButton.addActionListener(event -> flow.update());
        continueButton.addActionListener(event -> flow.proceed());
        retryButton.addActionListener(event -> flow.retry());
        offlineButton.addActionListener(event -> flow.playOffline());
        quitButton.addActionListener(event -> quit.run());
        updateButton.setName("startupUpdate");
        continueButton.setName("startupContinue");
        retryButton.setName("startupRetry");
        offlineButton.setName("startupOffline");
        quitButton.setName("startupQuit");

        JPanel card = UiKit.plate(30);
        card.setLayout(new GridBagLayout());
        GridBagConstraints at = new GridBagConstraints();
        at.gridx = 0;
        at.insets = new Insets(5, 6, 5, 6);
        at.fill = GridBagConstraints.HORIZONTAL;
        card.add(heading, at);
        card.add(detail, at);
        card.add(bar, at);
        card.add(failure, at);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);
        for (var button : new JButton[] {continueButton, quitButton, offlineButton, retryButton, updateButton})
            actions.add(button);
        // The row keeps its height while empty, so the plate does not jump between steps.
        actions.setPreferredSize(new Dimension(TEXT_WIDTH, updateButton.getPreferredSize().height));
        card.add(actions, at);
        footer.setName("startupFooter");
        card.add(footer, at);
        if (fixture != null) {
            JLabel tag = centered(Theme.bold(10f), Theme.AMBER);
            tag.setName("startupFixture");
            tag.setText("TEST FIXTURE · " + fixture.toUpperCase(Locale.ROOT) + " · NO REAL UPDATE IS CHECKED OR INSTALLED");
            card.add(tag, at);
        }
        add(card, new GridBagConstraints());

        // Enter presses the step's main button, as Enter submits the login form.
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "startup.primary");
        getActionMap().put("startup.primary", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                JButton primary = primaryButton();
                if (primary != null) primary.doClick(0);
            }
        });
        flow.addListener(this::render);
        render(flow.state());
    }

    private void render(StartupFlow.State state) {
        UpdateFlow.State update = state.update();
        String[] text = describe(state, restarts);
        heading.setText(text[0]);
        detail.setText(text[1]);
        boolean updating = state.phase() == StartupFlow.Phase.UPDATES;
        boolean installing = updating && update.stage() == UpdateFlow.Stage.INSTALLING;
        // The technical reason for a failed connection goes to the log, not to the player.
        String reason = updating && (update.stage() == UpdateFlow.Stage.CHECK_FAILED
                        || update.stage() == UpdateFlow.Stage.INSTALL_FAILED) ? update.failure() : null;
        failure.setText(reason == null ? " " : reason);
        boolean failed = reason != null || state.phase() == StartupFlow.Phase.CONNECTION_FAILED;
        boolean offered = updating && (update.stage() == UpdateFlow.Stage.OPTIONAL_UPDATE
                || update.stage() == UpdateFlow.Stage.REQUIRED_UPDATE);
        updateButton.setVisible(offered);
        retryButton.setVisible(failed);
        offlineButton.setVisible(state.phase() == StartupFlow.Phase.CONNECTION_FAILED);
        // A real installation restarts by itself once verified; only a fixture leaves the player to quit.
        boolean waiting = offered || failed || updating && update.stage() == UpdateFlow.Stage.INSTALLED && !restarts;
        // Continue appears only where entry is allowed; elsewhere Quit is the way out.
        continueButton.setVisible(waiting && updating && state.canContinue());
        quitButton.setVisible(waiting && !continueButton.isVisible());
        // A step with nothing to press is still working: the bar sweeps like a loading screen.
        if (installing) bar.setProgress(update.progress());
        else if (primaryButton() == null) bar.setLoading();
        else bar.hideBar();
        boolean offline = state.phase() == StartupFlow.Phase.OFFLINE_STARTING || state.phase() == StartupFlow.Phase.OFFLINE;
        footer.setText(("Version " + version + "  ·  " + (offline ? "offline play on this computer" : state.route().describe()))
                .toUpperCase(Locale.ROOT));
        revalidate();
        repaint();
        JButton primary = primaryButton();
        if (primary != null && isShowing()) primary.requestFocusInWindow();
    }

    private JButton primaryButton() {
        for (JButton button : new JButton[] {updateButton, retryButton, continueButton, quitButton})
            if (button.isVisible()) return button;
        return null;
    }

    /** The heading and one-line detail for a startup state. */
    static String[] describe(StartupFlow.State state, boolean restarts) {
        return switch (state.phase()) {
            case CONNECTING -> new String[] {"CONNECTING", "Connecting to the server…"};
            case CONNECTION_FAILED -> state.serverRequires() != null
                    ? new String[] {"UPDATE REQUIRED", "The server needs Deck Duel " + state.serverRequires() + " or newer. No update is available yet."}
                    : new String[] {"CONNECTION FAILED", "Cannot connect to the server."};
            case CONNECTED -> new String[] {"CONNECTED", "Opening login…"};
            case OFFLINE_STARTING -> new String[] {"STARTING OFFLINE PLAY", "Starting a private server on this computer…"};
            case OFFLINE -> new String[] {"OFFLINE PLAY", "Signing in to practice against the bots…"};
            case UPDATES -> state.serverRequires() != null && state.update().canInstall()
                    ? new String[] {"UPDATE REQUIRED", "The server needs version " + state.serverRequires() + " or newer. Update to keep playing online."}
                    : describe(state.update(), restarts);
        };
    }

    private static String[] describe(UpdateFlow.State update, boolean restarts) {
        String offered = update.offer() == null ? "" : update.offer().latestVersion();
        return switch (update.stage()) {
            case IDLE, CHECKING -> new String[] {"CHECKING FOR UPDATES", "Asking the update service for the latest release…"};
            case NOT_CONFIGURED -> new String[] {"UPDATES NOT SET UP", "No update service is configured, so nothing was checked."};
            case UP_TO_DATE -> new String[] {"UP TO DATE", "Version " + offered + " is the latest release."};
            case CHECK_FAILED -> new String[] {"UPDATE CHECK FAILED", "Retry, or continue without checking."};
            case OPTIONAL_UPDATE -> new String[] {"UPDATE AVAILABLE", "Version " + offered + " is ready. Update now, or continue."};
            case REQUIRED_UPDATE -> new String[] {"UPDATE REQUIRED", "Version " + offered + " is required to play."};
            case INSTALLING -> new String[] {"DOWNLOADING UPDATE",
                    "Downloading and verifying version " + offered + "…  " + Math.round(update.progress() * 100) + "%"};
            case INSTALL_FAILED -> new String[] {"UPDATE FAILED", update.offer().required()
                    ? "Version " + offered + " is still required. Retry the update."
                    : "Retry the update, or continue on this version."};
            case INSTALLED -> restarts
                    ? new String[] {"RESTARTING", "Version " + offered + " is verified. Deck Duel closes, installs it and starts again."}
                    : new String[] {"UPDATE INSTALLED", "Restart Deck Duel to finish updating to " + offered + "."};
        };
    }

    /** One line for the Settings page: what the last update check or install found. */
    static String summary(UpdateFlow.State update, boolean restarts) {
        String offered = update.offer() == null ? "" : update.offer().latestVersion();
        return switch (update.stage()) {
            case IDLE -> "Not checked yet.";
            case CHECKING -> "Checking for updates…";
            case NOT_CONFIGURED -> "No update service is configured; nothing was checked.";
            case UP_TO_DATE -> "Up to date.";
            case CHECK_FAILED -> "Check failed: " + update.failure();
            case OPTIONAL_UPDATE -> "Version " + offered + " is available.";
            case REQUIRED_UPDATE -> "Version " + offered + " is required.";
            case INSTALLING -> "Downloading " + offered + "…  " + Math.round(update.progress() * 100) + "%";
            case INSTALL_FAILED -> "Update failed: " + update.failure();
            case INSTALLED -> restarts ? offered + " is verified. Deck Duel restarts to install it once no match is in progress."
                    : "Installed " + offered + ". Restart Deck Duel to finish.";
        };
    }

    private static JLabel centered(java.awt.Font font, java.awt.Color colour) {
        JLabel label = new JLabel(" ", JLabel.CENTER);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font);
        label.setForeground(colour);
        label.setPreferredSize(new Dimension(TEXT_WIDTH, label.getFontMetrics(font).getHeight() + 2));
        return label;
    }

    /**
     * A brass bar in a sunken slot, like the castle-hall loading bar: filled to the download's
     * progress (the detail line reads out the percentage), a sweeping segment while a step is
     * loading, or empty space when hidden. Reduced motion shows the segment standing still.
     */
    private static final class ProgressBar extends JComponent {
        private static final double SEGMENT = .28, SWEEP_SECONDS = 1.6;
        private final BooleanSupplier reducedMotion;
        private final Timer frames = new Timer(33, event -> repaint());
        private final long start = System.nanoTime();
        private double progress = -1;
        private boolean loading;

        ProgressBar(BooleanSupplier reducedMotion) {
            this.reducedMotion = reducedMotion;
            setPreferredSize(new Dimension(TEXT_WIDTH, 12));
        }

        /** Shows the filled fraction of a download. */
        void setProgress(double value) { show(value, false); }

        /** Shows the sweeping loading segment. */
        void setLoading() { show(-1, true); }

        void hideBar() { show(-1, false); }

        private void show(double value, boolean sweep) {
            progress = value;
            loading = sweep;
            animate();
            repaint();
        }

        private void animate() {
            boolean run = loading && isDisplayable() && !reducedMotion.getAsBoolean();
            if (run && !frames.isRunning()) frames.start();
            else if (!run) frames.stop();
        }

        @Override public void addNotify() { super.addNotify(); animate(); }

        @Override public void removeNotify() { frames.stop(); super.removeNotify(); }

        @Override protected void paintComponent(Graphics graphics) {
            if (progress < 0 && !loading) return;
            var g = UiKit.nice(graphics);
            try {
                int x = 30, width = getWidth() - 60, height = getHeight();
                g.translate(x, 0);
                UiKit.paintSlot(g, width, height, false);
                int inner = width - 4;
                g.setPaint(new java.awt.GradientPaint(0, 2, Theme.BRASS_LIGHT, 0, height - 2, Theme.BRASS));
                if (loading) {
                    int segment = (int) Math.round(inner * SEGMENT);
                    double phase = (System.nanoTime() - start) / 1e9 / SWEEP_SECONDS % 1;
                    // Eased back and forth across the slot; centred and still with reduced motion.
                    double eased = reducedMotion.getAsBoolean() ? .5 : (1 - Math.cos(phase * 2 * Math.PI)) / 2;
                    int left = 2 + (int) Math.round((inner - segment) * eased);
                    g.fill(UiKit.chamfer(left, 2, segment, height - 4, 2));
                } else {
                    int filled = (int) Math.round(inner * progress);
                    if (filled > 0) g.fill(UiKit.chamfer(2, 2, filled, height - 4, 2));
                }
            } finally { g.dispose(); }
        }
    }
}
