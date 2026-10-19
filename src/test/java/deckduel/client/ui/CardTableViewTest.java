package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.Move;
import deckduel.core.card.PlayCard;
import deckduel.core.card.PlayerView;
import deckduel.core.card.Seat;
import deckduel.net.MatchUpdate;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import org.junit.jupiter.api.Test;

class CardTableViewTest {
    /** What the table asked of the client. */
    private static final class Recorder implements CardTableView.Actions {
        final List<Object> calls = new ArrayList<>();
        @Override public void move(Move move) { calls.add(move); }
        @Override public void rematch() { calls.add("rematch"); }
        @Override public void leave() { calls.add("leave"); }
    }

    private record Table(CardTableView view, Recorder actions, List<ScoringSounds.Cue> cues) { }

    private static Table table(ClientSettings settings, boolean sized) {
        Recorder actions = new Recorder();
        List<ScoringSounds.Cue> cues = new CopyOnWriteArrayList<>();
        var view = new CardTableView(actions, settings, new ScoringSounds(settings::gameSounds, cues::add));
        new Scene(view);
        view.getScene().getStylesheets().add(FxKit.STYLESHEET);
        if (sized) { size(view); view.shown(0); }
        return new Table(view, actions, cues);
    }

    private static void size(CardTableView view) {
        view.resize(1280, 720);
        view.applyCss();
        view.layout();
    }

    private static MatchState state() {
        return chosen(MatchState.start(DECK, DECK, CATALOG.landmarks(), 3));
    }

    private static final deckduel.core.card.Catalog CATALOG = CatalogLoader.loadBundled();
    private static final List<deckduel.core.card.CardDef> DECK = DeckRules.deck(CATALOG);

    /** Both players keep their first two Landmark offers, so round one is dealt. */
    private static MatchState chosen(MatchState state) {
        for (Seat seat : Seat.values())
            state = state.apply(seat, new deckduel.core.card.PickLandmarks(
                    state.view(seat).offers().stream().limit(2).map(deckduel.core.card.CardInstance::id).toList()));
        return state;
    }

    private static CardTableView.State shown(PlayerView view, boolean reducedMotion) {
        return new CardTableView.State(new MatchUpdate("M1", 1, "alice", "bob", false, true, false, false, false, view),
                false, reducedMotion);
    }

    private static Point2D centre(Rectangle r) { return new Point2D(r.getCenterX(), r.getCenterY()); }

    private static void click(CardTableView view, Rectangle target) {
        view.pressAt(centre(target));
        view.releaseAt(centre(target));
    }

    @Test void waitsWithoutInventingScoresUntilTheServerSendsAMatch() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            assertEquals(ScoreRail.WAITING, t.view.rail().description());
            assertTrue(t.view.lookupAll(".text-field").isEmpty(), "no chat input on the table");
            assertTrue(t.view.lookupAll(".button").stream()
                    .map(node -> ((javafx.scene.control.Button) node).getText())
                    .noneMatch(text -> text.toLowerCase(java.util.Locale.ROOT).contains("chat")),
                    "no chat action on the table");
            assertTrue(t.actions.calls.isEmpty());
        });
    }

    @Test void clickingACardThenASpaceSendsThatPlayAndWaitsForTheServer() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            int unit = 0;
            while (!view.hand().get(unit).def().occupiesSpace()) unit++;
            click(t.view, t.view.handBounds(unit));
            click(t.view, t.view.slotBounds(TableZone.OWN, 2));
            assertEquals(List.of(PlayCard.place(view.hand().get(unit).id(), 2)), t.actions.calls);
            assertTrue(view.you().row().cards().isEmpty(), "nothing is applied locally");
            assertTrue(t.view.rail().description().contains("Round 1 of 3"));
        });
    }

    @Test void dragOnlyModeIgnoresClicksButAcceptsADroppedCard() throws Exception {
        FxTesting.run(() -> {
            var settings = new ClientSettings();
            settings.setClickToSelectCards(false);
            var t = table(settings, true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            int unit = 0;
            while (!view.hand().get(unit).def().occupiesSpace()) unit++;
            click(t.view, t.view.handBounds(unit));
            click(t.view, t.view.slotBounds(TableZone.OWN, 0));
            assertTrue(t.actions.calls.isEmpty(), "a click does not select in drag-only mode");
            Rectangle from = t.view.handBounds(unit), to = t.view.slotBounds(TableZone.OWN, 5);
            t.view.pressAt(centre(from));
            t.view.dragTo(centre(to));
            t.view.releaseAt(centre(to));
            assertEquals(5, ((PlayCard) t.actions.calls.get(0)).slot());
        });
    }

    @Test void keyboardSelectsWithArrowsAndPlaysToANumberedSpace() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            int unit = 0;
            while (!view.hand().get(unit).def().occupiesSpace()) unit++;
            for (int i = 0; i <= unit; i++) t.view.keyPressed(key(javafx.scene.input.KeyCode.RIGHT));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.DIGIT4));
            assertEquals(List.of(PlayCard.place(view.hand().get(unit).id(), 3)), t.actions.calls);
        });
    }

    private static javafx.scene.input.KeyEvent key(javafx.scene.input.KeyCode code) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    @Test void draggingAHandCardWithinTheHandRearrangesItWithoutPlaying() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            long moved = view.hand().get(0).id();
            Rectangle third = t.view.handSlot(3, view.hand().size());
            t.view.pressAt(centre(t.view.handBounds(0)));
            t.view.dragTo(centre(third));
            assertEquals(3, t.view.shownHand().indexOf(moved), "the others make room while it is held there");
            t.view.releaseAt(centre(third));
            assertTrue(t.actions.calls.isEmpty(), "rearranging is not a play");
            assertEquals(3, t.view.shownHand().indexOf(moved));
            t.view.show(shown(view, true));
            assertEquals(3, t.view.shownHand().indexOf(moved), "the arrangement survives the next update");
            assertEquals(view.hand().stream().map(card -> card.id()).sorted().toList(),
                    t.view.shownHand().stream().sorted().toList(), "the same cards, only reordered");
        });
    }

    @Test void draggingACardOnYourRowMovesItOnScreenOnly() throws Exception {
        MatchState state = state();
        while (state.view(Seat.FIRST).you().row().cards().size() < 2 || state.turn() != Seat.FIRST)
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        PlayerView view = state.view(Seat.FIRST);
        int real = view.you().row().cards().get(0).slot(), empty = 0;
        while (view.you().row().at(empty) != null) empty++;
        int target = empty;
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            Rectangle before = t.view.slotBounds(TableZone.OWN, real), place = t.view.slotBounds(TableZone.OWN, target);
            t.view.pressAt(centre(before));
            t.view.dragTo(centre(place));
            t.view.releaseAt(centre(place));
            assertTrue(t.actions.calls.isEmpty(), "rearranging the row sends nothing to the server");
            assertEquals(place.getLocation(), t.view.slotBounds(TableZone.OWN, real).getLocation(), "the card shows in its new place");
            assertEquals(before.getLocation(), t.view.slotBounds(TableZone.OWN, target).getLocation(), "the empty space takes its old place");
            assertEquals(new CardTableView.Hit(TableZone.OWN, real), t.view.hitAt(centre(place)),
                    "clicks on the moved card still mean its real space");
        });
    }

    @Test void twoLandmarkOffersAreTickedThenKeptAndNothingElseIsClickable() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            PlayerView view = MatchState.start(DECK, DECK, CATALOG.landmarks(), 3).view(Seat.FIRST);
            t.view.show(shown(view, true));
            t.view.releaseAt(centre(t.view.confirmBounds()));
            assertTrue(t.actions.calls.isEmpty(), "nothing is sent before two are ticked");
            // A third tick is refused while two are ticked; unticking one frees a place.
            for (int i : new int[]{0, 1, 2, 1, 2}) click(t.view, t.view.offerBounds(i, 5));
            t.view.releaseAt(centre(t.view.confirmBounds()));
            var expected = new deckduel.core.card.PickLandmarks(List.of(view.offers().get(0).id(), view.offers().get(2).id()));
            assertEquals(List.of(expected), t.actions.calls, "the first and third offers are kept");
            var drawButton = t.view.drawBounds();
            assertNull(t.view.hitAt(centre(drawButton)), "the table underneath ignores clicks");
        });
    }

    @Test void aLandmarkChoiceArrivingBeforeLayoutDrawsNothingThenWorksByKeyboard() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), false);
            PlayerView view = MatchState.start(DECK, DECK, CATALOG.landmarks(), 3).view(Seat.FIRST);
            t.view.show(shown(view, true));
            size(t.view);
            t.view.shown(0);
            t.view.keyPressed(key(javafx.scene.input.KeyCode.DIGIT2));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.DIGIT4));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ENTER));
            assertEquals(List.of(new deckduel.core.card.PickLandmarks(List.of(view.offers().get(1).id(), view.offers().get(3).id()))),
                    t.actions.calls);
        });
    }

    @Test void skipButtonAndShortcutSendOnlyLegalServerMoves() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView before = state.view(state.turn());
            t.view.show(shown(before, true));
            click(t.view, t.view.skipBounds());
            assertEquals(List.of(new deckduel.core.card.SkipTurn()), t.actions.calls);
            assertEquals(0, before.you().playsUsed(), "no local state change");
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertEquals(2, t.actions.calls.size(), "the shortcut sends the same move");
            t.view.show(shown(state.view(state.turn().other()), true));
            click(t.view, t.view.skipBounds());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            t.view.show(new CardTableView.State(shown(before, true).match(), true, true));
            click(t.view, t.view.skipBounds());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            t.view.show(shown(MatchState.start(DECK, DECK, CATALOG.landmarks(), 3).view(Seat.FIRST), true));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            t.view.show(shown(state.forfeit(state.turn()).view(state.turn()), true));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertEquals(2, t.actions.calls.size(), "no skips when waiting, reconnecting, choosing or finished");
        });
    }

    @Test void landmarkShelvesStaySeparateFromTheLargerRowsAndHandAtBothWindowSizes() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            for (int[] size : new int[][]{{1280, 720}, {1470, 920}}) {
                t.view.resize(size[0], size[1]);
                t.view.layout();
                Rectangle viewport = new Rectangle(0, 0, size[0], size[1]);
                List<Rectangle> tiles = new ArrayList<>();
                for (TableZone zone : List.of(TableZone.OWN_LANDMARK, TableZone.OPPONENT_LANDMARK)) {
                    for (int i = 0; i < 2; i++) {
                        Rectangle tile = t.view.landmarkBounds(zone, i);
                        assertTrue(viewport.contains(tile), "Landmarks fit inside the window");
                        assertEquals(new CardTableView.Hit(zone, i), t.view.hitAt(centre(tile)),
                                "each relocated Landmark keeps its own target");
                        for (Rectangle earlier : tiles) assertFalse(tile.intersects(earlier));
                        tiles.add(tile);
                    }
                }
                for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
                    for (int slot = 0; slot < 7; slot++) {
                        Rectangle card = t.view.slotBounds(zone, slot);
                        assertTrue(viewport.contains(card));
                        for (Rectangle tile : tiles) assertFalse(tile.intersects(card), "Landmarks use no Unit space");
                        for (int hand = 0; hand < view.hand().size(); hand++)
                            assertFalse(card.intersects(t.view.handSlot(hand, view.hand().size())),
                                    "larger rows leave room for the enlarged hand");
                    }
                }
            }
        });
    }

    @Test void theDrawButtonSendsAManualDrawOnlyWhenOneIsAllowed() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            t.view.show(shown(state.view(state.turn()), true));
            click(t.view, t.view.drawBounds());
            assertEquals(List.of(new deckduel.core.card.DrawCard()), t.actions.calls);
            t.view.show(shown(state.view(state.turn().other()), true));
            click(t.view, t.view.drawBounds());
            assertEquals(1, t.actions.calls.size(), "no draw on the opponent's turn");
        });
    }

    @Test void reconnaissanceCardsAreKeptByClickingOne() throws Exception {
        MatchState state = state();
        Seat seat = state.turn();
        while (state.view(seat).looking().isEmpty()) {
            PlayerView view = state.view(state.turn());
            var recon = view.hand().stream().filter(c -> c.def().effect() instanceof deckduel.core.card.Effect.Reconnaissance).findFirst();
            if (view.seat() == seat && recon.isPresent() && view.yourTurn()) state = state.apply(seat, PlayCard.cast(recon.get().id(), null));
            else if (view.seat() == seat && view.canDraw()) state = state.apply(seat, new deckduel.core.card.DrawCard());
            else state = state.apply(state.turn(), GreedyBot.choose(view));
            if (state.round() > 1) fail("no Reconnaissance reached the hand in round one");
        }
        PlayerView looking = state.view(seat);
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(looking, true));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertTrue(t.actions.calls.isEmpty(), "Reconnaissance must be resolved before skipping");
            click(t.view, t.view.lookBounds(1, looking.looking().size()));
            assertEquals(List.of(new deckduel.core.card.KeepCard(looking.looking().get(1).id())), t.actions.calls);
        });
    }

    @Test void spiedCardsCanBeEnlargedAndDisappearFromTheInspectorWhenTheyLeaveTheEnemyHand() throws Exception {
        MatchState spied = null;
        for (long seed = 1; seed <= 100 && spied == null; seed++) {
            MatchState candidate = chosen(MatchState.start(DECK, DECK, CATALOG.landmarks(), seed));
            var spy = candidate.view(Seat.FIRST).hand().stream()
                    .filter(c -> c.def().effect() instanceof deckduel.core.card.Effect.Spy).findFirst();
            if (spy.isEmpty()) continue;
            if (candidate.turn() == Seat.SECOND) candidate = candidate.apply(Seat.SECOND, new deckduel.core.card.SkipTurn());
            spied = candidate.apply(Seat.FIRST, PlayCard.cast(spy.get().id(), null));
        }
        assertNotNull(spied);
        PlayerView before = spied.view(Seat.FIRST);
        var card = before.revealed().get(0);
        PlayCard enemyPlay = spied.view(Seat.SECOND).legalPlays().stream().filter(p -> p.cardId() == card.id()).findFirst().orElseThrow();
        PlayerView after = spied.apply(Seat.SECOND, enemyPlay).view(Seat.FIRST);
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(before, true));
            assertEquals(new CardTableView.Hit(TableZone.REVEALED, 0), t.view.hitAt(centre(t.view.revealedBounds(0))));
            click(t.view, t.view.revealedBounds(0));
            var details = (CardDetails) t.view.lookup("#cardDetails");
            assertTrue(details.isVisible());
            assertEquals(card.def().name(), ((javafx.scene.control.Label) details.lookup("#cardDetailName")).getText());
            assertEquals(card.def().text(), ((javafx.scene.control.Label) details.lookup("#cardDetailBuff")).getText());
            assertTrue(t.actions.calls.isEmpty(), "inspection does not play or steal a card");
            t.view.show(shown(after, true));
            assertFalse(details.isVisible(), "only cards still in the Spy-revealed hand remain inspectable");
            assertEquals("", details.getAccessibleText(), "closed inspector clears the old private description");
            click(t.view, new Rectangle(1030, 22, 20, 30));
            assertFalse(details.isVisible(), "unrevealed card backs do not open details");
        });
    }

    @Test void clickingALandmarkOpensReadOnlyBuffDetailsAndClosingKeepsThemClosed() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn().other());
            t.view.show(shown(view, true));
            click(t.view, t.view.landmarkBounds(TableZone.OWN_LANDMARK, 0));
            var details = (CardDetails) t.view.lookup("#cardDetails");
            assertTrue(details.isVisible(), "inspect even on the opponent's turn");
            assertEquals(view.you().landmarks().get(0).def().name(),
                    ((javafx.scene.control.Label) details.lookup("#cardDetailName")).getText());
            assertEquals(view.you().landmarks().get(0).def().text(),
                    ((javafx.scene.control.Label) details.lookup("#cardDetailBuff")).getText());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.D));
            click(t.view, t.view.handBounds(0));
            assertTrue(t.actions.calls.isEmpty(), "viewing a buff never sends a move");
            ((javafx.scene.control.Button) details.lookup("#closeCardDetails")).fire();
            assertFalse(details.isVisible());
            t.view.show(shown(view, true));
            assertFalse(details.isVisible(), "an update must not reopen a closed inspector");
            t.view.keyPressed(key(javafx.scene.input.KeyCode.L));
            assertTrue(details.isVisible(), "L opens Landmark details by keyboard");
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ESCAPE));
            assertFalse(details.isVisible());
            assertFalse(t.view.confirmShowing(), "Escape closes details without opening the leave dialog");
        });
    }

    @Test void anOpenInspectorRefreshesWhenTheServerSabotagesTheLandmark() throws Exception {
        MatchState found = null;
        for (long seed = 1; seed <= 100 && found == null; seed++) {
            MatchState candidate = chosen(MatchState.start(DECK, DECK, CATALOG.landmarks(), seed));
            if (candidate.view(Seat.SECOND).hand().stream().anyMatch(c -> c.def().effect() instanceof deckduel.core.card.Effect.Sabotage))
                found = candidate;
        }
        assertNotNull(found);
        MatchState before = found;
        if (found.turn() == Seat.FIRST) found = found.apply(Seat.FIRST, new deckduel.core.card.SkipTurn());
        long spell = found.view(Seat.SECOND).hand().stream()
                .filter(c -> c.def().effect() instanceof deckduel.core.card.Effect.Sabotage).findFirst().orElseThrow().id();
        MatchState after = found.apply(Seat.SECOND, PlayCard.cast(spell, found.view(Seat.FIRST).you().landmarks().get(0).card().id()));
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(before.view(Seat.FIRST), true));
            click(t.view, t.view.landmarkBounds(TableZone.OWN_LANDMARK, 0));
            var details = (CardDetails) t.view.lookup("#cardDetails");
            t.view.show(shown(after.view(Seat.FIRST), true));
            assertTrue(details.isVisible());
            assertTrue(((javafx.scene.control.Label) details.lookup("#cardDetailStatus")).getText().contains("SABOTAGED"));
            t.view.show(new CardTableView.State(null, false, true));
            assertFalse(details.isVisible(), "clearing a match also clears its inspector");
        });
    }

    @Test void sabotageTargetsAnEnemyLandmarkInItsArea() throws Exception {
        MatchState state = state();
        Seat seat = state.turn();
        for (int guard = 0; guard < 40; guard++) {
            PlayerView view = state.view(seat);
            if (view.yourTurn() && view.hand().stream().anyMatch(c -> c.def().effect() instanceof deckduel.core.card.Effect.Sabotage)) break;
            Seat turn = state.turn();
            state = view.yourTurn() && view.canDraw() ? state.apply(seat, new deckduel.core.card.DrawCard())
                    : state.apply(turn, GreedyBot.choose(state.view(turn)));
        }
        PlayerView view = state.view(seat);
        var sabotage = view.hand().stream().filter(c -> c.def().effect() instanceof deckduel.core.card.Effect.Sabotage).findFirst();
        org.junit.jupiter.api.Assumptions.assumeTrue(sabotage.isPresent() && view.yourTurn(), "seeded game reaches Sabotage");
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            int index = 0;
            while (view.hand().get(index).id() != sabotage.get().id()) index++;
            click(t.view, t.view.handBounds(index));
            Rectangle target = t.view.landmarkBounds(TableZone.OPPONENT_LANDMARK, 1);
            Point2D info = new Point2D(target.getMaxX() - 14, target.getCenterY());
            t.view.pressAt(info);
            t.view.releaseAt(info);
            assertTrue(t.actions.calls.isEmpty(), "the info icon inspects instead of casting");
            assertTrue(t.view.lookup("#cardDetails").isVisible());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ESCAPE));
            click(t.view, target);
            assertEquals(List.of(PlayCard.cast(sabotage.get().id(), view.opponent().landmarks().get(1).card().id())), t.actions.calls);
        });
    }

    @Test void leavingAMatchInProgressAsksBeforeForfeiting() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            t.view.show(shown(state.view(Seat.FIRST), true));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ESCAPE));
            assertTrue(t.view.confirmShowing(), "Escape with nothing selected offers to leave");
            assertTrue(t.actions.calls.isEmpty(), "nothing is sent until the player confirms");
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ESCAPE));
            assertFalse(t.view.confirmShowing());
        });
    }

    @Test void finishedMatchesOfferRematchAndReturnToTheMenu() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            while (state.phase() == MatchPhase.IN_PROGRESS) state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
            t.view.show(shown(state.view(Seat.FIRST), true));
            assertTrue(t.view.resultShowing());
            assertEquals("REMATCH", t.view.rematchButton().getText());
            t.view.rematchButton().fire();
            t.view.backButton().fire();
            assertEquals(List.of("rematch", "leave"), t.actions.calls);
            t.view.show(new CardTableView.State(null, false, true));
            assertFalse(t.view.resultShowing());
            assertEquals(ScoreRail.WAITING, t.view.rail().description(), "the old match is cleared");
        });
    }

    /** Plays from the first seat's hand until it has two matching Units, then returns the state before the second. */
    private static MatchState[] comboStep() {
        MatchState state = state();
        while (true) {
            MatchState before = state;
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
            var mine = state.view(Seat.FIRST).you().score();
            if (state.round() == 1 && !mine.pairs().isEmpty()
                    && mine.pairs().size() > before.view(Seat.FIRST).you().score().pairs().size())
                return new MatchState[]{before, state};
        }
    }

    @Test void aScoringPlayTracesCardThenEachNewPairThenCountsTheGain() throws Exception {
        MatchState[] step = comboStep();
        var before = step[0].view(Seat.FIRST);
        var after = step[1].view(Seat.FIRST);
        ScoringTrace trace = ScoringTrace.between(before, after);
        int newPairs = after.you().score().pairs().size() - before.you().score().pairs().size();
        assertEquals(newPairs, trace.links.stream().filter(link -> link.zone() == TableZone.OWN).count());
        assertEquals(ScoringSounds.Cue.Kind.CARD, trace.beats.get(0).cue().kind());
        assertEquals(ScoringSounds.Cue.Kind.GAIN, trace.beats.get(trace.beats.size() - 1).cue().kind());
        assertTrue(trace.finishAt > trace.links.get(trace.links.size() - 1).at(), "the score counts after the last pair");
        assertNull(ScoringTrace.between(after, after), "an unchanged table has nothing to trace");

        Table[] holder = new Table[1];
        FxTesting.run(() -> {
            var t = holder[0] = table(new ClientSettings(), true);
            t.view.show(shown(before, false));
            t.view.show(shown(after, false));
            assertTrue(t.view.rail().description().contains("Your score " + after.you().score().total() + "."),
                    "the true score is available at once");
            assertTrue(t.view.animating(), "the play is staged");
            assertTrue(t.cues.isEmpty(), "sounds follow the staging, not the arrival of the update");
        });
        Thread.sleep(Math.round((PlayAnimator.FLIGHT + trace.finishAt + 600) * Staging.TEMPO));
        FxTesting.run(() -> {
            var cues = holder[0].cues;
            // The opening deal still plays out alongside the play; it is not cut short.
            assertEquals(ScoringSounds.Cue.Kind.ROUND, cues.get(0).kind());
            int whoosh = cues.indexOf(new ScoringSounds.Cue(ScoringSounds.Cue.Kind.WHOOSH, 0));
            assertTrue(whoosh >= 0 && whoosh < cues.indexOf(new ScoringSounds.Cue(ScoringSounds.Cue.Kind.CARD, 0)),
                    "the card is heard flying before it lands");
            var scoring = java.util.EnumSet.of(ScoringSounds.Cue.Kind.CARD, ScoringSounds.Cue.Kind.PAIR,
                    ScoringSounds.Cue.Kind.GAIN, ScoringSounds.Cue.Kind.LOSS);
            assertEquals(trace.beats.stream().map(ScoringTrace.Beat::cue).toList(),
                    cues.stream().filter(cue -> scoring.contains(cue.kind())).toList(), "then the scoring trace in order");
        });
    }

    @Test void reducedMotionSkipsTheTraceAndMutedSoundsStaySilent() throws Exception {
        MatchState[] step = comboStep();
        FxTesting.run(() -> {
            var settings = new ClientSettings();
            var t = table(settings, true);
            t.view.show(shown(step[0].view(Seat.FIRST), true));
            t.view.show(shown(step[1].view(Seat.FIRST), true));
            assertEquals(List.of(new ScoringSounds.Cue(ScoringSounds.Cue.Kind.GAIN, 0)), t.cues, "only the result sound");
            assertFalse(t.view.animating(), "reduced motion stages no card flights or effects");
            settings.setGameSounds(false);
            t.view.show(shown(step[0].view(Seat.FIRST), true));
            assertEquals(1, t.cues.size());
        });
    }

    @Test void theOpeningDealWaitsForTheWindowToAppearThenPlaysInFull() throws Exception {
        Table[] holder = new Table[1];
        FxTesting.run(() -> {
            var t = holder[0] = table(new ClientSettings(), true);
            t.view.hidden();
            t.view.show(shown(state().view(Seat.FIRST), false));
            assertFalse(t.view.animating(), "nothing is dealt to a table that is not on screen");
            t.view.shown(300);
            assertFalse(t.view.animating(), "nor while its window is still appearing");
        });
        Thread.sleep(700);
        FxTesting.run(() -> assertTrue(holder[0].view.animating(), "then the whole deal plays"));
    }

    @Test void aMatchThatArrivesBeforeLayoutStillOpensWithTheDeal() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), false);
            t.view.show(shown(state().view(Seat.FIRST), false));
            assertFalse(t.view.animating(), "an unsized table cannot place the deal yet");
            size(t.view);
            assertFalse(t.view.animating(), "nor one that is not on screen: the deal would be missed");
            t.view.shown(0);
            assertTrue(t.view.animating(), "the opening deal starts once the table is sized and showing");
        });
    }
}
