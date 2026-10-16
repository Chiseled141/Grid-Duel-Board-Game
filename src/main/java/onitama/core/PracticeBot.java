package onitama.core;

import onitama.core.duel.CardInstance;
import onitama.core.duel.SkillTargets;
import onitama.core.duel.GameAction;
import onitama.core.duel.PlacedPiece;
import onitama.core.duel.PlayerView;
import onitama.core.duel.SelectionDraft;
import onitama.core.duel.SelectionOptions;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.Targeting;
import onitama.core.duel.TurnPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.Random;

/**
 * Built-in bot policy for Grid Duel practice and load testing.
 *
 * <p>Pure policy: receives only {@link PlayerView} and public definitions. Never accesses
 * hidden opponent skills, deck ordering, or match RNG. Handles all phases: movement,
 * skill casting, Foresight choice, and turn ending without hanging.
 */
public final class PracticeBot {

    private PracticeBot() {
    }

    /**
     * Chooses an action purely from the given PlayerView.
     */
    public static GameAction chooseAction(PlayerView view, Random random) {
        return chooseAction(view, Difficulty.ROOKIE, random);
    }

    /**
     * Chooses an action purely from the given PlayerView with the given difficulty setting.
     */
    public static GameAction chooseAction(PlayerView view, Difficulty difficulty, Random random) {
        Objects.requireNonNull(view, "view must not be null");
        Objects.requireNonNull(random, "random must not be null");

        if (view.outcome() != null || view.viewer() != view.turn()) {
            return null;
        }

        TurnPhase phase = view.phase();
        return switch (phase) {
            case CHOICE -> chooseForesight(view, random);
            case MOVEMENT -> chooseMovement(view, difficulty, random);
            case SKILL -> chooseSkill(view, difficulty, random);
            case END -> new GameAction.EndTurn();
            case FINISHED -> null;
        };
    }

    private static GameAction chooseForesight(PlayerView view, Random random) {
        if (view.ownChoice() == null) {
            return null;
        }
        List<CardInstance> preview = view.ownChoice().preview();
        List<String> order = new ArrayList<>(preview.stream().map(CardInstance::instanceId).toList());
        Collections.shuffle(order, random);
        return new GameAction.ResolveForesight(view.ownChoice().choiceId(), order);
    }

    private static GameAction chooseMovement(PlayerView view, Difficulty difficulty, Random random) {
        List<GameAction.MovePiece> legal = Targeting.legalMoves(view);
        if (legal.isEmpty()) {
            return new GameAction.SkipMovement();
        }

        List<GameAction.MovePiece> captures = new ArrayList<>();
        for (GameAction.MovePiece m : legal) {
            boolean isEnemy = view.pieces().stream().anyMatch(
                    pp -> pp.square().equals(m.to()) && pp.piece().color() != view.viewer());
            if (isEnemy) {
                captures.add(m);
            }
        }

        // Higher difficulty always prioritizes captures
        if (difficulty != Difficulty.ROOKIE && !captures.isEmpty()) {
            return captures.get(random.nextInt(captures.size()));
        }

        // Rookie has an 80% preference for captures
        if (!captures.isEmpty() && random.nextInt(100) < 80) {
            return captures.get(random.nextInt(captures.size()));
        }

        return legal.get(random.nextInt(legal.size()));
    }

    private static GameAction chooseSkill(PlayerView view, Difficulty difficulty, Random random) {
        int mana = view.viewer() == PlayerColor.BLUE ? view.blueMana() : view.redMana();
        List<GameAction.CastSkill> candidates = new ArrayList<>();
        List<CardInstance> hand = new ArrayList<>(view.ownSkillHand());
        Collections.shuffle(hand, random);
        for (CardInstance card : hand) {
            SkillDefinition def = view.rules().skill(card.definitionId());
            if (def == null || def.manaCost() > mana) continue;
            // Spending this single-use resource at full mana cannot help.
            if (def.id().equals("mana-surge") && mana >= view.rules().manaCap()) continue;
            List<GameAction.CastSkill> targets = new ArrayList<>();
            searchSkill(view, card.instanceId(), SelectionDraft.empty(), new HashSet<>(), targets, random);
            candidates.addAll(targets);
        }
        if (candidates.isEmpty()) return new GameAction.EndTurn();
        if (difficulty == Difficulty.ROOKIE) return candidates.get(random.nextInt(candidates.size()));
        // Stronger opponents prioritize eliminations and complete multi-target effects.
        Collections.shuffle(candidates, random);
        return candidates.stream().max(java.util.Comparator.comparingInt(PracticeBot::skillValue)).orElseThrow();
    }

    private static void searchSkill(PlayerView view, String instanceId, SelectionDraft draft,
                                    Set<SelectionDraft> visited, List<GameAction.CastSkill> results, Random random) {
        // Targeting is shared with the UI. A bounded search keeps practice responsive
        // and never constructs authoritative state or guesses hidden information.
        if (visited.size() >= 192 || results.size() >= 24 || !visited.add(draft)) return;
        SelectionOptions options = Targeting.skillOptions(view, instanceId, draft);
        if (options.error() != null) return;
        if (options.canSubmit()) results.add(new GameAction.CastSkill(instanceId, options.completeTargets()));
        List<SelectionDraft> next = new ArrayList<>();
        for (int piece : options.selectablePieceIds()) {
            if (draft.pieceIds().contains(piece)) continue;
            var pieces = new ArrayList<>(draft.pieceIds()); pieces.add(piece);
            next.add(new SelectionDraft(pieces, draft.destinations(), draft.direction()));
        }
        for (Square square : options.selectableSquares()) {
            if (draft.destinations().contains(square)) continue;
            var squares = new ArrayList<>(draft.destinations()); squares.add(square);
            next.add(new SelectionDraft(draft.pieceIds(), squares, draft.direction()));
        }
        if (draft.direction() == null) for (var direction : options.selectableDirections())
            next.add(new SelectionDraft(draft.pieceIds(), draft.destinations(), direction));
        Collections.shuffle(next, random);
        for (var child : next) searchSkill(view, instanceId, child, visited, results, random);
    }

    private static int skillValue(GameAction.CastSkill cast) {
        SkillTargets target = cast.targets();
        if (target instanceof SkillTargets.Attack attack) return 100 * attack.enemyIds().size();
        if (target instanceof SkillTargets.Enemy) return 100;
        if (target instanceof SkillTargets.MoveGroup group) return 10 + group.moves().size();
        if (target instanceof SkillTargets.Rally rally) return 10 + rally.moves().size();
        if (target instanceof SkillTargets.ShiftEnemies shifted) return 10 + shifted.enemyIds().size();
        if (target instanceof SkillTargets.None) return 1;
        return 10;
    }
}
