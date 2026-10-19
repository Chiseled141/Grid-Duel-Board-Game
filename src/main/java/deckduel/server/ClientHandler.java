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
    static final int HISTORY_LIMIT = 50;
    private final GameServer server;
    private final Socket socket;
    private ObjectOutputStream out;
    private volatile String username;
    private volatile boolean closed;

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
            else if (message instanceof CreateMatchRequest) createRoom();
            else if (message instanceof JoinMatchRequest request) joinRoom(request.roomCode());
            else if (message instanceof FindMatchRequest request) findMatch(request);
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
            MatchSession active = server.matches().sessionFor(username);
            if (active != null) active.attach(this);
        } catch (AuthenticationException exception) {
            send(new LoginResponse(false, exception.getMessage(), null));
        }
    }

    /** Drops rooms, queue places and match seat held by this connection's current account. */
    private void releaseAccount() {
        server.lobby().removeHostedLobby(this);
        server.lobby().dequeue(this);
        MatchSession active = server.matches().sessionFor(username);
        if (active != null) active.detach(this);
        server.registry().detach(this);
    }

    private void createRoom() {
        Entrant host = entrant(this::sendError);
        if (host == null) return;
        server.lobby().dequeue(this);
        send(new MatchCreated(server.lobby().createLobby(host)));
    }

    private void joinRoom(String roomCode) {
        Entrant joiner = entrant(this::sendError);
        if (joiner == null) return;
        String code = roomCode == null ? null : roomCode.trim().toUpperCase(Locale.ROOT);
        Entrant host = server.lobby().claim(code);
        if (host == null) { sendError("That room is no longer open."); return; }
        if (host.username().equals(username)) {
            server.lobby().restore(code, host);
            sendError("You cannot join your own room.");
            return;
        }
        if (!server.startMatch(host, joiner)) sendError("That room's host is already playing.");
    }

    private void findMatch(FindMatchRequest request) {
        if (!request.searching()) {
            server.lobby().dequeue(this);
            send(new MatchmakingStatus(false, null));
            return;
        }
        Entrant entrant = entrant(reason -> send(new MatchmakingStatus(false, reason)));
        if (entrant == null) return;
        server.lobby().removeHostedLobby(this);
        Entrant opponent = server.lobby().enqueue(entrant);
        if (opponent == null) send(new MatchmakingStatus(true, null));
        else if (!server.startMatch(opponent, entrant)) send(new MatchmakingStatus(false, "Could not start the match. Please search again."));
    }

    private void practice(boolean hard) {
        Entrant entrant = entrant(this::sendError);
        if (entrant != null && !server.startPractice(entrant, hard)) sendError(BUSY);
    }

    /** This connection as a match entrant, unless its account is already playing ({@code refuse} says why). */
    private Entrant entrant(Consumer<String> refuse) {
        if (server.matches().sessionFor(username) != null) { refuse.accept(BUSY); return null; }
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

    /** Notifies and closes the connection for duplicate login or shutdown. */
    public void kick(String reason) { send(new ErrorMessage(reason)); closeSocket(); }

    private void closeSocket() {
        try { socket.close(); }
        catch (IOException exception) { LOG.log(Level.FINE, "Closing socket", exception); }
    }

    String username() { return username; }
    boolean isClosed() { return closed; }
}
