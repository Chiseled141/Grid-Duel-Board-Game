package onitama.replay;

import onitama.core.Board;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.DealSnapshot;
import onitama.core.GameState;
import onitama.core.HalfMove;
import onitama.core.IllegalMoveException;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.Square;
import onitama.server.MatchResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Text-based replay files (UTF-8, one line per record) with the extension
 * {@code .onitama-replay}. The header records players, date, opening deal
 * and first player; every following line is one half-move in the format
 * {@code move <n>;<cardId>;<fromX>,<fromY>;<toX>,<toY>} (passes are written
 * as {@code pass <n>;<cardId>}). Reading validates every recorded half-move
 * against the rules engine, so a corrupted file is reported, never crashed on.
 */
public final class ReplayFile {

    /** The file extension used for replay files. */
    public static final String EXTENSION = ".onitama-replay";

    private static final String MAGIC = "ONITAMA-REPLAY 1";

    /** A parsed replay: the header plus the validated half-move list. */
    public record Replay(DealSnapshot deal, String blueUsername, String redUsername,
                         LocalDateTime playedAt, List<HalfMove> moves,
                         GameState initialState) {
    }

    private ReplayFile() {
    }

    /** Writes a finished match to the given path (server side, milestone M5). */
    public static void write(Path path, MatchResult result) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(MAGIC);
            out.newLine();
            out.write("blue " + result.blueUsername());
            out.newLine();
            out.write("red " + result.redUsername());
            out.newLine();
            out.write("date " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            out.newLine();
            out.write("deal " + cardIds(result.deal().blueHand()) + "|"
                    + cardIds(result.deal().redHand()) + "|" + result.deal().transit().id());
            out.newLine();
            out.write("first " + result.deal().firstPlayer());
            out.newLine();
            for (HalfMove move : result.history()) {
                out.write(lineOf(move));
                out.newLine();
            }
        }
    }

    /**
     * Reads and validates a replay file.
     *
     * @throws ReplayFormatException if the file is malformed or one of its
     *         moves violates the rules of Onitama
     */
    public static Replay read(Path path) throws IOException {
        List<String> lines;
        try (Stream<String> fileLines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines = fileLines.toList();
        }
        if (lines.isEmpty() || !lines.get(0).equals(MAGIC)) {
            throw new ReplayFormatException("not an Onitama replay file");
        }
        Header header = Header.parse(lines);
        GameState initial = new GameState(Board.newGame(), header.deal().blueHand(),
                header.deal().redHand(), header.deal().transit(), header.deal().firstPlayer());
        // The replay loop mutates the state, so the returned "initialState"
        // must be a snapshot taken before the first half-move is applied.
        GameState initialState = deepCopy(initial);

        List<HalfMove> moves = new ArrayList<>();
        for (String line : lines.subList(header.headerLineCount(), lines.size())) {
            HalfMove move = parseMoveLine(line);
            replayHalfMove(initial, move);
            moves.add(move);
        }
        return new Replay(header.deal(), header.blueUsername(), header.redUsername(),
                header.playedAt(), moves, initialState);
    }

    /** Deep-copies a game state via a serialization round-trip. */
    private static GameState deepCopy(GameState state) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(state);
            }
            try (var in = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                return (GameState) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new ReplayFormatException("could not snapshot the replay position");
        }
    }

    /** Applies one recorded half-move, converting rule violations into a parse error. */
    private static void replayHalfMove(GameState game, HalfMove move) {
        try {
            if (move.isPass()) {
                RulesEngine.pass(game, move.cardId());
            } else {
                RulesEngine.apply(game, new Move(move.from(), move.to(), move.cardId()));
            }
        } catch (IllegalMoveException | IllegalArgumentException e) {
            throw new ReplayFormatException("corrupted replay: move " + move.number()
                    + " is not legal (" + e.getMessage() + ")");
        }
    }

    private static String lineOf(HalfMove move) {
        if (move.isPass()) {
            return "pass " + move.number() + ";" + move.cardId();
        }
        return "move " + move.number() + ";" + move.cardId()
                + ";" + move.from().x() + "," + move.from().y()
                + ";" + move.to().x() + "," + move.to().y();
    }

    private static HalfMove parseMoveLine(String line) {
        String[] parts = line.trim().split(" ", 2);
        if (parts.length != 2) {
            throw new ReplayFormatException("corrupted replay line: " + line);
        }
        String[] fields = parts[1].split(";");
        try {
            int number = Integer.parseInt(fields[0]);
            if (parts[0].equals("pass") && fields.length == 2) {
                return new HalfMove(number, fields[1], null, null);
            }
            if (parts[0].equals("move") && fields.length == 4) {
                String[] from = fields[2].split(",");
                String[] to = fields[3].split(",");
                return new HalfMove(number, fields[1],
                        new Square(Integer.parseInt(from[0]), Integer.parseInt(from[1])),
                        new Square(Integer.parseInt(to[0]), Integer.parseInt(to[1])));
            }
        } catch (RuntimeException e) {
            throw new ReplayFormatException("corrupted replay line: " + line);
        }
        throw new ReplayFormatException("corrupted replay line: " + line);
    }

    private static String cardIds(List<Card> cards) {
        return cards.stream().map(Card::id).collect(java.util.stream.Collectors.joining(","));
    }

    /** The header fields of a replay file, parsed once and validated. */
    private record Header(DealSnapshot deal, String blueUsername, String redUsername,
                          LocalDateTime playedAt, int headerLineCount) {

        static Header parse(List<String> lines) {
            try {
                String blue = lines.get(1).substring("blue ".length());
                String red = lines.get(2).substring("red ".length());
                LocalDateTime date = LocalDateTime.parse(
                        lines.get(3).substring("date ".length()), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String[] dealParts = lines.get(4).substring("deal ".length()).split("\\|");
                List<Card> blueHand = Stream.of(dealParts[0].split(","))
                        .map(CardDeck::cardById).toList();
                List<Card> redHand = Stream.of(dealParts[1].split(","))
                        .map(CardDeck::cardById).toList();
                Card transit = CardDeck.cardById(dealParts[2]);
                PlayerColor first = PlayerColor.valueOf(lines.get(5).substring("first ".length()));
                return new Header(new DealSnapshot(blueHand, redHand, transit),
                        blue, red, date, 6);
            } catch (RuntimeException e) {
                throw new ReplayFormatException("corrupted replay header (" + e.getMessage() + ")");
            }
        }
    }
}
