package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.duel.*;
import deckduel.net.*;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for migrated menu actions and current-rule help. */
class ClientFramePresentationTest {
    private javax.swing.JButton surrender(java.awt.Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof javax.swing.JButton button && button.getText().equals("SURRENDER")) return button;
            if (child instanceof java.awt.Container container) {
                var found = surrender(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void windowHasNoMenuStripAndMusicFloatsOverTheScene() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame = new ClientFrame(null, null);
            try {
                assertEquals("Deck Duel", frame.getTitle());
                frame.setVisible(true); frame.validate();
                assertNull(frame.getJMenuBar());
                var scene = (javax.swing.JLayeredPane) frame.getContentPane().getComponent(0);
                var content = scene.getComponentsInLayer(javax.swing.JLayeredPane.DEFAULT_LAYER)[0];
                assertEquals(new java.awt.Rectangle(0, 0, scene.getWidth(), scene.getHeight()), content.getBounds());
                var sound = (javax.swing.JToggleButton) scene.getComponentsInLayer(javax.swing.JLayeredPane.PALETTE_LAYER)[0];
                assertEquals("menuMusicToggle", sound.getName());
                assertFalse(sound.isOpaque()); assertFalse(sound.isContentAreaFilled());
                assertTrue(sound.getY() >= 0 && sound.getX() + sound.getWidth() <= scene.getWidth());
                assertTrue(sound.getToolTipText().startsWith("Menu music:"));
            } finally { frame.dispose(); }
        });
    }

    @Test void resignUsesLiveViewAndRejectsDuplicateOrFinishedRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new GamePanel(model);
            assertFalse(surrender(panel).isEnabled());
            var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
            model.handleMatchSync(new MatchSync("menu", "ROOM", RulesEngine.viewFor(game, PlayerColor.BLUE), false, 1));
            assertTrue(surrender(panel).isEnabled());
            model.resign();
            assertNotNull(model.inFlightRequest());
            assertFalse(surrender(panel).isEnabled());
            var result = (ActionResult.Accepted) RulesEngine.apply(game, PlayerColor.BLUE, new GameAction.Resign());
            model.handleMatchSync(new MatchSync("menu", "ROOM", RulesEngine.viewFor(result.nextState(), PlayerColor.BLUE), false, 2));
            assertFalse(surrender(panel).isEnabled());
        });
    }

    @Test void resignRemainsAvailableDuringPausedForesightChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new GamePanel(model);
            var choice = DeckPresentationFixtures.skill("foresight", PlayerColor.BLUE, 18);
            var view = choice.after(PlayerColor.BLUE);
            assertEquals(TurnPhase.CHOICE, view.phase());
            model.handleMatchSync(new MatchSync("choice", "ROOM", view, true, 1));
            assertTrue(surrender(panel).isEnabled());
        });
    }

    @Test void helpUsesMatchBalanceAndExplainsDeckPrivacy() {
        var base = RulesConfigLoader.load(null);
        var custom = new RulesConfig(base.rulesVersion(), 0, 4, 3, 2, 7, 80,
                base.movementCards(), base.skills());
        var help = ClientFrame.helpTabText("How to Play", custom);
        assertTrue(help.contains("80 completed player turns"));
        assertTrue(help.contains("Start with 3 mana. Gain 2"));
        assertTrue(help.contains("up to 7"));
        var cards = ClientFrame.helpTabText("Cards", custom);
        assertTrue(cards.contains("4 skill cards"));
        assertTrue(cards.toLowerCase().contains("opponent skills stay hidden"));
        assertTrue(cards.contains("spent permanently"));
        assertTrue(ClientFrame.helpTabText("How to Play", null).contains("60 completed player turns"));
    }
}
