package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.client.ScoringSounds;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.PlayRecord;
import deckduel.core.card.Seat;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

class PlayAnimatorTest {
    private static final PlayAnimator.Layout LAYOUT = new PlayAnimator.Layout() {
        public Rectangle slot(TableZone zone, int slot) { return new Rectangle(250 + slot * 110, zone == TableZone.OWN ? 330 : 90, 100, 150); }
        public Rectangle hand(List<deckduel.core.card.CardInstance> hand, long cardId) {
            int index = 0;
            while (hand.get(index).id() != cardId) index++;
            return new Rectangle(250 + index * 110, 550, 100, 150);
        }
        public Rectangle landmark(TableZone zone, int index) {
            return new Rectangle(1030 + index * 110, zone == TableZone.OWN_LANDMARK ? 330 : 90, 100, 150);
        }
        public Rectangle pile(TableZone zone) { return new Rectangle(1150, zone == TableZone.OWN ? 334 : 94, 54, 81); }
        public Rectangle stage() { return new Rectangle(640, 220, 125, 188); }
        public Rectangle opponentHand() { return new Rectangle(1040, 20, 22, 33); }
        public Rectangle table() { return new Rectangle(250, 0, 900, 720); }
        public java.awt.geom.Point2D pocket(ScoreRail.Pocket pocket) {
            return new java.awt.geom.Point2D.Double(pocket == ScoreRail.Pocket.COMBOS ? 190 : 60, pocket == ScoreRail.Pocket.OPPONENT ? 100 : 350);
        }
    };

    /** The first card play in a seeded match that satisfies {@code wanted}, as the states before and after it. */
    private static MatchState[] find(BiPredicate<MatchState, MatchState> wanted) {
        return findMove((move, before, after) -> move instanceof deckduel.core.card.PlayCard && wanted.test(before, after));
    }

    interface MoveTest { boolean test(deckduel.core.card.Move move, MatchState before, MatchState after); }

    /** The first move of any kind in a seeded bot match that satisfies {@code wanted}. */
    private static MatchState[] findMove(MoveTest wanted) {
        var catalog = CatalogLoader.loadBundled();
        var deck = DeckRules.deck(catalog);
        for (long seed = 1; seed < 300; seed++) {
            MatchState state = MatchState.start(deck, deck, catalog.landmarks(), seed);
            while (state.phase() != MatchPhase.FINISHED) {
                MatchState before = state;
                Seat seat = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
                var move = GreedyBot.choose(state.view(seat));
                state = state.apply(seat, move);
                if (before.phase() == MatchPhase.IN_PROGRESS && wanted.test(move, before, state)) return new MatchState[]{before, state};
            }
        }
        throw new AssertionError("no such move in the seeded matches");
    }

    /**
     * Performs a plan on a JavaFX layer, posing every step at its start, middle and last frame,
     * so each effect builds and updates its nodes; returns the cues the sequence played.
     */
    private static List<ScoringSounds.Cue> performThrough(Staging staging) throws Exception {
        List<ScoringSounds.Cue> cues = new ArrayList<>();
        FxTesting.run(() -> {
            Pane layer = new Pane();
            layer.resize(1280, 720);
            var effects = new TableEffects(layer, new ScoringSounds(() -> true, cues::add));
            effects.perform(staging, 1);
            long start = System.nanoTime();
            for (Staging.Step step : staging.steps())
                for (double at : new double[]{step.at(), step.at() + step.duration() / 2.0, step.end() - 1})
                    effects.tick(start + Math.round(at * Staging.TEMPO * 1e6));
            assertFalse(effects.tick(start + Math.round((staging.length() + 1) * Staging.TEMPO * 1e6)), "the sequence ends");
            assertTrue(layer.getChildren().isEmpty(), "finished effects leave no nodes behind");
        });
        return cues;
    }

    @Test void aPlacedCardStaysHiddenOnTheRowUntilItLands() throws Exception {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().actor() == Seat.FIRST && after.view(Seat.FIRST).lastPlay().card().def().occupiesSpace());
        PlayRecord play = step[1].view(Seat.FIRST).lastPlay();
        var plan = PlayAnimator.play(step[0].view(Seat.FIRST), step[1].view(Seat.FIRST), "bob", LAYOUT);
        assertEquals(PlayAnimator.FLIGHT, plan.scoringDelay(), "scoring waits for the landing");
        assertTrue(plan.staging().hiddenOnRow(play.card().id(), 0));
        assertTrue(plan.staging().hiddenOnRow(play.card().id(), PlayAnimator.FLIGHT + PlayAnimator.LAND - 1));
        assertFalse(plan.staging().hiddenOnRow(play.card().id(), PlayAnimator.FLIGHT + PlayAnimator.LAND), "the real card shows once it has landed");
        assertTrue(plan.staging().steps().stream().anyMatch(s -> s.effect() instanceof Staging.Flight), "the card is carried, not teleported");
        assertTrue(plan.staging().steps().stream().anyMatch(s -> s.effect() instanceof Staging.Impact && s.at() == PlayAnimator.FLIGHT),
                "a soft ring and a little dust as it lands");
        List<ScoringSounds.Cue> cues = performThrough(plan.staging());
        assertEquals(ScoringSounds.Cue.Kind.WHOOSH, cues.get(0).kind(), "the card is heard leaving the hand first");
    }

    @Test void aRemovalWaitsForTheBoltToStrikeBeforeScoring() throws Exception {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().removed() != null);
        var plan = PlayAnimator.play(step[0].view(Seat.FIRST), step[1].view(Seat.FIRST), "bob", LAYOUT);
        int hit = PlayAnimator.RISE + PlayAnimator.CHARGE + PlayAnimator.BOLT;
        assertTrue(plan.scoringDelay() > hit, "the score changes only after the impact and a short hit-stop");
        assertTrue(plan.staging().steps().stream().anyMatch(s -> s.effect() instanceof Staging.Shatter && s.at() == hit));
        assertTrue(plan.staging().shakeAmplitude(hit + 1) > 0, "the blow shakes the table");
        assertTrue(performThrough(plan.staging()).stream().anyMatch(cue -> cue.kind() == ScoringSounds.Cue.Kind.IMPACT));
    }

    @Test void sabotageStrikesTheEnemyLandmarkWithAHammerAndCracksItWithoutDestroyingIt() throws Exception {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().card().def().effect() instanceof deckduel.core.card.Effect.Sabotage
                && after.view(Seat.FIRST).lastPlay().targetId() != null);
        var plan = PlayAnimator.play(step[0].view(Seat.FIRST), step[1].view(Seat.FIRST), "bob", LAYOUT);
        var steps = plan.staging().steps();
        int strike = PlayAnimator.RISE + PlayAnimator.CHARGE + PlayAnimator.SWING;
        assertTrue(steps.stream().anyMatch(s -> s.effect() instanceof Staging.Hammer h && s.at() + h.strike() == strike),
                "a hammer swings in and strikes");
        assertTrue(steps.stream().anyMatch(s -> s.effect() instanceof Staging.Cracks && s.at() == strike), "it cracks where it was struck");
        assertTrue(steps.stream().noneMatch(s -> s.effect() instanceof Staging.Shatter), "Landmarks are never destroyed");
        long target = step[1].view(Seat.FIRST).lastPlay().targetId();
        assertTrue(plan.staging().hiddenOnRow(target, strike), "the struck Landmark is drawn by the effect until the cracks finish");
        assertFalse(plan.staging().hiddenOnRow(target, strike + PlayAnimator.CRACK), "then it shows, shaded as sabotaged");
        assertTrue(plan.scoringDelay() > strike, "the score changes after the blow");
        long impacts = performThrough(plan.staging()).stream().filter(cue -> cue.kind() == ScoringSounds.Cue.Kind.IMPACT).count();
        assertEquals(1, impacts);
    }

    /** A Spell's own effect, the step no other Spell stages. */
    private record Signature(String spell, java.util.function.Predicate<PlayRecord> cast, Class<? extends Staging.Effect> effect) { }

    @Test void everySpellHasItsOwnSignatureAndOnlyDestroyFiresTheBolt() throws Exception {
        List<Signature> signatures = List.of(
                new Signature("destroy-unit", play -> play.removed() != null, Staging.Shatter.class),
                new Signature("disable-points", play -> play.targetId() != null, Staging.Lash.class),
                new Signature("disable-tags", play -> play.targetId() != null, Staging.Sigil.class),
                new Signature("clear-disable", play -> play.targetId() != null, Staging.Beam.class),
                new Signature("sabotage", play -> play.targetId() != null, Staging.Hammer.class),
                new Signature("random-discard", play -> play.handCard() != null, Staging.Burn.class),
                new Signature("random-steal", play -> play.handCard() != null, Staging.Tether.class),
                new Signature("draw-two", play -> true, Staging.Wellspring.class),
                new Signature("exchange", play -> play.handCard() != null, Staging.Vortex.class),
                new Signature("reconnaissance", play -> true, Staging.Peek.class),
                new Signature("spy", play -> true, Staging.Eye.class));
        assertEquals(signatures.size(), signatures.stream().map(Signature::effect).distinct().count(), "no two Spells share a signature");
        for (Signature signature : signatures) {
            MatchState[] step = find((before, after) -> {
                PlayRecord play = after.view(Seat.FIRST).lastPlay();
                return after.round() == before.round() && play.card().def().id().equals(signature.spell()) && signature.cast().test(play);
            });
            // Staged for the caster and for the opponent watching it.
            for (Seat viewer : Seat.values()) {
                var plan = PlayAnimator.play(step[0].view(viewer), step[1].view(viewer), "bob", LAYOUT);
                var steps = plan.staging().steps();
                assertTrue(steps.stream().anyMatch(s -> signature.effect().isInstance(s.effect())), signature.spell() + " shows its signature");
                assertEquals(signature.spell().equals("destroy-unit"), steps.stream().anyMatch(s -> s.effect() instanceof Staging.Bolt),
                        signature.spell() + ": only Destroy fires the bolt");
                for (Signature other : signatures)
                    if (other != signature)
                        assertTrue(steps.stream().noneMatch(s -> other.effect().isInstance(s.effect())),
                                signature.spell() + " does not borrow " + other.spell() + "'s effect");
                performThrough(plan.staging());
            }
        }
    }

    @Test void aReplacedUnitIsTornApartInsteadOfGoingToTheDiscardPile() throws Exception {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().replaced() != null);
        PlayRecord play = step[1].view(Seat.FIRST).lastPlay();
        for (Seat seat : Seat.values()) {
            var staging = PlayAnimator.play(step[0].view(seat), step[1].view(seat), "bob", LAYOUT).staging();
            var steps = staging.steps();
            assertTrue(steps.stream().anyMatch(s -> s.effect() instanceof Staging.Tear t && t.card() == play.replaced().def()),
                    "both players see the replaced Unit torn");
            assertTrue(steps.stream().noneMatch(s -> s.effect() instanceof Staging.Eject e && e.card() == play.replaced().def()),
                    "it leaves the game rather than flying to the discard pile");
            assertTrue(performThrough(staging).stream().anyMatch(cue -> cue.kind() == ScoringSounds.Cue.Kind.RIP), "the tear is heard");
        }
    }

    @Test void anExchangedCardSpiralsIntoTheVortexAndTheNewCardsComeOutOfIt() {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().card().def().id().equals("exchange") && after.view(Seat.FIRST).lastPlay().handCard() != null);
        PlayRecord play = step[1].view(Seat.FIRST).lastPlay();
        Seat caster = play.actor(), watcher = caster.other();
        var own = PlayAnimator.play(step[0].view(caster), step[1].view(caster), "bob", LAYOUT).staging().steps();
        assertTrue(own.stream().anyMatch(s -> s.effect() instanceof Staging.Spiral sp && sp.card() == play.handCard().def()));
        assertTrue(own.stream().noneMatch(s -> s.effect() instanceof Staging.Eject e && e.card() == play.handCard().def()),
                "the given-up card goes into the vortex, not to the discard pile");
        assertTrue(own.stream().anyMatch(s -> s.effect() instanceof Staging.Flight f && f.glide() && f.from().equals(LAYOUT.stage())),
                "the new cards come out of the vortex");
        var seen = PlayAnimator.play(step[0].view(watcher), step[1].view(watcher), "bob", LAYOUT).staging().steps();
        long arrivals = seen.stream().filter(s -> s.effect() instanceof Staging.Flight f && f.face() == null).count();
        assertEquals(play.drawn(), arrivals, "the watcher sees each drawn card arrive face down");
    }

    @Test void scoringChipsFlyToTheRailInTurnAndTheScoreSettlesAfterTheLastLands() throws Exception {
        MatchState[] step = find((before, after) -> after.round() == before.round()
                && after.view(Seat.FIRST).lastPlay().actor() == Seat.FIRST
                && after.view(Seat.FIRST).you().score().pairs().size() > before.view(Seat.FIRST).you().score().pairs().size() + 1);
        var before = step[0].view(Seat.FIRST);
        var after = step[1].view(Seat.FIRST);
        ScoringTrace trace = ScoringTrace.between(before, after);
        Staging staging = new Staging();
        List<ScoreRail.Tally> tallies = PlayAnimator.combos(staging, trace, 0, LAYOUT);
        var own = trace.links.stream().filter(link -> link.zone() == TableZone.OWN).toList();
        var combos = tallies.stream().filter(t -> t.pocket() == ScoreRail.Pocket.COMBOS).toList();
        assertEquals(own.stream().map(link -> (long) link.pair().points()).toList(), combos.stream().map(ScoreRail.Tally::points).toList(),
                "each new pair's bonus is carried to the Combos well");
        assertTrue(combos.stream().map(ScoreRail.Tally::atMillis).sorted().distinct().count() == combos.size(), "they land one after another");
        long units = after.you().score().units() - before.you().score().units();
        assertEquals(units, tallies.stream().filter(t -> t.pocket() == ScoreRail.Pocket.UNITS).mapToLong(ScoreRail.Tally::points).sum(),
                "the Units chip carries the row's true change");
        assertTrue(tallies.stream().allMatch(t -> t.atMillis() < trace.finishAt), "the score settles after the last chip lands");
        assertTrue(staging.steps().stream().anyMatch(s -> s.effect() instanceof Staging.Chip c && c.big() == (c.tone() == Staging.Tone.GOLD)));
        var tallyCues = performThrough(staging).stream().filter(cue -> cue.kind() == ScoringSounds.Cue.Kind.TALLY).toList();
        assertEquals(tallies.size(), tallyCues.size(), "every chip chinks as it lands");
        assertTrue(tallyCues.get(1).step() > tallyCues.get(0).step(), "each chink a little higher");
    }

    @Test void manualDrawsGlideFromTheDeckAndTheOpponentsArriveFaceDown() throws Exception {
        MatchState[] mine = findMove((move, before, after) -> move instanceof deckduel.core.card.DrawCard && before.turn() == Seat.FIRST);
        var own = PlayAnimator.handChanges(mine[0].view(Seat.FIRST), mine[1].view(Seat.FIRST), LAYOUT);
        assertNotNull(own);
        assertTrue(own.staging().steps().stream().anyMatch(s -> s.effect() instanceof Staging.Flight f && f.glide() && f.face() != null));
        var theirs = PlayAnimator.handChanges(mine[0].view(Seat.SECOND), mine[1].view(Seat.SECOND), LAYOUT);
        assertTrue(theirs.staging().steps().stream().allMatch(s -> s.effect() instanceof Staging.Flight f && f.face() == null),
                "the opponent sees only a card back move to the drawer's hand");
        performThrough(own.staging());
    }

    @Test void theRoundEndingPlaySweepsTheTableAndDealsTheNewHand() throws Exception {
        MatchState[] step = find((before, after) -> after.round() != before.round());
        var view = step[1].view(Seat.FIRST);
        var plan = PlayAnimator.play(step[0].view(Seat.FIRST), view, "bob", LAYOUT);
        assertEquals(10, view.hand().size());
        assertTrue(view.hand().stream().allMatch(card -> plan.staging().hiddenInHand(card.id(), 0)), "the new hand arrives card by card");
        assertTrue(plan.staging().steps().stream().anyMatch(s -> s.effect() instanceof Staging.Banner), "the next round is announced");
        performThrough(plan.staging());
    }

    @Test void aNewGameOpensWithTheRoundBannerAndADeal() throws Exception {
        var view = find((before, after) -> true)[0].view(Seat.FIRST);
        var plan = PlayAnimator.opening(view, LAYOUT);
        assertTrue(view.hand().stream().allMatch(card -> plan.staging().hiddenInHand(card.id(), 0)));
        long flights = plan.staging().steps().stream().filter(s -> s.effect() instanceof Staging.Flight f && f.flip()).count();
        assertEquals(view.hand().size(), flights, "each opening card turns face up on its way into the hand");
        List<ScoringSounds.Cue> cues = performThrough(plan.staging());
        assertEquals(ScoringSounds.Cue.Kind.ROUND, cues.get(0).kind());
        assertEquals(view.hand().size(), cues.stream().filter(cue -> cue.kind() == ScoringSounds.Cue.Kind.DEAL).count());
    }

    @Test void aDrawnCardGlidesSmoothlyAndSettlesExactlyAsTheHandCardSits() {
        Rectangle deck = new Rectangle(1150, 334, 54, 81), hand = new Rectangle(250, 560, 100, 150);
        double tilt = -.05;
        CardMotion.Pose start = CardMotion.glide(deck, hand, 0, PlayAnimator.DRAW, true, tilt);
        CardMotion.Pose end = CardMotion.glide(deck, hand, PlayAnimator.DRAW, PlayAnimator.DRAW, true, tilt);
        assertFalse(start.faceUp(), "it leaves the deck face down");
        assertEquals(deck.getCenterX(), start.x(), 1e-9);
        assertTrue(end.faceUp());
        assertEquals(0, end.turn(), 1e-9);
        assertEquals(hand.getCenterX(), end.x(), 1e-9, "no snap when the real hand card replaces it");
        assertEquals(hand.getCenterY(), end.y(), 1e-9);
        assertEquals(tilt, end.angle(), 1e-9, "it arrives at the fan's tilt");
        assertEquals(1, end.scale(), 1e-9);
        // Eased at both ends: the first and last frames move far less than a frame mid-flight.
        double frame = 1000 / 60.0 / Staging.TEMPO;
        double first = CardMotion.glide(deck, hand, frame, PlayAnimator.DRAW, true, tilt).x() - start.x();
        double middle = CardMotion.glide(deck, hand, PlayAnimator.DRAW / 2.0 + frame, PlayAnimator.DRAW, true, tilt).x()
                - CardMotion.glide(deck, hand, PlayAnimator.DRAW / 2.0, PlayAnimator.DRAW, true, tilt).x();
        double last = end.x() - CardMotion.glide(deck, hand, PlayAnimator.DRAW - frame, PlayAnimator.DRAW, true, tilt).x();
        assertTrue(Math.abs(first) < Math.abs(middle) / 20 && Math.abs(last) < Math.abs(middle) / 20, "no jolt leaving or arriving");
        assertTrue(PlayAnimator.DRAW * Staging.TEMPO >= 1200, "a draw takes over a second on screen");
    }

    @Test void aQuickReplyPlaysAlongsideTheDrawInsteadOfCuttingIt() throws Exception {
        MatchState[] mine = findMove((move, before, after) -> move instanceof deckduel.core.card.DrawCard && before.turn() == Seat.FIRST);
        var drawn = mine[1].view(Seat.FIRST).hand().stream()
                .filter(card -> mine[0].view(Seat.FIRST).hand().stream().noneMatch(old -> old.id() == card.id())).findFirst().orElseThrow();
        var draw = PlayAnimator.handChanges(mine[0].view(Seat.FIRST), mine[1].view(Seat.FIRST), LAYOUT).staging();
        var reply = PlayAnimator.opening(mine[1].view(Seat.FIRST), LAYOUT).staging();
        FxTesting.run(() -> {
            var effects = new TableEffects(new Pane(), new ScoringSounds(() -> false));
            effects.perform(draw, 1);
            effects.perform(reply, 1);
            effects.tick(System.nanoTime());
            assertTrue(effects.hiddenInHand(drawn.id()), "the first sequence's drawn card is still in flight");
            effects.clear();
            assertFalse(effects.running());
        });
    }

    @Test void aFlippingCardTurnsFromItsBackToItsFaceAboutItsVerticalAxis() {
        Rectangle from = new Rectangle(1150, 334, 54, 81), to = new Rectangle(250, 550, 100, 150);
        CardMotion.Pose start = CardMotion.travel(from, to, 0, PlayAnimator.DRAW, true);
        CardMotion.Pose end = CardMotion.travel(from, to, PlayAnimator.DRAW, PlayAnimator.DRAW, true);
        assertFalse(start.faceUp(), "it leaves the deck face down");
        assertEquals(Math.PI, start.turn(), 1e-9);
        assertTrue(end.faceUp());
        assertEquals(0, end.turn(), 1e-3, "and lands flat, face up");
        assertEquals(to.getCenterX(), end.x(), 1);
        assertEquals(to.getCenterY(), end.y(), 1);
    }
}
