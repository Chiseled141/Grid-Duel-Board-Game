package deckduel.db;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence for personal replays: one recording per account and match, holding that account's
 * own serialized views in revision order. Every read names the owner, so one account can never
 * load another's recording.
 */
public interface ReplayDao {

    /** Starts a recording and returns its id; the owner must exist. */
    long create(String owner, String matchKey, String opponent, boolean practice, String rulesVersion)
            throws SQLException;

    /**
     * Stores one frame. A revision already stored for this recording is ignored, so a repeated
     * view never appears twice.
     *
     * @return whether a new frame was stored
     */
    boolean addFrame(long replayId, int revision, byte[] view) throws SQLException;

    /** Marks the recording finished with the owner's result; {@code complete} is false when frames are missing. */
    void finish(long replayId, String result, String endReason, List<Long> yourRounds, List<Long> opponentRounds,
                boolean complete) throws SQLException;

    /** The owner's recording, or null when it does not exist or belongs to someone else. */
    ReplayRecord find(String owner, long replayId) throws SQLException;

    /** The owner's recording of the match stored under {@code matchKey}, or null. */
    ReplayRecord findByMatch(String owner, String matchKey) throws SQLException;

    /** The owner's finished practice recordings, newest first. */
    List<ReplayRecord> finishedPractice(String owner, int limit) throws SQLException;

    /** A recording's frames in revision order. */
    List<byte[]> frames(long replayId) throws SQLException;

    /** One recording's summary, from its owner's point of view. */
    record ReplayRecord(long id, String matchKey, String opponent, boolean practice, String rulesVersion,
                        String playedAt, boolean finished, boolean complete, String result, String endReason,
                        List<Long> yourRounds, List<Long> opponentRounds) {
        public ReplayRecord {
            yourRounds = List.copyOf(yourRounds);
            opponentRounds = List.copyOf(opponentRounds);
        }
    }
}
