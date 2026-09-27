package onitama;

import onitama.core.Card;
import onitama.core.GameState;
import onitama.core.PlayerColor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared test helper: compares two {@link GameState}s field by field and
 * square by square. GameState is a mutable domain object without value
 * equality, so tests describe equality with this helper instead.
 */
public final class GameStateAssert {

    private GameStateAssert() {
    }

    /** Asserts both states describe the identical position and progress. */
    public static void assertSameGame(GameState expected, GameState actual) {
        assertEquals(expected.turn(), actual.turn(), "turn");
        assertEquals(expected.moveNumber(), actual.moveNumber(), "moveNumber");
        assertEquals(expected.status(), actual.status(), "status");
        assertEquals(expected.winner(), actual.winner(), "winner");
        assertEquals(expected.way(), actual.way(), "way");
        assertEquals(expected.transit().id(), actual.transit().id(), "transit card");
        assertEquals(cardIds(expected.hand(PlayerColor.BLUE)), cardIds(actual.hand(PlayerColor.BLUE)),
                "blue hand");
        assertEquals(cardIds(expected.hand(PlayerColor.RED)), cardIds(actual.hand(PlayerColor.RED)),
                "red hand");
        assertEquals(expected.lastMove(), actual.lastMove(), "last move");
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                assertEquals(expected.board().pieceAt(x, y), actual.board().pieceAt(x, y),
                        "piece at (" + x + "," + y + ")");
            }
        }
    }

    private static List<String> cardIds(List<Card> cards) {
        return cards.stream().map(Card::id).toList();
    }
}
