package onitama.net;

import onitama.SerializationProbe;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.Board;
import onitama.core.duel.EndReason;
import onitama.core.duel.GameAction;
import onitama.core.duel.GameState;
import onitama.core.duel.PlayerView;
import onitama.core.duel.PublicEvent;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that legitimate protocol messages and recipient-specific views deserialize
 * under WIRE_FILTER, while prohibited classes (GameState, Board, RNG, host pending state,
 * and foreign classes) are rejected immediately.
 */
class MessageWireFilterTest {

    @Test
    void legitimateMessagesDeserializeUnderTheFilter() throws Exception {
        RulesConfig rules = RulesConfigLoader.load(null);
        GameState game = RulesEngine.newGame(rules, 42L);
        PlayerView view = RulesEngine.project(game, PlayerColor.BLUE);

        MatchSync sync = new MatchSync("match-1", "ROOM1", view, false, 1L);
        assertDeserializes(sync);
        assertDeserializes(new ActionRequest("match-1", 1L, 0L,
                new GameAction.MovePiece(1, new Square(2, 2))));
        assertDeserializes(new ActionReply("match-1", 1L,
                new ActionReceipt(true, 1L, null), sync));
        assertDeserializes(new GameUpdate(sync, List.of(
                new PublicEvent.TurnStarted(PlayerColor.BLUE, 1))));
        assertDeserializes(new GameOver(null, EndReason.DRAW, 1000, 1000));
        assertDeserializes(new LoginResponse(true, null,
                new UserProfile("alice", 1016, 3, 1), "token"));
        assertDeserializes(new ProtocolHello(6));
        assertDeserializes(new ProtocolReady(6));
        assertDeserializes(new onitama.net.FindMatchRequest(true));
        assertDeserializes(new onitama.net.FindMatchRequest(false));
        assertDeserializes(new onitama.net.MatchmakingStatus(true, null));
        assertDeserializes(new onitama.net.MatchmakingStatus(false, "Please log in first."));
        assertDeserializes(new ChatRequest("match-1", "Hello 👋"));
        assertDeserializes(new ChatMessage("match-1", "alice", "Good luck!"));
    }

    @Test
    void nonMessageObjectIsRejectedByTheFilter() throws Exception {
        assertObjectRejected(new SerializationProbe());
    }

    @Test
    void coreAuthoritativeObjectsAreNotWireSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(GameState.class),
                "authoritative GameState must not be serializable over the wire");
        assertFalse(Serializable.class.isAssignableFrom(Board.class),
                "authoritative Board must not be serializable over the wire");
    }

    @Test
    void unrelatedJdkAndCoreValuesAreRejected() throws Exception {
        assertObjectRejected(new java.util.Date(0));
        assertObjectRejected(new java.util.Random(1));
        assertObjectRejected(onitama.core.Difficulty.ROOKIE);
        assertObjectRejected(onitama.core.duel.SelectionDraft.empty());
        assertObjectRejected(new Object[1001]);
    }

    @Test
    void supportedCollectionImplementationsAndAllActionShapesRoundTrip() throws Exception {
        var profile = new UserProfile("alice", 1000, 0, 0);
        for (List<UserProfile> list : List.of(
                new java.util.ArrayList<>(List.of(profile)),
                new java.util.LinkedList<>(List.of(profile)),
                java.util.Arrays.asList(profile),
                java.util.Collections.unmodifiableList(new java.util.ArrayList<>(List.of(profile))),
                java.util.Collections.singletonList(profile),
                java.util.Collections.<UserProfile>emptyList())) {
            assertDeserializes(new LeaderboardResponse(list));
        }
        for (var target : List.of(
                new onitama.core.duel.SkillTargets.Relocate(0, new Square(1, 1)),
                new onitama.core.duel.SkillTargets.Swap(0, 1),
                new onitama.core.duel.SkillTargets.Attack(0, List.of(6, 7)),
                new onitama.core.duel.SkillTargets.Enemy(6),
                new onitama.core.duel.SkillTargets.Displace(0, 6),
                new onitama.core.duel.SkillTargets.RelocateEnemy(0, 6, new Square(3, 6)),
                new onitama.core.duel.SkillTargets.ShiftEnemies(List.of(6), onitama.core.duel.Direction.NORTH),
                new onitama.core.duel.SkillTargets.MoveGroup(List.of(new onitama.core.duel.Relocation(0, new Square(1, 1)))),
                new onitama.core.duel.SkillTargets.Rally(1, List.of(new onitama.core.duel.Relocation(0, new Square(1, 1)))),
                new onitama.core.duel.SkillTargets.None())) {
            assertDeserializes(new ActionRequest("match", 1, 0, new GameAction.CastSkill("skill#1", target)));
        }
        assertDeserializes(new ActionRequest("match", 1, 0, new GameAction.ResolveForesight(1, List.of("a", "b"))));
        assertDeserializes(new ActionRequest("match", 1, 0, new GameAction.Resign()));
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

    private static void assertObjectRejected(Object obj) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(obj);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER);
            assertThrows(IOException.class, in::readObject,
                    obj.getClass().getSimpleName() + " must be rejected by WIRE_FILTER");
        }
    }
}
