package onitama.core.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import onitama.core.PlayerColor;

/**
 * Authoritative, non-Serializable state of a 7x7 Grid Duel match.
 * Thread-confined to a MatchSession executor; mutated only through RulesEngine.
 */
public final class GameState {

    private final RulesConfig rules;
    private final Board board;
    private final ObjectiveLayout objectives;
    private final List<String> blueMovementHand;
    private final List<String> redMovementHand;
    private final List<CardInstance> blueSkillHand;
    private final List<CardInstance> redSkillHand;
    private final List<String> movementDeck;
    private final List<CardInstance> skillDeck;
    private final List<CardInstance> spentSkills;
    private int blueScore, redScore;
    private int blueMana;
    private int redMana;
    private PlayerColor turn;
    private TurnPhase phase;
    private int startedTurns;
    private int completedTurns;
    private long revision;
    private final GameRandom randomState;
    private PendingForesight pendingChoice;
    private GameOutcome outcome;

    GameState(
            RulesConfig rules,
            Board board,
            List<String> blueMovementHand,
            List<String> redMovementHand,
            List<CardInstance> blueSkillHand,
            List<CardInstance> redSkillHand,
            List<String> movementDeck,
            List<CardInstance> skillDeck,
            List<CardInstance> spentSkills,
            int blueMana,
            int redMana,
            PlayerColor turn,
            TurnPhase phase,
            int startedTurns,
            int completedTurns,
            long revision,
            GameRandom randomState,
            GameOutcome outcome) {
        this(rules, board, blueMovementHand, redMovementHand, blueSkillHand, redSkillHand,
                movementDeck, skillDeck, spentSkills, blueMana, redMana, turn, phase,
                startedTurns, completedTurns, revision, randomState, outcome, ObjectiveLayout.standard());
    }

    GameState(RulesConfig rules, Board board, List<String> blueMovementHand, List<String> redMovementHand,
              List<CardInstance> blueSkillHand, List<CardInstance> redSkillHand, List<String> movementDeck,
              List<CardInstance> skillDeck, List<CardInstance> spentSkills, int blueMana, int redMana,
              PlayerColor turn, TurnPhase phase, int startedTurns, int completedTurns, long revision,
              GameRandom randomState, GameOutcome outcome, ObjectiveLayout objectives) {
        this.objectives = Objects.requireNonNull(objectives, "objectives must not be null");
        this.rules = Objects.requireNonNull(rules, "rules must not be null");
        this.board = Objects.requireNonNull(board, "board must not be null");
        this.blueMovementHand = new ArrayList<>(blueMovementHand);
        this.redMovementHand = new ArrayList<>(redMovementHand);
        this.blueSkillHand = new ArrayList<>(blueSkillHand);
        this.redSkillHand = new ArrayList<>(redSkillHand);
        this.movementDeck = new ArrayList<>(movementDeck);
        this.skillDeck = new ArrayList<>(skillDeck);
        this.spentSkills = new ArrayList<>(spentSkills);
        this.blueMana = blueMana;
        this.redMana = redMana;
        this.turn = Objects.requireNonNull(turn, "turn must not be null");
        this.phase = Objects.requireNonNull(phase, "phase must not be null");
        this.startedTurns = startedTurns;
        this.completedTurns = completedTurns;
        this.revision = revision;
        this.randomState = Objects.requireNonNull(randomState, "randomState must not be null");
        this.pendingChoice = null;
        this.outcome = outcome;
    }

    /**
     * Creates an independent deep copy of another state, including board, collections and RNG.
     */
    GameState(GameState other) {
        this.blueScore = other.blueScore;
        this.redScore = other.redScore;
        this.objectives = other.objectives;
        this.rules = other.rules;
        this.board = new Board(other.board);
        this.blueMovementHand = new ArrayList<>(other.blueMovementHand);
        this.redMovementHand = new ArrayList<>(other.redMovementHand);
        this.blueSkillHand = new ArrayList<>(other.blueSkillHand);
        this.redSkillHand = new ArrayList<>(other.redSkillHand);
        this.movementDeck = new ArrayList<>(other.movementDeck);
        this.skillDeck = new ArrayList<>(other.skillDeck);
        this.spentSkills = new ArrayList<>(other.spentSkills);
        this.blueMana = other.blueMana;
        this.redMana = other.redMana;
        this.turn = other.turn;
        this.phase = other.phase;
        this.startedTurns = other.startedTurns;
        this.completedTurns = other.completedTurns;
        this.revision = other.revision;
        this.randomState = other.randomState.copy();
        this.pendingChoice = other.pendingChoice;
        this.outcome = other.outcome;
    }

    /** Public layout fixed for the lifetime of this match. */
    public ObjectiveLayout objectives() { return objectives; }

    public RulesConfig rules() { return rules; }
    public Board board() { return board; }
    public List<String> blueMovementHand() { return Collections.unmodifiableList(blueMovementHand); }
    public List<String> redMovementHand() { return Collections.unmodifiableList(redMovementHand); }
    public List<CardInstance> blueSkillHand() { return Collections.unmodifiableList(blueSkillHand); }
    public List<CardInstance> redSkillHand() { return Collections.unmodifiableList(redSkillHand); }
    public List<String> movementDeck() { return Collections.unmodifiableList(movementDeck); }
    public List<CardInstance> skillDeck() { return Collections.unmodifiableList(skillDeck); }
    public List<CardInstance> spentSkills() { return Collections.unmodifiableList(spentSkills); }
    /** Public score earned by eliminations and center control. */
    public int score(PlayerColor color) { return color == PlayerColor.BLUE ? blueScore : redScore; }

    void addScore(PlayerColor color, int amount) {
        if (color == PlayerColor.BLUE) blueScore += amount; else redScore += amount;
    }

    public int blueMana() { return blueMana; }
    public int redMana() { return redMana; }
    public PlayerColor turn() { return turn; }
    public TurnPhase phase() { return phase; }
    public int startedTurns() { return startedTurns; }
    public int completedTurns() { return completedTurns; }
    public long revision() { return revision; }
    public PendingForesight pendingChoice() { return pendingChoice; }
    public GameOutcome outcome() { return outcome; }

    List<String> mutableMovementHand(PlayerColor color) {
        return color == PlayerColor.BLUE ? blueMovementHand : redMovementHand;
    }

    List<CardInstance> mutableSkillHand(PlayerColor color) {
        return color == PlayerColor.BLUE ? blueSkillHand : redSkillHand;
    }

    List<String> mutableMovementDeck() { return movementDeck; }
    List<CardInstance> mutableSkillDeck() { return skillDeck; }
    List<CardInstance> mutableSpentSkills() { return spentSkills; }
    GameRandom randomState() { return randomState; }

    void setBlueMana(int mana) { this.blueMana = mana; }
    void setRedMana(int mana) { this.redMana = mana; }
    void setTurn(PlayerColor turn) { this.turn = turn; }
    void setPhase(TurnPhase phase) { this.phase = phase; }
    void setStartedTurns(int turns) { this.startedTurns = turns; }
    void setCompletedTurns(int turns) { this.completedTurns = turns; }
    void setRevision(long revision) { this.revision = revision; }
    void setPendingChoice(PendingForesight pendingChoice) { this.pendingChoice = pendingChoice; }
    void setOutcome(GameOutcome outcome) { this.outcome = outcome; }

    /**
     * Internal bookkeeping operation: consumes a held skill instance from player's hand,
     * appends it to the public spent pile, and draws one replacement from index 0 of the
     * skill deck if nonempty.
     *
     * <p>Note: Skill casting is not integrated yet. This operation is invoked by the later
     * skill resolver after effect validation.
     *
     * @throws IllegalArgumentException if the specified instance is not held in the player's skill hand
     */
    void consumeHeldSkill(PlayerColor player, String skillInstanceId) {
        List<CardInstance> hand = mutableSkillHand(player);
        int foundIndex = -1;
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).instanceId().equals(skillInstanceId)) {
                foundIndex = i;
                break;
            }
        }
        if (foundIndex < 0) {
            throw new IllegalArgumentException("skill instance not in " + player + " hand: " + skillInstanceId);
        }
        CardInstance spent = hand.remove(foundIndex);
        spentSkills.add(spent);
        if (!skillDeck.isEmpty()) {
            CardInstance replacement = skillDeck.remove(0);
            hand.add(replacement);
        }
    }
}
