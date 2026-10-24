package deckduel;

import deckduel.core.card.*;
import java.util.List;

/** Explicitly resolves King setup for tests concentrating on ordinary card actions. */
public final class TestMatches {
    private TestMatches() { }
    public static MatchState start(List<CardDef> a, List<CardDef> b, long seed) {
        return ready(MatchState.start(a, b, seed));
    }
    public static MatchState ready(MatchState state) {
        if (state.phase() != MatchPhase.KING_SELECTION) return state;
        // Avoid a pending manual-draw choice in tests focused on pre-existing draw mechanics.
        King king = state.view(Seat.FIRST).kingOffer().stream().filter(k -> k != King.NETHRION).findFirst().orElseThrow();
        for (Seat seat : Seat.values()) if (state.awaits(seat)) state = state.apply(seat, new ChooseKing(king));
        return state;
    }
}
