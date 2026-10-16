package onitama.client.ui;

import onitama.client.state.*;
import onitama.config.RulesConfigLoader;
import onitama.core.duel.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayedCardsPanelTest {
    @Test
    void retainsPublicHistoryAndClearsOnNewMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { fail("history never sends network actions"); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
            var viewer = game.turn();
            var first = RulesEngine.project(game, viewer);
            model.handleMatchSync(new MatchSync("timeline", "ROOM", first, false, 1));
            assertEquals(0, model.playedCards().size());
            for (int turn = 0; turn < 2; turn++) {
                var move = Targeting.legalMoves(RulesEngine.project(game, game.turn())).get(0);
                var accepted = (ActionResult.Accepted) RulesEngine.apply(game, game.turn(), move);
                game = accepted.nextState();
                model.handleGameUpdate(new GameUpdate(new MatchSync("timeline", "ROOM",
                        RulesEngine.project(game, viewer), false, 1), accepted.events()));
                accepted = (ActionResult.Accepted) RulesEngine.apply(game, game.turn(), new GameAction.EndTurn());
                game = accepted.nextState();
                model.handleGameUpdate(new GameUpdate(new MatchSync("timeline", "ROOM",
                        RulesEngine.project(game, viewer), false, 1), accepted.events()));
            }
            assertEquals(2, model.playedCards().size());
            assertEquals(1, model.playedCards().get(0).turn());
            assertEquals(2, model.playedCards().get(1).turn());
            model.handleMatchSync(new MatchSync("rematch", "ROOM", first, false, 1));
            assertEquals(0, model.playedCards().size());
        });
    }
}
