package onitama.net;

import java.io.Serial;

/** S→C. Authoritative queue status, with a nullable rejection reason. */
public record MatchmakingStatus(boolean searching, String error) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
