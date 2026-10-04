package onitama.client.state;

import onitama.core.CardDeck;
import onitama.core.Difficulty;
import onitama.core.GameState;
import onitama.core.IllegalMoveException;
import onitama.core.Move;
import onitama.core.Piece;
import onitama.core.PlayerColor;
import onitama.core.PracticeBot;
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
import onitama.net.ReconnectRequest;
import onitama.net.RegisterRequest;
import onitama.net.RematchAccept;
import onitama.net.RematchRequest;
import onitama.net.ResignRequest;
import onitama.net.UserProfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

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
    // Last successful connection target, for automatic reconnect attempts.
    private String lastHost = "127.0.0.1";
    private int lastPort = 5555;

    private List<MatchSummary> openMatches = List.of();
    private List<UserProfile> leaderboard = List.of();
    private String pendingRoomCode;

    // Match state (all null/empty when not in a match).
    private GameState state;
    private PlayerColor myColor;
    private String roomCode;
    private String opponentName;
    private final List<MoveInfo> historyMoves = new ArrayList<>();
    private final List<Piece> myCaptures = new ArrayList<>();
    private final List<Piece> enemyCaptures = new ArrayList<>();
    private boolean rematchOfferedByOpponent;
    private boolean gameOverAnnounced;
    /**
     * True once a {@link GameOver} arrived for the current match. The server
     * communicates a forfeit only through {@code GameOver} — the last
     * serialized {@code GameState} on the wire still says ONGOING — so the
     * model must gate selection, parking and resigning on this flag itself.
     */
    private boolean matchOver;

    // Selection state for the game board.
    private String selectedCardId;
    private Square selectedSquare;
    private List<Square> highlightedTargets = List.of();
    /** The square where the most recent half-move captured a piece (or null). */
    private Square lastCaptureSquare;

    // A parked ongoing match: the player opened the lobby mid-game and can
    // return without leaving the table. The state keeps live-updating via
    // MoveApplied broadcasts, so returning is always in sync.
    private boolean matchParked;
    private String parkedRoomCode;

    // Single-player practice: a local game against the built-in bot, played
    // entirely on the EDT — no server involvement, no Elo. The
    // Rookie heuristic answers instantly; Senior/Legend search on a worker
    // thread, so the generation counter guards against stale results.
    private static final int BOT_THINK_MILLIS = 900;
    /** Parked-practice room label shown in the lobby's return pill. */
    private static final String PRACTICE_ROOM_CODE = "PRACTICE";
    private boolean practiceMode;
    private Difficulty practiceDifficulty = Difficulty.ROOKIE;
    private final Random practiceRandom = new Random();
    private Timer botTimer;
    private int practiceGeneration;

    /** One compact move-history row (§17 of the design spec). */
    public record MoveInfo(int number, String cardName, String fromSquare,
                           String toSquare, boolean capture, boolean pass) {
    }

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
        lastHost = host;
        lastPort = port;
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
        if (practiceMode && state != null && state.isOngoing()) {
            // Joining online while a practice match is parked would silently
            // discard it — the player must settle the practice game first.
            fire(listener -> listener.onError(
                    "You have a practice match in progress — resign it "
                            + "before joining online."));
            return;
        }
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

    /**
     * Starts a local practice match against the built-in bot at the given
     * difficulty. The game is dealt and played entirely in the client: no
     * server, no Elo change. The human always sits on the Blue side.
     */
    public void startPracticeMatch(Difficulty difficulty) {
        if (hasParkedMatch()) {
            fire(listener -> listener.onError(
                    "You have an ongoing match — return to it first."));
            return;
        }
        stopBotTimer();
        practiceMode = true;
        practiceDifficulty = difficulty;
        practiceGeneration++;
        state = GameState.newGame(CardDeck.deal());
        myColor = PlayerColor.BLUE;
        roomCode = null;
        opponentName = difficulty.botName();
        historyMoves.clear();
        myCaptures.clear();
        enemyCaptures.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        matchOver = false;
        matchParked = false;
        parkedRoomCode = null;
        pendingRoomCode = null;
        lastCaptureSquare = null;
        clearSelection();
        setScreen(Screen.GAME);
        autoPassIfNeeded();
        scheduleBotTurn();
        fire(ClientModelListener::onMatchChanged);
    }

    /** True while the client is in a local practice match against the bot. */
    public boolean isPracticeMode() {
        return practiceMode;
    }

    /** The difficulty of the current (or last) practice match. */
    public Difficulty practiceDifficulty() {
        return practiceDifficulty;
    }

    /** Resigns the current match. */
    public void resign() {
        if (practiceMode) {
            // A practice game has nothing at stake; resigning abandons it
            // outright — it must not park like a paused match.
            stopBotTimer();
            clearMatchState();
            matchParked = false;
            parkedRoomCode = null;
            setScreen(Screen.LOBBY);
            refreshMatches();
            return;
        }
        // Only a running online match can be resigned; with no match, one
        // already finished, or a forfeit pending on the wire, the request
        // would be a silent no-op on the wire.
        if (state != null && state.isOngoing() && !matchOver) {
            connection.send(new ResignRequest());
        }
    }

    /** Offers a rematch after the game ended. */
    public void requestRematch() {
        connection.send(new RematchRequest());
    }

    /**
     * Opens the lobby. An ongoing match is parked (kept live in the
     * background) so the player can return to it — practice matches park
     * exactly like online ones, shown as "PRACTICE" in the lobby pill; a
     * finished one is cleared.
     */
    public void leaveToLobby() {
        if (practiceMode) {
            stopBotTimer();
            if (state != null && state.isOngoing()) {
                matchParked = true;
                parkedRoomCode = PRACTICE_ROOM_CODE;
                pendingRoomCode = null;
                clearSelection();
                setScreen(Screen.LOBBY);
                refreshMatches();
                return;
            }
            clearMatchState();
            matchParked = false;
            parkedRoomCode = null;
            setScreen(Screen.LOBBY);
            refreshMatches();
            return;
        }
        // Park only a match that can still continue; a GameOver has already
        // arrived for a finished one (its wire state still says ONGOING).
        if (state != null && state.isOngoing() && !matchOver) {
            matchParked = true;
            parkedRoomCode = roomCode;
            clearSelection();
            setScreen(Screen.LOBBY);
            refreshMatches();
            return;
        }
        clearMatchState();
        matchParked = false;
        parkedRoomCode = null;
        setScreen(Screen.LOBBY);
        refreshMatches();
    }

    /** True while an ongoing match is parked in the background. */
    public boolean hasParkedMatch() {
        return matchParked && parkedRoomCode != null;
    }

    public String parkedRoomCode() {
        return parkedRoomCode;
    }

    /** Returns to the parked match screen. */
    public void returnToParkedMatch() {
        if (!hasParkedMatch()) {
            return;
        }
        matchParked = false;
        setScreen(Screen.GAME);
        if (practiceMode) {
            // The bot timer was stopped for the pause: re-arm the forced
            // pass check and the bot's move so the game continues seamlessly
            // from the exact parked position.
            autoPassIfNeeded();
            scheduleBotTurn();
        }
        fire(ClientModelListener::onMatchChanged);
    }

    /** Called when the user clicks a board square. */
    public void boardClicked(Square clicked) {
        if (state == null) {
            return;
        }
        if (highlightedTargets.contains(clicked)) {
            if (practiceMode) {
                applyPracticeMove(new Move(selectedSquare, clicked, selectedCardId));
            } else {
                connection.send(new MoveRequest(selectedCardId, selectedSquare, clicked));
            }
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
            // The match is over: freeze local play (selection, parking,
            // resign) — the forfeited GameState on the wire still says
            // ONGOING — and drop any parked session so the lobby stops
            // offering "return to match" for a game that cannot continue.
            // The state itself is kept for the game-over dialog (Elo, moves).
            matchOver = true;
            matchParked = false;
            parkedRoomCode = null;
            clearSelection();
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
        String token = reconnectToken;
        if (token != null) {
            // Keep the profile and match context: the reconnect re-attaches to
            // the same session and the server sends the authoritative state.
            fire(ClientModelListener::onConnectionLost);
            attemptReconnect(token, 3);
        } else {
            clearMatchState();
            me = null;
            setScreen(Screen.LOGIN);
            fire(ClientModelListener::onConnectionLost);
        }
    }

    /**
     * Tries to re-attach to the interrupted session with the reconnect token
     * in a background thread (the server answers with the current match
     * state or, if the match is gone, a fresh login response). Falls back to
     * the login screen after the given number of attempts.
     */
    private void attemptReconnect(String token, int attemptsLeft) {
        new Thread(() -> {
            try {
                connection.ensureConnected(lastHost, lastPort);
                connection.send(new ReconnectRequest(token));
            } catch (IOException e) {
                if (attemptsLeft > 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    attemptReconnect(token, attemptsLeft - 1);
                } else {
                    fire(listener -> listener.onError(
                            "Could not reconnect (" + e.getMessage() + "). Please log in again."));
                    me = null;
                    reconnectToken = null;
                    setScreen(Screen.LOGIN);
                }
            }
        }, "onitama-reconnect").start();
    }

    private void handleLoginResponse(LoginResponse response) {
        if (response.ok()) {
            clearMatchState(); // e.g. a reconnect whose match is already gone
            me = response.profile();
            reconnectToken = response.reconnectToken();
            setScreen(Screen.LOBBY);
            refreshMatches();
        } else {
            fire(listener -> listener.onLoginFailed(response.errorText()));
        }
    }

    private void handleMatchStart(MatchStart start) {
        stopBotTimer();
        practiceMode = false;
        state = start.initialState();
        myColor = start.yourColor();
        roomCode = start.roomCode();
        opponentName = start.opponentUsername();
        historyMoves.clear();
        myCaptures.clear();
        enemyCaptures.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        matchOver = false;
        matchParked = false;
        parkedRoomCode = null;
        clearSelection();
        setScreen(Screen.GAME);
        autoPassIfNeeded();
        fire(ClientModelListener::onMatchChanged);
    }

    private void handleMoveApplied(MoveApplied applied) {
        GameState previous = state;
        state = applied.state();
        Move lastMove = applied.lastMove();
        if (lastMove == null) {
            lastCaptureSquare = null;
            historyMoves.add(new MoveInfo(state.moveNumber(),
                    state.transit().name(), "", "", false, true));
        } else {
            Piece captured = previous == null ? null : recordCapture(previous, lastMove);
            lastCaptureSquare = captured == null ? null : lastMove.to();
            historyMoves.add(new MoveInfo(state.moveNumber(),
                    cardName(lastMove.cardId()),
                    squareName(lastMove.from()), squareName(lastMove.to()),
                    captured != null, false));
        }
        clearSelection();
        autoPassIfNeeded();
        fire(ClientModelListener::onMatchChanged);
    }


    /**
     * Card display name for the move history. A card id from the wire that is
     * no longer in the deck must not kill the EDT listener chain, so it falls
     * back to a placeholder instead of throwing.
     */
    private static String cardName(String cardId) {
        try {
            return CardDeck.cardById(cardId).name();
        } catch (IllegalArgumentException e) {
            LOG.warning("unknown card id in move: " + cardId);
            return "Unknown Card";
        }
    }

    /** Records a captured piece by comparing the previous and current boards. */
    private Piece recordCapture(GameState previous, Move lastMove) {
        Piece victim = previous.board().pieceAt(lastMove.to());
        Piece occupant = state.board().pieceAt(lastMove.to());
        if (victim == null || occupant == null || victim.color() == occupant.color()) {
            return null;
        }
        if (victim.color() == myColor) {
            enemyCaptures.add(victim);
        } else {
            myCaptures.add(victim);
        }
        return victim;
    }

    /**
     * Passing is mandatory when the player to move has no legal move; the
     * client performs it automatically with its first hand card — locally in
     * practice mode, via the server in an online match.
     */
    private void autoPassIfNeeded() {
        if (myTurn() && RulesEngine.mustPass(state)) {
            if (practiceMode) {
                applyPracticePass();
            } else {
                connection.send(new PassTurn(state.hand(myColor).get(0).id()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Practice mode: local turns against the built-in bot (EDT only)
    // ------------------------------------------------------------------

    private PlayerColor botColor() {
        return myColor == null ? null : myColor.opponent();
    }

    /**
     * Applies the human's half-move to the local game and hands the turn to
     * the bot. {@code IllegalMoveException} cannot occur for moves reached
     * through the legal-targets highlighting; the catch is a safety net so a
     * bug can never kill the EDT.
     */
    private void applyPracticeMove(Move move) {
        try {
            Piece victim = state.board().pieceAt(move.to());
            RulesEngine.apply(state, move);
            recordPracticeMove(move, victim, myCaptures);
            advancePractice();
        } catch (IllegalMoveException e) {
            fire(listener -> listener.onError("Move rejected: " + e.getMessage()));
        }
    }

    /** Applies the human's mandatory pass with the first hand card. */
    private void applyPracticePass() {
        RulesEngine.pass(state, state.hand(myColor).get(0).id());
        historyMoves.add(new MoveInfo(state.moveNumber(),
                state.transit().name(), "", "", false, true));
        advancePractice();
    }

    /**
     * One-shot timer: lets the bot reply after a short, visible pause. The
     * Rookie then answers on the EDT; Senior and Legend search on a daemon
     * worker thread so the UI never freezes under them.
     */
    private void scheduleBotTurn() {
        stopBotTimer();
        if (!practiceMode || state == null || !state.isOngoing()
                || state.turn() != botColor()) {
            return;
        }
        botTimer = new Timer(BOT_THINK_MILLIS, event -> botTurnStarted());
        botTimer.setRepeats(false);
        botTimer.start();
    }

    private void stopBotTimer() {
        if (botTimer != null) {
            botTimer.stop();
            botTimer = null;
        }
    }

    /** The thinking pause is over: compute (or start computing) the bot move. */
    private void botTurnStarted() {
        botTimer = null;
        if (!practiceMode || state == null || !state.isOngoing()
                || state.turn() != botColor()) {
            return;
        }
        PlayerColor bot = botColor();
        if (practiceDifficulty == Difficulty.ROOKIE) {
            Move move = PracticeBot.chooseMove(state, bot, practiceDifficulty, practiceRandom);
            playBotMove(move);
            return;
        }
        // Stronger levels search a snapshot away from the EDT; the result is
        // only applied if this practice match is still the current one.
        GameState snapshot = new GameState(state);
        int generation = practiceGeneration;
        Thread searcher = new Thread(() -> {
            Move move = PracticeBot.chooseMove(snapshot, bot, practiceDifficulty, new Random());
            SwingUtilities.invokeLater(() -> applyBotMoveIfCurrent(generation, move));
        }, "onitama-practice-bot");
        searcher.setDaemon(true);
        searcher.start();
    }

    /** Discards a late search result if the practice match has changed. */
    private void applyBotMoveIfCurrent(int generation, Move move) {
        if (generation != practiceGeneration || !practiceMode || state == null
                || !state.isOngoing() || state.turn() != botColor()) {
            return;
        }
        playBotMove(move);
    }

    /** Applies the bot's chosen move on the EDT (null = mandatory pass). */
    private void playBotMove(Move move) {
        if (!practiceMode || state == null || !state.isOngoing()
                || state.turn() != botColor()) {
            return;
        }
        PlayerColor bot = botColor();
        if (move == null) {
            RulesEngine.pass(state, state.hand(bot).get(0).id());
            lastCaptureSquare = null;
            historyMoves.add(new MoveInfo(state.moveNumber(),
                    state.transit().name(), "", "", false, true));
        } else {
            Piece victim = state.board().pieceAt(move.to());
            RulesEngine.apply(state, move);
            recordPracticeMove(move, victim, enemyCaptures);
        }
        // The bot's half-move swaps the hands just like a human's; a selection
        // pointing at the pre-move hand must not survive it.
        clearSelection();
        advancePractice();
        fire(ClientModelListener::onMatchChanged);
    }

    /** Adds the half-move to the history and the captor's tray. */
    private void recordPracticeMove(Move move, Piece victim, List<Piece> captorTray) {
        lastCaptureSquare = victim == null ? null : move.to();
        if (victim != null) {
            captorTray.add(victim);
        }
        historyMoves.add(new MoveInfo(state.moveNumber(),
                CardDeck.cardById(move.cardId()).name(),
                squareName(move.from()), squareName(move.to()),
                victim != null, false));
    }

    /** After a practice half-move: hand over the turn or announce the end. */
    private void advancePractice() {
        if (!state.isOngoing()) {
            announcePracticeGameOver();
            return;
        }
        // The human may now be forced to pass (a pass can even end the game
        // on the move limit); the bot's own pass is part of playBotTurn.
        autoPassIfNeeded();
        if (!state.isOngoing()) {
            announcePracticeGameOver();
            return;
        }
        scheduleBotTurn();
    }

    /** Ends a finished practice game: stops the bot and shows the result. */
    private void announcePracticeGameOver() {
        stopBotTimer();
        matchOver = true;
        fire(listener -> listener.onGameOver(
                new GameOver(state.winner(), state.way(), 0, 0)));
    }

    private boolean myTurn() {
        return state != null && state.isOngoing() && !matchOver
                && state.turn() == myColor;
    }

    /** True once a GameOver arrived for the match currently in the model. */
    public boolean isMatchOver() {
        return matchOver;
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
        stopBotTimer();
        practiceMode = false;
        practiceGeneration++;
        state = null;
        myColor = null;
        roomCode = null;
        opponentName = null;
        pendingRoomCode = null;
        historyMoves.clear();
        myCaptures.clear();
        enemyCaptures.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        matchOver = false;
        clearSelection();
    }

    private void setScreen(Screen newScreen) {
        screen = newScreen;
        fire(listener -> listener.onScreenChanged(newScreen));
    }

    /**
     * Fires one event to every listener. Always runs on the Swing EDT:
     * calls from background threads (connect/reconnect failures) are
     * marshalled there, because listeners touch Swing components. Each
     * listener is isolated — a failing panel can never starve the others.
     */
    private void fire(Consumer<ClientModelListener> event) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> fire(event));
            return;
        }
        for (ClientModelListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "UI listener failed", e);
            }
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

    public List<MoveInfo> historyMoves() {
        return List.copyOf(historyMoves);
    }

    /** Board coordinates in the compact A1..E5 form used by the history. */
    private static String squareName(Square square) {
        return "" + (char) ('A' + square.x()) + (square.y() + 1);
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

    /** The square where the latest half-move captured a piece, or null. */
    public Square lastCaptureSquare() {
        return lastCaptureSquare;
    }
}
