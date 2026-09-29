package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.OnitamaClient;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
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

    private static final String WINDOW_TITLE = "Onitama Online";

    private final OnitamaClient client;
    private final ClientModel model;
    private final ClientSettings settings;
    private final CardLayout screens = new CardLayout();
    private final JPanel content = new JPanel(screens);

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
        BoardPanel.setDefaultPieceStyle(settings.pieceStyle());

        LoginPanel loginPanel = new LoginPanel(model, settings);
        LobbyPanel lobbyPanel = new LobbyPanel(model, settings,
                this::openLeaderboard, this::openReplayViewer);
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
                        "Onitama", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Desktop board-game target: open big, but never let the user shrink
        // the window below a composable size (sliver windows break the layout).
        setSize(Math.max(settings.windowWidth(), 1100),
                Math.max(settings.windowHeight(), 880));
        setMinimumSize(new java.awt.Dimension(780, 720));
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

    /** Game and Help menus; entries act on the current screen. */
    private javax.swing.JMenuBar buildMenuBar() {
        javax.swing.JMenuBar menuBar = new javax.swing.JMenuBar();
        // Aqua ignores background/foreground on menus — force the Basic UI
        // so the navigation strip can be styled like the rest of the app.
        menuBar.setUI(new javax.swing.plaf.basic.BasicMenuBarUI());
        menuBar.setBackground(Theme.BG);
        menuBar.setOpaque(true);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.OUTLINE));
        menuBar.setFont(Theme.bold(13f));

        javax.swing.JMenu game = styledMenu("Game");
        javax.swing.JMenuItem resign = new javax.swing.JMenuItem("Resign match");
        resign.addActionListener(event -> model.resign());
        javax.swing.JMenuItem lobby = new javax.swing.JMenuItem("Back to lobby");
        lobby.addActionListener(event -> model.leaveToLobby());
        javax.swing.JMenuItem exit = new javax.swing.JMenuItem("Exit");
        exit.addActionListener(event -> closeAndSave());
        game.add(resign);
        game.add(lobby);
        game.addSeparator();
        game.add(exit);
        menuBar.add(game);

        javax.swing.JMenu help = styledMenu("Help");
        help.add(javax.swing.Box.createHorizontalStrut(8));
        javax.swing.JMenuItem rules = new javax.swing.JMenuItem("How to play");
        rules.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "Onitama: move one of your pieces with one of your two hand cards.\n"
                        + "The used card swaps with the transit card, so your opponent will\n"
                        + "get it two half-moves later - always check what you hand over.\n\n"
                        + "Win by capturing the enemy Master (Way of the Stone) or by moving\n"
                        + "your Master onto the enemy Temple Arch (Way of the Stream).",
                "How to play", JOptionPane.INFORMATION_MESSAGE));
        javax.swing.JMenuItem about = new javax.swing.JMenuItem("About");
        about.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "Onitama Online - a networked implementation of the board game\n"
                        + "Onitama by Shimpei Sato (Arcane Wonders, 2014).\n"
                        + "Java Software Development final project.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(rules);
        help.add(about);
        menuBar.add(javax.swing.Box.createHorizontalStrut(10));
        menuBar.add(help);
        return menuBar;
    }

    /** A coal-styled menu with comfortable spacing between Game and Help. */
    private javax.swing.JMenu styledMenu(String text) {
        javax.swing.JMenu menu = new javax.swing.JMenu(text);
        menu.setUI(new javax.swing.plaf.basic.BasicMenuUI());
        menu.setBackground(Theme.BG);
        menu.setForeground(Theme.CREAM);
        menu.setFont(Theme.bold(13f));
        menu.setOpaque(true);
        menu.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
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
        screens.show(content, screen.name());
        setTitle(WINDOW_TITLE + (model.roomCode() != null
                ? " - match " + model.roomCode() : ""));
    }

    private void openLeaderboard() {
        model.requestLeaderboard();
        showScreen(Screen.LEADERBOARD);
    }

    private void openReplayViewer() {
        new ReplayViewer(this).setVisible(true);
    }

    private void closeAndSave() {
        settings.setWindowSize(getWidth(), getHeight());
        settings.save();
        client.close();
        dispose();
        System.exit(0);
    }
}
