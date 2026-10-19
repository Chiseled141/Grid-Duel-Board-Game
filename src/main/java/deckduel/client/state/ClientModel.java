package deckduel.client.state;

import deckduel.core.card.MatchPhase;
import deckduel.core.card.Move;
import deckduel.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * EDT-owned account, lobby and match state. The server is authoritative: the model only stores
 * the latest {@link MatchUpdate} and sends requests, never applying a move locally.
 */
public final class ClientModel {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ClientModel.class.getName());
    /** Reconnect attempts after losing the connection during a live match. */
    static final int RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MILLIS = 2000;

    private record Credentials(String host, int port, String username, String password) { }

    private final ServerConnection connection;
    private final List<ClientModelListener> listeners = new ArrayList<>();
    private Screen screen = Screen.LOGIN;
    private UserProfile me;
    private List<MatchSummary> openMatches = List.of();
    private List<HistoryEntry> history = List.of();
    private String pendingRoomCode;
    private boolean findingMatch, reducedMotion, reconnecting;
    private MatchUpdate match;
    private String leftMatchId;
    private long nextRequestId = 1;
    // One move per shown view: further clicks wait for the server's answer.
    private boolean awaitingAnswer;
    // Kept in memory only, so a dropped connection can log back in to the held seat.
    private Credentials credentials;

    /** Uses an asynchronous connection boundary; panels never touch sockets. */
    public ClientModel(ServerConnection connection) { this.connection = Objects.requireNonNull(connection); }
    /** Adds an observer of client state. */
    public void addListener(ClientModelListener listener) { listeners.add(listener); }
    /** Connects off the EDT and requests login. */
    public void login(String host, int port, String username, String password) {
        credentials = new Credentials(host, port, username, password);
        connectThenSend(host, port, new LoginRequest(username, password));
    }
    /** Connects off the EDT and requests registration. */
    public void register(String host, int port, String username, String password) {
        credentials = new Credentials(host, port, username, password);
        connectThenSend(host, port, new RegisterRequest(username, password));
    }
    private void connectThenSend(String host, int port, Message request) {
        if (connection.isConnected()) { connection.send(request); return; }
        Thread worker = new Thread(() -> {
            try { connection.ensureConnected(host, port); connection.send(request); }
            catch (IOException exception) {
                fire(listener -> listener.onLoginFailed("Cannot reach server " + host + ":" + port));
            }
        }, "deckduel-connect");
        worker.setDaemon(true);
        worker.start();
    }
    /** Requests match search with an automatic server deck. */
    public void findMatch() {
        if (findingMatch) return;
        if (me == null || screen != Screen.LOBBY || !connection.isConnected()) {
            fire(listener -> listener.onError("Connect to the server first."));
            return;
        }
        findingMatch = true;
        fire(ClientModelListener::onLobbyChanged);
        connection.send(new FindMatchRequest(true));
    }
    /** Waits for authoritative cancellation rather than assuming it succeeded. */
    public void cancelFindMatch() { if (findingMatch) connection.send(new FindMatchRequest(false)); }
    /** Requests a hosted room with an automatic server deck. */
    public void createMatch() { connection.send(new CreateMatchRequest()); }
    /** Requests admission to a hosted room. */
    public void joinMatch(String code) { connection.send(new JoinMatchRequest(code)); }
    /** Starts a practice match against the server's easy or hard practice bot. */
    public void startPractice(boolean hard) { connection.send(new PracticeRequest(hard)); }
    /** Refreshes the room directory. */
    public void refreshMatches() { connection.send(new ListMatchesRequest()); }
    /** Opens the offline region atlas. */
    public void openAtlas() { setScreen(Screen.ATLAS); }
    /** Returns to the main menu. */
    public void leaveToLobby() { setScreen(Screen.LOBBY); }
    /** Opens match history and requests the account's recorded matches. */
    public void requestHistory() {
        setScreen(Screen.HISTORY);
        connection.send(new HistoryRequest());
    }

    /** Sends one move (Landmark choice, card play, manual draw, keep or skip) against the revision currently shown. */
    public void move(Move move) {
        if (match == null || reconnecting || awaitingAnswer) return;
        if (!match.view().choosingLandmarks() && !match.view().yourTurn()) return;
        awaitingAnswer = true;
        connection.send(new MoveRequest(nextRequestId++, match.view().revision(), move));
    }
    /** Asks for a rematch after a finished match. */
    public void requestRematch() { if (match != null) connection.send(new RematchRequest()); }
    /** Leaves the match (forfeiting it if unfinished) and returns to the menu. */
    public void leaveMatch() {
        if (match == null) { leaveToLobby(); return; }
        leftMatchId = match.matchId();
        connection.send(new LeaveMatchRequest());
        match = null;
        setScreen(Screen.LOBBY);
    }

    /** Applies a server message; callers hand off socket messages to the EDT. */
    public void handleMessage(Message message) {
        if (message instanceof LoginResponse response) {
            if (!response.ok()) { fire(listener -> listener.onLoginFailed(response.errorText())); return; }
            me = response.profile();
            pendingRoomCode = null;
            findingMatch = false;
            if (reconnecting) {
                // The server follows with the held match view; stay on the table meanwhile.
                reconnecting = false;
                fire(ClientModelListener::onMatchChanged);
                return;
            }
            setScreen(Screen.LOBBY);
            refreshMatches();
        } else if (message instanceof MatchUpdate update) {
            acceptMatch(update);
        } else if (message instanceof ActionRejected rejected) {
            awaitingAnswer = false;
            LOG.info(() -> "Move rejected: " + rejected.reason());
            fire(listener -> listener.onActionRejected(rejected.reason()));
        } else if (message instanceof ProfileUpdate update) {
            me = update.profile();
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof MatchCreated created) {
            pendingRoomCode = created.roomCode();
            fire(listener -> listener.onMatchCreated(created.roomCode()));
        } else if (message instanceof ListMatchesResponse response) {
            openMatches = List.copyOf(response.openMatches());
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof HistoryResponse response) {
            history = List.copyOf(response.matches());
            fire(ClientModelListener::onHistoryChanged);
        } else if (message instanceof MatchmakingStatus status) {
            findingMatch = status.searching() && screen == Screen.LOBBY;
            fire(ClientModelListener::onLobbyChanged);
            if (status.error() != null) fire(listener -> listener.onError(status.error()));
        } else if (message instanceof ErrorMessage error) {
            fire(listener -> listener.onError(error.text()));
        } else if (message instanceof Ping ping) connection.send(new Pong(ping.timestamp()));
    }

    private void acceptMatch(MatchUpdate update) {
        // A leaver's last in-flight update must not pull them back to the table.
        if (update.matchId().equals(leftMatchId)) return;
        match = update;
        awaitingAnswer = false;
        findingMatch = false;
        pendingRoomCode = null;
        if (screen != Screen.TABLE) setScreen(Screen.TABLE);
        fire(ClientModelListener::onMatchChanged);
    }

    /** Reconnects during a live match; otherwise clears identity and returns to login. */
    public void handleConnectionLost() {
        if (match != null && match.view().phase() == MatchPhase.IN_PROGRESS && credentials != null && !reconnecting) {
            reconnecting = true;
            fire(ClientModelListener::onMatchChanged);
            reconnect(credentials);
            return;
        }
        endSession();
    }

    private void endSession() {
        reconnecting = false;
        me = null; pendingRoomCode = null; findingMatch = false; match = null;
        openMatches = List.of(); history = List.of();
        setScreen(Screen.LOGIN);
        fire(ClientModelListener::onConnectionLost);
    }

    private void reconnect(Credentials login) {
        Thread worker = new Thread(() -> {
            for (int attempt = 0; attempt < RECONNECT_ATTEMPTS; attempt++) {
                try {
                    if (attempt > 0) Thread.sleep(RECONNECT_DELAY_MILLIS);
                    connection.close();
                    connection.ensureConnected(login.host(), login.port());
                    connection.send(new LoginRequest(login.username(), login.password()));
                    return;
                } catch (IOException retry) {
                    // The server may be briefly unreachable; try again after the delay.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            SwingUtilities.invokeLater(this::endSession);
        }, "deckduel-reconnect");
        worker.setDaemon(true);
        worker.start();
    }

    private void setScreen(Screen next) { screen = next; fire(listener -> listener.onScreenChanged(next)); }
    private void fire(Consumer<ClientModelListener> event) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> fire(event));
            return;
        }
        List.copyOf(listeners).forEach(event);
    }
    /** Current page. */
    public Screen screen() { return screen; }
    /** Authenticated profile, or null before login. */
    public UserProfile me() { return me; }
    /** Last room listing received from the server. */
    public List<MatchSummary> openMatches() { return openMatches; }
    /** Last match history received from the server, newest first. */
    public List<HistoryEntry> history() { return history; }
    /** Room hosted by this connection, if any. */
    public String pendingRoomCode() { return pendingRoomCode; }
    /** Whether a search response is pending. */
    public boolean isFindingMatch() { return findingMatch; }
    /** The latest authoritative match update, or null outside a match. */
    public MatchUpdate match() { return match; }
    /** Whether the connection dropped mid-match and a reconnect is in progress. */
    public boolean isReconnecting() { return reconnecting; }
    /** Presentation preference shared by the pages. */
    public boolean reducedMotion() { return reducedMotion; }
    /** Updates motion preferences and refreshes visible observers. */
    public void setReducedMotion(boolean value) { reducedMotion = value; fire(ClientModelListener::onLobbyChanged); }
}
