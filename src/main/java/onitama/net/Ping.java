package onitama.net;

import java.io.Serial;

/**
 * Either direction. Heartbeat sent every 30 seconds of silence; a peer that
 * stays silent for 90 seconds is considered gone and disconnected.
 */
public record Ping(long timestamp) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
