package deckduel.client.startup;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.startup.StartupFlow.Phase;
import deckduel.client.startup.UpdateFlow.Stage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The startup journey's transitions. Background work waits in a queue until the test runs it,
 * so every intermediate state can be inspected; results come back inline.
 */
class StartupFlowTest {
    private static final ServerRoute ROUTE = ServerRoute.LOCAL_DEVELOPMENT;

    /** A worker whose tasks run only when the test asks. */
    private static final class Worker implements java.util.concurrent.Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runNext() { assertFalse(tasks.isEmpty(), "a background task is waiting"); tasks.remove().run(); }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    private static final class Fixture {
        final Worker worker = new Worker();
        final AtomicInteger checks = new AtomicInteger(), installs = new AtomicInteger(), connects = new AtomicInteger();
        final List<ServerRoute> dialed = new ArrayList<>();
        OfflineLauncher offline = () -> new ServerRoute("127.0.0.1", 40404);
        final List<String> trace = new ArrayList<>();
        final StartupFlow flow;
        final UpdateFlow updates;

        Fixture(UpdateService service, UpdateInstaller installer, ServerConnector connector) {
            UpdateService counted = installed -> { checks.incrementAndGet(); return service.check(installed); };
            UpdateInstaller countedInstall = (update, progress) -> { installs.incrementAndGet(); installer.install(update, progress); };
            ServerConnector countedConnect = route -> { connects.incrementAndGet(); dialed.add(route); connector.connect(route); };
            updates = new UpdateFlow("1.0.0", counted, countedInstall, worker, Runnable::run);
            flow = new StartupFlow(updates, countedConnect, () -> offline.start(), ROUTE, worker, Runnable::run, 0);
            flow.addListener(state -> trace.add(label(state)));
        }

        Fixture(UpdateService service) { this(service, (update, progress) -> progress.accept(1), route -> { }); }

        Phase phase() { return flow.state().phase(); }
        Stage stage() { return flow.state().update().stage(); }
    }

    private static String label(StartupFlow.State state) {
        return state.phase() == Phase.UPDATES ? state.update().stage().name() : state.phase().name();
    }

    @Test void upToDateChecksThenConnectsOnItsOwn() {
        var f = new Fixture(installed -> UpdateCheck.upToDate(installed));
        f.flow.start();
        assertEquals(Stage.CHECKING, f.stage());
        f.worker.runAll();
        assertEquals(List.of("CHECKING", "UP_TO_DATE", "CONNECTING", "CONNECTED"), f.trace);
        assertEquals("1.0.0", f.flow.state().update().offer().latestVersion());
        assertEquals(1, f.connects.get());
    }

    @Test void withoutAnUpdateServiceNothingIsReportedAsCheckedAndTheClientStillConnects() {
        var f = new Fixture(UpdateService.notConfigured());
        f.flow.start();
        f.worker.runAll();
        assertEquals(List.of("CHECKING", "NOT_CONFIGURED", "CONNECTING", "CONNECTED"), f.trace);
        assertNull(f.flow.state().update().offer().latestVersion(), "no version is invented");
        assertThrows(IOException.class, () -> UpdateInstaller.notConfigured().install(UpdateCheck.optional("x"), p -> { }));
    }

    @Test void anOptionalUpdateWaitsForTheChoiceAndContinueConnects() {
        var f = new Fixture(installed -> UpdateCheck.optional("1.1.0"));
        f.flow.start();
        f.worker.runAll();
        assertEquals(Stage.OPTIONAL_UPDATE, f.stage());
        assertEquals(0, f.connects.get(), "an offered update waits for the player");
        f.flow.proceed();
        f.worker.runAll();
        assertEquals(Phase.CONNECTED, f.phase());
        assertEquals(0, f.installs.get());
    }

    @Test void updatingReportsProgressAndThenWaitsForARestartWithoutConnecting() {
        List<Double> reported = new ArrayList<>();
        var f = new Fixture(installed -> UpdateCheck.optional("1.1.0"), (update, progress) -> {
            progress.accept(.25); progress.accept(.75); progress.accept(1);
        }, route -> { });
        f.updates.addListener(state -> { if (state.stage() == Stage.INSTALLING) reported.add(state.progress()); });
        f.flow.start();
        f.worker.runAll();
        f.flow.update();
        assertEquals(Stage.INSTALLING, f.stage());
        f.worker.runAll();
        assertEquals(List.of(0.0, .25, .75, 1.0), reported);
        assertEquals(Stage.INSTALLED, f.stage());
        f.flow.proceed();
        f.flow.retry();
        f.worker.runAll();
        assertEquals(Phase.UPDATES, f.phase(), "an installed update needs a restart before entry");
        assertEquals(0, f.connects.get());
    }

    @Test void aRequiredUpdateBlocksEntryUntilInstalled() {
        var f = new Fixture(installed -> UpdateCheck.required("2.0.0"));
        f.flow.start();
        f.worker.runAll();
        assertEquals(Stage.REQUIRED_UPDATE, f.stage());
        assertFalse(f.flow.state().update().canContinue());
        f.flow.proceed();
        f.flow.retry();
        assertTrue(f.worker.tasks.isEmpty(), "Continue and Retry start nothing");
        f.flow.update();
        f.worker.runAll();
        assertEquals(Stage.INSTALLED, f.stage());
        f.flow.proceed();
        f.worker.runAll();
        assertEquals(0, f.connects.get(), "the old client never reaches the server");
    }

    @Test void aFailedRequiredInstallOffersOnlyRetry() {
        var attempts = new AtomicInteger();
        var f = new Fixture(installed -> UpdateCheck.required("2.0.0"), (update, progress) -> {
            progress.accept(.6);
            if (attempts.getAndIncrement() == 0) throw new IOException("download interrupted");
            progress.accept(1);
        }, route -> { });
        f.flow.start();
        f.worker.runAll();
        f.flow.update();
        f.worker.runAll();
        assertEquals(Stage.INSTALL_FAILED, f.stage());
        assertEquals("download interrupted", f.flow.state().update().failure());
        assertEquals(.6, f.flow.state().update().progress());
        f.flow.proceed();
        assertTrue(f.worker.tasks.isEmpty(), "a required update cannot be skipped after a failure");
        f.flow.retry();
        f.worker.runAll();
        assertEquals(Stage.INSTALLED, f.stage());
        assertEquals(2, f.installs.get());
        assertEquals(0, f.connects.get());
    }

    @Test void aFailedOptionalInstallMayStillContinue() {
        var f = new Fixture(installed -> UpdateCheck.optional("1.1.0"),
                (update, progress) -> { throw new IOException("no space"); }, route -> { });
        f.flow.start();
        f.worker.runAll();
        f.flow.update();
        f.worker.runAll();
        assertEquals(Stage.INSTALL_FAILED, f.stage());
        f.flow.proceed();
        f.worker.runAll();
        assertEquals(Phase.CONNECTED, f.phase());
    }

    @Test void aFailedCheckRetriesOrContinues() {
        var failing = new AtomicInteger(1);
        var f = new Fixture(installed -> {
            if (failing.getAndDecrement() > 0) throw new IOException("service unreachable");
            return UpdateCheck.upToDate(installed);
        });
        f.flow.start();
        f.worker.runAll();
        assertEquals(Stage.CHECK_FAILED, f.stage());
        assertEquals("service unreachable", f.flow.state().update().failure());
        f.flow.retry();
        f.worker.runAll();
        assertEquals(List.of("CHECKING", "CHECK_FAILED", "CHECKING", "UP_TO_DATE", "CONNECTING", "CONNECTED"), f.trace);

        var skipped = new Fixture(installed -> { throw new IOException("service unreachable"); });
        skipped.flow.start();
        skipped.worker.runAll();
        skipped.flow.proceed();
        skipped.worker.runAll();
        assertEquals(Phase.CONNECTED, skipped.phase(), "an unreachable update service does not lock players out");
    }

    @Test void aFailedConnectionRetriesUntilItConnects() {
        var refusals = new AtomicInteger(2);
        var f = new Fixture(installed -> UpdateCheck.upToDate(installed), (u, p) -> { }, route -> {
            assertEquals(ROUTE, route);
            if (refusals.getAndDecrement() > 0) throw new IOException("Connection refused");
        });
        f.flow.start();
        f.worker.runAll();
        assertEquals(Phase.CONNECTION_FAILED, f.phase());
        assertEquals("Connection refused", f.flow.state().failure());
        f.flow.retry();
        f.worker.runAll();
        assertEquals(Phase.CONNECTION_FAILED, f.phase());
        f.flow.retry();
        f.worker.runAll();
        assertEquals(Phase.CONNECTED, f.phase());
        assertEquals(3, f.connects.get());
        assertEquals(1, f.checks.get(), "a connection retry does not repeat the update check");
    }

    @Test void repeatedPressesWhileWorkingStartNothingTwice() {
        var f = new Fixture(installed -> UpdateCheck.optional("1.1.0"));
        f.flow.start();
        f.flow.start();
        f.updates.check();
        f.worker.runNext();
        assertTrue(f.worker.tasks.isEmpty(), "one check while checking");
        f.flow.update();
        f.flow.update();
        f.flow.proceed();
        f.worker.runNext();
        assertTrue(f.worker.tasks.isEmpty(), "one install while installing");
        assertEquals(1, f.installs.get());

        var connecting = new Fixture(installed -> UpdateCheck.optional("1.1.0"));
        connecting.flow.start();
        connecting.worker.runAll();
        connecting.flow.proceed();
        connecting.flow.proceed();
        connecting.flow.retry();
        connecting.worker.runAll();
        assertEquals(1, connecting.connects.get(), "one connection attempt while connecting");
    }

    @Test void checksFromSettingsAfterConnectingNeverDialAgain() {
        var f = new Fixture(installed -> UpdateCheck.upToDate(installed));
        f.flow.start();
        f.worker.runAll();
        assertEquals(Phase.CONNECTED, f.phase());
        f.updates.check();
        f.worker.runAll();
        assertEquals(Stage.UP_TO_DATE, f.updates.state().stage());
        assertEquals(1, f.connects.get());
        assertEquals(Phase.CONNECTED, f.phase());
    }

    @Test void aBrokenServiceAnswerIsAFailureNotACrash() {
        var f = new Fixture(installed -> null);
        f.flow.start();
        f.worker.runAll();
        assertEquals(Stage.CHECK_FAILED, f.stage());
    }

    @Test void playOfflineIsOfferedOnlyAfterAFailedConnectionAndDialsTheLocalServer() {
        var refusals = new AtomicInteger(1);
        var f = new Fixture(installed -> UpdateCheck.optional("1.1.0"), (u, p) -> { }, route -> {
            if (route.equals(ROUTE) && refusals.getAndDecrement() > 0) throw new IOException("Connection refused");
        });
        f.flow.start();
        f.worker.runAll();
        f.flow.playOffline();
        assertTrue(f.worker.tasks.isEmpty(), "no offline play while an update is offered");
        f.flow.proceed();
        f.worker.runAll();
        assertEquals(Phase.CONNECTION_FAILED, f.phase());
        f.flow.playOffline();
        assertEquals(Phase.OFFLINE_STARTING, f.phase());
        f.flow.playOffline();
        f.flow.retry();
        f.worker.runAll();
        assertEquals(Phase.OFFLINE, f.phase());
        assertEquals(new ServerRoute("127.0.0.1", 40404), f.flow.state().route());
        assertEquals(List.of(ROUTE, new ServerRoute("127.0.0.1", 40404)), f.dialed, "one offline connection, no retry of the real server");
        f.flow.retry();
        f.flow.proceed();
        f.updates.check();
        f.worker.runAll();
        assertEquals(Phase.OFFLINE, f.phase(), "offline play stays offline");
        assertEquals(2, f.connects.get());
    }

    @Test void anOfflineServerThatCannotStartReturnsToTheFailedConnection() {
        var f = new Fixture(installed -> UpdateCheck.upToDate(installed), (u, p) -> { },
                route -> { throw new IOException("Connection refused"); });
        f.offline = () -> { throw new IOException("Offline play could not start: disk full"); };
        f.flow.start();
        f.worker.runAll();
        f.flow.playOffline();
        f.worker.runAll();
        assertEquals(Phase.CONNECTION_FAILED, f.phase());
        assertEquals("Offline play could not start: disk full", f.flow.state().failure());
        assertEquals(ROUTE, f.flow.state().route());
        assertEquals(1, f.connects.get(), "the local server was never dialed");
    }

    @Test void aServerRefusalForcesTheUpdateAndNeverLoopsOrOffersContinue() {
        var refusals = new AtomicInteger(1);
        var f = new Fixture(installed -> f0check(installed), (u, p) -> { }, route -> {
            if (refusals.getAndDecrement() > 0) throw new UpdateRequiredException("1.2.0", "This server needs Deck Duel 1.2.0 or newer.");
        });
        f.flow.start();
        f.worker.runAll();
        assertEquals(Phase.UPDATES, f.phase(), "the refusal leads back to the update step");
        assertEquals("1.2.0", f.flow.state().serverRequires());
        assertEquals(Stage.OPTIONAL_UPDATE, f.stage(), "the re-check found the release");
        assertFalse(f.flow.state().canContinue(), "an optional update becomes mandatory for this server");
        f.flow.proceed();
        assertTrue(f.worker.tasks.isEmpty());
        assertEquals(1, f.connects.get());
    }

    private static int checksSoFar = 0;

    /** Up to date on the first check, then a 1.2.0 release on the re-check. */
    private static UpdateCheck f0check(String installed) {
        return checksSoFar++ % 2 == 0 ? UpdateCheck.upToDate(installed) : UpdateCheck.optional("1.2.0");
    }

    @Test void aServerRefusalWithNoUpdateAvailableExplainsInsteadOfReconnecting() {
        var f = new Fixture(installed -> UpdateCheck.upToDate(installed), (u, p) -> { },
                route -> { throw new UpdateRequiredException("9.0.0", "This server needs Deck Duel 9.0.0 or newer."); });
        f.flow.start();
        f.worker.runAll();
        assertEquals(Phase.CONNECTION_FAILED, f.phase());
        assertTrue(f.flow.state().failure().contains("needs Deck Duel 9.0.0 or newer"));
        assertEquals(1, f.connects.get(), "no automatic retry loop");
        f.flow.retry();
        f.worker.runAll();
        assertEquals(2, f.connects.get(), "Retry tries again on request");
    }
}
