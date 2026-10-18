package onitama.core.duel;

import java.io.*;
import java.util.*;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Privacy, atomicity, conservation and overflow behavior for hand-control skills. */
class HandControlTest {
    private GameState game(String id, int handSize) {
        var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
        game.setTurn(PlayerColor.BLUE); game.setPhase(TurnPhase.SKILL); game.setBlueMana(5);
        game.mutableSkillDeck().addAll(game.blueSkillHand());
        game.mutableSkillHand(PlayerColor.BLUE).clear();
        var desired = new CardInstance(id + "#1", id);
        game.mutableSkillDeck().remove(desired); game.mutableSkillHand(PlayerColor.RED).remove(desired);
        game.mutableSkillHand(PlayerColor.BLUE).add(desired);
        while (game.blueSkillHand().size() < handSize)
            game.mutableSkillHand(PlayerColor.BLUE).add(game.mutableSkillDeck().remove(0));
        return game;
    }
    private ActionResult.Accepted cast(GameState state, String id) {
        return assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill(id + "#1", new SkillTargets.None())));
    }
    private void conservation(GameState state) {
        var all = new ArrayList<>(state.blueSkillHand()); all.addAll(state.redSkillHand());
        all.addAll(state.skillDeck()); all.addAll(state.spentSkills()); all.addAll(state.destroyedSkills());
        assertEquals(30, all.size());
        assertEquals(30, all.stream().map(CardInstance::instanceId).distinct().count());
        assertTrue(state.blueSkillHand().size() <= 6); assertTrue(state.redSkillHand().size() <= 6);
    }

    @Test void stealTransfersOneRealInstanceInsteadOfDrawing() {
        var game = game("steal", 3); var enemy = List.copyOf(game.redSkillHand());
        var next = cast(game, "steal").nextState();
        assertEquals(3, next.blueSkillHand().size()); assertEquals(enemy.size()-1, next.redSkillHand().size());
        assertEquals(game.skillDeck(), next.skillDeck()); assertEquals(1, next.blueMana());
        assertEquals(1, next.blueSkillHand().stream().filter(enemy::contains).count());
        assertEquals(0, next.redSkillHand().stream().filter(next.blueSkillHand()::contains).count());
        assertEquals(enemy, game.redSkillHand()); conservation(next);
        assertEquals(next.blueSkillHand(), cast(game, "steal").nextState().blueSkillHand());
    }

    @Test void spyRevealsOnlyToCasterAndExpiresAtEndTurn() {
        var game = game("spy", 3); var next = cast(game, "spy").nextState();
        assertEquals(game.redSkillHand(), RulesEngine.viewFor(next, PlayerColor.BLUE).revealedEnemySkills());
        assertTrue(RulesEngine.viewFor(next, PlayerColor.RED).revealedEnemySkills().isEmpty());
        assertTrue(RulesEngine.viewFor(game, PlayerColor.BLUE).revealedEnemySkills().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> RulesEngine.viewFor(next, PlayerColor.BLUE).revealedEnemySkills().clear());
        var ended = (ActionResult.Accepted) RulesEngine.apply(next, PlayerColor.BLUE, new GameAction.EndTurn());
        assertTrue(RulesEngine.viewFor(ended.nextState(), PlayerColor.BLUE).revealedEnemySkills().isEmpty());
        conservation(next);
    }

    @Test void emptyEnemyHandRejectsWithoutPaymentDrawOrRng() {
        for (String id : List.of("spy", "steal")) {
            var game = game(id, 3);
            game.mutableSkillDeck().addAll(game.redSkillHand()); game.mutableSkillHand(PlayerColor.RED).clear();
            long rng = game.randomState().state(); var hand = List.copyOf(game.blueSkillHand());
            assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(game, PlayerColor.BLUE,
                    new GameAction.CastSkill(id + "#1", new SkillTargets.None())));
            assertEquals(rng, game.randomState().state()); assertEquals(5, game.blueMana());
            assertEquals(hand, game.blueSkillHand()); assertTrue(game.spentSkills().isEmpty());
            assertFalse(Targeting.skillOptions(RulesEngine.viewFor(game, PlayerColor.BLUE), id+"#1", SelectionDraft.empty()).canSubmit());
            conservation(game);
        }
    }

    @Test void invalidTargetsAndInsufficientManaPreserveRandomTheft() {
        var game = game("steal", 3); long rng = game.randomState().state();
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(game, PlayerColor.BLUE,
                new GameAction.CastSkill("steal#1", new SkillTargets.Enemy(6))));
        game.setBlueMana(3);
        assertEquals(ActionError.INSUFFICIENT_MANA, assertInstanceOf(ActionResult.Rejected.class,
                RulesEngine.apply(game, PlayerColor.BLUE, new GameAction.CastSkill("steal#1", new SkillTargets.None()))).error());
        assertEquals(rng, game.randomState().state()); conservation(game);
    }

    @Test void drawTwoAddsTwoPlusNormalReplacement() {
        var game = game("draw-two", 3); var next = cast(game, "draw-two").nextState();
        assertEquals(5, next.blueSkillHand().size()); assertEquals(game.skillDeck().size()-3, next.skillDeck().size());
        assertTrue(next.destroyedSkills().isEmpty()); assertEquals(3, next.blueMana()); conservation(next);
    }

    @Test void fullHandTearsOverflowPermanentlyAndKeepsIdentitiesPrivate() throws Exception {
        var game = game("draw-two", 6); var deck = List.copyOf(game.skillDeck());
        var accepted = cast(game, "draw-two"); var next = accepted.nextState();
        assertEquals(6, next.blueSkillHand().size()); assertEquals(2, next.destroyedSkills().size());
        assertEquals(List.of(deck.get(1), deck.get(2)), next.destroyedSkills());
        assertTrue(next.blueSkillHand().contains(deck.get(0)), "casting frees one slot before any draws");
        assertEquals(2, accepted.events().stream().filter(e -> e instanceof PublicEvent.CardBurned).count());
        var blue = RulesEngine.viewFor(next, PlayerColor.BLUE); var red = RulesEngine.viewFor(next, PlayerColor.RED);
        assertEquals(next.destroyedSkills(), blue.ownBurnedDraws()); assertTrue(red.ownBurnedDraws().isEmpty());
        assertEquals(List.of(new CardInstance("draw-two#1", "draw-two")), red.spentSkills());
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(new GameUpdate(new MatchSync("burn", "ROOM", red, false, 1), accepted.events())); }
        String wire = bytes.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
        for (var burned : next.destroyedSkills()) assertFalse(wire.contains(burned.instanceId()));
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER); assertInstanceOf(GameUpdate.class, in.readObject());
        }
        conservation(next); conservation(game);
    }

    @Test void fiveCardHandBurnsOnlyTheNormalReplacement() {
        var game = game("draw-two", 5); var next = cast(game, "draw-two").nextState();
        assertEquals(6, next.blueSkillHand().size()); assertEquals(1, next.destroyedSkills().size()); conservation(next);
    }

    @Test void emptyDeckNeverRefillsFromDestroyedOrSpentCards() {
        var game = game("draw-two", 6);
        game.mutableSpentSkills().addAll(game.skillDeck()); game.mutableSkillDeck().clear();
        var next = cast(game, "draw-two").nextState();
        assertEquals(5, next.blueSkillHand().size()); assertTrue(next.skillDeck().isEmpty());
        assertTrue(next.destroyedSkills().isEmpty()); conservation(next);
    }

    @Test void refocusDiscardsOtherSkillsPermanentlyThenRedraws() {
        var game = game("refocus", 3); var old = List.copyOf(game.blueSkillHand());
        var next = cast(game, "refocus").nextState();
        assertEquals(3, next.blueSkillHand().size()); assertTrue(next.spentSkills().containsAll(old));
        assertTrue(next.blueSkillHand().stream().noneMatch(old::contains));
        assertEquals(game.skillDeck().size()-3, next.skillDeck().size()); conservation(next);
    }
    @Test void sabotageDiscardsAnEnemySkillAndDrawsOnlyItsOwnReplacement() {
        var game = game("sabotage", 3); var enemy = List.copyOf(game.redSkillHand());
        var next = cast(game, "sabotage").nextState();
        assertEquals(enemy.size()-1, next.redSkillHand().size());
        assertEquals(1, next.spentSkills().stream().filter(enemy::contains).count());
        assertEquals(3, next.blueSkillHand().size()); assertEquals(2, next.blueMana()); conservation(next);
    }

    @Test void barterExchangesRealInstancesAndRejectsWhenThereIsNoOtherOwnCard() {
        var game = game("barter", 3); var own = List.copyOf(game.blueSkillHand()); var enemy = List.copyOf(game.redSkillHand());
        var next = cast(game, "barter").nextState();
        assertEquals(1, next.blueSkillHand().stream().filter(enemy::contains).count());
        assertEquals(1, next.redSkillHand().stream().filter(own::contains).count());
        assertEquals(enemy.size(), next.redSkillHand().size()); conservation(next);
        var alone = game("barter", 1); long rng = alone.randomState().state();
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(alone, PlayerColor.BLUE,
                new GameAction.CastSkill("barter#1", new SkillTargets.None())));
        assertEquals(rng, alone.randomState().state()); conservation(alone);
    }

    @Test void siphonTransfersOnlyAvailableManaAfterPayingTheCost() {
        var game = game("siphon", 3); game.setRedMana(1);
        var next = cast(game, "siphon").nextState();
        assertEquals(4, next.blueMana()); assertEquals(0, next.redMana()); conservation(next);
        game.setRedMana(0);
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(game, PlayerColor.BLUE,
                new GameAction.CastSkill("siphon#1", new SkillTargets.None())));
        assertEquals(5, game.blueMana());
    }

    @Test void allHandControlSkillsAlsoWorkForRedUsingOnlyItsPermittedView() {
        for (String id : List.of("steal", "spy", "draw-two", "sabotage", "barter", "siphon")) {
            var game = game(id, 3);
            var blue = List.copyOf(game.blueSkillHand()); var red = List.copyOf(game.redSkillHand());
            game.mutableSkillHand(PlayerColor.BLUE).clear(); game.mutableSkillHand(PlayerColor.BLUE).addAll(red);
            game.mutableSkillHand(PlayerColor.RED).clear(); game.mutableSkillHand(PlayerColor.RED).addAll(blue);
            game.setTurn(PlayerColor.RED); game.setRedMana(5); game.setBlueMana(3);
            var view = RulesEngine.viewFor(game, PlayerColor.RED);
            var options = Targeting.skillOptions(view, id+"#1", SelectionDraft.empty());
            assertTrue(options.canSubmit(), id);
            var result = assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(game, PlayerColor.RED,
                    new GameAction.CastSkill(id+"#1", options.completeTargets())));
            conservation(result.nextState());
            assertEquals(TurnPhase.END, result.nextState().phase());
            assertTrue(RulesEngine.viewFor(result.nextState(), PlayerColor.BLUE).revealedEnemySkills().isEmpty());
        }
    }

}
