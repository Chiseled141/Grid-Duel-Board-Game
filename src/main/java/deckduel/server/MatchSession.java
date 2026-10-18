package deckduel.server;

import deckduel.core.Elo;
import deckduel.core.PlayerColor;
import deckduel.core.duel.ActionError;
import deckduel.core.duel.ActionResult;
import deckduel.core.duel.EndReason;
import deckduel.core.duel.GameAction;
import deckduel.core.duel.GameOutcome;
import deckduel.core.duel.GameState;
import deckduel.core.duel.PlayerView;
import deckduel.core.duel.PublicEvent;
import deckduel.core.duel.RulesConfig;
import deckduel.core.duel.RulesEngine;
import deckduel.core.duel.TurnPhase;
import deckduel.db.UserDao;
import deckduel.net.ActionReceipt;
import deckduel.net.ActionReply;
import deckduel.net.ActionRequest;
import deckduel.net.ChatRequest;
import deckduel.net.ChatMessage;
import deckduel.net.ErrorMessage;
import deckduel.net.GameOver;
import deckduel.net.GameUpdate;
import deckduel.net.MatchSync;
import deckduel.net.Message;
import deckduel.net.OpponentLeft;
import deckduel.net.RematchAccept;
import deckduel.net.UserProfile;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One live Deck Duel match session.
 * All mutations of the authoritative {@link GameState} run on a single-thread executor.
 * Concurrency model: thread-confined state, lock-free engine execution.
 */
public final class MatchSession {

    private static final Logger LOG = Logger.getLogger(MatchSession.class.getName());
    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

    private final GameServer server;
    private final String roomCode;
    private final String blueUsername;
    private final String redUsername;
    private final Map<PlayerColor, ClientHandler> players = new EnumMap<>(PlayerColor.class);
    private final ExecutorService executor;
    private final RulesConfig rulesConfig;

    private static final class PlayerTrack {
        long nextSequence = 1;
        Long lastChatNanos;
        ActionRequest lastRequest;
        ActionReceipt lastReceipt;

        void reset() {
            nextSequence = 1;
            lastChatNanos = null;
            lastRequest = null;
            lastReceipt = null;
        }
    }

    private final Map<PlayerColor, PlayerTrack> tracks = new EnumMap<>(PlayerColor.class);
    private String matchId;
    private GameState state;
    private volatile boolean finished;
    private GameOver gameOver;
    private boolean blueWantsRematch;
    private boolean redWantsRematch;
    private ScheduledFuture<?> forfeitTimer;
    private final Set<PlayerColor> disconnected = EnumSet.noneOf(PlayerColor.class);
    private boolean paused;

    /** Creates a session between two authenticated handlers; call {@link #start()} next. */
    public MatchSession(GameServer server, String roomCode, ClientHandler blue, ClientHandler red) {
        this.server = server;
        this.roomCode = roomCode;
        this.blueUsername = blue.username();
        this.redUsername = red.username();
        this.players.put(PlayerColor.BLUE, blue);
        this.players.put(PlayerColor.RED, red);
        this.tracks.put(PlayerColor.BLUE, new PlayerTrack());
        this.tracks.put(PlayerColor.RED, new PlayerTrack());
        this.rulesConfig = server.config().rulesConfig();
        int id = SESSION_COUNTER.incrementAndGet();
        this.executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "deck-duel-match-" + id));
        blue.joinMatch(this, PlayerColor.BLUE);
        red.joinMatch(this, PlayerColor.RED);
    }

    /** Deals the match and sends MatchSync to both players. */
    public void start() {
        submit(this::beginNewGame);
    }

    /** Deals a fresh game and announces it to both players. */
    private void beginNewGame() {
        matchId = UUID.randomUUID().toString();
        tracks.get(PlayerColor.BLUE).reset();
        tracks.get(PlayerColor.RED).reset();
        long seed = server.config().dealRandom().nextLong();
        state = RulesEngine.newOpeningGame(rulesConfig, seed);
        paused = false;
        announceMatchStart();
    }

    /**
     * Submits an gameplay action request from a connected client.
     */
    public void submitAction(ClientHandler sender, ActionRequest request) {
        submit(() -> {
            PlayerColor actor = colorOf(sender);
            if (actor == null || !ownsHandler(actor)) {
                sender.send(new ErrorMessage("you are not an active player in this match"));
                return;
            }
            if (!this.matchId.equals(request.matchId())) {
                sender.send(new ErrorMessage("stale or invalid matchId: " + request.matchId()));
                return;
            }

            PlayerTrack track = tracks.get(actor);
            long seq = request.sequence();
            long nextSeq = track.nextSequence;

            if (seq == nextSeq - 1) {
                if (Objects.equals(track.lastRequest, request)) {
                    sender.send(new ActionReply(matchId, seq, track.lastReceipt, createSync(actor)));
                    return;
                } else {
                    ActionReceipt receipt = new ActionReceipt(false, state.revision(), ActionError.SEQUENCE_CONFLICT);
                    sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                    return;
                }
            } else if (seq < nextSeq - 1) {
                ActionReceipt receipt = new ActionReceipt(false, state.revision(), ActionError.OLD_SEQUENCE);
                sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                return;
            } else if (seq > nextSeq) {
                ActionReceipt receipt = new ActionReceipt(false, state.revision(), ActionError.SEQUENCE_GAP);
                sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                return;
            }

            // Consume sequence
            track.nextSequence = nextSeq + 1;
            track.lastRequest = request;

            if (paused && !(request.action() instanceof GameAction.Resign)) {
                ActionReceipt receipt = new ActionReceipt(false, state.revision(), ActionError.MATCH_PAUSED);
                track.lastReceipt = receipt;
                sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                return;
            }

            // The two opening choices are independent; an opponent's confirmation
            // must not reject a choice already submitted against the initial view.
            boolean independentOpening = request.action() instanceof GameAction.ConfirmOpening
                    && state.phase() == deckduel.core.duel.TurnPhase.OPENING
                    && request.expectedRevision() >= 0 && request.expectedRevision() <= state.revision();
            if (request.expectedRevision() != state.revision() && !independentOpening) {
                ActionReceipt receipt = new ActionReceipt(false, state.revision(), ActionError.STALE_REVISION);
                track.lastReceipt = receipt;
                sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                return;
            }

            ActionResult result = RulesEngine.apply(state, actor, request.action());
            if (result instanceof ActionResult.Rejected rej) {
                ActionReceipt receipt = new ActionReceipt(false, state.revision(), rej.error());
                track.lastReceipt = receipt;
                sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));
                return;
            }

            ActionResult.Accepted acc = (ActionResult.Accepted) result;
            this.state = acc.nextState();
            ActionReceipt receipt = new ActionReceipt(true, state.revision(), null);
            track.lastReceipt = receipt;

            sendGameUpdates(acc.events());
            sender.send(new ActionReply(matchId, seq, receipt, createSync(actor)));

            if (state.phase() == TurnPhase.FINISHED) {
                GameOutcome outcome = state.outcome();
                finish(outcome.winner(), outcome.reason());
            }
        });
    }

    /** Relays validated chat on the match executor without changing gameplay state. */
    public void submitChat(ClientHandler sender, ChatRequest request) {
        submit(() -> {
            PlayerColor actor = colorOf(sender);
            if (actor == null || !chatParticipantsPresent() || finished || paused
                    || !Objects.equals(matchId, request.matchId())) {
                sender.send(new ErrorMessage("Chat requires the current live match and both players connected."));
                return;
            }
            if (!ChatRequest.validText(request.text())) {
                sender.send(new ErrorMessage("Chat must contain 1–280 characters on a single line."));
                return;
            }
            PlayerTrack track = tracks.get(actor);
            long now = System.nanoTime();
            if (track.lastChatNanos != null
                    && now - track.lastChatNanos < TimeUnit.MILLISECONDS.toNanos(500)) {
                sender.send(new ErrorMessage("Please wait a moment before sending another message."));
                return;
            }
            track.lastChatNanos = now;
            broadcast(new ChatMessage(matchId, sender.username(), request.text().strip()));
        });
    }

    private boolean chatParticipantsPresent() {
        ClientHandler blue = players.get(PlayerColor.BLUE);
        ClientHandler red = players.get(PlayerColor.RED);
        // A handler can log in again or enter a new match before its old session retires.
        return blue.currentMatch() == this && red.currentMatch() == this
                && blueUsername.equals(blue.username()) && redUsername.equals(red.username());
    }

    /** Records a rematch vote; a fresh game starts once both players voted. */
    public void submitRematch(ClientHandler sender) {
        submit(() -> {
            PlayerColor color = colorOf(sender);
            String rejection = null;
            boolean restart = false;
            synchronized (server.lobby()) {
                if (color == null || players.values().stream().anyMatch(player -> player.currentMatch() != this)) {
                    rejection = "A player has left this match. Find another opponent.";
                } else if (!finished) {
                    rejection = "the game is still running";
                } else {
                    if (color == PlayerColor.BLUE) blueWantsRematch = true;
                    else redWantsRematch = true;
                    restart = blueWantsRematch && redWantsRematch;
                    if (restart) {
                        blueWantsRematch = false;
                        redWantsRematch = false;
                        disconnected.clear();
                        // Reserve the handlers before a competing lobby admission can claim them.
                        finished = false;
                        gameOver = null;
                    }
                }
            }
            if (rejection != null) sender.send(new ErrorMessage(rejection));
            else {
                sendTo(color.opponent(), new RematchAccept());
                if (restart) beginNewGame();
            }
        });
    }

    /**
     * A player's connection dropped. If the game is running, the opponent is notified,
     * match is paused, and a forfeit timer is scheduled.
     */
    public void onDisconnect(ClientHandler handler) {
        submit(() -> {
            PlayerColor color = colorOf(handler);
            if (color == null || players.get(color) != handler) {
                return;
            }
            disconnected.add(color);
            if (finished) {
                maybeRetire();
                return;
            }
            paused = true;
            int graceSeconds = server.config().graceSeconds();
            sendTo(color.opponent(), new OpponentLeft(graceSeconds));
            sendTo(color.opponent(), createSync(color.opponent()));

            forfeitTimer = server.timers().schedule(
                    () -> submit(() -> {
                        if (!finished && players.get(color) == handler) {
                            ActionResult result = RulesEngine.forfeit(state, color);
                            if (result instanceof ActionResult.Accepted acc) {
                                state = acc.nextState();
                                sendGameUpdates(acc.events());
                                finish(color.opponent(), EndReason.FORFEIT);
                            }
                        }
                    }),
                    graceSeconds, TimeUnit.SECONDS);
        });
    }

    /**
     * A player reconnected: adopt the new handler, cancel forfeit timer, restore state.
     */
    public void onReconnect(ClientHandler newHandler, PlayerColor color) {
        submit(() -> {
            String expected = color == PlayerColor.BLUE ? blueUsername : redUsername;
            if (newHandler.currentMatch() != this || !expected.equals(newHandler.username())) return;
            players.put(color, newHandler);
            disconnected.remove(color);
            if (forfeitTimer != null) {
                forfeitTimer.cancel(false);
                forfeitTimer = null;
            }
            if (disconnected.isEmpty()) {
                paused = false;
            }
            if (finished) {
                newHandler.send(gameOver);
            } else {
                newHandler.send(createSync(color));
                if (!paused) {
                    sendTo(color.opponent(), createSync(color.opponent()));
                }
            }
        });
    }

    /** Stops the session immediately; shuts down executor. */
    public void abort() {
        executor.shutdownNow();
    }

    /** The room code this match was created from. */
    String roomCode() {
        return roomCode;
    }

    /** The current active match ID. */
    public String matchId() {
        return matchId;
    }

    /** True while this session still owns its players. */
    public boolean isActive() {
        return !finished;
    }

    private void announceMatchStart() {
        for (Map.Entry<PlayerColor, ClientHandler> entry : players.entrySet()) {
            PlayerColor color = entry.getKey();
            sendTo(color, createSync(color));
        }
    }

    private void sendGameUpdates(List<PublicEvent> events) {
        for (Map.Entry<PlayerColor, ClientHandler> entry : players.entrySet()) {
            PlayerColor color = entry.getKey();
            MatchSync sync = createSync(color);
            sendTo(color, new GameUpdate(sync, events));
        }
    }

    private MatchSync createSync(PlayerColor viewer) {
        PlayerView view = RulesEngine.project(state, viewer);
        long nextSeq = tracks.get(viewer).nextSequence;
        return new MatchSync(matchId, roomCode, view, paused, nextSeq);
    }

    private void broadcast(Message message) {
        for (PlayerColor color : PlayerColor.values()) sendTo(color, message);
    }

    private boolean ownsHandler(PlayerColor color) {
        ClientHandler handler = players.get(color);
        String expected = color == PlayerColor.BLUE ? blueUsername : redUsername;
        return handler.currentMatch() == this && expected.equals(handler.username());
    }

    private void sendTo(PlayerColor color, Message message) {
        // A finished or abandoned session must never send into a new account/match.
        if (ownsHandler(color)) players.get(color).send(message);
    }

    private void finish(PlayerColor winner, EndReason way) {
        if (finished) {
            return;
        }
        finished = true;
        MatchResult result = null;
        try {
            UserDao userDao = server.userDao();
            UserProfile blue = userDao.profileOf(blueUsername);
            UserProfile red = userDao.profileOf(redUsername);
            Elo.Result elo = Elo.update(blue.elo(), red.elo(), winner);
            result = new MatchResult(roomCode, blueUsername, redUsername,
                    winner, way, elo.blue(), elo.red(),
                    state.rules().rulesVersion(), state.startedTurns(), state.completedTurns());
            server.onMatchFinished(result);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "match " + roomCode + ": recording the result failed", e);
        } finally {
            final MatchResult recorded = result;
            gameOver = new GameOver(winner, way,
                    recorded == null ? 0 : recorded.blueEloAfter(),
                    recorded == null ? 0 : recorded.redEloAfter());
            broadcast(gameOver);
            releaseTokens();
            server.removeLiveMatch(roomCode);
            maybeRetire();
            LOG.info(() -> "match " + roomCode + " finished: " + way + ", winner="
                    + (recorded == null ? null : recorded.winnerUsername())
                    + ", startedTurns=" + state.startedTurns()
                    + ", completedTurns=" + state.completedTurns()
                    + (recorded == null ? " (result not recorded)" : ""));
        }
    }

    private void maybeRetire() {
        if (disconnected.size() == 2) {
            executor.shutdown();
        }
    }

    private void releaseTokens() {
        for (PlayerColor color : PlayerColor.values()) {
            if (!ownsHandler(color)) continue;
            String token = players.get(color).reconnectToken();
            if (token != null) {
                server.registry().clearToken(token);
            }
        }
    }

    private PlayerColor colorOf(ClientHandler handler) {
        for (Map.Entry<PlayerColor, ClientHandler> entry : players.entrySet()) {
            if (entry.getValue() == handler) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void submit(Runnable task) {
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "task after match end ignored", e);
        }
    }
}
