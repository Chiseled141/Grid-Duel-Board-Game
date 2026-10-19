package deckduel.server;

/**
 * Immutable server settings. A disconnected player keeps their seat for the reconnect grace
 * period; the practice bot waits {@code botDelayMillis} before each play so humans can follow it.
 */
public record ServerConfig(int port, int silenceSeconds, int clientPoolSize, int reconnectGraceSeconds,
                           int botDelayMillis) {
    /** Validates settings before opening any sockets. */
    public ServerConfig {
        if (port < 0 || port > 65535 || silenceSeconds < 1 || clientPoolSize < 1
                || reconnectGraceSeconds < 1 || botDelayMillis < 0)
            throw new IllegalArgumentException("Invalid server settings");
    }
    /** Production defaults; port zero supports isolated tests. */
    public static ServerConfig defaults(int port) { return new ServerConfig(port, 90, 32, 60, 700); }
}
