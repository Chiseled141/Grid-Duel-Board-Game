package deckduel.core.card;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import org.junit.jupiter.api.Test;

class PlannerBotTest {
    /** Measured outcome of the hard bot against the easy one on the bundled catalog, both seats, 200 seeded matches. */
    @Test void plannerBeatsTheGreedyBotOverSeededMatchesWithSwappedSeats() {
        var catalog = CatalogLoader.loadBundled();
        var deck = DeckRules.deck(catalog);
        int planner = 0, greedy = 0;
        for (int i = 0; i < 200; i++) {
            Seat plannerSeat = i % 2 == 0 ? Seat.FIRST : Seat.SECOND;
            MatchState state = MatchState.start(deck, deck, i);
            while (state.phase() != MatchPhase.FINISHED) {
                Seat seat = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
                PlayerView view = state.view(seat);
                state = state.apply(seat, seat == plannerSeat ? PlannerBot.choose(view) : GreedyBot.choose(view));
            }
            Seat winner = state.outcome().winner();
            if (winner == plannerSeat) planner++; else if (winner != null) greedy++;
        }
        System.out.println("PlannerBot " + planner + " wins, GreedyBot " + greedy + " wins, of 200 seeded matches");
        assertTrue(planner > greedy, "planner " + planner + " vs greedy " + greedy);
    }

    @Test void equivalentEmptySpacesAndDuplicateCardsAreConsideredOnce() {
        var soldier = Cards.unit("soldier", 2, "N1", "Pikemen");
        var hand = java.util.List.of(Cards.copy(soldier), Cards.copy(soldier));
        var board = new Rules.Board(Row.EMPTY, Row.EMPTY);
        assertEquals(1, PlannerBot.candidates(hand, board).size());
    }
}
