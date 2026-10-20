package deckduel.core.card;

import static deckduel.core.card.Cards.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** §5.1–5.5 match flow; each test names its input and checks the expected result. */
class MatchStateTest {
    private static final List<CardDef> UNITS = units();
    private static final CardDef DRAW_TWO = spell("draw-two", new Effect.Draw(2));
    private static final CardDef EXCHANGE = spell("exchange", new Effect.Exchange(1, 2));
    private static final CardDef DISCARD = spell("discard", new Effect.RandomDiscard(1));
    private static final CardDef STEAL = spell("steal", new Effect.RandomSteal(1));
    private static final CardDef RECON = spell("recon", new Effect.Reconnaissance(3));
    private static final CardDef SPY = spell("spy", new Effect.Spy(3));
    private static final CardDef DESTROY = spell("destroy", new Effect.Destroy(TargetSide.OPPONENT));
    private static final CardDef DISABLE_TAGS = spell("disable-tags", new Effect.Disable(TargetSide.OPPONENT, Suppression.TAGS));
    private static final CardDef CLEAR = spell("clear", new Effect.ClearDisable());

    /** The full automatic composition: every Unit and one copy of each Spell. */
    private static List<CardDef> fullDeck() {
        List<CardDef> deck = new ArrayList<>(UNITS);
        deck.addAll(List.of(DRAW_TWO, EXCHANGE, DISCARD, STEAL, RECON, SPY, DESTROY, DISABLE_TAGS, CLEAR));
        return deck;
    }

    private static List<CardDef> unitsOnly(int count) { return UNITS.subList(0, count); }

    private static PlayCard firstPlace(PlayerView view) {
        CardInstance unit = view.hand().stream().filter(card -> card.def().occupiesSpace()).findFirst().orElseThrow();
        return PlayCard.place(unit.id(), Rules.placementSlots(view.you().row()).get(0));
    }

    private static CardInstance inHand(PlayerView view, CardDef def) {
        return view.hand().stream().filter(card -> card.def().equals(def)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ setup

    @Test void theMatchDealsTenCardsToEachPlayerAtOnceWithNoSetupStep() {
        MatchState state = MatchState.start(fullDeck(), fullDeck(), 7);
        assertEquals(MatchPhase.IN_PROGRESS, state.phase());
        assertNotNull(state.turn(), "someone starts round one immediately");
        assertEquals(1, state.round());
        for (Seat seat : Seat.values()) {
            PlayerView view = state.view(seat);
            assertEquals(Rules.HAND_SIZE, view.hand().size());
            assertEquals(fullDeck().size() - Rules.HAND_SIZE, view.you().deckCount());
            assertEquals(Rules.PLAYS_PER_ROUND, view.you().playsRemaining());
        }
    }

    @Test void playingDoesNotDrawAndThreeManualDrawsAreAllowedPerRound() {
        MatchState state = started(fullDeck(), fullDeck(), seedStartedBy(Seat.FIRST));
        MatchState played = state.play(Seat.FIRST, firstPlace(state.view(Seat.FIRST)));
        assertEquals(Rules.HAND_SIZE - 1, played.view(Seat.FIRST).hand().size(), "no automatic replacement draw");
        assertEquals(Seat.SECOND, played.turn());

        MatchState drawing = played;
        for (int i = 1; i <= Rules.MANUAL_DRAWS; i++) {
            drawing = drawing.apply(Seat.SECOND, new DrawCard());
            assertEquals(Seat.SECOND, drawing.turn(), "a manual draw keeps the turn");
            assertEquals(Rules.HAND_SIZE + i, drawing.view(Seat.SECOND).hand().size());
            assertEquals(0, drawing.view(Seat.SECOND).you().playsUsed(), "and uses no play");
        }
        MatchState drawn = drawing;
        assertThrows(RuleViolation.class, () -> drawn.apply(Seat.SECOND, new DrawCard()), "only three per round");
        assertThrows(RuleViolation.class, () -> played.apply(Seat.FIRST, new DrawCard()), "only on your own turn");
    }

    @Test void fullRowsDoNotEndTheRoundAndAFullRowReplacesAUnit() {
        MatchState state = started(unitsOnly(20), unitsOnly(20), seedStartedBy(Seat.FIRST));
        for (int i = 0; i < Row.SIZE * 2; i++) state = state.play(state.turn(), firstPlace(state.view(state.turn())));
        assertTrue(state.view(Seat.FIRST).you().row().isFull());
        assertTrue(state.view(Seat.SECOND).you().row().isFull());
        assertEquals(1, state.round(), "both rows are full, yet the round goes on");
        PlayerView view = state.view(state.turn());
        CardInstance unit = view.hand().get(0);
        assertEquals(Row.SIZE, Rules.placementSlots(view.you().row()).size(), "every space may be replaced");
        MatchState replaced = state.play(state.turn(), PlayCard.place(unit.id(), 3));
        assertEquals(unit.id(), replaced.view(view.seat()).you().row().at(3).card().id());
        assertEquals(1, replaced.view(view.seat()).you().discardCount());
    }

    @Test void theRoundEndsAfterTenPlaysEachThenTenFreshCardsAreDealt() {
        MatchState state = started(fullDeck(), fullDeck(), seedStartedBy(Seat.FIRST));
        while (state.round() == 1) state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        PlayerView next = state.view(Seat.FIRST);
        assertEquals(2, next.round());
        assertEquals(1, next.rounds().size());
        assertEquals(Rules.HAND_SIZE, next.hand().size(), "a fresh ten-card hand, not the old one");
        assertEquals(0, next.you().drawsUsed(), "manual draws reset");
        assertTrue(next.you().row().cards().isEmpty());
        assertEquals(MatchPhase.IN_PROGRESS, next.phase());
    }

    @Test void aPlayerWhoCannotPlayIsSkippedAndKeepsTheirUnusedPlays() {
        // FIRST holds ten Units and an empty deck; SECOND discards FIRST's hand away.
        MatchState state = started(unitsOnly(10), copies(DISCARD, 30), seedStartedBy(Seat.SECOND));
        while (state.view(Seat.FIRST).you().handCount() > 0 && state.phase() == MatchPhase.IN_PROGRESS)
            state = state.apply(state.turn(), GreedyBot.choose(state.view(state.turn())));
        // FIRST may have played their own last card; one more turn for SECOND makes the skip happen.
        if (state.turn() == Seat.SECOND) state = state.apply(Seat.SECOND, GreedyBot.choose(state.view(Seat.SECOND)));
        PlayerView stuck = state.view(Seat.FIRST);
        assertEquals(0, stuck.hand().size());
        assertTrue(stuck.you().playsRemaining() > 0, "plays are kept");
        assertEquals(Seat.SECOND, state.turn(), "the turn passes to the player who can still act");
        assertTrue(stuck.events().stream().anyMatch(e -> e.actor() == Seat.FIRST && e.text().contains("cannot play")));
        int round = state.round();
        while (state.round() == round && state.phase() == MatchPhase.IN_PROGRESS)
            state = state.apply(Seat.SECOND, GreedyBot.choose(state.view(Seat.SECOND)));
        assertNotEquals(round, state.round(), "the round ends once neither player can act");
    }

    @Test void skippingUsesOnePlayAndPreservesCardsScoresAndDraws() {
        MatchState state = started(unitsOnly(20), unitsOnly(20), seedStartedBy(Seat.FIRST));
        state = state.play(Seat.FIRST, firstPlace(state.view(Seat.FIRST)));
        state = state.play(Seat.SECOND, firstPlace(state.view(Seat.SECOND)));
        state = state.apply(Seat.FIRST, new DrawCard());
        PlayerView before = state.view(Seat.FIRST);
        assertTrue(before.canSkip());
        MatchState skipped = state.apply(Seat.FIRST, new SkipTurn());
        PlayerView after = skipped.view(Seat.FIRST);
        assertEquals(before.revision() + 1, after.revision());
        assertEquals(Seat.SECOND, skipped.turn());
        assertEquals(before.you().playsUsed() + 1, after.you().playsUsed());
        assertEquals(before.hand(), after.hand());
        assertEquals(before.you().row(), after.you().row());
        assertEquals(before.you().score(), after.you().score());
        assertEquals(before.you().drawsUsed(), after.you().drawsUsed());
        assertEquals(before.you().deckCount(), after.you().deckCount());
        assertEquals(before.you().discardCount(), after.you().discardCount());
        assertEquals(before.opponent(), after.opponent());
        assertNull(after.lastPlay(), "a skip must not replay the previous card animation");
        assertTrue(after.events().get(after.events().size() - 1).text().contains("skipped"));
        assertEquals(before, state.view(Seat.FIRST), "the old state remains unchanged");
    }

    @Test void skipsCanFinishAllThreeRoundsWithoutDiscardingHands() {
        MatchState state = started(unitsOnly(20), unitsOnly(20), seedStartedBy(Seat.FIRST));
        for (int round = 1; round <= Rules.ROUNDS; round++) {
            for (int i = 0; i < 2 * Rules.PLAYS_PER_ROUND; i++) {
                assertEquals(round, state.round());
                assertEquals(Rules.HAND_SIZE, state.view(state.turn()).hand().size());
                assertTrue(state.view(state.turn()).canSkip());
                state = state.apply(state.turn(), new SkipTurn());
            }
            assertEquals(round, state.rounds().size(), "bank each round exactly once");
            if (round < Rules.ROUNDS) {
                assertEquals(0, state.view(Seat.FIRST).you().playsUsed());
                assertEquals(0, state.view(Seat.SECOND).you().playsUsed());
                assertEquals(0, state.view(Seat.FIRST).you().drawsUsed());
            }
        }
        assertEquals(MatchPhase.FINISHED, state.phase());
        MatchState finished = state;
        assertFalse(finished.view(Seat.FIRST).canSkip());
        assertThrows(RuleViolation.class, () -> finished.apply(Seat.FIRST, new SkipTurn()));
        assertEquals(state.rounds().stream().mapToLong(RoundResult::firstScore).sum(), state.matchTotal(Seat.FIRST));
    }

    @Test void skipsAreRejectedOnTheOpponentsTurn() {
        MatchState state = started(fullDeck(), fullDeck(), seedStartedBy(Seat.FIRST));
        PlayerView before = state.view(Seat.SECOND);
        assertFalse(before.canSkip());
        assertThrows(RuleViolation.class, () -> state.apply(Seat.SECOND, new SkipTurn()));
        assertEquals(before, state.view(Seat.SECOND));
    }

    @Test void skippingTheLastPlayLeavesOnlyTheOpponentAbleToAct() {
        MatchState state = started(unitsOnly(20), unitsOnly(20), seedStartedBy(Seat.FIRST));
        for (int i = 0; i < 19; i++) state = state.apply(state.turn(), new SkipTurn());
        assertEquals(1, state.round());
        assertEquals(0, state.view(Seat.FIRST).you().playsRemaining());
        assertFalse(state.view(Seat.FIRST).canSkip());
        assertEquals(Seat.SECOND, state.turn());
        assertEquals(1, state.view(Seat.SECOND).you().playsRemaining());
    }

    // ------------------------------------------------------------------ Spells

    @Test void boardSpellsResolveOnTheirTargets() {
        // A known board and hand, resolved through the same Rules the match uses.
        Rules.Board board = new Rules.Board(row(UNITS.get(0)), row(UNITS.get(1), UNITS.get(2)));
        CardInstance destroy = copy(DESTROY), disable = copy(DISABLE_TAGS), clear = copy(CLEAR);
        List<CardInstance> hand = List.of(destroy, disable, clear);
        long enemy = board.opponent().at(0).card().id();
        Rules.Board destroyed = Rules.resolve(destroy, PlayCard.cast(destroy.id(), enemy), hand, board).board();
        assertNull(destroyed.opponent().find(enemy));
        Rules.Board disabled = Rules.resolve(disable, PlayCard.cast(disable.id(), enemy), hand, board).board();
        assertTrue(disabled.opponent().find(enemy).has(Suppression.TAGS));
        assertTrue(Rules.spellTargets(clear, hand, board).isEmpty(), "nothing to clear yet");
        Rules.Board ownDisabled = new Rules.Board(board.own().update(board.own().at(0).with(Suppression.POINTS).with(Suppression.TAGS)),
                board.opponent());
        long own = ownDisabled.own().at(0).card().id();
        Rules.Board cleared = Rules.resolve(clear, PlayCard.cast(clear.id(), own), hand, ownDisabled).board();
        assertTrue(cleared.own().find(own).suppressed().isEmpty(), "both disables are cleared");
        assertThrows(RuleViolation.class, () -> Rules.resolve(destroy, PlayCard.cast(destroy.id(), own), hand, board),
                "Destroy targets an enemy Unit only");
    }

    @Test void drawTwoAndExchangeDrawWithoutUsingManualDraws() {
        List<CardDef> deck = new ArrayList<>(List.of(DRAW_TWO, EXCHANGE));
        deck.addAll(unitsOnly(30));
        long seed = findHolding(deck, Seat.FIRST, DRAW_TWO, EXCHANGE);
        MatchState state = started(deck, fullDeck(), seed);
        PlayerView view = state.view(Seat.FIRST);
        MatchState drew = state.play(Seat.FIRST, PlayCard.cast(inHand(view, DRAW_TWO).id(), null));
        PlayerView after = drew.view(Seat.FIRST);
        assertEquals(Rules.HAND_SIZE - 1 + 2, after.hand().size());
        assertEquals(0, after.you().drawsUsed());
        assertEquals(2, after.lastPlay().drawn());

        CardInstance exchange = inHand(view, EXCHANGE), discard = view.hand().stream().filter(c -> c != exchange && c.def().occupiesSpace()).findFirst().orElseThrow();
        assertThrows(RuleViolation.class, () -> state.play(Seat.FIRST, PlayCard.cast(exchange.id(), exchange.id())), "not itself");
        MatchState exchanged = state.play(Seat.FIRST, PlayCard.cast(exchange.id(), discard.id()));
        PlayerView afterExchange = exchanged.view(Seat.FIRST);
        assertEquals(Rules.HAND_SIZE - 2 + 2, afterExchange.hand().size());
        assertTrue(afterExchange.hand().stream().noneMatch(c -> c.id() == discard.id()));
        assertEquals(2, afterExchange.you().discardCount(), "the Spell and the chosen card");
        assertEquals(discard.id(), afterExchange.lastPlay().handCard().id());
    }

    @Test void randomDiscardAndStealAreSeededServerSideAndDoNothingToAnEmptyHand() {
        List<CardDef> deck = new ArrayList<>(List.of(DISCARD, STEAL));
        deck.addAll(unitsOnly(30));
        long seed = findHolding(deck, Seat.FIRST, DISCARD, STEAL);
        MatchState state = started(deck, fullDeck(), seed);
        PlayerView view = state.view(Seat.FIRST);
        MatchState discarded = state.play(Seat.FIRST, PlayCard.cast(inHand(view, DISCARD).id(), null));
        assertEquals(Rules.HAND_SIZE - 1, discarded.view(Seat.SECOND).hand().size());
        assertEquals(1, discarded.view(Seat.SECOND).you().discardCount());
        assertEquals(discarded.view(Seat.FIRST).lastPlay().handCard(),
                state.play(Seat.FIRST, PlayCard.cast(inHand(view, DISCARD).id(), null)).view(Seat.FIRST).lastPlay().handCard(),
                "the same state picks the same card");
        MatchState stole = state.play(Seat.FIRST, PlayCard.cast(inHand(view, STEAL).id(), null));
        CardInstance taken = stole.view(Seat.FIRST).lastPlay().handCard();
        assertTrue(stole.view(Seat.FIRST).hand().contains(taken), "the stolen card joins the caster's hand");
        assertFalse(stole.view(Seat.SECOND).hand().contains(taken));
        assertEquals(Rules.HAND_SIZE, stole.view(Seat.FIRST).hand().size(), "one played, one stolen");
    }

    @Test void reconnaissanceShowsTheTopThreeOnlyToTheCasterWhoKeepsOne() {
        List<CardDef> deck = new ArrayList<>(List.of(RECON));
        deck.addAll(unitsOnly(30));
        long seed = findHolding(deck, Seat.FIRST, RECON);
        MatchState state = started(deck, fullDeck(), seed);
        PlayerView view = state.view(Seat.FIRST);
        MatchState looking = state.play(Seat.FIRST, PlayCard.cast(inHand(view, RECON).id(), null));
        PlayerView caster = looking.view(Seat.FIRST);
        assertEquals(3, caster.looking().size());
        assertTrue(caster.keeping());
        assertEquals(Seat.FIRST, looking.turn(), "the turn waits for the choice");
        assertTrue(looking.view(Seat.SECOND).looking().isEmpty(), "the opponent never sees them");
        assertEquals(view.you().deckCount() - 3, caster.you().deckCount());
        assertThrows(RuleViolation.class, () -> looking.apply(Seat.FIRST, new DrawCard()), "keep first");
        assertFalse(caster.canSkip());
        assertThrows(RuleViolation.class, () -> looking.apply(Seat.FIRST, new SkipTurn()), "keep before skipping");
        assertThrows(RuleViolation.class, () -> looking.apply(Seat.FIRST, new KeepCard(view.hand().get(0).id())));
        CardInstance keep = caster.looking().get(1);
        MatchState kept = looking.apply(Seat.FIRST, new KeepCard(keep.id()));
        assertTrue(kept.view(Seat.FIRST).hand().contains(keep));
        assertEquals(view.you().deckCount() - 1, kept.view(Seat.FIRST).you().deckCount(), "the other two went to the bottom");
        assertEquals(Seat.SECOND, kept.turn());
        assertEquals(0, kept.view(Seat.FIRST).you().drawsUsed(), "not a manual draw");
    }

    @Test void spyRevealsThreeEnemyCardsToTheCasterOnly() {
        List<CardDef> deck = new ArrayList<>(List.of(SPY));
        deck.addAll(unitsOnly(30));
        long seed = findHolding(deck, Seat.FIRST, SPY);
        MatchState state = started(deck, fullDeck(), seed);
        MatchState spied = state.play(Seat.FIRST, PlayCard.cast(inHand(state.view(Seat.FIRST), SPY).id(), null));
        List<CardInstance> revealed = spied.view(Seat.FIRST).revealed();
        assertEquals(3, revealed.size());
        assertTrue(spied.view(Seat.SECOND).hand().containsAll(revealed), "the cards stay in the enemy hand");
        assertTrue(spied.view(Seat.SECOND).revealed().isEmpty(), "and only the caster learns them");
        assertFalse(visibleIds(spied.view(Seat.FIRST)).contains(unrevealedId(spied)), "no other enemy card reaches the caster");
    }

    // ------------------------------------------------------------------ privacy, replay, ending

    @Test void viewsNeverContainTheOpponentsHandOrDeck() {
        MatchState state = started(fullDeck(), fullDeck(), 99);
        String view = visibleIds(state.view(Seat.FIRST));
        for (CardInstance card : state.view(Seat.SECOND).hand()) assertFalse(view.contains("#" + card.id() + "#"), "opponent hand");
        List<String> fields = java.util.Arrays.stream(PlayerView.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertTrue(fields.stream().noneMatch(name -> name.contains("seed") || name.contains("pile") || name.contains("library")),
                "the view contract has no seed, RNG or deck order: " + fields);
    }

    @Test void sameSeedReplaysIdenticallyAndTheMatchEndsAfterThreeRounds() {
        MatchState a = started(fullDeck(), fullDeck(), 42), b = started(fullDeck(), fullDeck(), 42);
        assertEquals(a.view(Seat.FIRST).hand(), b.view(Seat.FIRST).hand());
        while (a.phase() == MatchPhase.IN_PROGRESS) {
            Move move = GreedyBot.choose(a.view(a.turn()));
            assertEquals(move, GreedyBot.choose(b.view(b.turn())));
            a = a.apply(a.turn(), move);
            b = b.apply(b.turn(), move);
        }
        assertEquals(MatchPhase.FINISHED, a.phase());
        assertEquals(Rules.ROUNDS, a.rounds().size());
        assertEquals(a.outcome(), b.outcome());
        long first = a.rounds().stream().mapToLong(RoundResult::firstScore).sum();
        assertEquals(first, a.matchTotal(Seat.FIRST), "match totals are the banked round scores");
    }

    @Test void forfeitEndsTheMatchImmediately() {
        MatchState state = MatchState.start(fullDeck(), fullDeck(), 3).forfeit(Seat.FIRST);
        assertEquals(MatchPhase.FINISHED, state.phase());
        assertEquals(Seat.SECOND, state.outcome().winner());
        assertTrue(state.outcome().forfeit());
    }

    // ------------------------------------------------------------------ helpers

    /** A seed started by {@code seat} whose opening hand holds the named cards. */
    private static long findHolding(List<CardDef> deck, Seat seat, CardDef... wanted) {
        for (long seed = 1; seed < 20000; seed++) {
            if ((new java.util.Random(seed).nextBoolean() ? Seat.FIRST : Seat.SECOND) != seat) continue;
            MatchState dealt = started(deck, fullDeck(), seed);
            List<CardDef> held = new ArrayList<>(dealt.view(seat).hand().stream().map(CardInstance::def).toList());
            // Each named card counts once, so naming a card twice needs two copies in hand.
            boolean all = true;
            for (CardDef card : wanted) all &= held.remove(card);
            if (all) return seed;
        }
        throw new AssertionError("no such seed");
    }

    /** An enemy hand card the caster was not shown, as it would appear in a serialized view. */
    private static String unrevealedId(MatchState state) {
        List<CardInstance> revealed = state.view(Seat.FIRST).revealed();
        return state.view(Seat.SECOND).hand().stream().filter(card -> !revealed.contains(card))
                .map(card -> "#" + card.id() + "#").findFirst().orElseThrow();
    }

    /** Every card instance id a view carries, in any field, each marked so ids do not match inside other numbers. */
    private static String visibleIds(PlayerView view) {
        StringBuilder ids = new StringBuilder();
        List<CardInstance> cards = new ArrayList<>(view.hand());
        cards.addAll(view.looking());
        cards.addAll(view.revealed());
        for (SideView side : List.of(view.you(), view.opponent())) {
            side.row().cards().forEach(card -> cards.add(card.card()));
        }
        if (view.lastPlay() != null)
            for (CardInstance card : java.util.Arrays.asList(view.lastPlay().card(), view.lastPlay().handCard(),
                    view.lastPlay().replaced(), view.lastPlay().removed())) if (card != null) cards.add(card);
        for (CardInstance card : cards) ids.append('#').append(card.id()).append('#');
        return ids.toString();
    }
}
