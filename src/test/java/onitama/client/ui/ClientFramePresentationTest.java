package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ServerConnection;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for migrated menu actions and current-rule help. */
class ClientFramePresentationTest {
    @Test void resignUsesLiveViewAndRejectsDuplicateOrFinishedRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            assertFalse(ClientFrame.canMenuResign(model));
            var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
            model.handleMatchSync(new MatchSync("menu", "ROOM", RulesEngine.viewFor(game, PlayerColor.BLUE), false, 1));
            assertTrue(ClientFrame.canMenuResign(model));
            model.resign();
            assertNotNull(model.inFlightRequest());
            assertFalse(ClientFrame.canMenuResign(model));
            var result = (ActionResult.Accepted) RulesEngine.apply(game, PlayerColor.BLUE, new GameAction.Resign());
            model.handleMatchSync(new MatchSync("menu", "ROOM", RulesEngine.viewFor(result.nextState(), PlayerColor.BLUE), false, 2));
            assertFalse(ClientFrame.canMenuResign(model));
        });
    }

    @Test void resignRemainsAvailableDuringPausedForesightChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var choice = DeckPresentationFixtures.skill("foresight", PlayerColor.BLUE, 18);
            var view = choice.after(PlayerColor.BLUE);
            assertEquals(TurnPhase.CHOICE, view.phase());
            model.handleMatchSync(new MatchSync("choice", "ROOM", view, true, 1));
            assertTrue(ClientFrame.canMenuResign(model));
        });
    }

    @Test void helpUsesMatchBalanceAndExplainsDeckPrivacy() {
        var base = RulesConfigLoader.load(null);
        var custom = new RulesConfig(base.rulesVersion(), 2, 4, 3, 2, 7, 80,
                base.movementCards(), base.skills());
        var help = ClientFrame.helpTabText("How to Play", custom);
        assertTrue(help.contains("80 completed player turns"));
        assertTrue(help.contains("Start with 3 mana. Gain 2"));
        assertTrue(help.contains("up to 7"));
        var cards = ClientFrame.helpTabText("Cards", custom);
        assertTrue(cards.contains("4 skill cards"));
        assertTrue(cards.contains("opponent skills stay hidden"));
        assertTrue(cards.contains("spent permanently"));
        assertTrue(ClientFrame.helpTabText("How to Play", null).contains("60 completed player turns"));
    }
}
