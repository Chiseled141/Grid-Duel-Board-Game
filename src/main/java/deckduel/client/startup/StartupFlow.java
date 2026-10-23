package deckduel.client.startup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The launch journey before login: check for updates, then connect to the server. Owned by one
 * UI thread like {@link UpdateFlow}, whose steps it reuses; the connection runs on
 * {@code worker}. Entry is blocked while a required update is not installed: {@link #proceed()}
 * does nothing then, and an installed update waits for a restart. When the server cannot be
 * reached, {@link #playOffline()} starts a private local server instead.
 */
public final class StartupFlow {

    /** How far the journey has come. */
    public enum Phase { UPDATES, CONNECTING, CONNECTION_FAILED, CONNECTED, OFFLINE_STARTING, OFFLINE }

    /**
     * One snapshot for the startup screen.
     *
     * @param update the update steps' latest state, shown during {@link Phase#UPDATES} and kept after
     * @param route the server connected to: the offline server's local route once {@link Phase#OFFLINE}
     * @param failure why the last connection attempt failed, or null
     * @param serverRequires the minimum version the server demanded when it refused this client, or null
     */
    public record State(Phase phase, UpdateFlow.State update, ServerRoute route, String failure, String serverRequires) {

        /** Whether the player may go on without installing: never once the server refused this version. */
        public boolean canContinue() { return serverRequires == null && update.canContinue(); }
    }

    private final UpdateFlow updates;
    private final ServerConnector connector;
    private final OfflineLauncher offline;
    private final ServerRoute route;
    private final Executor worker;
    private final Executor ui;
    private final long settleMillis;
    private final List<Consumer<State>> listeners = new ArrayList<>();
    private State state;
    // Set when the server refused this client as too old; no Continue, no automatic reconnect.
    private String serverRequires;

    /**
     * @param settleMillis how long "Up to date" stays readable before connecting on its own
     */
    public StartupFlow(UpdateFlow updates, ServerConnector connector, OfflineLauncher offline, ServerRoute route,
                       Executor worker, Executor ui, long settleMillis) {
        this.updates = Objects.requireNonNull(updates);
        this.connector = Objects.requireNonNull(connector);
        this.offline = Objects.requireNonNull(offline);
        this.route = Objects.requireNonNull(route);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.settleMillis = settleMillis;
        state = new State(Phase.UPDATES, updates.state(), route, null, null);
        updates.addListener(this::updateChanged);
    }

    /** Adds an observer, told of every later state on the UI thread. */
    public void addListener(Consumer<State> listener) { listeners.add(listener); }
    /** The current snapshot. */
    public State state() { return state; }

    /** Begins the journey with an update check. */
    public void start() {
        if (state.phase() == Phase.UPDATES && state.update().stage() == UpdateFlow.Stage.IDLE) updates.check();
    }

    /** The Update button: installs the offered release. */
    public void update() { if (state.phase() == Phase.UPDATES) updates.install(); }

    /** The Continue button: goes on to the server when no required update blocks entry. */
    public void proceed() {
        if (state.phase() == Phase.UPDATES && state.canContinue()) connect();
    }

    /** The Retry button: repeats the failed update step or connection. */
    public void retry() {
        if (state.phase() == Phase.CONNECTION_FAILED) connect();
        else if (state.phase() == Phase.UPDATES) updates.retry();
    }

    /** The Play offline button: after a failed connection, starts the local server and connects to it. */
    public void playOffline() {
        if (state.phase() != Phase.CONNECTION_FAILED) return;
        publish(new State(Phase.OFFLINE_STARTING, state.update(), route, null, serverRequires));
        worker.execute(() -> {
            try {
                ServerRoute local = offline.start();
                connector.connect(local);
                ui.execute(() -> publish(new State(Phase.OFFLINE, state.update(), local, null, serverRequires)));
            } catch (IOException | RuntimeException failure) {
                ui.execute(() -> publish(new State(Phase.CONNECTION_FAILED, state.update(), route, reason(failure), serverRequires)));
            }
        });
    }

    private void updateChanged(UpdateFlow.State update) {
        // Checks started from Settings after login must not dial the server again.
        if (state.phase() != Phase.UPDATES) return;
        publish(new State(Phase.UPDATES, update, route, null, serverRequires));
        boolean current = update.stage() == UpdateFlow.Stage.UP_TO_DATE || update.stage() == UpdateFlow.Stage.NOT_CONFIGURED;
        if (current && serverRequires != null) {
            // The server wants a newer client than the release channel offers: say so, never loop.
            publish(new State(Phase.CONNECTION_FAILED, update, route, "The server needs Deck Duel " + serverRequires
                    + " or newer, and no such update is available yet.", serverRequires));
            return;
        }
        if (current) {
            worker.execute(() -> {
                if (settled(settleMillis)) ui.execute(this::connect);
            });
        }
    }

    private void connect() {
        if (state.phase() != Phase.UPDATES && state.phase() != Phase.CONNECTION_FAILED) return;
        // A settle timer that fires after the player already pressed Continue is ignored here.
        if (state.phase() == Phase.UPDATES && !state.update().canContinue()) return;
        publish(new State(Phase.CONNECTING, state.update(), route, null, serverRequires));
        worker.execute(() -> {
            try {
                connector.connect(route);
                ui.execute(() -> publish(new State(Phase.CONNECTED, state.update(), route, null, serverRequires)));
            } catch (UpdateRequiredException outdated) {
                ui.execute(() -> serverRefused(outdated));
            } catch (IOException | RuntimeException failure) {
                ui.execute(() -> publish(new State(Phase.CONNECTION_FAILED, state.update(), route, reason(failure), serverRequires)));
            }
        });
    }

    /** The server refused this version before login: check for the update it needs. */
    private void serverRefused(UpdateRequiredException outdated) {
        serverRequires = outdated.minimumVersion();
        publish(new State(Phase.UPDATES, state.update(), route, null, serverRequires));
        updates.check();
    }

    private static String reason(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    /** Waits {@code millis}; false when interrupted because the client is closing. */
    private static boolean settled(long millis) {
        if (millis <= 0) return true;
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void publish(State next) {
        state = next;
        List.copyOf(listeners).forEach(listener -> listener.accept(next));
    }
}
