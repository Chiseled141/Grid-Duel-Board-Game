package onitama.net;

import java.io.Serial;

/**
 * Either direction. Reply to a {@link Ping}, echoing the ping's timestamp so
 * the sender can measure the round-trip time.
 */
public record Pong(long timestamp) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
