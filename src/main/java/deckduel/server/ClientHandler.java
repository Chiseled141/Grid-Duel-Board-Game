package deckduel.server;

import deckduel.db.AuthenticationException;
import deckduel.net.*;
import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Account/lobby connection; writes are serialized through one output monitor. */
public final class ClientHandler implements Runnable {
    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    static final String GAMEPLAY_UNAVAILABLE = "The new card gameplay is not ready yet.";
    private final GameServer server;
    private final Socket socket;
    private ObjectOutputStream out;
    private volatile String username;

    ClientHandler(GameServer server, Socket socket) { this.server = server; this.socket = socket; }

    @Override public void run() {
        try (socket) {
            socket.setSoTimeout(5000);
            synchronized (this) {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
            }
            var in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Message.WIRE_FILTER);
            send(new ErrorMessage(Message.GREETING));
            Object first = in.readObject();
            if (!(first instanceof ProtocolHello hello) || hello.version() != Message.PROTOCOL_VERSION) {
                send(new ErrorMessage("Incompatible protocol. Expected " + Message.GREETING));
                return;
            }
            send(new ProtocolReady(Message.PROTOCOL_VERSION));
            socket.setSoTimeout(server.config().silenceSeconds() * 1000);
            while (!socket.isClosed()) {
                Object raw = in.readObject();
                if (!(raw instanceof Message message)) {
                    send(new ErrorMessage("Protocol violation: expected a message."));
                    break;
                }
                dispatch(message);
            }
        } catch (IOException | ClassNotFoundException exception) {
            LOG.log(Level.FINE, "Connection closed", exception);
        } finally {
            server.lobby().removeHostedLobby(this);
            server.registry().detach(this);
            server.disconnected(this);
        }
    }

    private void dispatch(Message message) {
        try {
            if (message instanceof Ping ping) send(new Pong(ping.timestamp()));
            else if (message instanceof Pong) { /* A heartbeat response requires no response. */ }
            else if (message instanceof RegisterRequest request) authenticate(request.username(), request.password(), true);
            else if (message instanceof LoginRequest request) authenticate(request.username(), request.password(), false);
            else if (username == null) send(new ErrorMessage("Please log in first."));
            else if (message instanceof CreateMatchRequest) send(new MatchCreated(server.lobby().createLobby(this)));
            else if (message instanceof ListMatchesRequest) send(new ListMatchesResponse(server.lobby().openMatches()));
            else if (message instanceof LeaderboardRequest) send(new LeaderboardResponse(server.userDao().leaderboard(20)));
            else if (message instanceof JoinMatchRequest) send(new ErrorMessage(GAMEPLAY_UNAVAILABLE));
            else if (message instanceof FindMatchRequest request)
                send(new MatchmakingStatus(false, request.searching() ? GAMEPLAY_UNAVAILABLE : null));
            else send(new ErrorMessage("Unexpected message: " + message.getClass().getSimpleName()));
        } catch (Exception exception) {
            // Never log request records: authentication messages contain passwords.
            LOG.log(Level.WARNING, "Account/lobby request failed", exception);
            send(new ErrorMessage("Unable to complete the request."));
        }
    }

    private void authenticate(String name, String password, boolean registering) {
        try {
            UserProfile profile = registering ? server.userDao().register(name, password) : server.userDao().login(name, password);
            server.lobby().removeHostedLobby(this);
            server.registry().detach(this);
            username = profile.username();
            server.registry().attach(this);
            send(new LoginResponse(true, null, profile));
        } catch (AuthenticationException exception) {
            send(new LoginResponse(false, exception.getMessage(), null));
        }
    }

    /** Writes one full message; other threads never touch the stream directly. */
    public synchronized void send(Message message) {
        try {
            if (out != null) {
                out.writeObject(message);
                out.flush();
                out.reset();
            }
        } catch (IOException exception) { closeSocket(); }
    }

    /** Notifies and closes the connection for duplicate login or shutdown. */
    public void kick(String reason) { send(new ErrorMessage(reason)); closeSocket(); }

    private void closeSocket() {
        try { socket.close(); }
        catch (IOException exception) { LOG.log(Level.FINE, "Closing socket", exception); }
    }

    String username() { return username; }
}
