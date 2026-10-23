package deckduel.net;

import java.io.Serial;

/**
 * C→S. First client message after receiving the server's greeting: the protocol it speaks and
 * the client's release version, which the server checks against its minimum supported version
 * before any account details are exchanged.
 */
public record ProtocolHello(int version, String clientVersion) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}
