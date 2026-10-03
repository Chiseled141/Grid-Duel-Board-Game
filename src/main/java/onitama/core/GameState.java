package onitama.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The full authoritative state of one game: board, both hands, the transit
 * card, whose turn it is, the half-move counter and the result. The state is
 * mutated only by {@link RulesEngine}; all mutating methods are package-private
 * so client and server code can treat a received GameState as read-only.
 *
 * <p><b>Wire safety:</b> this class is mutable and {@link java.io.Serializable}.
 * When received over the network (inside {@code MoveApplied} or
 * {@code MatchStart}), callers must treat the deserialized instance as
 * <em>effectively immutable</em>. Mutating a received state will silently
 * corrupt the client's view of the game; if local rule checking is needed,
 * create a deep copy first.
 */
public final class GameState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Life cycle of a game. */
    public enum Status {
        ONGOING,
        FINISHED
    }

    private final Board board;
    private final List<Card> blueHand;
    private final List<Card> redHand;
    private Card transit;
    private PlayerColor turn;
    private int moveNumber;
    private Status status;
    private PlayerColor winner;
    private WinCondition way;
    private Move lastMove;

    /**
     * Creates an ongoing game from an explicit position. Used by
     * {@link #newGame(CardDeck.Deal)} and by tests that need custom positions.
     */
    public GameState(Board board, List<Card> blueHand, List<Card> redHand,
                     Card transit, PlayerColor turn) {
        this.board = board;
        this.blueHand = new ArrayList<>(blueHand);
        this.redHand = new ArrayList<>(redHand);
        this.transit = transit;
        this.turn = turn;
        this.moveNumber = 0;
        this.status = Status.ONGOING;
    }

    /**
     * Creates a game in the standard starting position from a card deal.
     */
    public static GameState newGame(CardDeck.Deal deal) {
        return new GameState(Board.newGame(), deal.blueHand(), deal.redHand(),
                deal.transit(), deal.firstPlayer());
    }

    /**
     * Creates a fully independent deep copy of another state. Cards and pieces
     * are immutable and shared; the board and hands are copied. This is the
     * "deep copy first" the wire-safety note asks for: search a copy freely
     * while the original stays untouched.
     */
    public GameState(GameState other) {
        this.board = new Board(other.board);
        this.blueHand = new ArrayList<>(other.blueHand);
        this.redHand = new ArrayList<>(other.redHand);
        this.transit = other.transit;
        this.turn = other.turn;
        this.moveNumber = other.moveNumber;
        this.status = other.status;
        this.winner = other.winner;
        this.way = other.way;
        this.lastMove = other.lastMove;
    }

    /** Returns the live board. It can only be mutated inside this package. */
    public Board board() {
        return board;
    }

    /** Returns the hand of the given color (always two cards while ongoing). */
    public List<Card> hand(PlayerColor color) {
        return color == PlayerColor.BLUE ? blueHand : redHand;
    }

    /** Returns the card currently waiting beside the board. */
    public Card transit() {
        return transit;
    }

    /** Returns the color whose turn it is. */
    public PlayerColor turn() {
        return turn;
    }

    /** Returns the number of half-moves played so far. */
    public int moveNumber() {
        return moveNumber;
    }

    public Status status() {
        return status;
    }

    /** Returns true while neither player has won or drawn yet. */
    public boolean isOngoing() {
        return status == Status.ONGOING;
    }

    /** Returns the winner, or {@code null} while ongoing or on a draw. */
    public PlayerColor winner() {
        return winner;
    }

    /** Returns how the game ended, or {@code null} while ongoing. */
    public WinCondition way() {
        return way;
    }

    /** Returns the last applied move, or {@code null} at game start or after a pass. */
    public Move lastMove() {
        return lastMove;
    }

    /**
     * Performs the card exchange after a turn: the used card leaves the
     * player's hand and becomes the transit card, and the previous transit
     * card joins the hand. Package-private (engine and tests only).
     */
    void cycleCard(String usedCardId, PlayerColor user) {
        List<Card> hand = hand(user);
        Card used = hand.stream()
                .filter(card -> card.id().equals(usedCardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "card not in hand of " + user + ": " + usedCardId));
        hand.remove(used);
        hand.add(transit);
        transit = used;
    }

    /**
     * Records that a half-move was played and toggles the turn. A pass is
     * recorded with a {@code null} move. Package-private (engine and tests only).
     */
    void advanceTurn(Move played) {
        lastMove = played;
        moveNumber++;
        turn = turn.opponent();
    }

    /** Marks the game finished with the given result ({@code winner} null on a draw). */
    void finishGame(PlayerColor winner, WinCondition way) {
        status = Status.FINISHED;
        this.winner = winner;
        this.way = way;
    }
}
