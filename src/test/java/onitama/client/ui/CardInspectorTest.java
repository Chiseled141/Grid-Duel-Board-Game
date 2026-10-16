package onitama.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import onitama.client.state.ClientModel;
import onitama.client.state.ServerConnection;
import onitama.config.RulesConfigLoader;
import onitama.config.SkillText;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The inspector must stay read-only, balance-aware and confined to permitted information. */
class CardInspectorTest {
    private record Ui(ClientModel model, GamePanel panel, List<Message> sent) { }

    private Ui ui(PlayerView view) {
        var sent = new ArrayList<Message>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        var panel = new GamePanel(model);
        model.handleMatchSync(new MatchSync("inspect", "ROOM", view, false, 1));
        panel.setSize(1280, 660);
        layout(panel);
        return new Ui(model, panel, sent);
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static <T> List<T> find(Container c, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(find(container, type));
        }
        return result;
    }

    private static <T> T field(Object o, String name, Class<T> type) {
        try {
            var field = o.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(o));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static CardInspectorPanel inspector(Ui ui) {
        return find(ui.panel, CardInspectorPanel.class).get(0);
    }

    private static String title(Ui ui) { return field(inspector(ui), "title", JLabel.class).getText(); }
    private static String description(Ui ui) { return field(inspector(ui), "description", JTextArea.class).getText(); }
    private static String guidance(Ui ui) { return field(inspector(ui), "guidance", JTextArea.class).getText(); }
    private static void hover(Component component, boolean entered) {
        component.dispatchEvent(new MouseEvent(component, entered ? MouseEvent.MOUSE_ENTERED : MouseEvent.MOUSE_EXITED,
                1, 0, 1, 1, 0, false));
    }

    private static PlayerView initial() {
        var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
        return RulesEngine.viewFor(game, game.turn());
    }

    private static PlayerView skillPhase(String skill) {
        var rules = RulesConfigLoader.load(null);
        for (int seed = 0; seed < 1000; seed++) {
            var game = RulesEngine.newGame(rules, seed);
            if (RulesEngine.viewFor(game, game.turn()).ownSkillHand().stream()
                    .noneMatch(c -> c.definitionId().equals(skill))) continue;
            game = ((ActionResult.Accepted) RulesEngine.apply(game, game.turn(), new GameAction.SkipMovement())).nextState();
            return RulesEngine.viewFor(game, game.turn());
        }
        throw new AssertionError("No skill fixture");
    }

    @Test void hoveredCardStaysReadableUntilSelectionChangesWithoutSendingAnAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = initial(); var ui = ui(view);
            try {
                assertEquals("YOUR NEXT ACTION", title(ui));
                String selected = view.viewer() == PlayerColor.BLUE ? view.blueMovementHand().get(0) : view.redMovementHand().get(0);
                ui.model.cardClicked(selected);
                var expected = view.rules().movementCards().stream().filter(c -> c.id().equals(selected)).findFirst().orElseThrow();
                assertEquals(expected.name().toUpperCase(), title(ui));
                var skill = find(ui.panel, SkillCardPanel.class).get(0);
                hover(skill, true);
                var def = view.rules().skill(skill.instance().definitionId());
                assertEquals(def.name().toUpperCase(), title(ui));
                assertTrue(description(ui).contains(SkillText.render(def)));
                assertEquals(selected, ui.model.selectedCardId());
                hover(skill, false);
                hover(find(inspector(ui), JScrollPane.class).get(0), true);
                assertEquals(def.name().toUpperCase(), title(ui), "preview must survive moving to its scrollbar");
                ui.model.cardClicked(selected);
                ui.model.cardClicked(selected);
                assertEquals(expected.name().toUpperCase(), title(ui));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void publicOpponentMovementCanBeInspectedButHiddenSkillsCannot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(initial());
            try {
                var opponent = field(ui.panel, "opponentCards", javax.swing.JPanel.class);
                var move = find(opponent, CardPanel.class).get(0);
                hover(move, true);
                assertEquals(move.card().name().toUpperCase(), title(ui));
                hover(move, false);
                String before = description(ui);
                for (var back : find(ui.panel, CardBackPanel.class)) { hover(back, true); hover(back, false); }
                assertEquals(move.card().name().toUpperCase(), title(ui), "public card remains available for scrolling");
                assertEquals(before, description(ui));
                assertEquals(3, find(ui.panel, SkillCardPanel.class).size(), "inspector renders snapshots, not extra hand cards");
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void targetProgressAndCastReadinessFollowTheLiveDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = skillPhase("dash"); var ui = ui(view);
            try {
                String id = view.ownSkillHand().stream().filter(c -> c.definitionId().equals("dash")).findFirst().orElseThrow().instanceId();
                ui.model.skillClicked(id);
                assertTrue(guidance(ui).contains("0 piece(s)"));
                int piece = ui.model.skillOptions().selectablePieceIds().get(0);
                ui.model.boardClicked(view.pieces().stream().filter(p -> p.piece().id() == piece).findFirst().orElseThrow().square());
                assertTrue(guidance(ui).contains("1 piece(s)"));
                ui.model.boardClicked(ui.model.skillOptions().selectableSquares().get(0));
                assertTrue(guidance(ui).contains("1 destination(s)"));
                assertTrue(guidance(ui).contains("ready to cast"));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void previewUsesLiveBalanceAndResetsAcrossMatchSync() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var base = RulesConfigLoader.load(null);
            var skills = base.skills().stream().map(d -> new SkillDefinition(d.id(), d.name(), d.category(), 4,
                    d.copies(), d.parameters(), d.descriptionTemplate(), d.artworkPath())).toList();
            var rules = new RulesConfig(base.rulesVersion(), 2, 3, 2, 1, 5, 60, base.movementCards(), skills);
            var game = RulesEngine.newGame(rules, 42);
            var view = RulesEngine.viewFor(game, game.turn()); var ui = ui(view);
            try {
                var card = find(ui.panel, SkillCardPanel.class).get(0);
                hover(card, true);
                assertTrue(description(ui).contains("4 MANA"));
                ui.model.handleMatchSync(new MatchSync("another", "NEXT", initial(), false, 1));
                assertEquals("YOUR NEXT ACTION", title(ui));
                assertFalse(description(ui).contains("4 MANA"));
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void spentBrowserShowsOnlyPublicSpentCardsAndCanReturnToSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var transition = DeckPresentationFixtures.skill("mana-surge", PlayerColor.BLUE, 18);
            var view = transition.after(PlayerColor.BLUE); var ui = ui(view);
            try {
                var toggle = find(inspector(ui), JToggleButton.class).get(0);
                toggle.doClick();
                assertEquals("SPENT SKILLS", title(ui));
                assertTrue(description(ui).contains("PUBLIC - BOTH PLAYERS"));
                for (var spent : view.spentSkills()) assertTrue(description(ui).contains(view.rules().skill(spent.definitionId()).name()));
                toggle.doClick();
                assertEquals("YOUR NEXT ACTION", title(ui));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void inspectorStillHasUsableScrollableContentWithDirectionControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = skillPhase("gust"); var ui = ui(view);
            try {
                ui.model.skillClicked(view.ownSkillHand().stream().filter(c -> c.definitionId().equals("gust")).findFirst().orElseThrow().instanceId());
                layout(ui.panel);
                for (var pane : find(inspector(ui), JScrollPane.class)) {
                    assertTrue(pane.getHeight() > 40, "wrapped guidance must not push the card reference out of bounds");
                    assertTrue(pane.getViewport().getHeight() > 20);
                    var content = pane.getViewport().getView();
                    assertTrue(content.getHeight() >= content.getPreferredSize().height,
                            "the full reference text must remain reachable by scrolling");
                }
                assertTrue(inspector(ui).getHeight() > 250);
                assertEquals("GUST", title(ui));
                var steps = field(inspector(ui), "steps", JLabel[].class);
                assertEquals(Theme.AMBER, steps[1].getBackground());
                ui.model.handleMatchSync(new MatchSync("inspect", "ROOM", view, true, 1));
                assertNotEquals(Theme.AMBER, steps[1].getBackground());
            } finally { ui.panel.removeNotify(); }
        });
    }
}
