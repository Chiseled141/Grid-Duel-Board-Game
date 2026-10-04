package onitama.server;

import java.util.Random;

/**
 * Immutable server configuration. The deal random is injectable so
 * integration tests can script deterministic games; production passes a
 * freshly seeded {@link Random}. The reconnect grace period is configurable
 * for the same reason (tests use 1 second, production 60). {@code
 * silenceSeconds} is the heartbeat silence limit (production 90; tests use
 * 1). {@code clientPoolSize} controls the maximum number of concurrent
 * client handler threads.
 */
public record ServerConfig(int port, int graceSeconds, Random dealRandom,
                           int silenceSeconds, int clientPoolSize) {

    /** Default production configuration with the given port. */
    public static ServerConfig defaults(int port) {
        return new ServerConfig(port, 60, new Random(), 90, 32);
    }

    /** Configuration with a custom reconnect grace period (tests). */
    public ServerConfig(int port, int graceSeconds, Random dealRandom) {
        this(port, graceSeconds, dealRandom, 90, 32);
    }
}
