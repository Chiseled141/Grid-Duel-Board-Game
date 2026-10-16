package onitama.client.ui;

import onitama.client.state.*;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.swing.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real accepted updates through the model and game panel on the EDT. */
class PresentationRegressionTest {
    private record ActionFixture(PlayerView before, PlayerView after, List<PublicEvent> events) { }
    private record Ui(ClientModel model, GamePanel panel, CardHandPanel hand) { }

    private static Object field(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private Ui ui(PlayerView view) {
        var model = new ClientModel(new ServerConnection() {
            public void send(Message m) { }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        var panel = new GamePanel(model);
        model.handleMatchSync(sync(view));
        panel.setSize(1440, 900);
        layout(panel);
        return new Ui(model, panel, (CardHandPanel) field(panel, "hand"));
    }

    private void layout(java.awt.Container parent) {
        parent.doLayout();
        for (var child : parent.getComponents()) if (child instanceof java.awt.Container c) layout(c);
    }
    private CardPlayOverlay play(Ui ui) { return (CardPlayOverlay) field(ui.panel, "playOverlay"); }

    private MatchSync sync(PlayerView view) { return new MatchSync("test", "ROOM", view, false, 1); }
    private List<String> ownMoves(PlayerView v) {
        return v.viewer() == PlayerColor.BLUE ? v.blueMovementHand() : v.redMovementHand();
    }
    private ActionFixture movement(boolean redrawSame) {
        var rules = RulesConfigLoader.load(null);
        for (long seed = 0; seed < 1000; seed++) {
            var state = RulesEngine.newGame(rules, seed);
            var before = RulesEngine.viewFor(state, state.turn());
            var moves = Targeting.legalMoves(before);
            if (moves.isEmpty()) continue;
            var move = moves.get(0);
            var result = (ActionResult.Accepted) RulesEngine.apply(state, state.turn(), move);
            var after = RulesEngine.viewFor(result.nextState(), state.turn());
            if (ownMoves(after).contains(move.movementCardId()) == redrawSame)
                return new ActionFixture(before, after, result.events());
        }
        throw new AssertionError("No deterministic movement fixture");
    }
    private ActionFixture skill() {
        var rules = RulesConfigLoader.load(null);
        for (long seed = 0; seed < 1000; seed++) {
            var state = RulesEngine.newGame(rules, seed);
            state = ((ActionResult.Accepted) RulesEngine.apply(state, state.turn(), new GameAction.SkipMovement())).nextState();
            var before = RulesEngine.viewFor(state, state.turn());
            for (var card : before.ownSkillHand()) {
                if (!card.definitionId().equals("mana-surge")) continue;
                var result = (ActionResult.Accepted) RulesEngine.apply(state, state.turn(),
                        new GameAction.CastSkill(card.instanceId(), new SkillTargets.None()));
                return new ActionFixture(before, RulesEngine.viewFor(result.nextState(), state.turn()), result.events());
            }
        }
        throw new AssertionError("No deterministic skill fixture");
    }
    private void deliver(Ui ui, ActionFixture f) {
        ui.model.handleActionReply(new ActionReply("test", 1,
                new ActionReceipt(true, f.after.revision(), null), sync(f.after)));
        ui.model.handleGameUpdate(new GameUpdate(sync(f.after), f.events));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void movementDepartureSurvivesReplyFirstWithCurrentHand(boolean same) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(same); var ui = ui(f.before);
            try {
                deliver(ui, f);
                assertEquals(f.after, ui.model.view(), "state must already be authoritative");
                assertTrue(play(ui).busy(), "accepted play must survive hand reconciliation");
                assertEquals(5, ui.hand.getComponentCount(), "decoration cannot intercept clicks as a child");
                play(ui).cancel();
                assertFalse(play(ui).busy());
                assertEquals(f.after.ownSkillHand(), ((List<?>) field(ui.hand, "cards")).stream()
                        .filter(c -> c instanceof SkillCardPanel)
                        .map(c -> ((SkillCardPanel) c).instance()).toList());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void skillSpendAnimatesRemovedOwnCardAndAdoptsReplacement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = skill(); var ui = ui(f.before);
            try {
                deliver(ui, f);
                assertTrue(play(ui).busy());
                var committed = f.events.stream().filter(e -> e instanceof PublicEvent.SkillCommitted)
                        .map(e -> ((PublicEvent.SkillCommitted) e).skill()).findFirst().orElseThrow();
                assertFalse(f.after.ownSkillHand().contains(committed));
                play(ui).cancel();
                assertEquals(f.after.ownSkillHand(), ((List<?>) field(ui.hand, "cards")).stream()
                        .filter(c -> c instanceof SkillCardPanel)
                        .map(c -> ((SkillCardPanel) c).instance()).toList());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void duplicateStaleAndWrongMatchUpdatesDoNotChangeHistoryOrReplay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(false); var ui = ui(f.before);
            try {
                deliver(ui, f);
                var history = ui.model.historyMoves(); var timer = field(play(ui), "timer");
                ui.model.handleGameUpdate(new GameUpdate(sync(f.after), f.events));
                ui.model.handleGameUpdate(new GameUpdate(sync(f.before), f.events));
                ui.model.handleGameUpdate(new GameUpdate(new MatchSync("other", "ROOM", f.after, false, 1), f.events));
                assertEquals(history, ui.model.historyMoves());
                assertEquals(f.after, ui.model.view());
                assertSame(timer, field(play(ui), "timer"));
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void updateOlderThanAdoptedReplyCannotAnimateOrEnterHistory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(false); var ui = ui(f.before);
            try {
                ui.model.handleActionReply(new ActionReply("test", 1, new ActionReceipt(true, f.after.revision(), null), sync(f.after)));
                ui.model.handleGameUpdate(new GameUpdate(sync(f.before), f.events));
                assertTrue(ui.model.historyMoves().isEmpty());
                assertFalse(play(ui).busy());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void reducedMotionReachesExistingCardsAndCancelsAllActiveEffects() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(true); var ui = ui(f.before);
            try {
                deliver(ui, f);
                var cards = ui.hand.getComponents();
                var board = (BoardPanel) field(ui.panel, "boardPanel");
                board.setSelection(new Square(0, 0), List.of());
                ui.model.setReducedMotion(true);
                assertArrayEquals(cards, ui.hand.getComponents(), "preference must not require rebuilding the hand");
                for (var card : cards) assertEquals(true, field(card, "reducedMotion"));
                assertFalse(((Timer) field(play(ui), "timer")).isRunning());
                assertNull(field(board, "selectionPulseTimer"));
                play(ui).cancel();
                for (var card : cards) {
                    if (card instanceof CardPanel) assertNull(field(card, "tween"));
                    else assertNull(field(card, "hoverTimer"));
                }
            } finally { ui.panel.removeNotify(); }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"reconnect", "new-match", "disconnect", "dispose"})
    void lifecycleResetDiscardsTimersAndOutgoingCopy(String reason) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(true); var ui = ui(f.before);
            try {
                deliver(ui, f);
                var timer = (Timer) field(play(ui), "timer");
                assertTrue(timer.isRunning());
                switch (reason) {
                    case "reconnect" -> ui.model.handleMatchSync(sync(f.after));
                    case "new-match" -> ui.model.handleMatchSync(new MatchSync("next", "ROOM", f.before, false, 1));
                    case "disconnect" -> ui.model.handleConnectionLost();
                    case "dispose" -> ui.panel.removeNotify();
                }
                assertFalse(timer.isRunning());
                assertFalse(play(ui).busy());
                play(ui).cancel();
                for (var card : ui.hand.getComponents()) {
                    if (card instanceof CardPanel) assertNull(field(card, "tween"));
                }
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void acceptedActionKeepsFreshReflowInVisibleHand() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(false); var ui = ui(f.before);
            JFrame frame = new JFrame("Animation reflow regression");
            try {
                frame.setContentPane(ui.panel);
                frame.setSize(1280, 720);
                frame.setVisible(true);
                frame.validate();
                assertTrue(ui.hand.isShowing());
                deliver(ui, f);
                Timer reflow = (Timer) field(ui.hand, "reflowTimer");
                assertNotNull(reflow, "a visible replacement starts reflow");
                assertTrue(reflow.isRunning(), "accepted-event cleanup must not cancel the new reflow");
                assertTrue(play(ui).busy());
            } finally { frame.dispose(); }
        });
    }

    @Test void reducedMotionStillShowsTheAcceptedCardBesideTheBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(false); var ui = ui(f.before);
            try {
                ui.model.setReducedMotion(true);
                deliver(ui, f);
                assertFalse(play(ui).busy());
                var area = (PlayedCardArea) field(ui.panel, "ownPlay");
                assertNotNull(field(area, "face"), "reduced motion must retain public card information");
                assertEquals(false, field(area, "arriving"));
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void cancelledBoardMoveNeverRunsCaptureCallback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var board = new BoardPanel(); var invoked = new java.util.concurrent.atomic.AtomicBoolean();
            board.animateMoves(List.of(new BoardPanel.MovingPiece(1, new Square(0, 0), new Square(1, 1), PlayerColor.BLUE)),
                    () -> invoked.set(true));
            board.cancelAnimations(); board.finishMoveAnimation();
            assertFalse(invoked.get());
            assertFalse(((Timer) field(board, "animationTimer")).isRunning());
        });
    }
}
