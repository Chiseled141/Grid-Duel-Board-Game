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

/** Card usability, targeting and privacy remain intact after removing the details panel. */
class MatchPresentationTest {
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

    @Test void cardsKeepFullLiveTooltipsWithoutASeparateDetailsPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(initial());
            try {
                var card = find(ui.panel, SkillCardPanel.class).get(0);
                hover(card, true);
                assertTrue(card.getToolTipText().contains(SkillText.render(ui.model.view().rules().skill(card.instance().definitionId()))));
                assertTrue(find(ui.panel, javax.swing.JButton.class).stream().noneMatch(b -> b.getText().equalsIgnoreCase("Card details")));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void publicOpponentMovementAndHiddenSkillPrivacyRemain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(initial());
            try {
                var opponent = field(ui.panel, "opponentCards", javax.swing.JPanel.class);
                assertEquals(2, find(opponent, CardPanel.class).size());
                assertEquals(3, find(opponent, CardBackPanel.class).size());
                for (var back : find(opponent, CardBackPanel.class)) assertNull(back.getToolTipText());
                assertEquals(3, find(ui.panel, SkillCardPanel.class).size());
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void castReadinessRemainsAvailableOnTheActionTooltip() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = skillPhase("dash"); var ui = ui(view);
            try {
                ui.model.skillClicked(view.ownSkillHand().stream().filter(c -> c.definitionId().equals("dash")).findFirst().orElseThrow().instanceId());
                int piece = ui.model.skillOptions().selectablePieceIds().get(0);
                ui.model.boardClicked(view.pieces().stream().filter(p -> p.piece().id() == piece).findFirst().orElseThrow().square());
                ui.model.boardClicked(ui.model.skillOptions().selectableSquares().get(0));
                assertTrue(ui.model.canCastSkill());
                assertTrue(field(ui.panel, "castSkillButton", javax.swing.JButton.class).getToolTipText().contains("Ready to cast"));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void cardsUseLiveBalanceAfterDetailsRemoval() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var base = RulesConfigLoader.load(null);
            var skills = base.skills().stream().map(d -> new SkillDefinition(d.id(), d.name(), d.category(), 4,
                    d.copies(), d.parameters(), d.descriptionTemplate(), d.artworkPath())).toList();
            var rules = new RulesConfig(base.rulesVersion(), 2, 3, 2, 1, 5, 60, base.movementCards(), skills);
            var game = RulesEngine.newGame(rules, 42); var ui = ui(RulesEngine.viewFor(game, game.turn()));
            try { for (var card : find(ui.panel, SkillCardPanel.class)) assertTrue(card.getToolTipText().contains("4 Mana")); }
            finally { ui.panel.removeNotify(); }
        });
    }

    @Test void removedScreenBannersDoNotReturnOnRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(initial());
            try {
                ui.model.clearSelection();
                for (var label : find(ui.panel, JLabel.class)) {
                    String text = label.getText().toUpperCase();
                    assertFalse(text.startsWith("VS ") || text.contains("YOUR HAND") || text.contains("CHOOSE A MOVEMENT CARD"));
                }
            } finally { ui.panel.removeNotify(); }
        });
    }

    @Test void directionControlsAndPauseStillConstrainCasting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = skillPhase("gust"); var ui = ui(view);
            try {
                ui.model.skillClicked(view.ownSkillHand().stream().filter(c -> c.definitionId().equals("gust")).findFirst().orElseThrow().instanceId());
                assertTrue(field(ui.panel, "directionPanel", javax.swing.JPanel.class).isVisible());
                ui.model.handleMatchSync(new MatchSync("inspect", "ROOM", view, true, 1));
                assertFalse(field(ui.panel, "castSkillButton", javax.swing.JButton.class).isEnabled());
            } finally { ui.panel.removeNotify(); }
        });
    }
}
