package onitama.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The practice-mode bot at all three difficulty levels. Rookie keeps its
 * deliberate beginner mercy (the "usually" tests run over many seeds);
 * Senior and Legend search, so their tactical promises hold deterministically.
 * Legend must also beat the Rookie consistently.
 */
class PracticeBotTest {

    @Test
    void allLevelsCaptureTheEnemyMasterImmediately() {
        for (Difficulty level : Difficulty.values()) {
            GameState state = TestPositions.position(PlayerColor.BLUE, "tiger", "ox",
                    "horse", "crane", "goose");
            state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));
            state.board().set(new Square(2, 4), new Piece(PlayerColor.RED, true));

            Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE, level, new Random(1));

            assertEquals(new Move(new Square(2, 2), new Square(2, 4), "tiger"), move,
                    level + " must never miss Way of the Stone");
        }
    }

    @Test
    void allLevelsTakeTheStreamWhenPossible() {
        for (Difficulty level : Difficulty.values()) {
            GameState state = TestPositions.position(PlayerColor.BLUE, "eel", "horse",
                    "dragon", "frog", "boar");
            state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, true));

            Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE, level, new Random(2));

            assertNotNull(move, level.name());
            assertEquals(new Square(2, 4), move.to(),
                    level + " must step onto the enemy Temple Arch");
            assertTrue(state.board().pieceAt(move.from()).master(),
                    level + " must use the master for the stream");
        }
    }

    @Test
    void rookieUsuallyTakesFreeCaptures() {
        int captures = 0;
        for (int seed = 0; seed < 100; seed++) {
            GameState state = TestPositions.position(PlayerColor.BLUE, "ox", "mantis",
                    "tiger", "frog", "goose");
            state.board().set(new Square(1, 1), new Piece(PlayerColor.BLUE, false));
            state.board().set(new Square(1, 2), new Piece(PlayerColor.RED, false));

            Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE,
                    Difficulty.ROOKIE, new Random(seed));

            assertTrue(RulesEngine.legalMoves(state).contains(move),
                    "seed " + seed + " must stay legal");
            if (move.cardId().equals("ox") && move.to().equals(new Square(1, 2))) {
                captures++;
            }
        }
        // The 15% beginner-mercy roll may skip the capture; it must stay rare.
        assertTrue(captures >= 70, "rookie ignored a free capture too often: "
                + captures + "/100");
    }

    @Test
    void rookieUsuallyAvoidsWalkingIntoACapture() {
        assertTrue(blundersIntoCapture(Difficulty.ROOKIE) <= 15,
                "rookie walked into the obvious capture too often");
    }

    @Test
    void seniorNeverWalksIntoACapture() {
        assertEquals(0, blundersIntoCapture(Difficulty.SENIOR),
                "senior's 2-ply search must see the simple capture");
    }

    /** Counts how often the level steps onto the square red's tiger covers. */
    private static int blundersIntoCapture(Difficulty level) {
        int blunders = 0;
        for (int seed = 0; seed < 100; seed++) {
            GameState state = TestPositions.position(PlayerColor.BLUE, "tiger", "ox",
                    "tiger", "monkey", "goose");
            state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));
            state.board().set(new Square(2, 0), new Piece(PlayerColor.RED, false));

            Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE, level, new Random(seed));

            assertNotNull(move);
            // Red's tiger reaches (2,1) from (2,0), so stepping there hangs the piece.
            if (move.to().equals(new Square(2, 1))) {
                blunders++;
            }
        }
        return blunders;
    }

    @Test
    void seniorNeverHangsTheMaster() {
        int captures = 0;
        for (int seed = 0; seed < 100; seed++) {
            GameState state = TestPositions.position(PlayerColor.BLUE, "ox", "eel",
                    "tiger", "monkey", "goose");
            state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, true));
            state.board().set(new Square(2, 3), new Piece(PlayerColor.RED, false));

            Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE,
                    Difficulty.SENIOR, new Random(seed));

            // (2,1) and (3,2) are covered by red's tiger from (2,3): hanging the
            // master there loses the game. Taking the student at (2,3) is safe.
            assertEquals(new Square(2, 3), move.to(), "seed " + seed);
            if (move.to().equals(new Square(2, 3))) {
                captures++;
            }
        }
        assertEquals(100, captures, "senior must always take the safe capture");
    }

    @Test
    void legendFindsTheForcedWin() {
        // Blue master (2,2); red master (2,4). Blue plays to (2,3) — red has no
        // capture there — and wins on the next turn with the transit boar
        // (stone if red stayed, stream if red stepped aside). A 3-ply forced win.
        GameState state = TestPositions.position(PlayerColor.BLUE, "horse", "ox",
                "monkey", "cobra", "boar");
        state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, true));
        state.board().set(new Square(2, 4), new Piece(PlayerColor.RED, true));

        Move move = PracticeBot.chooseMove(state, PlayerColor.BLUE,
                Difficulty.LEGEND, new Random(4));

        assertEquals(new Square(2, 3), move.to(),
                "legend must play the forced mate-in-three, not drift");
    }

    @Test
    void legendBeatsTheRookie() {
        int legendWins = 0;
        for (int game = 0; game < 6; game++) {
            PlayerColor legendColor = game % 2 == 0 ? PlayerColor.BLUE : PlayerColor.RED;
            GameState state = GameState.newGame(CardDeck.deal(new Random(1000 + game)));
            Random random = new Random(game * 77 + 5);
            while (state.isOngoing()) {
                PlayerColor mover = state.turn();
                Difficulty level = mover == legendColor ? Difficulty.LEGEND : Difficulty.ROOKIE;
                Move move = PracticeBot.chooseMove(state, mover, level, random);
                if (move == null) {
                    RulesEngine.pass(state, state.hand(mover).get(0).id());
                } else {
                    RulesEngine.apply(state, move);
                }
            }
            if (state.winner() == legendColor) {
                legendWins++;
            }
        }
        assertTrue(legendWins >= 5, "legend only won " + legendWins + "/6 vs the rookie");
    }

    @Test
    void botGamesAreLegalAndAlwaysFinish() {
        Difficulty[] levels = Difficulty.values();
        for (long seed = 0; seed < 12; seed++) {
            Difficulty level = levels[(int) (seed % levels.length)];
            GameState state = TestPositions.standardGame(seed);
            Random random = new Random(seed * 7919 + 13);
            while (state.isOngoing()) {
                PlayerColor mover = state.turn();
                Move move = PracticeBot.chooseMove(state, mover, level, random);
                if (move == null) {
                    assertTrue(RulesEngine.mustPass(state),
                            "pass offered although legal moves exist (seed " + seed + ")");
                    RulesEngine.pass(state, state.hand(mover).get(0).id());
                } else {
                    assertTrue(RulesEngine.legalMoves(state).contains(move),
                            "illegal " + level + " move (seed " + seed + "): " + move);
                    RulesEngine.apply(state, move);
                }
            }
            assertTrue(state.moveNumber() <= RulesEngine.MOVE_LIMIT,
                    "game ran past the move cap (seed " + seed + ")");
        }
    }

    @Test
    void chooseMoveNeverMutatesTheGivenState() {
        for (Difficulty level : Difficulty.values()) {
            GameState state = TestPositions.standardGame(123);
            String boardBefore = state.board().toString();
            List<Card> blueBefore = List.copyOf(state.hand(PlayerColor.BLUE));
            List<Card> redBefore = List.copyOf(state.hand(PlayerColor.RED));

            PracticeBot.chooseMove(state, state.turn(), level, new Random(9));

            assertEquals(boardBefore, state.board().toString(), level + " mutated the board");
            assertEquals(blueBefore, List.copyOf(state.hand(PlayerColor.BLUE)), level.name());
            assertEquals(redBefore, List.copyOf(state.hand(PlayerColor.RED)), level.name());
            assertTrue(state.isOngoing(), level.name());
        }
    }
}
