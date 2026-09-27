package onitama.server;

import onitama.core.CardDeck;
import onitama.core.DealSnapshot;
import onitama.core.Elo;
import onitama.core.GameState;
import onitama.core.HalfMove;
import onitama.core.IllegalMoveException;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.WinCondition;
import onitama.db.UserDao;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.MatchStart;
import onitama.net.Message;
import onitama.net.MoveApplied;
import onitama.net.MoveRejected;
import onitama.net.MoveRequest;
import onitama.net.OpponentLeft;
import onitama.net.RematchAccept;
import onitama.net.UserProfile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One live game. All mutations of the authoritative {@link GameState} happen
 * as tasks on a single-thread executor, so the game state is thread-confined
 * and needs no locks — the concurrency model of the whole server in one
 * class. Public methods only submit tasks and return immediately; they may
 * be called from any thread (client handler reader loops, grace timers).
 */
public final class MatchSession {

    private static final Logger LOG = Logger.getLogger(MatchSession.class.getName());
    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

    private final GameServer server;
    private final String roomCode;
    private final String blueUsername;
    private final String redUsername;
    private final Map<PlayerColor, ClientHandler> players = new EnumMap<>(PlayerColor.class);
    private final List<HalfMove> history = new ArrayList<>();
    private final ExecutorService executor;

    private GameState state;
    private DealSnapshot deal;
    private volatile boolean finished;
    private GameOver gameOver;
    private boolean blueWantsRematch;
    private boolean redWantsRematch;
    private ScheduledFuture<?> forfeitTimer;
    /** Colors currently without a live connection; confined to the executor. */
    private final Set<PlayerColor> disconnected = EnumSet.noneOf(PlayerColor.class);

    /** Creates a session between two authenticated handlers; call {@link #start()} next. */
    public MatchSession(GameServer server, String roomCode, ClientHandler blue, ClientHandler red) {
        this.server = server;
        this.roomCode = roomCode;
        this.blueUsername = blue.username();
        this.redUsername = red.username();
        this.players.put(PlayerColor.BLUE, blue);
        this.players.put(PlayerColor.RED, red);
        int id = SESSION_COUNTER.incrementAndGet();
        this.executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "onitama-match-" + id));
        blue.joinMatch(this, PlayerColor.BLUE);
        red.joinMatch(this, PlayerColor.RED);
    }

    /** Deals the cards and sends {@link MatchStart} to both players. */
    public void start() {
        submit(this::beginNewGame);
    }

    /** Deals a fresh game, resets the history and announces it to both players. */
    private void beginNewGame() {
        CardDeck.Deal deal = CardDeck.deal(server.config().dealRandom());
        state = GameState.newGame(deal);
        this.deal = new DealSnapshot(deal.blueHand(), deal.redHand(), deal.transit());
        history.clear();
        announceMatchStart();
    }

    /** Validates and applies a move requested by {@code sender}. */
    public void submitMove(ClientHandler sender, MoveRequest request) {
        submit(() -> {
            PlayerColor color = colorOf(sender);
            if (color == null) {
                sender.send(new MoveRejected("you are not in this match"));
                return;
            }
            if (finished) {
                sender.send(new MoveRejected("the game is already over"));
                return;
            }
            if (state.turn() != color) {
                sender.send(new MoveRejected("it is not your turn"));
                return;
            }
            Move move = new Move(request.from(), request.to(), request.cardId());
            try {
                RulesEngine.apply(state, move);
                history.add(new HalfMove(state.moveNumber(), request.cardId(),
                        request.from(), request.to()));
                broadcast(new MoveApplied(state, move));
                if (!state.isOngoing()) {
                    finish(state.winner(), state.way());
                }
            } catch (IllegalMoveException e) {
                sender.send(new MoveRejected(e.getMessage()));
            }
        });
    }

    /** Applies a pass requested by {@code sender} (only legal with no legal move). */
    public void submitPass(ClientHandler sender, String cardIdToDiscard) {
        submit(() -> {
            PlayerColor color = colorOf(sender);
            if (color == null || finished) {
                sender.send(new MoveRejected("no active game"));
                return;
            }
            try {
                RulesEngine.pass(state, cardIdToDiscard);
                history.add(new HalfMove(state.moveNumber(), cardIdToDiscard, null, null));
                broadcast(new MoveApplied(state, null));
                if (!state.isOngoing()) {
                    finish(state.winner(), state.way());
                }
            } catch (IllegalMoveException e) {
                sender.send(new MoveRejected(e.getMessage()));
            }
        });
    }

    /** The sender resigns; the opponent wins by forfeit. */
    public void submitResign(ClientHandler sender) {
        submit(() -> {
            PlayerColor color = colorOf(sender);
            if (color != null && !finished) {
                finish(color.opponent(), WinCondition.FORFEIT);
            }
        });
    }

    /** Records a rematch vote; a fresh game starts once both players voted. */
    public void submitRematch(ClientHandler sender) {
        submit(() -> {
            PlayerColor color = colorOf(sender);
            if (color == null) {
                return;
            }
            if (!finished) {
                sender.send(new ErrorMessage("the game is still running"));
                return;
            }
            if (color == PlayerColor.BLUE) {
                blueWantsRematch = true;
            } else {
                redWantsRematch = true;
            }
            players.get(color.opponent()).send(new RematchAccept());
            if (blueWantsRematch && redWantsRematch) {
                blueWantsRematch = false;
                redWantsRematch = false;
                disconnected.clear();
                finished = false;
                gameOver = null;
                beginNewGame();
            }
        });
    }

    /**
     * A player's connection dropped. If the game is still running, the
     * opponent is told and a grace timer is armed; if it fires before a
     * reconnect, the disconnected player forfeits. Events from a handler
     * that was already replaced by a reconnect are ignored.
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
            int graceSeconds = server.config().graceSeconds();
            players.get(color.opponent()).send(new OpponentLeft(graceSeconds));
            forfeitTimer = server.timers().schedule(
                    () -> submit(() -> {
                        if (!finished && players.get(color) == handler) {
                            finish(color.opponent(), WinCondition.FORFEIT);
                        }
                    }),
                    graceSeconds, TimeUnit.SECONDS);
        });
    }

    /**
     * A player re-attached through a new connection: adopt the new handler,
     * cancel the forfeit timer and deliver the current state.
     */
    public void onReconnect(ClientHandler newHandler, PlayerColor color) {
        submit(() -> {
            players.put(color, newHandler);
            disconnected.remove(color);
            if (forfeitTimer != null) {
                forfeitTimer.cancel(false);
                forfeitTimer = null;
            }
            if (finished) {
                newHandler.send(gameOver);
            } else {
                newHandler.send(new MoveApplied(state, state.lastMove()));
            }
        });
    }

    /** Stops the session immediately (server shutdown); no further messages. */
    public void abort() {
        executor.shutdownNow();
    }

    /** The room code this match was created from. */
    String roomCode() {
        return roomCode;
    }

    /**
     * True while this session still owns its players: the game is running or
     * finished-but-rematchable. Read from other threads to decide whether a
     * player may start something new.
     */
    public boolean isActive() {
        return !finished;
    }

    private void announceMatchStart() {
        for (Map.Entry<PlayerColor, ClientHandler> entry : players.entrySet()) {
            PlayerColor color = entry.getKey();
            String opponent = color == PlayerColor.BLUE ? redUsername : blueUsername;
            entry.getValue().send(new MatchStart(color, roomCode, opponent, state));
        }
    }

    private void broadcast(Message message) {
        players.values().forEach(handler -> handler.send(message));
    }

    /**
     * Ends the match: computes the Elo update, hands the result to the
     * server's persistence hook (match row + both players' stats in one
     * transaction, plus the replay file), then sends {@link GameOver} to both
     * players. The executor stays alive so the players can still agree on a
     * rematch; it is retired once both players are gone.
     */
    private void finish(PlayerColor winner, WinCondition way) {
        if (finished) {
            return;
        }
        finished = true;
        UserDao userDao = server.userDao();
        UserProfile blue = userDao.profileOf(blueUsername);
        UserProfile red = userDao.profileOf(redUsername);
        Elo.Result elo = Elo.update(blue.elo(), red.elo(), winner);
        MatchResult result = new MatchResult(roomCode, blueUsername, redUsername,
                winner, way, elo.blue(), elo.red(), deal, List.copyOf(history));
        server.onMatchFinished(result);

        gameOver = new GameOver(winner, way, elo.blue(), elo.red());
        broadcast(gameOver);
        releaseTokens();
        server.removeLiveMatch(roomCode);
        maybeRetire();
        LOG.info(() -> "match " + roomCode + " finished: " + way + ", winner="
                + result.winnerUsername() + ", half-moves=" + result.moveCount());
    }

    /**
     * Shuts the session executor down once the match is over and both
     * players have disconnected; until then the session stays available for
     * rematch votes and reconnect deliveries.
     */
    private void maybeRetire() {
        if (disconnected.size() == 2) {
            executor.shutdown();
        }
    }

    private void updateStats(UserDao userDao, UserProfile profile, boolean won, int newElo) {
        userDao.updateStats(profile.username(), newElo,
                profile.wins() + (won ? 1 : 0),
                profile.losses() + (!won ? 1 : 0));
    }

    private void releaseTokens() {
        for (ClientHandler handler : players.values()) {
            String token = handler.reconnectToken();
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

    /** Submits a task, converting a rejected task (match over) into a no-op. */
    private void submit(Runnable task) {
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "task after match end ignored", e);
        }
    }
}
