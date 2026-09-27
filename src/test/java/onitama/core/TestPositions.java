package onitama.core;

import java.util.List;

/**
 * Test fixture for building arbitrary game positions quickly. Only usable from
 * inside the {@code onitama.core} package, which is exactly the point: tests
 * may set up positions, production code outside core may not mutate them.
 */
final class TestPositions {

    private TestPositions() {
    }

    /**
     * Builds an ongoing game from cards identified by id, with Blue to move or
     * the given color to move.
     */
    static GameState position(PlayerColor turn,
                              String blueCard1, String blueCard2,
                              String redCard1, String redCard2,
                              String transitId) {
        return new GameState(new Board(),
                List.of(CardDeck.cardById(blueCard1), CardDeck.cardById(blueCard2)),
                List.of(CardDeck.cardById(redCard1), CardDeck.cardById(redCard2)),
                CardDeck.cardById(transitId), turn);
    }

    /** Returns a standard starting position dealt with the given seed. */
    static GameState standardGame(long seed) {
        return GameState.newGame(CardDeck.deal(new java.util.Random(seed)));
    }
}
