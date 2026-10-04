package onitama.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The built-in opponent of the client's single-player practice mode, in three
 * difficulty levels ({@link Difficulty}). Like the rules engine this class is
 * pure logic — no I/O, no threading; the caller supplies the randomness.
 *
 * <p><b>Rookie</b> plays a forgiving heuristic: it never misses an instant
 * win, usually takes free captures and avoids the most obvious blunders, but
 * rolls a "beginner mercy" chance of playing a random card instead — so it is
 * beatable and its play stays varied.</p>
 *
 * <p><b>Senior</b> and <b>Legend</b> search with copy-make negamax + alpha-beta
 * (both hands are open information). Senior looks {@code 2} half-moves ahead:
 * it no longer hangs pieces or hands the opponent an instant win. Legend looks
 * {@code 4} half-moves ahead with iterative deepening inside a wall-clock
 * budget, finds short forced wins, and punishes positional drift. The
 * evaluation counts material and each Master's progress toward the stream.</p>
 */
public final class PracticeBot {

    /** Chance (percent) of the Rookie ignoring the scoring and playing a random card. */
    static final int MERCY_PERCENT = 15;

    private static final int JITTER = 12;
    private static final int CAPTURE_BONUS = 60;
    private static final int FORWARD_BONUS = 6;
    private static final int MASTER_FORWARD_BONUS = 8;
    private static final int ESCAPE_BONUS = 40;
    private static final int HANGING_PENALTY = 70;
    private static final int MASTER_HANGING_PENALTY = 200;

    // --- search constants (Senior / Legend) ---

    private static final int WIN_SCORE = 100_000;
    private static final int STUDENT_VALUE = 90;
    private static final int MASTER_ADVANCE = 10;
    private static final int LEAF_JITTER = 2;
    /** Wall-clock budget for the whole search (iterative deepening cut-off). */
    private static final long TIME_BUDGET_MS = 1200;

    /** Thrown when the time budget runs out; the last completed depth wins. */
    private static final class TimeUp extends RuntimeException {
    }

    private PracticeBot() {
    }

    /**
     * Picks the half-move the bot plays.
     *
     * @param state     the game to move in; the bot never mutates it (an
     *                  internal copy is searched)
     * @param botColor  the bot's color
     * @param difficulty the level to play at
     * @param random    the caller's randomness source
     * @return the chosen move, or {@code null} when the bot has no legal move
     *         and must pass (the caller then discards a hand card via
     *         {@link RulesEngine#pass})
     */
    public static Move chooseMove(GameState state, PlayerColor botColor,
                                  Difficulty difficulty, Random random) {
        if (difficulty == Difficulty.ROOKIE) {
            return rookieMove(state, botColor, random);
        }
        List<Move> legal = RulesEngine.legalMoves(state);
        if (legal.isEmpty()) {
            return null;
        }
        return search(new GameState(state), legal, difficulty.searchDepth(), random);
    }

    // ------------------------------------------------------------------
    // Rookie: the forgiving heuristic
    // ------------------------------------------------------------------

    private static Move rookieMove(GameState state, PlayerColor botColor, Random random) {
        List<Move> legal = RulesEngine.legalMoves(state);
        if (legal.isEmpty()) {
            return null;
        }
        for (Move move : legal) {
            if (winsInstantly(state, botColor, move)) {
                return move;
            }
        }
        if (random.nextInt(100) < MERCY_PERCENT) {
            return legal.get(random.nextInt(legal.size()));
        }
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : legal) {
            int score = rookieScore(state, botColor, move, random);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    /** True if the move ends the game on the spot (stone or stream). */
    private static boolean winsInstantly(GameState state, PlayerColor botColor, Move move) {
        Piece victim = state.board().pieceAt(move.to());
        if (victim != null && victim.master()) {
            return true;
        }
        Piece mover = state.board().pieceAt(move.from());
        return mover.master() && move.to().equals(botColor.opponent().templeArch());
    }

    private static int rookieScore(GameState state, PlayerColor botColor, Move move, Random random) {
        int score = random.nextInt(JITTER);
        Piece victim = state.board().pieceAt(move.to());
        if (victim != null) {
            score += CAPTURE_BONUS;
        }
        Piece mover = state.board().pieceAt(move.from());
        int forward = forwardSteps(botColor, move);
        score += forward * FORWARD_BONUS;
        if (mover.master()) {
            score += forward * MASTER_FORWARD_BONUS;
        }
        if (isThreatened(state, botColor, move.from())) {
            score += ESCAPE_BONUS;
        }
        int hanging = mover.master() ? MASTER_HANGING_PENALTY : HANGING_PENALTY;
        if (isThreatened(state, botColor, move.to())) {
            score -= hanging;
        }
        return score;
    }

    /** Positive when the move advances toward the enemy home row. */
    private static int forwardSteps(PlayerColor mover, Move move) {
        return mover == PlayerColor.BLUE
                ? move.to().y() - move.from().y()
                : move.from().y() - move.to().y();
    }

    /**
     * True if any enemy piece could move onto the square with one of the
     * cards currently in the enemy hand. Onitama is open information, so
     * the bot can check this exactly; the Rookie only uses it to avoid the
     * most obvious blunders.
     */
    private static boolean isThreatened(GameState state, PlayerColor defender, Square square) {
        PlayerColor enemy = defender.opponent();
        List<Square> enemyPieces = state.board().occupiedBy(enemy);
        for (Card card : state.hand(enemy)) {
            for (Square from : enemyPieces) {
                // No guard for from.equals(square) is needed: a card can never
                // move a piece onto its own square (no (0,0) offset exists),
                // and an enemy piece can never share the defender's square.
                if (card.destinationsFrom(from, enemy).contains(square)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Senior / Legend: copy-make negamax with alpha-beta
    // ------------------------------------------------------------------

    private static Move search(GameState state, List<Move> legal, int maxDepth, Random random) {
        long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;
        List<Move> ordered = orderMoves(state, legal, random);
        Move best = ordered.get(0);
        try {
            for (int depth = 2; depth <= maxDepth; depth++) {
                Move depthBest = null;
                int alpha = Integer.MIN_VALUE + 1;
                for (Move move : ordered) {
                    GameState next = new GameState(state);
                    RulesEngine.apply(next, move);
                    int score = -negamax(next, depth - 1, Integer.MIN_VALUE + 1, -alpha,
                            1, deadline, random);
                    if (depthBest == null || score > alpha) {
                        depthBest = move;
                        alpha = score;
                    }
                }
                best = depthBest;
                // Search yesterday's best move first at the next depth.
                ordered.remove(best);
                ordered.add(0, best);
                if (alpha >= WIN_SCORE - 64) {
                    break; // a forced win is already found; deeper search is noise
                }
            }
        } catch (TimeUp timeUp) {
            // keep the best move of the last fully searched depth
        }
        return best;
    }

    /** Negamax from the perspective of {@code state.turn()}; never mutates state. */
    private static int negamax(GameState state, int depth, int alpha, int beta,
                               int ply, long deadline, Random random) {
        if (System.currentTimeMillis() > deadline) {
            throw new TimeUp();
        }
        if (!state.isOngoing()) {
            if (state.winner() == null) {
                return 0; // move-limit draw
            }
            return state.winner() == state.turn() ? WIN_SCORE - ply : -(WIN_SCORE - ply);
        }
        if (depth == 0) {
            return evaluate(state, random);
        }
        List<Move> legal = RulesEngine.legalMoves(state);
        if (legal.isEmpty()) {
            // Forced pass: the discard choice matters (it hands a card over),
            // so branch on both hand cards.
            int best = Integer.MIN_VALUE + 1;
            for (Card card : state.hand(state.turn())) {
                GameState next = new GameState(state);
                RulesEngine.pass(next, card.id());
                int score = -negamax(next, depth - 1, -beta, -alpha, ply + 1, deadline, random);
                if (score > best) {
                    best = score;
                }
                if (best > alpha) {
                    alpha = best;
                }
                if (alpha >= beta) {
                    break;
                }
            }
            return best;
        }
        int best = Integer.MIN_VALUE + 1;
        for (Move move : orderMoves(state, legal, random)) {
            GameState next = new GameState(state);
            RulesEngine.apply(next, move);
            int score = -negamax(next, depth - 1, -beta, -alpha, ply + 1, deadline, random);
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /**
     * Orders moves for the search: game-ending moves first, then captures,
     * then the rest — with a small stable random tiebreak so games vary.
     * Returns a new list; the input is not modified.
     */
    private static List<Move> orderMoves(GameState state, List<Move> legal, Random random) {
        record Keyed(Move move, int key) {
        }
        List<Keyed> keyed = new ArrayList<>(legal.size());
        for (Move move : legal) {
            int key = random.nextInt(50);
            Piece victim = state.board().pieceAt(move.to());
            if (victim != null) {
                key += victim.master() ? 2000 : 1000;
            }
            Piece mover = state.board().pieceAt(move.from());
            if (mover != null && mover.master()
                    && move.to().equals(mover.color().opponent().templeArch())) {
                key += 1500;
            }
            keyed.add(new Keyed(move, key));
        }
        keyed.sort(Comparator.comparingInt(Keyed::key).reversed());
        List<Move> ordered = new ArrayList<>(legal.size());
        keyed.forEach(entry -> ordered.add(entry.move()));
        return ordered;
    }

    /**
     * Static evaluation from the perspective of the side to move: material
     * (students) plus each Master's progress toward the stream. Direct
     * tactics are the search's job; this term only shapes the quiet game.
     */
    private static int evaluate(GameState state, Random random) {
        int score = random.nextInt(2 * LEAF_JITTER + 1) - LEAF_JITTER;
        PlayerColor me = state.turn();
        score += sideTerms(state, me) - sideTerms(state, me.opponent());
        return score;
    }

    private static int sideTerms(GameState state, PlayerColor color) {
        int terms = 0;
        Square arch = color.opponent().templeArch();
        for (Square from : state.board().occupiedBy(color)) {
            Piece piece = state.board().pieceAt(from);
            if (piece.master()) {
                int distance = Math.abs(arch.x() - from.x()) + Math.abs(arch.y() - from.y());
                terms += (8 - distance) * MASTER_ADVANCE;
            } else {
                terms += STUDENT_VALUE;
            }
        }
        return terms;
    }
}
