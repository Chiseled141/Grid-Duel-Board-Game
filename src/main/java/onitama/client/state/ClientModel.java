package onitama.client.state;

import onitama.config.RulesConfigLoader;
import onitama.core.CardDeck;
import onitama.core.Difficulty;
import onitama.core.PlayerColor;
import onitama.core.PracticeBot;
import onitama.core.Square;
import onitama.core.duel.ActionError;
import onitama.core.duel.ActionResult;
import onitama.core.duel.CardInstance;
import onitama.core.duel.Direction;
import onitama.core.duel.GameAction;
import onitama.core.duel.GameState;
import onitama.core.duel.PlacedPiece;
import onitama.core.duel.PlayerView;
import onitama.core.duel.PublicEvent;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import onitama.core.duel.SelectionDraft;
import onitama.core.duel.SelectionOptions;
import onitama.core.duel.Targeting;
import onitama.core.duel.TurnPhase;
import onitama.net.ActionReply;
import onitama.net.ActionRequest;
import onitama.net.ChatRequest;
import onitama.net.ChatMessage;
import onitama.net.CreateMatchRequest;
import onitama.net.FindMatchRequest;
import onitama.net.MatchmakingStatus;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.GameUpdate;
import onitama.net.JoinMatchRequest;
import onitama.net.LeaderboardRequest;
import onitama.net.LeaderboardResponse;
import onitama.net.ListMatchesRequest;
import onitama.net.ListMatchesResponse;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchSync;
import onitama.net.MatchSummary;
import onitama.net.Message;
import onitama.net.OpponentLeft;
import onitama.net.ReconnectRequest;
import onitama.net.RegisterRequest;
import onitama.net.RematchAccept;
import onitama.net.RematchRequest;
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

/**
 * The client-side model, owned by the Swing EDT: every field is only read or
 * written on the EDT. The socket reader thread hands messages in via
 * {@code SwingUtilities.invokeLater}, so no synchronization is needed anywhere
 * in this class. Panels subscribe as {@link ClientModelListener}s.
 */
public final class ClientModel {

    private static final Logger LOG = Logger.getLogger(ClientModel.class.getName());

    private final ServerConnection connection;
    private final List<ClientModelListener> listeners = new ArrayList<>();

    private Screen screen = Screen.LOGIN;
    private UserProfile me;
    private String reconnectToken;
    private String lastHost = "127.0.0.1";
    private int lastPort = 5555;

    private List<MatchSummary> openMatches = List.of();
    private List<UserProfile> leaderboard = List.of();
    private String pendingRoomCode;
    private boolean findingMatch;

    // Match state
    private MatchSync currentSync;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private ActionRequest inFlightRequest;
    private String roomCode;
    private String opponentName;
    private final List<MoveInfo> historyMoves = new ArrayList<>();
    private final List<PlayedCard> playedCards = new ArrayList<>();
    private boolean rematchOfferedByOpponent;
    private boolean gameOverAnnounced;
    private boolean matchOver;

    // Selection state
    private String selectedCardId;
    private Square selectedSquare;
    private Integer selectedPieceId;
    private List<Square> highlightedTargets = List.of();
    private Square lastCaptureSquare;
    private Square lastMoveFrom;
    private Square lastMoveTo;

    // Skill targeting draft
    private String selectedSkillInstanceId;
    private SelectionDraft skillDraft;
    private SelectionOptions skillOptions;

    // Parked match
    private boolean matchParked;
    private String parkedRoomCode;

    // Practice mode
    private boolean practiceMode;
    private Difficulty practiceDifficulty = Difficulty.ROOKIE;
    private GameState practiceState;
    private Random practiceRandom;
    private final javax.swing.Timer botTimer = new javax.swing.Timer(300, e -> stepBot());

    // Presentation and animation state
    private boolean reducedMotion;
    private String presentationMatchId;
    private long lastPresentedRevision = -1;
    private PlayerView priorViewForPresentation;

    /** One compact move-history row. */
    public record MoveInfo(int number, String cardName, String fromSquare,
                           String toSquare, boolean capture, boolean pass) {
    }

    /** A card revealed by a permitted public play event, never an opponent draw. */
    public record PlayedCard(int turn, PlayerColor actor, String definitionId, boolean skill) { }

    /** Public card plays in acceptance order; same-match reconnect retains the local list. */
    public List<PlayedCard> playedCards() {
        return List.copyOf(playedCards);
    }

    public ClientModel(ServerConnection connection) {
        this.connection = connection;
        this.botTimer.setRepeats(false);
    }

    public void addListener(ClientModelListener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------
    // Actions called by UI
    // ------------------------------------------------------------------

    public void login(String host, int port, String username, String password) {
        connectThenSend(host, port, new LoginRequest(username, password));
    }

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

    /** Joins the server queue once; an existing match must be resumed first. */
    public void findMatch() {
        if (findingMatch) return;
        if (me == null || screen != Screen.LOBBY || hasParkedMatch() || !connection.isConnected()) {
            fire(listener -> listener.onError("Connect to the server and finish your current match first."));
            return;
        }
        findingMatch = true;
        pendingRoomCode = null;
        fire(ClientModelListener::onLobbyChanged);
        connection.send(new FindMatchRequest(true));
    }

    /** Cancellation takes effect when the server acknowledges it. */
    public void cancelFindMatch() {
        if (findingMatch) connection.send(new FindMatchRequest(false));
    }

    /** True while a search or its cancellation is awaiting the server. */
    public boolean isFindingMatch() {
        return findingMatch;
    }

    public void createMatch() {
        connection.send(new CreateMatchRequest());
    }

    public void joinMatch(String roomCode) {
        connection.send(new JoinMatchRequest(roomCode));
    }

    public void refreshMatches() {
        connection.send(new ListMatchesRequest());
    }

    /** Opens the local historical atlas without a server request. */
    public void openAtlas() {
        setScreen(Screen.ATLAS);
    }

    /** Leaves the local atlas without altering a parked match or querying the server. */
    public void closeAtlas() {
        if (screen == Screen.ATLAS) setScreen(Screen.LOBBY);
    }

    public void requestLeaderboard() {
        setScreen(Screen.LEADERBOARD);
        connection.send(new LeaderboardRequest());
    }

    /** True when chat can be sent to a connected opponent in the live match. */
    public boolean canChat() {
        return screen == Screen.GAME && currentSync != null && !practiceMode
                && !matchOver && !currentSync.paused() && connection.isConnected();
    }

    /** Queues match text through the connection; display waits for the server echo. */
    public boolean sendChat(String text) {
        if (!canChat() || !ChatRequest.validText(text)) return false;
        connection.send(new ChatRequest(currentSync.matchId(), text));
        return true;
    }

    /** Immutable, bounded local history; messages missed during disconnect are not replayed. */
    public List<ChatMessage> chatMessages() {
        return List.copyOf(chatMessages);
    }

    public void resign() {
        if (practiceMode) {
            clearMatchState();
            setScreen(Screen.LOBBY);
            refreshMatches();
            return;
        }
        if (currentSync != null && !matchOver) {
            sendAction(new GameAction.Resign());
        }
    }

    public void requestRematch() {
        if (practiceMode) {
            startPracticeMatch(practiceDifficulty);
            return;
        }
        connection.send(new RematchRequest());
    }

    public void leaveToLobby() {
        if (practiceMode) {
            stopBotTimer();
            if (practiceState != null && practiceState.outcome() == null) {
                matchParked = true;
                parkedRoomCode = "PRACTICE";
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
        if (currentSync != null && !matchOver) {
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

    public boolean hasParkedMatch() {
        return matchParked && parkedRoomCode != null;
    }

    public String parkedRoomCode() {
        return parkedRoomCode;
    }

    public void returnToParkedMatch() {
        if (!hasParkedMatch()) {
            return;
        }
        matchParked = false;
        screen = Screen.GAME;
        fire(ClientModelListener::onMatchResumed);
        if (practiceMode) {
            scheduleBotTurn();
        }
        fire(ClientModelListener::onMatchChanged);
    }

    /** Sends an authoritative game action adhering to single in-flight request discipline. */
    public boolean sendAction(GameAction action) {
        if (practiceMode) {
            if (practiceState == null || matchOver) {
                return false;
            }
            if (practiceState.turn() != PlayerColor.BLUE && practiceState.phase() != TurnPhase.OPENING) {
                return false;
            }
            PlayerView before = currentSync != null ? currentSync.view() : null;
            ActionResult result = RulesEngine.apply(practiceState, PlayerColor.BLUE, action);
            if (result instanceof ActionResult.Rejected rejected) {
                fire(listener -> listener.onError("Action rejected: " + rejected.error().name()));
                return false;
            }
            ActionResult.Accepted accepted = (ActionResult.Accepted) result;
            this.practiceState = accepted.nextState();
            if (accepted.events() != null) {
                for (PublicEvent event : accepted.events()) {
                    recordPublicEvent(event);
                }
            }
            long nextSeq = (currentSync != null ? currentSync.nextSequence() : 1L) + 1;
            PlayerView after = RulesEngine.viewFor(practiceState, PlayerColor.BLUE);
            this.currentSync = new MatchSync(currentSync.matchId(), "PRACTICE", after, false, nextSeq);
            this.lastPresentedRevision = after.revision();
            this.priorViewForPresentation = after;
            clearSelection();
            fire(ClientModelListener::onMatchChanged);
            List<PublicEvent> events = accepted.events() != null ? accepted.events() : List.of();
            fire(listener -> listener.onAcceptedPresentation(before, after, events));
            if (practiceState.outcome() != null) {
                announcePracticeGameOver();
            } else {
                scheduleBotTurn();
            }
            return true;
        }

        if (currentSync == null || matchOver || inFlightRequest != null) {
            return false;
        }
        long seq = currentSync.nextSequence();
        long rev = currentSync.view().revision();
        ActionRequest req = new ActionRequest(currentSync.matchId(), seq, rev, action);
        this.inFlightRequest = req;
        connection.send(req);
        fire(ClientModelListener::onMatchChanged);
        return true;
    }

    public void skipMovement() {
        if (canSkipMovement()) {
            sendAction(new GameAction.SkipMovement());
            clearSelection();
        }
    }

    public void endTurn() {
        if (canEndTurn()) {
            sendAction(new GameAction.EndTurn());
            clearSelection();
        }
    }

    public void castSkill() {
        if (canCastSkill()) {
            sendAction(new GameAction.CastSkill(selectedSkillInstanceId, skillOptions.completeTargets()));
            clearSelection();
        }
    }

    public void resolveForesight(List<String> order) {
        if (currentSync != null && currentSync.view().phase() == TurnPhase.CHOICE
                && currentSync.view().ownChoice() != null) {
            sendAction(new GameAction.ResolveForesight(currentSync.view().ownChoice().choiceId(), order));
        }
    }

    public void selectDirection(Direction dir) {
        if (selectedSkillInstanceId != null && skillDraft != null) {
            skillDraft = new SelectionDraft(skillDraft.pieceIds(), skillDraft.destinations(), dir);
            skillOptions = Targeting.skillOptions(currentSync.view(), selectedSkillInstanceId, skillDraft);
            fire(ClientModelListener::onMatchChanged);
        }
    }

    public void boardClicked(Square clicked) {
        if (currentSync == null || !myTurn() || inFlightRequest != null) {
            return;
        }
        PlayerView view = currentSync.view();
        if (view.phase() == TurnPhase.MOVEMENT) {
            if (highlightedTargets.contains(clicked)) {
                List<GameAction.MovePiece> legal = Targeting.legalMoves(view);
                for (GameAction.MovePiece m : legal) {
                    if (selectedPieceId != null && m.pieceId() == selectedPieceId
                            && m.to().equals(clicked)) {
                        sendAction(m);
                        clearSelection();
                        return;
                    }
                }
            }
            // Friendly piece selection
            PlacedPiece friendly = null;
            for (PlacedPiece pp : view.pieces()) {
                if (pp.square().equals(clicked) && pp.piece().color() == view.viewer()) {
                    friendly = pp;
                    break;
                }
            }
            if (friendly != null) {
                selectedSquare = clicked;
                selectedPieceId = friendly.piece().id();
            } else {
                selectedSquare = null;
                selectedPieceId = null;
            }
            recomputeMovementTargets();
            fire(ClientModelListener::onMatchChanged);
        } else if (view.phase() == TurnPhase.SKILL && selectedSkillInstanceId != null) {
            if (skillOptions != null) {
                PlacedPiece ppAtClicked = null;
                for (PlacedPiece pp : view.pieces()) {
                    if (pp.square().equals(clicked)) {
                        ppAtClicked = pp;
                        break;
                    }
                }
                if (ppAtClicked != null && skillOptions.selectablePieceIds().contains(ppAtClicked.piece().id())) {
                    List<Integer> newPieces = new ArrayList<>(skillDraft.pieceIds());
                    newPieces.add(ppAtClicked.piece().id());
                    skillDraft = new SelectionDraft(newPieces, skillDraft.destinations(), skillDraft.direction());
                    skillOptions = Targeting.skillOptions(view, selectedSkillInstanceId, skillDraft);
                    fire(ClientModelListener::onMatchChanged);
                    return;
                }
                if (skillOptions.selectableSquares().contains(clicked)) {
                    List<Square> newSquares = new ArrayList<>(skillDraft.destinations());
                    newSquares.add(clicked);
                    skillDraft = new SelectionDraft(skillDraft.pieceIds(), newSquares, skillDraft.direction());
                    skillOptions = Targeting.skillOptions(view, selectedSkillInstanceId, skillDraft);
                    fire(ClientModelListener::onMatchChanged);
                    return;
                }
            }
        }
    }

    public void cardClicked(String cardId) {
        if (!myTurn() || currentSync.view().phase() != TurnPhase.MOVEMENT) {
            return;
        }
        selectedCardId = Objects.equals(selectedCardId, cardId) ? null : cardId;
        selectedSkillInstanceId = null;
        skillDraft = null;
        skillOptions = null;
        recomputeMovementTargets();
        fire(ClientModelListener::onMatchChanged);
    }

    public void skillClicked(String instanceId) {
        if (!myTurn() || currentSync.view().phase() != TurnPhase.SKILL) {
            return;
        }
        if (Objects.equals(selectedSkillInstanceId, instanceId)) {
            clearSelection();
        } else {
            selectedCardId = null;
            selectedSquare = null;
            selectedPieceId = null;
            highlightedTargets = List.of();
            selectedSkillInstanceId = instanceId;
            skillDraft = SelectionDraft.empty();
            skillOptions = Targeting.skillOptions(currentSync.view(), selectedSkillInstanceId, skillDraft);
        }
        fire(ClientModelListener::onMatchChanged);
    }

    public void clearSelection() {
        selectedCardId = null;
        selectedSquare = null;
        selectedPieceId = null;
        highlightedTargets = List.of();
        selectedSkillInstanceId = null;
        skillDraft = null;
        skillOptions = null;
    }

    private void recomputeMovementTargets() {
        if (currentSync == null || !myTurn() || currentSync.view().phase() != TurnPhase.MOVEMENT) {
            highlightedTargets = List.of();
            return;
        }
        PlayerView view = currentSync.view();
        List<GameAction.MovePiece> legal = Targeting.legalMoves(view);
        List<Square> dests = new ArrayList<>();
        for (GameAction.MovePiece m : legal) {
            if (selectedPieceId == null || m.pieceId() != selectedPieceId) {
                continue;
            }
            if (!dests.contains(m.to())) {
                dests.add(m.to());
            }
        }
        highlightedTargets = List.copyOf(dests);
    }

    // ------------------------------------------------------------------
    // Message handling
    // ------------------------------------------------------------------

    public void handleMessage(Message message) {
        if (message instanceof LoginResponse response) {
            handleLoginResponse(response);
        } else if (message instanceof MatchmakingStatus status) {
            findingMatch = status.searching() && screen == Screen.LOBBY && !hasParkedMatch();
            fire(ClientModelListener::onLobbyChanged);
            if (status.error() != null) fire(listener -> listener.onError(status.error()));
        } else if (message instanceof MatchCreated created) {
            pendingRoomCode = created.roomCode();
            fire(listener -> listener.onMatchCreated(created.roomCode()));
        } else if (message instanceof ListMatchesResponse response) {
            openMatches = response.openMatches();
            fire(ClientModelListener::onLobbyChanged);
        } else if (message instanceof LeaderboardResponse response) {
            leaderboard = response.topPlayers();
            fire(ClientModelListener::onLeaderboardChanged);
        } else if (message instanceof MatchSync sync) {
            handleMatchSync(sync);
        } else if (message instanceof ActionReply reply) {
            handleActionReply(reply);
        } else if (message instanceof GameUpdate update) {
            handleGameUpdate(update);
        } else if (message instanceof ChatMessage chat) {
            if (!practiceMode && currentSync != null && currentSync.matchId().equals(chat.matchId())
                    && chat.sender() != null && ChatRequest.validText(chat.text())) {
                if (chatMessages.size() == 200) chatMessages.remove(0);
                chatMessages.add(chat);
                fire(ClientModelListener::onChatChanged);
            }
        } else if (message instanceof GameOver over) {
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
        }
    }

    public void handleMatchSync(MatchSync sync) {
        findingMatch = false;
        boolean sameMatch = currentSync != null && currentSync.matchId().equals(sync.matchId());
        if (sameMatch && sync.view().revision() < currentSync.view().revision()) {
            return;
        }
        ActionRequest pending = sameMatch ? inFlightRequest : null;
        if (!sameMatch) {
            clearMatchState();
        }
        practiceMode = false;
        this.currentSync = sync;
        this.inFlightRequest = pending;
        this.roomCode = sync.roomCode();
        this.matchOver = sync.view().outcome() != null;
        this.matchParked = false;
        this.parkedRoomCode = null;
        this.pendingRoomCode = null;
        this.rematchOfferedByOpponent = false;
        if (!sameMatch) {
            this.gameOverAnnounced = false;
        }
        // Baseline reset on initial sync or reconnect: historical moves are not replayed
        if (!sameMatch || presentationMatchId == null || !presentationMatchId.equals(sync.matchId())) {
            this.presentationMatchId = sync.matchId();
            this.lastPresentedRevision = sync.view().revision();
            this.priorViewForPresentation = sync.view();
        } else if (sync.view().revision() > lastPresentedRevision) {
            this.lastPresentedRevision = sync.view().revision();
            this.priorViewForPresentation = sync.view();
        }
        fire(ClientModelListener::onPresentationReset);
        clearSelection();
        // Even an already-committed request must recover its receipt using the
        // original payload; assigning a new sequence could apply it twice.
        if (pending != null) {
            connection.send(pending);
        }
        setScreen(Screen.GAME);
        fire(ClientModelListener::onMatchChanged);
    }

    public void handleActionReply(ActionReply reply) {
        if (currentSync != null && reply.matchId().equals(currentSync.matchId())) {
            if (inFlightRequest != null && reply.sequence() == inFlightRequest.sequence()) {
                inFlightRequest = null;
            }
            if (!reply.receipt().accepted()) {
                fire(listener -> listener.onError("Action rejected: " + reply.receipt().error()));
            }
            if (reply.current() != null) {
                MatchSync sync = reply.current();
                long rev = sync.view().revision();
                if (presentationMatchId == null || !presentationMatchId.equals(sync.matchId())) {
                    presentationMatchId = sync.matchId();
                    lastPresentedRevision = rev;
                    priorViewForPresentation = sync.view();
                } else if (rev > lastPresentedRevision && priorViewForPresentation == null) {
                    priorViewForPresentation = currentSync != null ? currentSync.view() : null;
                }
                adoptSync(sync);
            }
        }
    }

    public void handleGameUpdate(GameUpdate update) {
        MatchSync sync = update.current();
        if (currentSync == null || !currentSync.matchId().equals(sync.matchId())
                || !sync.matchId().equals(presentationMatchId)
                || sync.view().revision() < currentSync.view().revision()
                || sync.view().revision() <= lastPresentedRevision) return;
        // An action reply can adopt this same revision before its event-bearing update.
        PlayerView before = priorViewForPresentation;
        lastPresentedRevision = sync.view().revision();
        priorViewForPresentation = sync.view();
        for (PublicEvent event : update.events()) recordPublicEvent(event);
        adoptSync(sync);
        fire(listener -> listener.onAcceptedPresentation(before, sync.view(), update.events()));
    }

    public void adoptSync(MatchSync sync) {
        if (sync == null) return;
        if (currentSync != null && (!currentSync.matchId().equals(sync.matchId())
                || sync.view().revision() < currentSync.view().revision())) {
            return;
        }
        boolean revChanged = currentSync == null || sync.view().revision() != currentSync.view().revision();
        this.currentSync = sync;
        if (sync.view().outcome() != null) {
            this.matchOver = true;
        }
        if (revChanged) {
            clearSelection();
        }
        fire(ClientModelListener::onMatchChanged);
    }

    private void recordPublicEvent(PublicEvent event) {
        if (event instanceof PublicEvent.PieceMoved move) {
            String fromStr = squareName(move.from());
            String toStr = squareName(move.to());
            historyMoves.add(new MoveInfo(historyMoves.size() + 1,
                    "Move", fromStr, toStr, false, false));
            lastMoveFrom = move.from();
            lastMoveTo = move.to();
        } else if (event instanceof PublicEvent.PieceEliminated elim) {
            lastCaptureSquare = elim.at();
        } else if (event instanceof PublicEvent.MovementPlayed played) {
            playedCards.add(new PlayedCard(currentSync.view().startedTurns(), played.actor(), played.cardId(), false));
            historyMoves.add(new MoveInfo(historyMoves.size() + 1,
                    played.cardId(), "", "", false, false));
        } else if (event instanceof PublicEvent.SkillCommitted committed) {
            playedCards.add(new PlayedCard(currentSync.view().startedTurns(), committed.actor(),
                    committed.skill().definitionId(), true));
            historyMoves.add(new MoveInfo(historyMoves.size() + 1,
                    committed.skill().instanceId(), "", "", false, false));
        }
    }

    public void handleConnectionLost() {
        findingMatch = false;
        fire(ClientModelListener::onLobbyChanged);
        String token = reconnectToken;
        if (token != null) {
            fire(ClientModelListener::onConnectionLost);
            attemptReconnect(token, 3);
        } else {
            clearMatchState();
            me = null;
            setScreen(Screen.LOGIN);
            fire(ClientModelListener::onConnectionLost);
        }
    }

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
            findingMatch = false;
            clearMatchState();
            me = response.profile();
            reconnectToken = response.reconnectToken();
            setScreen(Screen.LOBBY);
            refreshMatches();
        } else {
            fire(listener -> listener.onLoginFailed(response.errorText()));
        }
    }

    public void startPracticeMatch(Difficulty difficulty) {
        startPracticeMatch(difficulty, new Random().nextLong());
    }

    // Seeded entry point keeps practice startup reproducible in regression tests.
    void startPracticeMatch(Difficulty difficulty, long seed) {
        if (hasParkedMatch() || findingMatch) {
            fire(listener -> listener.onError(
                    "You have an ongoing match — return to it first."));
            return;
        }
        stopBotTimer();
        clearMatchState();
        this.practiceMode = true;
        this.practiceDifficulty = difficulty != null ? difficulty : Difficulty.ROOKIE;
        this.practiceRandom = new Random(seed);
        RulesConfig rules = RulesConfigLoader.load(null);
        this.practiceState = RulesEngine.newOpeningGame(rules, seed);
        this.currentSync = new MatchSync("practice-" + java.util.UUID.randomUUID(), "PRACTICE",
                RulesEngine.viewFor(practiceState, PlayerColor.BLUE), false, 1L);
        this.presentationMatchId = currentSync.matchId();
        this.lastPresentedRevision = 0;
        this.priorViewForPresentation = currentSync.view();
        this.inFlightRequest = null;
        this.roomCode = "PRACTICE";
        this.opponentName = "Practice Bot (" + practiceDifficulty + ")";
        setScreen(Screen.GAME);
        fire(ClientModelListener::onMatchChanged);
        scheduleBotTurn();
    }

    private boolean matchEntryPending;
    private boolean openingPresentationPending;

    /** Holds local practice turns while the match-entry screen is visible. */
    public void setMatchEntryPending(boolean pending) {
        matchEntryPending = pending;
        if (pending) stopBotTimer();
        else if (screen == Screen.GAME) scheduleBotTurn();
    }

    /** Holds practice gameplay until the opening cards land; the bot may still confirm its hand. */
    public void setOpeningPresentationPending(boolean pending) {
        if (openingPresentationPending == pending) return;
        openingPresentationPending = pending;
        if (pending && practiceState != null && practiceState.phase() != TurnPhase.OPENING) stopBotTimer();
        else if (!pending && screen == Screen.GAME) scheduleBotTurn();
    }

    private boolean openingAllowsBotAction() {
        return !openingPresentationPending || practiceState.phase() == TurnPhase.OPENING;
    }

    private boolean botNeedsAction() {
        return practiceState.phase() == TurnPhase.OPENING
                ? !practiceState.openingConfirmed(PlayerColor.RED) : practiceState.turn() == PlayerColor.RED;
    }

    private void scheduleBotTurn() {
        if (!matchEntryPending && screen == Screen.GAME && practiceMode && practiceState != null
                && openingAllowsBotAction() && botNeedsAction()
                && practiceState.outcome() == null) {
            botTimer.restart();
        }
    }

    private void stopBotTimer() {
        botTimer.stop();
    }

    void stepBot() {
        if (matchEntryPending || screen != Screen.GAME || !practiceMode || practiceState == null
                || !openingAllowsBotAction() || !botNeedsAction()
                || practiceState.outcome() != null) {
            return;
        }
        PlayerView botView = RulesEngine.viewFor(practiceState, PlayerColor.RED);
        GameAction botAction = PracticeBot.chooseAction(botView, practiceDifficulty, practiceRandom);
        if (botAction == null) {
            return;
        }
        PlayerView before = currentSync != null ? currentSync.view() : null;
        ActionResult result = RulesEngine.apply(practiceState, PlayerColor.RED, botAction);
        if (result instanceof ActionResult.Accepted accepted) {
            this.practiceState = accepted.nextState();
            if (accepted.events() != null) {
                for (PublicEvent event : accepted.events()) {
                    recordPublicEvent(event);
                }
            }
            long nextSeq = (currentSync != null ? currentSync.nextSequence() : 1L) + 1;
            PlayerView after = RulesEngine.viewFor(practiceState, PlayerColor.BLUE);
            this.currentSync = new MatchSync(currentSync.matchId(), "PRACTICE", after, false, nextSeq);
            this.lastPresentedRevision = after.revision();
            this.priorViewForPresentation = after;
            clearSelection();
            fire(ClientModelListener::onMatchChanged);
            List<PublicEvent> events = accepted.events() != null ? accepted.events() : List.of();
            fire(listener -> listener.onAcceptedPresentation(before, after, events));
            if (practiceState.outcome() != null) {
                announcePracticeGameOver();
            } else {
                scheduleBotTurn();
            }
        }
    }

    private void announcePracticeGameOver() {
        stopBotTimer();
        matchOver = true;
        fire(listener -> listener.onGameOver(
                new GameOver(practiceState.outcome().winner(), practiceState.outcome().reason(), 0, 0)));
    }

    public boolean isPracticeMode() {
        return practiceMode;
    }

    public Difficulty practiceDifficulty() {
        return practiceDifficulty;
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        fire(ClientModelListener::onMatchChanged);
    }

    private void clearMatchState() {
        fire(ClientModelListener::onPresentationReset);
        stopBotTimer();
        openingPresentationPending = false;
        practiceMode = false;
        practiceState = null;
        practiceRandom = null;
        currentSync = null;
        chatMessages.clear();
        fire(ClientModelListener::onChatChanged);
        inFlightRequest = null;
        roomCode = null;
        opponentName = null;
        pendingRoomCode = null;
        historyMoves.clear();
        playedCards.clear();
        rematchOfferedByOpponent = false;
        gameOverAnnounced = false;
        matchOver = false;
        presentationMatchId = null;
        lastPresentedRevision = -1;
        priorViewForPresentation = null;
        clearSelection();
    }

    private void setScreen(Screen newScreen) {
        screen = newScreen;
        fire(listener -> listener.onScreenChanged(newScreen));
    }

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
    // Getters
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

    public String pendingRoomCode() {
        return pendingRoomCode;
    }

    public MatchSync currentSync() {
        return currentSync;
    }

    public PlayerView view() {
        return currentSync != null ? currentSync.view() : null;
    }

    public PlayerColor myColor() {
        return view() != null ? view().viewer() : null;
    }

    public PlayerColor turn() {
        return view() != null ? view().turn() : null;
    }

    public TurnPhase phase() {
        return view() != null ? view().phase() : null;
    }

    public RulesConfig rules() {
        return view() != null ? view().rules() : null;
    }

    public String roomCode() {
        return roomCode;
    }

    public String opponentName() {
        return opponentName == null ? "OPPONENT" : opponentName;
    }

    public List<MoveInfo> historyMoves() {
        return List.copyOf(historyMoves);
    }

    private static String squareName(Square square) {
        if (square == null) return "";
        return "" + (char) ('A' + square.x()) + (square.y() + 1);
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

    public boolean isMatchOver() {
        return matchOver;
    }

    public boolean myTurn() {
        return view() != null && view().viewer() == view().turn()
                && view().outcome() == null && !matchOver;
    }

    public boolean canSkipMovement() {
        return myTurn() && view().phase() == TurnPhase.MOVEMENT && inFlightRequest == null;
    }

    public boolean canEndTurn() {
        return myTurn() && (view().phase() == TurnPhase.SKILL || view().phase() == TurnPhase.END)
                && inFlightRequest == null;
    }

    public boolean canCastSkill() {
        return skillOptions != null && skillOptions.canSubmit() && inFlightRequest == null;
    }

    public String selectedCardId() {
        return selectedCardId;
    }

    public Square selectedSquare() {
        return selectedSquare;
    }

    public Integer selectedPieceId() {
        return selectedPieceId;
    }

    public List<Square> highlightedTargets() {
        return highlightedTargets;
    }

    public String selectedSkillInstanceId() {
        return selectedSkillInstanceId;
    }

    public SelectionDraft skillDraft() {
        return skillDraft;
    }

    public SelectionOptions skillOptions() {
        return skillOptions;
    }

    public Square lastCaptureSquare() {
        return lastCaptureSquare;
    }

    public Square lastMoveFrom() {
        return lastMoveFrom;
    }

    public Square lastMoveTo() {
        return lastMoveTo;
    }

    public ActionRequest inFlightRequest() {
        return inFlightRequest;
    }
}
