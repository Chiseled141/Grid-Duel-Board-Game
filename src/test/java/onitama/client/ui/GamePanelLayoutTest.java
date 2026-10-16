package onitama.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import onitama.client.state.ClientModel;
import onitama.client.state.ServerConnection;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import onitama.net.MatchSync;
import onitama.net.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The board and complete private/public hand must fit without leaking opponent skills. */
class GamePanelLayoutTest {
    private void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }

    private <T> List<T> find(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container c) result.addAll(find(c, type));
        }
        return result;
    }

    @ParameterizedTest
    @CsvSource({"1280,660,3,BLUE", "1440,820,3,RED", "1280,660,6,RED", "1440,820,6,BLUE", "1960,1180,3,BLUE"})
    void boardAndUnifiedHandFitAtSupportedWindowSizes(int width, int height, int handSize, PlayerColor viewer) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RulesConfig r = RulesConfigLoader.load(null);
            RulesConfig rules = new RulesConfig(r.rulesVersion(), 2, handSize, r.initialMana(),
                    r.manaPerTurn(), r.manaCap(), r.turnLimit(), r.movementCards(), r.skills());
            var view = RulesEngine.viewFor(RulesEngine.newGame(rules, 42), viewer);
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new GamePanel(model);
            model.handleMatchSync(new MatchSync("layout", "ROOM", view, false, 1));
            panel.setSize(width, height);
            layout(panel);
            CardHandPanel hand = find(panel, CardHandPanel.class).get(0);
            assertEquals(2, find(hand, CardPanel.class).size(), "both movement cards belong in bottom hand");
            assertEquals(handSize, find(hand, SkillCardPanel.class).size());
            assertEquals(handSize + 2, hand.getComponentCount());
            assertTrue(SwingUtilities.convertPoint(hand, 0, 0, panel).y > height / 2);
            for (Component card : hand.getComponents()) {
                assertTrue(card.getX() >= 0 && card.getY() >= 0);
                assertTrue(card.getX() + card.getWidth() <= hand.getWidth());
                assertTrue(card.getY() + card.getHeight() <= hand.getHeight());
            }
            BoardPanel board = find(panel, BoardPanel.class).get(0);
            board.computeGeometry();
            assertTrue(board.cell() >= 24, "board should stay usable at minimum window size");
            var boardPosition = SwingUtilities.convertPoint(board, 0, 0, panel);
            for (JButton button : find(panel, JButton.class)) {
                if (!List.of("SKIP MOVEMENT", "END TURN").contains(button.getText())) continue;
                assertInside(panel, button, width, height);
                var position = SwingUtilities.convertPoint(button, 0, 0, panel);
                assertTrue(position.x + button.getWidth() <= boardPosition.x,
                        "turn buttons must occupy the left rail, outside the board");
            }
            if (height >= 1180) assertTrue(board.cell() >= 95,
                    "large windows must use their freed vertical space for a larger board");
            assertTrue(board.originX() >= 0 && board.originY() >= 0);
            assertTrue(board.originX() + 7 * board.cell() <= board.getWidth());
            assertTrue(board.originY() + 7 * board.cell() <= board.getHeight());
            assertEquals(view.opponentSkillCount(), find(panel, CardBackPanel.class).size());
            assertEquals(2, find(panel, DeckStackPanel.class).size());
            assertEquals(2, find(panel, PlayerBadgePanel.class).size());
            for (Component item : find(panel, DeckStackPanel.class)) assertInside(panel, item, width, height);
            for (Component item : find(panel, PlayerBadgePanel.class)) assertInside(panel, item, width, height);
            assertEquals(handSize, find(panel, SkillCardPanel.class).size(), "opponent skill faces must never be constructed");
            for (CardBackPanel back : find(panel, CardBackPanel.class)) assertNull(back.getToolTipText());
        });
    }

    private void assertInside(GamePanel parent, Component child, int width, int height) {
        var point = SwingUtilities.convertPoint(child, 0, 0, parent);
        assertTrue(child.getWidth() > 0 && child.getHeight() > 0);
        assertTrue(point.x >= 0 && point.y >= 0);
        assertTrue(point.x + child.getWidth() <= width && point.y + child.getHeight() <= height);
    }

    @Test
    void smallBoardNeverDrawsOutsideItsActualBounds() {
        BoardPanel board = new BoardPanel();
        board.setSize(new Dimension(240, 210));
        board.computeGeometry();
        assertTrue(board.originX() >= 0 && board.originY() >= 0);
        assertTrue(board.originY() + 7 * board.cell() <= 210);
        assertTrue(board.originX() + 7 * board.cell() <= 240);
    }

    @Test
    void allSkillIllustrationsAndSharedBackAreBundled() {
        for (var skill : RulesConfigLoader.load(null).skills()) {
            assertNotNull(getClass().getResource("/" + skill.artworkPath()), skill.id());
        }
        assertNotNull(getClass().getResource("/assets/skills/card-back.png"));
    }
}
