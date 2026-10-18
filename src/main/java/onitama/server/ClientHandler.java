package onitama.server;

import onitama.core.PlayerColor;
import onitama.db.AuthenticationException;
import onitama.net.ActionRequest;
import onitama.net.ChatRequest;
import onitama.net.CreateMatchRequest;
import onitama.net.FindMatchRequest;
import onitama.net.MatchmakingStatus;
import onitama.net.ErrorMessage;
import onitama.net.JoinMatchRequest;
import onitama.net.LeaderboardRequest;
import onitama.net.LeaderboardResponse;
import onitama.net.ListMatchesRequest;
import onitama.net.ListMatchesResponse;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.Message;
import onitama.net.Ping;
import onitama.net.Pong;
import onitama.net.ProtocolHello;
import onitama.net.ProtocolReady;
import onitama.net.ReconnectRequest;
import onitama.net.RegisterRequest;
import onitama.net.RematchRequest;
import onitama.net.UserProfile;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
            Map.entry(FindMatchRequest.class, m -> handleFindMatch((FindMatchRequest) m)),
            Map.entry(CreateMatchRequest.class, m -> handleCreateMatch((CreateMatchRequest) m)),
            Map.entry(JoinMatchRequest.class, m -> handleJoinMatch((JoinMatchRequest) m)),
            Map.entry(ListMatchesRequest.class, m -> handleListMatches((ListMatchesRequest) m)),
            Map.entry(ActionRequest.class, m -> handleAction((ActionRequest) m)),
            Map.entry(ChatRequest.class, m -> handleChat((ChatRequest) m)),
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
            // Handshake deadline is 5 seconds.
            socket.setSoTimeout(5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Message.WIRE_FILTER);

            // Step 3 §8: Server sends greeting first
            send(new ErrorMessage("GRID_DUEL_PROTOCOL:6"));
            Object firstRaw;
            try {
                firstRaw = in.readObject();
            } catch (SocketTimeoutException e) {
                LOG.info(() -> describe() + " timed out during protocol handshake");
                send(new ErrorMessage("handshake timeout: GRID_DUEL_PROTOCOL:6 required"));
                return;
            }
            if (!(firstRaw instanceof ProtocolHello hello)) {
                LOG.warning(() -> describe() + " sent pre-handshake message: "
                        + (firstRaw == null ? "null" : firstRaw.getClass().getSimpleName()));
                send(new ErrorMessage("protocol handshake required: GRID_DUEL_PROTOCOL:6"));
                return;
            }
            if (hello.version() != 6) {
                LOG.warning(() -> describe() + " unsupported protocol version: " + hello.version());
                send(new ErrorMessage("incompatible protocol version: expected 6, got " + hello.version()));
                return;
            }
            send(new ProtocolReady(6));

            // Restore standard silence limit after handshake
            socket.setSoTimeout(server.config().silenceSeconds() * 1000);
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
            // A dropped or corrupt stream is a connection event, not a
            // protocol violation: LOG.warning stays reserved for the
            // non-message-object case above.
            LOG.log(Level.FINE, e, () -> describe() + " connection error");
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
        cancelSearchBeforeAuthentication();
        try {
            UserProfile profile = server.userDao().register(request.username(), request.password());
            completeLogin(profile);
        } catch (AuthenticationException e) {
            send(new LoginResponse(false, e.getMessage(), null, null));
        }
    }

    private void handleLogin(LoginRequest request) {
        cancelSearchBeforeAuthentication();
        try {
            UserProfile profile = server.userDao().login(request.username(), request.password());
            completeLogin(profile);
        } catch (AuthenticationException e) {
            send(new LoginResponse(false, e.getMessage(), null, null));
        }
    }

    private void cancelSearchBeforeAuthentication() {
        boolean searching;
        synchronized (server.lobby()) {
            searching = server.lobby().isSearching(this);
            server.lobby().cancelSearch(this);
        }
        if (searching) send(new MatchmakingStatus(false, null));
    }

    private void completeLogin(UserProfile profile) {
        synchronized (server.lobby()) {
            server.lobby().cancelSearch(this);
            server.lobby().removeHostedLobby(this);
            // A room join may have reserved this handler before authentication began.
            // Notify that session so its peer receives a pause/forfeit, not a silent orphan.
            if (match != null) match.onDisconnect(this);
            if (username != null) server.registry().detach(this);
            if (reconnectToken != null) server.registry().clearToken(reconnectToken);
            username = profile.username();
            match = null;
            color = null;
        }
        reconnectToken = server.registry().attach(this);
        send(new LoginResponse(true, null, profile, reconnectToken));
        LOG.info(() -> "user '" + username + "' logged in");
    }

    private void handleCreateMatch(CreateMatchRequest request) {
        String error;
        String code = null;
        synchronized (server.lobby()) {
            error = lobbyAdmissionError();
            if (error == null) code = server.lobby().createLobby(this);
        }
        send(error == null ? new MatchCreated(code) : new ErrorMessage(error));
    }

    private void handleJoinMatch(JoinMatchRequest request) {
        String error;
        MatchSession session = null;
        synchronized (server.lobby()) {
            error = lobbyAdmissionError();
            if (error == null) {
                String code = request.roomCode() == null ? "" : request.roomCode().trim().toUpperCase(java.util.Locale.ROOT);
                ClientHandler host = server.lobby().takeLobby(code, this);
                if (host == null) error = "room not found: " + code;
                else {
                    server.lobby().removeHostedLobby(this);
                    session = prepareMatch(code, host);
                }
            }
        }
        if (session != null) session.start();
        else send(new ErrorMessage(error));
    }

    private void handleFindMatch(FindMatchRequest request) {
        MatchmakingStatus status;
        MatchSession session = null;
        ClientHandler opponent = null;
        synchronized (server.lobby()) {
            if (username == null) {
                status = new MatchmakingStatus(false, "Please log in first.");
            } else if (!request.searching()) {
                server.lobby().cancelSearch(this);
                status = new MatchmakingStatus(false, null);
            } else if (!availableForMatchmaking() || match != null && match.isActive()) {
                status = new MatchmakingStatus(false, "You are already in a match or disconnected.");
            } else {
                // Leaving a finished game also withdraws any old rematch invitation.
                if (match != null) match.onDisconnect(this);
                match = null;
                color = null;
                opponent = server.lobby().findOpponent(this);
                if (opponent == null) status = new MatchmakingStatus(true, null);
                else {
                    String code = server.lobby().createLobby(opponent);
                    server.lobby().takeLobby(code, this);
                    session = prepareMatch(code, opponent);
                    status = new MatchmakingStatus(false, null);
                }
            }
        }
        // A slow socket must not hold the shared lobby admission monitor.
        send(status);
        if (session != null) {
            opponent.send(new MatchmakingStatus(false, null));
            session.start();
        }
    }

    private MatchSession prepareMatch(String roomCode, ClientHandler host) {
        MatchSession session = new MatchSession(server, roomCode, host, this);
        server.registerLiveMatch(session);
        return session;
    }

    private String lobbyAdmissionError() {
        if (username == null) return "please log in first";
        if (match != null && match.isActive()) return "you are already in a match";
        if (server.lobby().isSearching(this)) return "Cancel Find match before opening another game.";
        return null;
    }

    private void handleListMatches(ListMatchesRequest request) {
        send(new ListMatchesResponse(server.lobby().openMatches()));
    }

    private void handleAction(ActionRequest request) {
        MatchSession session = match;
        if (session == null) {
            send(new ErrorMessage("you are not in a match"));
            return;
        }
        session.submitAction(this, request);
    }

    private void handleChat(ChatRequest request) {
        if (notAuthenticated()) return;
        MatchSession session = match;
        if (session == null) {
            send(new ErrorMessage("Chat is only available in a live match."));
            return;
        }
        session.submitChat(this, request);
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
        // Snapshot identity and match only after removing waiting entries. Pairing
        // either reserves the match first, or sees the old connection closed.
        synchronized (server.lobby()) {
            server.lobby().cancelSearch(this);
            server.lobby().cancelSearch(previous);
            server.lobby().removeHostedLobby(previous);
            username = previous.username();
            reconnectToken = token;
            match = previous.match;
            color = previous.color;
            previous.closeSocket();
        }
        server.registry().attach(this);
        server.registry().rebind(token, this);

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
     * sent twice (e.g. a shared rules snapshot in successive MatchSync views) would
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
        MatchSession session;
        synchronized (server.lobby()) {
            server.lobby().cancelSearch(this);
            server.lobby().removeHostedLobby(this);
            session = match;
        }
        if (session != null) session.onDisconnect(this);
        server.registry().detach(this);
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

    private String describe() {
        return username == null ? "unauthenticated " + socket.getRemoteSocketAddress()
                : "user '" + username + "'";
    }

    // Package-private state accessors used by the registries and match code.

    boolean availableForMatchmaking() {
        return username != null && !socket.isClosed();
    }

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
