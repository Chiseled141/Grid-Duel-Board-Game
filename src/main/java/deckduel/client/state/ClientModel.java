package deckduel.client.state;

import deckduel.client.startup.ServerRoute;
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
 * the latest {@link MatchUpdate} and sends requests, never applying a move locally. A replay
 * being watched lives in its own {@link ReplayPlayback}, apart from the live match.
 */
public final class ClientModel {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ClientModel.class.getName());
    /** Reconnect attempts after losing the connection during a live match. */
    static final int RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MILLIS = 2000;

    private record Credentials(ServerRoute route, String username, String password) { }

    private final ServerConnection connection;
    private final List<ClientModelListener> listeners = new ArrayList<>();
    private Screen screen = Screen.LOGIN;
    private UserProfile me;
    private List<MatchSummary> openMatches = List.of();
    private List<HistoryEntry> history = List.of();
    private String pendingRoomCode;
    private boolean reducedMotion, reconnecting;
    private MatchUpdate match;
    private String leftMatchId;
    private long nextRequestId = 1;
    // One move per shown view: further clicks wait for the server's answer.
    private boolean awaitingAnswer;
    // Kept in memory only, so a dropped connection can log back in to the held seat.
    private Credentials credentials;
    private boolean offline;
    // Remembered for the rest of the session once found; it never interrupts a match in progress.
    private String requiredUpdate;
    // True while an update downloads, verifies or waits to restart; new matches wait for it.
    private boolean updating;
    // The server's maintenance message while it prepares for a restart, or null.
    private String maintenance;
    // Runs once no match is in progress, such as the restart that finishes an update.
    private Runnable afterMatch;
    private ReplayPlayback replay;
    private Long pendingReplay;

    /** Uses an asynchronous connection boundary; panels never touch sockets. */
    public ClientModel(ServerConnection connection) { this.connection = Objects.requireNonNull(connection); }
    /** Adds an observer of client state. */
    public void addListener(ClientModelListener listener) { listeners.add(listener); }
    /** Connects off the EDT and requests login. */
    public void login(ServerRoute route, String username, String password) {
        credentials = new Credentials(route, username, password);
        connectThenSend(route, new LoginRequest(username, password));
    }
    /** Connects off the EDT and requests registration. */
    public void register(ServerRoute route, String username, String password) {
        credentials = new Credentials(route, username, password);
        connectThenSend(route, new RegisterRequest(username, password));
    }
    private void connectThenSend(ServerRoute route, Message request) {
        if (connection.isConnected()) { connection.send(request); return; }
        Thread worker = new Thread(() -> {
            try { connection.ensureConnected(route); connection.send(request); }
            catch (IOException exception) {
                String reason = exception.getMessage() == null ? "" : ": " + exception.getMessage();
                fire(listener -> listener.onLoginFailed("Cannot reach the " + route.describe() + reason));
            }
        }, "deckduel-connect");
        worker.setDaemon(true);
        worker.start();
    }
    /** Signs in to offline play's own server, which runs inside this client. */
    public void beginOffline(ServerRoute route, String username, String password) {
        offline = true;
        register(route, username, password);
    }
    /** Requests a hosted room with an automatic server deck. */
    public void createMatch() { if (!updateBlocks()) connection.send(new CreateMatchRequest()); }
    /** Requests admission to a hosted room. */
    public void joinMatch(String code) { if (!updateBlocks()) connection.send(new JoinMatchRequest(code)); }
    /** Starts a practice match against the server's easy or hard practice bot. */
    public void startPractice(boolean hard) { if (!updateBlocks()) connection.send(new PracticeRequest(hard)); }
    /**
     * Remembers that {@code version} must be installed before another match starts. A match in
     * progress continues; only new matches and rematches are refused.
     */
    public void requireUpdate(String version) {
        if (requiredUpdate != null) return;
        requiredUpdate = Objects.requireNonNull(version);
        closeRoom();
        fire(ClientModelListener::onLobbyChanged);
    }
    /** Marks an update as installing (or not); while it is, new matches wait and a waiting room closes. */
    public void setUpdating(boolean installing) {
        if (updating == installing) return;
        updating = installing;
        if (installing) closeRoom();
        fire(ClientModelListener::onLobbyChanged);
    }
    /** Whether new matches are refused because an update is required or installing. */
    public boolean newMatchesBlocked() { return requiredUpdate != null || updating || maintenance != null; }
    /** The server's maintenance message while it prepares for a restart, or null. */
    public String maintenance() { return maintenance; }
    /** Whether a match is being played (not finished, left or absent). */
    public boolean matchInProgress() { return match != null && match.view().phase() == MatchPhase.IN_PROGRESS; }
    /**
     * Runs {@code action} now when no match is in progress, otherwise once the current match
     * finishes or is left. A later request replaces an earlier one that is still waiting.
     */
    public void whenNoMatchInProgress(Runnable action) {
        if (!matchInProgress()) { action.run(); return; }
        afterMatch = action;
    }
    private void runAfterMatch() {
        if (afterMatch == null || matchInProgress()) return;
        Runnable action = afterMatch;
        afterMatch = null;
        action.run();
    }
    /** Closes this connection's waiting room so nobody can start a match in it any more. */
    private void closeRoom() {
        if (pendingRoomCode == null) return;
        pendingRoomCode = null;
        connection.send(new CancelRoomRequest());
    }
    private boolean updateBlocks() {
        if (!newMatchesBlocked()) return false;
        String reason = requiredUpdate != null
                ? "Version " + requiredUpdate + " is required. Install the update before starting another match."
                : updating ? "An update is being installed. New matches can start after Deck Duel restarts." : maintenance;
        fire(listener -> listener.onError(reason));
        return true;
    }
    /** Refreshes the room directory. */
    public void refreshMatches() { connection.send(new ListMatchesRequest()); }
    /** Shows the update and connection checks; the frame calls this once as the client launches. */
    public void showStartup() { setScreen(Screen.STARTUP); }
    /** Opens login once the startup checks have connected to the server. */
    public void startupFinished() { if (screen == Screen.STARTUP) setScreen(Screen.LOGIN); }
    /** Returns to the main menu. */
    public void leaveToLobby() { setScreen(Screen.LOBBY); }
    /** Opens match history and requests the account's recorded matches. */
    public void requestHistory() {
        setScreen(Screen.HISTORY);
        connection.send(new HistoryRequest());
    }

    /** Sends one move (card play, manual draw, keep or skip) against the revision currently shown. */
    public void move(Move move) {
        if (match == null || reconnecting || awaitingAnswer) return;
        if (!match.view().yourTurn()) return;
        awaitingAnswer = true;
        connection.send(new MoveRequest(nextRequestId++, match.view().revision(), move));
    }
    /** Asks for a rematch after a finished match, unless a required update is waiting. */
    public void requestRematch() { if (match != null && !updateBlocks()) connection.send(new RematchRequest()); }
    /** Asks for one of the account's own replays; it opens when the server answers. */
    public void requestReplay(long replayId) {
        if (screen != Screen.HISTORY) return;
        pendingReplay = replayId;
        connection.send(new ReplayRequest(replayId));
    }
    /** Stops watching the replay and returns to match history. */
    public void closeReplay() {
        replay = null;
        if (screen == Screen.REPLAY) setScreen(Screen.HISTORY);
    }
    /** Leaves the match (forfeiting it if unfinished) and returns to the menu. */
    public void leaveMatch() {
        if (match == null) { leaveToLobby(); return; }
        leftMatchId = match.matchId();
        connection.send(new LeaveMatchRequest());
        match = null;
        setScreen(Screen.LOBBY);
        runAfterMatch();
    }

    /** Applies a server message; callers hand off socket messages to the EDT. */
    public void handleMessage(Message message) {
        if (message instanceof LoginResponse response) {
            if (!response.ok()) { fire(listener -> listener.onLoginFailed(response.errorText())); return; }
            me = response.profile();
            pendingRoomCode = null;
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
            // A room that opens after an update block began is closed again at once.
            if (newMatchesBlocked()) { connection.send(new CancelRoomRequest()); updateBlocks(); return; }
            pendingRoomCode = created.roomCode();
            fire(listener -> listener.onMatchCreated(created.roomCode()));
        } else if (message instanceof ListMatchesResponse response) {
            openMatches = List.copyOf(response.openMatches());
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof HistoryResponse response) {
            history = List.copyOf(response.matches());
            fire(ClientModelListener::onHistoryChanged);
        } else if (message instanceof ReplayResponse response) {
            // Only the replay asked for, and only while history is still open.
            if (pendingReplay == null || pendingReplay != response.replayId() || screen != Screen.HISTORY) return;
            pendingReplay = null;
            replay = new ReplayPlayback(response);
            setScreen(Screen.REPLAY);
        } else if (message instanceof MaintenanceNotice notice) {
            maintenance(notice);
        } else if (message instanceof ErrorMessage error) {
            pendingReplay = null;
            fire(listener -> listener.onError(error.text()));
        } else if (message instanceof Ping ping) connection.send(new Pong(ping.timestamp()));
    }

    /** The server started or ended maintenance; it already closed any waiting room. */
    private void maintenance(MaintenanceNotice notice) {
        boolean affected = pendingRoomCode != null || matchInProgress();
        maintenance = notice.active() ? notice.text() : null;
        if (notice.active()) pendingRoomCode = null;
        fire(ClientModelListener::onLobbyChanged);
        if (notice.active() && affected) fire(listener -> listener.onError(notice.text()));
    }

    private void acceptMatch(MatchUpdate update) {
        // A leaver's last in-flight update must not pull them back to the table.
        if (update.matchId().equals(leftMatchId)) return;
        match = update;
        awaitingAnswer = false;
        pendingRoomCode = null;
        // A match that starts (a joined room) replaces a replay being watched.
        replay = null;
        pendingReplay = null;
        if (screen != Screen.TABLE) setScreen(Screen.TABLE);
        fire(ClientModelListener::onMatchChanged);
        runAfterMatch();
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
        offline = false;
        maintenance = null;
        replay = null; pendingReplay = null;
        me = null; pendingRoomCode = null; match = null;
        openMatches = List.of(); history = List.of();
        setScreen(Screen.LOGIN);
        fire(ClientModelListener::onConnectionLost);
        runAfterMatch();
    }

    private void reconnect(Credentials login) {
        Thread worker = new Thread(() -> {
            for (int attempt = 0; attempt < RECONNECT_ATTEMPTS; attempt++) {
                try {
                    if (attempt > 0) Thread.sleep(RECONNECT_DELAY_MILLIS);
                    connection.close();
                    connection.ensureConnected(login.route());
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
    /** The latest authoritative match update, or null outside a match. */
    public MatchUpdate match() { return match; }
    /** Whether this session plays offline on the client's own server. */
    public boolean isOffline() { return offline; }
    /** The update version that must be installed before the next match, or null. */
    public String requiredUpdate() { return requiredUpdate; }
    /** The replay being watched, or null. */
    public ReplayPlayback replay() { return replay; }
    /** Whether the connection dropped mid-match and a reconnect is in progress. */
    public boolean isReconnecting() { return reconnecting; }
    /** Presentation preference shared by the pages. */
    public boolean reducedMotion() { return reducedMotion; }
    /** Updates motion preferences and refreshes visible observers. */
    public void setReducedMotion(boolean value) { reducedMotion = value; fire(ClientModelListener::onLobbyChanged); }
}
