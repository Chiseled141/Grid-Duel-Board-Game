package deckduel.net;

import java.io.Serial;

/**
 * S→C. Server acknowledgment after accepting ProtocolHello.
 */
public record ProtocolReady(int version) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}
