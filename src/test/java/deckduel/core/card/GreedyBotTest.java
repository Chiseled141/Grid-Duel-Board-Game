package deckduel.core.card;

import static deckduel.core.card.Cards.*;
import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreedyBotTest {
    @Test void botsDrawKeepAndFinishAWholeMatchWithOnlyLegalMoves() {
        var catalog = CatalogLoader.loadBundled();
        var deck = DeckRules.deck(catalog);
        MatchState state = MatchState.start(deck, deck, 21);
        int moves = 0;
        while (state.phase() != MatchPhase.FINISHED) {
            for (Seat seat : Seat.values()) if (state.awaits(seat)) { state = state.apply(seat, GreedyBot.choose(state.view(seat))); break; }
            assertTrue(++moves <= 3 * (2 * Rules.PLAYS_PER_ROUND + 2 * Rules.MANUAL_DRAWS + 10) + 2, "the match always progresses");
        }
        assertEquals(Rules.ROUNDS, state.rounds().size());
    }

    @Test void botPrefersThePlayThatMostImprovesItsLead() {
        CardDef knight = unit("knight", 5, "N1", "Knights");
        CardDef destroy = spell("destroy", new Effect.Destroy(TargetSide.OPPONENT));
        CardDef footman = unit("footman", 1, "N2", "Footmen");
        Row opponent = row(knight, knight), own = row(footman);
        var hand = List.of(copy(footman), copy(destroy));
        // Two cards for two plays left: the bot plays rather than draws.
        var view = view(own, opponent, hand, Rules.PLAYS_PER_ROUND - 2);
        Move choice = GreedyBot.choose(view);
        assertEquals(hand.get(1).id(), ((PlayCard) choice).cardId(), "destroying a knight swings 14 points; a footman adds 4");
    }

    @Test void botDrawsWhenItsHandIsShorterThanItsPlays() {
        var hand = List.of(copy(unit("a", 1, "N1", "Footmen")));
        assertEquals(new DrawCard(), GreedyBot.choose(view(Row.EMPTY, Row.EMPTY, hand, 0)), "one card for ten plays: draw first");
    }

    /** The bot's own view on its turn, with {@code playsUsed} plays gone and a deck to draw from. */
    private static PlayerView view(Row own, Row opponent, List<CardInstance> hand, int playsUsed) {
        var you = new SideView(own, Scoring.score(own), playsUsed, 0, hand.size(), 20, 0, 0);
        var them = new SideView(opponent, Scoring.score(opponent), 2, 0, 8, 20, 0, 0);
        return new PlayerView(Seat.FIRST, 0, MatchPhase.IN_PROGRESS, 1, Seat.FIRST, you, them,
                hand, List.of(), List.of(), null, null, List.of(), List.of());
    }
}
