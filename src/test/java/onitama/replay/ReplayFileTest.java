package onitama.replay;

import onitama.core.GameState;
import onitama.core.HalfMove;
import onitama.core.Move;
import onitama.core.RulesEngine;
import onitama.server.MatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replay file I/O: a written replay round-trips (header + every half-move),
 * reading reconstructs the identical game, and corrupted files are reported
 * with a friendly exception instead of crashing.
 */
class ReplayFileTest {

    @TempDir
    Path tempDir;

    @Test
    void writtenReplayRoundTrips() throws Exception {
        // Play five scripted half-moves to build a match result.
        var deal = onitama.core.CardDeck.deal(new Random(11));
        GameState game = GameState.newGame(deal);
        java.util.List<HalfMove> history = new java.util.ArrayList<>();
        for (int i = 0; i < 5 && game.isOngoing(); i++) {
            Move move = RulesEngine.legalMoves(game).get(0);
            RulesEngine.apply(game, move);
            history.add(new HalfMove(game.moveNumber(), move.cardId(),
                    move.from(), move.to()));
        }
        MatchResult result = new MatchResult("ROOM1", "alice", "bob",
                game.winner(), game.way(), 1016, 984,
                new onitama.core.DealSnapshot(deal.blueHand(), deal.redHand(), deal.transit()),
                history);

        Path file = tempDir.resolve("test.onitama-replay");
        ReplayFile.write(file, result);
        ReplayFile.Replay replay = ReplayFile.read(file);

        assertEquals("alice", replay.blueUsername());
        assertEquals("bob", replay.redUsername());
        assertEquals(history.size(), replay.moves().size());
        for (int i = 0; i < history.size(); i++) {
            assertEquals(history.get(i), replay.moves().get(i));
        }
        // The reconstructed initial state must match a fresh deal with the same seed.
        GameState initial = GameState.newGame(onitama.core.CardDeck.deal(new Random(11)));
        assertEquals(initial.transit().id(), replay.initialState().transit().id());
        assertEquals(initial.turn(), replay.initialState().turn());
        assertEquals(initial.board().toString(), replay.initialState().board().toString());
    }

    @Test
    void corruptedFileIsRejected() throws Exception {
        Path file = tempDir.resolve("bad.onitama-replay");
        Files.writeString(file, "this is not a replay");
        assertThrows(ReplayFormatException.class, () -> ReplayFile.read(file));
    }

    @Test
    void tamperedMoveIsRejectedByTheRulesEngine() throws Exception {
        var deal = onitama.core.CardDeck.deal(new Random(12));
        GameState game = GameState.newGame(deal);
        Move move = RulesEngine.legalMoves(game).get(0);
        RulesEngine.apply(game, move);
        MatchResult result = new MatchResult("ROOM1", "alice", "bob",
                game.winner(), game.way(), 1000, 1000,
                new onitama.core.DealSnapshot(deal.blueHand(), deal.redHand(), deal.transit()),
                List.of(new HalfMove(1, move.cardId(), move.from(), move.to())));

        Path file = tempDir.resolve("tampered.onitama-replay");
        ReplayFile.write(file, result);
        // Rewrite the first move with a fabricated destination far away.
        String content = Files.readString(file)
                .replaceFirst("move 1;[a-z]+;\\d,\\d;\\d,\\d", "move 1;dragon;0,0;4,4");
        Files.writeString(file, content);

        ReplayFormatException error = assertThrows(ReplayFormatException.class,
                () -> ReplayFile.read(file));
        assertTrue(error.getMessage().contains("move 1"));
    }
}
