package onitama.server;

import java.util.Random;

/**
 * Immutable server configuration. The deal random is injectable so
 * integration tests can script deterministic games; production passes a
 * freshly seeded {@link Random}. The reconnect grace period is configurable
 * for the same reason (tests use 1 second, production 60).
 */
public record ServerConfig(int port, int graceSeconds, Random dealRandom) {

    /** Default production configuration: any free port is chosen by the OS when 0. */
    public static ServerConfig defaults(int port) {
        return new ServerConfig(port, 60, new Random());
    }
}
