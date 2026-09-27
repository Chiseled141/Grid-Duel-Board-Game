package onitama.core;

import java.util.List;

/**
 * The pure rules of Onitama: computing legal moves, validating and applying
 * half-moves, handling passes and detecting the end of the game. The engine
 * performs no I/O and knows nothing about networking; both the server
 * (authoritative) and the client (move highlighting) reuse it unchanged.
 */
public final class RulesEngine {

    /**
     * House rule from the brief: a game that reaches this many half-moves
     * without a winner is declared a draw.
     */
    public static final int MOVE_LIMIT = 300;

    private RulesEngine() {
    }

    /**
     * Returns the legal destination squares for the given piece and card:
     * the card's pattern squares for {@code mover} (rotated for Red), keeping
     * only squares on the board that are empty or occupied by an enemy piece.
     */
    public static List<Square> legalDestinations(Board board, Square from, Card card, PlayerColor mover) {
        return card.destinationsFrom(from, mover).stream()
                .filter(destination -> {
                    Piece piece = board.pieceAt(destination);
                    return piece == null || piece.color() != mover;
                })
                .toList();
    }

    /**
     * Returns every legal move for the player whose turn it is: every own
     * piece combined with every hand card and every legal destination.
     */
    public static List<Move> legalMoves(GameState state) {
        PlayerColor mover = state.turn();
        return state.hand(mover).stream()
                .flatMap(card -> state.board().occupiedBy(mover).stream()
                        .flatMap(from -> legalDestinations(state.board(), from, card, mover).stream()
                                .map(to -> new Move(from, to, card.id()))))
                .toList();
    }

    /** Returns true if the player to move has no legal move and must pass. */
    public static boolean mustPass(GameState state) {
        return legalMoves(state).isEmpty();
    }

    /**
     * Validates and applies a half-move: the card is played, the piece moves
     * (possibly capturing), the card exchange runs and the turn toggles. If
     * the move wins the game or the move limit is reached, the state is
     * finished accordingly.
     *
     * @throws IllegalMoveException if the game is over, it is not the mover's
     *         turn, the card is not in the mover's hand, or the destination
     *         is not part of the card's pattern or holds a friendly piece
     */
    public static void apply(GameState state, Move move) {
        if (!state.isOngoing()) {
            throw new IllegalMoveException("the game is already finished");
        }
        PlayerColor mover = state.turn();
        Card card = state.hand(mover).stream()
                .filter(handCard -> handCard.id().equals(move.cardId()))
                .findFirst()
                .orElseThrow(() -> new IllegalMoveException(
                        "card not in hand of " + mover + ": " + move.cardId()));
        Piece piece = state.board().pieceAt(move.from());
        if (piece == null || piece.color() != mover) {
            throw new IllegalMoveException("no " + mover + " piece on " + move.from());
        }
        if (!Board.isInside(move.to())) {
            throw new IllegalMoveException("destination outside the board: " + move.to());
        }
        if (!legalDestinations(state.board(), move.from(), card, mover).contains(move.to())) {
            throw new IllegalMoveException(
                    card.name() + " cannot move from " + move.from() + " to " + move.to());
        }

        Piece captured = state.board().move(move.from(), move.to());
        state.cycleCard(card.id(), mover);
        state.advanceTurn(move);

        WinCondition way = WinCondition.evaluate(state.board(), move, captured);
        if (way != null) {
            state.finishGame(mover, way);
        } else if (state.moveNumber() >= MOVE_LIMIT) {
            state.finishGame(null, WinCondition.DRAW);
        }
    }

    /**
     * Applies a pass: only legal when the player to move truly has no legal
     * move. The player still performs the card exchange with the chosen
     * discard, then the turn toggles.
     *
     * @throws IllegalMoveException if a legal move exists or the card is not
     *         in the passing player's hand
     */
    public static void pass(GameState state, String cardIdToDiscard) {
        if (!state.isOngoing()) {
            throw new IllegalMoveException("the game is already finished");
        }
        if (!mustPass(state)) {
            throw new IllegalMoveException("passing is illegal while a legal move exists");
        }
        PlayerColor passer = state.turn();
        boolean inHand = state.hand(passer).stream()
                .anyMatch(card -> card.id().equals(cardIdToDiscard));
        if (!inHand) {
            throw new IllegalMoveException(
                    "card not in hand of " + passer + ": " + cardIdToDiscard);
        }
        state.cycleCard(cardIdToDiscard, passer);
        state.advanceTurn(null);
        if (state.moveNumber() >= MOVE_LIMIT) {
            state.finishGame(null, WinCondition.DRAW);
        }
    }
}
