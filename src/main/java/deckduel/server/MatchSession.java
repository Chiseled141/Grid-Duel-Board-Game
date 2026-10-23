package deckduel.server;

import deckduel.core.EndReason;
import deckduel.core.PlayerColor;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchOutcome;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.Move;
import deckduel.core.card.PlannerBot;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RuleViolation;
import deckduel.core.card.Seat;
import deckduel.net.ActionRejected;
import deckduel.net.ErrorMessage;
import deckduel.net.MatchUpdate;
import deckduel.net.MoveRequest;
import deckduel.net.ProfileUpdate;
import java.security.SecureRandom;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One match between two seats. Every state change runs on the session's own single-thread
 * executor, so moves, timers and reconnects never interleave. The server builds both decks,
 * owns seeds, hidden cards and validation; each participant only ever receives their own
 * {@link MatchUpdate}. Each human seat's own view is also recorded for their replay once per state
 * revision, whether or not it reached them, so reconnects and retried requests add no frames.
 */
final class MatchSession {
    private static final Logger LOG = Logger.getLogger(MatchSession.class.getName());
    static final String EASY_BOT = "Practice bot (easy)", HARD_BOT = "Practice bot (hard)";

    /** One account's recording of the current game. */
    private static final class Recording {
        final long id;
        int revision = -1;
        // Set when a frame could not be stored; the replay is then marked incomplete.
        boolean missing;

        Recording(long id) { this.id = id; }
    }

    private static final class Participant {
        final Seat seat;
        final String username;
        final boolean bot;
        // Chooses the bot's moves from its own view; null for humans.
        final java.util.function.Function<PlayerView, Move> brain;
        ClientHandler handler;
        // Set on the caller's thread too, so a leaver gets no further match updates.
        volatile boolean left;
        boolean rematch;
        long lastAccepted = Long.MIN_VALUE;
        ScheduledFuture<?> grace;
        Recording recording;

        Participant(Seat seat, String username, ClientHandler handler, java.util.function.Function<PlayerView, Move> brain) {
            this.seat = seat;
            this.username = username;
            this.bot = brain != null;
            this.brain = brain;
            this.handler = handler;
        }
    }

    private final GameServer server;
    private final String id;
    private final boolean practice;
    private final Participant first, second;
    private final SecureRandom seeds = new SecureRandom();
    private final ExecutorService executor;
    private MatchState state;
    private int game = 1;
    // Published for the operator's status from the match thread; read from any thread.
    private volatile boolean inProgress = true;
    private volatile int heldSeats;

    private MatchSession(GameServer server, boolean practice, Participant first, Participant second) {
        this.server = server;
        this.practice = practice;
        this.first = first;
        this.second = second;
        byte[] bytes = new byte[5];
        seeds.nextBytes(bytes);
        id = "M" + HexFormat.of().formatHex(bytes).toUpperCase(java.util.Locale.ROOT);
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "deckduel-match-" + id);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** A recorded match between two connected accounts; {@code a} takes the first seat. */
    static MatchSession between(GameServer server, Entrant a, Entrant b) {
        return new MatchSession(server, false, new Participant(Seat.FIRST, a.username(), a.handler(), null),
                new Participant(Seat.SECOND, b.username(), b.handler(), null));
    }

    /** A practice match against the easy (greedy) or hard (planning) bot. */
    static MatchSession practice(GameServer server, Entrant human, boolean hard) {
        return new MatchSession(server, true, new Participant(Seat.FIRST, human.username(), human.handler(), null),
                new Participant(Seat.SECOND, hard ? HARD_BOT : EASY_BOT, null, hard ? PlannerBot::choose : GreedyBot::choose));
    }

    /** Usernames that must not be in another match while this one runs. */
    List<String> humanUsernames() {
        return practice ? List.of(first.username) : List.of(first.username, second.username);
    }

    /** Deals the first game. */
    void start() {
        submit(() -> {
            newGame();
            // A participant may have dropped while the match was being paired.
            for (Participant p : List.of(first, second))
                if (!p.bot && p.handler != null && p.handler.isClosed()) disconnect(p);
        });
    }

    /** Validates and applies one move; stale or illegal moves change nothing. */
    void move(ClientHandler handler, MoveRequest request) {
        submit(() -> {
            Participant p = participant(handler);
            if (p == null) return;
            if (request.requestId() == p.lastAccepted) { send(p); return; }
            if (state.phase() == MatchPhase.FINISHED) { reject(p, request, "The match is over."); return; }
            if (request.move() == null || request.revision() != state.revision()) {
                reject(p, request, "The table changed before your move arrived. Please choose again.");
                send(p);
                return;
            }
            try {
                state = state.apply(p.seat, request.move());
            } catch (RuleViolation violation) {
                reject(p, request, violation.getMessage());
                return;
            }
            p.lastAccepted = request.requestId();
            changed();
        });
    }

    /** Leaves the match; an unfinished match is forfeited. */
    void leave(ClientHandler handler) {
        String username = handler.username();
        // Release synchronously so the player's next lobby request is not refused as "busy".
        server.matches().release(username, this);
        Participant leaving = byName(username);
        if (leaving != null && leaving.handler == handler) leaving.left = true;
        submit(() -> {
            Participant p = byName(username);
            if (p == null || p.handler != handler) return;
            depart(p);
        });
    }

    /** Asks for a rematch; the next game starts once both players asked (the bot always agrees). */
    void rematch(ClientHandler handler) {
        submit(() -> {
            Participant p = participant(handler);
            if (p == null || state.phase() != MatchPhase.FINISHED) return;
            Participant other = other(p);
            if (other.left) { handler.send(new ErrorMessage("Your opponent has left; a rematch is not possible.")); return; }
            if (server.draining()) { handler.send(new ErrorMessage(ClientHandler.MAINTENANCE)); return; }
            p.rematch = true;
            if (other.bot) other.rematch = true;
            if (other.rematch) {
                if (!server.reserveRematch(this)) { handler.send(new ErrorMessage(ClientHandler.MAINTENANCE)); return; }
                game++;
                newGame();
            } else broadcast();
        });
    }

    /** Restores a reconnecting account to its seat. */
    void attach(ClientHandler handler) {
        String username = handler.username();
        submit(() -> {
            Participant p = byName(username);
            if (p == null || p.left) return;
            if (p.grace != null) p.grace.cancel(false);
            p.grace = null;
            p.handler = handler;
            LOG.info(() -> username + " rejoined match " + id);
            publishStatus();
            broadcast();
        });
    }

    /** Handles a dropped connection: the seat is held for the grace period during play. */
    void detach(ClientHandler handler) {
        submit(() -> {
            Participant p = participant(handler);
            if (p != null) disconnect(p);
        });
    }

    /** Stops the session's thread during server shutdown. */
    void close() { executor.shutdownNow(); inProgress = false; }

    /** Whether a game or its final persistence is still in progress, for the restart check. */
    boolean inProgress() { return inProgress; }

    /** Seats held for players who dropped and may still reconnect. */
    int heldSeats() { return heldSeats; }

    /** Called under the admission barrier before a rematch is queued as active. */
    void reserveGame() { inProgress = true; }

    private void publishStatus() {
        inProgress = state != null && state.phase() != MatchPhase.FINISHED;
        // A finished game holds nothing worth waiting for, even if a grace timer is still pending.
        heldSeats = !inProgress ? 0 : (first.grace != null ? 1 : 0) + (second.grace != null ? 1 : 0);
    }

    private void disconnect(Participant p) {
        p.handler = null;
        if (state.phase() == MatchPhase.FINISHED) { depart(p); return; }
        int seconds = server.config().reconnectGraceSeconds();
        int forGame = game;
        p.grace = later(() -> graceExpired(p, forGame), TimeUnit.SECONDS.toMillis(seconds));
        LOG.info(() -> p.username + " disconnected from match " + id + "; holding the seat for " + seconds + "s");
        publishStatus();
        broadcast();
    }

    private void graceExpired(Participant p, int forGame) {
        if (p.handler != null || p.left || game != forGame || state.phase() == MatchPhase.FINISHED) return;
        depart(p);
    }

    private void depart(Participant p) {
        p.left = true;
        p.rematch = false;
        p.grace = null;
        if (state.phase() != MatchPhase.FINISHED) {
            state = state.forfeit(p.seat);
            changed();
        } else broadcast();
        publishStatus();
        // Keep the session discoverable until a forfeit's replay and result have been saved.
        server.matches().release(p.username, this);
        if ((first.bot || first.left) && (second.bot || second.left)) close();
    }

    private void newGame() {
        for (Participant p : List.of(first, second)) { p.rematch = false; p.lastAccepted = Long.MIN_VALUE; }
        // Both players get the same automatic deck; the seed shuffles each one independently.
        var deck = DeckRules.deck(server.catalog());
        state = MatchState.start(deck, deck, seeds.nextLong());
        LOG.info(() -> "match " + id + " game " + game + ": " + first.username + " vs " + second.username);
        for (Participant p : List.of(first, second)) {
            long replay = p.bot ? -1 : server.replays().begin(p.username, gameKey(), other(p).username, practice,
                    server.catalog().version());
            p.recording = replay < 0 ? null : new Recording(replay);
        }
        changed();
    }

    /** Publishes an accepted change, then records a finished match or lets a waited-on bot move. */
    private void changed() {
        capture();
        broadcast();
        if (state.phase() == MatchPhase.FINISHED) {
            if (!practice) record();
            // wait-safe must include the final replay writes and result transaction.
            publishStatus();
            return;
        }
        publishStatus();
        for (Participant p : List.of(first, second)) {
            if (!p.bot || !state.awaits(p.seat)) continue;
            int revision = state.revision(), forGame = game;
            later(() -> botMove(p, revision, forGame), server.config().botDelayMillis());
        }
    }

    /** Stores each recording human's view of a new revision, and closes the recordings when the game ends. */
    private void capture() {
        for (Participant p : List.of(first, second)) {
            Recording recording = p.recording;
            if (recording == null) continue;
            if (state.revision() != recording.revision) {
                recording.revision = state.revision();
                if (!server.replays().frame(recording.id, state.view(p.seat))) recording.missing = true;
            }
            if (state.phase() == MatchPhase.FINISHED) {
                server.replays().finish(recording.id, state.view(p.seat), !recording.missing);
                p.recording = null;
            }
        }
    }

    /** Names this game for its result row and replays: the session id and the rematch number. */
    private String gameKey() { return id + "-" + game; }

    private void botMove(Participant bot, int revision, int forGame) {
        if (game != forGame || state.revision() != revision || !state.awaits(bot.seat)) return;
        state = state.apply(bot.seat, bot.brain.apply(state.view(bot.seat)));
        changed();
    }

    private void record() {
        try {
            MatchOutcome outcome = state.outcome();
            PlayerColor winner = outcome.winner() == null ? null : outcome.winner() == Seat.FIRST ? PlayerColor.BLUE : PlayerColor.RED;
            EndReason way = outcome.forfeit() ? EndReason.FORFEIT : winner == null ? EndReason.DRAW : EndReason.SCORE;
            server.recorder().record(new MatchResult(gameKey(), first.username, second.username, winner, way,
                    server.catalog().version(), state.round(), state.rounds().size()), state.rounds());
            for (Participant p : List.of(first, second))
                if (p.handler != null) p.handler.send(new ProfileUpdate(server.userDao().profileOf(p.username)));
        } catch (RuntimeException exception) {
            LOG.log(Level.SEVERE, "Could not record match " + id, exception);
        }
    }

    private void broadcast() {
        for (Participant p : List.of(first, second)) send(p);
    }

    private void send(Participant p) {
        if (p.bot || p.left || p.handler == null) return;
        Participant other = other(p);
        p.handler.send(new MatchUpdate(id, game, p.username, other.username, practice,
                other.bot || other.handler != null, other.left, p.rematch, other.rematch, state.view(p.seat)));
    }

    private void reject(Participant p, MoveRequest request, String reason) {
        // Only the move's kind is logged; card ids and hands stay private.
        LOG.info(() -> "match " + id + ": rejected " + (request.move() == null ? "empty move" : request.move().getClass().getSimpleName())
                + " from " + p.username + ": " + reason);
        p.handler.send(new ActionRejected(request.requestId(), reason));
    }

    private Participant participant(ClientHandler handler) {
        for (Participant p : List.of(first, second)) if (!p.bot && !p.left && p.handler == handler) return p;
        return null;
    }

    private Participant byName(String username) {
        for (Participant p : List.of(first, second)) if (!p.bot && p.username.equals(username)) return p;
        return null;
    }

    private Participant other(Participant p) { return p == first ? second : first; }

    /** Runs a task on this match's executor after a delay; returns null once the server is stopping. */
    private ScheduledFuture<?> later(Runnable task, long millis) {
        try { return server.timers().schedule(() -> submit(task), millis, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException stopping) { return null; }
    }

    private void submit(Runnable task) {
        try {
            executor.execute(() -> {
                try { task.run(); }
                catch (RuntimeException exception) { LOG.log(Level.SEVERE, "Match " + id + " task failed", exception); }
            });
        } catch (RejectedExecutionException closed) {
            LOG.log(Level.FINE, "Match " + id + " is closed", closed);
        }
    }
}
