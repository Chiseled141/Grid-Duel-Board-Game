package onitama.client.ui;

import java.awt.Point;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import onitama.config.RulesConfigLoader;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.Offset;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The reskin must preserve actual movement, live details and retired-media cleanup. */
class MedievalPresentationTest {
    @Test void movementDiagramsMatchTheBoardForEveryViewerAndActor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rules = RulesConfigLoader.load(null);
            var game = RulesEngine.newGame(rules, 42);
            for (PlayerColor viewer : PlayerColor.values()) {
                var board = new BoardPanel(); board.setView(RulesEngine.viewFor(game, viewer));
                for (PlayerColor actor : PlayerColor.values()) for (Card card : rules.movementCards()) {
                    int radius = radius(card);
                    Set<Point> expected = new java.util.HashSet<>();
                    for (Offset offset : card.offsets()) {
                        int dx = actor == PlayerColor.BLUE ? offset.dx() : -offset.dx();
                        int dy = actor == PlayerColor.BLUE ? offset.dy() : -offset.dy();
                        expected.add(new Point(radius + board.displayX(3 + dx) - board.displayX(3),
                                radius + board.displayY(3 + dy) - board.displayY(3)));
                    }
                    assertDiagram(card, actor, viewer, expected);
                }
            }
            assertEquals("Lance", CardArt.nameFor(CardDeck.cardById("tiger")));
        });
    }

    @Test void customOffsetsWithTheSameIdCannotReuseAStaleFace() {
        var original = CardDeck.cardById("tiger");
        var changed = new Card("tiger", "Tiger", original.stamp(), List.of(new Offset(1, 0)));
        assertNotSame(CardArt.movementFace(original, PlayerColor.BLUE), CardArt.movementFace(changed, PlayerColor.BLUE));
        assertDiagram(changed, PlayerColor.BLUE, PlayerColor.BLUE, Set.of(new Point(3, 2)));
    }

    private void assertDiagram(Card card, PlayerColor color, PlayerColor viewer, Set<Point> destinations) {
        var face = CardArt.movementFace(card, color, viewer);
        int radius = radius(card), size = radius * 2 + 1, cell = 125 / size;
        int left = (200 - size * cell) / 2;
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) {
            if (row == radius && col == radius) continue;
            int pixel = face.getRGB(2 * (left + col * cell + 4), 2 * (79 + row * cell + 4));
            assertEquals(destinations.contains(new Point(col, row)), pixel == Theme.playerTextColor(color).getRGB(),
                    "landing at " + col + "," + row + " for " + color);
        }
    }

    private int radius(Card card) {
        return Math.max(2, card.offsets().stream()
                .mapToInt(offset -> Math.max(Math.abs(offset.dx()), Math.abs(offset.dy()))).max().orElseThrow());
    }

    @Test void ruleDescriptionsStayOffTheFaceButRemainAvailableForInspection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var base = RulesConfigLoader.load(null);
            var first = base.skills().get(0);
            var changed = new SkillDefinition(first.id(), first.name(), first.category(), first.manaCost(), first.copies(),
                    first.parameters(), "A distinctive updated live rule.", first.artworkPath());
            var before = CardArt.skillFace(first); var after = CardArt.skillFace(changed);
            assertArrayEquals(before.getRGB(0, 0, 400, 480, null, 0, 400),
                    after.getRGB(0, 0, 400, 480, null, 0, 400), "rule text must not be printed on the art");
            var skills = base.skills().stream().map(skill -> skill.id().equals(first.id()) ? changed : skill).toList();
            var rules = new RulesConfig(base.rulesVersion(), 0, 3, 2, 1, 5, 60, base.movementCards(), skills);
            var panel = new SkillCardPanel(new CardInstance(first.id() + "#1", first.id()), rules,
                    PlayerColor.BLUE, 5, TurnPhase.SKILL, true, false, null);
            assertTrue(panel.getToolTipText().contains("A distinctive updated live rule."));
            assertTrue(java.util.Arrays.stream(panel.getMouseListeners()).anyMatch(CardDetails.class::isInstance));
        });
    }

    @Test void onlyTheMedievalRuntimeArtworkAndMusicRemain() {
        for (String retired : List.of("menu-valley.gif", "cinematic-valley.png", "cards/blue/tiger-blue.png",
                "skills/sweep.png", "skills/card-back.png", "piece-student-blue.png"))
            assertNull(getClass().getResource("/assets/" + retired), retired);
        assertNull(getClass().getResource("/audio/menu-music.wav"));
        assertNotNull(getClass().getResource("/audio/golden-fields.wav"));
        for (var skill : RulesConfigLoader.load(null).skills()) {
            assertTrue(skill.artworkPath().startsWith("assets/medieval/skills/"));
            assertNotNull(getClass().getResource("/" + skill.artworkPath()));
        }
    }
}
