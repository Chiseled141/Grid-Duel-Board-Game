package onitama.core.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import onitama.core.PlayerColor;
import onitama.core.Square;

/**
 * Pure rules engine for Grid Duel. No I/O, no mutable global state.
 */
public final class RulesEngine {

    private RulesEngine() {}

    /**
     * Creates an already-started position for simulations; live routes use newOpeningGame.
     */
    public static GameState newGame(RulesConfig rules, long seed) {
        Objects.requireNonNull(rules, "rules must not be null");

        List<String> movementDeck = new ArrayList<>();

        // Canonical definition order makes seeded deals independent of config list ordering.
        Map<String, SkillDefinition> skillMap = rules.skills().stream()
                .collect(Collectors.toMap(SkillDefinition::id, s -> s));
        List<CardInstance> skillDeck = new ArrayList<>();
        for (String defId : RulesConfig.CANONICAL_SKILL_IDS) {
            SkillDefinition def = skillMap.get(defId);
            if (def != null) {
                for (int ordinal = 1; ordinal <= def.copies(); ordinal++) {
                    skillDeck.add(new CardInstance(defId + "#" + ordinal, defId));
                }
            }
        }

        // Copyable randomness keeps rejected candidate actions atomic.
        GameRandom rng = new GameRandom(seed);

        rng.shuffle(skillDeck);

        // Reserved movement slots remain empty in every live match.
        List<String> blueMovementHand = new ArrayList<>();
        List<String> redMovementHand = new ArrayList<>();
        // Alternate the skill deal between the players.
        List<CardInstance> blueSkillHand = new ArrayList<>();
        List<CardInstance> redSkillHand = new ArrayList<>();
        for (int i = 0; i < rules.skillHandSize(); i++) {
            blueSkillHand.add(skillDeck.remove(0));
            redSkillHand.add(skillDeck.remove(0));
        }

        // Starter selection: 0 Blue, 1 Red.
        PlayerColor starter = rng.nextInt(2) == 0 ? PlayerColor.BLUE : PlayerColor.RED;

        // Simulations start immediately; newOpeningGame defers this reward until both confirm.
        int blueMana = rules.initialMana();
        int redMana = rules.initialMana();
        if (starter == PlayerColor.BLUE) {
            blueMana = Math.min(rules.manaCap(), blueMana + rules.manaPerTurn());
        } else {
            redMana = Math.min(rules.manaCap(), redMana + rules.manaPerTurn());
        }

        Board board = Board.newGame();

        return new GameState(
                rules,
                board,
                blueMovementHand,
                redMovementHand,
                blueSkillHand,
                redSkillHand,
                movementDeck,
                skillDeck,
                new ArrayList<>(),
                blueMana,
                redMana,
                starter,
                TurnPhase.MOVEMENT,
                1,
                0,
                0L,
                rng,
                null, ObjectiveLayout.generate(rng));
    }

    /** Creates a live match awaiting both players' private opening skill choices. */
    public static GameState newOpeningGame(RulesConfig rules, long seed) {
        GameState state = newGame(rules, seed);
        state.setPhase(TurnPhase.OPENING);
        state.setStartedTurns(0);
        state.setBlueMana(rules.initialMana());
        state.setRedMana(rules.initialMana());
        return state;
    }

    private static ActionResult applyOpening(GameState state, PlayerColor actor, GameAction.ConfirmOpening action) {
        if (state.phase() != TurnPhase.OPENING || state.openingConfirmed(actor))
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        List<String> ids = action.replaceInstanceIds();
        if (ids.size() != new java.util.HashSet<>(ids).size() || ids.size() > state.skillDeck().size())
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        List<CardInstance> hand = state.mutableSkillHand(actor);
        if (!hand.stream().map(CardInstance::instanceId).toList().containsAll(ids))
            return new ActionResult.Rejected(ActionError.CARD_NOT_HELD);
        GameState next = new GameState(state);
        List<CardInstance> returned = new ArrayList<>();
        for (int slot = 0; slot < hand.size(); slot++) if (ids.contains(hand.get(slot).instanceId())) {
            returned.add(hand.get(slot));
            next.mutableSkillHand(actor).set(slot, next.mutableSkillDeck().remove(0));
        }
        if (!returned.isEmpty()) {
            next.mutableSkillDeck().addAll(returned);
            next.randomState().shuffle(next.mutableSkillDeck());
        }
        next.confirmOpening(actor);
        List<PublicEvent> events = List.of();
        if (next.openingConfirmed(actor.opponent())) {
            next.setPhase(TurnPhase.MOVEMENT);
            next.setStartedTurns(1);
            int mana = Math.min(next.rules().manaCap(), next.rules().initialMana() + next.rules().manaPerTurn());
            if (next.turn() == PlayerColor.BLUE) next.setBlueMana(mana); else next.setRedMana(mana);
            events = List.of(new PublicEvent.TurnStarted(next.turn(), 1));
        }
        next.setRevision(state.revision() + 1);
        return new ActionResult.Accepted(next, events);
    }

    /**
     * Pure state transition: applies an action to a state. Input state is never mutated.
     */
    public static ActionResult apply(GameState state, PlayerColor actor, GameAction action) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(action, "action must not be null");

        if (state.phase() == TurnPhase.FINISHED) {
            return new ActionResult.Rejected(ActionError.GAME_FINISHED);
        }

        if (action instanceof GameAction.Resign) {
            return applyResign(state, actor);
        }

        if (action instanceof GameAction.ConfirmOpening opening) return applyOpening(state, actor, opening);
        if (state.phase() == TurnPhase.OPENING) return new ActionResult.Rejected(ActionError.WRONG_PHASE);

        if (actor != state.turn()) {
            return new ActionResult.Rejected(ActionError.NOT_YOUR_TURN);
        }

        if (action instanceof GameAction.MovePiece move) {
            return applyMove(state, actor, move);
        } else if (action instanceof GameAction.SkipMovement) {
            return applySkipMovement(state, actor);
        } else if (action instanceof GameAction.CastSkill cast) {
            return applyCastSkill(state, actor, cast);
        } else if (action instanceof GameAction.ResolveForesight resolve) {
            return applyResolveForesight(state, actor, resolve);
        } else if (action instanceof GameAction.EndTurn) {
            return applyEndTurn(state, actor);
        } else {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }
    }

    /**
     * Host-triggered forfeit on grace expiry. Terminal outcome for loser.
     */
    public static ActionResult forfeit(GameState state, PlayerColor loser) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(loser, "loser must not be null");

        if (state.phase() == TurnPhase.FINISHED) {
            return new ActionResult.Rejected(ActionError.GAME_FINISHED);
        }

        GameState next = new GameState(state);
        next.setPendingChoice(null);
        next.setPhase(TurnPhase.FINISHED);
        GameOutcome outcome = new GameOutcome(loser.opponent(), EndReason.FORFEIT);
        next.setOutcome(outcome);
        next.setRevision(state.revision() + 1);

        List<PublicEvent> events = List.of(new PublicEvent.MatchEnded(outcome));
        return new ActionResult.Accepted(next, events);
    }

    private static ActionResult applyResign(GameState state, PlayerColor actor) {
        GameState next = new GameState(state);
        next.setPendingChoice(null);
        next.setPhase(TurnPhase.FINISHED);
        GameOutcome outcome = new GameOutcome(actor.opponent(), EndReason.FORFEIT);
        next.setOutcome(outcome);
        next.setRevision(state.revision() + 1);

        List<PublicEvent> events = List.of(new PublicEvent.MatchEnded(outcome));
        return new ActionResult.Accepted(next, events);
    }

    private static ActionResult applySkipMovement(GameState state, PlayerColor actor) {
        if (state.phase() != TurnPhase.MOVEMENT) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }
        GameState next = new GameState(state);
        next.setPhase(TurnPhase.SKILL);
        next.setRevision(state.revision() + 1);
        return new ActionResult.Accepted(next, List.of());
    }

    private static ActionResult applyEndTurn(GameState state, PlayerColor actor) {
        if (state.phase() != TurnPhase.MOVEMENT
                && state.phase() != TurnPhase.SKILL
                && state.phase() != TurnPhase.END) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }

        GameState next = new GameState(state);
        next.clearIntel();
        int completed = next.completedTurns() + 1;
        next.setCompletedTurns(completed);

        if (completed >= next.rules().turnLimit()) {
            next.setPhase(TurnPhase.FINISHED);
            GameOutcome outcome = new GameOutcome(null, EndReason.DRAW);
            next.setOutcome(outcome);
            next.setRevision(state.revision() + 1);
            return new ActionResult.Accepted(next, List.of(new PublicEvent.MatchEnded(outcome)));
        }

        PlayerColor nextTurn = actor.opponent();
        next.setTurn(nextTurn);
        next.setStartedTurns(next.startedTurns() + 1);
        if (nextTurn == PlayerColor.BLUE) {
            next.setBlueMana(Math.min(next.rules().manaCap(), next.blueMana() + next.rules().manaPerTurn() + next.objectives().manaBonus(next.board(), nextTurn)));
        } else {
            next.setRedMana(Math.min(next.rules().manaCap(), next.redMana() + next.rules().manaPerTurn() + next.objectives().manaBonus(next.board(), nextTurn)));
        }
        next.setPhase(TurnPhase.MOVEMENT);
        next.setRevision(state.revision() + 1);

        next.addScore(nextTurn, next.objectives().scoreBonus(next.board(), nextTurn));
        List<PublicEvent> events = new ArrayList<>();
        events.add(new PublicEvent.TurnStarted(nextTurn, next.startedTurns()));
        finishVictory(next, nextTurn, events);
        return new ActionResult.Accepted(next, events);
    }

    private static ActionResult applyMove(GameState state, PlayerColor actor, GameAction.MovePiece move) {
        if (state.phase() != TurnPhase.MOVEMENT) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }

        Square from = state.board().findPiece(move.pieceId());
        if (from == null) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }
        Piece movingPiece = state.board().pieceAt(from);
        if (movingPiece == null || movingPiece.color() != actor) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }

        if (!Board.isInside(move.to())) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }

        Piece destPiece = state.board().pieceAt(move.to());
        if (destPiece != null && destPiece.color() == actor) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }

        if (Math.abs(from.x() - move.to().x()) + Math.abs(from.y() - move.to().y()) != 1) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }

        // Action is valid: deep copy state and apply
        GameState next = new GameState(state);

        // 1. Relocate piece and capture if destination occupied
        Piece captured = next.board().move(from, move.to());

        // 3. Events in deterministic documented order
        List<PublicEvent> events = new ArrayList<>();
        events.add(new PublicEvent.PieceMoved(move.pieceId(), from, move.to()));
        if (captured != null) {
            events.add(new PublicEvent.PieceEliminated(captured.id(), move.to()));
        }

        awardEliminations(state, next, actor);
        if (!finishVictory(next, actor, events)) next.setPhase(TurnPhase.SKILL);

        next.setRevision(state.revision() + 1);
        return new ActionResult.Accepted(next, List.copyOf(events));
    }

    private static ActionResult applyCastSkill(GameState state, PlayerColor actor, GameAction.CastSkill cast) {
        if (state.phase() != TurnPhase.SKILL) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }

        List<CardInstance> hand = actor == PlayerColor.BLUE ? state.blueSkillHand() : state.redSkillHand();
        CardInstance heldInstance = null;
        for (CardInstance c : hand) {
            if (c.instanceId().equals(cast.skillInstanceId())) {
                heldInstance = c;
                break;
            }
        }
        if (heldInstance == null) {
            return new ActionResult.Rejected(ActionError.CARD_NOT_HELD);
        }

        SkillDefinition definition = null;
        for (SkillDefinition def : state.rules().skills()) {
            if (def.id().equals(heldInstance.definitionId())) {
                definition = def;
                break;
            }
        }
        if (definition == null) {
            return new ActionResult.Rejected(ActionError.CARD_NOT_HELD);
        }

        int cost = definition.manaCost();
        int currentMana = actor == PlayerColor.BLUE ? state.blueMana() : state.redMana();
        if (currentMana < cost) {
            return new ActionResult.Rejected(ActionError.INSUFFICIENT_MANA);
        }

        // Handle Foresight special two-action commitment protocol
        if (definition.id().equals("foresight")) {
            if (!(cast.targets() instanceof SkillTargets.None)) {
                return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
            }
            int lookahead = definition.parameters().getOrDefault(SkillParameter.LOOKAHEAD, 3);
            int previewSize = Math.min(lookahead, state.skillDeck().size());

            if (previewSize >= 2) {
                // Enters CHOICE phase
                GameState next = new GameState(state);
                long choiceId = state.revision() + 1;
                List<CardInstance> preview = List.copyOf(state.skillDeck().subList(0, previewSize));
                next.setPhase(TurnPhase.CHOICE);
                next.setPendingChoice(new PendingForesight(actor, heldInstance, cost, choiceId, preview));
                next.setRevision(choiceId);
                List<PublicEvent> events = List.of(new PublicEvent.SkillCommitted(actor, heldInstance));
                return new ActionResult.Accepted(next, events);
            } else {
                // 0 or 1 preview cards: resolve immediately in the same action
                GameState next = new GameState(state);
                if (actor == PlayerColor.BLUE) {
                    next.setBlueMana(currentMana - cost);
                } else {
                    next.setRedMana(currentMana - cost);
                }
                next.consumeHeldSkill(actor, heldInstance.instanceId());
                next.setPhase(TurnPhase.END);
                next.setRevision(state.revision() + 1);
                List<PublicEvent> events = List.of(new PublicEvent.SkillCommitted(actor, heldInstance));
                return new ActionResult.Accepted(next, events);
            }
        }

        // Deep copy state before mutating candidate
        GameState next = new GameState(state);
        List<PublicEvent> events = new ArrayList<>();

        // Pay on the candidate before effects apply capped resource gains.
        // A rejected effect discards this candidate, preserving the original state.
        if (actor == PlayerColor.BLUE) {
            next.setBlueMana(currentMana - cost);
        } else {
            next.setRedMana(currentMana - cost);
        }
        next.spendSkill(actor, heldInstance.instanceId());
        ActionError error = SkillEffects.resolve(definition, state, next, actor, cast.targets(), events);
        if (error != null) {
            return new ActionResult.Rejected(error);
        }

        // Add SkillCommitted event first
        events.add(0, new PublicEvent.SkillCommitted(actor, heldInstance));

        // The played card has already freed its hand slot; theft replaces the normal draw.
        if (!definition.id().equals("steal")) next.drawSkills(actor, 1, events);

        awardEliminations(state, next, actor);
        if (!finishVictory(next, actor, events)) next.setPhase(TurnPhase.END);

        next.setRevision(state.revision() + 1);
        return new ActionResult.Accepted(next, List.copyOf(events));
    }

    private static ActionResult applyResolveForesight(
            GameState state, PlayerColor actor, GameAction.ResolveForesight resolve) {
        if (state.phase() != TurnPhase.CHOICE || state.pendingChoice() == null) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }

        PendingForesight pending = state.pendingChoice();
        if (pending.actor() != actor) {
            return new ActionResult.Rejected(ActionError.NOT_YOUR_TURN);
        }

        if (resolve.choiceId() != pending.choiceId()) {
            return new ActionResult.Rejected(ActionError.INVALID_CHOICE);
        }

        List<CardInstance> preview = pending.preview();
        List<String> submitted = resolve.orderedInstanceIds();
        if (submitted.size() != preview.size()) {
            return new ActionResult.Rejected(ActionError.INVALID_CHOICE);
        }

        java.util.Set<String> submittedSet = new java.util.HashSet<>(submitted);
        if (submittedSet.size() != submitted.size()) {
            return new ActionResult.Rejected(ActionError.INVALID_CHOICE);
        }

        java.util.Map<String, CardInstance> previewMap = preview.stream()
                .collect(java.util.stream.Collectors.toMap(CardInstance::instanceId, c -> c));
        if (!submittedSet.equals(previewMap.keySet())) {
            return new ActionResult.Rejected(ActionError.INVALID_CHOICE);
        }

        // Action is valid: deep copy state and reorder preview prefix
        GameState next = new GameState(state);
        for (int i = 0; i < submitted.size(); i++) {
            next.mutableSkillDeck().set(i, previewMap.get(submitted.get(i)));
        }

        // Deduct reserved mana
        int reserved = pending.reservedMana();
        if (actor == PlayerColor.BLUE) {
            next.setBlueMana(next.blueMana() - reserved);
        } else {
            next.setRedMana(next.redMana() - reserved);
        }

        // Spend reserved card and draw replacement (which draws the newly first card)
        next.consumeHeldSkill(actor, pending.skillInstance().instanceId());
        next.setPendingChoice(null);
        next.setPhase(TurnPhase.END);
        next.setRevision(state.revision() + 1);

        return new ActionResult.Accepted(next, List.of());
    }

    private static void awardEliminations(GameState before, GameState after, PlayerColor actor) {
        int eliminated = before.board().countPieces(actor.opponent()) - after.board().countPieces(actor.opponent());
        after.addScore(actor, eliminated);
    }

    private static boolean finishVictory(GameState state, PlayerColor actor, List<PublicEvent> events) {
        EndReason reason = state.board().countPieces(actor.opponent()) == 0 ? EndReason.ELIMINATION
                : state.score(actor) >= state.rules().scoreToWin() ? EndReason.SCORE : null;
        if (reason == null) return false;
        state.setPhase(TurnPhase.FINISHED);
        state.setOutcome(new GameOutcome(actor, reason));
        events.add(new PublicEvent.MatchEnded(state.outcome()));
        return true;
    }

    /**
     * Projects authoritative state into recipient-specific immutable player view.
     */
    public static PlayerView project(GameState state, PlayerColor viewer) {
        return viewFor(state, viewer);
    }

    /**
     * Projects authoritative state into recipient-specific immutable player view.
     */
    public static PlayerView viewFor(GameState state, PlayerColor viewer) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(viewer, "viewer must not be null");

        List<CardInstance> ownSkillHand = viewer == PlayerColor.BLUE
                ? state.blueSkillHand()
                : state.redSkillHand();
        int opponentSkillCount = viewer == PlayerColor.BLUE
                ? state.redSkillHand().size()
                : state.blueSkillHand().size();

        int reservedMana = 0;
        ForesightView ownChoice = null;
        if (state.pendingChoice() != null && state.pendingChoice().actor() == viewer) {
            reservedMana = state.pendingChoice().reservedMana();
            ownChoice = new ForesightView(
                    state.pendingChoice().choiceId(),
                    state.pendingChoice().skillInstance().instanceId(),
                    state.pendingChoice().reservedMana(),
                    state.pendingChoice().preview());
        }

        return new PlayerView(
                viewer,
                state.rules(),
                state.revision(),
                state.board().placedPieces(),
                state.turn(),
                state.phase(),
                state.startedTurns(),
                state.completedTurns(),
                state.blueMovementHand(),
                state.redMovementHand(),
                ownSkillHand,
                opponentSkillCount,
                state.blueMana(),
                state.redMana(),
                reservedMana,
                state.movementDeck().size(),
                state.skillDeck().size(),
                state.spentSkills(),
                ownChoice,
                state.outcome(), state.score(PlayerColor.BLUE), state.score(PlayerColor.RED), state.objectives(),
                state.openingConfirmed(viewer), state.openingConfirmed(viewer.opponent()), state.intelFor(viewer), state.burnedDrawsFor(viewer));
    }
}
