package onitama;

import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.Square;

import java.util.List;
import java.util.Random;

/**
 * Command-line entry point. Dispatches on the first argument to the matching
 * application mode. Milestone 1 implements the {@code demo} mode, which prints
 * a freshly dealt board and every legal move of the first turn; the server,
 * client and bots modes are added by later milestones.
 */
public final class Main {

    private Main() {
    }

    /** Program entry: {@code java -jar onitama.jar demo}. */
    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("demo")) {
            System.err.println("usage: onitama demo");
            System.exit(1);
        }
        runDemo();
    }

    /** Prints the starting position, the dealt cards and all legal first moves. */
    private static void runDemo() {
        GameState state = GameState.newGame(CardDeck.deal(new Random(42)));
        System.out.println("First to move: " + state.turn());
        System.out.println("Blue hand: " + cardNames(state.hand(PlayerColor.BLUE)));
        System.out.println("Red hand:  " + cardNames(state.hand(PlayerColor.RED)));
        System.out.println("Transit:   " + state.transit().name());
        System.out.println();
        System.out.println(state.board());

        PlayerColor mover = state.turn();
        List<Move> legal = RulesEngine.legalMoves(state);
        System.out.println(legal.size() + " legal moves for " + mover + ":");
        for (Move move : legal) {
            Square from = move.from();
            Square to = move.to();
            String piece = state.board().pieceAt(from).master() ? "Master" : "Student";
            System.out.printf("  %-8s %-7s %s -> %s%n",
                    move.cardId(), piece, from, to);
        }
    }

    private static String cardNames(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(card.name()).append(" (").append(card.id()).append(")");
        }
        return sb.toString();
    }
}
