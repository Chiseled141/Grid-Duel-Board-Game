package deckduel.server;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.Catalog;
import deckduel.db.UserDao;
import java.io.IOException;
import java.net.InetAddress;
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

/** Local server for accounts, hosted rooms, practice and authoritative card matches. */
public final class GameServer {
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());
    private final ServerConfig config;
    private final UserDao userDao;
    private final Catalog catalog;
    private final ResultRecorder recorder;
    private final MatchHistory history;
    private final SessionRegistry registry = new SessionRegistry();
    private final LobbyManager lobby = new LobbyManager();
    private final MatchRegistry matches = new MatchRegistry();
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService clients;
    private final ScheduledExecutorService timers;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile ServerSocket listener;

    /** Creates a server with the bundled card catalog that does not record match history. */
    public GameServer(ServerConfig config, UserDao userDao) {
        this(config, userDao, CatalogLoader.loadBundled(), ResultRecorder.NONE, MatchHistory.NONE);
    }

    /** Creates a server with an immutable card catalog shared by every match, a result recorder and history reader. */
    public GameServer(ServerConfig config, UserDao userDao, Catalog catalog, ResultRecorder recorder, MatchHistory history) {
        this.config = config;
        this.userDao = userDao;
        this.catalog = catalog;
        this.recorder = recorder;
        this.history = history;
        clients = Executors.newFixedThreadPool(config.clientPoolSize(), task -> daemon(task, "deckduel-client"));
        // Timers only post work onto match executors; they never touch match state themselves.
        timers = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "deckduel-match-timers"));
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Binds loopback only and returns the actual port. */
    public int start() throws IOException {
        listener = new ServerSocket(config.port(), 50, InetAddress.getLoopbackAddress());
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
        if (!matches.bind(session, session.humanUsernames())) { session.close(); return false; }
        for (Entrant entrant : entrants) lobby.removeHostedLobby(entrant.handler());
        session.start();
        return true;
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
    ScheduledExecutorService timers() { return timers; }
}
