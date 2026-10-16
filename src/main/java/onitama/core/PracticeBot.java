package onitama.core;

import onitama.core.duel.CardInstance;
import onitama.core.duel.Direction;
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

        // 25% chance for Rookie to save mana and skip skill casting
        if (difficulty == Difficulty.ROOKIE && random.nextInt(100) < 25) {
            return new GameAction.EndTurn();
        }

        for (CardInstance card : view.ownSkillHand()) {
            SkillDefinition def = view.rules().skill(card.definitionId());
            if (def == null || def.manaCost() > mana) {
                continue;
            }

            // Zero-target skills (Mana Surge, Refocus)
            SelectionOptions initial = Targeting.skillOptions(view, card.instanceId(), SelectionDraft.empty());
            if (initial.error() == null && initial.canSubmit()) {
                return new GameAction.CastSkill(card.instanceId(), initial.completeTargets());
            }

            // Single piece target search
            if (initial.error() == null && !initial.selectablePieceIds().isEmpty()) {
                for (int pieceId : initial.selectablePieceIds()) {
                    SelectionDraft d1 = new SelectionDraft(List.of(pieceId), List.of(), null);
                    SelectionOptions o1 = Targeting.skillOptions(view, card.instanceId(), d1);
                    if (o1.canSubmit()) {
                        return new GameAction.CastSkill(card.instanceId(), o1.completeTargets());
                    }

                    if (!o1.selectableSquares().isEmpty()) {
                        Square targetSq = o1.selectableSquares().get(random.nextInt(o1.selectableSquares().size()));
                        SelectionDraft d2 = new SelectionDraft(List.of(pieceId), List.of(targetSq), null);
                        SelectionOptions o2 = Targeting.skillOptions(view, card.instanceId(), d2);
                        if (o2.canSubmit()) {
                            return new GameAction.CastSkill(card.instanceId(), o2.completeTargets());
                        }
                    }

                    if (!o1.selectableDirections().isEmpty()) {
                        Direction dir = o1.selectableDirections().get(random.nextInt(o1.selectableDirections().size()));
                        SelectionDraft d2 = new SelectionDraft(List.of(pieceId), List.of(), dir);
                        SelectionOptions o2 = Targeting.skillOptions(view, card.instanceId(), d2);
                        if (o2.canSubmit()) {
                            return new GameAction.CastSkill(card.instanceId(), o2.completeTargets());
                        }
                    }
                }
            }
        }

        return new GameAction.EndTurn();
    }
}
