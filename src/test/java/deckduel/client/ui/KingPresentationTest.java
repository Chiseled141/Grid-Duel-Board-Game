package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.*;
import deckduel.net.MatchUpdate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible visual evidence at supported desktop sizes, with layout and trace assertions. */
class KingPresentationTest {
    private static CardTableView.State snapshot(PlayerView view) {
        return new CardTableView.State(new MatchUpdate("qa", 1, "alice", "bob", false, true, false, false, false, view), false, true);
    }
    private static void capture(CardTableView table, String name) throws Exception {
        WritableImage image = FxTesting.call(() -> table.snapshot(null, null));
        Path out = Path.of("target", "kings-qa", name + ".png");
        Files.createDirectories(out.getParent());
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out.toFile());
    }

    /** UI-only round-two fixture: four palettes and nonzero contributions on occupied rows. */
    private static PlayerView deployed(PlayerView dealt) {
        var units = DeckRules.deck(CatalogLoader.loadBundled()).stream().filter(CardDef::occupiesSpace).toList();
        var chosen = new java.util.ArrayList<CardDef>();
        chosen.addAll(units.stream().filter(card -> card.classes().contains("Assassin")).limit(3).toList());
        chosen.addAll(units.stream().filter(card -> card.classes().contains("Wizard")).limit(2).toList());
        chosen.addAll(units.stream().filter(card -> card.classes().contains("Bowman")).limit(2).toList());
        Row row = Row.EMPTY;
        for (int i = 0; i < chosen.size(); i++) row = row.place(i, new CardInstance(1000 + i, chosen.get(i)));
        var yours = new KingStatus(List.of(King.MORDRAVEN, King.ALTHARION), 2, false);
        var theirs = new KingStatus(List.of(King.VARKHAN, King.THALVAREN), 2, false);
        var you = new SideView(row, Scoring.score(row, yours), 7, 0, dealt.hand().size(), 35, 0, 177, yours);
        var opponent = new SideView(row, Scoring.score(row, theirs), 7, 0, 10, 35, 0, 158, theirs);
        return new PlayerView(Seat.FIRST, 3, MatchPhase.IN_PROGRESS, 2, Seat.FIRST, you, opponent,
                dealt.hand(), List.of(), List.of(), null, null, List.of(), List.of());
    }

    @Test void originalArtOffersSlotsInspectorAndEdictFitTheTable() throws Exception {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState opening = null;
        for (long seed = 0; opening == null; seed++) {
            var candidate = MatchState.start(deck, deck, seed);
            if (candidate.view(Seat.FIRST).kingOffer().contains(King.NETHRION)) opening = candidate;
        }
        MatchState offered = opening;
        MatchState ready = offered.apply(Seat.FIRST, new ChooseKing(King.NETHRION)).apply(Seat.SECOND, new ChooseKing(King.NETHRION));
        PlayerView edict = ready.apply(ready.turn(), new DrawCard()).view(ready.turn());
        PlayerView deployed = deployed(offered.view(Seat.FIRST));
        CardTableView table = FxTesting.call(() -> {
            for (King king : King.values()) KingArt.image(king);
            var view = new CardTableView(new CardTableView.Actions() {
                public void move(Move move) { }
                public void rematch() { }
                public void leave() { }
            }, new ClientSettings(), new ScoringSounds(() -> false));
            new Scene(view); view.getScene().getStylesheets().add(FxKit.STYLESHEET);
            return view;
        });
        long deadline = System.currentTimeMillis() + 10000;
        while (!FxTesting.call(() -> List.of(King.values()).stream().allMatch(k -> KingArt.image(k).getProgress() == 1))) {
            assertTrue(System.currentTimeMillis() < deadline, "paintings load in the background"); Thread.sleep(20);
        }
        for (int[] size : new int[][] {{1280, 720}, {1470, 923}, {1920, 1080}}) {
            FxTesting.run(() -> {
                table.resize(size[0], size[1]); table.applyCss(); table.layout(); table.shown(0);
                table.show(snapshot(offered.view(Seat.FIRST))); table.applyCss(); table.layout();
                assertTrue(((KingTableControls) table.lookup("#kingControls")).choosingVisible());
                assertEquals(size[0], table.lookup("#kingSelection").getBoundsInParent().getWidth(), .1);
                assertFalse(table.animating());
            });
            capture(table, "offer-" + size[0]);
            FxTesting.run(() -> ((javafx.scene.control.Button) table.lookup("#offer-" + King.NETHRION.id()))
                    .getOnMouseEntered().handle(null));
            capture(table, "offer-hover-" + size[0]);
            FxTesting.run(() -> ((javafx.scene.control.Button) table.lookup("#offer-" + King.NETHRION.id()))
                    .getOnMouseExited().handle(null));
            FxTesting.run(() -> {
                var toggle = (javafx.scene.control.Button) table.lookup("#kingHandToggle");
                assertTrue(toggle.isVisible());
                double handBottom = java.util.stream.IntStream.range(0, offered.view(Seat.FIRST).hand().size())
                        .mapToDouble(i -> table.handBounds(i).getMaxY()).max().orElseThrow();
                assertTrue(toggle.getBoundsInParent().getMinY() > handBottom, "toggle stays below the hand");
                toggle.fire();
                assertFalse(((KingTableControls) table.lookup("#kingControls")).choosingVisible());
            });
            capture(table, "hand-preview-" + size[0]);
            FxTesting.run(() -> {
                table.show(snapshot(deployed)); table.applyCss(); table.layout();
                var button = table.lookup("#yourKing0");
                var location = button.localToScene(button.getBoundsInLocal());
                assertTrue(location.getMinX() > table.slotBounds(TableZone.OWN, 6).getMaxX(), "Kings do not cover Units");
                assertTrue(location.getMaxX() < table.drawBounds().x, "Kings do not cover actions");
                var kingArt = (javafx.scene.image.ImageView) ((javafx.scene.control.Button) button).getGraphic();
                assertEquals(table.slotBounds(TableZone.OWN, 0).height, kingArt.getFitHeight(), .1);
            });
            capture(table, "table-" + size[0]);
        }
        FxTesting.run(() -> {
            var button = (javafx.scene.control.Button) table.lookup("#yourKing0");
            button.getOnMouseEntered().handle(null);
            assertTrue(table.lookup("#kingHoverArt").isVisible());
        });
        capture(table, "king-hover-art");
        FxTesting.run(() -> ((javafx.scene.control.Button) table.lookup("#yourKing0")).fire());
        capture(table, "inspector");
        FxTesting.run(() -> {
            ((KingTableControls) table.lookup("#kingControls")).closeInspector();
            table.show(snapshot(edict));
        });
        capture(table, "secret-edict");
        FxTesting.run(table::hidden);
    }

    @Test void kingOnlyScoreChangeGetsItsOwnTraceAfterMultiplier() throws Exception {
        var unit = new CardDef("a", "Assassin", CardKind.UNIT, 2, List.of("Ashkar"), List.of("Assassin"), null);
        Row row = Row.EMPTY.place(0, new CardInstance(1, unit));
        KingStatus before = new KingStatus(List.of(King.AURENDOR), 0, false), after = new KingStatus(List.of(King.AURENDOR), 1, false);
        SideView oldSide = new SideView(row, Scoring.score(row, before), 0, 0, 10, 35, 0, 0, before);
        SideView newSide = new SideView(row, Scoring.score(row, after), 1, 0, 9, 35, 1, 0, after);
        PlayerView was = new PlayerView(Seat.FIRST, 2, MatchPhase.IN_PROGRESS, 1, Seat.FIRST, oldSide, oldSide,
                List.of(), List.of(), List.of(), null, null, List.of(), List.of());
        PlayerView now = new PlayerView(Seat.FIRST, 3, MatchPhase.IN_PROGRESS, 1, Seat.SECOND, newSide, oldSide,
                List.of(), List.of(), List.of(), null, null, List.of(), List.of());
        ScoringTrace trace = ScoringTrace.between(was, now);
        assertNotNull(trace); assertEquals(3, trace.kingGain.get(TableZone.OWN));
        assertTrue(trace.kingsAt >= trace.formationAt); assertTrue(trace.finishAt > trace.kingsAt);
    }
}
