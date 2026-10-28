package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Accepted King ownership travels with its aura, and cancellation restores the actual slots. */
class KingDeploymentTest {
    private static final java.util.List<CardDef> DECK = DeckRules.deck(CatalogLoader.loadBundled());

    private static KingTableControls controls(PlayerView offered, java.util.List<ChooseKing> moves) {
        var controls = new KingTableControls(moves::add);
        new Scene(new Pane(controls), 1280, 720);
        controls.position(1020, 100, 350, 1280, 720, 90, 126);
        controls.update(offered, 0, false, false, false);
        controls.applyCss(); controls.layout();
        return controls;
    }

    @Test void bothPublicKingsFlyWithAuraThenRevealTheirSlotsWithoutSendingMoves() throws Exception {
        MatchState opening = MatchState.start(DECK, DECK, 3);
        King king = opening.view(Seat.FIRST).kingOffer().get(1);
        MatchState waiting = opening.apply(Seat.FIRST, new ChooseKing(king));
        MatchState ready = waiting.apply(Seat.SECOND, new ChooseKing(king));
        var moves = new ArrayList<ChooseKing>();
        KingTableControls controls = FxTesting.call(() -> controls(opening.view(Seat.FIRST), moves));
        FxTesting.run(() -> {
            controls.update(waiting.view(Seat.FIRST), 0, false, false, false);
            assertFalse(controls.deploying(), "private confirmation does not disclose ownership");
            controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            assertTrue(controls.deploying());
            assertFalse(controls.choosingVisible());
            for (String side : new String[] {"your", "foe"}) {
                var flight = (ImageView) controls.lookup("#" + side + "KingFlight0");
                assertNotNull(flight.getEffect(), "the aura travels on the same node as the painting");
                assertTrue(flight.getFitHeight() > 126);
                assertEquals(0, controls.lookup("#" + side + "King0").getParent().getOpacity());
            }
        });
        Thread.sleep(250);
        var image = FxTesting.call(() -> controls.snapshot(null, null));
        Path evidence = Path.of("target", "kings-qa", "deployment-in-flight.png");
        Files.createDirectories(evidence.getParent());
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", evidence.toFile());
        Thread.sleep(600);
        FxTesting.run(() -> {
            assertFalse(controls.deploying());
            assertNull(controls.lookup("#yourKingFlight0"));
            assertEquals(1, controls.lookup("#yourKing0").getParent().getOpacity());
            assertEquals(1, controls.lookup("#foeKing0").getParent().getOpacity());
            controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            assertFalse(controls.deploying(), "repeated snapshots do not replay the flight");
            assertTrue(moves.isEmpty()); controls.reset();
        });
    }

    @Test void roundTwoUsesSecondSlotAndReducedMotionResizeAndHidingFinishCleanly() throws Exception {
        MatchState state = MatchState.start(DECK, DECK, 3);
        King first = state.view(Seat.FIRST).kingOffer().get(0);
        state = state.apply(Seat.FIRST, new ChooseKing(first)).apply(Seat.SECOND, new ChooseKing(first));
        while (state.phase() == MatchPhase.IN_PROGRESS) state = state.apply(state.turn(), new SkipTurn());
        MatchState opening = state;
        King second = opening.view(Seat.FIRST).kingOffer().get(0);
        MatchState ready = opening.apply(Seat.FIRST, new ChooseKing(second)).apply(Seat.SECOND, new ChooseKing(second));
        FxTesting.run(() -> {
            var controls = controls(opening.view(Seat.FIRST), new ArrayList<>());
            ((Button) controls.lookup("#kingHandToggle")).fire();
            controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            assertTrue(controls.deploying(), "a hidden offer still deploys the accepted King");
            assertNotNull(controls.lookup("#yourKingFlight1"));
            assertNull(controls.lookup("#yourKingFlight0"));
            controls.update(ready.view(Seat.FIRST), 0, false, false, true);
            assertFalse(controls.deploying());
            assertEquals(1, controls.lookup("#yourKing1").getParent().getOpacity());
            controls.reset(); controls.update(opening.view(Seat.FIRST), 0, false, false, false);
            controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            controls.position(1100, 100, 350, 1400, 800, 90, 126);
            assertFalse(controls.deploying(), "resizing snaps to the newly laid out slots");
            controls.reset(); controls.update(opening.view(Seat.FIRST), 0, false, false, false);
            controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            controls.suspend(); assertFalse(controls.deploying());
            assertEquals(1, controls.lookup("#yourKing1").getParent().getOpacity());
            controls.reset(); controls.update(ready.view(Seat.FIRST), 0, false, false, false);
            assertFalse(controls.deploying(), "an initial reconnect snapshot appears immediately");
            controls.reset();
        });
    }
}
