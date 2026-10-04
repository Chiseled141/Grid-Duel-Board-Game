package onitama.net;

import onitama.SerializationProbe;
import onitama.core.CardDeck;
import onitama.core.GameState;
import onitama.core.PlayerColor;
import onitama.core.WinCondition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire filter installed next to every {@code ObjectInputStream} (server,
 * client, load-test bot) is an allowlist: every legitimate message still
 * deserializes under it — including the GameState-heavy ones with their board
 * arrays — while a serialized object from outside the protocol packages is
 * rejected during {@code readObject}, before any of its code can run.
 */
class MessageWireFilterTest {

    @Test
    void legitimateMessagesDeserializeUnderTheFilter() throws Exception {
        GameState game = GameState.newGame(CardDeck.deal(new Random(3)));
        assertDeserializes(new MatchStart(PlayerColor.BLUE, "ROOM1", "bob", game));
        // The heaviest payload: a full GameState with its Piece[][] board.
        assertDeserializes(new MoveApplied(game, null));
        assertDeserializes(new GameOver(null, WinCondition.DRAW, 1000, 1000));
        assertDeserializes(new LoginResponse(true, null,
                new UserProfile("alice", 1016, 3, 1), "token"));
    }

    @Test
    void nonMessageObjectIsRejectedByTheFilter() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new SerializationProbe());
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER);
            assertThrows(IOException.class, in::readObject,
                    "a serial object outside the allowlist must be rejected");
        }
    }

    private static void assertDeserializes(Message message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(message);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER);
            assertTrue(in.readObject() instanceof Message,
                    message.getClass().getSimpleName() + " must pass the wire filter");
        }
    }
}
