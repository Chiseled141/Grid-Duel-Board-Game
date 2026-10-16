package onitama.core.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import onitama.core.PlayerColor;
import org.junit.jupiter.api.Test;

class ForesightTest {

    private GameState createForesightState(int deckSize, int lookahead) {
        RulesConfig base = DuelTestFixture.defaultRulesConfig();
        // Adjust lookahead if needed
        List<SkillDefinition> skills = new ArrayList<>();
        for (SkillDefinition def : base.skills()) {
            if (def.id().equals("foresight")) {
                skills.add(new SkillDefinition(
                        def.id(), def.name(), def.category(), def.manaCost(), def.copies(),
                        java.util.Map.of(SkillParameter.LOOKAHEAD, lookahead),
                        def.descriptionTemplate(), def.artworkPath()));
            } else {
                skills.add(def);
            }
        }
        RulesConfig rules = new RulesConfig(
                base.rulesVersion(), base.movementHandSize(), base.skillHandSize(),
                base.initialMana(), base.manaPerTurn(), base.manaCap(), base.turnLimit(),
                base.movementCards(), skills);

        GameState state = RulesEngine.newGame(rules, 42L);
        state.mutableSkillHand(PlayerColor.BLUE).clear();
        state.mutableSkillHand(PlayerColor.BLUE).add(new CardInstance("foresight#1", "foresight"));
        state.setBlueMana(3);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.SKILL);

        // Populate deck with known cards
        state.mutableSkillDeck().clear();
        for (int i = 0; i < deckSize; i++) {
            String defId = RulesConfig.CANONICAL_SKILL_IDS.get(i);
            state.mutableSkillDeck().add(new CardInstance(defId + "#1", defId));
        }
        return state;
    }

    @Test
    void testForesightPrefixSizesZeroAndOneResolveImmediately() {
        // Deck size 0
        GameState state0 = createForesightState(0, 3);
        ActionResult res0 = RulesEngine.apply(state0, PlayerColor.BLUE,
                new GameAction.CastSkill("foresight#1", new SkillTargets.None()));
        assertTrue(res0 instanceof ActionResult.Accepted);
        GameState next0 = ((ActionResult.Accepted) res0).nextState();
        assertEquals(TurnPhase.END, next0.phase());
        assertEquals(2, next0.blueMana()); // 3 - 1
        assertNull(next0.pendingChoice());
        assertEquals(0, next0.blueSkillHand().size());

        // Deck size 1
        GameState state1 = createForesightState(1, 3);
        CardInstance singleCard = state1.skillDeck().get(0);
        ActionResult res1 = RulesEngine.apply(state1, PlayerColor.BLUE,
                new GameAction.CastSkill("foresight#1", new SkillTargets.None()));
        assertTrue(res1 instanceof ActionResult.Accepted);
        GameState next1 = ((ActionResult.Accepted) res1).nextState();
        assertEquals(TurnPhase.END, next1.phase());
        assertEquals(2, next1.blueMana());
        assertNull(next1.pendingChoice());
        assertEquals(1, next1.blueSkillHand().size());
        assertEquals(singleCard.instanceId(), next1.blueSkillHand().get(0).instanceId());
    }

    @Test
    void testForesightChoiceLifecycleAndPrivacy() {
        GameState state = createForesightState(5, 3);
        List<CardInstance> originalDeck = List.copyOf(state.skillDeck());
        long startRevision = state.revision();

        // 1. Begin Foresight
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("foresight#1", new SkillTargets.None()));
        assertTrue(res instanceof ActionResult.Accepted);
        ActionResult.Accepted accepted = (ActionResult.Accepted) res;
        GameState choiceState = accepted.nextState();

        assertEquals(TurnPhase.CHOICE, choiceState.phase());
        assertEquals(startRevision + 1, choiceState.revision());
        assertNotNull(choiceState.pendingChoice());
        assertEquals(startRevision + 1, choiceState.pendingChoice().choiceId());
        assertEquals(3, choiceState.pendingChoice().preview().size());
        assertEquals(3, choiceState.blueMana()); // mana still in balance until resolve

        // Verify Privacy in PlayerViews
        PlayerView blueView = RulesEngine.viewFor(choiceState, PlayerColor.BLUE);
        assertNotNull(blueView.ownChoice());
        assertEquals(3, blueView.ownChoice().preview().size());
        assertEquals(1, blueView.reservedMana());

        PlayerView redView = RulesEngine.viewFor(choiceState, PlayerColor.RED);
        assertNull(redView.ownChoice());
        assertEquals(0, redView.reservedMana());
        assertEquals(TurnPhase.CHOICE, redView.phase());

        // 2. Reject actions out of turn or wrong actor
        ActionResult redResolve = RulesEngine.apply(choiceState, PlayerColor.RED,
                new GameAction.ResolveForesight(blueView.ownChoice().choiceId(), List.of("sidestep#1", "dash#1", "blink#1")));
        assertTrue(redResolve instanceof ActionResult.Rejected r && r.error() == ActionError.NOT_YOUR_TURN);

        // 3. Reject invalid permutations
        // Duplicate IDs
        ActionResult dupResolve = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                new GameAction.ResolveForesight(blueView.ownChoice().choiceId(), List.of(
                        originalDeck.get(0).instanceId(),
                        originalDeck.get(0).instanceId(),
                        originalDeck.get(2).instanceId())));
        assertTrue(dupResolve instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_CHOICE);

        // Wrong size
        ActionResult sizeResolve = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                new GameAction.ResolveForesight(blueView.ownChoice().choiceId(), List.of(
                        originalDeck.get(0).instanceId(),
                        originalDeck.get(1).instanceId())));
        assertTrue(sizeResolve instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_CHOICE);

        // Foreign ID
        ActionResult foreignResolve = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                new GameAction.ResolveForesight(blueView.ownChoice().choiceId(), List.of(
                        originalDeck.get(0).instanceId(),
                        originalDeck.get(1).instanceId(),
                        "vault#1")));
        assertTrue(foreignResolve instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_CHOICE);

        // Wrong choiceId
        ActionResult wrongIdResolve = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                new GameAction.ResolveForesight(99999L, List.of(
                        originalDeck.get(2).instanceId(),
                        originalDeck.get(1).instanceId(),
                        originalDeck.get(0).instanceId())));
        assertTrue(wrongIdResolve instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_CHOICE);

        // 4. Valid resolve: reverse top 3 cards
        List<String> reversedTop3 = List.of(
                originalDeck.get(2).instanceId(),
                originalDeck.get(1).instanceId(),
                originalDeck.get(0).instanceId());

        ActionResult validRes = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                new GameAction.ResolveForesight(blueView.ownChoice().choiceId(), reversedTop3));
        assertTrue(validRes instanceof ActionResult.Accepted);
        GameState finalState = ((ActionResult.Accepted) validRes).nextState();

        assertEquals(TurnPhase.END, finalState.phase());
        assertEquals(2, finalState.blueMana()); // paid 1 reserved mana
        assertNull(finalState.pendingChoice());
        assertEquals(choiceState.revision() + 1, finalState.revision());

        // Card bookkeeping:
        // Caster consumed foresight#1 and drew the new top card of deck!
        // The new top card was reversedTop3.get(0) which is originalDeck.get(2)
        assertEquals(1, finalState.blueSkillHand().size());
        assertEquals(originalDeck.get(2).instanceId(), finalState.blueSkillHand().get(0).instanceId());

        // Next card in deck is reversedTop3.get(1), then reversedTop3.get(2)
        assertEquals(originalDeck.get(1).instanceId(), finalState.skillDeck().get(0).instanceId());
        assertEquals(originalDeck.get(0).instanceId(), finalState.skillDeck().get(1).instanceId());
        // 4th and 5th cards remain unchanged
        assertEquals(originalDeck.get(3).instanceId(), finalState.skillDeck().get(2).instanceId());
        assertEquals(originalDeck.get(4).instanceId(), finalState.skillDeck().get(3).instanceId());
    }

    @Test
    void testForesightWithDifferentLookaheadSizes() {
        for (int lookahead = 2; lookahead <= 5; lookahead++) {
            GameState state = createForesightState(5, lookahead);
            ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                    new GameAction.CastSkill("foresight#1", new SkillTargets.None()));
            assertTrue(res instanceof ActionResult.Accepted);
            GameState choiceState = ((ActionResult.Accepted) res).nextState();
            assertEquals(TurnPhase.CHOICE, choiceState.phase());
            assertEquals(lookahead, choiceState.pendingChoice().preview().size());

            // Resolve with reversed preview
            List<String> rev = new ArrayList<>();
            for (int i = lookahead - 1; i >= 0; i--) {
                rev.add(choiceState.pendingChoice().preview().get(i).instanceId());
            }
            ActionResult resResolve = RulesEngine.apply(choiceState, PlayerColor.BLUE,
                    new GameAction.ResolveForesight(choiceState.pendingChoice().choiceId(), rev));
            assertTrue(resResolve instanceof ActionResult.Accepted);
            GameState endState = ((ActionResult.Accepted) resResolve).nextState();
            assertEquals(TurnPhase.END, endState.phase());
        }
    }

    @Test
    void testResignOrForfeitDuringChoiceClearsReservation() {
        GameState state = createForesightState(4, 3);
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("foresight#1", new SkillTargets.None()));
        GameState choiceState = ((ActionResult.Accepted) res).nextState();
        assertEquals(TurnPhase.CHOICE, choiceState.phase());

        // Resign
        ActionResult resignRes = RulesEngine.apply(choiceState, PlayerColor.BLUE, new GameAction.Resign());
        assertTrue(resignRes instanceof ActionResult.Accepted);
        GameState resignedState = ((ActionResult.Accepted) resignRes).nextState();
        assertEquals(TurnPhase.FINISHED, resignedState.phase());
        assertNull(resignedState.pendingChoice());
        assertEquals(PlayerColor.RED, resignedState.outcome().winner());

        // Forfeit
        ActionResult forfeitRes = RulesEngine.forfeit(choiceState, PlayerColor.BLUE);
        assertTrue(forfeitRes instanceof ActionResult.Accepted);
        GameState forfeitedState = ((ActionResult.Accepted) forfeitRes).nextState();
        assertEquals(TurnPhase.FINISHED, forfeitedState.phase());
        assertNull(forfeitedState.pendingChoice());
    }
}
