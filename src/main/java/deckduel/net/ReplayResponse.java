package deckduel.net;

import deckduel.core.card.PlayerView;
import java.io.Serial;
import java.util.List;

/**
 * S→C. One of the recipient's own replays: the views they were sent during the match, one per
 * state revision in order, ending with the final result when the match finished. The frames hold
 * only what the recipient could see then; {@code complete} is false when actions are missing.
 */
public record ReplayResponse(long replayId, String opponent, boolean practice, String playedAt,
                             boolean complete, List<PlayerView> frames) implements Message {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies the frames; a replay always has at least the opening deal. */
    public ReplayResponse {
        frames = List.copyOf(frames);
        if (frames.isEmpty()) throw new IllegalArgumentException("A replay needs at least one frame");
    }
}
