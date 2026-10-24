package deckduel.client.startup;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.startup.StartupFlow.Phase;
import deckduel.client.startup.UpdateFlow.Stage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Each local fixture drives the real flows into the state it is named for, without contacting anything. */
class StartupFixtureTest {
    private record Run(StartupFlow flow, AtomicInteger realConnects) { }

    private static Run run(StartupFixture fixture) {
        StartupServices services = fixture.services(0);
        assertEquals(fixture.id(), services.fixture(), "a fixture run is always labelled");
        var realConnects = new AtomicInteger();
        var updates = new UpdateFlow("1.0.0", services.updates(), services.installer(), Runnable::run, Runnable::run);
        var flow = new StartupFlow(updates, services.connection().apply(route -> realConnects.incrementAndGet()),
                () -> { throw new AssertionError("fixtures never start offline play on their own"); },
                ServerRoute.LOCAL_DEVELOPMENT, Runnable::run, Runnable::run, 0);
        flow.start();
        return new Run(flow, realConnects);
    }

    @Test void upToDateConnectsThroughTheRealConnector() {
        var run = run(StartupFixture.UP_TO_DATE);
        assertEquals(Phase.CONNECTED, run.flow.state().phase());
        assertEquals(Stage.UP_TO_DATE, run.flow.state().update().stage());
        assertEquals(1, run.realConnects.get());
    }

    @Test void optionalUpdateOffersAMadeUpVersionAndInstallsNothingReal() {
        var run = run(StartupFixture.OPTIONAL_UPDATE);
        assertEquals(Stage.OPTIONAL_UPDATE, run.flow.state().update().stage());
        assertEquals(StartupFixture.OPTIONAL_VERSION, run.flow.state().update().offer().latestVersion());
        run.flow.update();
        assertEquals(Stage.INSTALLED, run.flow.state().update().stage());
        assertEquals(0, run.realConnects.get());
    }

    @Test void requiredUpdateBlocksContinue() {
        var run = run(StartupFixture.REQUIRED_UPDATE);
        run.flow.proceed();
        assertEquals(Stage.REQUIRED_UPDATE, run.flow.state().update().stage());
        assertEquals(0, run.realConnects.get());
    }

    @Test void installFailsOnceThenRetrySucceeds() {
        var run = run(StartupFixture.INSTALL_FAILS);
        run.flow.update();
        var failed = run.flow.state().update();
        assertEquals(Stage.INSTALL_FAILED, failed.stage());
        assertTrue(failed.failure().startsWith("Test fixture:"));
        assertEquals(.55, failed.progress(), 1e-9, "fails part-way after reporting progress");
        run.flow.proceed();
        assertEquals(Stage.INSTALL_FAILED, run.flow.state().update().stage(), "still required");
        run.flow.retry();
        assertEquals(Stage.INSTALLED, run.flow.state().update().stage());
    }

    @Test void checkFailsOnceThenRetryConnects() {
        var run = run(StartupFixture.CHECK_FAILS);
        assertEquals(Stage.CHECK_FAILED, run.flow.state().update().stage());
        run.flow.retry();
        assertEquals(Phase.CONNECTED, run.flow.state().phase());
    }

    @Test void connectionFailsOnceWithoutDialingThenRetryUsesTheRealConnector() {
        var run = run(StartupFixture.CONNECTION_FAILS);
        assertEquals(Phase.CONNECTION_FAILED, run.flow.state().phase());
        assertTrue(run.flow.state().failure().startsWith("Test fixture:"));
        assertEquals(0, run.realConnects.get());
        run.flow.retry();
        assertEquals(Phase.CONNECTED, run.flow.state().phase());
        assertEquals(1, run.realConnects.get());
    }

    @Test void commandLineNamesRoundTripAndUnknownNamesAreRejected() {
        Arrays.stream(StartupFixture.values()).forEach(f -> assertEquals(f, StartupFixture.fromId(f.id())));
        assertEquals("optional-update", StartupFixture.OPTIONAL_UPDATE.id());
        assertThrows(IllegalArgumentException.class, () -> StartupFixture.fromId("production"));
    }

    @Test void theInstalledVersionIsTheBuildVersionAndTheDefaultRouteIsLocal() {
        assertTrue(InstalledVersion.current().matches("\\d+\\.\\d+\\.\\d+.*"), InstalledVersion.current());
        assertTrue(ServerRoute.LOCAL_DEVELOPMENT.local());
        assertEquals("local development server 127.0.0.1:5555", ServerRoute.LOCAL_DEVELOPMENT.describe());
        assertFalse(new ServerRoute("192.0.2.10", 5555).local());
        assertEquals(ServerRoute.LOCAL_DEVELOPMENT.host(), new deckduel.client.ClientSettings().host());
        assertEquals(ServerRoute.LOCAL_DEVELOPMENT.port(), new deckduel.client.ClientSettings().port());
    }
}
