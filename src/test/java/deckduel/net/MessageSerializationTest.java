package deckduel.net;

import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {
    static List<Message> messages() {
        var user = new UserProfile("alice", 1000, 0, 0);
        return List.of(new RegisterRequest("alice", "secret"), new LoginRequest("alice", "secret"),
                new LoginResponse(true, null, user), new LoginResponse(false, "Invalid login", null),
                new CreateMatchRequest(), new MatchCreated("ABCDE"), new JoinMatchRequest("ABCDE"),
                new FindMatchRequest(true), new MatchmakingStatus(false, "Gameplay not ready"),
                new ListMatchesRequest(), new ListMatchesResponse(List.of(new MatchSummary("ABCDE", "alice"))),
                new LeaderboardRequest(), new LeaderboardResponse(List.of(user)), new Ping(10), new Pong(10),
                new ErrorMessage("Example"), new ProtocolHello(Message.PROTOCOL_VERSION), new ProtocolReady(Message.PROTOCOL_VERSION));
    }

    @Test void everyShellMessageRoundTripsAndUsesTheStableSerialId() throws Exception {
        for (var message : messages()) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(message); out.flush(); out.reset(); }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                in.setObjectInputFilter(Message.WIRE_FILTER);
                assertEquals(message, in.readObject());
            }
            assertEquals(1L, ObjectStreamClass.lookup(message.getClass()).getSerialVersionUID());
        }
        var actualTypes = messages().stream().map(Object::getClass).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(Message.class.getPermittedSubclasses()), actualTypes);
    }
}
