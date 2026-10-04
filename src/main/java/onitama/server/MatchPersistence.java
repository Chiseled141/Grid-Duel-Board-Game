package onitama.server;

import onitama.db.Database;
import onitama.db.MatchDao;
import onitama.replay.ReplayFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records a finished match: writes the replay file first, then persists the
 * match row and both players' Elo/wins/losses in ONE database transaction
 * (rollback on failure — a partial record would corrupt the ratings). Replay
 * failures do not block the match record (the path is simply stored as null).
 */
public final class MatchPersistence {

    private static final Logger LOG = Logger.getLogger(MatchPersistence.class.getName());

    private final Database database;
    private final MatchDao matchDao;
    private final Path replayDir;

    /**
     * Creates the coordinator; {@code replayDir} may be null to skip replay
     * writing (used by integration tests).
     */
    public MatchPersistence(Database database, MatchDao matchDao, Path replayDir) {
        this.database = database;
        this.matchDao = matchDao;
        this.replayDir = replayDir;
    }

    /** Persists the match. Never throws: failures are logged and dropped. */
    public void record(MatchResult result) {
        String replayPath = writeReplay(result);
        try {
            long matchId = database.inTransaction(connection ->
                    matchDao.recordMatch(connection, result, replayPath));
            LOG.info(() -> "recorded match " + result.roomCode() + " as id " + matchId
                    + (replayPath == null ? "" : " with replay " + replayPath));
        } catch (Exception e) {
            // The game itself is unaffected; the record is simply missing.
            LOG.log(Level.SEVERE, "could not record match " + result.roomCode(), e);
        }
    }

    private String writeReplay(MatchResult result) {
        if (replayDir == null) {
            return null;
        }
        try {
            Files.createDirectories(replayDir);
            String fileName = "match-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + "-" + result.roomCode() + ReplayFile.EXTENSION;
            Path path = replayDir.resolve(fileName);
            ReplayFile.write(path, result);
            return path.toString();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not write replay for " + result.roomCode(), e);
            return null;
        }
    }
}
