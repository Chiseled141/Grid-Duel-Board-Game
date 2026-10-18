package deckduel.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.core.PlayerColor;
import deckduel.core.duel.*;
import deckduel.core.duel.DeckPresentationFixtures.Transition;
import deckduel.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Accepted engine transitions exercise actual model dispatch, layout and overlay integration. */
class DeckPresentationTest {
    record Ui(ClientModel model, GamePanel panel, DeckDrawOverlay overlay, AtomicLong clock) { }

    static Ui create(PlayerView view) {
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        var clock = new AtomicLong();
        var overlay = new DeckDrawOverlay(clock::get);
        var panel = new GamePanel(model, overlay);
        model.handleMatchSync(sync(view));
        panel.setSize(1280, 660);
        layout(panel);
        return new Ui(model, panel, overlay, clock);
    }

    static MatchSync sync(PlayerView view) { return new MatchSync("deck-test", "LOCAL", view, false, 1); }

    static void deliver(Ui ui, Transition transition, PlayerColor viewer) {
        var after = transition.after(viewer);
        // Deliberately exercise the reply-first order that previously lost presentation events.
        ui.model.handleActionReply(new ActionReply("deck-test", 1,
                new ActionReceipt(true, after.revision(), null), sync(after)));
        ui.model.handleGameUpdate(new GameUpdate(sync(after), transition.result().events()));
    }

    static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }

    static Object field(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SuppressWarnings("unchecked")
    static List<DeckDrawOverlay.Flight> flights(Ui ui) {
        return (List<DeckDrawOverlay.Flight>) field(ui.overlay, "flights");
    }

    private void withTransition(Transition transition, PlayerColor viewer, java.util.function.Consumer<Ui> assertions) throws Exception {
        Ui[] ref = new Ui[1];
        try {
            SwingUtilities.invokeAndWait(() -> ref[0] = create(transition.before(viewer)));
            SwingUtilities.invokeAndWait(() -> deliver(ref[0], transition, viewer));
            // Layout callback is queued before this EDT barrier; no sleeps or wall-clock guessing.
            SwingUtilities.invokeAndWait(() -> assertions.accept(ref[0]));
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (ref[0] != null) ref[0].panel.removeNotify(); });
        }
    }

    @ParameterizedTest
    @CsvSource({"BLUE,BLUE,false", "RED,RED,false", "BLUE,RED,false", "RED,BLUE,false",
            "BLUE,BLUE,true", "RED,RED,true", "BLUE,RED,true", "RED,BLUE,true"})
    void movementDrawsFromTheMovementDeckIncludingSameCard(PlayerColor actor, PlayerColor viewer, boolean same) throws Exception {
        var transition = DeckPresentationFixtures.movement(actor, same);
        withTransition(transition, viewer, ui -> {
            assertEquals(transition.after(viewer), ui.model.view());
            assertTrue(flights(ui).isEmpty(), "basic movement never draws a card");
            assertTrue(transition.after(viewer).blueMovementHand().isEmpty());
            assertTrue(transition.after(viewer).redMovementHand().isEmpty());

        });
    }

    @ParameterizedTest
    @CsvSource({"mana-surge,BLUE,BLUE,18,1", "mana-surge,BLUE,RED,18,1",
            "refocus,RED,RED,18,3", "refocus,RED,BLUE,18,3", "refocus,BLUE,BLUE,0,0",
            "mana-surge,BLUE,BLUE,0,0", "mana-surge,BLUE,RED,0,0",
            "foresight,BLUE,BLUE,18,0", "foresight,BLUE,RED,18,0",
            "foresight,RED,RED,1,1", "foresight,RED,BLUE,1,1", "foresight,BLUE,BLUE,0,0"})
    void skillDrawsFollowConsumptionAndPrivacy(String skill, PlayerColor actor, PlayerColor viewer, int deckCount, int count) throws Exception {
        var transition = DeckPresentationFixtures.skill(skill, actor, deckCount);
        withTransition(transition, viewer, ui -> {
            assertEquals(count, flights(ui).size());
            var after = transition.after(viewer);
            assertEquals(2, find(ui.panel, DeckStackPanel.class).size());
            var badge = (PlayerBadgePanel) field(ui.panel, "ownBadge");
            assertEquals(viewer, field(badge, "color"));
            assertEquals(viewer == PlayerColor.BLUE ? after.blueMana() : after.redMana(), field(badge, "mana"));
            assertEquals(7, field(badge, "cap"), "cap comes from live balance config");
            assertEquals(after.reservedMana(), field(badge, "reserved"));
            var opponent = (PlayerBadgePanel) field(ui.panel, "opponentBadge");
            assertEquals(viewer.opponent(), field(opponent, "color"));
            assertEquals(viewer == PlayerColor.BLUE ? after.redMana() : after.blueMana(), field(opponent, "mana"));
            assertEquals(0, field(opponent, "reserved"));
            var sourceDeck = (DeckStackPanel) field(ui.panel, actor == viewer ? "drawPile" : "opponentDrawPile");
            for (var flight : flights(ui)) {
                assertEquals(SwingUtilities.convertRectangle(sourceDeck, sourceDeck.topBounds(), ui.overlay), flight.source().get());
                if (flight.key().startsWith("opponent-slot:")) assertNull(flight.face(), "opponent replacement must have no face image");
                else assertNotNull(flight.face());
            }
            if (actor != viewer && count > 0) {
                assertEquals(transition.before(viewer).opponentSkillCount(), after.opponentSkillCount(),
                        "draw still occurs when opponent hand count stays the same");
            }
            assertTrue(find(ui.panel, JList.class).stream().allMatch(list ->
                    List.of("playedCards", "matchChatHistory").contains(list.getName())),
                    "Only public card history and match chat use lists; the old Move Log stays removed");
            assertTrue(find(ui.panel, JLabel.class).stream().noneMatch(l -> l.getText().contains("MOVE LOG")));
            assertEquals(2, find(ui.panel, DeckStackPanel.class).size());
            assertEquals(2, find(ui.panel, PlayerBadgePanel.class).size());
            assertEquals(after.ownSkillHand().size(), find(ui.panel, SkillCardPanel.class).size());
        });
    }

    @ParameterizedTest @CsvSource({"BLUE,BLUE", "BLUE,RED", "RED,RED", "RED,BLUE"})
    void foresightEmptyEventResolutionStillDraws(PlayerColor actor, PlayerColor viewer) throws Exception {
        var transition = DeckPresentationFixtures.resolveForesight(actor);
        assertTrue(transition.result().events().isEmpty());
        withTransition(transition, viewer, ui -> {
            assertEquals(1, flights(ui).size());
            var sourceDeck = (DeckStackPanel) field(ui.panel, actor == viewer ? "drawPile" : "opponentDrawPile");
            assertEquals(SwingUtilities.convertRectangle(sourceDeck, sourceDeck.topBounds(), ui.overlay), flights(ui).get(0).source().get());
            assertEquals(actor != viewer, flights(ui).get(0).face() == null);
            assertEquals(0, field(field(ui.panel, "ownBadge"), "reserved"));
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void refocusRedrawsBothEvenWhenTheSamePairReturns(boolean opponent) throws Exception {
        var transition = DeckPresentationFixtures.refocusSameHand(PlayerColor.BLUE);
        withTransition(transition, opponent ? PlayerColor.RED : PlayerColor.BLUE, ui -> {
            assertEquals(3, flights(ui).size());
            assertEquals(0, flights(ui).stream().filter(f -> f.key().startsWith("move:")).count());
            assertEquals(0, find(ui.panel, CardPanel.class).stream().filter(DeckDrawOverlay::isMasked).count(),
                    "only the two movement destinations are masked, even when components are reused");
            assertEquals(3, flights(ui).stream().map(DeckDrawOverlay.Flight::key).distinct().count());
        });
    }

    @ParameterizedTest @ValueSource(strings = {"reduced", "pause", "leave", "reconnect", "new-match", "disconnect", "dispose", "resize"})
    void lifecycleCancelsFlightsAndQueuedLayoutCallbacks(String reason) throws Exception {
        var transition = DeckPresentationFixtures.skill("mana-surge", PlayerColor.BLUE, 18);
        for (boolean queued : new boolean[]{false, true}) {
            Ui[] ref = new Ui[1];
            try {
                SwingUtilities.invokeAndWait(() -> ref[0] = create(transition.before(PlayerColor.BLUE)));
                SwingUtilities.invokeAndWait(() -> {
                    deliver(ref[0], transition, PlayerColor.BLUE);
                    if (queued) cancel(ref[0], reason);
                });
                SwingUtilities.invokeAndWait(() -> { if (!queued) { assertEquals(1, flights(ref[0]).size()); cancel(ref[0], reason); } });
                SwingUtilities.invokeAndWait(() -> {
                    assertTrue(flights(ref[0]).isEmpty(), reason + " must not leave/restart a flight");
                    assertFalse(((javax.swing.Timer) field(ref[0].overlay, "timer")).isRunning());
                    for (var card : find(ref[0].panel, CardPanel.class)) assertFalse(DeckDrawOverlay.isMasked(card));
                });
            } finally { SwingUtilities.invokeAndWait(() -> { if (ref[0] != null) ref[0].panel.removeNotify(); }); }
        }
    }

    private void cancel(Ui ui, String reason) {
        switch (reason) {
            case "reduced" -> ui.model.setReducedMotion(true);
            case "pause" -> ui.model.adoptSync(new MatchSync("deck-test", "LOCAL", ui.model.view(), true, 1));
            case "leave" -> ui.model.leaveToLobby();
            case "reconnect" -> ui.model.handleMatchSync(sync(ui.model.view()));
            case "new-match" -> ui.model.handleMatchSync(new MatchSync("new", "LOCAL", ui.model.view(), false, 1));
            case "disconnect" -> ui.model.handleConnectionLost();
            case "dispose" -> ui.panel.removeNotify();
            case "resize" -> ui.panel.setSize(1440, 820);
            default -> throw new AssertionError(reason);
        }
    }

    @Test void skipAndEndTurnNeverDraw() throws Exception {
        var before = DeckPresentationFixtures.movement(PlayerColor.BLUE, false).before();
        for (GameAction action : List.of(new GameAction.SkipMovement(), new GameAction.EndTurn())) {
            var accepted = (ActionResult.Accepted) RulesEngine.apply(before, before.turn(), action);
            withTransition(new Transition(before, accepted), PlayerColor.BLUE, ui -> assertTrue(flights(ui).isEmpty()));
            before = accepted.nextState();
        }
    }

    static <T> List<T> find(Container parent, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(find(container, type));
        }
        return result;
    }
}
