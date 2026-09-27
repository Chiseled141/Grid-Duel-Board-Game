package onitama.client.state;

import onitama.client.OnitamaClient;
import onitama.core.CardDeck;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.Piece;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.Square;
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
import onitama.net.MatchSummary;
import onitama.net.Message;
import onitama.net.MoveApplied;
import onitama.net.MoveRejected;
import onitama.net.MoveRequest;
import onitama.net.OpponentLeft;
import onitama.net.PassTurn;
import onitama.net.RegisterRequest;
import onitama.net.RematchAccept;
import onitama.net.RematchRequest;
import onitama.net.ResignRequest;
import onitama.net.UserProfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The client-side model, owned by the Swing EDT: every field is only read or
 * written on the EDT. The socket reader thread hands messages in via
 * {@code SwingUtilities.invokeLater} (wired in the client frame), so no
 * synchronization is needed anywhere in this class. Panels subscribe as
 * {@link ClientModelListener}s and re-render from the model's getters.
 */
public final class ClientModel {

    private static final Logger LOG = Logger.getLogger(ClientModel.class.getName());

    private final ServerConnection connection;
    private final List<ClientModelListener> listeners = new ArrayList<>();

    private Screen screen = Screen.LOGIN;
    private UserProfile me;
    private String reconnectToken;

    private List<MatchSummary> openMatches = List.of();
    private List<UserProfile> leaderboard = List.of();
    private String pendingRoomCode;

    // Match state (all null/empty when not in a match).
    private GameState state;
    private PlayerColor myColor;
    private String roomCode;
    private String opponentName;
    private final List<String> historyLines = new ArrayList<>();
    private final List<Piece> myCaptures = new ArrayList<>();
    private final List<Piece> enemyCaptures = new ArrayList<>();
    private boolean rematchOfferedByOpponent;
    private boolean gameOverAnnounced;

    // Selection state for the game board.
    private String selectedCardId;
    private Square selectedSquare;
    private List<Square> highlightedTargets = List.of();

    /** Creates the model; outgoing messages go through the connection. */
    public ClientModel(ServerConnection connection) {
        this.connection = connection;
    }

    /** Subscribes a listener; listeners fire on the EDT. */
    public void addListener(ClientModelListener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------
    // Actions called by the UI (EDT)
    // ------------------------------------------------------------------

    /**
     * Connects (in a background thread) and sends a login request.
     * The answer arrives as a {@link LoginResponse} on the EDT.
     */
    public void login(String host, int port, String username, String password) {
        connectThenSend(host, port, new LoginRequest(username, password));
    }

    /** Connects (in a background thread) and sends a registration request. */
    public void register(String host, int port, String username, String password) {
        connectThenSend(host, port, new RegisterRequest(username, password));
    }

    private void connectThenSend(String host, int port, Message request) {
        if (connection.isConnected()) {
            connection.send(request);
            return;
        }
        // Connecting can block for seconds — never on the EDT.
        new Thread(() -> {
            try {
                connection.ensureConnected(host, port);
                connection.send(request);
            } catch (IOException e) {
                fire(listener -> listener.onError(
                        "Cannot reach server " + host + ":" + port + " (" + e.getMessage() + ")"));
            }
        }, "onitama-connect").start();
    }

    /** Asks the server to open a lobby. */
    public void createMatch() {
        connection.send(new CreateMatchRequest());
    }

    /** Asks to join the match with the given room code. */
    public void joinMatch(String roomCode) {
        connection.send(new JoinMatchRequest(roomCode));
    }

    /** Refreshes the open-match list. */
    public void refreshMatches() {
        connection.send(new ListMatchesRequest());
    }

    /** Requests the leaderboard. */
    public void requestLeaderboard() {
        connection.send(new LeaderboardRequest());
    }

    /** Resigns the current match. */
    public void resign() {
        connection.send(new ResignRequest());
    }

    /** Offers a rematch after the game ended. */
    public void requestRematch() {
        connection.send(new RematchRequest());
    }

    /** Leaves a finished match locally and returns to the lobby. */
    public void leaveToLobby() {
        clearMatchState();
        setScreen(Screen.LOBBY);
        refreshMatches();
    }

    /** Called when the user clicks a board square. */
    public void boardClicked(Square clicked) {
        if (state == null) {
            return;
        }
        if (highlightedTargets.contains(clicked)) {
            connection.send(new MoveRequest(selectedCardId, selectedSquare, clicked));
            clearSelection();
            fire(ClientModelListener::onMatchChanged);
            return;
        }
        if (!myTurn()) {
            return;
        }
        Piece piece = state.board().pieceAt(clicked);
        if (piece != null && piece.color() == myColor) {
            selectedSquare = clicked;
        } else {
            clearSelection();
        }
        recomputeTargets();
        fire(ClientModelListener::onMatchChanged);
    }

    /** Called when the user clicks one of their hand cards. */
    public void cardClicked(String cardId) {
        if (!myTurn()) {
            return;
        }
        selectedCardId = Objects.equals(selectedCardId, cardId) ? null : cardId;
        recomputeTargets();
        fire(ClientModelListener::onMatchChanged);
    }

    /** Clears the card/piece selection (ESC or state change). */
    public void clearSelection() {
        selectedCardId = null;
        selectedSquare = null;
        highlightedTargets = List.of();
    }

    // ------------------------------------------------------------------
    // Message handling (EDT, fed by the socket reader via invokeLater)
    // ------------------------------------------------------------------

    /** Handles one message from the server. Must run on the EDT. */
    public void handleMessage(Message message) {
        if (message instanceof LoginResponse response) {
            handleLoginResponse(response);
        } else if (message instanceof MatchCreated created) {
            pendingRoomCode = created.roomCode();
            fire(listener -> listener.onMatchCreated(created.roomCode()));
        } else if (message instanceof ListMatchesResponse response) {
            openMatches = response.openMatches();
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof LeaderboardResponse response) {
            leaderboard = response.topPlayers();
            fire(ClientModelListener::onLeaderboardChanged);
        } else if (message instanceof MatchStart start) {
            handleMatchStart(start);
        } else if (message instanceof MoveApplied applied) {
            handleMoveApplied(applied);
        } else if (message instanceof MoveRejected rejected) {
            fire(listener -> listener.onError("Move rejected: " + rejected.reason()));
        } else if (message instanceof GameOver over) {
            fire(listener -> listener.onGameOver(over));
        } else if (message instanceof OpponentLeft left) {
            fire(listener -> listener.onOpponentLeft(left.graceSeconds()));
        } else if (message instanceof RematchAccept) {
            rematchOfferedByOpponent = true;
            fire(ClientModelListener::onRematchOffered);
        } else if (message instanceof ErrorMessage error) {
            fire(listener -> listener.onError(error.text()));
        } else if (message instanceof onitama.net.Pong) {
            // Heartbeat round-trips are used in milestone M6.
        } else {
            LOG.warning("unhandled message type: " + message.getClass().getSimpleName());
        }
    }

    /** Called when the connection dropped; resets the UI to the login screen. */
    public void handleConnectionLost() {
        boolean wasInMatch = state != null;
        clearMatchState();
        me = null;
        reconnectToken = null;
        setScreen(Screen.LOGIN);
        fire(listener -> listener.onError(wasInMatch
                ? "Connection lost. Please log in again."
                : "Connection lost."));
        fire(ClientModelListener::onConnectionLost);
    }

    private void handleLoginResponse(LoginResponse response) {
        if (response.ok()) {
            me = response.profile();
            reconnectToken = response.reconnectToken();
            setScreen(Screen.LOBBY);
            refreshMatches();
        } else {
            fire(listener -> listener.onLoginFailed(response.errorText()));
        }
    }

    private void handleMatchStart(MatchStart start) {
        state = start.initialState();
        myColor = start.yourColor();
        roomCode = start.roomCode();
        opponentName = start.opponentUsername();
        historyLines.clear();
        myCaptures.clear();
        enemyCaptures.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        clearSelection();
        setScreen(Screen.GAME);
        autoPassIfNeeded();
        fire(ClientModelListener::onMatchChanged);
    }

    private void handleMoveApplied(MoveApplied applied) {
        GameState previous = state;
        state = applied.state();
        Move lastMove = applied.lastMove();
        if (previous != null && lastMove != null) {
            recordCapture(previous, lastMove);
            historyLines.add(state.moveNumber() + ". "
                    + lastMove.cardId() + " " + lastMove.from() + " \u2192 " + lastMove.to());
        } else {
            historyLines.add(state.moveNumber() + ". pass");
        }
        clearSelection();
        autoPassIfNeeded();
        fire(ClientModelListener::onMatchChanged);
    }

    /** Records a captured piece by comparing the previous and current boards. */
    private void recordCapture(GameState previous, Move lastMove) {
        Piece victim = previous.board().pieceAt(lastMove.to());
        Piece occupant = state.board().pieceAt(lastMove.to());
        if (victim == null || occupant == null || victim.color() == occupant.color()) {
            return;
        }
        if (victim.color() == myColor) {
            enemyCaptures.add(victim);
        } else {
            myCaptures.add(victim);
        }
    }

    /**
     * Passing is mandatory when the player to move has no legal move; the
     * client performs it automatically with its first hand card.
     */
    private void autoPassIfNeeded() {
        if (myTurn() && RulesEngine.mustPass(state)) {
            connection.send(new PassTurn(state.hand(myColor).get(0).id()));
        }
    }

    private boolean myTurn() {
        return state != null && state.isOngoing() && state.turn() == myColor;
    }

    private void recomputeTargets() {
        boolean cardInHand = selectedCardId != null
                && state.hand(myColor).stream()
                        .anyMatch(card -> card.id().equals(selectedCardId));
        Piece piece = selectedSquare == null ? null : state.board().pieceAt(selectedSquare);
        boolean pieceThere = piece != null && piece.color() == myColor;
        if (cardInHand && pieceThere) {
            highlightedTargets = RulesEngine.legalDestinations(state.board(),
                    selectedSquare, CardDeck.cardById(selectedCardId), myColor);
        } else {
            highlightedTargets = List.of();
        }
    }

    private void clearMatchState() {
        state = null;
        myColor = null;
        roomCode = null;
        opponentName = null;
        pendingRoomCode = null;
        historyLines.clear();
        myCaptures.clear();
        enemyCaptures.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        clearSelection();
    }

    private void setScreen(Screen newScreen) {
        screen = newScreen;
        fire(listener -> listener.onScreenChanged(newScreen));
    }

    private void fire(Consumer<ClientModelListener> event) {
        for (ClientModelListener listener : listeners) {
            event.accept(listener);
        }
    }

    // ------------------------------------------------------------------
    // Getters (EDT only)
    // ------------------------------------------------------------------

    public Screen screen() {
        return screen;
    }

    public UserProfile me() {
        return me;
    }

    public String reconnectToken() {
        return reconnectToken;
    }

    public List<MatchSummary> openMatches() {
        return openMatches;
    }

    public List<UserProfile> leaderboard() {
        return leaderboard;
    }

    /** The room code of a lobby this client created and still waits on. */
    public String pendingRoomCode() {
        return pendingRoomCode;
    }

    public GameState state() {
        return state;
    }

    public PlayerColor myColor() {
        return myColor;
    }

    public String roomCode() {
        return roomCode;
    }

    public String opponentName() {
        return opponentName;
    }

    public List<String> historyLines() {
        return List.copyOf(historyLines);
    }

    public List<Piece> myCaptures() {
        return List.copyOf(myCaptures);
    }

    public List<Piece> enemyCaptures() {
        return List.copyOf(enemyCaptures);
    }

    public boolean rematchOfferedByOpponent() {
        return rematchOfferedByOpponent;
    }

    public boolean gameOverAnnounced() {
        return gameOverAnnounced;
    }

    public void markGameOverAnnounced() {
        gameOverAnnounced = true;
    }

    public String selectedCardId() {
        return selectedCardId;
    }

    public Square selectedSquare() {
        return selectedSquare;
    }

    public List<Square> highlightedTargets() {
        return highlightedTargets;
    }

    /** A short human-readable status line for the game screen. */
    public String statusLine() {
        if (state == null) {
            return "";
        }
        if (!state.isOngoing()) {
            if (state.winner() == null) {
                return "Game over: draw";
            }
            boolean iWon = state.winner() == myColor;
            return "Game over: " + (iWon ? "you win" : opponentName + " wins")
                    + " (" + state.way() + ")";
        }
        if (state.turn() == myColor) {
            return RulesEngine.mustPass(state)
                    ? "No legal move - passing..."
                    : "Your turn - pick a card and a piece";
        }
        return "Waiting for " + opponentName + "...";
    }
}
