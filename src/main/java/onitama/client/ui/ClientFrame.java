package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.OnitamaClient;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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

        LoginPanel loginPanel = new LoginPanel(model, settings);
        LobbyPanel lobbyPanel = new LobbyPanel(model,
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

        setSize(settings.windowWidth(), settings.windowHeight());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeAndSave();
            }
        });
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
