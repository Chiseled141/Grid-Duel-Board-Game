package deckduel.core.duel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import deckduel.core.PlayerColor;
import deckduel.core.Square;

/**
 * Internal core dispatcher for skill effect validation and resolution.
 * Modifies candidate GameState/Board and appends visual events on success.
 * Common lifecycle (mana deduction, card bookkeeping, phase advance, elimination victory)
 * is owned by RulesEngine.
 */
final class SkillEffects {

    private SkillEffects() {}

    /**
     * Resolves a skill effect. Returns null on success, or an ActionError on rejection.
     */
    static ActionError resolve(
            SkillDefinition definition,
            GameState state,
            GameState next,
            PlayerColor actor,
            SkillTargets targets,
            List<PublicEvent> events) {

        Board board = next.board();

        return switch (definition.id()) {
            case "sidestep" -> resolveSidestep(definition, board, actor, targets, events);
            case "dash" -> resolveDash(definition, board, actor, targets, events);
            case "blink" -> resolveBlink(definition, board, actor, targets, events);
            case "exchange" -> resolveExchange(definition, board, actor, targets, events);
            case "advance" -> resolveAdvance(board, actor, targets, events);
            case "recall" -> resolveRecall(board, actor, targets, events);
            case "vault" -> resolveVault(definition, board, actor, targets, events);
            case "rally" -> resolveRally(definition, board, actor, targets, events);
            case "scatter" -> resolveScatter(definition, board, actor, targets, events);
            case "strike" -> resolveStrike(board, actor, targets, events);
            case "pierce" -> resolvePierce(definition, board, actor, targets, events);
            case "surround" -> resolveSurround(definition, board, actor, targets, events);
            case "sweep" -> resolveSweep(definition, board, actor, targets, events);
            case "flank" -> resolveFlank(board, actor, targets, events);
            case "crossfire" -> resolveCrossfire(definition, board, actor, targets, events);
            case "chain-strike" -> resolveChainStrike(board, actor, targets, events);
            case "shove" -> resolveShove(board, actor, targets, events);
            case "pull" -> resolvePull(board, actor, targets, events);
            case "transposition" -> resolveTransposition(definition, board, actor, targets, events);
            case "gust" -> resolveGust(definition, board, actor, targets, events);
            case "banish" -> resolveBanish(board, actor, targets, events);
            case "mana-surge" -> resolveManaSurge(definition, next, actor, targets);
            case "refocus" -> resolveRefocus(next, actor, targets, events);
            case "steal" -> resolveSteal(next, actor, targets);
            case "spy" -> resolveSpy(next, actor, targets);
            case "draw-two" -> resolveDraw(next, actor, targets, events);
            case "sabotage" -> resolveSabotage(next, actor, targets);
            case "barter" -> resolveBarter(next, actor, targets);
            case "siphon" -> resolveSiphon(next, actor, targets);
            case "foresight" -> resolveForesight(targets);
            default -> ActionError.INVALID_TARGETS;
        };
    }

    // --- Movement / Position ---

    private static ActionError resolveSidestep(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

        int distance = def.parameters().getOrDefault(SkillParameter.DISTANCE, 1);
        boolean isExactOrthogonal = (from.x() == to.x() && Math.abs(from.y() - to.y()) == distance)
                || (from.y() == to.y() && Math.abs(from.x() - to.x()) == distance);
        if (!isExactOrthogonal) return ActionError.INVALID_TARGETS;

        board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        return null;
    }

    private static ActionError resolveDash(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

        int distance = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        boolean isExactOrthogonal = (dx == 0 && Math.abs(dy) == distance)
                || (dy == 0 && Math.abs(dx) == distance);
        if (!isExactOrthogonal) return ActionError.INVALID_TARGETS;

        int stepX = Integer.signum(dx);
        int stepY = Integer.signum(dy);
        for (int step = 1; step <= distance; step++) {
            Square sq = new Square(from.x() + step * stepX, from.y() + step * stepY);
            if (board.pieceAt(sq) != null) return ActionError.INVALID_TARGETS;
        }

        board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        return null;
    }

    private static ActionError resolveBlink(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null || from.equals(to)) return ActionError.INVALID_TARGETS;

        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);
        int manhattan = Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y());
        if (manhattan <= 0 || manhattan > range) return ActionError.INVALID_TARGETS;

        board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        return null;
    }

    private static ActionError resolveExchange(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Swap swap)) return ActionError.INVALID_TARGETS;
        if (swap.firstPieceId() == swap.secondPieceId()) return ActionError.INVALID_TARGETS;

        Piece p1 = board.findPieceById(swap.firstPieceId());
        Piece p2 = board.findPieceById(swap.secondPieceId());
        if (p1 == null || p1.color() != actor || p2 == null || p2.color() != actor) {
            return ActionError.INVALID_TARGETS;
        }

        Square sq1 = board.findPiece(swap.firstPieceId());
        Square sq2 = board.findPiece(swap.secondPieceId());
        int manhattan = Math.abs(sq1.x() - sq2.x()) + Math.abs(sq1.y() - sq2.y());
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);
        if (manhattan <= 0 || manhattan > range) return ActionError.INVALID_TARGETS;

        board.swap(sq1, sq2);
        events.add(new PublicEvent.PieceMoved(swap.firstPieceId(), sq1, sq2));
        events.add(new PublicEvent.PieceMoved(swap.secondPieceId(), sq2, sq1));
        return null;
    }

    private static ActionError resolveAdvance(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.MoveGroup mg)) return ActionError.INVALID_TARGETS;
        if (mg.moves().size() != 2) return ActionError.INVALID_TARGETS;

        Relocation m1 = mg.moves().get(0);
        Relocation m2 = mg.moves().get(1);
        if (m1.pieceId() == m2.pieceId() || m1.to().equals(m2.to())) return ActionError.INVALID_TARGETS;

        Piece p1 = board.findPieceById(m1.pieceId());
        Piece p2 = board.findPieceById(m2.pieceId());
        if (p1 == null || p1.color() != actor || p2 == null || p2.color() != actor) {
            return ActionError.INVALID_TARGETS;
        }

        Square from1 = board.findPiece(m1.pieceId());
        Square from2 = board.findPiece(m2.pieceId());
        int forwardDy = actor == PlayerColor.BLUE ? 1 : -1;
        Square exp1 = new Square(from1.x(), from1.y() + forwardDy);
        Square exp2 = new Square(from2.x(), from2.y() + forwardDy);

        if (!m1.to().equals(exp1) || !m2.to().equals(exp2)) return ActionError.INVALID_TARGETS;
        if (!Board.isInside(m1.to()) || !Board.isInside(m2.to())) return ActionError.INVALID_TARGETS;
        if (board.pieceAt(m1.to()) != null || board.pieceAt(m2.to()) != null) return ActionError.INVALID_TARGETS;

        board.move(from1, m1.to());
        board.move(from2, m2.to());
        events.add(new PublicEvent.PieceMoved(m1.pieceId(), from1, m1.to()));
        events.add(new PublicEvent.PieceMoved(m2.pieceId(), from2, m2.to()));
        return null;
    }

    private static ActionError resolveRecall(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null || from.equals(to)) return ActionError.INVALID_TARGETS;

        int homeRow = actor == PlayerColor.BLUE ? 0 : 6;
        if (to.y() != homeRow) return ActionError.INVALID_TARGETS;

        board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        return null;
    }

    private static ActionError resolveVault(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

        int distance = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);
        boolean isExactOrthogonal = (from.x() == to.x() && Math.abs(from.y() - to.y()) == distance)
                || (from.y() == to.y() && Math.abs(from.x() - to.x()) == distance);
        if (!isExactOrthogonal) return ActionError.INVALID_TARGETS;

        board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        return null;
    }

    private static ActionError resolveRally(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Rally rally)) return ActionError.INVALID_TARGETS;
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (rally.moves().isEmpty() || rally.moves().size() > maxTargets) return ActionError.INVALID_TARGETS;

        Piece anchor = board.findPieceById(rally.anchorId());
        if (anchor == null || anchor.color() != actor) return ActionError.INVALID_TARGETS;
        Square anchorSq = board.findPiece(rally.anchorId());

        Set<Integer> movingPieceIds = new HashSet<>();
        for (Relocation move : rally.moves()) {
            if (move.pieceId() == rally.anchorId() || !movingPieceIds.add(move.pieceId())) {
                return ActionError.INVALID_TARGETS;
            }
            Piece p = board.findPieceById(move.pieceId());
            if (p == null || p.color() != actor) return ActionError.INVALID_TARGETS;

            Square currentSq = board.findPiece(move.pieceId());
            Square to = move.to();
            if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

            int manhattanBefore = Math.abs(currentSq.x() - anchorSq.x()) + Math.abs(currentSq.y() - anchorSq.y());
            int manhattanAfter = Math.abs(to.x() - anchorSq.x()) + Math.abs(to.y() - anchorSq.y());
            int stepDist = Math.abs(currentSq.x() - to.x()) + Math.abs(currentSq.y() - to.y());

            if (stepDist != 1 || manhattanAfter >= manhattanBefore) {
                return ActionError.INVALID_TARGETS;
            }

            board.move(currentSq, to);
            events.add(new PublicEvent.PieceMoved(move.pieceId(), currentSq, to));
        }

        return null;
    }

    private static ActionError resolveScatter(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.MoveGroup mg)) return ActionError.INVALID_TARGETS;
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (mg.moves().isEmpty() || mg.moves().size() > maxTargets) return ActionError.INVALID_TARGETS;

        Set<Integer> movingPieceIds = new HashSet<>();
        Set<Square> destSquares = new HashSet<>();
        List<Square> fromSquares = new ArrayList<>();

        for (Relocation m : mg.moves()) {
            if (!movingPieceIds.add(m.pieceId()) || !destSquares.add(m.to())) {
                return ActionError.INVALID_TARGETS;
            }
            Piece p = board.findPieceById(m.pieceId());
            if (p == null || p.color() != actor) return ActionError.INVALID_TARGETS;

            Square from = board.findPiece(m.pieceId());
            Square to = m.to();
            if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

            boolean isDiagonalOne = Math.abs(from.x() - to.x()) == 1 && Math.abs(from.y() - to.y()) == 1;
            if (!isDiagonalOne) return ActionError.INVALID_TARGETS;

            fromSquares.add(from);
        }

        for (int i = 0; i < mg.moves().size(); i++) {
            Relocation m = mg.moves().get(i);
            board.move(fromSquares.get(i), m.to());
            events.add(new PublicEvent.PieceMoved(m.pieceId(), fromSquares.get(i), m.to()));
        }
        return null;
    }

    // --- Attacks ---

    private static ActionError resolveStrike(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Attack attack)) return ActionError.INVALID_TARGETS;
        if (attack.enemyIds().size() != 1) return ActionError.INVALID_TARGETS;

        Piece source = board.findPieceById(attack.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;

        int enemyId = attack.enemyIds().get(0);
        Piece enemy = board.findPieceById(enemyId);
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square sourceSq = board.findPiece(attack.sourcePieceId());
        Square enemySq = board.findPiece(enemyId);
        int manhattan = Math.abs(sourceSq.x() - enemySq.x()) + Math.abs(sourceSq.y() - enemySq.y());
        if (manhattan != 1) return ActionError.INVALID_TARGETS;

        board.remove(enemySq);
        events.add(new PublicEvent.PieceEliminated(enemyId, enemySq));
        return null;
    }

    private static ActionError resolvePierce(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Attack attack)) return ActionError.INVALID_TARGETS;
        if (attack.enemyIds().size() != 1) return ActionError.INVALID_TARGETS;

        Piece source = board.findPieceById(attack.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;

        int enemyId = attack.enemyIds().get(0);
        Piece enemy = board.findPieceById(enemyId);
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square sourceSq = board.findPiece(attack.sourcePieceId());
        Square enemySq = board.findPiece(enemyId);
        int distance = def.parameters().getOrDefault(SkillParameter.DISTANCE, 2);

        int dx = enemySq.x() - sourceSq.x();
        int dy = enemySq.y() - sourceSq.y();
        boolean isExactOrthogonal = (dx == 0 && Math.abs(dy) == distance)
                || (dy == 0 && Math.abs(dx) == distance);
        if (!isExactOrthogonal) return ActionError.INVALID_TARGETS;

        int stepX = Integer.signum(dx);
        int stepY = Integer.signum(dy);
        for (int step = 1; step < distance; step++) {
            Square sq = new Square(sourceSq.x() + step * stepX, sourceSq.y() + step * stepY);
            if (board.pieceAt(sq) != null) return ActionError.INVALID_TARGETS;
        }

        board.remove(enemySq);
        events.add(new PublicEvent.PieceEliminated(enemyId, enemySq));
        return null;
    }

    private static ActionError resolveSurround(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Enemy enemyTarget)) return ActionError.INVALID_TARGETS;
        int enemyId = enemyTarget.enemyId();
        Piece enemy = board.findPieceById(enemyId);
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square enemySq = board.findPiece(enemyId);
        int requiredAllies = def.parameters().getOrDefault(SkillParameter.REQUIRED_ALLIES, 2);

        int allyCount = 0;
        Square[] orthogonal = {
                new Square(enemySq.x() + 1, enemySq.y()),
                new Square(enemySq.x() - 1, enemySq.y()),
                new Square(enemySq.x(), enemySq.y() + 1),
                new Square(enemySq.x(), enemySq.y() - 1)
        };
        for (Square sq : orthogonal) {
            Piece p = board.pieceAt(sq);
            if (p != null && p.color() == actor) {
                allyCount++;
            }
        }

        if (allyCount < requiredAllies) return ActionError.INVALID_TARGETS;

        board.remove(enemySq);
        events.add(new PublicEvent.PieceEliminated(enemyId, enemySq));
        return null;
    }

    private static ActionError resolveSweep(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Attack attack)) return ActionError.INVALID_TARGETS;
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (attack.enemyIds().isEmpty() || attack.enemyIds().size() > maxTargets) return ActionError.INVALID_TARGETS;

        Piece source = board.findPieceById(attack.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;
        Square sourceSq = board.findPiece(attack.sourcePieceId());

        Set<Integer> uniqueEnemies = new HashSet<>(attack.enemyIds());
        if (uniqueEnemies.size() != attack.enemyIds().size()) return ActionError.INVALID_TARGETS;

        List<Square> enemySquares = new ArrayList<>();
        for (int enemyId : attack.enemyIds()) {
            Piece enemy = board.findPieceById(enemyId);
            if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

            Square enemySq = board.findPiece(enemyId);
            int manhattan = Math.abs(sourceSq.x() - enemySq.x()) + Math.abs(sourceSq.y() - enemySq.y());
            if (manhattan != 1) return ActionError.INVALID_TARGETS;

            enemySquares.add(enemySq);
        }

        for (int i = 0; i < attack.enemyIds().size(); i++) {
            board.remove(enemySquares.get(i));
            events.add(new PublicEvent.PieceEliminated(attack.enemyIds().get(i), enemySquares.get(i)));
        }
        return null;
    }

    private static ActionError resolveFlank(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Relocate relocate)) return ActionError.INVALID_TARGETS;
        Piece piece = board.findPieceById(relocate.pieceId());
        if (piece == null || piece.color() != actor) return ActionError.INVALID_TARGETS;

        Square from = board.findPiece(relocate.pieceId());
        Square to = relocate.to();
        if (!Board.isInside(to)) return ActionError.INVALID_TARGETS;

        boolean isDiagonalOne = Math.abs(from.x() - to.x()) == 1 && Math.abs(from.y() - to.y()) == 1;
        if (!isDiagonalOne) return ActionError.INVALID_TARGETS;

        Piece destPiece = board.pieceAt(to);
        if (destPiece != null && destPiece.color() == actor) return ActionError.INVALID_TARGETS;

        Piece captured = board.move(from, to);
        events.add(new PublicEvent.PieceMoved(relocate.pieceId(), from, to));
        if (captured != null) {
            events.add(new PublicEvent.PieceEliminated(captured.id(), to));
        }
        return null;
    }

    private static ActionError resolveCrossfire(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Enemy enemyTarget)) return ActionError.INVALID_TARGETS;
        int enemyId = enemyTarget.enemyId();
        Piece enemy = board.findPieceById(enemyId);
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square enemySq = board.findPiece(enemyId);
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 3);

        boolean hasHorizontalSupport = checkClearLineSupport(board, actor, enemySq, -1, 0, range)
                && checkClearLineSupport(board, actor, enemySq, 1, 0, range);
        boolean hasVerticalSupport = checkClearLineSupport(board, actor, enemySq, 0, -1, range)
                && checkClearLineSupport(board, actor, enemySq, 0, 1, range);

        if (!hasHorizontalSupport && !hasVerticalSupport) return ActionError.INVALID_TARGETS;

        board.remove(enemySq);
        events.add(new PublicEvent.PieceEliminated(enemyId, enemySq));
        return null;
    }

    private static boolean checkClearLineSupport(
            Board board, PlayerColor actor, Square from, int stepX, int stepY, int range) {
        for (int dist = 1; dist <= range; dist++) {
            Square sq = new Square(from.x() + dist * stepX, from.y() + dist * stepY);
            if (!Board.isInside(sq)) return false;
            Piece p = board.pieceAt(sq);
            if (p != null) {
                return p.color() == actor;
            }
        }
        return false;
    }

    private static ActionError resolveChainStrike(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Attack attack)) return ActionError.INVALID_TARGETS;
        if (attack.enemyIds().isEmpty() || attack.enemyIds().size() > 2) return ActionError.INVALID_TARGETS;

        Piece source = board.findPieceById(attack.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;
        Square sourceSq = board.findPiece(attack.sourcePieceId());

        int e1Id = attack.enemyIds().get(0);
        Piece e1 = board.findPieceById(e1Id);
        if (e1 == null || e1.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square e1Sq = board.findPiece(e1Id);
        int manhattan1 = Math.abs(sourceSq.x() - e1Sq.x()) + Math.abs(sourceSq.y() - e1Sq.y());
        if (manhattan1 != 1) return ActionError.INVALID_TARGETS;

        Square e2Sq = null;
        int e2Id = -1;
        if (attack.enemyIds().size() == 2) {
            e2Id = attack.enemyIds().get(1);
            if (e2Id == e1Id) return ActionError.INVALID_TARGETS;

            Piece e2 = board.findPieceById(e2Id);
            if (e2 == null || e2.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

            e2Sq = board.findPiece(e2Id);
            int manhattan2 = Math.abs(e1Sq.x() - e2Sq.x()) + Math.abs(e1Sq.y() - e2Sq.y());
            if (manhattan2 != 1) return ActionError.INVALID_TARGETS;
        }

        board.remove(e1Sq);
        events.add(new PublicEvent.PieceEliminated(e1Id, e1Sq));
        if (e2Sq != null) {
            board.remove(e2Sq);
            events.add(new PublicEvent.PieceEliminated(e2Id, e2Sq));
        }
        return null;
    }

    // --- Control ---

    private static ActionError resolveShove(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Displace displace)) return ActionError.INVALID_TARGETS;
        Piece source = board.findPieceById(displace.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;

        Piece enemy = board.findPieceById(displace.enemyId());
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square sourceSq = board.findPiece(displace.sourcePieceId());
        Square enemySq = board.findPiece(displace.enemyId());
        int dx = enemySq.x() - sourceSq.x();
        int dy = enemySq.y() - sourceSq.y();
        if (Math.abs(dx) + Math.abs(dy) != 1) return ActionError.INVALID_TARGETS;

        Square dest = new Square(enemySq.x() + dx, enemySq.y() + dy);
        if (!Board.isInside(dest) || board.pieceAt(dest) != null) return ActionError.INVALID_TARGETS;

        board.move(enemySq, dest);
        events.add(new PublicEvent.PieceMoved(displace.enemyId(), enemySq, dest));
        return null;
    }

    private static ActionError resolvePull(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Displace displace)) return ActionError.INVALID_TARGETS;
        Piece source = board.findPieceById(displace.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;

        Piece enemy = board.findPieceById(displace.enemyId());
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square sourceSq = board.findPiece(displace.sourcePieceId());
        Square enemySq = board.findPiece(displace.enemyId());
        int dx = enemySq.x() - sourceSq.x();
        int dy = enemySq.y() - sourceSq.y();
        boolean isExactTwoOrthogonal = (dx == 0 && Math.abs(dy) == 2) || (dy == 0 && Math.abs(dx) == 2);
        if (!isExactTwoOrthogonal) return ActionError.INVALID_TARGETS;

        Square mid = new Square(sourceSq.x() + dx / 2, sourceSq.y() + dy / 2);
        if (board.pieceAt(mid) != null) return ActionError.INVALID_TARGETS;

        board.move(enemySq, mid);
        events.add(new PublicEvent.PieceMoved(displace.enemyId(), enemySq, mid));
        return null;
    }

    private static ActionError resolveTransposition(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.Swap swap)) return ActionError.INVALID_TARGETS;
        Piece p1 = board.findPieceById(swap.firstPieceId());
        Piece p2 = board.findPieceById(swap.secondPieceId());
        if (p1 == null || p1.color() != actor || p2 == null || p2.color() != actor.opponent()) {
            return ActionError.INVALID_TARGETS;
        }

        Square sq1 = board.findPiece(swap.firstPieceId());
        Square sq2 = board.findPiece(swap.secondPieceId());
        int manhattan = Math.abs(sq1.x() - sq2.x()) + Math.abs(sq1.y() - sq2.y());
        int range = def.parameters().getOrDefault(SkillParameter.RANGE, 2);
        if (manhattan <= 0 || manhattan > range) return ActionError.INVALID_TARGETS;

        board.swap(sq1, sq2);
        events.add(new PublicEvent.PieceMoved(swap.firstPieceId(), sq1, sq2));
        events.add(new PublicEvent.PieceMoved(swap.secondPieceId(), sq2, sq1));
        return null;
    }

    private static ActionError resolveGust(
            SkillDefinition def, Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.ShiftEnemies se)) return ActionError.INVALID_TARGETS;
        int maxTargets = def.parameters().getOrDefault(SkillParameter.MAX_TARGETS, 2);
        if (se.enemyIds().isEmpty() || se.enemyIds().size() > maxTargets) return ActionError.INVALID_TARGETS;

        Set<Integer> uniqueEnemies = new HashSet<>(se.enemyIds());
        if (uniqueEnemies.size() != se.enemyIds().size()) return ActionError.INVALID_TARGETS;

        List<Square> fromSquares = new ArrayList<>();
        Set<Square> destSquares = new HashSet<>();

        for (int enemyId : se.enemyIds()) {
            Piece enemy = board.findPieceById(enemyId);
            if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

            Square from = board.findPiece(enemyId);
            Square to = se.direction().step(from);
            if (!Board.isInside(to) || board.pieceAt(to) != null || !destSquares.add(to)) {
                return ActionError.INVALID_TARGETS;
            }
            fromSquares.add(from);
        }

        for (int i = 0; i < se.enemyIds().size(); i++) {
            int enemyId = se.enemyIds().get(i);
            Square from = fromSquares.get(i);
            Square to = se.direction().step(from);
            board.move(from, to);
            events.add(new PublicEvent.PieceMoved(enemyId, from, to));
        }
        return null;
    }

    private static ActionError resolveBanish(
            Board board, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.RelocateEnemy re)) return ActionError.INVALID_TARGETS;
        Piece source = board.findPieceById(re.sourcePieceId());
        if (source == null || source.color() != actor) return ActionError.INVALID_TARGETS;

        Piece enemy = board.findPieceById(re.enemyId());
        if (enemy == null || enemy.color() != actor.opponent()) return ActionError.INVALID_TARGETS;

        Square sourceSq = board.findPiece(re.sourcePieceId());
        Square enemySq = board.findPiece(re.enemyId());
        if (Math.abs(sourceSq.x() - enemySq.x()) + Math.abs(sourceSq.y() - enemySq.y()) != 1) {
            return ActionError.INVALID_TARGETS;
        }

        Square to = re.to();
        if (!Board.isInside(to) || board.pieceAt(to) != null) return ActionError.INVALID_TARGETS;

        int enemyStartingRow = enemy.color() == PlayerColor.BLUE ? 0 : 6;
        if (to.y() != enemyStartingRow) return ActionError.INVALID_TARGETS;

        board.move(enemySq, to);
        events.add(new PublicEvent.PieceMoved(re.enemyId(), enemySq, to));
        return null;
    }

    // --- Resources / Choice ---

    private static ActionError resolveManaSurge(
            SkillDefinition def, GameState next, PlayerColor actor, SkillTargets targets) {
        if (!(targets instanceof SkillTargets.None)) return ActionError.INVALID_TARGETS;
        int gain = def.parameters().getOrDefault(SkillParameter.MANA_GAIN, 2);
        int cap = next.rules().manaCap();
        if (actor == PlayerColor.BLUE) {
            next.setBlueMana(Math.min(cap, next.blueMana() + gain));
        } else {
            next.setRedMana(Math.min(cap, next.redMana() + gain));
        }
        return null;
    }

    private static ActionError resolveRefocus(
            GameState next, PlayerColor actor, SkillTargets targets, List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.None)) return ActionError.INVALID_TARGETS;
        List<CardInstance> hand = next.mutableSkillHand(actor);
        int count = hand.size();
        next.mutableSpentSkills().addAll(hand);
        hand.clear();
        next.drawSkills(actor, count, events);
        return null;
    }

    private static ActionError resolveSteal(GameState next, PlayerColor actor, SkillTargets targets) {
        if (!(targets instanceof SkillTargets.None) || next.mutableSkillHand(actor.opponent()).isEmpty())
            return ActionError.INVALID_TARGETS;
        var enemy = next.mutableSkillHand(actor.opponent());
        var stolen = enemy.remove(next.randomState().nextInt(enemy.size()));
        next.mutableSkillHand(actor).add(stolen);
        return null;
    }

    private static ActionError resolveSpy(GameState next, PlayerColor actor, SkillTargets targets) {
        if (!(targets instanceof SkillTargets.None) || next.mutableSkillHand(actor.opponent()).isEmpty())
            return ActionError.INVALID_TARGETS;
        next.revealEnemyHand(actor);
        return null;
    }

    private static ActionError resolveSabotage(GameState next, PlayerColor actor, SkillTargets targets) {
        var enemy = next.mutableSkillHand(actor.opponent());
        if (!(targets instanceof SkillTargets.None) || enemy.isEmpty()) return ActionError.INVALID_TARGETS;
        next.mutableSpentSkills().add(enemy.remove(next.randomState().nextInt(enemy.size())));
        return null;
    }

    private static ActionError resolveBarter(GameState next, PlayerColor actor, SkillTargets targets) {
        var own = next.mutableSkillHand(actor); var enemy = next.mutableSkillHand(actor.opponent());
        if (!(targets instanceof SkillTargets.None) || own.isEmpty() || enemy.isEmpty()) return ActionError.INVALID_TARGETS;
        int ownSlot = next.randomState().nextInt(own.size()), enemySlot = next.randomState().nextInt(enemy.size());
        CardInstance offered = own.set(ownSlot, enemy.get(enemySlot));
        enemy.set(enemySlot, offered);
        return null;
    }

    private static ActionError resolveSiphon(GameState next, PlayerColor actor, SkillTargets targets) {
        if (!(targets instanceof SkillTargets.None)) return ActionError.INVALID_TARGETS;
        int mine = actor == PlayerColor.BLUE ? next.blueMana() : next.redMana();
        int theirs = actor == PlayerColor.BLUE ? next.redMana() : next.blueMana();
        int amount = Math.min(2, Math.min(theirs, next.rules().manaCap() - mine));
        if (amount <= 0) return ActionError.INVALID_TARGETS;
        if (actor == PlayerColor.BLUE) { next.setBlueMana(mine + amount); next.setRedMana(theirs - amount); }
        else { next.setRedMana(mine + amount); next.setBlueMana(theirs - amount); }
        return null;
    }

    private static ActionError resolveDraw(GameState next, PlayerColor actor, SkillTargets targets,
                                           List<PublicEvent> events) {
        if (!(targets instanceof SkillTargets.None)) return ActionError.INVALID_TARGETS;
        next.drawSkills(actor, 2, events);
        return null;
    }

    private static ActionError resolveForesight(SkillTargets targets) {
        if (!(targets instanceof SkillTargets.None)) return ActionError.INVALID_TARGETS;
        return null;
    }
}
