package onitama.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import onitama.client.state.ClientModel;
import onitama.client.state.ServerConnection;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Desktop checks for the committed choice dialog and its terminal lifecycle. */
class ForesightDialogTest {
    private JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton b && text.equalsIgnoreCase(b.getText())) return b;
            if (child instanceof Container c) {
                JButton found = button(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void choosingDoesNotBlockResignOrCloseBeforeAcknowledgement() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a desktop display");
        SwingUtilities.invokeAndWait(() -> {
            var rules = RulesConfigLoader.load(null);
            var submitted = new ArrayList<List<String>>();
            var resigned = new AtomicBoolean();
            ForesightDialog dialog = new ForesightDialog(null,
                    List.of(new CardInstance("blink#1", "blink"), new CardInstance("dash#1", "dash")),
                    rules, PlayerColor.BLUE, submitted::add, () -> resigned.set(true));
            try {
                dialog.setVisible(true);
                assertFalse(dialog.isModal());
                assertEquals(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE, dialog.getDefaultCloseOperation());
                assertNotNull(button(dialog, "Right"), "reorder controls need readable labels with the bundled font");
                button(dialog, "Left").doClick();
                button(dialog, "Confirm Deck Order").doClick();
                assertEquals(List.of(List.of("dash#1", "blink#1")), submitted);
                assertTrue(dialog.isDisplayable(), "submission must wait for the server outcome");
                dialog.setActionsEnabled(false, true);
                assertFalse(button(dialog, "Confirm Deck Order").isEnabled());
                button(dialog, "Surrender").doClick();
                assertTrue(resigned.get(), "paused choices must still allow resignation");
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void panelClosesChoiceOnTerminalViewAndConnectionLoss() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a desktop display");
        var rules = RulesConfigLoader.load(null);
        GameState game = null;
        for (long seed = 0; seed < 1000; seed++) {
            var candidate = RulesEngine.newGame(rules, seed);
            if (candidate.turn() == PlayerColor.BLUE && candidate.blueSkillHand().stream()
                    .anyMatch(c -> c.definitionId().equals("foresight"))) { game = candidate; break; }
        }
        assertNotNull(game);
        var skip = (ActionResult.Accepted) RulesEngine.apply(game, PlayerColor.BLUE, new GameAction.SkipMovement());
        String id = skip.nextState().blueSkillHand().stream().filter(c -> c.definitionId().equals("foresight"))
                .findFirst().orElseThrow().instanceId();
        var cast = (ActionResult.Accepted) RulesEngine.apply(skip.nextState(), PlayerColor.BLUE,
                new GameAction.CastSkill(id, new SkillTargets.None()));
        var terminal = (ActionResult.Accepted) RulesEngine.apply(cast.nextState(), PlayerColor.RED, new GameAction.Resign());
        var choice = new MatchSync("choice", "ROOM", RulesEngine.viewFor(cast.nextState(), PlayerColor.BLUE), false, 1);
        var ended = new MatchSync("choice", "ROOM", RulesEngine.viewFor(terminal.nextState(), PlayerColor.BLUE), false, 1);
        ClientModel model = new ClientModel(new ServerConnection() {
            public void send(Message message) { }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        JFrame[] frame = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new JFrame("Foresight regression check");
                frame[0].add(new GamePanel(model));
                frame[0].setSize(1100, 700);
                frame[0].setVisible(true);
                model.handleMatchSync(choice);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasVisibleChoice(frame[0]));
                model.resolveForesight(choice.view().ownChoice().preview().stream()
                        .map(CardInstance::instanceId).toList());
                assertFalse(button(frame[0], "Surrender").isEnabled(), "one in-flight action gates resignation");
                model.handleActionReply(new ActionReply("choice", 1,
                        new ActionReceipt(false, choice.view().revision(), ActionError.INVALID_CHOICE),
                        new MatchSync("choice", "ROOM", choice.view(), false, 2)));
                assertTrue(button(frame[0], "Surrender").isEnabled(), "a rejection must restore resignation");
                model.handleGameUpdate(new GameUpdate(ended, terminal.events()));
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(hasVisibleChoice(frame[0]), "terminal view must dispose the preview");
                model.handleMatchSync(new MatchSync("next", "ROOM", choice.view(), false, 1));
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasVisibleChoice(frame[0]));
                model.handleConnectionLost();
                assertFalse(hasVisibleChoice(frame[0]));
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame[0] != null) {
                    for (Window child : frame[0].getOwnedWindows()) child.dispose();
                    frame[0].dispose();
                }
            });
        }
    }

    private boolean hasVisibleChoice(JFrame frame) {
        for (Window child : frame.getOwnedWindows()) {
            if (child instanceof ForesightDialog && child.isVisible()) return true;
        }
        return false;
    }
}
