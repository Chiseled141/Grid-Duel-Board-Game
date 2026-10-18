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
    private final List<CardInstance> destroyedSkills = new ArrayList<>();
    private final List<CardInstance> blueBurnedDraws = new ArrayList<>();
    private final List<CardInstance> redBurnedDraws = new ArrayList<>();
    private List<CardInstance> blueIntel = List.of(), redIntel = List.of();
    private int blueScore, redScore;
    private boolean blueOpeningConfirmed, redOpeningConfirmed;
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
        this.destroyedSkills.addAll(other.destroyedSkills);
        this.blueIntel = other.blueIntel;
        this.redIntel = other.redIntel;
        this.blueOpeningConfirmed = other.blueOpeningConfirmed;
        this.redOpeningConfirmed = other.redOpeningConfirmed;
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

    /** Permanently destroyed overflow draws, retained only by the host for conservation. */
    public List<CardInstance> destroyedSkills() { return List.copyOf(destroyedSkills); }

    /** Only the drawing player receives these identities for the current accepted action. */
    public List<CardInstance> burnedDrawsFor(PlayerColor viewer) {
        return List.copyOf(viewer == PlayerColor.BLUE ? blueBurnedDraws : redBurnedDraws);
    }

    /** Private snapshot granted by Espionage, valid through the caster's current turn. */
    public List<CardInstance> intelFor(PlayerColor viewer) {
        return viewer == PlayerColor.BLUE ? blueIntel : redIntel;
    }

    void revealEnemyHand(PlayerColor actor) {
        List<CardInstance> snapshot = List.copyOf(mutableSkillHand(actor.opponent()));
        if (actor == PlayerColor.BLUE) blueIntel = snapshot; else redIntel = snapshot;
    }

    void clearIntel() { blueIntel = List.of(); redIntel = List.of(); }

    void drawSkills(PlayerColor actor, int count, List<PublicEvent> events) {
        for (int i = 0; i < count && !skillDeck.isEmpty(); i++) {
            CardInstance drawn = skillDeck.remove(0);
            if (mutableSkillHand(actor).size() < 6) {
                mutableSkillHand(actor).add(drawn);
            } else {
                destroyedSkills.add(drawn);
                (actor == PlayerColor.BLUE ? blueBurnedDraws : redBurnedDraws).add(drawn);
                events.add(new PublicEvent.CardBurned(actor));
            }
        }
    }

    void spendSkill(PlayerColor actor, String instanceId) {
        CardInstance card = mutableSkillHand(actor).stream()
                .filter(c -> c.instanceId().equals(instanceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Skill is not held"));
        mutableSkillHand(actor).remove(card);
        spentSkills.add(card);
    }

    /** Public layout fixed for the lifetime of this match. */
    public ObjectiveLayout objectives() { return objectives; }

    /** Whether this player has committed the one-time opening choice. */
    public boolean openingConfirmed(PlayerColor color) {
        return color == PlayerColor.BLUE ? blueOpeningConfirmed : redOpeningConfirmed;
    }

    void confirmOpening(PlayerColor color) {
        if (color == PlayerColor.BLUE) blueOpeningConfirmed = true; else redOpeningConfirmed = true;
    }

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
     * Used for Foresight, whose committed choice resolves before normal replacement.
     *
     * @throws IllegalArgumentException if the specified instance is not held in the player's skill hand
     */
    void consumeHeldSkill(PlayerColor player, String skillInstanceId) {
        spendSkill(player, skillInstanceId);
        drawSkills(player, 1, new ArrayList<>());
    }
}
