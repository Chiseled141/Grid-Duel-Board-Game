package onitama.core.duel;

import java.io.*;
import java.util.List;
import java.util.Random;
import onitama.core.*;
import onitama.net.MatchSync;
import onitama.net.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Turn-boundary rewards, capture scoring and terminal priorities for objective rules. */
class ObjectiveRulesTest {
    private static final Square CENTER = new Square(1, 3);
    private static final List<Square> MANA = ObjectiveLayout.standard().mana();
    private GameState game(PlayerColor turn) {
        var initial = DuelTestFixture.withMovementCards(turn, "tiger", "frog");
        var state = new GameState(initial.rules(), initial.board(), initial.blueMovementHand(),
                initial.redMovementHand(), initial.blueSkillHand(), initial.redSkillHand(),
                initial.movementDeck(), initial.skillDeck(), initial.spentSkills(), 0, 0,
                turn, TurnPhase.MOVEMENT, 1, 0, 0, initial.randomState().copy(), null);
        for (var piece : state.board().placedPieces()) state.board().remove(piece.square());
        state.setBlueMana(0); state.setRedMana(0);
        return state;
    }

    private GameState accepted(GameState state, GameAction action) {
        var result = RulesEngine.apply(state, state.turn(), action);
        assertInstanceOf(ActionResult.Accepted.class, result);
        return ((ActionResult.Accepted) result).nextState();
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void threeSquareJumpCapturesAndScoresForBothOrientations(PlayerColor actor) {
        var state = game(actor);
        int home = actor == PlayerColor.BLUE ? 0 : 6;
        int ownId = actor == PlayerColor.BLUE ? 0 : 6, enemyId = actor == PlayerColor.BLUE ? 6 : 0;
        state.board().set(new Square(1, home), new Piece(ownId, actor));
        state.board().set(CENTER, new Piece(enemyId, actor.opponent()));
        state.board().set(new Square(0, 6 - home), new Piece(enemyId + 1, actor.opponent()));
        var action = new GameAction.MovePiece("tiger", ownId, CENTER);
        assertTrue(Targeting.legalMoves(RulesEngine.viewFor(state, actor)).contains(action));
        var next = accepted(state, action);
        assertEquals(1, next.score(actor));
        assertEquals(0, state.score(actor), "input must remain immutable");
        assertEquals(0, actor == PlayerColor.BLUE ? next.blueMana() : next.redMana());
        assertNull(next.outcome());
        assertEquals(1, RulesEngine.viewFor(next, actor.opponent()).score(actor));
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(next, actor, action));
        assertEquals(1, next.score(actor), "rejection cannot repeat capture points");
    }

    @Test void movementScoreWinPrecedesTheDrawLimitEvenWithEnemiesRemaining() {
        var state = game(PlayerColor.BLUE); state.setCompletedTurns(59);
        state.board().set(new Square(1, 0), new Piece(0, PlayerColor.BLUE));
        state.board().set(CENTER, new Piece(6, PlayerColor.RED));
        state.board().set(new Square(0, 6), new Piece(7, PlayerColor.RED));
        state.addScore(PlayerColor.BLUE, 7);
        var next = accepted(state, new GameAction.MovePiece("tiger", 0, CENTER));
        assertEquals(new GameOutcome(PlayerColor.BLUE, EndReason.SCORE), next.outcome());
        assertEquals(8, next.score(PlayerColor.BLUE));
        assertEquals(59, next.completedTurns());
    }

    @Test void centerNeedsOccupationUntilTheNextOwnTurn() {
        var state = game(PlayerColor.BLUE);
        state.board().set(CENTER, new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(0, 6), new Piece(6, PlayerColor.RED));
        var redTurn = accepted(state, new GameAction.EndTurn());
        assertEquals(0, redTurn.score(PlayerColor.BLUE));
        var blueTurn = accepted(redTurn, new GameAction.EndTurn());
        assertEquals(1, blueTurn.score(PlayerColor.BLUE));
        var skipped = accepted(blueTurn, new GameAction.SkipMovement());
        assertEquals(1, skipped.score(PlayerColor.BLUE));
        assertEquals(1, RulesEngine.viewFor(skipped, PlayerColor.BLUE).blueScore());
        assertEquals(1, RulesEngine.viewFor(skipped, PlayerColor.RED).blueScore());
        assertEquals(1, skipped.score(PlayerColor.BLUE), "view/reconnect projection grants nothing");
    }

    @Test void vacatingCenterPreventsTheNextReward() {
        var state = game(PlayerColor.RED);
        state.board().set(new Square(3, 4), new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(0, 6), new Piece(6, PlayerColor.RED));
        assertEquals(0, accepted(state, new GameAction.EndTurn()).score(PlayerColor.BLUE));
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void twoManaSquaresRewardOnlyTheIncomingSideAndRespectCap(PlayerColor color) {
        var state = game(color.opponent());
        int ownId = color == PlayerColor.BLUE ? 0 : 6, enemyId = color == PlayerColor.BLUE ? 6 : 0;
        state.board().set(MANA.get(0), new Piece(ownId, color));
        state.board().set(MANA.get(1), new Piece(ownId + 1, color));
        state.board().set(new Square(0, color == PlayerColor.BLUE ? 6 : 0), new Piece(enemyId, color.opponent()));
        var next = accepted(state, new GameAction.EndTurn());
        assertEquals(3, color == PlayerColor.BLUE ? next.blueMana() : next.redMana());
        assertEquals(0, color == PlayerColor.BLUE ? next.redMana() : next.blueMana());
        if (color == PlayerColor.BLUE) state.setBlueMana(4); else state.setRedMana(4);
        next = accepted(state, new GameAction.EndTurn());
        assertEquals(5, color == PlayerColor.BLUE ? next.blueMana() : next.redMana());
        assertEquals(0, next.score(color), "mana squares do not give points");
    }

    private GameState sweepGame(boolean lastEnemies) {
        var state = game(PlayerColor.BLUE);
        state.setPhase(TurnPhase.SKILL); state.setBlueMana(5);
        state.board().set(CENTER, new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(0, 3), new Piece(6, PlayerColor.RED));
        state.board().set(new Square(2, 3), new Piece(7, PlayerColor.RED));
        if (!lastEnemies) state.board().set(new Square(6, 6), new Piece(8, PlayerColor.RED));
        state.mutableSkillHand(PlayerColor.BLUE).clear();
        state.mutableSkillHand(PlayerColor.BLUE).add(new CardInstance("sweep#1", "sweep"));
        return state;
    }

    private GameAction sweep() {
        return new GameAction.CastSkill("sweep#1", new SkillTargets.Attack(0, List.of(6, 7)));
    }

    @Test void atomicMultiEliminationSkillScoresEveryVictimAndCanWinOnPoints() {
        var state = sweepGame(false); state.addScore(PlayerColor.BLUE, 6);
        var next = accepted(state, sweep());
        assertEquals(8, next.score(PlayerColor.BLUE));
        assertEquals(new GameOutcome(PlayerColor.BLUE, EndReason.SCORE), next.outcome());
        assertNull(next.board().findPiece(6)); assertNull(next.board().findPiece(7));
        assertEquals(1, next.board().countPieces(PlayerColor.RED));
        assertEquals(6, state.score(PlayerColor.BLUE));
    }

    @Test void eliminationTakesPriorityWhenTheSameSkillAlsoReachesTarget() {
        var state = sweepGame(true); state.addScore(PlayerColor.BLUE, 7);
        var next = accepted(state, sweep());
        assertEquals(9, next.score(PlayerColor.BLUE));
        assertEquals(new GameOutcome(PlayerColor.BLUE, EndReason.ELIMINATION), next.outcome());
    }

    @Test void invalidAtomicSkillDoesNotAwardPartialPoints() {
        var state = sweepGame(false); state.addScore(PlayerColor.BLUE, 3);
        var invalid = new GameAction.CastSkill("sweep#1", new SkillTargets.Attack(0, List.of(6, 6)));
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(state, PlayerColor.BLUE, invalid));
        assertEquals(3, state.score(PlayerColor.BLUE));
        assertEquals(3, state.board().countPieces(PlayerColor.RED));
        assertEquals(5, state.blueMana());
    }

    @Test void incomingCenterRewardCanEndMatchBeforeAnotherAction() {
        var state = game(PlayerColor.RED);
        state.board().set(CENTER, new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(0, 6), new Piece(6, PlayerColor.RED));
        state.addScore(PlayerColor.BLUE, 7);
        var next = accepted(state, new GameAction.EndTurn());
        assertEquals(new GameOutcome(PlayerColor.BLUE, EndReason.SCORE), next.outcome());
        assertEquals(TurnPhase.FINISHED, next.phase());
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(next, PlayerColor.BLUE, new GameAction.EndTurn()));
    }

    @Test void turnLimitDoesNotStartAnotherTurnOrAwardPostDrawRewards() {
        var state = game(PlayerColor.RED); state.setCompletedTurns(59);
        state.board().set(CENTER, new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(0, 6), new Piece(6, PlayerColor.RED));
        state.addScore(PlayerColor.BLUE, 7);
        var next = accepted(state, new GameAction.EndTurn());
        assertEquals(EndReason.DRAW, next.outcome().reason());
        assertEquals(7, next.score(PlayerColor.BLUE));
    }

    @Test void scoreSurvivesRecipientProjectionCopyAndFilteredWireRoundTrip() throws Exception {
        var state = sweepGame(false); state.addScore(PlayerColor.BLUE, 3);
        var next = accepted(state, sweep());
        var view = RulesEngine.viewFor(next, PlayerColor.RED);
        var message = new MatchSync("score", "ROOM", view, false, 1);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(message); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER);
            var received = (MatchSync) in.readObject();
            assertEquals(view, received.view());
            assertEquals(5, received.view().blueScore());
            assertEquals(8, received.view().rules().scoreToWin());
            assertEquals(next.redSkillHand(), received.view().ownSkillHand());
            assertEquals(next.blueSkillHand().size(), received.view().opponentSkillCount());
        }
    }

    @Test void seniorBotMovesOntoCenterAndKeepsItsDefenderThere() {
        var state = game(PlayerColor.BLUE);
        state.board().set(new Square(1, 0), new Piece(0, PlayerColor.BLUE));
        state.board().set(new Square(6, 6), new Piece(6, PlayerColor.RED));
        var action = PracticeBot.chooseAction(RulesEngine.viewFor(state, PlayerColor.BLUE), Difficulty.SENIOR, new Random(1));
        assertEquals(new GameAction.MovePiece("tiger", 0, CENTER), action);
        state = accepted(state, action); state.setPhase(TurnPhase.MOVEMENT);
        assertInstanceOf(GameAction.SkipMovement.class,
                PracticeBot.chooseAction(RulesEngine.viewFor(state, PlayerColor.BLUE), Difficulty.SENIOR, new Random(1)));
    }
}
