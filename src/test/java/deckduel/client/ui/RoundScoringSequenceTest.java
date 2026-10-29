package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.client.ScoringSounds;
import deckduel.core.card.*;
import deckduel.net.MatchUpdate;
import java.util.List;
import java.util.ArrayList;
import javafx.scene.Scene;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real accepted final moves must finish their scoring before bank/deal/results are presented. */
class RoundScoringSequenceTest {
    private static final List<CardDef> DECK = DeckRules.deck(CatalogLoader.loadBundled());
    private record Ending(MatchState before, MatchState after) { }
    private record Table(CardTableView view, List<Move> moves) { }

    private static Move choose(PlayerView view, CardKind reserve) {
        if (view.choosingKing() || view.keeping()) return GreedyBot.choose(view);
        var desired = view.legalPlays().stream().filter(play -> view.hand().stream()
                .anyMatch(card -> card.id() == play.cardId() && card.def().kind() == reserve)).toList();
        if (view.you().playsRemaining() == 1 && !desired.isEmpty()) return desired.get(0);
        Move move = GreedyBot.choose(view);
        long reserved = view.hand().stream().filter(card -> card.def().kind() == reserve).count();
        if (reserved == 1 && move instanceof PlayCard play && desired.stream().anyMatch(option -> option.cardId() == play.cardId()))
            return view.legalPlays().stream().filter(option -> option.cardId() != play.cardId()).findFirst()
                    .<Move>map(option -> option).orElseGet(SkipTurn::new);
        return move;
    }

    private static Ending ending(int round, CardKind kind) {
        for (int seed = 0; seed < 128; seed++) {
            MatchState state = MatchState.start(DECK, DECK, seed);
            while (state.phase() != MatchPhase.FINISHED) {
                MatchState before = state;
                Seat actor = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
                state = state.apply(actor, choose(state.view(actor), kind));
                if (state.rounds().size() == before.rounds().size() || before.round() != round) continue;
                var finalPlay = state.view(Seat.FIRST).lastPlay();
                var closing = state.closingRoundView(Seat.FIRST);
                if (finalPlay != null && finalPlay.card().def().kind() == kind
                        && (closing.you().score().total() != before.view(Seat.FIRST).you().score().total()
                        || closing.opponent().score().total() != before.view(Seat.FIRST).opponent().score().total()))
                    return new Ending(before, state);
            }
        }
        throw new AssertionError("No scored ending for " + kind);
    }

    private static CardTableView.State update(MatchState state, boolean reduced) {
        return new CardTableView.State(new MatchUpdate("sequence", 1, "you", "opponent", true, true, false,
                false, false, state.view(Seat.FIRST), 0, state.closingRoundView(Seat.FIRST)), false, reduced);
    }

    private static Table table(MatchState before) {
        List<Move> moves = new ArrayList<>();
        var view = new CardTableView(new CardTableView.Actions() {
            public void move(Move move) { moves.add(move); }
            public void rematch() { }
            public void leave() { }
        }, new ScoringSounds(() -> false));
        new Scene(view); view.getScene().getStylesheets().add(FxKit.STYLESHEET);
        view.resize(1280, 720); view.applyCss(); view.layout();
        view.show(update(before, true)); view.shown(0);
        return new Table(view, moves);
    }

    private static void awaitConclusion(CardTableView table, int round, boolean finished, long finalScore) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        boolean sawFinalCountBeforeConclusion = false;
        while (System.currentTimeMillis() < deadline) {
            int status = FxTesting.call(() -> {
                if (table.finishingRound()) {
                    assertFalse(table.resultShowing());
                    assertTrue(table.rail().description().startsWith("Round " + round + " of 3"));
                    if (!table.rail().isAnimating()) {
                        assertEquals(finalScore, table.rail().displayedScore()); return 1;
                    }
                    return 0;
                }
                assertEquals(finished, table.resultShowing());
                assertFalse(table.scoring(), "a result cannot overtake any scoring trace or score count");
                return 2;
            });
            if (status == 1) sawFinalCountBeforeConclusion = true;
            if (status == 2) { assertTrue(sawFinalCountBeforeConclusion, "the final number settles before the conclusion"); return; }
            Thread.sleep(25);
        }
        fail("Conclusion did not follow scoring; final count observed=" + sawFinalCountBeforeConclusion);
    }

    @Test void lastUnitFinishesScoringBeforeTheNextRoundClearsAndDeals() throws Exception {
        var ending = ending(1, CardKind.UNIT);
        Table table = FxTesting.call(() -> {
            var t = table(ending.before()); t.view.show(update(ending.after(), false));
            assertTrue(t.view.finishingRound()); assertTrue(t.view.scoring());
            assertTrue(t.view.rail().description().contains("Match total 0"));
            return t;
        });
        try {
            awaitConclusion(table.view, 1, false, ending.after().closingRoundView(Seat.FIRST).you().score().total());
            FxTesting.run(() -> {
                assertTrue(table.view.rail().description().startsWith("Round 2 of 3"));
                assertTrue(table.view.animating(), "clear/banner/deal starts after final scoring");
                assertTrue(table.moves.isEmpty());
            });
        } finally { FxTesting.run(table.view::hidden); }
    }

    @Test void finalSpellFinishesEffectsAndBothScoresBeforeTheMatchResult() throws Exception {
        var ending = ending(3, CardKind.SPELL);
        Table table = FxTesting.call(() -> {
            var t = table(ending.before()); t.view.show(update(ending.after(), false));
            assertTrue(t.view.finishingRound()); assertFalse(t.view.resultShowing()); return t;
        });
        try {
            awaitConclusion(table.view, 3, true, ending.after().closingRoundView(Seat.FIRST).you().score().total());
            FxTesting.run(() -> {
                assertEquals(ending.after().view(Seat.FIRST).you().score().total(), table.view.rail().displayedScore());
                assertFalse(table.view.rail().isAnimating()); assertTrue(table.moves.isEmpty());
            });
        } finally { FxTesting.run(table.view::hidden); }
    }

    @Test void reducedMotionHiddenScreensAndNewGamesDoNotWaitOnAnObsoleteConclusion() throws Exception {
        var ending = ending(1, CardKind.UNIT);
        FxTesting.run(() -> {
            var t = table(ending.before()); t.view.show(update(ending.after(), false));
            assertTrue(t.view.finishingRound());
            t.view.show(update(ending.after(), true));
            assertFalse(t.view.finishingRound()); assertFalse(t.view.scoring());
            t.view.show(update(ending.before(), true)); t.view.show(update(ending.after(), false));
            assertTrue(t.view.finishingRound()); t.view.hidden();
            assertFalse(t.view.finishingRound()); assertFalse(t.view.scoring());
            assertTrue(t.view.rail().description().startsWith("Round 2 of 3"));
            t.view.shown(0); t.view.show(update(ending.before(), true)); t.view.show(update(ending.after(), false));
            var rematch = deckduel.TestMatches.start(DECK, DECK, 9);
            t.view.show(new CardTableView.State(new MatchUpdate("sequence", 2, "you", "opponent", true,
                    true, false, false, false, rematch.view(Seat.FIRST)), false, true));
            assertFalse(t.view.finishingRound()); assertFalse(t.view.scoring());
            t.view.show(new CardTableView.State(null, false, false));
            assertFalse(t.view.finishingRound()); assertFalse(t.view.scoring()); t.view.hidden();
        });
    }

    @Test void aFinalSkipCannotOvertakeThePreviousUnitsScoreAnimation() throws Exception {
        MatchState state = deckduel.TestMatches.start(DECK, DECK, 0);
        while (state.round() < 3) {
            state = state.apply(state.turn(), new SkipTurn());
            state = deckduel.TestMatches.ready(state);
        }
        while (state.view(Seat.FIRST).you().playsUsed() + state.view(Seat.FIRST).opponent().playsUsed() < 18)
            state = state.apply(state.turn(), new SkipTurn());
        MatchState before = state;
        Seat actor = before.turn();
        CardInstance unit = before.view(actor).hand().stream().filter(card -> card.def().occupiesSpace()).findFirst().orElseThrow();
        MatchState placed = before.apply(actor, PlayCard.place(unit.id(), 0));
        MatchState finished = placed.apply(placed.turn(), new SkipTurn());
        assertNull(finished.view(Seat.FIRST).lastPlay());
        Table table = FxTesting.call(() -> {
            var t = table(before); t.view.show(update(placed, false)); t.view.show(update(finished, false));
            assertEquals(placed.revision(), t.view.shownRevision());
            assertTrue(t.view.scoring()); assertFalse(t.view.resultShowing()); return t;
        });
        try {
            long deadline = System.currentTimeMillis() + 20_000;
            while (!FxTesting.call(table.view::resultShowing)) {
                assertTrue(System.currentTimeMillis() < deadline, "skip result must follow the preceding score");
                Thread.sleep(25);
            }
            FxTesting.run(() -> {
                assertFalse(table.view.scoring()); assertFalse(table.view.rail().isAnimating());
                assertEquals(finished.revision(), table.view.shownRevision()); assertTrue(table.moves.isEmpty());
            });
        } finally { FxTesting.run(table.view::hidden); }
    }
}
