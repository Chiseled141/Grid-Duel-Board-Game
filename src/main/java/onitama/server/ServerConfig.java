package onitama.server;

import java.util.Objects;
import java.util.Random;
import onitama.config.RulesConfigLoader;
import onitama.core.duel.RulesConfig;

/**
 * Immutable server configuration. The deal random is injectable so
 * integration tests can script deterministic games; production passes a
 * freshly seeded {@link Random}. The reconnect grace period is configurable
 * for the same reason (tests use 1 second, production 60). {@code
 * silenceSeconds} is the heartbeat silence limit (production 90; tests use
 * 1). {@code clientPoolSize} controls the maximum number of concurrent
 * client handler threads. {@code rulesConfig} is the frozen balance configuration.
 */
public record ServerConfig(int port, int graceSeconds, Random dealRandom,
                           int silenceSeconds, int clientPoolSize, RulesConfig rulesConfig) {

    public ServerConfig {
        dealRandom = dealRandom != null ? dealRandom : new Random();
        if (rulesConfig == null) {
            rulesConfig = RulesConfigLoader.load(null);
        }
    }

    /** Default production configuration with the given port. */
    public static ServerConfig defaults(int port) {
        return defaults(port, null);
    }

    /** Default production configuration with the given port and rules configuration. */
    public static ServerConfig defaults(int port, RulesConfig rulesConfig) {
        return new ServerConfig(port, 60, new Random(), 90, 32,
                rulesConfig != null ? rulesConfig : RulesConfigLoader.load(null));
    }

    /** Configuration with a custom reconnect grace period (tests). */
    public ServerConfig(int port, int graceSeconds, Random dealRandom) {
        this(port, graceSeconds, dealRandom, 90, 32, RulesConfigLoader.load(null));
    }

    /** Configuration with custom grace, random, silence, and client pool size (tests). */
    public ServerConfig(int port, int graceSeconds, Random dealRandom, int silenceSeconds, int clientPoolSize) {
        this(port, graceSeconds, dealRandom, silenceSeconds, clientPoolSize, RulesConfigLoader.load(null));
    }
}
