package onitama.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT04 — the card cycle: after every turn the used card is in transit, the
 * previous transit card is in the player's hand, hands always hold two cards,
 * the opponent's view of a card is the rotated pattern, and passing is only
 * permitted when no legal move exists.
 */
class CardCycleTest {

    @Test
    void usedCardBecomesTransitAndOldTransitJoinsHand() {
        GameState state = TestPositions.standardGame(1);
        PlayerColor mover = state.turn();
        Card oldTransit = state.transit();
        Move move = RulesEngine.legalMoves(state).get(0);

        RulesEngine.apply(state, move);

        assertEquals(move.cardId(), state.transit().id(), "used card must be the new transit card");
        List<Card> hand = state.hand(mover);
        assertEquals(2, hand.size(), "hand always has two cards");
        assertTrue(hand.stream().anyMatch(card -> card.id().equals(oldTransit.id())),
                "previous transit card joins the hand");
        assertFalse(hand.stream().anyMatch(card -> card.id().equals(move.cardId())),
                "used card left the hand");
        assertEquals(mover.opponent(), state.turn(), "turn toggles");
        assertEquals(1, state.moveNumber());
    }

    @Test
    void handsStayAtTwoCardsAcrossManyTurns() {
        GameState state = TestPositions.standardGame(3);
        for (int i = 0; i < 6 && state.isOngoing(); i++) {
            List<Move> legal = RulesEngine.legalMoves(state);
            assertFalse(legal.isEmpty(), "in the opening a legal move always exists");
            RulesEngine.apply(state, legal.get(0));
            assertEquals(2, state.hand(PlayerColor.BLUE).size());
            assertEquals(2, state.hand(PlayerColor.RED).size());
        }
    }

    @Test
    void opponentsViewOfACardIsTheRotatedPattern() {
        Card tiger = CardDeck.cardById("tiger");
        Square origin = new Square(2, 2);

        List<Square> blueView = tiger.destinationsFrom(origin, PlayerColor.BLUE);
        List<Square> redView = tiger.destinationsFrom(origin, PlayerColor.RED);

        // Tiger (Blue): (0,+2),(0,-1) -> (2,4),(2,1). Rotated for Red -> (2,0),(2,3).
        assertEquals(List.of(new Square(2, 4), new Square(2, 1)), blueView);
        assertEquals(List.of(new Square(2, 0), new Square(2, 3)), redView);
    }

    @Test
    void passIsLegalOnlyWithNoLegalMove() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "crab", "elephant",
                "dragon", "frog", "tiger");
        // Blue fills Red's home row: every forward move leaves the board and every
        // sideways move hits a friendly piece, so Blue truly has no legal move.
        state.board().set(new Square(0, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(1, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(2, 4), new Piece(PlayerColor.BLUE, true));
        state.board().set(new Square(3, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(4, 4), new Piece(PlayerColor.BLUE, false));

        assertTrue(RulesEngine.mustPass(state), "Blue has no legal move with crab/elephant");

        RulesEngine.pass(state, "crab");

        List<Card> hand = state.hand(PlayerColor.BLUE);
        assertEquals(2, hand.size());
        assertTrue(hand.stream().anyMatch(card -> card.id().equals("elephant")));
        assertTrue(hand.stream().anyMatch(card -> card.id().equals("tiger")),
                "previous transit card joins the hand on a pass too");
        assertEquals("crab", state.transit().id(), "discarded card becomes transit");
        assertEquals(PlayerColor.RED, state.turn());
        assertEquals(1, state.moveNumber());
        assertEquals(GameState.Status.ONGOING, state.status());
    }

    @Test
    void passWithLegalMoveAvailableIsRejected() {
        GameState state = TestPositions.standardGame(5);
        String anyHandCard = state.hand(state.turn()).get(0).id();
        assertFalse(RulesEngine.legalMoves(state).isEmpty());
        assertThrows(IllegalMoveException.class,
                () -> RulesEngine.pass(state, anyHandCard));
    }

    @Test
    void passWithCardNotInHandIsRejected() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "crab", "elephant",
                "dragon", "frog", "tiger");
        state.board().set(new Square(2, 4), new Piece(PlayerColor.BLUE, true));
        state.board().set(new Square(0, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(1, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(3, 4), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(4, 4), new Piece(PlayerColor.BLUE, false));

        assertThrows(IllegalMoveException.class, () -> RulesEngine.pass(state, "boar"));
    }
}
