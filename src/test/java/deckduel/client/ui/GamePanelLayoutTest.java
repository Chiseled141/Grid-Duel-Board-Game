package deckduel.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.duel.RulesConfig;
import deckduel.core.duel.RulesEngine;
import deckduel.net.MatchSync;
import deckduel.net.Message;
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
            RulesConfig rules = new RulesConfig(r.rulesVersion(), 0, handSize, r.initialMana(),
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
            assertEquals(0, find(hand, CardPanel.class).size(), "movement cards are removed");
            assertEquals(handSize, find(hand, SkillCardPanel.class).size());
            assertEquals(handSize, hand.getComponentCount());
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
            var help = find(panel, JButton.class).stream().filter(b -> "matchHowToPlay".equals(b.getName())).findFirst().orElseThrow();
            assertInside(panel, help, width, height);
            var helpPosition = SwingUtilities.convertPoint(help, 0, 0, panel);
            assertTrue(helpPosition.x < boardPosition.x && helpPosition.y <= 20, "help occupies the empty top-left rail");
            for (JButton button : find(panel, JButton.class)) {
                if (!List.of("SKIP MOVEMENT", "END TURN").contains(button.getText())) continue;
                assertInside(panel, button, width, height);
                var position = SwingUtilities.convertPoint(button, 0, 0, panel);
                assertTrue(position.x + button.getWidth() <= boardPosition.x,
                        "turn buttons must occupy the left rail, outside the board");
            }
            if (height >= 1180) {
                assertTrue(board.cell() >= 98, "reclaimed hand spacing enlarges the board in large windows");
                for (Component card : hand.getComponents()) {
                    assertTrue(card.getHeight() >= 250, "large windows expose taller card descriptions");
                    if (handSize == 3) assertTrue(card.getWidth() >= 210, "default hand cards grow wider");
                }
            }
            for (PlayedCardArea area : find(panel, PlayedCardArea.class)) {
                assertInside(panel, area, width, height);
                assertTrue(area.getWidth() >= 130, "latest plays are large enough to inspect");
                var areaPoint = SwingUtilities.convertPoint(area, 0, 0, panel);
                var grid = new java.awt.Rectangle(boardPosition.x + board.originX(),
                        boardPosition.y + board.originY(), 7 * board.cell(), 7 * board.cell());
                assertFalse(grid.intersects(new java.awt.Rectangle(areaPoint.x, areaPoint.y,
                        area.getWidth(), area.getHeight())), "played-card areas stay outside board squares");
            }
            for (CardPanel card : find(panel, CardPanel.class)) {
                if (!SwingUtilities.isDescendingFrom(card, hand)) assertTrue(card.getHeight() >= 110,
                        "public enemy movement cards must be enlarged");
            }
            assertTrue(board.originX() >= 0 && board.originY() >= 0);
            assertTrue(board.originX() + 7 * board.cell() <= board.getWidth());
            assertTrue(board.originY() + 7 * board.cell() <= board.getHeight());
            assertEquals(view.opponentSkillCount(), find(panel, CardBackPanel.class).size());
            assertEquals(2, find(panel, DeckStackPanel.class).size());
            DeckStackPanel pile = find(panel, DeckStackPanel.class).stream().filter(p -> "drawPile".equals(p.getName())).findFirst().orElseThrow();
            var pilePosition = SwingUtilities.convertPoint(pile, 0, 0, panel);
            var enemyDeck = find(panel, DeckStackPanel.class).stream().filter(p -> "opponentDrawPile".equals(p.getName())).findFirst().orElseThrow();
            var enemyBand = enemyDeck.getParent();
            int previousRight = 0;
            boolean movementSeen = false;
            for (var item : enemyBand.getComponents()) {
                assertTrue(item.getX() >= previousRight, "opponent cards stay in one ordered row");
                previousRight = item.getX() + item.getWidth();
                assertTrue(previousRight <= enemyBand.getWidth());
                if (item instanceof CardPanel) movementSeen = true;
                if (item instanceof CardBackPanel) assertFalse(movementSeen, "hidden skills are leftmost");
            }
            assertSame(enemyDeck, enemyBand.getComponent(enemyBand.getComponentCount() - 1));
            assertTrue(enemyDeck.topBounds().height >= 90 && enemyDeck.getHeight() <= 120);
            assertNull(enemyDeck.getToolTipText());
            assertTrue(pilePosition.x + pile.getWidth() <= boardPosition.x, "deck lives in left rail");
            assertTrue(pilePosition.y > height / 2, "deck occupies the lower left");
            assertTrue(pile.topBounds().height >= hand.getComponent(0).getHeight() - 24,
                    "deck grows to approximately hand-card size");
            for (JButton button : find(panel, JButton.class)) {
                if (button.isVisible() && List.of("SKIP MOVEMENT", "CAST SKILL", "END TURN").contains(button.getText())) {
                    var actionPosition = SwingUtilities.convertPoint(button, 0, 0, panel);
                    assertTrue(actionPosition.y + button.getHeight() <= pilePosition.y,
                            "turn actions sit above the deck");
                }
            }
            assertTrue(find(pile.getParent().getParent(), javax.swing.JList.class).isEmpty(), "public timeline panel is removed");
            ChatPanel chat = find(panel, ChatPanel.class).get(0);
            assertInside(panel, chat, width, height);
            var transcript = find(chat, javax.swing.JScrollPane.class).get(0);
            assertTrue(transcript.getViewport().getHeight() >= 30, "chat transcript must remain readable");
            assertInside(panel, find(chat, javax.swing.JTextField.class).get(0), width, height);
            var sendButton = find(chat, JButton.class).stream()
                    .filter(button -> "matchChatSend".equals(button.getName())).findFirst().orElseThrow();
            assertInside(panel, sendButton, width, height);
            assertEquals(2, find(panel, PlayerBadgePanel.class).size());
            for (String label : List.of("MENU", "SURRENDER")) {
                var button = find(panel, JButton.class).stream().filter(b -> label.equals(b.getText())).findFirst().orElseThrow();
                assertTrue(button instanceof UiKit.PillButton);
                assertInside(panel, button, width, height);
                assertTrue(button.getWidth() >= button.getPreferredSize().width, "match navigation labels fit their pills");
            }
            for (Component item : find(panel, DeckStackPanel.class)) assertInside(panel, item, width, height);
            for (Component item : find(panel, PlayerBadgePanel.class)) assertInside(panel, item, width, height);
            var opponent = find(help.getParent(), PlayerBadgePanel.class).get(0);
            var opponentPosition = SwingUtilities.convertPoint(opponent, 0, 0, panel);
            assertTrue(opponentPosition.y >= helpPosition.y + help.getHeight() + 8,
                    "opponent panel sits below How to Play");
            assertTrue(opponentPosition.x + opponent.getWidth() <= boardPosition.x,
                    "opponent panel stays in the left rail");
            assertTrue(opponentPosition.y + opponent.getHeight() <= pilePosition.y);
            assertEquals(handSize, find(panel, SkillCardPanel.class).size(), "opponent skill faces must never be constructed");
            for (CardBackPanel back : find(panel, CardBackPanel.class)) assertNull(back.getToolTipText());
        });
    }

    private void assertInside(GamePanel parent, Component child, int width, int height) {
        var point = SwingUtilities.convertPoint(child, 0, 0, parent);
        assertTrue(child.getWidth() > 0 && child.getHeight() > 0,
                () -> child.getClass().getSimpleName() + "[" + child.getName() + "] has no bounds: "
                        + child.getBounds() + " in " + child.getParent().getBounds());
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
        assertNotNull(CardArt.backFace());
    }
}
