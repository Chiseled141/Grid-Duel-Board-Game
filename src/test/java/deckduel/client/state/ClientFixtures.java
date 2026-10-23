package deckduel.client.state;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.PlayerView;
import deckduel.core.card.Seat;
import deckduel.net.MatchUpdate;
import deckduel.net.ReplayResponse;
import java.util.ArrayList;
import java.util.List;

/** Real match data for client tests in any package: an opening update and a recorded game. */
public final class ClientFixtures {
    private ClientFixtures() { }

    /** The dealt opening of a match on the bundled catalog, as {@code seat} sees it. */
    public static MatchUpdate update(String id, Seat seat) { return ClientModelTest.update(id, seat); }

    /** The first seat's views through a seeded greedy game, as the server records them, as replay 5 against bob. */
    public static ReplayResponse replay(boolean complete) {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState state = MatchState.start(deck, deck, 7);
        List<PlayerView> frames = new ArrayList<>(List.of(state.view(Seat.FIRST)));
        while (state.phase() != MatchPhase.FINISHED) {
            Seat mover = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
            state = state.apply(mover, GreedyBot.choose(state.view(mover)));
            frames.add(state.view(Seat.FIRST));
        }
        return new ReplayResponse(5, "bob", false, "2026-10-23T09:15:00", complete, frames);
    }
}
