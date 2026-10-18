package deckduel.net;

import deckduel.core.PlayerColor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UT09 — every message type round-trips through Java serialization with all
 * fields preserved. Messages are records, so equality already compares every
 * component. Only live version-2 messages belong to this inventory.
 */
class MessageSerializationTest {

    /**
     * One sample per message class. When a new message class is added to the
     * protocol, add it here — a missing entry would silently skip its test.
     */
    static Stream<Message> sampleMessages() {
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
                new GameOver(PlayerColor.BLUE, deckduel.core.duel.EndReason.ELIMINATION, 1016, 984),
                new GameOver(null, deckduel.core.duel.EndReason.DRAW, 1000, 1000),
                new RematchRequest(),
                new RematchAccept(),
                new OpponentLeft(60),
                new ReconnectRequest("token-123"),
                new LeaderboardRequest(),
                new LeaderboardResponse(List.of(new UserProfile("alice", 1200, 10, 2))),
                new Ping(1695000000000L),
                new Pong(1695000000000L),
                new ErrorMessage("room not found"),
                new ChatRequest("m1", "Hello 👋"),
                new ChatMessage("m1", "alice", "Good luck!"),
                new ProtocolHello(7),
                new ProtocolReady(7),
                new ActionRequest("m1", 1L, 0L, new deckduel.core.duel.GameAction.SkipMovement()),
                new MatchSync("m1", "ROOM1", deckduel.core.duel.RulesEngine.project(
                        deckduel.core.duel.RulesEngine.newGame(deckduel.config.RulesConfigLoader.load(null), 42L),
                        PlayerColor.BLUE), false, 1L),
                new ActionReply("m1", 1L, new ActionReceipt(true, 1L, null),
                        new MatchSync("m1", "ROOM1", deckduel.core.duel.RulesEngine.project(
                                deckduel.core.duel.RulesEngine.newGame(deckduel.config.RulesConfigLoader.load(null), 42L),
                                PlayerColor.BLUE), false, 2L)),
                new GameUpdate(new MatchSync("m1", "ROOM1", deckduel.core.duel.RulesEngine.project(
                        deckduel.core.duel.RulesEngine.newGame(deckduel.config.RulesConfigLoader.load(null), 42L),
                        PlayerColor.BLUE), false, 2L),
                        List.of(new deckduel.core.duel.PublicEvent.TurnStarted(PlayerColor.BLUE, 1))));
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
        assertEquals(original, copy);
    }

}
