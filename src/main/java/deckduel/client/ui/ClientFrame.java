package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.MenuMusic;
import deckduel.client.ClientTls;
import deckduel.client.DeckDuelClient;
import deckduel.client.startup.Endpoints;
import deckduel.client.OfflineServer;
import deckduel.client.startup.InstalledVersion;
import deckduel.client.startup.StartupFlow;
import deckduel.client.startup.StartupServices;
import deckduel.client.startup.UpdateFlow;
import deckduel.client.state.*;
import deckduel.client.update.UpdateFinisher;
import deckduel.launcher.InstallLayout;
import java.awt.CardLayout;
import javax.net.ssl.SSLContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/**
 * Owns the server connection and switches between the Swing startup, login, menu and match
 * history; the match table is a JavaFX screen shown in its own window by {@link FxScreens}.
 * The client opens on the startup checks, which connect before login.
 */
public final class ClientFrame extends JFrame {
    /** How long "Up to date" stays readable before the client connects on its own. */
    private static final long SETTLE_MILLIS = 700;

    private final DeckDuelClient client;
    private final ClientModel model;
    private final ClientSettings settings;
    private final MenuMusic menuMusic = new MenuMusic();
    private final deckduel.client.ScoringSounds scoringSounds;
    private final CardLayout screens = new CardLayout();
    private final JPanel content = new JPanel(screens);
    private JToggleButton soundToggle;
    private final FxScreens fxScreens;
    // Runs update checks, installs and the startup connection, off both UI threads. Work handed
    // over after the window closed is dropped rather than thrown on the EDT.
    private final ExecutorService startupWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "deckduel-startup");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private final OfflineServer offline = new OfflineServer();
    private boolean disposed;

    /** Builds the original menu scene with no release channel; optional host/port are development overrides. */
    public ClientFrame(String host, Integer port) { this(host, port, StartupServices.unconfigured()); }

    /** Builds the menu scene for a copy that was not started from an installation. */
    public ClientFrame(String host, Integer port, StartupServices services) {
        this(Endpoints.resolve(host, port, null, Endpoints.packaged(), ClientSettings.load().host(), ClientSettings.load().port()),
                ClientTls.systemTrust(), services, null);
    }

    /**
     * Builds the menu scene.
     *
     * @param server the server chosen by {@link Endpoints#resolve}; saved preferences are not rewritten
     * @param trust the certificate authorities trusted for TLS (the Java runtime's own for players)
     * @param installation the installation that started this copy, told once the client has started
     *        properly so the launcher never rolls it back; null when run directly from a jar
     */
    public ClientFrame(Endpoints.Choice server, SSLContext trust, StartupServices services, InstallLayout installation) {
        super("Deck Duel");
        UiKit.installSystemTheme();
        settings = ClientSettings.load();
        java.util.logging.Logger.getLogger(ClientFrame.class.getName())
                .info(() -> "server: " + server.route().describe() + " (" + server.source() + ")");
        client = new DeckDuelClient(message -> SwingUtilities.invokeLater(() -> modelMessage(message)),
                reason -> SwingUtilities.invokeLater(this::connectionLost), trust, InstalledVersion.current(),
                DeckDuelClient.HANDSHAKE_TIMEOUT_MILLIS);
        model = new ClientModel(client);
        model.setReducedMotion(settings.reducedMotion());
        String version = InstalledVersion.current();
        var updates = new UpdateFlow(version, services.updates(), services.installer(),
                startupWorker, SwingUtilities::invokeLater);
        var startup = new StartupFlow(updates,
                services.connection().apply(client::ensureConnected), offline::start,
                server.route(), startupWorker, SwingUtilities::invokeLater, SETTLE_MILLIS);
        startup.addListener(state -> {
            if (state.phase() == StartupFlow.Phase.CONNECTED) model.startupFinished();
            // Offline play signs in to its own fresh account and opens the main menu directly.
            if (state.phase() == StartupFlow.Phase.OFFLINE)
                model.beginOffline(state.route(), OfflineServer.USERNAME, offline.password());
        });
        boolean restarts = services.restarter() != null;
        content.add(new StartupPanel(model, settings, startup, version, services.fixture(), restarts, this::closeAndSave),
                Screen.STARTUP.name());
        content.add(new LoginPanel(model, settings, server.route()), Screen.LOGIN.name());
        scoringSounds = new deckduel.client.ScoringSounds(settings::gameSounds);
        var lobby = new LobbyPanel(model, settings, this::closeAndSave, updates, services.fixture(), restarts);
        lobby.onGameSoundsChanged(() -> { if (!settings.gameSounds()) scoringSounds.stop(); });
        content.add(lobby, Screen.LOBBY.name());
        content.add(new HistoryPanel(model, settings, model::leaveToLobby), Screen.HISTORY.name());
        content.add(new ReplayPanel(model), Screen.REPLAY.name());
        add(buildScene());
        fxScreens = new FxScreens(model, settings, scoringSounds, this, () -> soundToggle.doClick(),
                settings::soundEnabled, bounds -> { setBounds(bounds); closeAndSave(); });
        // Remembers required updates, holds new matches while installing, and restarts once no match is in progress.
        new UpdateFinisher(model, updates, services.restarter(), () -> { closeAndSave(); System.exit(0); }, text -> {
            if (fxScreens.showing()) fxScreens.notice(text);
            else UiKit.noticeDialog(this, "Deck Duel", text);
        });
        if (installation != null) startupWorker.execute(() -> confirmStarted(installation, version));
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                // The JavaFX table has no card here; the menu stays behind it, hidden.
                if (screen != Screen.TABLE) screens.show(content, screen.name());
                updateMenuMusic();
            }
            // A failed offline sign-in is shown on the login form rather than leaving the startup screen up.
            @Override public void onLoginFailed(String reason) { model.startupFinished(); }
            @Override public void onError(String text) {
                if (fxScreens.showing()) fxScreens.notice(text);
                else UiKit.noticeDialog(ClientFrame.this, "Deck Duel", text);
            }
        });
        model.showStartup();
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
        startup.start();
    }

    /** Tells the launcher this version started properly, so it is never rolled back. */
    private static void confirmStarted(InstallLayout installation, String version) {
        try {
            installation.confirm(version);
        } catch (java.io.IOException | IllegalArgumentException unrecorded) {
            java.util.logging.Logger.getLogger(ClientFrame.class.getName())
                    .log(java.util.logging.Level.WARNING, "could not confirm the start of " + version, unrecorded);
        }
    }

    private void modelMessage(deckduel.net.Message message) { model.handleMessage(message); }
    private void connectionLost() { model.handleConnectionLost(); }

    private JComponent buildScene() {
        JLayeredPane scene = new JLayeredPane() {
            @Override public void doLayout() {
                content.setBounds(0, 0, getWidth(), getHeight());
                soundToggle.setBounds(Math.max(0, getWidth() - 60), 16, 44, 40);
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
        fxScreens.quit();
    }

    /** Closes audio and the account socket without leaving background connection threads running. */
    @Override public void dispose() {
        // Quit paths may arrive twice (Quit then the window's close); release everything once.
        if (disposed) { super.dispose(); return; }
        disposed = true;
        startupWorker.shutdownNow();
        client.close();
        offline.close();
        menuMusic.close();
        scoringSounds.close();
        fxScreens.dispose();
        super.dispose();
    }
}
