package onitama.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UT03 — win conditions: capturing the enemy Master wins by STONE, the Master
 * reaching the opponent's Temple Arch wins by STREAM, and a Student on the
 * enemy Temple Arch does not win at all.
 */
class WinConditionTest {

    @Test
    void capturingEnemyMasterWinsByStone() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(2, 4), new Piece(PlayerColor.RED, true));

        RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar"));

        assertEquals(GameState.Status.FINISHED, state.status());
        assertEquals(PlayerColor.BLUE, state.winner());
        assertEquals(WinCondition.STONE, state.way());
    }

    @Test
    void masterReachingOpponentTempleArchWinsByStream() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, true));
        // Red's Master far away; (2,4) — Red's Temple Arch — is empty.
        state.board().set(new Square(0, 0), new Piece(PlayerColor.RED, true));

        RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar"));

        assertEquals(GameState.Status.FINISHED, state.status());
        assertEquals(PlayerColor.BLUE, state.winner());
        assertEquals(WinCondition.STREAM, state.way());
    }

    @Test
    void studentOnOpponentTempleArchDoesNotWin() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(0, 0), new Piece(PlayerColor.RED, true));

        RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar"));

        assertEquals(GameState.Status.ONGOING, state.status());
        assertNull(state.winner());
        assertNull(state.way());
    }

    @Test
    void redCanWinByStoneToo() {
        GameState state = TestPositions.position(PlayerColor.RED, "dragon", "frog",
                "boar", "tiger", "horse");
        state.board().set(new Square(2, 1), new Piece(PlayerColor.RED, false));
        state.board().set(new Square(2, 0), new Piece(PlayerColor.BLUE, true));

        RulesEngine.apply(state, new Move(new Square(2, 1), new Square(2, 0), "boar"));

        assertEquals(GameState.Status.FINISHED, state.status());
        assertEquals(PlayerColor.RED, state.winner());
        assertEquals(WinCondition.STONE, state.way());
    }
}
