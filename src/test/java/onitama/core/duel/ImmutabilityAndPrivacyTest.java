package onitama.core.duel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmutabilityAndPrivacyTest {

    @Test
    @DisplayName("apply does not mutate original state on success")
    void applyLeavesInputStateUntouched() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState original = RulesEngine.newGame(config, 42L);
        PlayerColor actor = original.turn();

        long originalRev = original.revision();
        int originalBlueMana = original.blueMana();
        int originalRedMana = original.redMana();
        List<String> originalHand = actor == PlayerColor.BLUE ? original.blueMovementHand() : original.redMovementHand();
        List<String> originalHandCopy = List.copyOf(originalHand);
        Square originalPieceLoc = original.board().findPiece(actor == PlayerColor.BLUE ? 0 : 6);

        PlayerView view = RulesEngine.viewFor(original, actor);
        List<GameAction.MovePiece> moves = Targeting.legalMoves(view);
        ActionResult result = RulesEngine.apply(original, actor, moves.get(0));

        assertTrue(result instanceof ActionResult.Accepted);

        // Original remains completely unmodified
        assertEquals(originalRev, original.revision());
        assertEquals(originalBlueMana, original.blueMana());
        assertEquals(originalRedMana, original.redMana());
        assertEquals(originalHandCopy, originalHand);
        assertEquals(originalPieceLoc, original.board().findPiece(actor == PlayerColor.BLUE ? 0 : 6));
        assertEquals(TurnPhase.MOVEMENT, original.phase());
    }

    @Test
    @DisplayName("Rejected action leaves state and future RNG behavior completely untouched")
    void rejectedActionLeavesRngUntouched() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();

        // State A: receives a rejection first, then applies a valid move
        GameState stateA = RulesEngine.newGame(config, 999L);
        PlayerColor actorA = stateA.turn();

        // Illegal move (off-board)
        GameAction.MovePiece illegalMove = new GameAction.MovePiece(actorA == PlayerColor.BLUE ? 0 : 6,
                new Square(0, -1));
        ActionResult rejectRes = RulesEngine.apply(stateA, actorA, illegalMove);
        assertTrue(rejectRes instanceof ActionResult.Rejected);

        // State B: identical untouched control state
        GameState stateB = RulesEngine.newGame(config, 999L);
        PlayerColor actorB = stateB.turn();
        assertEquals(actorA, actorB);

        // Now apply the identical legal move on both
        PlayerView viewA = RulesEngine.viewFor(stateA, actorA);
        GameAction.MovePiece legalMove = Targeting.legalMoves(viewA).get(0);

        ActionResult accA = RulesEngine.apply(stateA, actorA, legalMove);
        ActionResult accB = RulesEngine.apply(stateB, actorB, legalMove);

        assertTrue(accA instanceof ActionResult.Accepted);
        assertTrue(accB instanceof ActionResult.Accepted);

        GameState nextA = ((ActionResult.Accepted) accA).nextState();
        GameState nextB = ((ActionResult.Accepted) accB).nextState();

        // Decks, hands and RNG state must match identically
        assertEquals(nextA.blueMovementHand(), nextB.blueMovementHand());
        assertEquals(nextA.redMovementHand(), nextB.redMovementHand());
        assertEquals(nextA.movementDeck(), nextB.movementDeck());
        assertEquals(nextA.randomState().state(), nextB.randomState().state());
    }

    @Test
    @DisplayName("Blue and Red views agree on public data and differ correctly on private hands")
    void playerViewPrivacy() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        PlayerView blueView = RulesEngine.viewFor(state, PlayerColor.BLUE);
        PlayerView redView = RulesEngine.viewFor(state, PlayerColor.RED);

        // Public agreement
        assertEquals(blueView.pieces(), redView.pieces());
        assertEquals(blueView.turn(), redView.turn());
        assertEquals(blueView.phase(), redView.phase());
        assertEquals(blueView.blueMana(), redView.blueMana());
        assertEquals(blueView.redMana(), redView.redMana());
        assertEquals(blueView.blueMovementHand(), redView.blueMovementHand());
        assertEquals(blueView.redMovementHand(), redView.redMovementHand());
        assertEquals(blueView.movementDeckCount(), redView.movementDeckCount());
        assertEquals(blueView.skillDeckCount(), redView.skillDeckCount());
        assertEquals(blueView.spentSkills(), redView.spentSkills());
        assertEquals(blueView.revision(), redView.revision());
        assertEquals(blueView.outcome(), redView.outcome());

        // Private divergence: each sees own skill instances and only opponent count
        assertEquals(3, blueView.ownSkillHand().size());
        assertEquals(3, blueView.opponentSkillCount());
        assertEquals(3, redView.ownSkillHand().size());
        assertEquals(3, redView.opponentSkillCount());

        assertNotEquals(blueView.ownSkillHand(), redView.ownSkillHand());

        // Ensure opponent's instance IDs are not leaked in blueView
        for (CardInstance redCard : redView.ownSkillHand()) {
            assertFalse(blueView.ownSkillHand().contains(redCard));
        }
    }

    @Test
    @DisplayName("PlayerView serializes and deserializes cleanly without hidden host state")
    void playerViewSerializationRoundTrip() throws Exception {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        PlayerView originalView = RulesEngine.viewFor(state, PlayerColor.BLUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(originalView);
            oos.flush();
        }

        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 0);

        PlayerView deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            deserialized = (PlayerView) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals(originalView.viewer(), deserialized.viewer());
        assertEquals(originalView.revision(), deserialized.revision());
        assertEquals(originalView.pieces(), deserialized.pieces());
        assertEquals(originalView.blueMana(), deserialized.blueMana());
        assertEquals(originalView.redMana(), deserialized.redMana());
        assertEquals(originalView.ownSkillHand(), deserialized.ownSkillHand());
        assertEquals(originalView.opponentSkillCount(), deserialized.opponentSkillCount());
        assertEquals(originalView.movementDeckCount(), deserialized.movementDeckCount());
        assertEquals(originalView.skillDeckCount(), deserialized.skillDeckCount());
    }
}
