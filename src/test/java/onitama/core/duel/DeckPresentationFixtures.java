package onitama.core.duel;

import java.util.ArrayList;
import java.util.List;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;

/** Legal engine transitions for UI tests, with deterministic public/private projections. */
public final class DeckPresentationFixtures {
    private DeckPresentationFixtures() { }

    /** A real accepted action with its authoritative starting state, used only by tests. */
    public record Transition(GameState before, ActionResult.Accepted result) {
        public PlayerView before(PlayerColor viewer) { return RulesEngine.viewFor(before, viewer); }
        public PlayerView after(PlayerColor viewer) { return RulesEngine.viewFor(result.nextState(), viewer); }
    }

    /** Finds a deterministic normal movement, including immediate same-card redraw. */
    public static Transition movement(PlayerColor actor, boolean sameCard) {
        var rules = RulesConfigLoader.load(null);
        for (int seed = 0; seed < 2000; seed++) {
            var game = RulesEngine.newGame(rules, seed);
            if (game.turn() != actor) continue;
            var move = Targeting.legalMoves(RulesEngine.viewFor(game, actor)).get(0);
            var result = (ActionResult.Accepted) RulesEngine.apply(game, actor, move);
            var hand = actor == PlayerColor.BLUE ? result.nextState().blueMovementHand() : result.nextState().redMovementHand();
            return new Transition(game, result);
        }
        throw new AssertionError("No movement fixture");
    }

    /** Casts a resource skill with enough mana; trimmed deck cards represent earlier spent cards. */
    public static Transition skill(String definition, PlayerColor actor, int deckSize) {
        return skill(definition, actor, deckSize, false);
    }

    /** Finds a Refocus that redraws the same two cards, so hand differences alone cannot detect it. */
    public static Transition refocusSameHand(PlayerColor actor) {
        return skill("refocus", actor, 18, true);
    }

    private static Transition skill(String definition, PlayerColor actor, int deckSize, boolean sameHand) {
        var base = RulesConfigLoader.load(null);
        var rules = new RulesConfig(base.rulesVersion(), 0, 3, 5, 1, 7, 60, base.movementCards(), base.skills());
        for (int seed = 0; seed < 2000; seed++) {
            var start = RulesEngine.newGame(rules, seed);
            if (start.turn() != actor) continue;
            var hand = actor == PlayerColor.BLUE ? start.blueSkillHand() : start.redSkillHand();
            var skill = hand.stream().filter(c -> c.definitionId().equals(definition)).findFirst();
            if (skill.isEmpty()) continue;
            var spent = new ArrayList<>(start.spentSkills());
            spent.addAll(start.skillDeck().subList(deckSize, start.skillDeck().size()));
            var game = new GameState(rules, start.board(), start.blueMovementHand(), start.redMovementHand(),
                    start.blueSkillHand(), start.redSkillHand(), start.movementDeck(),
                    List.copyOf(start.skillDeck().subList(0, deckSize)), spent,
                    start.blueMana(), start.redMana(), actor, TurnPhase.SKILL, 1, 0, 1, new GameRandom(seed), null);
            var result = (ActionResult.Accepted) RulesEngine.apply(game, actor,
                    new GameAction.CastSkill(skill.get().instanceId(), new SkillTargets.None()));
            if (sameHand) {
                var beforeMoves = actor == PlayerColor.BLUE ? game.blueMovementHand() : game.redMovementHand();
                var afterMoves = actor == PlayerColor.BLUE ? result.nextState().blueMovementHand() : result.nextState().redMovementHand();
                if (!new java.util.HashSet<>(beforeMoves).equals(new java.util.HashSet<>(afterMoves))) continue;
            }
            return new Transition(game, result);
        }
        throw new AssertionError("No skill fixture");
    }

    /** Six-card hand casting Draw Two, producing one held draw and two overflow tears. */
    public static Transition fullHandDraw(PlayerColor actor) {
        var base = RulesConfigLoader.load(null);
        var rules = new RulesConfig(base.rulesVersion(), 0, 6, 5, 1, 5, 60, List.of(), base.skills());
        for (int seed = 0; seed < 1000; seed++) {
            var game = RulesEngine.newGame(rules, seed);
            if (game.turn() != actor) continue;
            var card = game.mutableSkillHand(actor).stream().filter(c -> c.definitionId().equals("draw-two")).findFirst();
            if (card.isEmpty()) continue;
            game.setPhase(TurnPhase.SKILL);
            var result = (ActionResult.Accepted) RulesEngine.apply(game, actor,
                    new GameAction.CastSkill(card.get().instanceId(), new SkillTargets.None()));
            return new Transition(game, result);
        }
        throw new AssertionError("No full-hand fixture");
    }

    /** Resolves an actual pending Foresight; its event list is intentionally empty. */
    public static Transition resolveForesight(PlayerColor actor) {
        var choice = skill("foresight", actor, 18).result().nextState();
        var pending = choice.pendingChoice();
        var result = (ActionResult.Accepted) RulesEngine.apply(choice, actor, new GameAction.ResolveForesight(
                pending.choiceId(), pending.preview().stream().map(CardInstance::instanceId).toList()));
        return new Transition(choice, result);
    }
}
