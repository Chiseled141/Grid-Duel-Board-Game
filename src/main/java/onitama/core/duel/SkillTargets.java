package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import onitama.core.Square;

/**
 * Target selection payloads for skill cards (Step 3 contract).
 */
public sealed interface SkillTargets permits
        SkillTargets.Relocate,
        SkillTargets.Swap,
        SkillTargets.Attack,
        SkillTargets.Enemy,
        SkillTargets.Displace,
        SkillTargets.RelocateEnemy,
        SkillTargets.ShiftEnemies,
        SkillTargets.MoveGroup,
        SkillTargets.Rally,
        SkillTargets.None {

    record Relocate(int pieceId, Square to) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public Relocate {
            Objects.requireNonNull(to, "to must not be null");
        }
    }

    record Swap(int firstPieceId, int secondPieceId) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record Attack(int sourcePieceId, List<Integer> enemyIds) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public Attack {
            enemyIds = List.copyOf(enemyIds);
        }
    }

    record Enemy(int enemyId) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record Displace(int sourcePieceId, int enemyId) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record RelocateEnemy(int sourcePieceId, int enemyId, Square to) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public RelocateEnemy {
            Objects.requireNonNull(to, "to must not be null");
        }
    }

    record ShiftEnemies(List<Integer> enemyIds, Direction direction) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public ShiftEnemies {
            enemyIds = List.copyOf(enemyIds);
            Objects.requireNonNull(direction, "direction must not be null");
        }
    }

    record MoveGroup(List<Relocation> moves) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public MoveGroup {
            moves = List.copyOf(moves);
        }
    }

    record Rally(int anchorId, List<Relocation> moves) implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public Rally {
            moves = List.copyOf(moves);
        }
    }

    record None() implements SkillTargets, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
