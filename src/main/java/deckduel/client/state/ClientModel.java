package deckduel.client.state;

import deckduel.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** EDT-owned account/lobby state; the retired game has no state or actions here. */
public final class ClientModel {
    private final ServerConnection connection;
    private final List<ClientModelListener> listeners = new ArrayList<>();
    private Screen screen = Screen.LOGIN;
    private UserProfile me;
    private List<MatchSummary> openMatches = List.of();
    private List<UserProfile> leaderboard = List.of();
    private String pendingRoomCode;
    private boolean findingMatch, reducedMotion;

    /** Uses an asynchronous connection boundary; panels never touch sockets. */
    public ClientModel(ServerConnection connection) { this.connection = Objects.requireNonNull(connection); }
    /** Adds an observer of shell state. */
    public void addListener(ClientModelListener listener) { listeners.add(listener); }
    /** Connects off the EDT and requests login. */
    public void login(String host, int port, String username, String password) {
        connectThenSend(host, port, new LoginRequest(username, password));
    }
    /** Connects off the EDT and requests registration. */
    public void register(String host, int port, String username, String password) {
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
    /** Requests match search; the shell server rejects it explicitly until gameplay exists. */
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
    /** Requests a hosted room. */
    public void createMatch() { connection.send(new CreateMatchRequest()); }
    /** Requests admission; no placeholder game is started on rejection. */
    public void joinMatch(String code) { connection.send(new JoinMatchRequest(code)); }
    /** Refreshes the room directory. */
    public void refreshMatches() { connection.send(new ListMatchesRequest()); }
    /** Opens the local deck region browser. */
    public void openDeck() { setScreen(Screen.DECK); }
    /** Returns from the deck browser without opening a connection. */
    public void closeDeck() { if (screen == Screen.DECK) setScreen(Screen.LOBBY); }
    /** Opens the non-playable card-table design. */
    public void openTablePreview() { setScreen(Screen.TABLE); }
    /** Returns to the main menu. */
    public void leaveToLobby() { setScreen(Screen.LOBBY); }
    /** Opens rankings and requests real account data. */
    public void requestLeaderboard() {
        setScreen(Screen.LEADERBOARD);
        connection.send(new LeaderboardRequest());
    }
    /** Applies a shell message; callers hand off socket messages to the EDT. */
    public void handleMessage(Message message) {
        if (message instanceof LoginResponse response) {
            if (!response.ok()) { fire(listener -> listener.onLoginFailed(response.errorText())); return; }
            me = response.profile();
            pendingRoomCode = null;
            findingMatch = false;
            setScreen(Screen.LOBBY);
            refreshMatches();
        } else if (message instanceof MatchCreated created) {
            pendingRoomCode = created.roomCode();
            fire(listener -> listener.onMatchCreated(created.roomCode()));
        } else if (message instanceof ListMatchesResponse response) {
            openMatches = List.copyOf(response.openMatches());
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof LeaderboardResponse response) {
            leaderboard = List.copyOf(response.topPlayers());
            fire(ClientModelListener::onLeaderboardChanged);
        } else if (message instanceof MatchmakingStatus status) {
            findingMatch = status.searching() && screen == Screen.LOBBY;
            fire(ClientModelListener::onLobbyChanged);
            if (status.error() != null) fire(listener -> listener.onError(status.error()));
        } else if (message instanceof ErrorMessage error) {
            fire(listener -> listener.onError(error.text()));
        } else if (message instanceof Ping ping) connection.send(new Pong(ping.timestamp()));
    }
    /** Clears identity and hosted-room indicators after losing the connection. */
    public void handleConnectionLost() {
        me = null; pendingRoomCode = null; findingMatch = false;
        openMatches = List.of(); leaderboard = List.of();
        setScreen(Screen.LOGIN);
        fire(ClientModelListener::onConnectionLost);
    }
    private void setScreen(Screen next) { screen = next; fire(listener -> listener.onScreenChanged(next)); }
    private void fire(Consumer<ClientModelListener> event) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> fire(event));
            return;
        }
        List.copyOf(listeners).forEach(event);
    }
    /** Current shell page. */
    public Screen screen() { return screen; }
    /** Authenticated profile, or null before login. */
    public UserProfile me() { return me; }
    /** Last room listing received from the server. */
    public List<MatchSummary> openMatches() { return openMatches; }
    /** Last rankings received from the server. */
    public List<UserProfile> leaderboard() { return leaderboard; }
    /** Room hosted by this connection, if any. */
    public String pendingRoomCode() { return pendingRoomCode; }
    /** Whether a search response is pending. */
    public boolean isFindingMatch() { return findingMatch; }
    /** Presentation preference shared by the shell pages. */
    public boolean reducedMotion() { return reducedMotion; }
    /** Updates motion preferences and refreshes visible shell observers. */
    public void setReducedMotion(boolean value) { reducedMotion = value; fire(ClientModelListener::onLobbyChanged); }
}
