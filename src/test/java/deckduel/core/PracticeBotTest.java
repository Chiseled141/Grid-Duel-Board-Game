package deckduel.core;

import deckduel.config.RulesConfigLoader;
import deckduel.core.duel.ActionResult;
import deckduel.core.duel.CardInstance;
import deckduel.core.duel.ForesightView;
import deckduel.core.duel.GameAction;
import deckduel.core.duel.GameState;
import deckduel.core.duel.PlayerView;
import deckduel.core.duel.RulesConfig;
import deckduel.core.duel.RulesEngine;
import deckduel.core.duel.Targeting;
import deckduel.core.duel.TurnPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit and simulation tests for {@link PracticeBot} in Deck Duel 7x7.
 */
class PracticeBotTest {

    private RulesConfig rules;

    @BeforeEach
    void setUp() {
        rules = RulesConfigLoader.load(null);
    }

    @Test
    void returnsNullWhenNotMyTurnOrGameOver() {
        GameState state = RulesEngine.newGame(rules, 42L);
        PlayerColor turn = state.turn();
        PlayerColor opponent = turn == PlayerColor.BLUE ? PlayerColor.RED : PlayerColor.BLUE;
        PlayerView opponentView = RulesEngine.viewFor(state, opponent);

        // Not opponent's turn
        assertNull(PracticeBot.chooseAction(opponentView, new Random(1)));
    }

    @Test
    void resolvesForesightWithValidPermutation() {
        List<CardInstance> preview = List.of(
                new CardInstance("strike#1", "strike"),
                new CardInstance("dash#1", "dash"),
                new CardInstance("vault#1", "vault")
        );
        ForesightView choice = new ForesightView(15L, "foresight#1", 2, preview);
        PlayerView view = new PlayerView(
                PlayerColor.BLUE, rules, 1L, List.of(), PlayerColor.BLUE, TurnPhase.CHOICE,
                1, 1, List.of(), List.of(), List.of(),
                3, 2, 2, 2, 10, 15, List.of(),
                choice, null
        );

        GameAction action = PracticeBot.chooseAction(view, new Random(42));
        assertNotNull(action);
        assertTrue(action instanceof GameAction.ResolveForesight);
        GameAction.ResolveForesight resolve = (GameAction.ResolveForesight) action;
        assertEquals(15L, resolve.choiceId());
        assertEquals(3, resolve.orderedInstanceIds().size());
        assertEquals(Set.of("strike#1", "dash#1", "vault#1"), new HashSet<>(resolve.orderedInstanceIds()));
    }

    @Test
    void endsTurnInEndPhase() {
        PlayerView view = new PlayerView(
                PlayerColor.BLUE, rules, 1L, List.of(), PlayerColor.BLUE, TurnPhase.END,
                1, 1, List.of(), List.of(), List.of(),
                3, 2, 2, 0, 10, 15, List.of(),
                null, null
        );
        GameAction action = PracticeBot.chooseAction(view, new Random(1));
        assertNotNull(action);
        assertTrue(action instanceof GameAction.EndTurn);
    }

    @Test
    void seniorAndLegendAlwaysTakeCaptures() {
        int foundCaptures = 0;
        for (long seed = 1; seed <= 20 && foundCaptures < 10; seed++) {
            GameState s = RulesEngine.newGame(rules, seed);
            int steps = 0;
            while (s.outcome() == null && steps++ < 200) {
                PlayerView pv = RulesEngine.viewFor(s, s.turn());
                if (pv.phase() == TurnPhase.MOVEMENT) {
                    boolean captureAvailable = false;
                    for (GameAction.MovePiece m : Targeting.legalMoves(pv)) {
                        boolean isEnemy = pv.pieces().stream().anyMatch(
                                pp -> pp.square().equals(m.to()) && pp.piece().color() != pv.viewer());
                        if (isEnemy) {
                            captureAvailable = true;
                            break;
                        }
                    }
                    if (captureAvailable) {
                        GameAction seniorAction = PracticeBot.chooseAction(pv, Difficulty.SENIOR, new Random(seed));
                        assertTrue(seniorAction instanceof GameAction.MovePiece);
                        GameAction.MovePiece seniorMove = (GameAction.MovePiece) seniorAction;
                        boolean targetIsEnemy = pv.pieces().stream().anyMatch(
                                pp -> pp.square().equals(seniorMove.to()) && pp.piece().color() != pv.viewer());
                        assertTrue(targetIsEnemy, "Senior must choose a capture when available");

                        GameAction legendAction = PracticeBot.chooseAction(pv, Difficulty.LEGEND, new Random(seed));
                        assertTrue(legendAction instanceof GameAction.MovePiece);
                        GameAction.MovePiece legendMove = (GameAction.MovePiece) legendAction;
                        boolean legendTargetIsEnemy = pv.pieces().stream().anyMatch(
                                pp -> pp.square().equals(legendMove.to()) && pp.piece().color() != pv.viewer());
                        assertTrue(legendTargetIsEnemy, "Legend must choose a capture when available");
                        foundCaptures++;
                    }
                }
                GameAction act = PracticeBot.chooseAction(pv, new Random(seed + steps));
                if (act == null) break;
                ActionResult res = RulesEngine.apply(s, s.turn(), act);
                if (res instanceof ActionResult.Accepted acc) {
                    s = acc.nextState();
                } else {
                    break;
                }
            }
        }
        assertTrue(foundCaptures >= 5, "Should have tested at least 5 capture situations: " + foundCaptures);
    }

    @Test
    void rookieUsuallyTakesFreeCaptures() {
        int captureOpportunities = 0;
        int rookieCaptures = 0;
        for (long seed = 1; seed <= 20 && captureOpportunities < 30; seed++) {
            GameState s = RulesEngine.newGame(rules, seed);
            int steps = 0;
            while (s.outcome() == null && steps++ < 200) {
                PlayerView pv = RulesEngine.viewFor(s, s.turn());
                if (pv.phase() == TurnPhase.MOVEMENT) {
                    boolean captureAvailable = false;
                    for (GameAction.MovePiece m : Targeting.legalMoves(pv)) {
                        boolean isEnemy = pv.pieces().stream().anyMatch(
                                pp -> pp.square().equals(m.to()) && pp.piece().color() != pv.viewer());
                        if (isEnemy) {
                            captureAvailable = true;
                            break;
                        }
                    }
                    if (captureAvailable) {
                        captureOpportunities++;
                        GameAction act = PracticeBot.chooseAction(pv, Difficulty.ROOKIE, new Random(seed + steps));
                        if (act instanceof GameAction.MovePiece mp) {
                            boolean isEnemy = pv.pieces().stream().anyMatch(
                                    pp -> pp.square().equals(mp.to()) && pp.piece().color() != pv.viewer());
                            if (isEnemy) {
                                rookieCaptures++;
                            }
                        }
                    }
                }
                GameAction act = PracticeBot.chooseAction(pv, new Random(seed + steps));
                if (act == null) break;
                ActionResult res = RulesEngine.apply(s, s.turn(), act);
                if (res instanceof ActionResult.Accepted acc) {
                    s = acc.nextState();
                } else {
                    break;
                }
            }
        }
        assertTrue(captureOpportunities >= 10, "Should encounter capture opportunities: " + captureOpportunities);
        double captureRate = (double) rookieCaptures / captureOpportunities;
        assertTrue(captureRate >= 0.60, "Rookie should take captures at high rate (>=60%), got " + captureRate);
    }

    @Test
    void botGamesAreLegalAndAlwaysFinish() {
        for (long seed = 1; seed <= 12; seed++) {
            GameState state = RulesEngine.newGame(rules, seed);
            Random random = new Random(seed * 31 + 7);
            Difficulty difficulty = Difficulty.values()[(int) (seed % Difficulty.values().length)];

            int safetyLimit = 300;
            while (state.outcome() == null && safetyLimit-- > 0) {
                PlayerView view = RulesEngine.viewFor(state, state.turn());
                GameAction action = PracticeBot.chooseAction(view, difficulty, random);
                assertNotNull(action, "Bot must return an action on its turn (seed " + seed + ")");

                ActionResult result = RulesEngine.apply(state, state.turn(), action);
                assertTrue(result instanceof ActionResult.Accepted, "Bot action rejected on seed " + seed + ": "
                        + (result instanceof ActionResult.Rejected rej ? rej.error().name() : ""));
                state = ((ActionResult.Accepted) result).nextState();
            }

            assertTrue(safetyLimit > 0, "Game did not terminate in safety limit on seed " + seed);
            assertNotNull(state.outcome(), "Game must reach an outcome on seed " + seed);
            assertTrue(state.completedTurns() <= 60, "Game must finish within 60 completed turns");
        }
    }
}
