package deckduel.client.update;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.startup.UpdateCheck;
import deckduel.client.startup.UpdateFlow;
import deckduel.client.state.ClientFixtures;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.core.card.Seat;
import deckduel.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Updates never close the game during a match, hold new matches while installing, and recover from a failed hand-over. */
class UpdateFinisherTest {
    private record Fixture(ClientModel model, UpdateFlow updates, List<Message> sent, List<String> events) { }

    private static Fixture fixture(boolean failHandOver, boolean real) {
        List<Message> sent = new ArrayList<>();
        List<String> events = new ArrayList<>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
        });
        var updates = new UpdateFlow("1.0.0", installed -> UpdateCheck.optional("1.1.0"),
                (update, progress) -> progress.accept(1), Runnable::run, Runnable::run);
        new UpdateFinisher(model, updates, !real ? null : version -> {
            if (failHandOver) throw new IOException("java not found");
            events.add("handOver " + version);
        }, () -> events.add("close"), text -> events.add("notice " + text));
        model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
        sent.clear();
        return new Fixture(model, updates, sent, events);
    }

    @Test void aVerifiedUpdateWaitsForTheMatchToEndThenHandsOverAndCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(false, true);
            f.model.handleMessage(ClientFixtures.update("M1", Seat.FIRST));
            assertTrue(f.model.matchInProgress());
            f.updates.check();
            f.updates.install();
            assertEquals(UpdateFlow.Stage.INSTALLED, f.updates.state().stage());
            assertEquals(List.of("notice Update 1.1.0 is ready. Deck Duel restarts when this match ends."), f.events,
                    "the match is never interrupted");
            f.model.startPractice(true);
            assertTrue(f.sent.isEmpty(), "no new match while an update waits to install");
            f.model.leaveMatch();
            assertEquals(List.of("notice Update 1.1.0 is ready. Deck Duel restarts when this match ends.", "handOver 1.1.0", "close"), f.events);
        });
    }

    @Test void withoutAMatchTheGameHandsOverAtOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(false, true);
            f.updates.check();
            f.updates.install();
            assertEquals(List.of("handOver 1.1.0", "close"), f.events);
        });
    }

    @Test void aFailedHandOverKeepsTheGameOpenAndRetryTriesAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(true, true);
            f.updates.check();
            f.updates.install();
            assertEquals(UpdateFlow.Stage.INSTALL_FAILED, f.updates.state().stage());
            assertTrue(f.updates.state().failure().contains("java not found"));
            assertFalse(f.events.contains("close"), "the game stays open");
            assertFalse(f.model.newMatchesBlocked(), "matches are allowed again after a failure");
        });
    }

    @Test void fixturesWithoutARealInstallationNeverCloseTheGame() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(false, false);
            f.updates.check();
            f.updates.install();
            assertEquals(UpdateFlow.Stage.INSTALLED, f.updates.state().stage());
            assertTrue(f.events.isEmpty());
            assertFalse(f.model.newMatchesBlocked());
        });
    }

    @Test void aWaitingRoomClosesWhenAnUpdateStartsInstalling() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(false, true);
            f.model.createMatch();
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.sent.clear();
            var slow = new UpdateFlow("1.0.0", installed -> UpdateCheck.optional("1.1.0"), (u, p) -> { }, task -> { }, Runnable::run);
            new UpdateFinisher(f.model, slow, version -> { }, () -> { }, text -> { });
            slow.check();
            f.model.setUpdating(true);
            assertEquals(List.of(new CancelRoomRequest()), f.sent, "the room is closed before anyone can join");
            assertNull(f.model.pendingRoomCode());
        });
    }
}
