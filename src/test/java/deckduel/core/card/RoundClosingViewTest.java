package deckduel.core.card;

import deckduel.cards.CatalogLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundClosingViewTest {
    @Test void finalRowsScoresHandsAndKingCountersAreCapturedBeforeEveryBankAndDeal() {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState state = MatchState.start(deck, deck, 0);
        int endings = 0;
        while (state.phase() != MatchPhase.FINISHED) {
            MatchState before = state;
            Seat actor = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
            Move move = GreedyBot.choose(state.view(actor));
            state = state.apply(actor, move);
            if (state.rounds().size() == before.rounds().size()) {
                assertNull(state.closingRoundView(Seat.FIRST)); continue;
            }
            endings++;
            for (Seat seat : Seat.values()) {
                var closing = state.closingRoundView(seat); var current = state.view(seat);
                assertEquals(before.round(), closing.round()); assertEquals(current.revision(), closing.revision());
                assertEquals(MatchPhase.IN_PROGRESS, closing.phase()); assertNull(closing.turn());
                assertNull(closing.outcome()); assertEquals(before.rounds(), closing.rounds());
                assertEquals(current.rounds().get(endings - 1).scoreOf(seat), closing.you().score().total());
                assertEquals(before.view(seat).you().matchTotal(), closing.you().matchTotal());
                assertEquals(closing.you().matchTotal() + closing.you().score().total(), current.you().matchTotal());
                assertEquals(closing.hand().size(), state.closingRoundView(seat.other()).opponent().handCount());
                if (current.lastPlay() != null) {
                    assertEquals(seat == actor ? current.lastPlay().actorRow() : current.lastPlay().opponentRow(), closing.you().row());
                    int spell = seat == actor && current.lastPlay().card().def().kind() == CardKind.SPELL ? 1 : 0;
                    assertEquals(before.view(seat).you().kings().spellsPlayed() + spell, closing.you().kings().spellsPlayed());
                }
            }
        }
        assertEquals(3, endings);
    }

    @Test void forfeitsDoNotInventAnUnbankedRoundOrChangeResultRules() {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        var state = deckduel.TestMatches.start(deck, deck, 2);
        var forfeited = state.forfeit(Seat.SECOND);
        assertNull(forfeited.closingRoundView(Seat.FIRST));
        assertTrue(forfeited.rounds().isEmpty()); assertTrue(forfeited.outcome().forfeit());
        assertEquals(Seat.FIRST, forfeited.outcome().winner());
    }

    @Test void finalSpellKeepsAurendorsBonusAndDrawnCardsBeforeTheNextRoundResetsThem() {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState state = null;
        for (int seed = 0; state == null; seed++) {
            var candidate = MatchState.start(deck, deck, seed);
            if (!candidate.view(Seat.FIRST).kingOffer().contains(King.AURENDOR)) continue;
            candidate = candidate.apply(Seat.FIRST, new ChooseKing(King.AURENDOR))
                    .apply(Seat.SECOND, new ChooseKing(King.AURENDOR));
            if (candidate.view(candidate.turn().other()).hand().stream().anyMatch(card -> card.def().id().equals("draw-two")))
                state = candidate;
        }
        for (int i = 0; i < 2; i++) {
            var unit = state.view(state.turn()).hand().stream().filter(card -> card.def().occupiesSpace()).findFirst().orElseThrow();
            state = state.apply(state.turn(), PlayCard.place(unit.id(), 0));
        }
        while (state.view(Seat.FIRST).you().playsUsed() + state.view(Seat.FIRST).opponent().playsUsed() < 19)
            state = state.apply(state.turn(), new SkipTurn());
        Seat actor = state.turn();
        var before = state.view(actor);
        var spell = before.hand().stream().filter(card -> card.def().id().equals("draw-two")).findFirst().orElseThrow();
        var next = state.apply(actor, PlayCard.cast(spell.id(), null));
        var closing = next.closingRoundView(actor);
        assertEquals(1, closing.you().kings().spellsPlayed()); assertEquals(3, closing.you().score().kingPoints());
        assertEquals(before.you().score().total() + 3, closing.you().score().total());
        assertEquals(before.hand().size() + 1, closing.hand().size());
        assertEquals(before.you().deckCount() - 2, closing.you().deckCount());
        assertEquals(0, next.view(actor).you().kings().spellsPlayed());
        assertEquals(closing.you().score().total(), next.rounds().get(0).scoreOf(actor));
    }
}
