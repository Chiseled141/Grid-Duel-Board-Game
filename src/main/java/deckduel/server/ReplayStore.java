package deckduel.server;

import deckduel.core.card.PlayerView;
import deckduel.net.ErrorMessage;
import deckduel.net.Message;

/**
 * Keeps each account's own match views for replays. Recording runs on a match's thread, so
 * implementations must not throw into it; a failed write is reported, never retried silently.
 */
public interface ReplayStore {
    /** The reply when a replay does not exist, belongs to someone else, or cannot be read. */
    String UNAVAILABLE = "Replay unavailable.";

    /** For servers started without a database: records nothing. */
    ReplayStore NONE = new ReplayStore() {
        @Override public long begin(String owner, String matchKey, String opponent, boolean practice, String rulesVersion) { return -1; }
        @Override public boolean frame(long replayId, PlayerView view) { return false; }
        @Override public void finish(long replayId, PlayerView last, boolean complete) { }
        @Override public Message replay(String owner, long replayId) { return new ErrorMessage(UNAVAILABLE); }
    };

    /** Starts {@code owner}'s recording of one game; returns its id, or -1 when it cannot be stored. */
    long begin(String owner, String matchKey, String opponent, boolean practice, String rulesVersion);

    /** Stores the owner's view at one revision; false when it could not be stored. */
    boolean frame(long replayId, PlayerView view);

    /** Marks the recording finished with the owner's final view; {@code complete} is false when frames are missing. */
    void finish(long replayId, PlayerView last, boolean complete);

    /** The reply to {@code owner}'s replay request: a ReplayResponse, or an ErrorMessage when unavailable. */
    Message replay(String owner, long replayId);
}
