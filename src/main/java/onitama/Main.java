package onitama;

import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.Square;
import onitama.db.InMemoryUserDao;
import onitama.server.GameServer;
import onitama.server.ServerConfig;

import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.SwingUtilities;

/**
 * Command-line entry point (Factory Method): builds the right application
 * from the first argument.
 *
 * <pre>
 *   java -jar onitama.jar demo   [--seed n]              # print a board and legal moves
 *   java -jar onitama.jar server --port 5555             # headless server
 *   java -jar onitama.jar client --host &lt;ip&gt; --port 5555 # Swing client
 * </pre>
 *
 * The {@code bots} mode is added by a later milestone.
 */
public final class Main {

    private Main() {
    }

    /** Program entry; dispatches on args[0]. */
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        Map<String, String> options = parseOptions(args);
        switch (args[0]) {
            case "demo" -> runDemo(options);
            case "server" -> runServer(options);
            case "client" -> launchClient(options);
            default -> {
                usage();
                System.exit(1);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: onitama demo [--seed n]");
        System.err.println("       onitama server --port <port>");
        System.err.println("       onitama client [--host <host>] [--port <port>]");
    }

    /** Parses "--key value" pairs after the mode argument into a map. */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                options.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return options;
    }

    // ------------------------------------------------------------------
    // demo
    // ------------------------------------------------------------------

    /** Prints the starting position, the dealt cards and all legal first moves. */
    private static void runDemo(Map<String, String> options) {
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        GameState state = GameState.newGame(CardDeck.deal(new Random(seed)));
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

    // ------------------------------------------------------------------
    // server
    // ------------------------------------------------------------------

    private static void runServer(Map<String, String> options) {
        int port = Integer.parseInt(options.getOrDefault("port", "5555"));
        // Milestone M5 replaces the in-memory accounts with SQLite persistence.
        GameServer server = new GameServer(ServerConfig.defaults(port), new InMemoryUserDao());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "onitama-shutdown"));
        try {
            server.start();
            server.awaitShutdown();
        } catch (Exception e) {
            System.err.println("server failed: " + e.getMessage());
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // client
    // ------------------------------------------------------------------

    /** Launches the Swing client on the EDT; CLI host/port override settings. */
    private static void launchClient(Map<String, String> options) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("client mode needs a graphical environment");
            System.exit(1);
        }
        String host = options.get("host");
        Integer port = options.containsKey("port")
                ? Integer.valueOf(options.get("port")) : null;
        SwingUtilities.invokeLater(() -> new onitama.client.ui.ClientFrame(host, port).setVisible(true));
    }
}
