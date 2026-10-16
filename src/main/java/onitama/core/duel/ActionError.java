package onitama.core.duel;

/**
 * Standard reasons for action validation rejection.
 */
public enum ActionError {
    WRONG_PHASE,
    NOT_YOUR_TURN,
    CARD_NOT_HELD,
    INSUFFICIENT_MANA,
    INVALID_TARGETS,
    INVALID_CHOICE,
    GAME_FINISHED,
    MATCH_PAUSED,
    STALE_REVISION,
    OLD_SEQUENCE,
    SEQUENCE_GAP,
    SEQUENCE_CONFLICT
}
