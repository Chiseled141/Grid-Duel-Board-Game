package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.MenuMusic;
import deckduel.client.DeckDuelClient;
import deckduel.client.state.*;
import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/** Owns the account connection and switches between the preserved menu and local shell pages. */
public final class ClientFrame extends JFrame {
    private final DeckDuelClient client;
    private final ClientModel model;
    private final ClientSettings settings;
    private final MenuMusic menuMusic = new MenuMusic();
    private final CardLayout screens = new CardLayout();
    private final JPanel content = new JPanel(screens);
    private JToggleButton soundToggle;

    /** Builds the original menu scene; optional host/port override saved connection settings. */
    public ClientFrame(String host, Integer port) {
        super("Deck Duel");
        UiKit.installSystemTheme();
        settings = ClientSettings.load();
        if (host != null) settings.setHost(host);
        if (port != null) settings.setPort(port);
        client = new DeckDuelClient(message -> SwingUtilities.invokeLater(() -> modelMessage(message)),
                reason -> SwingUtilities.invokeLater(this::connectionLost));
        model = new ClientModel(client);
        model.setReducedMotion(settings.reducedMotion());
        content.add(new LoginPanel(model, settings), Screen.LOGIN.name());
        content.add(new LobbyPanel(model, settings, model::openDeck, this::closeAndSave), Screen.LOBBY.name());
        content.add(new DeckPanel(model::closeDeck), Screen.DECK.name());
        content.add(new CardTablePanel(model::leaveToLobby), Screen.TABLE.name());
        content.add(new LeaderboardPanel(model, settings, model::leaveToLobby), Screen.LEADERBOARD.name());
        add(buildScene());
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                screens.show(content, screen.name());
                updateMenuMusic();
            }
            @Override public void onError(String text) {
                JOptionPane.showMessageDialog(ClientFrame.this, text, "Deck Duel", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        screens.show(content, Screen.LOGIN.name());
        var screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new java.awt.Dimension(Math.min(1280, screen.width - 40), Math.min(720, screen.height - 80)));
        setSize(Math.min(Math.max(settings.windowWidth(), 1280), screen.width - 40),
                Math.min(Math.max(settings.windowHeight(), 720), screen.height - 80));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeAndSave(); }
        });
        updateMenuMusic();
    }

    private void modelMessage(deckduel.net.Message message) { model.handleMessage(message); }
    private void connectionLost() { model.handleConnectionLost(); }

    private JComponent buildScene() {
        JLayeredPane scene = new JLayeredPane() {
            @Override public void doLayout() {
                content.setBounds(0, 0, getWidth(), getHeight());
                soundToggle.setBounds(Math.max(0, getWidth() - 60), 12, 48, 44);
            }
        };
        soundToggle = UiKit.soundToggle();
        soundToggle.setName("menuMusicToggle");
        soundToggle.getAccessibleContext().setAccessibleName("Toggle menu music");
        soundToggle.setSelected(settings.soundEnabled());
        soundToggle.addItemListener(event -> {
            settings.setSoundEnabled(soundToggle.isSelected());
            updateMenuMusic();
            java.util.concurrent.CompletableFuture.runAsync(settings::save);
        });
        scene.add(content, JLayeredPane.DEFAULT_LAYER);
        scene.add(soundToggle, JLayeredPane.PALETTE_LAYER);
        return scene;
    }

    private void updateMenuMusic() {
        soundToggle.setToolTipText("Menu music: " + (settings.soundEnabled() ? "On" : "Off"));
        menuMusic.setPlaying(settings.soundEnabled(), settings.menuTheme());
    }

    private void closeAndSave() {
        settings.setWindowSize(getWidth(), getHeight());
        settings.save();
        dispose();
    }

    /** Closes audio and the account socket without leaving background connection threads running. */
    @Override public void dispose() {
        client.close();
        menuMusic.close();
        super.dispose();
    }
}
