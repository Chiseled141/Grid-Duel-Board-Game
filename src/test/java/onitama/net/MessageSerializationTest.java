package onitama.net;

import onitama.GameStateAssert;
import onitama.core.CardDeck;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.WinCondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UT09 — every message type round-trips through Java serialization with all
 * fields preserved. Messages are records, so equality already compares every
 * component; GameState-bearing messages are compared field by field via
 * {@link GameStateAssert}.
 */
class MessageSerializationTest {

    /**
     * One sample per message class. When a new message class is added to the
     * protocol, add it here — a missing entry would silently skip its test.
     */
    static Stream<Message> sampleMessages() {
        GameState game = GameState.newGame(CardDeck.deal(new Random(1)));
        Move sampleMove = new Move(new Square(2, 0), new Square(2, 2), "tiger");
        return Stream.of(
                new RegisterRequest("alice", "secret"),
                new LoginRequest("alice", "secret"),
                new LoginResponse(true, null, new UserProfile("alice", 1016, 3, 1), "token-123"),
                new LoginResponse(false, "wrong password", null, null),
                new CreateMatchRequest(),
                new MatchCreated("AB3XZ"),
                new JoinMatchRequest("AB3XZ"),
                new ListMatchesRequest(),
                new ListMatchesResponse(List.of(new MatchSummary("AB3XZ", "alice"))),
                new MatchStart(PlayerColor.BLUE, "AB3XZ", "bob", game),
                new MoveRequest("tiger", new Square(2, 0), new Square(2, 2)),
                new MoveApplied(game, sampleMove),
                new MoveRejected("not your turn"),
                new PassTurn("tiger"),
                new GameOver(PlayerColor.BLUE, WinCondition.STONE, 1016, 984),
                new GameOver(null, WinCondition.DRAW, 1000, 1000),
                new RematchRequest(),
                new RematchAccept(),
                new ResignRequest(),
                new OpponentLeft(60),
                new ReconnectRequest("token-123"),
                new LeaderboardRequest(),
                new LeaderboardResponse(List.of(new UserProfile("alice", 1200, 10, 2))),
                new Ping(1695000000000L),
                new Pong(1695000000000L),
                new ErrorMessage("room not found"));
    }

    @ParameterizedTest(name = "round-trip of {0}")
    @MethodSource("sampleMessages")
    void messageSurvivesSerializationRoundTrip(Message original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
            out.flush();
        }

        Object copy;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = in.readObject();
        }

        assertEquals(original.getClass(), copy.getClass());
        assertFieldsPreserved(original, (Message) copy);
    }

    /** Compares the copy to the original, field by field where equality needs help. */
    private void assertFieldsPreserved(Message original, Message copy) {
        if (original instanceof MatchStart expected) {
            MatchStart actual = (MatchStart) copy;
            assertEquals(expected.yourColor(), actual.yourColor());
            assertEquals(expected.roomCode(), actual.roomCode());
            assertEquals(expected.opponentUsername(), actual.opponentUsername());
            GameStateAssert.assertSameGame(expected.initialState(), actual.initialState());
        } else if (original instanceof MoveApplied expected) {
            MoveApplied actual = (MoveApplied) copy;
            assertEquals(expected.lastMove(), actual.lastMove());
            GameStateAssert.assertSameGame(expected.state(), actual.state());
        } else {
            // All other messages are records of comparable components.
            assertEquals(original, copy);
        }
    }
}
