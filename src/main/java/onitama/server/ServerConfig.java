package onitama.server;

import java.nio.file.Path;
import java.util.Random;

/**
 * Immutable server configuration. The deal random is injectable so
 * integration tests can script deterministic games; production passes a
 * freshly seeded {@link Random}. The reconnect grace period is configurable
 * for the same reason (tests use 1 second, production 60). A null replayDir
 * disables replay writing (tests).
 */
public record ServerConfig(int port, int graceSeconds, Random dealRandom, Path replayDir) {

    /** Default production configuration with the given port. */
    public static ServerConfig defaults(int port) {
        return new ServerConfig(port, 60, new Random(), Path.of("replays"));
    }
}
