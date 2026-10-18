package onitama.client.ui;

import java.util.List;
import javax.swing.*;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real accepted skill transitions to verify permitted faces and tearing in the match UI. */
class HandControlPresentationTest {
    @ParameterizedTest @EnumSource(PlayerColor.class)
    void fullHandDrawShowsTwoTearsWithFacesOnlyToDrawer(PlayerColor actor) throws Exception {
        var transition = DeckPresentationFixtures.fullHandDraw(actor);
        for (PlayerColor viewer : PlayerColor.values()) {
            var ui = new DeckPresentationTest.Ui[1];
            try {
                SwingUtilities.invokeAndWait(() -> ui[0] = DeckPresentationTest.create(transition.before(viewer)));
                SwingUtilities.invokeAndWait(() -> DeckPresentationTest.deliver(ui[0], transition, viewer));
                SwingUtilities.invokeAndWait(() -> {
                    var flights = DeckPresentationTest.flights(ui[0]);
                    var burns = flights.stream().filter(DeckDrawOverlay.Flight::tear).toList();
                    assertEquals(2, burns.size()); assertEquals(3, flights.size());
                    for (var burn : burns) assertEquals(actor == viewer, burn.face() != null);
                    assertEquals(6, actor == viewer ? ui[0].model().view().ownSkillHand().size()
                            : ui[0].model().view().opponentSkillCount());
                    ui[0].model().setReducedMotion(true);
                    assertTrue(DeckPresentationTest.flights(ui[0]).isEmpty());
                });
            } finally { SwingUtilities.invokeAndWait(() -> { if (ui[0] != null) ui[0].panel().removeNotify(); }); }
        }
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void spyShowsEnemyFacesOnlyForCasterThenHidesThemAtEndTurn(PlayerColor actor) throws Exception {
        var transition = DeckPresentationFixtures.skill("spy", actor, 18);
        var ended = (ActionResult.Accepted) RulesEngine.apply(transition.result().nextState(), actor, new GameAction.EndTurn());
        for (PlayerColor viewer : PlayerColor.values()) {
            SwingUtilities.invokeAndWait(() -> {
                var ui = DeckPresentationTest.create(transition.after(viewer));
                try {
                    var row = (JPanel) DeckPresentationTest.field(ui.panel(), "opponentCards");
                    long faces = java.util.Arrays.stream(row.getComponents()).filter(c -> c instanceof SkillCardPanel).count();
                    assertEquals(actor == viewer ? transition.after(viewer).opponentSkillCount() : 0, faces);
                    ui.model().handleMatchSync(new onitama.net.MatchSync("deck-test", "LOCAL",
                            RulesEngine.viewFor(ended.nextState(), viewer), false, 1));
                    assertEquals(0, java.util.Arrays.stream(row.getComponents()).filter(c -> c instanceof SkillCardPanel).count());
                } finally { ui.panel().removeNotify(); }
            });
        }
    }
}
