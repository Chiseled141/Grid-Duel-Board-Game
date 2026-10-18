package deckduel.client.ui;

import deckduel.client.state.*;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.Square;
import deckduel.core.duel.*;
import deckduel.net.*;
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

    @Test void gestureBoundHandCardsAnimateOnHoverAndStopOnRemoval() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(movement(false).before);
            try {
                for (var component : ui.hand.getComponents()) {
                    var card = (JComponent) component;
                    var entered = new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_ENTERED,
                            System.currentTimeMillis(), 0, 20, 20, 0, false);
                    for (var listener : card.getMouseListeners()) listener.mouseEntered(entered);
                    var timer = (Timer) field(card, card instanceof CardPanel ? "tween" : "hoverTimer");
                    assertNotNull(timer, "gesture binding must not disable hover feedback");
                    assertTrue(timer.isRunning());
                    if (card instanceof CardPanel movement) movement.setReducedMotion(true);
                    else ((SkillCardPanel) card).setReducedMotion(true);
                    assertFalse(timer.isRunning(), "reduced motion stops active hover animation");
                }
            } finally { ui.panel.removeNotify(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void movementKeepsBoardFeedbackWithoutASideCard(boolean same) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = movement(same); var ui = ui(f.before);
            try {
                deliver(ui, f);
                assertEquals(f.after, ui.model.view(), "state must already be authoritative");
                assertFalse(play(ui).busy(), "movement does not fly into the skill-only display");
                assertNull(field(field(ui.panel, "ownPlay"), "face"));
                assertNotNull(ui.model.lastMoveFrom()); assertNotNull(ui.model.lastMoveTo());
                assertEquals(3, ui.hand.getComponentCount(), "decoration cannot intercept clicks as a child");
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
            var f = skill(); var ui = ui(f.before);
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
            var f = skill(); var ui = ui(f.before);
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
                assertTrue(play(ui).busy(), "skill flight and hand reflow coexist");
            } finally { frame.dispose(); }
        });
    }

    @Test void reducedMotionStillShowsTheAcceptedSkillBesideTheBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = skill(); var ui = ui(f.before);
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void playedCardsExposeOnlyAcceptedPublicDetails(boolean skill, boolean opponent) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rules = RulesConfigLoader.load(null);
            for (long seed = 0; seed < 1000; seed++) {
                var game = RulesEngine.newGame(rules, seed);
                var actor = game.turn();
                if (skill) game = ((ActionResult.Accepted) RulesEngine.apply(game, actor,
                        new GameAction.SkipMovement())).nextState();
                var actorView = RulesEngine.viewFor(game, actor);
                var resource = actorView.ownSkillHand().stream()
                        .filter(c -> c.definitionId().equals("mana-surge")).findFirst();
                if (skill && resource.isEmpty()) continue;
                GameAction action = skill ? new GameAction.CastSkill(resource.orElseThrow().instanceId(), new SkillTargets.None())
                        : Targeting.legalMoves(actorView).get(0);
                var accepted = (ActionResult.Accepted) RulesEngine.apply(game, actor, action);
                var viewer = opponent ? actor.opponent() : actor;
                var ui = ui(RulesEngine.viewFor(game, viewer));
                try {
                    var area = (PlayedCardArea) field(ui.panel, opponent ? "enemyPlay" : "ownPlay");
                    assertNull(area.getToolTipText());
                    ui.model.handleGameUpdate(new GameUpdate(sync(RulesEngine.viewFor(accepted.nextState(), viewer)), accepted.events()));
                    assertNull(area.getToolTipText(), "concealed/in-flight cards cannot expose details");
                    play(ui).cancel();
                    String expected = skill ? deckduel.config.SkillText.render(rules.skill("mana-surge"))
                            : "MOVE";
                    if (skill) assertTrue(area.getToolTipText().contains(expected));
                    else {
                        assertNull(area.getToolTipText());
                        assertNull(field(area, "face"), "movement must not enter either skill area");
                        assertFalse(play(ui).busy());
                        assertNotNull(ui.model.lastMoveFrom()); assertNotNull(ui.model.lastMoveTo());
                    }
                    assertTrue(java.util.Arrays.stream(area.getMouseListeners()).anyMatch(CardDetails.class::isInstance));
                    ui.model.handleMatchSync(new MatchSync("new", "ROOM", RulesEngine.viewFor(game, viewer), false, 1));
                    assertNull(area.getToolTipText(), "new matches clear old card details");
                } finally { ui.panel.removeNotify(); }
                return;
            }
            fail("No deterministic public-card fixture");
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void movementPreservesThePreviousSkillOnEitherSide(boolean opponent) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var transition = DeckPresentationFixtures.skill("mana-surge", PlayerColor.BLUE, 18);
            var game = transition.before();
            var viewer = opponent ? PlayerColor.RED : PlayerColor.BLUE;
            var ui = ui(RulesEngine.viewFor(game, viewer));
            try {
                ui.model.setReducedMotion(true);
                var accepted = transition.result(); game = accepted.nextState();
                ui.model.handleGameUpdate(new GameUpdate(sync(RulesEngine.viewFor(game, viewer)), accepted.events()));
                var area = (PlayedCardArea) field(ui.panel, opponent ? "enemyPlay" : "ownPlay");
                Object face = field(area, "face"); String details = area.getToolTipText();
                assertNotNull(face); assertNotNull(details);
                accepted = (ActionResult.Accepted) RulesEngine.apply(game, game.turn(), new GameAction.EndTurn());
                game = accepted.nextState();
                ui.model.handleGameUpdate(new GameUpdate(sync(RulesEngine.viewFor(game, viewer)), accepted.events()));
                var move = Targeting.legalMoves(RulesEngine.viewFor(game, game.turn())).get(0);
                accepted = (ActionResult.Accepted) RulesEngine.apply(game, game.turn(), move); game = accepted.nextState();
                ui.model.handleGameUpdate(new GameUpdate(sync(RulesEngine.viewFor(game, viewer)), accepted.events()));
                assertSame(face, field(area, "face")); assertEquals(details, area.getToolTipText());
                assertNotNull(ui.model.lastMoveFrom()); assertNotNull(ui.model.lastMoveTo());
                assertFalse(play(ui).busy());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void turnEndDoesNotCutOffAnAcceptedPieceMove() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var transition = DeckPresentationFixtures.movement(PlayerColor.RED, false);
            var ui = ui(transition.before(PlayerColor.BLUE));
            try {
                var moved = transition.result();
                ui.model.handleGameUpdate(new GameUpdate(sync(transition.after(PlayerColor.BLUE)), moved.events()));
                var board = (BoardPanel) field(ui.panel, "boardPanel");
                var moving = List.copyOf((List<?>) field(board, "movingPieces"));
                assertFalse(moving.isEmpty());
                Object started = field(board, "moveAnimStart");
                var end = (ActionResult.Accepted) RulesEngine.apply(moved.nextState(), PlayerColor.RED, new GameAction.EndTurn());
                ui.model.handleGameUpdate(new GameUpdate(sync(RulesEngine.viewFor(end.nextState(), PlayerColor.BLUE)), end.events()));
                assertEquals(moving, field(board, "movingPieces"));
                assertEquals(started, field(board, "moveAnimStart"), "non-board updates do not restart or truncate travel");
                assertEquals(RulesEngine.viewFor(end.nextState(), PlayerColor.BLUE), ui.model.view());
                board.finishMoveAnimation(); assertTrue(((List<?>) field(board, "movingPieces")).isEmpty());
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
