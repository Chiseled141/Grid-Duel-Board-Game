package onitama.server;

import onitama.core.PlayerColor;
import onitama.db.AuthenticationException;
import onitama.db.UserDao;
import onitama.net.CreateMatchRequest;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.JoinMatchRequest;
import onitama.net.LeaderboardRequest;
import onitama.net.LeaderboardResponse;
import onitama.net.ListMatchesRequest;
import onitama.net.ListMatchesResponse;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchStart;
import onitama.net.Message;
import onitama.net.MoveRequest;
import onitama.net.MoveRejected;
import onitama.net.PassTurn;
import onitama.net.Ping;
import onitama.net.Pong;
import onitama.net.ReconnectRequest;
import onitama.net.RegisterRequest;
import onitama.net.RematchRequest;
import onitama.net.ResignRequest;
import onitama.net.UserProfile;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One connected client: a blocking read loop plus the per-message dispatch
 * table. All state (identity, current match) lives here and is only written
 * by this handler's own thread, the reconnect path or a fully synchronized
 * method — never arbitrarily by other handlers.
 *
 * <p>Writes to the socket go through {@link #send(Message)}, which is
 * synchronized: the handler thread itself, match executors and the shutdown
 * path all funnel through one monitor, so the single ObjectOutputStream is
 * never corrupted (single-writer-by-monitor rule).
 */
public final class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final GameServer server;
    private final Socket socket;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile String username;
    private volatile String reconnectToken;
    private volatile MatchSession match;
    private volatile PlayerColor color;

    /** Dispatch table: message class → handler (Visitor-lite via functional registry). */
    private final Map<Class<?>, Consumer<Message>> handlers = Map.ofEntries(
            Map.entry(RegisterRequest.class, m -> handleRegister((RegisterRequest) m)),
            Map.entry(LoginRequest.class, m -> handleLogin((LoginRequest) m)),
            Map.entry(CreateMatchRequest.class, m -> handleCreateMatch((CreateMatchRequest) m)),
            Map.entry(JoinMatchRequest.class, m -> handleJoinMatch((JoinMatchRequest) m)),
            Map.entry(ListMatchesRequest.class, m -> handleListMatches((ListMatchesRequest) m)),
            Map.entry(MoveRequest.class, m -> handleMove((MoveRequest) m)),
            Map.entry(PassTurn.class, m -> handlePass((PassTurn) m)),
            Map.entry(ResignRequest.class, m -> handleResign((ResignRequest) m)),
            Map.entry(RematchRequest.class, m -> handleRematch((RematchRequest) m)),
            Map.entry(ReconnectRequest.class, m -> handleReconnect((ReconnectRequest) m)),
            Map.entry(LeaderboardRequest.class, m -> handleLeaderboard((LeaderboardRequest) m)),
            Map.entry(Ping.class, m -> send(new Pong(((Ping) m).timestamp()))));

    ClientHandler(GameServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket) {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Object raw = in.readObject();
                if (!(raw instanceof Message message)) {
                    LOG.warning(() -> describe() + " sent a non-message object: "
                            + raw.getClass().getName());
                    send(new ErrorMessage("protocol violation: not a message"));
                    break;
                }
                dispatch(message);
            }
        } catch (EOFException | SocketException e) {
            LOG.fine(() -> describe() + " disconnected");
        } catch (IOException | ClassNotFoundException e) {
            LOG.log(Level.WARNING, () -> describe() + " connection error: " + e);
        } finally {
            onDisconnected();
        }
    }

    private void dispatch(Message message) {
        Consumer<Message> handler = handlers.get(message.getClass());
        if (handler == null) {
            send(new ErrorMessage("unexpected message type: "
                    + message.getClass().getSimpleName()));
            return;
        }
        try {
            handler.accept(message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> describe() + " handler failed on " + message);
            send(new ErrorMessage("internal server error"));
        }
    }

    // ------------------------------------------------------------------
    // Message handlers. Each receives one message type and answers with the
    // protocol response; nothing here blocks for long — match work is
    // submitted to the match's own executor.
    // ------------------------------------------------------------------

    private void handleRegister(RegisterRequest request) {
        try {
            UserProfile profile = server.userDao().register(request.username(), request.password());
            completeLogin(profile);
        } catch (AuthenticationException e) {
            send(new LoginResponse(false, e.getMessage(), null, null));
        }
    }

    private void handleLogin(LoginRequest request) {
        try {
            UserProfile profile = server.userDao().login(request.username(), request.password());
            completeLogin(profile);
        } catch (AuthenticationException e) {
            send(new LoginResponse(false, e.getMessage(), null, null));
        }
    }

    private void completeLogin(UserProfile profile) {
        username = profile.username();
        match = null;
        color = null;
        reconnectToken = server.registry().attach(this);
        send(new LoginResponse(true, null, profile, reconnectToken));
        LOG.info(() -> "user '" + username + "' logged in");
    }

    private void handleCreateMatch(CreateMatchRequest request) {
        if (notAuthenticated() || busyInMatch()) {
            return;
        }
        String roomCode = server.lobby().createLobby(this);
        send(new MatchCreated(roomCode));
    }

    private void handleJoinMatch(JoinMatchRequest request) {
        if (notAuthenticated() || busyInMatch()) {
            return;
        }
        String roomCode = request.roomCode() == null ? "" : request.roomCode().trim().toUpperCase();
        ClientHandler host = server.lobby().takeLobby(roomCode, this);
        if (host == null) {
            send(new ErrorMessage("room not found: " + roomCode));
            return;
        }
        MatchSession session = new MatchSession(server, roomCode, host, this);
        server.registerLiveMatch(session);
        session.start();
    }

    private void handleListMatches(ListMatchesRequest request) {
        send(new ListMatchesResponse(server.lobby().openMatches()));
    }

    private void handleMove(MoveRequest request) {
        MatchSession session = match;
        if (session == null) {
            send(new MoveRejected("you are not in a match"));
            return;
        }
        session.submitMove(this, request);
    }

    private void handlePass(PassTurn request) {
        MatchSession session = match;
        if (session == null) {
            send(new MoveRejected("you are not in a match"));
            return;
        }
        session.submitPass(this, request.cardIdToDiscard());
    }

    private void handleResign(ResignRequest request) {
        MatchSession session = match;
        if (session != null) {
            session.submitResign(this);
        }
    }

    private void handleRematch(RematchRequest request) {
        MatchSession session = match;
        if (session != null) {
            session.submitRematch(this);
        }
    }

    private void handleReconnect(ReconnectRequest request) {
        String token = request.reconnectToken();
        ClientHandler previous = server.registry().byToken(token);
        if (previous == null || previous.username() == null) {
            send(new ErrorMessage("unknown or expired reconnect token"));
            return;
        }
        // Adopt the identity and, if one exists, the parked match of the old handler.
        username = previous.username();
        reconnectToken = token;
        match = previous.match;
        color = previous.color;
        server.registry().rebind(token, this);
        previous.closeSocket();

        MatchSession session = match;
        if (session != null && color != null) {
            session.onReconnect(this, color);
        } else {
            // The old session is gone (e.g. match ended): land in the lobby.
            try {
                send(new LoginResponse(true, null, server.userDao().profileOf(username), token));
            } catch (AuthenticationException e) {
                send(new ErrorMessage("account no longer exists"));
            }
        }
        LOG.info(() -> "user '" + username + "' reconnected");
    }

    private void handleLeaderboard(LeaderboardRequest request) {
        List<UserProfile> top = server.userDao().leaderboard(20);
        send(new LeaderboardResponse(top));
    }

    // ------------------------------------------------------------------
    // Connection lifecycle
    // ------------------------------------------------------------------

    /**
     * Sends one message; the only place that writes to the socket.
     * {@code out.reset()} clears the stream's object-handle table so every
     * message is serialized as a full snapshot — without it, a mutable object
     * sent twice (e.g. the live GameState in MatchStart and MoveApplied) would
     * be written as a back-reference and the receiver would keep the stale
     * first copy forever.
     */
    public synchronized void send(Message message) {
        try {
            if (out != null) {
                out.reset();
                out.writeObject(message);
                out.flush();
            }
        } catch (IOException e) {
            LOG.fine(() -> describe() + " is unreachable: " + e);
        }
    }

    /** Tells the client it is being disconnected, then closes the socket. */
    public void kick(String reason) {
        send(new ErrorMessage(reason));
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.fine(() -> describe() + " socket already closed");
        }
    }

    private void onDisconnected() {
        MatchSession session = match;
        if (session != null) {
            session.onDisconnect(this);
        }
        server.registry().detach(this);
        server.lobby().removeHostedLobby(this);
        if (username != null) {
            LOG.info(() -> "user '" + username + "' disconnected");
        }
    }

    private boolean notAuthenticated() {
        if (username == null) {
            send(new ErrorMessage("please log in first"));
            return true;
        }
        return false;
    }

    private boolean busyInMatch() {
        if (match != null && match.isActive()) {
            send(new ErrorMessage("you are already in a match"));
            return true;
        }
        return false;
    }

    private String describe() {
        return username == null ? "unauthenticated " + socket.getRemoteSocketAddress()
                : "user '" + username + "'";
    }

    // Package-private state accessors used by the registries and match code.

    String username() {
        return username;
    }

    String reconnectToken() {
        return reconnectToken;
    }

    MatchSession currentMatch() {
        return match;
    }

    void joinMatch(MatchSession session, PlayerColor assignedColor) {
        this.match = session;
        this.color = assignedColor;
    }
}
