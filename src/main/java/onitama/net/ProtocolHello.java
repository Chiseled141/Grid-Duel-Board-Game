package onitama.net;

import java.io.Serial;

/**
 * C→S. First client message after receiving the server's greeting.
 */
public record ProtocolHello(int version) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}
