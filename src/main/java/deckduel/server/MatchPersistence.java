package deckduel.server;

import deckduel.core.card.MatchOutcome;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.db.Database;
import deckduel.db.MatchDao;
import deckduel.db.PersistenceException;
import deckduel.db.ReplayDao;
import deckduel.net.ErrorMessage;
import deckduel.net.HistoryEntry;
import deckduel.net.Message;
import deckduel.net.ReplayResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records a finished match: the match row, its round scores and both players'
 * wins/losses in ONE database transaction (rollback on failure — a
 * partial record would corrupt the record). Also stores and serves each
 * account's personal replays, separately from that transaction.
 */
public final class MatchPersistence implements ResultRecorder, MatchHistory, ReplayStore {

    private static final Logger LOG = Logger.getLogger(MatchPersistence.class.getName());
    /** Kept below the wire filter's array limit, far above any real game's revisions. */
    static final int MAX_FRAMES = 900;

    private final Database database;
    private final MatchDao matchDao;
    private final ReplayDao replayDao;

    /** Creates the persistence hook. */
    public MatchPersistence(Database database, MatchDao matchDao, ReplayDao replayDao) {
        this.database = database;
        this.matchDao = matchDao;
        this.replayDao = replayDao;
    }

    /** Persists the match. Never throws: failures are logged and dropped. */
    @Override
    public void record(MatchResult result, List<RoundResult> rounds) {
        try {
            long matchId = database.inTransaction(connection -> {
                long id = matchDao.recordMatch(connection, result);
                matchDao.recordRounds(connection, id, rounds);
                return id;
            });
            LOG.info(() -> "recorded match " + result.roomCode() + " as id " + matchId);
        } catch (Exception e) {
            // The game itself is unaffected; the record is simply missing.
            LOG.log(Level.SEVERE, "could not record match " + result.roomCode(), e);
        }
    }

    /**
     * Reads the user's matches, including legacy rows, which simply have no round scores, and
     * their finished practice games, which only exist as replays. Newest first.
     */
    @Override
    public List<HistoryEntry> historyFor(String username, int limit) {
        try {
            List<HistoryEntry> entries = new ArrayList<>();
            for (MatchDao.MatchRecord match : matchDao.recentMatchesFor(username, limit)) {
                boolean blue = match.blueUsername().equals(username);
                List<Long> own = new ArrayList<>(), theirs = new ArrayList<>();
                for (RoundResult round : matchDao.roundsOf(match.id())) {
                    own.add(blue ? round.firstScore() : round.secondScore());
                    theirs.add(blue ? round.secondScore() : round.firstScore());
                }
                HistoryEntry.Result result = match.winnerUsername() == null ? HistoryEntry.Result.DRAW
                        : match.winnerUsername().equals(username) ? HistoryEntry.Result.WIN : HistoryEntry.Result.LOSS;
                ReplayDao.ReplayRecord replay = match.matchKey() == null ? null : replayDao.findByMatch(username, match.matchKey());
                entries.add(new HistoryEntry(match.playedAt(), blue ? match.redUsername() : match.blueUsername(), result,
                        match.endReason(), match.rulesVersion(), own, theirs, false,
                        replay == null ? null : replay.id(), status(replay)));
            }
            for (ReplayDao.ReplayRecord practice : replayDao.finishedPractice(username, limit))
                entries.add(new HistoryEntry(practice.playedAt(), practice.opponent(), HistoryEntry.Result.valueOf(practice.result()),
                        practice.endReason(), practice.rulesVersion(), practice.yourRounds(), practice.opponentRounds(), true,
                        practice.id(), status(practice)));
            // Both sources store ISO local timestamps, so text order is time order.
            entries.sort(Comparator.comparing(HistoryEntry::playedAt).reversed());
            return List.copyOf(entries.subList(0, Math.min(limit, entries.size())));
        } catch (SQLException e) {
            throw new PersistenceException("could not load match history", e);
        }
    }

    private static HistoryEntry.Replay status(ReplayDao.ReplayRecord replay) {
        if (replay == null) return HistoryEntry.Replay.UNAVAILABLE;
        return replay.finished() && replay.complete() ? HistoryEntry.Replay.AVAILABLE : HistoryEntry.Replay.INCOMPLETE;
    }

    @Override
    public long begin(String owner, String matchKey, String opponent, boolean practice, String rulesVersion) {
        try {
            return replayDao.create(owner, matchKey, opponent, practice, rulesVersion);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "could not start a replay of " + matchKey, e);
            return -1;
        }
    }

    @Override
    public boolean frame(long replayId, PlayerView view) {
        if (view.revision() >= MAX_FRAMES) return false;
        try {
            return replayDao.addFrame(replayId, view.revision(), encode(view));
        } catch (SQLException | IOException e) {
            LOG.log(Level.WARNING, "could not store a replay frame", e);
            return false;
        }
    }

    /** {@code last} is the owner's finished view, which carries the outcome and banked rounds. */
    @Override
    public void finish(long replayId, PlayerView last, boolean complete) {
        try {
            MatchOutcome outcome = last.outcome();
            String result = outcome.winner() == null ? "DRAW" : outcome.winner() == last.seat() ? "WIN" : "LOSS";
            String way = outcome.forfeit() ? "FORFEIT" : outcome.winner() == null ? "DRAW" : "SCORE";
            List<Long> own = new ArrayList<>(), theirs = new ArrayList<>();
            for (RoundResult round : last.rounds()) {
                own.add(round.scoreOf(last.seat()));
                theirs.add(round.scoreOf(last.seat().other()));
            }
            replayDao.finish(replayId, result, way, own, theirs, complete);
        } catch (SQLException | RuntimeException e) {
            // The recording stays unfinished and is shown as incomplete; the match thread carries on.
            LOG.log(Level.WARNING, "could not finish a replay", e);
        }
    }

    @Override
    public Message replay(String owner, long replayId) {
        try {
            ReplayDao.ReplayRecord record = replayDao.find(owner, replayId);
            if (record == null) return new ErrorMessage(UNAVAILABLE);
            List<PlayerView> frames = new ArrayList<>();
            for (byte[] frame : replayDao.frames(replayId)) frames.add(decode(frame));
            if (frames.isEmpty()) return new ErrorMessage(UNAVAILABLE);
            return new ReplayResponse(record.id(), record.opponent(), record.practice(), record.playedAt(),
                    record.finished() && record.complete(), frames);
        } catch (SQLException e) {
            throw new PersistenceException("could not load a replay", e);
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            // Frames written by an incompatible build are refused, never partly shown.
            LOG.log(Level.WARNING, "unreadable replay " + replayId, e);
            return new ErrorMessage("This replay was recorded by another version and cannot be shown.");
        }
    }

    private static byte[] encode(PlayerView view) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(view);
        }
        return bytes.toByteArray();
    }

    private static PlayerView decode(byte[] frame) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(frame))) {
            // Stored frames are read under the same allowlist as the wire.
            in.setObjectInputFilter(Message.WIRE_FILTER);
            return (PlayerView) in.readObject();
        }
    }
}
