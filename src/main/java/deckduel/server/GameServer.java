package deckduel.server;

import deckduel.db.UserDao;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Local account/lobby server; no gameplay engine or active-match registry remains. */
public final class GameServer {
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());
    private final ServerConfig config;
    private final UserDao userDao;
    private final SessionRegistry registry = new SessionRegistry();
    private final LobbyManager lobby = new LobbyManager();
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService clients;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile ServerSocket listener;

    /** Creates the shell without creating or recording games. */
    public GameServer(ServerConfig config, UserDao userDao) {
        this.config = config;
        this.userDao = userDao;
        clients = Executors.newFixedThreadPool(config.clientPoolSize(), task -> {
            Thread thread = new Thread(task, "deckduel-client");
            thread.setDaemon(true);
            return thread;
        });
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

    void disconnected(ClientHandler handler) { connections.remove(handler); }
    ServerConfig config() { return config; }
    UserDao userDao() { return userDao; }
    SessionRegistry registry() { return registry; }
    LobbyManager lobby() { return lobby; }
}
