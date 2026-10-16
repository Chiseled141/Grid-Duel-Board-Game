package onitama.core.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import onitama.core.Card;
import onitama.core.Offset;
import onitama.core.PlayerColor;
import onitama.core.Square;

/**
 * Shared UI and policy targeting helpers computed purely from PlayerView.
 */
public final class Targeting {

    private Targeting() {}

    /**
     * Enumerates all legal movement actions for the current view.
     * Pure, consumes no RNG, and returns empty if out of turn, wrong phase, or match finished.
     */
    public static List<GameAction.MovePiece> legalMoves(PlayerView view) {
        Objects.requireNonNull(view, "view must not be null");

        if (view.viewer() != view.turn()
                || view.phase() != TurnPhase.MOVEMENT
                || view.outcome() != null) {
            return List.of();
        }

        PlayerColor mover = view.viewer();
        List<String> handIds = mover == PlayerColor.BLUE
                ? view.blueMovementHand()
                : view.redMovementHand();

        Map<String, Card> cardCatalog = view.rules().movementCards().stream()
                .collect(Collectors.toMap(Card::id, c -> c));

        Map<Square, PlayerColor> occupied = new HashMap<>();
        List<PlacedPiece> friendlyPieces = new ArrayList<>();
        for (PlacedPiece pp : view.pieces()) {
            occupied.put(pp.square(), pp.piece().color());
            if (pp.piece().color() == mover) {
                friendlyPieces.add(pp);
            }
        }

        List<GameAction.MovePiece> moves = new ArrayList<>();
        for (String cardId : handIds) {
            Card card = cardCatalog.get(cardId);
            if (card == null) continue;

            for (PlacedPiece piece : friendlyPieces) {
                Square origin = piece.square();
                for (Offset offset : card.offsets()) {
                    Offset effective = mover == PlayerColor.BLUE ? offset : offset.rotate180();
                    Square dest = origin.plus(effective);
                    if (Board.isInside(dest)) {
                        PlayerColor destColor = occupied.get(dest);
                        if (destColor == null || destColor != mover) {
                            moves.add(new GameAction.MovePiece(card.id(), piece.piece().id(), dest));
                        }
                    }
                }
            }
        }

        return List.copyOf(moves);
    }

    /**
     * Evaluates skill targeting options and validity from the player's view and draft.
     */
    public static SelectionOptions skillOptions(PlayerView view, String skillInstanceId, SelectionDraft draft) {
        if (view == null || skillInstanceId == null) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        if (view.outcome() != null) {
            return SelectionOptions.error(ActionError.GAME_FINISHED);
        }
        if (view.viewer() != view.turn()) {
            return SelectionOptions.error(ActionError.NOT_YOUR_TURN);
        }
        if (view.phase() != TurnPhase.SKILL) {
            return SelectionOptions.error(ActionError.WRONG_PHASE);
        }

        CardInstance held = null;
        for (CardInstance c : view.ownSkillHand()) {
            if (c.instanceId().equals(skillInstanceId)) {
                held = c;
                break;
            }
        }
        if (held == null) {
            return SelectionOptions.error(ActionError.CARD_NOT_HELD);
        }

        SkillDefinition def = null;
        for (SkillDefinition d : view.rules().skills()) {
            if (d.id().equals(held.definitionId())) {
                def = d;
                break;
            }
        }
        if (def == null) {
            return SelectionOptions.error(ActionError.CARD_NOT_HELD);
        }

        int mana = view.viewer() == PlayerColor.BLUE ? view.blueMana() : view.redMana();
        if (mana < def.manaCost()) {
            return SelectionOptions.error(ActionError.INSUFFICIENT_MANA);
        }

        SelectionDraft currentDraft = draft != null ? draft : SelectionDraft.empty();
        if (currentDraft.pieceIds().size() > 3 || currentDraft.destinations().size() > 2
                || new HashSet<>(currentDraft.pieceIds()).size() != currentDraft.pieceIds().size()
                || (currentDraft.direction() != null && !def.id().equals("gust"))) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        PlayerColor actor = view.viewer();

        Map<Square, PlacedPiece> bySquare = new HashMap<>();
        Map<Integer, PlacedPiece> byId = new HashMap<>();
        for (PlacedPiece pp : view.pieces()) {
            bySquare.put(pp.square(), pp);
            byId.put(pp.piece().id(), pp);
        }

        return switch (def.id()) {
            case "sidestep" -> skillOptionsSidestep(def, currentDraft, actor, bySquare, byId);
            case "dash" -> skillOptionsDash(def, currentDraft, actor, bySquare, byId);
            case "blink" -> skillOptionsBlink(def, currentDraft, actor, bySquare, byId);
            case "exchange" -> skillOptionsExchange(def, currentDraft, actor, byId, view.pieces());
            case "advance" -> skillOptionsAdvance(currentDraft, actor, bySquare, byId);
            case "recall" -> skillOptionsRecall(currentDraft, actor, bySquare, byId);
            case "vault" -> skillOptionsVault(def, currentDraft, actor, bySquare, byId);
            case "rally" -> skillOptionsRally(def, currentDraft, actor, bySquare, byId, view.pieces());
            case "scatter" -> skillOptionsScatter(def, currentDraft, actor, bySquare, byId, view.pieces());
            case "strike" -> skillOptionsStrike(currentDraft, actor, bySquare, byId);
            case "pierce" -> skillOptionsPierce(def, currentDraft, actor, bySquare, byId);
            case "surround" -> skillOptionsSurround(def, currentDraft, actor, bySquare, byId);
            case "sweep" -> skillOptionsSweep(def, currentDraft, actor, bySquare, byId);
            case "flank" -> skillOptionsFlank(currentDraft, actor, bySquare, byId);
            case "crossfire" -> skillOptionsCrossfire(def, currentDraft, actor, bySquare, byId);
            case "chain-strike" -> skillOptionsChainStrike(currentDraft, actor, bySquare, byId);
            case "shove" -> skillOptionsShove(currentDraft, actor, bySquare, byId);
            case "pull" -> skillOptionsPull(currentDraft, actor, bySquare, byId);
            case "transposition" -> skillOptionsTransposition(def, currentDraft, actor, bySquare, byId, view.pieces());
            case "gust" -> skillOptionsGust(def, currentDraft, actor, bySquare, byId);
            case "banish" -> skillOptionsBanish(currentDraft, actor, bySquare, byId);
            case "mana-surge", "refocus", "foresight" -> skillOptionsNone(currentDraft);
            default -> SelectionOptions.error(ActionError.INVALID_TARGETS);
        };
    }

    // --- Helpers & Per-Skill Implementations ---

    private static SelectionOptions skillOptionsNone(SelectionDraft draft) {
        if (!draft.pieceIds().isEmpty() || !draft.destinations().isEmpty() || draft.direction() != null) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        return new SelectionOptions(List.of(), List.of(), List.of(), new SkillTargets.None(), true, null);
    }

    private static SelectionOptions skillOptionsSidestep(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int dist = def.parameters().getOrDefault(SkillParameter.DISTANCE, 1);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !emptyOrthogonalSquares(pp.square(), dist, bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int pieceId = draft.pieceIds().get(0);
            PlacedPiece pp = byId.get(pieceId);
            if (pp == null || pp.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            List<Square> squares = emptyOrthogonalSquares(pp.square(), dist, bySquare);
            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(pieceId), squares, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!squares.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(pieceId), squares, List.of(),
                        new SkillTargets.Relocate(pieceId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsDash(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int dist = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !dashDestinations(pp.square(), dist, bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int pieceId = draft.pieceIds().get(0);
            PlacedPiece pp = byId.get(pieceId);
            if (pp == null || pp.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            List<Square> squares = dashDestinations(pp.square(), dist, bySquare);
            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(pieceId), squares, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!squares.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(pieceId), squares, List.of(),
                        new SkillTargets.Relocate(pieceId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsBlink(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !blinkDestinations(pp.square(), range, bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int pieceId = draft.pieceIds().get(0);
            PlacedPiece pp = byId.get(pieceId);
            if (pp == null || pp.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            List<Square> squares = blinkDestinations(pp.square(), range, bySquare);
            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(pieceId), squares, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!squares.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(pieceId), squares, List.of(),
                        new SkillTargets.Relocate(pieceId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsExchange(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Integer, PlacedPiece> byId, List<PlacedPiece> allPieces) {
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : allPieces) {
                if (pp.piece().color() == actor) {
                    boolean hasFriend = allPieces.stream().anyMatch(other ->
                            other.piece().color() == actor
                                    && other.piece().id() != pp.piece().id()
                                    && manhattan(pp.square(), other.square()) <= range);
                    if (hasFriend) selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int firstId = draft.pieceIds().get(0);
            PlacedPiece first = byId.get(firstId);
            if (first == null || first.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            List<Integer> secondCandidates = new ArrayList<>();
            for (PlacedPiece pp : allPieces) {
                if (pp.piece().color() == actor && pp.piece().id() != firstId) {
                    int d = manhattan(first.square(), pp.square());
                    if (d <= range && d > 0) secondCandidates.add(pp.piece().id());
                }
            }
            return new SelectionOptions(secondCandidates, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int firstId = draft.pieceIds().get(0);
            int secondId = draft.pieceIds().get(1);
            if (firstId == secondId) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            PlacedPiece first = byId.get(firstId);
            PlacedPiece second = byId.get(secondId);
            if (first == null || first.piece().color() != actor
                    || second == null || second.piece().color() != actor) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            int d = manhattan(first.square(), second.square());
            if (d <= 0 || d > range) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            return new SelectionOptions(List.of(firstId, secondId), List.of(), List.of(),
                    new SkillTargets.Swap(firstId, secondId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsAdvance(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int forwardDy = actor == PlayerColor.BLUE ? 1 : -1;
        if (draft.pieceIds().size() > 2 || draft.destinations().size() > draft.pieceIds().size()) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        // Revalidate the whole prefix against the current view, including final drafts.
        for (int i = 0; i < draft.pieceIds().size(); i++) {
            PlacedPiece piece = byId.get(draft.pieceIds().get(i));
            if (piece == null || piece.piece().color() != actor) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            Square destination = new Square(piece.square().x(), piece.square().y() + forwardDy);
            if (!Board.isInside(destination) || bySquare.containsKey(destination)
                    || (i < draft.destinations().size() && !destination.equals(draft.destinations().get(i)))) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
        }

        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor) {
                    Square exp = new Square(pp.square().x(), pp.square().y() + forwardDy);
                    if (Board.isInside(exp) && !bySquare.containsKey(exp)) {
                        selectable.add(pp.piece().id());
                    }
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int p1 = draft.pieceIds().get(0);
            PlacedPiece pp1 = byId.get(p1);
            if (pp1 == null || pp1.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            Square dest1 = new Square(pp1.square().x(), pp1.square().y() + forwardDy);
            if (!Board.isInside(dest1) || bySquare.containsKey(dest1)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(p1), List.of(dest1), List.of(), null, false, null);
            } else if (draft.destinations().size() == 1 && draft.destinations().get(0).equals(dest1)) {
                List<Integer> p2Selectable = new ArrayList<>();
                for (PlacedPiece pp2 : byId.values()) {
                    if (pp2.piece().color() == actor && pp2.piece().id() != p1) {
                        Square exp2 = new Square(pp2.square().x(), pp2.square().y() + forwardDy);
                        if (Board.isInside(exp2) && !bySquare.containsKey(exp2) && !exp2.equals(dest1)) {
                            p2Selectable.add(pp2.piece().id());
                        }
                    }
                }
                return new SelectionOptions(p2Selectable, List.of(), List.of(), null, false, null);
            }
        } else if (draft.pieceIds().size() == 2 && draft.destinations().size() == 1) {
            int p1 = draft.pieceIds().get(0);
            int p2 = draft.pieceIds().get(1);
            if (p1 == p2) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            PlacedPiece pp2 = byId.get(p2);
            if (pp2 == null || pp2.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            Square dest2 = new Square(pp2.square().x(), pp2.square().y() + forwardDy);
            Square dest1 = draft.destinations().get(0);
            if (!Board.isInside(dest2) || bySquare.containsKey(dest2) || dest2.equals(dest1)) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(p2), List.of(dest2), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2 && draft.destinations().size() == 2) {
            int p1 = draft.pieceIds().get(0);
            int p2 = draft.pieceIds().get(1);
            Square d1 = draft.destinations().get(0);
            Square d2 = draft.destinations().get(1);
            if (p1 == p2 || d1.equals(d2)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            return new SelectionOptions(List.of(p1, p2), List.of(d1, d2), List.of(),
                    new SkillTargets.MoveGroup(List.of(new Relocation(p1, d1), new Relocation(p2, d2))), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsRecall(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int homeRow = actor == PlayerColor.BLUE ? 0 : 6;
        List<Square> emptyHomeSquares = new ArrayList<>();
        for (int x = 0; x < 7; x++) {
            Square sq = new Square(x, homeRow);
            if (!bySquare.containsKey(sq)) emptyHomeSquares.add(sq);
        }

        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor) {
                    boolean canRecall = emptyHomeSquares.stream().anyMatch(sq -> !sq.equals(pp.square()));
                    if (canRecall) selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int pId = draft.pieceIds().get(0);
            PlacedPiece pp = byId.get(pId);
            if (pp == null || pp.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            List<Square> validDests = emptyHomeSquares.stream().filter(sq -> !sq.equals(pp.square())).toList();
            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(pId), validDests, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!validDests.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(pId), validDests, List.of(),
                        new SkillTargets.Relocate(pId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsVault(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int dist = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);
        return skillOptionsSidestep(def, draft, actor, bySquare, byId);
    }

    private static SelectionOptions skillOptionsRally(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId, List<PlacedPiece> allPieces) {
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (draft.pieceIds().size() > maxTargets + 1
                || draft.destinations().size() > Math.max(0, draft.pieceIds().size() - 1)) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }

        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableAnchors = new ArrayList<>();
            for (PlacedPiece anchor : byId.values()) {
                if (anchor.piece().color() == actor) {
                    boolean hasMover = byId.values().stream().anyMatch(mover ->
                            mover.piece().color() == actor && mover.piece().id() != anchor.piece().id()
                                    && !validRallySteps(mover.square(), anchor.square(), bySquare).isEmpty());
                    if (hasMover) selectableAnchors.add(anchor.piece().id());
                }
            }
            return new SelectionOptions(selectableAnchors, List.of(), List.of(), null, false, null);
        }

        int anchorId = draft.pieceIds().get(0);
        PlacedPiece anchor = byId.get(anchorId);
        if (anchor == null || anchor.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
        Square anchorSq = anchor.square();
        Map<Square, PlacedPiece> validatedSquares = new HashMap<>(bySquare);
        for (int i = 1; i < draft.pieceIds().size(); i++) {
            PlacedPiece mover = byId.get(draft.pieceIds().get(i));
            if (mover == null || mover.piece().color() != actor || mover.piece().id() == anchorId) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            if (i - 1 < draft.destinations().size()) {
                Square destination = draft.destinations().get(i - 1);
                if (!validRallySteps(mover.square(), anchorSq, validatedSquares).contains(destination)) {
                    return SelectionOptions.error(ActionError.INVALID_TARGETS);
                }
                // Rally is sequential: the next mover may use a square just vacated.
                validatedSquares.remove(mover.square());
                validatedSquares.put(destination, new PlacedPiece(mover.piece(), destination));
            }
        }

        // 1 piece in draft (just anchor)
        if (draft.pieceIds().size() == 1) {
            List<Integer> selectableMovers = new ArrayList<>();
            for (PlacedPiece mover : byId.values()) {
                if (mover.piece().color() == actor && mover.piece().id() != anchorId) {
                    if (!validRallySteps(mover.square(), anchorSq, bySquare).isEmpty()) {
                        selectableMovers.add(mover.piece().id());
                    }
                }
            }
            return new SelectionOptions(selectableMovers, List.of(), List.of(), null, false, null);
        }

        // [anchor, p1] and 0 destinations
        if (draft.pieceIds().size() == 2 && draft.destinations().isEmpty()) {
            int p1 = draft.pieceIds().get(1);
            PlacedPiece pp1 = byId.get(p1);
            if (pp1 == null || pp1.piece().color() != actor || p1 == anchorId) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> steps = validRallySteps(pp1.square(), anchorSq, bySquare);
            return new SelectionOptions(List.of(p1), steps, List.of(), null, false, null);
        }

        // [anchor, p1] and 1 destination (complete single move rally, but can optionally add second)
        if (draft.pieceIds().size() == 2 && draft.destinations().size() == 1) {
            int p1 = draft.pieceIds().get(1);
            Square d1 = draft.destinations().get(0);
            PlacedPiece pp1 = byId.get(p1);
            if (pp1 == null || pp1.piece().color() != actor || p1 == anchorId) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> steps = validRallySteps(pp1.square(), anchorSq, bySquare);
            if (!steps.contains(d1)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            SkillTargets complete = new SkillTargets.Rally(anchorId, List.of(new Relocation(p1, d1)));

            // Compute potential second movers on candidate board where pp1 moved to d1
            Map<Square, PlacedPiece> candBySquare = new HashMap<>(bySquare);
            candBySquare.remove(pp1.square());
            candBySquare.put(d1, pp1);

            List<Integer> secondMovers = new ArrayList<>();
            if (maxTargets >= 2) {
                for (PlacedPiece mover : byId.values()) {
                    if (mover.piece().color() == actor && mover.piece().id() != anchorId && mover.piece().id() != p1) {
                        if (!validRallySteps(mover.square(), anchorSq, candBySquare).isEmpty()) {
                            secondMovers.add(mover.piece().id());
                        }
                    }
                }
            }

            return new SelectionOptions(secondMovers, List.of(), List.of(), complete, true, null);
        }

        // [anchor, p1, p2] and 1 destination
        if (draft.pieceIds().size() == 3 && draft.destinations().size() == 1) {
            int p1 = draft.pieceIds().get(1);
            int p2 = draft.pieceIds().get(2);
            Square d1 = draft.destinations().get(0);
            PlacedPiece pp1 = byId.get(p1);
            PlacedPiece pp2 = byId.get(p2);
            if (pp1 == null || pp2 == null || p2 == anchorId || p2 == p1 || pp2.piece().color() != actor) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }

            Map<Square, PlacedPiece> candBySquare = new HashMap<>(bySquare);
            candBySquare.remove(pp1.square());
            candBySquare.put(d1, pp1);

            List<Square> steps2 = validRallySteps(pp2.square(), anchorSq, candBySquare);
            return new SelectionOptions(List.of(p2), steps2, List.of(), null, false, null);
        }

        // [anchor, p1, p2] and 2 destinations
        if (draft.pieceIds().size() == 3 && draft.destinations().size() == 2) {
            int p1 = draft.pieceIds().get(1);
            int p2 = draft.pieceIds().get(2);
            Square d1 = draft.destinations().get(0);
            Square d2 = draft.destinations().get(1);
            SkillTargets complete = new SkillTargets.Rally(anchorId, List.of(new Relocation(p1, d1), new Relocation(p2, d2)));
            return new SelectionOptions(List.of(anchorId, p1, p2), List.of(d1, d2), List.of(), complete, true, null);
        }

        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static List<Square> validRallySteps(Square current, Square anchor, Map<Square, PlacedPiece> bySquare) {
        int currentDist = manhattan(current, anchor);
        List<Square> result = new ArrayList<>();
        Square[] candidates = {
                new Square(current.x() + 1, current.y()),
                new Square(current.x() - 1, current.y()),
                new Square(current.x(), current.y() + 1),
                new Square(current.x(), current.y() - 1)
        };
        for (Square sq : candidates) {
            if (Board.isInside(sq) && !bySquare.containsKey(sq) && manhattan(sq, anchor) < currentDist) {
                result.add(sq);
            }
        }
        return result;
    }

    private static SelectionOptions skillOptionsScatter(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId, List<PlacedPiece> allPieces) {
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (draft.pieceIds().size() > maxTargets || draft.destinations().size() > draft.pieceIds().size()
                || new HashSet<>(draft.destinations()).size() != draft.destinations().size()) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        for (int i = 0; i < draft.pieceIds().size(); i++) {
            PlacedPiece mover = byId.get(draft.pieceIds().get(i));
            if (mover == null || mover.piece().color() != actor
                    || (i < draft.destinations().size()
                    && !emptyDiagonalSquares(mover.square(), bySquare).contains(draft.destinations().get(i)))) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
        }

        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !emptyDiagonalSquares(pp.square(), bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1 && draft.destinations().isEmpty()) {
            int p1 = draft.pieceIds().get(0);
            PlacedPiece pp1 = byId.get(p1);
            if (pp1 == null || pp1.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> diags = emptyDiagonalSquares(pp1.square(), bySquare);
            return new SelectionOptions(List.of(p1), diags, List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1 && draft.destinations().size() == 1) {
            int p1 = draft.pieceIds().get(0);
            Square d1 = draft.destinations().get(0);
            PlacedPiece pp1 = byId.get(p1);
            if (pp1 == null || pp1.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> diags = emptyDiagonalSquares(pp1.square(), bySquare);
            if (!diags.contains(d1)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            SkillTargets complete = new SkillTargets.MoveGroup(List.of(new Relocation(p1, d1)));
            List<Integer> secondMovers = new ArrayList<>();
            if (maxTargets >= 2) {
                for (PlacedPiece pp2 : byId.values()) {
                    if (pp2.piece().color() == actor && pp2.piece().id() != p1) {
                        boolean hasOtherDiag = emptyDiagonalSquares(pp2.square(), bySquare).stream().anyMatch(sq -> !sq.equals(d1));
                        if (hasOtherDiag) secondMovers.add(pp2.piece().id());
                    }
                }
            }
            return new SelectionOptions(secondMovers, List.of(), List.of(), complete, true, null);
        } else if (draft.pieceIds().size() == 2 && draft.destinations().size() == 1) {
            int p1 = draft.pieceIds().get(0);
            int p2 = draft.pieceIds().get(1);
            Square d1 = draft.destinations().get(0);
            PlacedPiece pp2 = byId.get(p2);
            if (pp2 == null || pp2.piece().color() != actor || p2 == p1) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> diags2 = emptyDiagonalSquares(pp2.square(), bySquare).stream().filter(sq -> !sq.equals(d1)).toList();
            return new SelectionOptions(List.of(p2), diags2, List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2 && draft.destinations().size() == 2) {
            int p1 = draft.pieceIds().get(0);
            int p2 = draft.pieceIds().get(1);
            Square d1 = draft.destinations().get(0);
            Square d2 = draft.destinations().get(1);
            if (p1 == p2 || d1.equals(d2)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            SkillTargets complete = new SkillTargets.MoveGroup(List.of(new Relocation(p1, d1), new Relocation(p2, d2)));
            return new SelectionOptions(List.of(p1, p2), List.of(d1, d2), List.of(), complete, true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsStrike(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !adjacentEnemies(pp.square(), actor.opponent(), bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = adjacentEnemies(source.square(), actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int enemyId = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy = byId.get(enemyId);
            if (source == null || source.piece().color() != actor
                    || enemy == null || enemy.piece().color() != actor.opponent()
                    || manhattan(source.square(), enemy.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(sourceId, enemyId), List.of(), List.of(),
                    new SkillTargets.Attack(sourceId, List.of(enemyId)), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsPierce(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int dist = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !pierceEnemies(pp.square(), dist, actor.opponent(), bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = pierceEnemies(source.square(), dist, actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int enemyId = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy = byId.get(enemyId);
            if (source == null || source.piece().color() != actor
                    || enemy == null || enemy.piece().color() != actor.opponent()) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            List<Integer> validEnemies = pierceEnemies(source.square(), dist, actor.opponent(), bySquare);
            if (!validEnemies.contains(enemyId)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            return new SelectionOptions(List.of(sourceId, enemyId), List.of(), List.of(),
                    new SkillTargets.Attack(sourceId, List.of(enemyId)), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsSurround(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int req = def.parameters().getOrDefault(SkillParameter.REQUIRED_ALLIES, 2);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableEnemies = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor.opponent()) {
                    if (adjacentAlliesCount(pp.square(), actor, bySquare) >= req) {
                        selectableEnemies.add(pp.piece().id());
                    }
                }
            }
            return new SelectionOptions(selectableEnemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int enemyId = draft.pieceIds().get(0);
            PlacedPiece enemy = byId.get(enemyId);
            if (enemy == null || enemy.piece().color() != actor.opponent()
                    || adjacentAlliesCount(enemy.square(), actor, bySquare) < req) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(enemyId), List.of(), List.of(),
                    new SkillTargets.Enemy(enemyId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsSweep(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (draft.pieceIds().size() > maxTargets + 1 || !draft.destinations().isEmpty()) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableSources = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !adjacentEnemies(pp.square(), actor.opponent(), bySquare).isEmpty()) {
                    selectableSources.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableSources, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = adjacentEnemies(source.square(), actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int e1 = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy1 = byId.get(e1);
            if (source == null || source.piece().color() != actor || enemy1 == null || enemy1.piece().color() != actor.opponent()
                    || manhattan(source.square(), enemy1.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }

            SkillTargets complete = new SkillTargets.Attack(sourceId, List.of(e1));
            List<Integer> remainingEnemies = maxTargets == 1 ? List.of() : adjacentEnemies(source.square(), actor.opponent(), bySquare)
                    .stream().filter(id -> id != e1).toList();

            return new SelectionOptions(remainingEnemies, List.of(), List.of(), complete, true, null);
        } else if (draft.pieceIds().size() == 3) {
            int sourceId = draft.pieceIds().get(0);
            int e1 = draft.pieceIds().get(1);
            int e2 = draft.pieceIds().get(2);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy1 = byId.get(e1);
            PlacedPiece enemy2 = byId.get(e2);
            if (source == null || source.piece().color() != actor || enemy1 == null || enemy2 == null
                    || enemy1.piece().color() != actor.opponent() || enemy2.piece().color() != actor.opponent()
                    || e1 == e2 || manhattan(source.square(), enemy1.square()) != 1 || manhattan(source.square(), enemy2.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(sourceId, e1, e2), List.of(), List.of(),
                    new SkillTargets.Attack(sourceId, List.of(e1, e2)), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsFlank(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !flankDestinations(pp.square(), actor, bySquare).isEmpty()) {
                    selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int pieceId = draft.pieceIds().get(0);
            PlacedPiece pp = byId.get(pieceId);
            if (pp == null || pp.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Square> squares = flankDestinations(pp.square(), actor, bySquare);
            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(pieceId), squares, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!squares.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(pieceId), squares, List.of(),
                        new SkillTargets.Relocate(pieceId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsCrossfire(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableEnemies = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor.opponent() && hasCrossfireSupport(pp.square(), actor, range, bySquare)) {
                    selectableEnemies.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableEnemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int enemyId = draft.pieceIds().get(0);
            PlacedPiece enemy = byId.get(enemyId);
            if (enemy == null || enemy.piece().color() != actor.opponent()
                    || !hasCrossfireSupport(enemy.square(), actor, range, bySquare)) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(enemyId), List.of(), List.of(),
                    new SkillTargets.Enemy(enemyId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsChainStrike(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableSources = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !adjacentEnemies(pp.square(), actor.opponent(), bySquare).isEmpty()) {
                    selectableSources.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableSources, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = adjacentEnemies(source.square(), actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int e1 = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy1 = byId.get(e1);
            if (source == null || source.piece().color() != actor || enemy1 == null || enemy1.piece().color() != actor.opponent()
                    || manhattan(source.square(), enemy1.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }

            SkillTargets complete = new SkillTargets.Attack(sourceId, List.of(e1));
            List<Integer> secondEnemies = adjacentEnemies(enemy1.square(), actor.opponent(), bySquare)
                    .stream().filter(id -> id != e1).toList();

            return new SelectionOptions(secondEnemies, List.of(), List.of(), complete, true, null);
        } else if (draft.pieceIds().size() == 3) {
            int sourceId = draft.pieceIds().get(0);
            int e1 = draft.pieceIds().get(1);
            int e2 = draft.pieceIds().get(2);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy1 = byId.get(e1);
            PlacedPiece enemy2 = byId.get(e2);
            if (source == null || source.piece().color() != actor || enemy1 == null || enemy2 == null
                    || enemy1.piece().color() != actor.opponent() || enemy2.piece().color() != actor.opponent()
                    || e1 == e2 || manhattan(source.square(), enemy1.square()) != 1 || manhattan(enemy1.square(), enemy2.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            return new SelectionOptions(List.of(sourceId, e1, e2), List.of(), List.of(),
                    new SkillTargets.Attack(sourceId, List.of(e1, e2)), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsShove(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableSources = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor) {
                    boolean hasShovable = adjacentEnemies(pp.square(), actor.opponent(), bySquare).stream().anyMatch(eId -> {
                        PlacedPiece e = byId.get(eId);
                        int dx = e.square().x() - pp.square().x();
                        int dy = e.square().y() - pp.square().y();
                        Square dest = new Square(e.square().x() + dx, e.square().y() + dy);
                        return Board.isInside(dest) && !bySquare.containsKey(dest);
                    });
                    if (hasShovable) selectableSources.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableSources, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> validEnemies = adjacentEnemies(source.square(), actor.opponent(), bySquare).stream().filter(eId -> {
                PlacedPiece e = byId.get(eId);
                int dx = e.square().x() - source.square().x();
                int dy = e.square().y() - source.square().y();
                Square dest = new Square(e.square().x() + dx, e.square().y() + dy);
                return Board.isInside(dest) && !bySquare.containsKey(dest);
            }).toList();
            return new SelectionOptions(validEnemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int enemyId = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy = byId.get(enemyId);
            if (source == null || source.piece().color() != actor || enemy == null || enemy.piece().color() != actor.opponent()
                    || manhattan(source.square(), enemy.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            int dx = enemy.square().x() - source.square().x();
            int dy = enemy.square().y() - source.square().y();
            Square dest = new Square(enemy.square().x() + dx, enemy.square().y() + dy);
            if (!Board.isInside(dest) || bySquare.containsKey(dest)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            return new SelectionOptions(List.of(sourceId, enemyId), List.of(), List.of(),
                    new SkillTargets.Displace(sourceId, enemyId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsPull(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableSources = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor) {
                    boolean hasPullable = pullableEnemies(pp.square(), actor.opponent(), bySquare).size() > 0;
                    if (hasPullable) selectableSources.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableSources, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = pullableEnemies(source.square(), actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int enemyId = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy = byId.get(enemyId);
            if (source == null || source.piece().color() != actor || enemy == null || enemy.piece().color() != actor.opponent()) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            List<Integer> validEnemies = pullableEnemies(source.square(), actor.opponent(), bySquare);
            if (!validEnemies.contains(enemyId)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

            return new SelectionOptions(List.of(sourceId, enemyId), List.of(), List.of(),
                    new SkillTargets.Displace(sourceId, enemyId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsTransposition(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId, List<PlacedPiece> allPieces) {
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 2);
        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectable = new ArrayList<>();
            for (PlacedPiece pp : allPieces) {
                if (pp.piece().color() == actor) {
                    boolean hasEnemy = allPieces.stream().anyMatch(other ->
                            other.piece().color() == actor.opponent() && manhattan(pp.square(), other.square()) <= range);
                    if (hasEnemy) selectable.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectable, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int firstId = draft.pieceIds().get(0);
            PlacedPiece first = byId.get(firstId);
            if (first == null || first.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = allPieces.stream()
                    .filter(pp -> pp.piece().color() == actor.opponent() && manhattan(first.square(), pp.square()) <= range)
                    .map(pp -> pp.piece().id()).toList();
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int firstId = draft.pieceIds().get(0);
            int secondId = draft.pieceIds().get(1);
            PlacedPiece first = byId.get(firstId);
            PlacedPiece second = byId.get(secondId);
            if (first == null || first.piece().color() != actor || second == null || second.piece().color() != actor.opponent()) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }
            if (manhattan(first.square(), second.square()) > range) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            return new SelectionOptions(List.of(firstId, secondId), List.of(), List.of(),
                    new SkillTargets.Swap(firstId, secondId), true, null);
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsGust(
            SkillDefinition def, SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (draft.pieceIds().size() > maxTargets || !draft.destinations().isEmpty()) {
            return SelectionOptions.error(ActionError.INVALID_TARGETS);
        }
        Direction dir = draft.direction();

        if (dir == null) {
            // Direction not chosen yet
            if (draft.pieceIds().isEmpty()) {
                List<Integer> enemies = byId.values().stream()
                        .filter(pp -> pp.piece().color() == actor.opponent())
                        .map(pp -> pp.piece().id()).toList();
                return new SelectionOptions(enemies, List.of(), List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST), null, false, null);
            } else {
                int e1 = draft.pieceIds().get(0);
                PlacedPiece pp = byId.get(e1);
                if (pp == null || pp.piece().color() != actor.opponent()) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                List<Direction> validDirs = new ArrayList<>();
                for (Direction d : Direction.values()) {
                    Square to = d.step(pp.square());
                    if (Board.isInside(to) && !bySquare.containsKey(to)) validDirs.add(d);
                }
                return new SelectionOptions(draft.pieceIds(), List.of(), validDirs, null, false, null);
            }
        } else {
            // Direction chosen
            List<Integer> validEnemies = byId.values().stream()
                    .filter(pp -> pp.piece().color() == actor.opponent() && Board.isInside(dir.step(pp.square())) && !bySquare.containsKey(dir.step(pp.square())))
                    .map(pp -> pp.piece().id()).toList();

            if (draft.pieceIds().isEmpty()) {
                return new SelectionOptions(validEnemies, List.of(), List.of(dir), null, false, null);
            } else if (draft.pieceIds().size() == 1) {
                int e1 = draft.pieceIds().get(0);
                if (!validEnemies.contains(e1)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                SkillTargets complete = new SkillTargets.ShiftEnemies(List.of(e1), dir);
                PlacedPiece pp1 = byId.get(e1);
                Square to1 = dir.step(pp1.square());

                List<Integer> remainingEnemies = maxTargets == 1 ? List.of() : validEnemies.stream()
                        .filter(id -> id != e1 && !dir.step(byId.get(id).square()).equals(to1)).toList();
                return new SelectionOptions(remainingEnemies, List.of(), List.of(dir), complete, true, null);
            } else if (draft.pieceIds().size() == 2) {
                int e1 = draft.pieceIds().get(0);
                int e2 = draft.pieceIds().get(1);
                if (e1 == e2 || !validEnemies.contains(e1) || !validEnemies.contains(e2)) {
                    return SelectionOptions.error(ActionError.INVALID_TARGETS);
                }
                Square to1 = dir.step(byId.get(e1).square());
                Square to2 = dir.step(byId.get(e2).square());
                if (to1.equals(to2)) return SelectionOptions.error(ActionError.INVALID_TARGETS);

                SkillTargets complete = new SkillTargets.ShiftEnemies(List.of(e1, e2), dir);
                return new SelectionOptions(List.of(e1, e2), List.of(), List.of(dir), complete, true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    private static SelectionOptions skillOptionsBanish(
            SelectionDraft draft, PlayerColor actor,
            Map<Square, PlacedPiece> bySquare, Map<Integer, PlacedPiece> byId) {
        int enemyHomeRow = actor == PlayerColor.BLUE ? 6 : 0;
        List<Square> emptyEnemyHomeSquares = new ArrayList<>();
        for (int x = 0; x < 7; x++) {
            Square sq = new Square(x, enemyHomeRow);
            if (!bySquare.containsKey(sq)) emptyEnemyHomeSquares.add(sq);
        }

        if (draft.pieceIds().isEmpty()) {
            List<Integer> selectableSources = new ArrayList<>();
            for (PlacedPiece pp : byId.values()) {
                if (pp.piece().color() == actor && !adjacentEnemies(pp.square(), actor.opponent(), bySquare).isEmpty()
                        && !emptyEnemyHomeSquares.isEmpty()) {
                    selectableSources.add(pp.piece().id());
                }
            }
            return new SelectionOptions(selectableSources, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 1) {
            int sourceId = draft.pieceIds().get(0);
            PlacedPiece source = byId.get(sourceId);
            if (source == null || source.piece().color() != actor) return SelectionOptions.error(ActionError.INVALID_TARGETS);
            List<Integer> enemies = adjacentEnemies(source.square(), actor.opponent(), bySquare);
            return new SelectionOptions(enemies, List.of(), List.of(), null, false, null);
        } else if (draft.pieceIds().size() == 2) {
            int sourceId = draft.pieceIds().get(0);
            int enemyId = draft.pieceIds().get(1);
            PlacedPiece source = byId.get(sourceId);
            PlacedPiece enemy = byId.get(enemyId);
            if (source == null || source.piece().color() != actor || enemy == null || enemy.piece().color() != actor.opponent()
                    || manhattan(source.square(), enemy.square()) != 1) {
                return SelectionOptions.error(ActionError.INVALID_TARGETS);
            }

            if (draft.destinations().isEmpty()) {
                return new SelectionOptions(List.of(sourceId, enemyId), emptyEnemyHomeSquares, List.of(), null, false, null);
            } else if (draft.destinations().size() == 1) {
                Square to = draft.destinations().get(0);
                if (!emptyEnemyHomeSquares.contains(to)) return SelectionOptions.error(ActionError.INVALID_TARGETS);
                return new SelectionOptions(List.of(sourceId, enemyId), emptyEnemyHomeSquares, List.of(),
                        new SkillTargets.RelocateEnemy(sourceId, enemyId, to), true, null);
            }
        }
        return SelectionOptions.error(ActionError.INVALID_TARGETS);
    }

    // --- Geometry & Search Helpers ---

    private static List<Square> emptyOrthogonalSquares(Square from, int distance, Map<Square, PlacedPiece> bySquare) {
        List<Square> result = new ArrayList<>();
        Square[] candidates = {
                new Square(from.x() + distance, from.y()),
                new Square(from.x() - distance, from.y()),
                new Square(from.x(), from.y() + distance),
                new Square(from.x(), from.y() - distance)
        };
        for (Square sq : candidates) {
            if (Board.isInside(sq) && !bySquare.containsKey(sq)) {
                result.add(sq);
            }
        }
        return result;
    }

    private static List<Square> emptyDiagonalSquares(Square from, Map<Square, PlacedPiece> bySquare) {
        List<Square> result = new ArrayList<>();
        Square[] candidates = {
                new Square(from.x() + 1, from.y() + 1),
                new Square(from.x() + 1, from.y() - 1),
                new Square(from.x() - 1, from.y() + 1),
                new Square(from.x() - 1, from.y() - 1)
        };
        for (Square sq : candidates) {
            if (Board.isInside(sq) && !bySquare.containsKey(sq)) {
                result.add(sq);
            }
        }
        return result;
    }

    private static List<Square> dashDestinations(Square from, int distance, Map<Square, PlacedPiece> bySquare) {
        List<Square> result = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            boolean clear = true;
            for (int step = 1; step <= distance; step++) {
                Square sq = new Square(from.x() + step * d[0], from.y() + step * d[1]);
                if (!Board.isInside(sq) || bySquare.containsKey(sq)) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                result.add(new Square(from.x() + distance * d[0], from.y() + distance * d[1]));
            }
        }
        return result;
    }

    private static List<Square> blinkDestinations(Square from, int range, Map<Square, PlacedPiece> bySquare) {
        List<Square> result = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int dist = Math.abs(dx) + Math.abs(dy);
                if (dist >= 1 && dist <= range) {
                    Square sq = new Square(from.x() + dx, from.y() + dy);
                    if (Board.isInside(sq) && !bySquare.containsKey(sq)) {
                        result.add(sq);
                    }
                }
            }
        }
        return result;
    }

    private static List<Square> flankDestinations(Square from, PlayerColor actor, Map<Square, PlacedPiece> bySquare) {
        List<Square> result = new ArrayList<>();
        Square[] candidates = {
                new Square(from.x() + 1, from.y() + 1),
                new Square(from.x() + 1, from.y() - 1),
                new Square(from.x() - 1, from.y() + 1),
                new Square(from.x() - 1, from.y() - 1)
        };
        for (Square sq : candidates) {
            if (Board.isInside(sq)) {
                PlacedPiece pp = bySquare.get(sq);
                if (pp == null || pp.piece().color() == actor.opponent()) {
                    result.add(sq);
                }
            }
        }
        return result;
    }

    private static List<Integer> adjacentEnemies(Square from, PlayerColor enemyColor, Map<Square, PlacedPiece> bySquare) {
        List<Integer> result = new ArrayList<>();
        Square[] candidates = {
                new Square(from.x() + 1, from.y()),
                new Square(from.x() - 1, from.y()),
                new Square(from.x(), from.y() + 1),
                new Square(from.x(), from.y() - 1)
        };
        for (Square sq : candidates) {
            PlacedPiece pp = bySquare.get(sq);
            if (pp != null && pp.piece().color() == enemyColor) {
                result.add(pp.piece().id());
            }
        }
        return result;
    }

    private static List<Integer> pierceEnemies(Square from, int distance, PlayerColor enemyColor, Map<Square, PlacedPiece> bySquare) {
        List<Integer> result = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            boolean pathClear = true;
            for (int step = 1; step < distance; step++) {
                Square sq = new Square(from.x() + step * d[0], from.y() + step * d[1]);
                if (!Board.isInside(sq) || bySquare.containsKey(sq)) {
                    pathClear = false;
                    break;
                }
            }
            if (pathClear) {
                Square enemySq = new Square(from.x() + distance * d[0], from.y() + distance * d[1]);
                PlacedPiece pp = bySquare.get(enemySq);
                if (pp != null && pp.piece().color() == enemyColor) {
                    result.add(pp.piece().id());
                }
            }
        }
        return result;
    }

    private static int adjacentAlliesCount(Square from, PlayerColor allyColor, Map<Square, PlacedPiece> bySquare) {
        int count = 0;
        Square[] candidates = {
                new Square(from.x() + 1, from.y()),
                new Square(from.x() - 1, from.y()),
                new Square(from.x(), from.y() + 1),
                new Square(from.x(), from.y() - 1)
        };
        for (Square sq : candidates) {
            PlacedPiece pp = bySquare.get(sq);
            if (pp != null && pp.piece().color() == allyColor) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasCrossfireSupport(Square from, PlayerColor allyColor, int range, Map<Square, PlacedPiece> bySquare) {
        boolean hSupport = checkCrossfireRay(from, -1, 0, allyColor, range, bySquare)
                && checkCrossfireRay(from, 1, 0, allyColor, range, bySquare);
        boolean vSupport = checkCrossfireRay(from, 0, -1, allyColor, range, bySquare)
                && checkCrossfireRay(from, 0, 1, allyColor, range, bySquare);
        return hSupport || vSupport;
    }

    private static boolean checkCrossfireRay(Square from, int stepX, int stepY, PlayerColor allyColor, int range, Map<Square, PlacedPiece> bySquare) {
        for (int dist = 1; dist <= range; dist++) {
            Square sq = new Square(from.x() + dist * stepX, from.y() + dist * stepY);
            if (!Board.isInside(sq)) return false;
            PlacedPiece pp = bySquare.get(sq);
            if (pp != null) {
                return pp.piece().color() == allyColor;
            }
        }
        return false;
    }

    private static List<Integer> pullableEnemies(Square from, PlayerColor enemyColor, Map<Square, PlacedPiece> bySquare) {
        List<Integer> result = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Square mid = new Square(from.x() + d[0], from.y() + d[1]);
            Square target = new Square(from.x() + 2 * d[0], from.y() + 2 * d[1]);
            if (Board.isInside(target) && !bySquare.containsKey(mid)) {
                PlacedPiece pp = bySquare.get(target);
                if (pp != null && pp.piece().color() == enemyColor) {
                    result.add(pp.piece().id());
                }
            }
        }
        return result;
    }

    private static int manhattan(Square s1, Square s2) {
        return Math.abs(s1.x() - s2.x()) + Math.abs(s1.y() - s2.y());
    }
}
