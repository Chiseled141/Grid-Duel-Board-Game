package deckduel.server;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.Catalog;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteReplayDao;
import deckduel.db.SqliteUserDao;
import deckduel.db.UserDao;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLServerSocket;

/** Local server for accounts, hosted rooms, practice and authoritative card matches. */
public final class GameServer {
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());
    private final ServerConfig config;
    private final UserDao userDao;
    private final Catalog catalog;
    private final ResultRecorder recorder;
    private final MatchHistory history;
    private final ReplayStore replays;
    private final SessionRegistry registry = new SessionRegistry();
    private final LobbyManager lobby = new LobbyManager();
    private final MatchRegistry matches = new MatchRegistry();
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService clients;
    private final ScheduledExecutorService timers;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile ServerSocket listener;
    private volatile boolean draining;
    // Match admission, rematch reservation and the maintenance snapshot share one barrier.
    private final Object admission = new Object();

    /** Creates a server with the bundled card catalog that does not record match history or replays. */
    public GameServer(ServerConfig config, UserDao userDao) {
        this(config, userDao, CatalogLoader.loadBundled(), ResultRecorder.NONE, MatchHistory.NONE, ReplayStore.NONE);
    }

    /**
     * Creates a server with an immutable card catalog shared by every match, a result recorder,
     * history reader and replay store.
     */
    public GameServer(ServerConfig config, UserDao userDao, Catalog catalog, ResultRecorder recorder, MatchHistory history,
                      ReplayStore replays) {
        this.config = config;
        this.userDao = userDao;
        this.catalog = catalog;
        this.recorder = recorder;
        this.history = history;
        this.replays = replays;
        clients = Executors.newFixedThreadPool(config.clientPoolSize(), task -> daemon(task, "deckduel-client"));
        // Timers only post work onto match executors; they never touch match state themselves.
        timers = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "deckduel-match-timers"));
    }

    /** Creates a server that keeps accounts, results, history and replays in {@code database}, on the bundled catalog. */
    public static GameServer recording(Database database, ServerConfig config) {
        var users = new SqliteUserDao(database);
        Catalog catalog = CatalogLoader.loadBundled();
        LOG.info("Card catalog " + catalog.version());
        var persistence = new MatchPersistence(database, new SqliteMatchDao(database, users), new SqliteReplayDao(database));
        return new GameServer(config, users, catalog, persistence, persistence, persistence);
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * Listens on the configured address and returns the actual port. Anything but loopback is
     * refused without TLS, so a public plaintext listener cannot be started by mistake.
     */
    public int start() throws IOException {
        if (!config.loopbackOnly() && config.tls() == null)
            throw new IOException("Refusing to listen on " + config.bind().getHostAddress() + " without TLS; configure tls.certificateChain and tls.privateKey");
        if (config.tls() == null) {
            listener = new ServerSocket(config.port(), 50, config.bind());
        } else {
            SSLServerSocket secure = (SSLServerSocket) config.tls().context().getServerSocketFactory()
                    .createServerSocket(config.port(), 50, config.bind());
            secure.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
            listener = secure;
        }
        LOG.info(() -> "listening on " + config.bind().getHostAddress() + ":" + listener.getLocalPort()
                + (config.tls() == null ? " (plain, this computer only)" : " (TLS)"));
        Thread acceptor = new Thread(this::accept, "deckduel-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        return listener.getLocalPort();
    }

    /** Waits for shutdown in the server command. */
    public void awaitShutdown() throws InterruptedException { stopped.await(); }

    /** Closes authenticated and unauthenticated sockets, then releases workers. */
    public void stop() {
        try { if (listener != null) listener.close(); }
        catch (IOException exception) { LOG.log(Level.FINE, "Closing listener", exception); }
        connections.forEach(handler -> handler.kick("Server is shutting down."));
        clients.shutdownNow();
        timers.shutdownNow();
        matches.sessions().forEach(MatchSession::close);
        lobby.clear();
        stopped.countDown();
    }

    private void accept() {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                ClientHandler handler = new ClientHandler(this, socket);
                connections.add(handler);
                try { clients.execute(handler); }
                catch (RejectedExecutionException exception) { handler.kick("Server is shutting down."); connections.remove(handler); }
            } catch (IOException exception) {
                if (!listener.isClosed()) LOG.log(Level.WARNING, "Accept failed", exception);
            }
        }
    }

    /** Starts a recorded match unless either account is already playing; {@code a} takes the first seat. */
    boolean startMatch(Entrant a, Entrant b) { return start(MatchSession.between(this, a, b), List.of(a, b)); }

    /** Starts an unrated practice match against the easy or hard bot. */
    boolean startPractice(Entrant human, boolean hard) { return start(MatchSession.practice(this, human, hard), List.of(human)); }

    private boolean start(MatchSession session, List<Entrant> entrants) {
        // Register under the drain barrier before queuing any match work.
        synchronized (admission) {
            if (draining || !matches.bind(session, session.humanUsernames())) { session.close(); return false; }
        }
        for (Entrant entrant : entrants) lobby.removeHostedLobby(entrant.handler());
        session.start();
        return true;
    }

    /**
     * Starts maintenance: no new rooms, joins, practice matches or rematches; waiting rooms are
     * closed and every connected player is told. Matches in progress and held seats continue.
     */
    public void drain() {
        synchronized (admission) {
            draining = true;
            lobby.close();
        }
        connections.forEach(handler -> handler.notifyMaintenance(true));
        LOG.info(() -> "maintenance started: " + status());
    }

    /** Ends maintenance and admits new matches again. */
    public void resume() {
        synchronized (admission) {
            lobby.reopen();
            draining = false;
        }
        connections.forEach(handler -> handler.notifyMaintenance(false));
        LOG.info("maintenance ended");
    }

    /** Reserves a rematch before maintenance can observe the old game as idle. */
    boolean reserveRematch(MatchSession session) {
        synchronized (admission) {
            if (draining) return false;
            session.reserveGame();
            return true;
        }
    }

    /** Whether maintenance is under way. */
    public boolean draining() { return draining; }

    /** Connections, rooms, matches in progress and held seats right now. */
    public ServerStatus status() {
        synchronized (admission) {
            List<MatchSession> sessions = matches.sessions();
            return new ServerStatus(draining, connections.size(), lobby.openMatches().size(),
                    (int) sessions.stream().filter(MatchSession::inProgress).count(),
                    sessions.stream().mapToInt(MatchSession::heldSeats).sum(),
                    config.tls() == null ? null : config.tls().certificate().getNotAfter().toInstant());
        }
    }

    void disconnected(ClientHandler handler) { connections.remove(handler); }
    ServerConfig config() { return config; }
    UserDao userDao() { return userDao; }
    SessionRegistry registry() { return registry; }
    LobbyManager lobby() { return lobby; }
    MatchRegistry matches() { return matches; }
    Catalog catalog() { return catalog; }
    ResultRecorder recorder() { return recorder; }
    MatchHistory history() { return history; }
    ReplayStore replays() { return replays; }
    ScheduledExecutorService timers() { return timers; }
}
