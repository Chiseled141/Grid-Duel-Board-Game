package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.MenuMusic;
import onitama.client.OnitamaClient;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

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

    private static final String WINDOW_TITLE = "Grid Duel";

    private final OnitamaClient client;
    private final ClientModel model;
    private final ClientSettings settings;
    private final MenuMusic menuMusic = new MenuMusic();
    private Screen visibleScreen = Screen.LOGIN;
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

        this.client = new OnitamaClient(
                message -> onIncomingMessage(message),
                reason -> SwingUtilities.invokeLater(this::onConnectionLost));
        this.model = new ClientModel(client);
        this.model.setReducedMotion(settings.reducedMotion());
        BoardPanel.setDefaultPieceStyle(settings.pieceStyle());

        LoginPanel loginPanel = new LoginPanel(model, settings);
        LobbyPanel lobbyPanel = new LobbyPanel(model, settings, this::openLeaderboard, this::closeAndSave);
        GamePanel gamePanel = new GamePanel(model);
        LeaderboardPanel leaderboardPanel = new LeaderboardPanel(model, () -> model.leaveToLobby());

        content.add(loginPanel, Screen.LOGIN.name());
        content.add(lobbyPanel, Screen.LOBBY.name());
        content.add(gamePanel, Screen.GAME.name());
        content.add(leaderboardPanel, Screen.LEADERBOARD.name());
        add(content);
        showScreen(Screen.LOGIN);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                showScreen(screen);
            }

            @Override
            public void onError(String text) {
                JOptionPane.showMessageDialog(ClientFrame.this, text,
                        "Grid Duel", JOptionPane.ERROR_MESSAGE);
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
        setJMenuBar(buildMenuBar());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeAndSave();
            }
        });
    }

    /**
     * The app header: the Game/Help
     * menus with comfortable 8-12px padding, and a menu-music toggle on the
     * right. The painting remains unobstructed by a title block.
     */
    private javax.swing.JMenuBar buildMenuBar() {
        javax.swing.JMenuBar menuBar = new javax.swing.JMenuBar();
        // Aqua ignores background/foreground on menus — force the Basic UI
        // so the navigation strip can be styled like the rest of the app.
        menuBar.setUI(new javax.swing.plaf.basic.BasicMenuBarUI());
        menuBar.setBackground(Theme.BG);
        menuBar.setOpaque(true);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.OUTLINE));

        javax.swing.JMenu game = styledMenu("Game");
        javax.swing.JMenuItem resign = new javax.swing.JMenuItem("Resign match");
        resign.addActionListener(event -> confirmMenuResign());
        javax.swing.JMenuItem lobby = new javax.swing.JMenuItem("Back to lobby");
        lobby.addActionListener(event -> model.leaveToLobby());
        javax.swing.JMenuItem exit = new javax.swing.JMenuItem("Exit");
        exit.addActionListener(event -> closeAndSave());
        javax.swing.JCheckBoxMenuItem reducedMotion = new javax.swing.JCheckBoxMenuItem(
                "Reduced motion", settings.reducedMotion());
        reducedMotion.addActionListener(event -> {
            boolean rm = reducedMotion.isSelected();
            settings.setReducedMotion(rm);
            model.setReducedMotion(rm);
            settings.save();
        });
        game.add(resign);
        game.add(lobby);
        game.addSeparator();
        game.add(reducedMotion);
        game.addSeparator();
        game.add(exit);
        menuBar.add(game);

        javax.swing.JMenu help = styledMenu("Help");
        javax.swing.JMenuItem rules = new javax.swing.JMenuItem("How to play");
        rules.addActionListener(event -> showHelpOverlay());
        javax.swing.JMenuItem about = new javax.swing.JMenuItem("About");
        about.addActionListener(event -> showAboutOverlay());
        help.add(rules);
        help.add(about);
        menuBar.add(help);

        menuBar.add(javax.swing.Box.createHorizontalGlue());
        soundToggle = UiKit.soundToggle();
        soundToggle.setSelected(settings.soundEnabled());
        updateSoundTooltip();
        // Apply the existing saved preference to the menu track.
        soundToggle.addItemListener(event -> {
            boolean on = event.getStateChange() == java.awt.event.ItemEvent.SELECTED;
            settings.setSoundEnabled(on);
            updateMenuMusic();
            settings.save();
            updateSoundTooltip();
        });
        menuBar.add(soundToggle);
        menuBar.add(javax.swing.Box.createHorizontalStrut(10));
        return menuBar;
    }

    private void updateSoundTooltip() {
        soundToggle.setToolTipText("Menu music: " + (soundToggle.isSelected() ? "On" : "Off"));
    }

    /**
     * Confirmation guard for the menu's resign entry: a forfeit costs Elo,
     * so it must never happen on a stray click, and with no match running
     * there is nothing to resign.
     */
    private void confirmMenuResign() {
        if (!canMenuResign(model)) return;
        boolean confirmed = UiKit.confirmDialog(this, "Resign",
                "Resign this match?", "Yes", UiKit.Pill.DANGER);
        if (confirmed && canMenuResign(model)) {
            model.resign();
        }
    }

    /** Menu actions use the same live recipient view as the game panel. */
    static boolean canMenuResign(ClientModel model) {
        return model.view() != null && !model.isMatchOver() && model.inFlightRequest() == null;
    }

    /**
     * The help overlay: a cream themed dialog (same surface family as the
     * game-over screen) with a display-size HOW TO PLAY header, three tab
     * pills (How to Play / Cards / Controls) switching a CardLayout, and a
     * drawn close button. Modal; ESC or the ✕ closes it.
     */
    private void showHelpOverlay() {
        UiKit.DialogSurface surface = new UiKit.DialogSurface(Theme.CREAM, 18);
        surface.setLayout(new BorderLayout(0, 10));
        surface.setBorder(BorderFactory.createEmptyBorder(18, 22, 18 + Theme.SHADOW_OFFSET, 22));
        javax.swing.JDialog dialog = UiKit.undecoratedDialog(this, surface, 520, 420);

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
            pages.add(helpPage(helpTabText(name, model.rules())), name);
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
    private JComponent helpPage(String html) {
        JLabel page = new JLabel("<html><table width=452><tr><td>" + html + "</td></tr></table>");
        page.setFont(Theme.normal(12f));
        page.setForeground(Theme.INK);
        page.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        return page;
    }

    /** The themed About dialog: the same cream surface family as Help. */
    private void showAboutOverlay() {
        UiKit.DialogSurface surface = new UiKit.DialogSurface(Theme.CREAM, 18);
        surface.setLayout(new BorderLayout(0, 12));
        surface.setBorder(BorderFactory.createEmptyBorder(20, 24, 20 + Theme.SHADOW_OFFSET, 24));
        javax.swing.JDialog dialog = UiKit.undecoratedDialog(this, surface, 430, 220);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        javax.swing.JLabel title = new javax.swing.JLabel("ABOUT");
        title.setFont(Theme.display(18f));
        title.setForeground(Theme.INK);
        header.add(title, BorderLayout.WEST);
        header.add(UiKit.closeButton(dialog::dispose), BorderLayout.EAST);
        surface.add(header, BorderLayout.NORTH);

        JLabel body = new JLabel("<html><div style='width:350px'>"
                + "Grid Duel — a 7x7 strategy game with movement and skill cards. "
                + "Movement inspired by Onitama by Shimpei Sato."
                + "<br><br>Java Software Development final project.</div></html>");
        body.setFont(Theme.normal(12f));
        body.setForeground(Theme.INK);
        body.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        surface.add(body, BorderLayout.CENTER);

        UiKit.playAppearTween(surface);
        dialog.setVisible(true);
    }

    /** The three help tabs' copy (§Help overlay). */
    static String helpTabText(String tab, onitama.core.duel.RulesConfig rules) {
        int limit = rules == null ? 60 : rules.turnLimit();
        int cap = rules == null ? 5 : rules.manaCap();
        int gain = rules == null ? 1 : rules.manaPerTurn();
        int initial = rules == null ? 2 : rules.initialMana();
        int skills = rules == null ? 3 : rules.skillHandSize();
        return switch (tab) {
            case "Cards" -> "<b>THE CARDS</b><br><br>Start with two movement cards and "
                    + skills + " skill cards. Both players share a 16-card movement deck and a "
                    + "24-card skill deck. Movement hands are public; opponent skills stay hidden."
                    + "<br><br>A used movement card returns to the deck, which is shuffled before "
                    + "drawing its replacement. The same card may return immediately."
                    + "<br><br>A successfully cast skill is spent permanently. Draw a replacement "
                    + "if the skill deck has cards. Skipping does not draw a card.";
            case "Controls" -> "<b>CONTROLS</b><br><br>Select a movement card, a friendly piece, "
                    + "then a highlighted destination. Use Skip Movement to go straight to skills."
                    + "<br><br>Select a skill and follow its targeting instructions. Resolve any "
                    + "Foresight choice before continuing. Hover cards for their full descriptions."
                    + "<br><br>Use End Turn when ready. ESC clears the current selection.";
            default -> "<b>GRID DUEL</b><br><br>Each player has six identical pieces on a 7x7 board. "
                    + "Eliminate every opposing piece to win. Otherwise, " + limit
                    + " completed player turns end in a draw. Elimination takes priority."
                    + "<br><br>Turn order: optional movement, optional one skill, then End Turn. "
                    + "Movement jumps; landing on an enemy eliminates it. Friendly landing is illegal."
                    + "<br><br>Start with " + initial + " mana. Gain " + gain
                    + " at each own-turn start, including the first, up to " + cap + ".";
        };
    }

    /** A coal-styled menu with comfortable spacing between Game and Help. */
    private javax.swing.JMenu styledMenu(String text) {
        javax.swing.JMenu menu = new javax.swing.JMenu(text);
        menu.setUI(new javax.swing.plaf.basic.BasicMenuUI());
        menu.setBackground(Theme.BG);
        menu.setForeground(Theme.CREAM);
        menu.setFont(Theme.bold(14f));
        menu.setOpaque(true);
        menu.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return menu;
    }

    /** Reader-thread callback: hands the message to the model on the EDT. */
    private void onIncomingMessage(onitama.net.Message message) {
        SwingUtilities.invokeLater(() -> model.handleMessage(message));
    }

    /** Reader-thread callback after an unexpected disconnect. */
    private void onConnectionLost() {
        model.handleConnectionLost();
    }

    private void showScreen(Screen screen) {
        visibleScreen = screen;
        updateMenuMusic();
        screens.show(content, screen.name());
        setTitle(WINDOW_TITLE + (model.roomCode() != null
                ? " - match " + model.roomCode() : ""));
    }

    private void updateMenuMusic() {
        menuMusic.setPlaying(settings.soundEnabled() && visibleScreen != Screen.GAME);
    }

    /** Releases the menu audio even when the frame is disposed without using Exit. */
    @Override
    public void dispose() {
        menuMusic.close();
        super.dispose();
    }

    private void openLeaderboard() {
        model.requestLeaderboard();
        showScreen(Screen.LEADERBOARD);
    }

    private void closeAndSave() {
        settings.setWindowSize(getWidth(), getHeight());
        settings.save();
        client.close();
        dispose();
        System.exit(0);
    }
}
