package onitama.client.ui;

import onitama.client.state.*;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import onitama.net.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Fast remote/bot updates must play in sequence, without stale frame or hidden-card leaks. */
class AnimationSequenceTest {
    record Fixture(GameState before, ActionResult.Accepted move, ActionResult.Accepted skill, ActionResult.Accepted end) { }
    record Ui(ClientModel model, GamePanel panel, DeckDrawOverlay draws, AtomicLong clock) { }
    private Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private Fixture fixture(PlayerColor actor) {
        var rules = RulesConfigLoader.load(null);
        for (int seed = 0; seed < 1000; seed++) {
            var game = RulesEngine.newGame(rules, seed);
            if (game.turn() != actor) continue;
            var dash = RulesEngine.viewFor(game, actor).ownSkillHand().stream().filter(c -> c.definitionId().equals("dash")).findFirst();
            if (dash.isEmpty()) continue;
            var move = (ActionResult.Accepted) RulesEngine.apply(game, actor,
                    Targeting.legalMoves(RulesEngine.viewFor(game, actor)).get(0));
            var skillView = RulesEngine.viewFor(move.nextState(), actor);
            var initial = Targeting.skillOptions(skillView, dash.get().instanceId(), SelectionDraft.empty());
            if (initial.selectablePieceIds().isEmpty()) continue;
            int piece = initial.selectablePieceIds().get(0);
            var targets = Targeting.skillOptions(skillView, dash.get().instanceId(), new SelectionDraft(List.of(piece), List.of(), null));
            var complete = Targeting.skillOptions(skillView, dash.get().instanceId(),
                    new SelectionDraft(List.of(piece), List.of(targets.selectableSquares().get(0)), null));
            var skill = (ActionResult.Accepted) RulesEngine.apply(move.nextState(), actor,
                    new GameAction.CastSkill(dash.get().instanceId(), complete.completeTargets()));
            var end = (ActionResult.Accepted) RulesEngine.apply(skill.nextState(), actor, new GameAction.EndTurn());
            return new Fixture(game, move, skill, end);
        }
        throw new AssertionError("No dash fixture");
    }
    private Ui ui(Fixture fixture, PlayerColor viewer) {
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { fail("visual playback cannot send gameplay actions"); }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        var clock = new AtomicLong(); var draws = new DeckDrawOverlay(clock::get);
        var panel = new GamePanel(model, draws);
        model.handleMatchSync(new MatchSync("sequence", "ROOM", RulesEngine.viewFor(fixture.before, viewer), false, 1));
        panel.setSize(1440, 900); layout(panel);
        return new Ui(model, panel, draws, clock);
    }
    private void layout(Container parent) {
        parent.doLayout();
        for (var child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }
    private void deliver(Ui ui, ActionResult.Accepted accepted, PlayerColor viewer) {
        ui.model.handleGameUpdate(new GameUpdate(new MatchSync("sequence", "ROOM",
                RulesEngine.viewFor(accepted.nextState(), viewer), false, 1), accepted.events()));
    }
    private void advance(Ui ui) {
        // Deterministically complete the current visual batch; no sleeps or game-state mutation.
        ((CardPlayOverlay) field(ui.panel, "playOverlay")).cancel();
        ((BoardPanel) field(ui.panel, "boardPanel")).finishMoveAnimation();
        var timer = (Timer) field(ui.panel, "presentationTimer");
        for (var listener : timer.getActionListeners()) listener.actionPerformed(new ActionEvent(timer, 0, "elapsed"));
    }
    @ParameterizedTest @EnumSource(PlayerColor.class)
    void rapidOpponentMoveSkillEndPreservesEachVisualAndDrawOrigin(PlayerColor actor) throws Exception {
        var fixture = fixture(actor); var viewer = actor.opponent(); var ref = new Ui[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = ui(fixture, viewer));
        try {
            SwingUtilities.invokeAndWait(() -> {
                var ui = ref[0];
                deliver(ui, fixture.move, viewer);
                var board = (BoardPanel) field(ui.panel, "boardPanel");
                var moving = List.copyOf((List<?>) field(board, "movingPieces"));
                Object started = field(board, "moveAnimStart");
                deliver(ui, fixture.skill, viewer); deliver(ui, fixture.end, viewer);
                assertEquals(RulesEngine.viewFor(fixture.end.nextState(), viewer), ui.model.view(), "model adopts newest state immediately");
                assertEquals(moving, field(board, "movingPieces")); assertEquals(started, field(board, "moveAnimStart"));
                assertEquals(fixture.move.nextState().board().placedPieces(), field(board, "pieces"), "rendered board follows the active visual batch");
                assertEquals(2, ((java.util.Deque<?>) field(ui.panel, "presentationQueue")).size());
                assertFalse(((CardPlayOverlay) field(ui.panel, "playOverlay")).busy(), "skill waits for movement and draw");
                assertNull(((PlayedCardArea) field(ui.panel, "enemyPlay")).getToolTipText());
                // Clicking while watching cannot truncate the active move or submit an action.
                board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_CLICKED, 1, 0, 100, 100, 1, false, MouseEvent.BUTTON1));
                assertEquals(moving, field(board, "movingPieces"));
            });
            SwingUtilities.invokeAndWait(() -> {
                var ui = ref[0];
                @SuppressWarnings("unchecked") var flights = (List<DeckDrawOverlay.Flight>) field(ui.draws, "flights");
                assertEquals(1, flights.size(), "newer authoritative revisions must not suppress the queued draw");
                var deck = (DeckStackPanel) field(ui.panel, "opponentDrawPile");
                assertEquals(SwingUtilities.convertRectangle(deck, deck.topBounds(), ui.draws), flights.get(0).source().get());
                ui.clock.set(500_000_000); ui.draws.tick(); assertTrue(ui.draws.isFlyingTo(flights.get(0).key()));
                advance(ui);
                assertTrue(((CardPlayOverlay) field(ui.panel, "playOverlay")).busy());
                assertEquals(fixture.skill.nextState().board().placedPieces(), field(field(ui.panel, "boardPanel"), "pieces"));
            });
            SwingUtilities.invokeAndWait(() -> {
                var ui = ref[0];
                @SuppressWarnings("unchecked") var flights = (List<DeckDrawOverlay.Flight>) field(ui.draws, "flights");
                assertEquals(1, flights.size()); assertNull(flights.get(0).face(), "enemy skill draw never reveals hidden identity");
                advance(ui);
                assertNull(field(ui.panel, "activePresentation"));
                assertTrue(((java.util.Deque<?>) field(ui.panel, "presentationQueue")).isEmpty());
                assertFalse(((Timer) field(ui.panel, "presentationTimer")).isRunning());
                assertNotNull(((PlayedCardArea) field(ui.panel, "enemyPlay")).getToolTipText());
                assertEquals(ui.model.view().pieces(), field(field(ui.panel, "boardPanel"), "pieces"));
            });
        } finally { SwingUtilities.invokeAndWait(() -> ref[0].panel.removeNotify()); }
    }

    @ParameterizedTest @ValueSource(strings = {"reduced", "pause", "reconnect", "resize", "dispose"})
    void queuedWorkCannotSurviveReset(String reason) throws Exception {
        var f = fixture(PlayerColor.RED); var ref = new Ui[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = ui(f, PlayerColor.BLUE));
        try {
            SwingUtilities.invokeAndWait(() -> {
                var ui = ref[0]; deliver(ui, f.move, PlayerColor.BLUE); deliver(ui, f.skill, PlayerColor.BLUE);
                switch (reason) {
                    case "reduced" -> ui.model.setReducedMotion(true);
                    case "pause" -> ui.model.adoptSync(new MatchSync("sequence", "ROOM", ui.model.view(), true, 1));
                    case "reconnect" -> ui.model.handleMatchSync(new MatchSync("sequence", "ROOM", ui.model.view(), false, 1));
                    case "resize" -> ui.panel.setSize(1280, 720);
                    case "dispose" -> ui.panel.removeNotify();
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                var ui = ref[0];
                assertNull(field(ui.panel, "activePresentation"));
                assertTrue(((java.util.Deque<?>) field(ui.panel, "presentationQueue")).isEmpty());
                assertFalse(((Timer) field(ui.panel, "presentationTimer")).isRunning());
                assertTrue(((List<?>) field(ui.draws, "flights")).isEmpty());
            });
        } finally { SwingUtilities.invokeAndWait(() -> ref[0].panel.removeNotify()); }
    }
}
