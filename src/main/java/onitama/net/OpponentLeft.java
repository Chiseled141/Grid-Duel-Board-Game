package onitama.net;

import java.io.Serial;

/**
 * S→C. The opponent disconnected: the match is parked for the given grace
 * period; if they do not reconnect in time, they forfeit.
 */
public record OpponentLeft(int graceSeconds) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
