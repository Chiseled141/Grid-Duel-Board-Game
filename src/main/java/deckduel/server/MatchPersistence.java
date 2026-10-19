package deckduel.server;

import deckduel.core.card.RoundResult;
import deckduel.db.Database;
import deckduel.db.MatchDao;
import deckduel.db.PersistenceException;
import deckduel.net.HistoryEntry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records a finished match: the match row, its round scores and both players'
 * wins/losses in ONE database transaction (rollback on failure — a
 * partial record would corrupt the record).
 */
public final class MatchPersistence implements ResultRecorder, MatchHistory {

    private static final Logger LOG = Logger.getLogger(MatchPersistence.class.getName());

    private final Database database;
    private final MatchDao matchDao;

    /** Creates the persistence hook. */
    public MatchPersistence(Database database, MatchDao matchDao) {
        this.database = database;
        this.matchDao = matchDao;
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

    /** Reads the user's matches, including legacy rows, which simply have no round scores. */
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
                entries.add(new HistoryEntry(match.playedAt(), blue ? match.redUsername() : match.blueUsername(), result,
                        match.endReason(), match.rulesVersion(), own, theirs));
            }
            return entries;
        } catch (SQLException e) {
            throw new PersistenceException("could not load match history", e);
        }
    }
}
