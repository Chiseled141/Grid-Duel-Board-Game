package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.MenuMusic;
import deckduel.client.DeckDuelClient;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The main window: owns the socket client and the model, wires the reader
 * thread into the EDT, and switches between the four screens with a
 * {@link CardLayout}. Also the central place where server errors are shown
 * as dialogs.
 */
public final class ClientFrame extends JFrame {

    private static final String WINDOW_TITLE = "Deck Duel";

    private final DeckDuelClient client;
    private final ClientModel model;
    private final ClientSettings settings;
    private final MenuMusic menuMusic = new MenuMusic();
    private Screen visibleScreen = Screen.LOGIN;
    private LoadingPanel loadingPanel;
    private final GamePanel gamePanel;
    private final CardLayout screens = new CardLayout();
    private final JPanel content = new JPanel(screens);
    private javax.swing.JToggleButton soundToggle;

    /**
     * Builds the frame, connects the model to the network and shows login.
     * CLI host/port (when given) override the saved settings.
     */
    public ClientFrame(String host, Integer port) {
        super(WINDOW_TITLE);
        UiKit.installSystemTheme();
        this.settings = ClientSettings.load();
        if (host != null) {
            settings.setHost(host);
        }
        if (port != null) {
            settings.setPort(port);
        }

        this.client = new DeckDuelClient(
                message -> onIncomingMessage(message),
                reason -> SwingUtilities.invokeLater(this::onConnectionLost));
        this.model = new ClientModel(client);
        this.model.setReducedMotion(settings.reducedMotion());
        BoardPanel.setDefaultPieceStyle(settings.pieceStyle());

        LoginPanel loginPanel = new LoginPanel(model, settings);
        LobbyPanel lobbyPanel = new LobbyPanel(model, settings, model::openAtlas, this::closeAndSave);
        gamePanel = new GamePanel(model, settings);
        AtlasPanel atlasPanel = new AtlasPanel(model::closeAtlas);

        content.add(loginPanel, Screen.LOGIN.name());
        content.add(lobbyPanel, Screen.LOBBY.name());
        content.add(gamePanel, Screen.GAME.name());
        content.add(atlasPanel, Screen.ATLAS.name());
        add(buildScene());
        showScreen(Screen.LOGIN);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                showScreen(screen);
            }

            @Override
            public void onMatchResumed() {
                closeLoading();
                visibleScreen = Screen.GAME;
                renderScreen();
                model.setMatchEntryPending(false);
                gamePanel.setEntryPending(false);
            }

            @Override
            public void onError(String text) {
                JOptionPane.showMessageDialog(ClientFrame.this, text,
                        "Deck Duel", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Desktop board-game target: open big (clamped to the actual screen),
        // never below a composable size (sliver windows break the layout).
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int minW = Math.min(1280, screen.width - 40);
        int minH = Math.min(720, screen.height - 80);
        setMinimumSize(new java.awt.Dimension(minW, minH));
        setSize(Math.min(Math.max(settings.windowWidth(), 1280), screen.width - 40),
                Math.min(Math.max(settings.windowHeight(), 720), screen.height - 80));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeAndSave();
            }
        });
    }

    /** Floats the music icon over full-window screens without reserving a header row. */
    private JComponent buildScene() {
        javax.swing.JLayeredPane scene = new javax.swing.JLayeredPane() {
            @Override public void doLayout() {
                content.setBounds(0, 0, getWidth(), getHeight());
                soundToggle.setBounds(Math.max(0, getWidth() - 60), 12, 48, 44);
            }
        };
        soundToggle = UiKit.soundToggle();
        soundToggle.setName("menuMusicToggle");
        soundToggle.getAccessibleContext().setAccessibleName("Toggle menu music");
        soundToggle.setSelected(settings.soundEnabled());
        updateSoundTooltip();
        soundToggle.addItemListener(event -> {
            boolean on = event.getStateChange() == java.awt.event.ItemEvent.SELECTED;
            settings.setSoundEnabled(on);
            updateMenuMusic();
            java.util.concurrent.CompletableFuture.runAsync(settings::save);
            updateSoundTooltip();
        });
        scene.add(content, javax.swing.JLayeredPane.DEFAULT_LAYER);
        scene.add(soundToggle, javax.swing.JLayeredPane.PALETTE_LAYER);
        return scene;
    }

    private void updateSoundTooltip() {
        soundToggle.setToolTipText("Menu music: " + (soundToggle.isSelected() ? "On" : "Off"));
    }

    /**
     * The help overlay: a cream themed dialog (same surface family as the
     * game-over screen) with a display-size HOW TO PLAY header, three tab
     * pills (How to Play / Cards / Controls) switching a CardLayout, and a
     * drawn close button. Modal; ESC or the ✕ closes it.
     */

    /** Opens the shared live-rule help from either the lobby or an in-match control. */
    static void showHelpOverlay(java.awt.Window owner, deckduel.core.duel.RulesConfig rules) {
        UiKit.DialogSurface surface = new UiKit.DialogSurface(Theme.CREAM, 18);
        surface.setLayout(new BorderLayout(0, 10));
        surface.setBorder(BorderFactory.createEmptyBorder(18, 22, 18 + Theme.SHADOW_OFFSET, 22));
        javax.swing.JDialog dialog = UiKit.undecoratedDialog(owner, surface, 520, 420);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        javax.swing.JLabel title = new javax.swing.JLabel("HOW TO PLAY");
        title.setFont(Theme.display(18f));
        title.setForeground(Theme.INK);
        header.add(title, BorderLayout.WEST);
        header.add(UiKit.closeButton(dialog::dispose), BorderLayout.EAST);

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        JPanel pages = new JPanel(new java.awt.CardLayout());
        pages.setOpaque(false);
        java.util.List<UiKit.PillButton> tabButtons = new java.util.ArrayList<>();
        for (String name : java.util.List.of("How to Play", "Cards", "Controls")) {
            UiKit.PillButton tab = UiKit.miniPill(name, UiKit.Pill.GOLD_OUTLINE);
            tab.addActionListener(event -> {
                ((java.awt.CardLayout) pages.getLayout()).show(pages, name);
                for (UiKit.PillButton other : tabButtons) {
                    other.setVariant(other == tab ? UiKit.Pill.GOLD : UiKit.Pill.GOLD_OUTLINE);
                }
            });
            tabButtons.add(tab);
            tabs.add(tab);
            pages.add(helpPage(helpTabText(name, rules)), name);
        }
        tabButtons.get(0).setVariant(UiKit.Pill.GOLD);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(header);
        top.add(Box.createVerticalStrut(10));
        top.add(tabs);
        surface.add(top, BorderLayout.NORTH);
        surface.add(pages, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /** A body-text page: ink 12px, wrapped HTML in a fixed-width table
     * (Swing's HTML engine honors table widths, not div widths). */
    private static JComponent helpPage(String html) {
        JLabel page = new JLabel("<html><table width=452><tr><td>" + html + "</td></tr></table>");
        page.setFont(Theme.normal(12f));
        page.setForeground(Theme.INK);
        page.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        return page;
    }


    /** The three help tabs' copy (§Help overlay). */
    static String helpTabText(String tab, deckduel.core.duel.RulesConfig rules) {
        int limit = rules == null ? 60 : rules.turnLimit();
        int cap = rules == null ? 5 : rules.manaCap();
        int gain = rules == null ? 1 : rules.manaPerTurn();
        int initial = rules == null ? 2 : rules.initialMana();
        int skills = rules == null ? 3 : rules.skillHandSize();
        return switch (tab) {
            case "Cards" -> "<b>THE CARDS</b><br><br>Start with " + skills
                    + " skill cards from a shared skill deck. Opponent skills stay hidden except when Espionage reveals them privately."
                    + "<br><br>A successfully cast skill is spent permanently. Draw a replacement if available; Pilfer steals instead."
                    + "<br><br>Hold up to 6 cards. Any additional draw is torn and permanently destroyed. Only its owner sees its face."
                    + "<br><br>Draw Two draws extra cards. Refocus permanently discards your other skills, then draws that many. Skipping draws nothing.";
            case "Controls" -> "<b>CONTROLS</b><br><br>Select a friendly piece, "
                    + "then a highlighted destination. Use Skip Movement to go straight to skills."
                    + "<br><br>Select a skill and follow its targeting instructions. Resolve any "
                    + "Foresight choice before continuing. Hover cards for their full descriptions."
                    + "<br><br>Use End Turn when ready. ESC clears the current selection.";
            default -> "<b>DECK DUEL</b><br><br>Each player has seven identical pieces on a 7x7 board. "
                    + "Eliminate every opposing piece or reach " + (rules == null ? 8 : rules.scoreToWin()) + " points to win. Earn 1 point per enemy eliminated. Hold either gold-highlighted square until your next turn starts for +1 point each. Two mirrored gold-highlighted squares are chosen on the middle row for each match. Otherwise, " + limit
                    + " completed player turns end in a draw. Elimination takes priority."
                    + "<br><br>Turn order: optional movement, optional one skill, then End Turn. "
                    + "Move 1 square horizontally or vertically; landing on an enemy eliminates it. Friendly landing is illegal."
                    + "<br><br>Start with " + initial + " mana. Gain " + gain
                    + " at each own-turn start, including the first, up to " + cap + "."
                    + " Occupy either blue-highlighted square on the adjacent central rows for +1 bonus mana at your turn start; the same cap applies.";
        };
    }

    /** Reader-thread callback: hands the message to the model on the EDT. */
    private void onIncomingMessage(deckduel.net.Message message) {
        SwingUtilities.invokeLater(() -> model.handleMessage(message));
    }

    /** Reader-thread callback after an unexpected disconnect. */
    private void onConnectionLost() {
        model.handleConnectionLost();
    }

    private void showScreen(Screen screen) {
        visibleScreen = screen;
        if (screen == Screen.GAME) {
            if (loadingPanel == null) {
                model.setMatchEntryPending(true);
                gamePanel.setEntryPending(true);
                loadingPanel = new LoadingPanel(settings, model.rules(), this::enterFromLoading);
                content.add(loadingPanel, "LOADING");
            }
        } else {
            closeLoading();
            model.setMatchEntryPending(false);
            gamePanel.setEntryPending(false);
        }
        renderScreen();
    }

    private void renderScreen() {
        boolean loading = loadingPanel != null;
        soundToggle.setVisible(loading || visibleScreen != Screen.GAME);
        updateMenuMusic();
        screens.show(content, loading ? "LOADING" : visibleScreen.name());
        setTitle(WINDOW_TITLE + (model.roomCode() != null
                ? " - match " + model.roomCode() : ""));
    }

    private void enterFromLoading() {
        if (loadingPanel == null || !loadingPanel.isReady() || model.screen() != Screen.GAME) return;
        closeLoading();
        renderScreen();
        model.setMatchEntryPending(false);
        gamePanel.setEntryPending(false);
    }

    private void closeLoading() {
        if (loadingPanel == null) return;
        loadingPanel.close(); content.remove(loadingPanel); loadingPanel = null;
    }

    private void updateMenuMusic() {
        menuMusic.setPlaying(settings.soundEnabled() && (loadingPanel != null || visibleScreen != Screen.GAME), settings.menuTheme());
    }

    /** Releases the menu audio even when the frame is disposed without using Exit. */
    @Override
    public void dispose() {
        closeLoading();
        menuMusic.close();
        super.dispose();
    }

    private void closeAndSave() {
        settings.setWindowSize(getWidth(), getHeight());
        settings.save();
        client.close();
        dispose();
        System.exit(0);
    }
}
