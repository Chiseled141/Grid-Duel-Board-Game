package deckduel.client.startup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The update check and install steps, independent of any screen. Owned by one UI thread: every
 * method is called there and every listener is told there, through {@code ui}. The blocking
 * service calls run on {@code worker}. Actions that do not fit the current stage are ignored,
 * so a double click or a stale button never starts a second check or install.
 */
public final class UpdateFlow {

    /** Where the update steps stand. */
    public enum Stage {
        IDLE, CHECKING, UP_TO_DATE, NOT_CONFIGURED, CHECK_FAILED,
        OPTIONAL_UPDATE, REQUIRED_UPDATE, INSTALLING, INSTALL_FAILED, INSTALLED
    }

    /**
     * One snapshot for the screens.
     *
     * @param offer the last check's answer, kept through installing so screens know the version
     *        and whether it is required; null before a check succeeds
     * @param progress install progress from 0 to 1
     * @param failure why the last check or install failed, or null
     */
    public record State(Stage stage, UpdateCheck offer, double progress, String failure) {

        /** Whether the player may go on to the server from here without installing. */
        public boolean canContinue() {
            return switch (stage) {
                case UP_TO_DATE, NOT_CONFIGURED, CHECK_FAILED, OPTIONAL_UPDATE -> true;
                case INSTALL_FAILED -> !offer.required();
                default -> false;
            };
        }

        /** Whether an Update (or retried install) can start from here. */
        public boolean canInstall() {
            return stage == Stage.OPTIONAL_UPDATE || stage == Stage.REQUIRED_UPDATE || stage == Stage.INSTALL_FAILED;
        }
    }

    private final String installedVersion;
    private final UpdateService service;
    private final UpdateInstaller installer;
    private final Executor worker;
    private final Executor ui;
    private final List<Consumer<State>> listeners = new ArrayList<>();
    private State state = new State(Stage.IDLE, null, 0, null);

    /**
     * @param worker runs the blocking service calls off the UI threads
     * @param ui hands results back to the thread that owns this flow
     */
    public UpdateFlow(String installedVersion, UpdateService service, UpdateInstaller installer,
                      Executor worker, Executor ui) {
        this.installedVersion = Objects.requireNonNull(installedVersion);
        this.service = Objects.requireNonNull(service);
        this.installer = Objects.requireNonNull(installer);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
    }

    /** Adds an observer, told of every later state on the UI thread. */
    public void addListener(Consumer<State> listener) { listeners.add(listener); }
    /** The current snapshot. */
    public State state() { return state; }
    /** The version this client runs. */
    public String installedVersion() { return installedVersion; }

    /** Asks the update service for the latest release, unless a check or install is running. */
    public void check() {
        if (state.stage() == Stage.CHECKING || state.stage() == Stage.INSTALLING) return;
        publish(new State(Stage.CHECKING, null, 0, null));
        worker.execute(() -> {
            try {
                UpdateCheck answer = Objects.requireNonNull(service.check(installedVersion), "update check answer");
                ui.execute(() -> publish(new State(stageFor(answer), answer, 0, null)));
            } catch (IOException | RuntimeException failure) {
                ui.execute(() -> publish(new State(Stage.CHECK_FAILED, null, 0, reason(failure))));
            }
        });
    }

    /** Installs the offered release; also the retry after a failed install. */
    public void install() {
        if (!state.canInstall()) return;
        UpdateCheck offer = state.offer();
        publish(new State(Stage.INSTALLING, offer, 0, null));
        worker.execute(() -> {
            try {
                installer.install(offer, fraction -> ui.execute(() -> progress(fraction)));
                ui.execute(() -> publish(new State(Stage.INSTALLED, offer, 1, null)));
            } catch (IOException | RuntimeException failure) {
                ui.execute(() -> publish(new State(Stage.INSTALL_FAILED, offer, state.progress(), reason(failure))));
            }
        });
    }

    /**
     * Reports that a verified update could not be finished (the helper did not start). The
     * update shows as failed, and Retry prepares it again, reusing the verified files.
     */
    public void handOverFailed(String reason) {
        if (state.stage() != Stage.INSTALLED) return;
        publish(new State(Stage.INSTALL_FAILED, state.offer(), 1, reason));
    }

    /** Repeats whichever step failed. */
    public void retry() {
        if (state.stage() == Stage.CHECK_FAILED) check();
        else if (state.stage() == Stage.INSTALL_FAILED) install();
    }

    private void progress(double fraction) {
        // A late report after the install finished or failed must not reopen it.
        if (state.stage() != Stage.INSTALLING) return;
        publish(new State(Stage.INSTALLING, state.offer(), Math.max(0, Math.min(1, fraction)), null));
    }

    private static Stage stageFor(UpdateCheck answer) {
        return switch (answer.status()) {
            case UP_TO_DATE -> Stage.UP_TO_DATE;
            case OPTIONAL -> Stage.OPTIONAL_UPDATE;
            case REQUIRED -> Stage.REQUIRED_UPDATE;
            case NOT_CONFIGURED -> Stage.NOT_CONFIGURED;
        };
    }

    private static String reason(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private void publish(State next) {
        state = next;
        List.copyOf(listeners).forEach(listener -> listener.accept(next));
    }
}
