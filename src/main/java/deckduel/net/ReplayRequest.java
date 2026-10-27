package deckduel.net;

import java.io.Serial;

/** C→S. Asks for one of the signed-in account's own replays, by the id its history entry carries. */
public record ReplayRequest(long replayId) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
