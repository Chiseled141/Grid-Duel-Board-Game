package onitama.server;

import onitama.db.Database;
import onitama.db.MatchDao;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records a finished match: persists the match row and both players'
 * Elo/wins/losses in ONE database transaction (rollback on failure — a
 * partial record would corrupt the ratings).
 */
public final class MatchPersistence {

    private static final Logger LOG = Logger.getLogger(MatchPersistence.class.getName());

    private final Database database;
    private final MatchDao matchDao;

    /** Creates the persistence hook. */
    public MatchPersistence(Database database, MatchDao matchDao) {
        this.database = database;
        this.matchDao = matchDao;
    }

    /** Persists the match. Never throws: failures are logged and dropped. */
    public void record(MatchResult result) {
        try {
            long matchId = database.inTransaction(connection ->
                    matchDao.recordMatch(connection, result));
            LOG.info(() -> "recorded match " + result.roomCode() + " as id " + matchId);
        } catch (Exception e) {
            // The game itself is unaffected; the record is simply missing.
            LOG.log(Level.SEVERE, "could not record match " + result.roomCode(), e);
        }
    }
}
