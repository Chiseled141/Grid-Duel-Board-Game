package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardInstance;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.Move;
import deckduel.core.card.PlayCard;
import deckduel.core.card.PlayerView;
import deckduel.core.card.Row;
import deckduel.core.card.RowCard;
import deckduel.core.card.Scoring;
import deckduel.core.card.Seat;
import deckduel.core.card.SideView;
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
        var view = new CardTableView(actions, new ScoringSounds(settings::gameSounds, cues::add));
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
        return deckduel.TestMatches.start(DECK, DECK, 3);
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

    @Test void kingSelectionConfirmsThroughActionsThenShowsPublicArtworkAndInspector() throws Exception {
        MatchState initial = MatchState.start(DECK, DECK, 3);
        var king = initial.view(Seat.FIRST).kingOffer().get(0);
        MatchState waiting = initial.apply(Seat.FIRST, new deckduel.core.card.ChooseKing(king));
        MatchState ready = waiting.apply(Seat.SECOND, new deckduel.core.card.ChooseKing(king));
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(initial.view(Seat.FIRST), true));
            var controls = (KingTableControls) t.view.lookup("#kingControls");
            assertTrue(controls.choosingVisible());
            var confirm = (javafx.scene.control.Button) controls.lookup("#confirm-" + king.id());
            assertEquals("CONFIRM", confirm.getText()); assertTrue(confirm.isDisabled());
            ((javafx.scene.control.Button) controls.lookup("#offer-" + king.id())).fire();
            assertTrue(t.actions.calls.isEmpty(), "select before confirm");
            confirm.fire(); assertEquals(List.of(new deckduel.core.card.ChooseKing(king)), t.actions.calls);
            assertEquals(0, t.view.shownRevision(), "selection waits for the server");
            t.view.show(shown(waiting.view(Seat.FIRST), true));
            assertTrue(controls.choosingVisible());
            assertTrue(((javafx.scene.control.Button) controls.lookup("#confirm-" + king.id())).isDisabled());
            t.view.show(shown(ready.view(Seat.FIRST), true));
            assertFalse(controls.choosingVisible());
            var own = (javafx.scene.control.Button) controls.lookup("#yourKing0");
            assertInstanceOf(javafx.scene.image.ImageView.class, own.getGraphic());
            assertTrue(((javafx.scene.image.ImageView) own.getGraphic()).isPreserveRatio());
            assertTrue(own.getAccessibleText().contains(king.title()));
            own.fire();
            assertTrue(controls.lookupAll(".label").stream().anyMatch(n -> ((javafx.scene.control.Label) n).getText().contains("Current contribution")));
            assertEquals(1, t.actions.calls.size(), "inspection sends no move");
            assertTrue(t.view.rail().description().contains("Kings +0 after multiplier"));
            controls.closeInspector();
            t.view.hidden();
        });
    }

    @Test void kingToggleRevealsTheHandWithoutSendingMovesOrLosingTheCandidate() throws Exception {
        MatchState opening = MatchState.start(DECK, DECK, 3);
        PlayerView view = opening.view(Seat.FIRST);
        var king = view.kingOffer().get(1);
        long deadline = System.currentTimeMillis() + 30_000;
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            var update = new MatchUpdate("M1", 1, "alice", "bob", false, true, false, false, false, view, deadline);
            t.view.show(new CardTableView.State(update, false, true));
            var controls = (KingTableControls) t.view.lookup("#kingControls");
            var confirm = (javafx.scene.control.Button) controls.lookup("#confirm-" + king.id());
            ((javafx.scene.control.Button) controls.lookup("#offer-" + king.id())).fire();
            var toggle = (javafx.scene.control.Button) controls.lookup("#kingHandToggle");
            assertTrue(toggle.isVisible()); toggle.fire();
            assertFalse(controls.choosingVisible()); assertTrue(controls.selectionHidden());
            int index = view.hand().size() - 1;
            assertEquals(new CardTableView.Hit(TableZone.HAND, index), t.view.hitAt(centre(t.view.handBounds(index))));
            int unit = 0; while (!view.hand().get(unit).def().occupiesSpace()) unit++;
            t.view.moveTo(centre(t.view.handBounds(unit)));
            assertNull(t.view.previewTarget(), "the visible hand must not open Unit popups during King selection");
            for (int handIndex = 0; handIndex < view.hand().size(); handIndex++) {
                t.view.moveTo(centre(t.view.handBounds(handIndex)));
                assertNull(t.view.previewTarget(), "neither Unit nor Spell previews are queued");
            }
            assertFalse(t.view.lookup("#spellHoverDetails").isVisible());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.D));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertTrue(t.actions.calls.isEmpty(), "hiding the offer does not unlock gameplay");
            assertEquals("", toggle.getText()); assertNotNull(toggle.getGraphic());
            assertTrue(toggle.getAccessibleText().contains("Show King choices"));
            assertTrue(controls.lookup("#kingHandCountdown").isVisible());
            toggle.fire(); assertTrue(controls.choosingVisible());
            assertEquals("CONFIRM", confirm.getText()); assertFalse(confirm.isDisabled());
            confirm.fire(); assertEquals(List.of(new deckduel.core.card.ChooseKing(king)), t.actions.calls);
            assertEquals(0, t.view.shownRevision());
            t.view.hidden();
        });
    }

    @Test void kingHoverShowsOnlyAnEnlargedPaintingWithoutAnInformationTooltip() throws Exception {
        MatchState opening = MatchState.start(DECK, DECK, 3);
        var king = opening.view(Seat.FIRST).kingOffer().get(0);
        MatchState ready = opening.apply(Seat.FIRST, new deckduel.core.card.ChooseKing(king))
                .apply(Seat.SECOND, new deckduel.core.card.ChooseKing(king));
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(ready.view(Seat.FIRST), true));
            var button = (javafx.scene.control.Button) t.view.lookup("#yourKing0");
            assertNull(button.getTooltip());
            var small = (javafx.scene.image.ImageView) button.getGraphic();
            assertEquals(t.view.slotBounds(TableZone.OWN, 0).height, small.getFitHeight(), .1);
            button.getOnMouseEntered().handle(null);
            var preview = (javafx.scene.image.ImageView) t.view.lookup("#kingHoverArt");
            assertTrue(preview.isVisible()); assertTrue(preview.isPreserveRatio());
            assertTrue(preview.getFitHeight() >= 400 && preview.getFitHeight() <= 480);
            assertSame(small.getImage(), preview.getImage());
            button.fire();
            assertTrue(((javafx.scene.image.ImageView) t.view.lookup("#kingInspectorArt")).getFitHeight() <= 480);
            ((KingTableControls) t.view.lookup("#kingControls")).closeInspector();
            button.getOnMouseExited().handle(null); assertFalse(preview.isVisible());
            assertTrue(t.actions.calls.isEmpty()); t.view.hidden();
        });
    }

    @Test void kingRevealAnimatesAndReducedMotionCancelsItWithoutClientAutoSelection() throws Exception {
        PlayerView view = MatchState.start(DECK, DECK, 3).view(Seat.FIRST);
        List<deckduel.core.card.ChooseKing> moves = new ArrayList<>();
        KingTableControls controls = FxTesting.call(() -> {
            var c = new KingTableControls(moves::add);
            new Scene(new javafx.scene.layout.Pane(c));
            c.position(920, 92, 310, 1280, 720, 90, 126);
            c.update(view, System.currentTimeMillis() + 30_000, false, false, false);
            assertTrue(c.revealing()); return c;
        });
        Thread.sleep(750);
        FxTesting.run(() -> {
            assertFalse(controls.revealing());
            var toggle = (javafx.scene.control.Button) controls.lookup("#kingHandToggle");
            toggle.fire(); toggle.fire(); assertTrue(controls.revealing());
            controls.update(view, System.currentTimeMillis() - 1, false, false, true);
            assertFalse(controls.revealing());
            assertTrue(((javafx.scene.control.Label) controls.lookup("#kingCountdown")).getText().startsWith("0s"));
            assertTrue(moves.isEmpty(), "only the server chooses a default King");
            controls.reset(); assertFalse(toggle.isVisible()); assertFalse(controls.choosingVisible());
        });
    }

    @Test void secretEdictUsesItsOwnPrivateChoiceAndKeepMoveWithReducedMotion() throws Exception {
        MatchState initial = null;
        for (int seed = 0; initial == null; seed++) {
            MatchState candidate = MatchState.start(DECK, DECK, seed);
            if (candidate.view(Seat.FIRST).kingOffer().contains(deckduel.core.card.King.NETHRION)) initial = candidate;
        }
        var king = deckduel.core.card.King.NETHRION;
        initial = initial.apply(Seat.FIRST, new deckduel.core.card.ChooseKing(king)).apply(Seat.SECOND, new deckduel.core.card.ChooseKing(king));
        PlayerView pending = initial.apply(initial.turn(), new deckduel.core.card.DrawCard()).view(initial.turn());
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(pending, true));
            assertFalse(t.view.animating());
            t.view.keyPressed(key(javafx.scene.input.KeyCode.S));
            assertTrue(t.actions.calls.isEmpty());
            click(t.view, t.view.lookBounds(1, 2));
            assertEquals(List.of(new deckduel.core.card.KeepEdictCard(pending.looking().get(1).id())), t.actions.calls);
            assertEquals(2, pending.looking().size(), "UI never changes the snapshot");
            t.view.hidden();
        });
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

    @Test void unitsOnEitherRowAndInTheHandDoNotOpenHoverDetails() throws Exception {
        PlayerView view = rowView();
        int slot = 0;
        while (view.you().row().at(slot) == null) slot++;
        int unit = slot, empty = view.you().row().at(deckduel.core.card.Row.SIZE - 1) == null ? deckduel.core.card.Row.SIZE - 1 : 0;
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(view, true));
            Rectangle card = t.view.rowCardBounds(TableZone.OWN, unit);
            t.view.moveTo(centre(card));
            assertNull(t.view.previewTarget(), "Unit rows have no magnified detail panel");
            for (var enemy : view.opponent().row().cards()) {
                t.view.moveTo(centre(t.view.rowCardBounds(TableZone.OPPONENT, enemy.slot())));
                assertNull(t.view.previewTarget());
            }
            int handUnit = 0;
            while (!view.hand().get(handUnit).def().occupiesSpace()) handUnit++;
            t.view.moveTo(centre(t.view.handBounds(handUnit)));
            assertNull(t.view.previewTarget(), "hand Units are already large");
            t.view.moveTo(centre(t.view.slotBounds(TableZone.OWN, empty)));
            assertNull(t.view.previewTarget(), "an empty space has nothing to preview");
            t.view.moveTo(centre(card));
            t.view.pressAt(centre(card));
            assertNull(t.view.previewTarget(), "picking the card up dismisses its preview");
        });
    }

    @Test void hoveringEverySpellShowsReadableRulesWithoutSendingAMove() throws Exception {
        var spells = DECK.stream().filter(card -> !card.occupiesSpace())
                .map(card -> new deckduel.core.card.CardInstance(DECK.indexOf(card) + 1L, card)).toList();
        PlayerView base = state().view(Seat.FIRST);
        PlayerView view = new PlayerView(base.seat(), base.revision(), base.phase(), base.round(), base.turn(),
                base.you(), base.opponent(), spells, base.rounds(), base.events(), base.outcome(),
                base.lastPlay(), List.of(), List.of());
        Table t = FxTesting.call(() -> {
            var table = table(new ClientSettings(), true);
            table.view.show(shown(view, true));
            return table;
        });
        for (int i = 0; i < spells.size(); i++) {
            int index = i;
            FxTesting.run(() -> {
                t.view.moveTo(centre(t.view.handBounds(index)));
                assertFalse(t.view.lookup("#spellHoverDetails").isVisible(), "wait for the hover delay");
            });
            Thread.sleep(550);
            FxTesting.run(() -> {
                var tip = t.view.lookup("#spellHoverDetails");
                assertTrue(tip.isVisible(), spells.get(index).def().name());
                assertTrue(tip.isMouseTransparent(), "details must not intercept card input");
                var layers = t.view.getChildrenUnmodifiable();
                assertTrue(layers.indexOf(tip) > layers.indexOf(t.view.lookup("#kingControls")),
                        "Kings and their aura cannot cover Spell details");
                assertTrue(layers.indexOf(tip) < layers.indexOf(t.view.lookup("#matchResult")));
                assertTrue(layers.indexOf(tip) < layers.indexOf(t.view.lookup("#cardDetails")));
                assertEquals(spells.get(index).def().name(),
                        ((javafx.scene.control.Label) t.view.lookup("#spellHoverName")).getText());
                assertEquals(spells.get(index).def().text(),
                        ((javafx.scene.control.Label) t.view.lookup("#spellHoverEffect")).getText());
                var bounds = tip.getBoundsInParent();
                assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= t.view.getWidth());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= t.view.getHeight());
                t.view.moveTo(new Point2D(1, 700));
                assertFalse(tip.isVisible(), "leaving the card dismisses its details");
                assertTrue(t.actions.calls.isEmpty(), "hover is read-only");
            });
        }
        FxTesting.run(() -> t.view.moveTo(centre(t.view.handBounds(spells.size() - 1))));
        Thread.sleep(550);
        var hoverImage = FxTesting.call(() -> {
            assertTrue(t.view.lookup("#spellHoverDetails").isVisible());
            return t.view.snapshot(null, null);
        });
        var hoverPath = java.nio.file.Path.of("target/spell-hover-qa/rightmost-spell-with-king.png");
        java.nio.file.Files.createDirectories(hoverPath.getParent());
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(hoverImage, null), "png", hoverPath.toFile());
        FxTesting.run(() -> t.view.moveTo(centre(t.view.handBounds(0))));
        Thread.sleep(550);
        FxTesting.run(() -> {
            assertTrue(t.view.lookup("#spellHoverDetails").isVisible());
            t.view.pressAt(centre(t.view.handBounds(0)));
            assertFalse(t.view.lookup("#spellHoverDetails").isVisible(), "picking up a card dismisses details");
            t.view.moveTo(centre(t.view.handBounds(0)));
            t.view.show(shown(base, true));
        });
        Thread.sleep(550);
        FxTesting.run(() -> {
            assertFalse(t.view.lookup("#spellHoverDetails").isVisible(), "a newer view cancels stale hover details");
            t.view.hidden();
        });
    }

    private static void slowlyDrag(CardTableView view, Point2D from, Point2D to) {
        int steps = Math.max(1, (int) Math.ceil(from.distance(to) / 2));
        for (int step = 1; step <= steps; step++)
            view.dragTo(from.add(to.subtract(from).multiply(step / (double) steps)));
    }

    @Test void smallMouseStepsStartHandDragsAndOnlyServerUpdatesPlayTheCard() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            MatchState state = state(); var view = state.view(state.turn());
            t.view.show(shown(view, true)); t.view.show(shown(view, false));
            int index = 0; while (!view.hand().get(index).def().occupiesSpace()) index++;
            long cardId = view.hand().get(index).id();
            Point2D origin = centre(t.view.handBounds(index));
            t.view.pressAt(origin);
            for (int dx = 2; dx <= 10; dx += 2) t.view.dragTo(origin.add(dx, 0));
            assertTrue(t.view.lookup("#draggedCardLayer").isVisible(), "slow events must accumulate beyond the drag threshold");
            var layer = t.view.lookup("#draggedCardLayer"); assertTrue(layer.isMouseTransparent());
            var children = t.view.getChildrenUnmodifiable();
            assertTrue(children.indexOf(layer) > children.indexOf(t.view.lookup("#kingControls")));
            assertTrue(children.indexOf(layer) < children.indexOf(t.view.lookup("#matchResult")));
            Point2D target = centre(t.view.slotBounds(TableZone.OWN, 0));
            slowlyDrag(t.view, origin.add(10, 0), target); t.view.releaseAt(target);
            assertEquals(List.of(PlayCard.place(cardId, 0)), t.actions.calls);
            assertTrue(view.you().row().cards().isEmpty(), "a visual drag applies no gameplay locally");
            assertFalse(layer.isVisible());
            var accepted = state.apply(state.turn(), PlayCard.place(cardId, 0));
            t.view.show(shown(accepted.view(view.seat()), false));
            assertTrue(t.view.animating(), "the server's accepted play still runs its card animation");
            t.view.hidden();
        });
    }

    @Test void holdingLiftsTheHandCardAndCancelledDragEasesBackInsteadOfTeleporting() throws Exception {
        Table[] holder = new Table[1]; Rectangle[] resting = new Rectangle[1]; int[] index = new int[1];
        FxTesting.run(() -> {
            var t = holder[0] = table(new ClientSettings(), true);
            var state = state(); var view = state.view(state.turn());
            t.view.show(shown(view, true)); t.view.show(shown(view, false));
            index[0] = view.hand().size() - 1;
            resting[0] = t.view.handBounds(index[0]); t.view.pressAt(centre(resting[0]));
        });
        Thread.sleep(200);
        var image = FxTesting.call(() -> {
            var t = holder[0];
            assertTrue(t.view.handBounds(index[0]).y < resting[0].y, "press wakes the lift without any mouse movement");
            Point2D origin = centre(resting[0]);
            for (int dx = 2; dx <= 10; dx += 2) t.view.dragTo(origin.add(dx, 0));
            var king = t.view.lookup("#yourKing0");
            var bounds = t.view.sceneToLocal(king.localToScene(king.getBoundsInLocal()));
            t.view.dragTo(new Point2D(bounds.getCenterX(), bounds.getCenterY()));
            assertTrue(t.view.lookup("#draggedCardLayer").isVisible());
            return t.view.snapshot(null, null);
        });
        var path = java.nio.file.Path.of("target/drag-qa/drag-over-king.png");
        java.nio.file.Files.createDirectories(path.getParent());
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", path.toFile());
        FxTesting.run(() -> {
            var t = holder[0]; Point2D outside = new Point2D(80, 80);
            t.view.dragTo(outside); t.view.releaseAt(outside);
            var returning = t.view.handBounds(index[0]);
            assertEquals(outside.getX(), returning.getCenterX(), 1);
            assertEquals(outside.getY(), returning.getCenterY(), 1);
            assertTrue(t.actions.calls.isEmpty());
        });
        Thread.sleep(1500);
        FxTesting.run(() -> {
            var t = holder[0]; assertEquals(resting[0], t.view.handBounds(index[0]));
            t.view.hidden();
        });
    }

    @Test void slowRowDragsAndEscapeHiddenOrFreshPressCannotLeaveAGhostGesture() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true); var view = rowView();
            t.view.show(shown(view, true)); t.view.show(shown(view, false));
            var cards = view.you().row().cards(); int from = cards.get(0).slot(), to = cards.get(1).slot();
            Rectangle source = t.view.slotBounds(TableZone.OWN, from), target = t.view.slotBounds(TableZone.OWN, to);
            Point2D origin = centre(source); t.view.pressAt(origin);
            for (int dx = 2; dx <= 10; dx += 2) t.view.dragTo(origin.add(dx, 0));
            assertTrue(t.view.lookup("#draggedCardLayer").isVisible());
            t.view.releaseAt(centre(target));
            assertEquals(target, t.view.slotBounds(TableZone.OWN, from)); assertTrue(t.actions.calls.isEmpty());
            Point2D hand = centre(t.view.handBounds(0)); t.view.pressAt(hand); t.view.dragTo(hand.add(10, 0));
            t.view.keyPressed(key(javafx.scene.input.KeyCode.ESCAPE));
            assertFalse(t.view.lookup("#draggedCardLayer").isVisible()); assertFalse(t.view.confirmShowing());
            t.view.show(shown(view, true));
            hand = centre(t.view.handBounds(0)); t.view.pressAt(hand); t.view.dragTo(hand.add(10, 0));
            t.view.hidden(); t.view.shown(0); t.view.dragTo(hand.add(50, 0));
            assertFalse(t.view.lookup("#draggedCardLayer").isVisible(), "a hidden table discards its gesture");
            t.view.pressAt(hand); t.view.dragTo(hand.add(10, 0));
            t.view.pressAt(new Point2D(2, 2)); t.view.dragTo(hand);
            assertFalse(t.view.lookup("#draggedCardLayer").isVisible(), "a fresh press replaces a missed-release gesture");
            assertTrue(t.actions.calls.isEmpty()); t.view.hidden();
        });
    }

    @Test void cardsStillHiddenInTheOpeningDealCannotStartAnInvisibleDrag() throws Exception {
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true); var state = state();
            t.view.show(shown(state.view(state.turn()), false));
            Point2D last = centre(t.view.handBounds(state.view(state.turn()).hand().size() - 1));
            assertNull(t.view.hitAt(last), "a hand position is not a card until that card is visible");
            t.view.pressAt(last); slowlyDrag(t.view, last, last.add(10, -10));
            assertFalse(t.view.lookup("#draggedCardLayer").isVisible());
            assertTrue(t.actions.calls.isEmpty()); t.view.hidden();
            t.view.show(shown(state.view(state.turn()), true)); t.view.shown(0);
            Point2D hand = centre(t.view.handBounds(0)); t.view.pressAt(hand); t.view.dragTo(hand.add(10, 0));
            assertTrue(t.view.lookup("#draggedCardLayer").isVisible());
            t.view.show(shown(state.forfeit(state.turn().other()).view(state.turn()), true));
            assertFalse(t.view.lookup("#draggedCardLayer").isVisible(), "a finished match cancels any held card");
            t.view.hidden();
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
        while (state.view(Seat.FIRST).you().row().cards().size() < 2 || state.turn() != Seat.FIRST) {
            state = deckduel.TestMatches.ready(state);
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        }
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
        while (state.view(Seat.FIRST).you().row().cards().size() < 2 || state.turn() != Seat.FIRST) {
            state = deckduel.TestMatches.ready(state);
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        }
        return state.view(Seat.FIRST);
    }

    @Test void theOpponentsReplyWaitsUntilYourPlayHasBeenShown() throws Exception {
        MatchState start = state();
        Seat you = start.turn();
        var unit = start.view(you).hand().stream().filter(card -> card.def().occupiesSpace()).findFirst().orElseThrow();
        MatchState mine = start.apply(you, new PlayCard(unit.id(), 0, null));
        MatchState reply = mine.apply(you.other(), new deckduel.core.card.SkipTurn());
        Table[] holder = new Table[1];
        FxTesting.run(() -> {
            var t = holder[0] = table(new ClientSettings(), true);
            t.view.show(shown(start.view(you), true));
            t.view.show(shown(start.view(you), false)); // Enable motion after setup, without an opening deal.
            t.view.show(shown(mine.view(you), false));
            assertTrue(t.view.animating(), "your own play is staged at once");
            assertEquals(mine.view(you).revision(), t.view.shownRevision());
            t.view.show(shown(reply.view(you), false));
            assertEquals(mine.view(you).revision(), t.view.shownRevision(), "the reply waits for your play");
        });
        long deadline = System.currentTimeMillis() + 20_000;
        int[] revision = {-1};
        while (System.currentTimeMillis() < deadline) {
            FxTesting.run(() -> revision[0] = holder[0].view.shownRevision());
            if (revision[0] == reply.view(you).revision()) break;
            Thread.sleep(100);
        }
        assertEquals(reply.view(you).revision(), revision[0], "then the reply is shown");
        FxTesting.run(() -> {
            var t = holder[0];
            t.view.show(shown(mine.view(you), false));
            t.view.show(shown(start.view(you), true));
            assertEquals(start.view(you).revision(), t.view.shownRevision(), "reduced motion never waits");
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

    @Test void castingASpellThatNeedsNoTargetShowsAButtonThatResolvesIt() throws Exception {
        CardDef drawTwo = DECK.stream().filter(card -> card.id().equals("draw-two")).findFirst().orElseThrow();
        CardInstance spell = new CardInstance(90_001, drawTwo);
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(spellView(List.of(spell), List.of()), true));
            click(t.view, t.view.handBounds(0));
            assertTrue(t.actions.calls.isEmpty(), "selecting a Spell sends nothing");
            click(t.view, t.view.castBounds());
            assertEquals(List.of(PlayCard.cast(spell.id(), null)), t.actions.calls);
        });
    }

    @Test void aSpellThatTakesATargetAsksForOneThroughItsButtonBeforeItCasts() throws Exception {
        CardDef destroy = DECK.stream().filter(card -> card.id().equals("destroy-unit")).findFirst().orElseThrow();
        CardDef knight = DECK.stream().filter(card -> card.id().equals("ashkar-knight")).findFirst().orElseThrow();
        CardInstance spell = new CardInstance(90_002, destroy);
        CardInstance enemy = new CardInstance(90_003, knight);
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(spellView(List.of(spell), List.of(RowCard.placed(2, enemy))), true));
            click(t.view, t.view.handBounds(0));
            // Selecting the Spell alone does not arm its targets.
            click(t.view, t.view.rowCardBounds(TableZone.OPPONENT, 2));
            assertTrue(t.actions.calls.isEmpty(), "targets wait for the Cast button");
            click(t.view, t.view.castBounds());
            assertTrue(t.actions.calls.isEmpty(), "the button asks for a target instead of casting");
            click(t.view, t.view.rowCardBounds(TableZone.OPPONENT, 2));
            assertEquals(List.of(PlayCard.cast(spell.id(), enemy.id())), t.actions.calls);
        });
    }

    /** A view in the recipient's turn with a chosen hand and enemy row, for Spell casting tests. */
    private static PlayerView spellView(List<CardInstance> hand, List<RowCard> enemy) {
        Row enemyRow = new Row(enemy);
        SideView you = new SideView(Row.EMPTY, Scoring.score(Row.EMPTY), 0, 0, hand.size(), 20, 0, 0);
        SideView them = new SideView(enemyRow, Scoring.score(enemyRow), 0, 0, 5, 20, 0, 0);
        return new PlayerView(Seat.FIRST, 1, MatchPhase.IN_PROGRESS, 1, Seat.FIRST, you, them, hand,
                List.of(), List.of(), null, null, List.of(), List.of());
    }

    /** A round-one view in which the starting player is choosing which Reconnaissance card to keep. */
    private static PlayerView reconnoitring() {
        for (long seed = 1; seed < 100; seed++) {
            MatchState state = deckduel.TestMatches.start(DECK, DECK, seed);
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

    @Test void aPointerLeftOnAKeptReconnaissanceCardDoesNotBreakTheTable() throws Exception {
        PlayerView looking = reconnoitring();
        PlayerView kept = new PlayerView(looking.seat(), looking.revision() + 1, looking.phase(), looking.round(), looking.turn(),
                looking.you(), looking.opponent(), looking.hand(), looking.rounds(), looking.events(), looking.outcome(),
                looking.lastPlay(), List.of(), looking.revealed());
        FxTesting.run(() -> {
            var t = table(new ClientSettings(), true);
            t.view.show(shown(looking, true));
            t.view.moveTo(centre(t.view.lookBounds(1, looking.looking().size())));
            assertNotNull(t.view.detailText(), "the pointer rests on the second choice");
            t.view.show(shown(kept, true));
            assertNull(t.view.detailText(), "the remembered pointer outlives the emptied choice without failing");
        });
    }

    @Test void spiedCardsCanBeEnlargedAndDisappearFromTheInspectorWhenTheyLeaveTheEnemyHand() throws Exception {
        MatchState spied = null;
        for (long seed = 1; seed <= 100 && spied == null; seed++) {
            MatchState candidate = deckduel.TestMatches.start(DECK, DECK, seed);
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
            while (state.phase() != MatchPhase.FINISHED) {
                state = deckduel.TestMatches.ready(state);
                state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
            }
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
            state = deckduel.TestMatches.ready(state);
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
            t.view.show(shown(before, true));
            t.view.show(shown(after, false));
            assertTrue(t.view.rail().description().contains("Your score " + after.you().score().total() + "."),
                    "the true score is available at once");
            assertTrue(t.view.animating(), "the play is staged");
            assertTrue(t.cues.isEmpty(), "sounds follow the staging, not the arrival of the update");
        });
        Thread.sleep(Math.round((PlayAnimator.FLIGHT + trace.finishAt + 600) * Staging.TEMPO));
        FxTesting.run(() -> {
            var cues = holder[0].cues;
            // The baseline hand is already dealt; the accepted card starts with its flight.
            assertEquals(ScoringSounds.Cue.Kind.WHOOSH, cues.get(0).kind());
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

    @Test void leavingTheTableStopsTheRemainingDealSounds() throws Exception {
        Table t = FxTesting.call(() -> {
            var table = table(new ClientSettings(), true);
            table.view.show(shown(state().view(Seat.FIRST), false));
            assertTrue(table.view.animating());
            table.view.hidden();
            assertFalse(table.view.animating(), "a hidden table must stop performing the deal");
            return table;
        });
        int before = t.cues.size();
        Thread.sleep(900);
        FxTesting.run(() -> assertEquals(before, t.cues.size(), "no delayed table cues in the menu"));
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
