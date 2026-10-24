package deckduel.client.startup;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The update and connection services a client starts with.
 *
 * @param connection wraps the real connector; fixtures use it to fail an attempt on purpose
 * @param restarter finishes a verified update by closing and restarting the game, or null when
 *        nothing real was installed (no release channel, or a fixture)
 * @param fixture the name of the local test fixture in use, or null for real services
 */
public record StartupServices(UpdateService updates, UpdateInstaller installer,
                              UnaryOperator<ServerConnector> connection, UpdateRestarter restarter, String fixture) {

    public StartupServices {
        Objects.requireNonNull(updates);
        Objects.requireNonNull(installer);
        Objects.requireNonNull(connection);
    }

    /** No release channel is configured: nothing is checked, and the connection is the real one. */
    public static StartupServices unconfigured() {
        return new StartupServices(UpdateService.notConfigured(), UpdateInstaller.notConfigured(),
                UnaryOperator.identity(), null, null);
    }
}
