package deckduel.client.ui;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.duel.*;
import deckduel.net.MatchSync;
import deckduel.net.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Targeting instructions must agree with the engine's legal selection and cast readiness. */
class TargetingPresentationTest {
    @ParameterizedTest
    @CsvSource({"dash,BLUE", "dash,RED", "scatter,BLUE", "scatter,RED"})
    void destinationAndCompleteSelectionHaveAccurateInstructions(String skill, PlayerColor viewer) throws Exception {
        var view = skillPhase(skill, viewer);
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new GamePanel(model);
            try {
                model.handleMatchSync(new MatchSync("targeting", "ROOM", view, false, 1));
                String id = view.ownSkillHand().stream().filter(c -> c.definitionId().equals(skill))
                        .findFirst().orElseThrow().instanceId();
                model.skillClicked(id);
                BoardPanel instruction = field(panel, "boardPanel", BoardPanel.class);
                JButton cast = field(panel, "castSkillButton", JButton.class);
                assertTrue(instruction.getToolTipText().contains("choose a highlighted piece"));
                assertFalse(cast.isEnabled());

                int pieceId = model.skillOptions().selectablePieceIds().get(0);
                model.boardClicked(view.pieces().stream().filter(p -> p.piece().id() == pieceId)
                        .findFirst().orElseThrow().square());
                assertTrue(instruction.getToolTipText().contains("destination square"), instruction.getToolTipText());
                assertFalse(cast.isEnabled());

                model.boardClicked(model.skillOptions().selectableSquares().get(0));
                assertTrue(model.canCastSkill());
                assertTrue(cast.isEnabled());
                assertTrue(instruction.getToolTipText().startsWith("Ready to cast"), instruction.getToolTipText());
                assertEquals(instruction.getToolTipText(), cast.getToolTipText());
                if (skill.equals("scatter")) {
                    assertTrue(instruction.getToolTipText().contains("More targets are optional"));
                    int another = model.skillOptions().selectablePieceIds().stream()
                            .filter(p -> !model.skillDraft().pieceIds().contains(p)).findFirst().orElseThrow();
                    model.boardClicked(view.pieces().stream().filter(p -> p.piece().id() == another)
                            .findFirst().orElseThrow().square());
                    assertFalse(cast.isEnabled());
                    assertTrue(instruction.getToolTipText().contains("destination square"));
                    model.boardClicked(model.skillOptions().selectableSquares().get(0));
                    assertTrue(cast.isEnabled());
                    assertTrue(instruction.getToolTipText().startsWith("Ready to cast"));
                    assertFalse(instruction.getToolTipText().contains("More targets are optional"));
                }
                model.handleMatchSync(new MatchSync("targeting", "ROOM", view, true, 1));
                assertFalse(cast.isEnabled());
                assertTrue(instruction.getToolTipText().startsWith("Match paused"));
            } finally {
                panel.removeNotify();
            }
        });
    }

    private PlayerView skillPhase(String skill, PlayerColor viewer) {
        var rules = RulesConfigLoader.load(null);
        for (long seed = 0; seed < 1000; seed++) {
            var game = RulesEngine.newGame(rules, seed);
            if (game.turn() != viewer || RulesEngine.viewFor(game, viewer).ownSkillHand().stream()
                    .noneMatch(c -> c.definitionId().equals(skill))) continue;
            var skip = (ActionResult.Accepted) RulesEngine.apply(game, viewer, new GameAction.SkipMovement());
            return RulesEngine.viewFor(skip.nextState(), viewer);
        }
        throw new AssertionError("No fixture for " + skill + "/" + viewer);
    }

    private <T> T field(GamePanel panel, String name, Class<T> type) {
        try {
            var field = GamePanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(panel));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
