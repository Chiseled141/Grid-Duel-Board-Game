package onitama.core.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.Offset;
import onitama.core.PlayerColor;
import onitama.core.Square;

/**
 * Pure rules engine for Grid Duel. No I/O, no mutable global state.
 */
public final class RulesEngine {

    private RulesEngine() {}

    /**
     * Creates a new match in the standard initial state with deterministic deals and starting mana.
     */
    public static GameState newGame(RulesConfig rules, long seed) {
        Objects.requireNonNull(rules, "rules must not be null");

        // 1. Build movement deck in Appendix A order from CardDeck.catalog()
        List<String> movementDeck = CardDeck.catalog().stream()
                .map(Card::id)
                .collect(Collectors.toCollection(ArrayList::new));

        // 2. Build skill instances in Appendix B order, ordinal order within definition
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

        // 3. Deterministic SplitMix64 RNG
        GameRandom rng = new GameRandom(seed);

        // 4. Shuffle movement deck
        rng.shuffle(movementDeck);

        // 5. Shuffle skill deck
        rng.shuffle(skillDeck);

        // 6. Deal movement cards alternately Blue, Red, Blue, Red
        List<String> blueMovementHand = new ArrayList<>();
        List<String> redMovementHand = new ArrayList<>();
        for (int i = 0; i < rules.movementHandSize(); i++) {
            blueMovementHand.add(movementDeck.remove(0));
            redMovementHand.add(movementDeck.remove(0));
        }

        // 7. Deal skills alternately Blue then Red
        List<CardInstance> blueSkillHand = new ArrayList<>();
        List<CardInstance> redSkillHand = new ArrayList<>();
        for (int i = 0; i < rules.skillHandSize(); i++) {
            blueSkillHand.add(skillDeck.remove(0));
            redSkillHand.add(skillDeck.remove(0));
        }

        // 8. Starter selection: 0 Blue, 1 Red
        PlayerColor starter = rng.nextInt(2) == 0 ? PlayerColor.BLUE : PlayerColor.RED;

        // 9. Initial mana: starter gains manaPerTurn
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
                null);
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
            next.setBlueMana(Math.min(next.rules().manaCap(), next.blueMana() + next.rules().manaPerTurn()));
        } else {
            next.setRedMana(Math.min(next.rules().manaCap(), next.redMana() + next.rules().manaPerTurn()));
        }
        next.setPhase(TurnPhase.MOVEMENT);
        next.setRevision(state.revision() + 1);

        List<PublicEvent> events = List.of(new PublicEvent.TurnStarted(nextTurn, next.startedTurns()));
        return new ActionResult.Accepted(next, events);
    }

    private static ActionResult applyMove(GameState state, PlayerColor actor, GameAction.MovePiece move) {
        if (state.phase() != TurnPhase.MOVEMENT) {
            return new ActionResult.Rejected(ActionError.WRONG_PHASE);
        }

        List<String> hand = state.mutableMovementHand(actor);
        if (!hand.contains(move.movementCardId())) {
            return new ActionResult.Rejected(ActionError.CARD_NOT_HELD);
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

        Card card = findCard(state.rules(), move.movementCardId());
        if (card == null) {
            return new ActionResult.Rejected(ActionError.CARD_NOT_HELD);
        }

        boolean legalOffset = false;
        for (Offset offset : card.offsets()) {
            Offset effective = actor == PlayerColor.BLUE ? offset : offset.rotate180();
            if (from.plus(effective).equals(move.to())) {
                legalOffset = true;
                break;
            }
        }
        if (!legalOffset) {
            return new ActionResult.Rejected(ActionError.INVALID_TARGETS);
        }

        // Action is valid: deep copy state and apply
        GameState next = new GameState(state);

        // 1. Relocate piece and capture if destination occupied
        Piece captured = next.board().move(from, move.to());

        // 2. Recycle movement card: return -> shuffle -> draw
        List<String> nextHand = next.mutableMovementHand(actor);
        nextHand.remove(move.movementCardId());
        next.mutableMovementDeck().add(move.movementCardId());
        next.randomState().shuffle(next.mutableMovementDeck());
        String drawn = next.mutableMovementDeck().remove(0);
        nextHand.add(drawn);

        // 3. Events in deterministic documented order
        List<PublicEvent> events = new ArrayList<>();
        events.add(new PublicEvent.MovementPlayed(actor, move.movementCardId()));
        events.add(new PublicEvent.PieceMoved(move.pieceId(), from, move.to()));
        if (captured != null) {
            events.add(new PublicEvent.PieceEliminated(captured.id(), move.to()));
        }

        // 4. Win condition check: elimination wins immediately
        int opponentRemaining = next.board().countPieces(actor.opponent());
        if (opponentRemaining == 0) {
            next.setPhase(TurnPhase.FINISHED);
            GameOutcome outcome = new GameOutcome(actor, EndReason.ELIMINATION);
            next.setOutcome(outcome);
            events.add(new PublicEvent.MatchEnded(outcome));
        } else {
            next.setPhase(TurnPhase.SKILL);
        }

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
        ActionError error = SkillEffects.resolve(definition, state, next, actor, cast.targets(), events);
        if (error != null) {
            return new ActionResult.Rejected(error);
        }

        // Add SkillCommitted event first
        events.add(0, new PublicEvent.SkillCommitted(actor, heldInstance));

        // Card bookkeeping: consume held skill, append to spent, draw top replacement if available
        next.consumeHeldSkill(actor, heldInstance.instanceId());

        // Elimination check
        int oppRemaining = next.board().countPieces(actor.opponent());
        if (oppRemaining == 0) {
            next.setPhase(TurnPhase.FINISHED);
            GameOutcome outcome = new GameOutcome(actor, EndReason.ELIMINATION);
            next.setOutcome(outcome);
            events.add(new PublicEvent.MatchEnded(outcome));
        } else {
            next.setPhase(TurnPhase.END);
        }

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

    private static Card findCard(RulesConfig rules, String cardId) {
        for (Card card : rules.movementCards()) {
            if (card.id().equals(cardId)) {
                return card;
            }
        }
        return null;
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
                state.outcome());
    }
}
