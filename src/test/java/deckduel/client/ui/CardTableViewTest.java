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
        return MatchState.start(DECK, DECK, 3);
    }

    private static final deckduel.core.card.Catalog CATALOG = CatalogLoader.loadBundled();
    private static final List<deckduel.core.card.CardDef> DECK = DeckRules.deck(CATALOG);

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

    @Test void restingOnARowCardQueuesALargePreviewThatAClickDismisses() throws Exception {
        PlayerView view = rowView();
        int slot = 0;
        while (view.you().row().at(slot) == null) slot++;
        int unit = slot, empty = view.you().row().at(deckduel.core.card.Row.SIZE - 1) == null ? deckduel.core.card.Row.SIZE - 1 : 0;
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            Rectangle card = t.view.rowCardBounds(TableZone.OWN, unit);
            t.view.moveTo(centre(card));
            assertEquals(new CardTableView.Hit(TableZone.OWN, unit), t.view.previewTarget());
            t.view.moveTo(centre(t.view.handBounds(0)));
            assertNull(t.view.previewTarget(), "hand cards are already large");
            t.view.moveTo(centre(t.view.slotBounds(TableZone.OWN, empty)));
            assertNull(t.view.previewTarget(), "an empty space has nothing to preview");
            t.view.moveTo(centre(card));
            t.view.pressAt(centre(card));
            assertNull(t.view.previewTarget(), "picking the card up dismisses its preview");
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

    @Test void rowRearrangementPreviewsASwapThenSettlesWithoutSendingAMove() throws Exception {
        PlayerView view = rowView();
        int from = view.you().row().cards().get(0).slot(), to = view.you().row().cards().get(1).slot();
        Table[] holder = new Table[1];
        Rectangle[] places = new Rectangle[2];
        FxTesting.run(() -> {
            var t = holder[0] = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            t.view.show(shown(view, false)); // Enable motion after setup, without an opening deal.
            Rectangle origin = places[0] = t.view.slotBounds(TableZone.OWN, from);
            Rectangle target = places[1] = t.view.slotBounds(TableZone.OWN, to);
            t.view.pressAt(centre(origin));
            t.view.dragTo(centre(target));
            assertEquals(origin, t.view.slotBounds(TableZone.OWN, to), "the displaced card heads to the source space");
            assertEquals(target, t.view.rowCardBounds(TableZone.OWN, to), "it starts sliding from its visible position");
            t.view.dragTo(centre(target));
            assertEquals(origin, t.view.slotBounds(TableZone.OWN, to), "a stationary pointer keeps the same preview");
            t.view.releaseAt(centre(target).add(0, 20));
            assertEquals(target, t.view.slotBounds(TableZone.OWN, from));
            assertEquals(target.y + 20, t.view.rowCardBounds(TableZone.OWN, from).y,
                    "the dropped card starts settling from the pointer");
            assertTrue(t.actions.calls.isEmpty(), "no gameplay action for the swap");
        });
        Thread.sleep(1500);
        FxTesting.run(() -> {
            var t = holder[0];
            assertEquals(places[1], t.view.rowCardBounds(TableZone.OWN, from));
            assertEquals(places[0], t.view.rowCardBounds(TableZone.OWN, to));
            assertEquals(new CardTableView.Hit(TableZone.OWN, from), t.view.hitAt(centre(places[1])),
                    "targeting still uses the server's original slot");
            t.view.show(shown(view, false));
            assertEquals(places[1], t.view.slotBounds(TableZone.OWN, from), "the swap survives server updates");
            assertTrue(t.actions.calls.isEmpty());
        });
    }

    @Test void cancelledRowDragReturnsToItsOriginalSpaceAndReducedMotionSnaps() throws Exception {
        PlayerView view = rowView();
        int from = view.you().row().cards().get(0).slot(), to = view.you().row().cards().get(1).slot();
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            t.view.show(shown(view, false));
            Rectangle origin = t.view.slotBounds(TableZone.OWN, from), target = t.view.slotBounds(TableZone.OWN, to);
            t.view.pressAt(centre(origin));
            t.view.dragTo(centre(target));
            Point2D outside = new Point2D(target.getCenterX(), origin.y - 35);
            t.view.dragTo(outside);
            assertEquals(origin, t.view.slotBounds(TableZone.OWN, from), "leaving the row cancels its preview");
            t.view.releaseAt(outside);
            assertNotEquals(origin, t.view.rowCardBounds(TableZone.OWN, from), "a cancelled card glides back");
            t.view.show(shown(view, true));
            assertEquals(origin, t.view.rowCardBounds(TableZone.OWN, from), "reduced motion immediately settles it");
            t.view.pressAt(centre(origin));
            t.view.dragTo(centre(target));
            assertEquals(origin, t.view.rowCardBounds(TableZone.OWN, to), "reduced motion previews without easing");
            t.view.releaseAt(centre(target));
            assertEquals(target, t.view.rowCardBounds(TableZone.OWN, from));
            assertTrue(t.actions.calls.isEmpty());
        });
    }

    private static PlayerView rowView() {
        MatchState state = state();
        while (state.view(Seat.FIRST).you().row().cards().size() < 2 || state.turn() != Seat.FIRST)
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        return state.view(Seat.FIRST);
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
            t.view.show(shown(state.forfeit(state.turn()).view(state.turn()), true));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertEquals(2, t.actions.calls.size(), "no skips when waiting, reconnecting or finished");
        });
    }

    @Test void bothRowsAndTheLargerHandFitTheWindowAtBothSizes() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state();
            PlayerView view = state.view(state.turn());
            t.view.show(shown(view, true));
            for (int[] size : new int[][]{{1280, 720}, {1470, 920}}) {
                t.view.resize(size[0], size[1]);
                t.view.layout();
                Rectangle viewport = new Rectangle(0, 0, size[0], size[1]);
                for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
                    for (int slot = 0; slot < 7; slot++) {
                        Rectangle card = t.view.slotBounds(zone, slot);
                        assertTrue(viewport.contains(card));
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

    /** A round-one view in which the starting player is choosing which Reconnaissance card to keep. */
    private static PlayerView reconnoitring() {
        for (long seed = 1; seed < 100; seed++) {
            MatchState state = MatchState.start(DECK, DECK, seed);
            Seat seat = state.turn();
            while (state.round() == 1 && state.view(seat).looking().isEmpty()) {
                PlayerView view = state.view(state.turn());
                var recon = view.hand().stream().filter(c -> c.def().effect() instanceof deckduel.core.card.Effect.Reconnaissance).findFirst();
                if (view.seat() == seat && recon.isPresent() && view.yourTurn()) state = state.apply(seat, PlayCard.cast(recon.get().id(), null));
                else if (view.seat() == seat && view.canDraw()) state = state.apply(seat, new deckduel.core.card.DrawCard());
                else state = state.apply(state.turn(), GreedyBot.choose(view));
            }
            if (state.round() == 1) return state.view(seat);
        }
        throw new AssertionError("no Reconnaissance reached the hand in round one");
    }

    @Test void reconnaissanceCardsAreKeptByClickingOne() throws Exception {
        PlayerView looking = reconnoitring();
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
            MatchState candidate = MatchState.start(DECK, DECK, seed);
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
