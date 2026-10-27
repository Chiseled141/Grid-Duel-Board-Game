package deckduel.server;

import deckduel.db.AuthenticationException;
import deckduel.net.*;
import java.io.*;
import java.net.Socket;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One client connection; writes are serialized through one output monitor. */
public final class ClientHandler implements Runnable {
    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    static final String BUSY = "Finish or leave your current match first.";
    /** Shown while the server prepares for a restart. */
    static final String MAINTENANCE = "The server is about to restart for maintenance. New matches cannot start right now; "
            + "matches in progress finish normally. Please try again in a few minutes.";
    static final int HISTORY_LIMIT = 50;
    private final GameServer server;
    private final Socket socket;
    private ObjectOutputStream out;
    private volatile String username;
    private volatile boolean closed;

    ClientHandler(GameServer server, Socket socket) { this.server = server; this.socket = socket; }

    @Override public void run() {
        try (socket) {
            // A silent or slow peer gets this long for its TLS handshake and hello, then is dropped.
            socket.setSoTimeout(server.config().handshakeTimeoutMillis());
            if (socket instanceof javax.net.ssl.SSLSocket secure) secure.startHandshake();
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
            UpdateRequired outdated = Compatibility.check(hello.clientVersion(), server.config().minimumClientVersion());
            if (outdated != null) {
                // Refused before any account details are exchanged.
                LOG.info(() -> "refused client version " + hello.clientVersion() + "; minimum " + outdated.minimumVersion());
                send(outdated);
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
            // Mark closed before looking up the match, so a match paired concurrently sees one or the other.
            closed = true;
            releaseAccount();
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
            else if (message instanceof ListMatchesRequest) send(new ListMatchesResponse(server.lobby().openMatches()));
            else if (message instanceof HistoryRequest) send(new HistoryResponse(server.history().historyFor(username, HISTORY_LIMIT)));
            // Owner-only: the store looks the replay up together with this account's name.
            else if (message instanceof ReplayRequest request) send(server.replays().replay(username, request.replayId()));
            else if (message instanceof CreateMatchRequest) createRoom();
            else if (message instanceof CancelRoomRequest) {
                server.lobby().removeHostedLobby(this);
                send(new ListMatchesResponse(server.lobby().openMatches()));
            }
            else if (message instanceof JoinMatchRequest request) joinRoom(request.roomCode());
            else if (message instanceof PracticeRequest request) practice(request.hard());
            else if (message instanceof MoveRequest request) inMatch(session -> session.move(this, request));
            else if (message instanceof RematchRequest) inMatch(session -> session.rematch(this));
            else if (message instanceof LeaveMatchRequest) inMatch(session -> session.leave(this));
            else send(new ErrorMessage("Unexpected message: " + message.getClass().getSimpleName()));
        } catch (Exception exception) {
            // Never log request records: authentication messages contain passwords.
            LOG.log(Level.WARNING, "Request failed", exception);
            send(new ErrorMessage("Unable to complete the request."));
        }
    }

    private void authenticate(String name, String password, boolean registering) {
        try {
            UserProfile profile = registering ? server.userDao().register(name, password) : server.userDao().login(name, password);
            releaseAccount();
            username = profile.username();
            server.registry().attach(this);
            send(new LoginResponse(true, null, profile));
            if (server.draining()) send(new MaintenanceNotice(true, MAINTENANCE));
            MatchSession active = server.matches().sessionFor(username);
            if (active != null) active.attach(this);
        } catch (AuthenticationException exception) {
            send(new LoginResponse(false, exception.getMessage(), null));
        }
    }

    /** Drops rooms and match seat held by this connection's current account. */
    private void releaseAccount() {
        server.lobby().removeHostedLobby(this);
        MatchSession active = server.matches().sessionFor(username);
        if (active != null) active.detach(this);
        server.registry().detach(this);
    }

    private void createRoom() {
        Entrant host = entrant();
        if (host == null) return;
        String code = server.lobby().createLobby(host);
        if (code == null) sendError(MAINTENANCE);
        else send(new MatchCreated(code));
    }

    private void joinRoom(String roomCode) {
        Entrant joiner = entrant();
        if (joiner == null) return;
        if (server.draining()) { sendError(MAINTENANCE); return; }
        String code = roomCode == null ? null : roomCode.trim().toUpperCase(Locale.ROOT);
        Entrant host = server.lobby().claim(code);
        if (host == null) { sendError("That room is no longer open."); return; }
        if (host.username().equals(username)) {
            server.lobby().restore(code, host);
            sendError("You cannot join your own room.");
            return;
        }
        if (!server.startMatch(host, joiner)) sendError(server.draining() ? MAINTENANCE : "That room's host is already playing.");
    }

    private void practice(boolean hard) {
        Entrant entrant = entrant();
        if (entrant == null) return;
        if (server.draining()) { sendError(MAINTENANCE); return; }
        if (!server.startPractice(entrant, hard)) sendError(BUSY);
    }

    /** This connection as a match entrant, unless its account is already playing (it is told why). */
    private Entrant entrant() {
        if (server.matches().sessionFor(username) != null) { sendError(BUSY); return null; }
        return new Entrant(this);
    }

    private void inMatch(Consumer<MatchSession> action) {
        MatchSession session = server.matches().sessionFor(username);
        if (session == null) sendError("You are not in a match.");
        else action.accept(session);
    }

    private void sendError(String text) { send(new ErrorMessage(text)); }

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

    /** Tells a signed-in player that maintenance started or ended. */
    void notifyMaintenance(boolean active) {
        if (username != null) send(new MaintenanceNotice(active, active ? MAINTENANCE : "Maintenance is over; new matches can start again."));
    }

    /** Notifies and closes the connection for duplicate login or shutdown. */
    public void kick(String reason) { send(new ErrorMessage(reason)); closeSocket(); }

    private void closeSocket() {
        try { socket.close(); }
        catch (IOException exception) { LOG.log(Level.FINE, "Closing socket", exception); }
    }

    String username() { return username; }
    boolean isClosed() { return closed; }
}
