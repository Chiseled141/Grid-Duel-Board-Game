package onitama.server;

import onitama.db.UserDao;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The authoritative Onitama server. The main accept loop runs on its own
 * thread; every accepted socket becomes a {@link ClientHandler} executed on
 * a fixed thread pool. Shared state (lobby, session registry, live matches)
 * lives in concurrent maps, while each live game is confined to its own
 * single-thread executor inside {@link MatchSession} — see the report's
 * concurrency section for the full reasoning.
 */
public final class GameServer {

    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final ServerConfig config;
    private final UserDao userDao;
    private final MatchPersistence persistence;
    private final SessionRegistry registry = new SessionRegistry();
    private final LobbyManager lobby = new LobbyManager();
    private final ExecutorService clientPool;
    private final ScheduledExecutorService timers;
    private final Map<String, MatchSession> liveMatches = new ConcurrentHashMap<>();
    private final CountDownLatch stopped = new CountDownLatch(1);

    private volatile ServerSocket serverSocket;

    /**
     * Creates the server with persistence. Call {@link #start()} to bind the
     * port and begin accepting connections, and {@link #stop()} to shut
     * everything down.
     */
    public GameServer(ServerConfig config, UserDao userDao, MatchPersistence persistence) {
        this.config = config;
        this.userDao = userDao;
        this.persistence = persistence;
        this.clientPool = Executors.newFixedThreadPool(config.clientPoolSize(), namedThreads("onitama-client-"));
        this.timers = Executors.newScheduledThreadPool(1, namedThreads("onitama-timer-"));
    }

    /** Convenience for tests: a server without persistence (log-only hook). */
    public GameServer(ServerConfig config, UserDao userDao) {
        this(config, userDao, null);
    }

    /**
     * Binds the server socket and starts the accept loop.
     *
     * @return the actual port (useful when configured with port 0 for tests)
     * @throws IOException if the port cannot be bound
     */
    public int start() throws IOException {
        serverSocket = new ServerSocket(config.port());
        int boundPort = serverSocket.getLocalPort();
        Thread acceptor = new Thread(this::acceptLoop, "onitama-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        LOG.info("Onitama server listening on port " + boundPort);
        return boundPort;
    }

    /** Blocks until {@link #stop()} has completed; used by the server main. */
    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    /**
     * Shuts the server down cleanly: stop accepting, kick all clients, abort
     * live matches, stop all executors. Safe to call from a shutdown hook.
     */
    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "closing server socket failed", e);
        }
        registry.kickAll("server is shutting down");
        liveMatches.values().forEach(MatchSession::abort);
        clientPool.shutdownNow();
        timers.shutdownNow();
        lobby.clear();
        stopped.countDown();
        LOG.info("server stopped");
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                clientPool.submit(new ClientHandler(this, socket));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    LOG.log(Level.WARNING, "accept failed", e);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Wiring used by ClientHandler and MatchSession (package-private API)
    // ------------------------------------------------------------------

    /** Registers a freshly started match so reconnects can find it by room. */
    void registerLiveMatch(MatchSession session) {
        liveMatches.put(session.roomCode(), session);
    }

    /** Removes a finished match. */
    void removeLiveMatch(String roomCode) {
        liveMatches.remove(roomCode);
    }

    /**
     * Persistence hook called once per finished match from the match
     * executor: records the match row, both players' stats (one transaction)
     * and the replay file. Without a {@link MatchPersistence} (tests) it only
     * logs.
     */
    public void onMatchFinished(MatchResult result) {
        if (persistence != null) {
            persistence.record(result);
        }
        LOG.info("match " + result.roomCode() + " finished: " + result.way()
                + ", winner=" + result.winnerUsername()
                + ", half-moves=" + result.moveCount());
    }

    ServerConfig config() {
        return config;
    }

    UserDao userDao() {
        return userDao;
    }

    SessionRegistry registry() {
        return registry;
    }

    LobbyManager lobby() {
        return lobby;
    }

    ScheduledExecutorService timers() {
        return timers;
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
