package deckduel.client.startup;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlled local stand-ins that put the startup journey in each state on demand
 * ({@code client --startup-fixture <name>}). They contact nothing and install nothing: the
 * offered versions are made up, an install only waits while reporting progress, and the screen
 * labels the run as a fixture. Only the connection is real, apart from a deliberate first failure.
 */
public enum StartupFixture {
    /** The check finds the installed version current. */
    UP_TO_DATE,
    /** An optional release is offered; installing it succeeds. */
    OPTIONAL_UPDATE,
    /** A required release blocks entry; installing it succeeds. */
    REQUIRED_UPDATE,
    /** A required release whose first install fails part-way; Retry succeeds. */
    INSTALL_FAILS,
    /** The first check cannot reach the update service; Retry finds the client current. */
    CHECK_FAILS,
    /** Updates are current but the first connection attempt fails; Retry connects for real. */
    CONNECTION_FAILS;

    /** The made-up version an optional fixture offers. */
    public static final String OPTIONAL_VERSION = "1.1.0-fixture";
    /** The made-up version a required fixture offers. */
    public static final String REQUIRED_VERSION = "2.0.0-fixture";
    private static final int INSTALL_STEPS = 20;

    /** The command-line name, for example {@code optional-update}. */
    public String id() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    /** Looks up a fixture by its command-line name. */
    public static StartupFixture fromId(String id) {
        return Arrays.stream(values()).filter(fixture -> fixture.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown startup fixture '" + id + "'; expected one of "
                        + Arrays.stream(values()).map(StartupFixture::id).toList()));
    }

    /** Fresh fixture services that pause about {@code stepMillis} per simulated network step. */
    public StartupServices services(long stepMillis) {
        AtomicInteger checks = new AtomicInteger(), installs = new AtomicInteger(), connects = new AtomicInteger();
        UpdateService updates = installed -> {
            pause(stepMillis * 8);
            if (this == CHECK_FAILS && checks.getAndIncrement() == 0)
                throw new IOException("Test fixture: the update service could not be reached.");
            return switch (this) {
                case OPTIONAL_UPDATE -> UpdateCheck.optional(OPTIONAL_VERSION);
                case REQUIRED_UPDATE, INSTALL_FAILS -> UpdateCheck.required(REQUIRED_VERSION);
                default -> UpdateCheck.upToDate(installed);
            };
        };
        UpdateInstaller installer = (update, progress) -> {
            boolean failing = this == INSTALL_FAILS && installs.getAndIncrement() == 0;
            for (int step = 1; step <= INSTALL_STEPS; step++) {
                pause(stepMillis);
                if (failing && step == INSTALL_STEPS * 3 / 5)
                    throw new IOException("Test fixture: the download was interrupted.");
                progress.accept(step / (double) INSTALL_STEPS);
            }
        };
        return new StartupServices(updates, installer, real -> route -> {
            pause(stepMillis * 4);
            if (this == CONNECTION_FAILS && connects.getAndIncrement() == 0)
                throw new IOException("Test fixture: the first connection attempt was refused.");
            real.connect(route);
        }, null, id());
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", interrupted);
        }
    }
}
