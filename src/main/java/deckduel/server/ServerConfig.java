package deckduel.server;

/** Immutable account/lobby settings; gameplay configuration belongs to the future engine. */
public record ServerConfig(int port, int silenceSeconds, int clientPoolSize) {
    /** Validates settings before opening any sockets. */
    public ServerConfig {
        if (port < 0 || port > 65535 || silenceSeconds < 1 || clientPoolSize < 1)
            throw new IllegalArgumentException("Invalid server settings");
    }
    /** Production defaults; port zero supports isolated tests. */
    public static ServerConfig defaults(int port) { return new ServerConfig(port, 90, 32); }
}
