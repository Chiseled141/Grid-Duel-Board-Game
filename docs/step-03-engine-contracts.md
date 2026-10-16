# Step 3 — Engine and integration contracts

Status: **implemented in `onitama.core.duel` and the live client/server** (2026-10-12).
The staging amendment below records historical migration scope; current status and
review evidence are in [the continuation record](handoffs/gameplay-progress.md).

### Step 4 staging amendment (2026-10-11)

To prevent breaking the running legacy 5×5 game (network messages, UI, bots, and tests) before coordinated network/client migration, Step 4 implements the target engine under **`onitama.core.duel`** (tests under `src/test/java/onitama/core/duel`):
- Types in `onitama.core.duel` retain the Step 3 simple names: `GameState`, `RulesEngine`, `Board`, `Piece`, `RulesConfig`, etc.
- Immutable `onitama.core.PlayerColor`, `Square`, `Offset`, and `Card` are reused directly; `CardDeck.catalog()` supplies the 16 movement card definitions.
- Legacy `onitama.core.Board`, `Piece`, `GameState`, and `RulesEngine` remain unchanged for legacy runtime and existing tests.
- Scope implemented in Step 4: 7×7 board, 6 pieces per player, SplitMix64 RNG, movement recycling, internal skill deck consumption/replacement, match setup/mana, movement/skip/end-turn/resign/forfeit transitions, elimination & draw victory, `PlayerView` projection, public events, and `Targeting.legalMoves(PlayerView)`.
- Deferred to later steps: skill effects dispatcher, Foresight choices, wire messages, config properties loader, and GUI integration. `GameAction` implements `MovePiece`, `SkipMovement`, `EndTurn`, and `Resign`. `ForesightView` exists for `PlayerView` typing with `ownChoice=null`.
At the later coordinated cutover, `onitama.core.duel` becomes the sole runtime engine.

[The authoritative spec](../GRID-DUEL-BOARD-GAME_SPEC.md) owns gameplay. This document fixes names, ownership, data shapes and integration semantics for the implementing tasks. If a rule needs changing, edit the spec first. No runtime Java, protocol or database changes are made in Step 3. Do not add empty implementations or make the current client consume these types prematurely.

## 1. Ownership and migration boundaries

| Package | Owns | Must not do |
| --- | --- | --- |
| `onitama.core.duel` (staged) / `onitama.core` | State, immutable definitions, actions, targeting, effects, projection and seeded random transitions | Files, sockets, Swing or player authentication |
| `onitama.server` | Authenticated actor, match identity, request sequencing, disconnect timers, config loading at match creation, persistence | Trust an actor or result supplied by a client |
| `onitama.net` | Version handshake, action requests and permitted views | Serialize authoritative state, decks or RNG |
| `onitama.config` (new) | UTF-8 properties parsing and validation | Mutate an active match or add a JSON dependency |
| `onitama.client.state` / `ui` | Received view, selection draft, pending request, display and animation | Reach into server state or decide that an action succeeded locally |
| Practice host / bots | Host runs the same engine; bot policy receives only a player view | Give a policy actual hidden cards or host RNG state |

Evolve existing `GameState`, `RulesEngine`, `Board`, `Piece` and `CardDeck` in their assigned migration. Reuse `PlayerColor`, `Square`, `Offset`, `Card` and `Elo`; remove Master/transit/stamp-start dependencies deliberately with consumers/tests. Do not maintain two permanent game engines. Staged types in `onitama.core.duel` isolate the new engine during construction; switch engine/server/client consumers in coordinated buildable increments. Menu music remains independent.

All wire DTOs are final records implementing Serializable with `serialVersionUID=1L`. Copy collections defensively; never expose mutable arrays/maps/lists or share a mutable Board with a view. Nullable fields are listed explicitly. A missing required value, unknown ID, invalid coordinate, oversized list or invalid enum is rejected before board indexing. Wire constructors and the server boundary both enforce applicable bounds.

## 2. Definitions and identity

Use these exact names under `onitama.core.duel` (staged) unless the contract is updated first:

```java
record Piece(int id, PlayerColor color) {}
record PlacedPiece(Piece piece, Square square) {}
record CardInstance(String instanceId, String definitionId) {}
record SkillDefinition(String id, String name, SkillCategory category,
                       int manaCost, int copies, Map<SkillParameter, Integer> parameters,
                       String descriptionTemplate, String artworkPath) {}
record RulesConfig(String rulesVersion, int movementHandSize, int skillHandSize,
                   int initialMana, int manaPerTurn, int manaCap, int turnLimit,
                   List<Card> movementCards, List<SkillDefinition> skills) {}
enum SkillCategory { MOVEMENT, ATTACK, CONTROL, RESOURCE }
enum SkillParameter { DISTANCE, RANGE, MAX_TARGETS, REQUIRED_ALLIES, MANA_GAIN, LOOKAHEAD }
enum TurnPhase { MOVEMENT, SKILL, CHOICE, END, FINISHED }
enum EndReason { ELIMINATION, FORFEIT, DRAW }
record GameOutcome(PlayerColor winner, EndReason reason) {}
```

Shapes omit routine constructors/Javadoc/serialization declarations. `GameOutcome.winner` is null only for DRAW; ongoing state has a null outcome. Legacy STONE/STREAM strings remain DB history, not new live EndReason values.

Piece IDs 0–5 are Blue, 6–11 Red, assigned left-to-right along each starting row. IDs persist through moves/swaps; eliminated IDs never return. Coordinates remain canonical Blue-relative, including network targets. GUI converts clicks for Red before constructing actions.

Movement cards retain existing lowercase definition IDs and one copy each. Skill instances are `definitionId#ordinal`, ordinals starting at 1, e.g. `strike#1`. An instance occupies exactly one of hand/deck/spent. This distinction supports later configured multiple copies without confusing a held copy with another. Public skill definitions and copy counts are not secret; membership/order of hidden zones is secret. Do not expose instance IDs for opponent held cards. All definition and instance IDs are bounded to 64 characters.

`RulesConfig` contains public definitions, never shuffled order, seed, hands or mutable configuration. Rules version is `grid-duel-v1`; protocol version is separately 2; config schema version is separately 1.

## 3. Authoritative state and pure engine API

`GameState` becomes non-Serializable, with no public mutable internals. Host code replaces a state reference after an accepted transition. It contains:

| Field | Meaning |
| --- | --- |
| `RulesConfig rules` | Immutable snapshot selected at match creation |
| `Board board` | 49 squares, zero/one piece per square, unique surviving IDs |
| `blueMovementHand`, `redMovementHand` | Ordered movement definition IDs; two per player |
| `blueSkillHand`, `redSkillHand` | Ordered CardInstance lists |
| `movementDeck`, `skillDeck` | Private ordered lists; index 0 is the top |
| `spentSkills` | Public chronological list of consumed instances |
| `blueMana`, `redMana` | Mana balances including any pending reserved mana |
| `turn`, `phase` | Active color and phase |
| `startedTurns`, `completedTurns` | Start at 1 and 0 after initial mana grant |
| `revision` | Starts at 0; increments once per accepted player action or terminal host event |
| `randomState` | Copyable private PRNG state; never serialized or exposed to a bot |
| `pendingChoice` | Null or private PendingForesight |
| `outcome` | Null while ongoing |

Use a package-private copyable PRNG wrapper (`GameRandom`) with `copy()`, `nextInt(bound)` and `shuffle(list)`. Use SplitMix64: initialize its 64-bit state to seed; each nextLong adds `0x9E3779B97F4A7C15L`, then mixes with xor-shifts 30/27/31 and multipliers `0xBF58476D1CE4E5B9L` and `0x94D049BB133111EBL` in that order, using Java long overflow and unsigned shifts. For nextInt(bound>0), draw `u=nextLong() >>> 1`, take `r=u % bound`, and retry while `u-r+(bound-1)` overflows negative; return `(int) r`. Shuffle uses descending Fisher–Yates with nextInt(i+1). Copy preserves the exact state. Fixed-seed tests must pin sequences; never share a mutable RNG between candidate and original states. Random draws occur only in a candidate transition, not validation/target highlighting. No cryptographic randomness is required inside game simulation. The host generates the unpredictable live seed outside core and keeps it private.

```java
// RulesEngine static public API
GameState newGame(RulesConfig rules, long seed);
ActionResult apply(GameState state, PlayerColor actor, GameAction action);
ActionResult forfeit(GameState state, PlayerColor loser);
PlayerView viewFor(GameState state, PlayerColor viewer);

sealed interface ActionResult {
    record Accepted(GameState nextState, List<PublicEvent> events) implements ActionResult {}
    record Rejected(ActionError error) implements ActionResult {}
}
```

`apply` never mutates its input. Validate all public structure/ownership/phase constraints, work on a deep candidate copy (including decks, pending choice and RNG), validate the complete skill sequence, then return Accepted. On rejection return only an error; original state including random progression remains exactly unchanged. UI legality must not invoke `apply` on a fabricated full state. Host calls `forfeit` for grace expiry; a player uses Resign below. Repeat termination rejects with GAME_FINISHED and never writes a second match result.

Setup random call order is movement shuffle, skill shuffle, alternating movement deal, alternating skill deal, then nextInt(2) for first player (0 Blue, 1 Red). Definitions are ordered by Appendix order before constructing decks; copies ordered by ordinal. On normal movement append the used card to the deck, shuffle, draw index 0. Refocus appends held cards in current hand order, shuffles once, draws two. Tests pin these operations to reproducible behavior.

## 4. Actions and phases

```java
sealed interface GameAction {
    record MovePiece(String movementCardId, int pieceId, Square to) implements GameAction {}
    record SkipMovement() implements GameAction {}
    record CastSkill(String skillInstanceId, SkillTargets targets) implements GameAction {}
    record ResolveForesight(long choiceId, List<String> orderedInstanceIds) implements GameAction {}
    record EndTurn() implements GameAction {}
    record Resign() implements GameAction {}
}
```

Actions do not contain a trusted actor, mana cost, derived capture, seed or result. Source square is resolved from stable piece ID at the request's expected revision. Lists carry player-selected order where required.

| Current phase | Accepted operation | Next phase |
| --- | --- | --- |
| MOVEMENT | Legal MovePiece or SkipMovement | SKILL, or FINISHED on elimination |
| MOVEMENT / SKILL / END | EndTurn | Increment completedTurns; FINISHED if at limit, else start opponent turn, grant mana once, increment startedTurns, enter MOVEMENT |
| SKILL | Ordinary legal CastSkill | END, or FINISHED on elimination |
| SKILL | Foresight with 2+ preview cards | CHOICE |
| SKILL | Foresight with 0/1 preview cards | Resolve deterministic ordering immediately, enter END |
| CHOICE | Valid ResolveForesight | END |
| Any ongoing phase | Resign (either player) / host forfeit | FINISHED |

Only the active player may move/skip/cast/resolve/end. Resign is allowed out of turn and during a pending choice. No separate SkipSkill API: EndTurn does it. UI keeps EndTurn available after a cast; a cast does not implicitly start the opponent's turn. A movement/skill elimination performs its mandatory disposal/replacement before marking FINISHED; no EndTurn follows. No mana or started-turn increment after draw/terminal result.

Example: before the 60th turn ends, startedTurns=60, completedTurns=59. A winning action ends with those counters; EndTurn without a win makes completedTurns=60 and a draw. Persistence uses both, never revision as a turn count.

## 5. Skill targets — complete mapping for all 24

Use nested records in sealed `SkillTargets`, with the following shapes. `Relocation` and `Direction` are shared core value types. Every list has a maximum of 2 entries except a Foresight ordering (maximum 5). Exactly-one and configured minimum/maximum requirements are enforced per effect.

```java
record Relocation(int pieceId, Square to) {}
enum Direction { NORTH, EAST, SOUTH, WEST } // canonical y-1, x+1, y+1, x-1
// SkillTargets variants:
record Relocate(int pieceId, Square to) implements SkillTargets {}
record Swap(int firstPieceId, int secondPieceId) implements SkillTargets {}
record Attack(int sourcePieceId, List<Integer> enemyIds) implements SkillTargets {}
record Enemy(int enemyId) implements SkillTargets {}
record Displace(int sourcePieceId, int enemyId) implements SkillTargets {}
record RelocateEnemy(int sourcePieceId, int enemyId, Square to) implements SkillTargets {}
record ShiftEnemies(List<Integer> enemyIds, Direction direction) implements SkillTargets {}
record MoveGroup(List<Relocation> moves) implements SkillTargets {}
record Rally(int anchorId, List<Relocation> moves) implements SkillTargets {}
record None() implements SkillTargets {}
```

| Skill | Target variant | Validation/resolution beyond common checks |
| --- | --- | --- |
| Sidestep | Relocate | Friendly; exact DISTANCE orthogonal, destination empty |
| Dash | Relocate | Friendly; exact DISTANCE orthogonal; every traversed square, including landing, empty |
| Blink | Relocate | Friendly; distinct empty destination at Manhattan distance 1..RANGE; ignore path |
| Exchange | Swap | Two different allies within RANGE; swap |
| Advance | MoveGroup | Exactly 2 different allies, each one square forward, destinations initially empty; simultaneous |
| Recall | Relocate | Friendly; empty destination on own starting row |
| Strike | Attack | One orthogonally adjacent enemy; source friendly stays still |
| Pierce | Attack | One enemy exact DISTANCE orthogonal, clear intermediate squares; source stays |
| Surround | Enemy | Enemy adjacent to at least REQUIRED_ALLIES; supporting allies derived from board |
| Sweep | Attack | 1..MAX_TARGETS distinct orthogonally adjacent enemies; remove simultaneously |
| Shove | Displace | Friendly source and adjacent enemy; derive destination one square directly away, empty/on-board |
| Pull | Displace | Enemy exactly 2 orthogonal squares from friendly source; middle empty; relocate into middle |
| Vault | Relocate | Friendly; exact DISTANCE orthogonal, empty destination, intervening pieces ignored |
| Flank | Relocate | Friendly; one-square diagonal; empty or enemy destination, eliminate if enemy |
| Transposition | Swap | First friendly, second enemy, within RANGE; swap without elimination |
| Gust | ShiftEnemies | 1..MAX_TARGETS distinct enemies, same one-square direction, initially empty destinations; simultaneous |
| Rally | Rally | Fixed friendly anchor; 1..MAX_TARGETS different other allies; listed one-step orthogonal moves sequentially reduce distance and land empty |
| Scatter | MoveGroup | 1..MAX_TARGETS different allies, one-square diagonals, initially empty distinct destinations; simultaneous |
| Crossfire | Enemy | Derive supporting friendly pieces on opposite sides in one row/column, each within RANGE with clear intervening squares |
| Chain Strike | Attack | First enemy orthogonally adjacent to friendly source; optional second adjacent to first's original square; listed order, distinct enemies |
| Mana Surge | None | Gain MANA_GAIN up to cap; legal even at cap |
| Refocus | None | Recycle both current movement cards and draw two, never reopen movement phase |
| Foresight | None | Begin private choice from top min(LOOKAHEAD, remaining) cards |
| Banish | RelocateEnemy | Friendly source; adjacent enemy; empty destination on enemy starting row |

A wrong target variant is INVALID_TARGETS, not an implicit conversion. Derived supporters for Surround/Crossfire require existence of a qualifying formation, not player-supplied friendly IDs. This avoids irrelevant target choices. Range parameters cannot change orthogonal/diagonal geometry or ownership requirements.

`SkillEffects` is an internal core dispatcher keyed by definition ID. Each effect uses shared relocation/elimination helpers where appropriate, but does not itself pay mana, draw, advance phase or increment revision: RulesEngine owns that common lifecycle exactly once. Effect code reads validated parameters, never duplicates baked costs/ranges.

## 6. Player view, targeting and public events

```java
record PlayerView(PlayerColor viewer, RulesConfig rules, long revision,
                  List<PlacedPiece> pieces, PlayerColor turn, TurnPhase phase,
                  int startedTurns, int completedTurns,
                  List<String> blueMovementHand, List<String> redMovementHand,
                  List<CardInstance> ownSkillHand, int opponentSkillCount,
                  int blueMana, int redMana, int reservedMana,
                  int movementDeckCount, int skillDeckCount,
                  List<CardInstance> spentSkills, ForesightView ownChoice,
                  GameOutcome outcome) {}
record ForesightView(long choiceId, String skillInstanceId, int reservedMana,
                     List<CardInstance> preview) {}
```

`ownChoice` is null unless the viewer owns a pending choice; `reservedMana` is the viewer's reservation (0 normally). Opponents see CHOICE and the active color, not the private choice ID, preview or ordering. Board view is a copied list of immutable occupied-square values, not Board. No references to GameState or pending host state can be reachable from PlayerView, events, exception text or logs. Do not dump full request/state `toString()` to logs.

Public event variants (nested in sealed `PublicEvent`):

- `PieceMoved(int pieceId, Square from, Square to)`; `PieceEliminated(int pieceId, Square at)`.
- `MovementPlayed(PlayerColor actor, String cardId)`.
- `SkillCommitted(PlayerColor actor, CardInstance skill)` emitted once on acceptance, including Foresight preview acceptance; played card identity is public from commitment. No draw identity event.
- `TurnStarted(PlayerColor actor, int startedTurns)`; `MatchEnded(GameOutcome outcome)`.

Full views contain final mana/decks/phase; no redundant delta events needed. Ordered movement/elimination events describe visual effects, never a sequence the client applies to reconstruct authoritative state. Simultaneous groups animate together based on the known skill; no render event exposes hidden cards. Never echo ResolveForesight payload to opponent. Server history stores only these sanitized events.

Shared UI legality contract, implemented in `Targeting` using only PlayerView:

```java
List<GameAction.MovePiece> legalMoves(PlayerView view);
SelectionOptions skillOptions(PlayerView view, String skillInstanceId, SelectionDraft draft);
record SelectionDraft(List<Integer> pieceIds, List<Square> destinations, Direction direction) {}
record SelectionOptions(List<Integer> selectablePieceIds, List<Square> selectableSquares,
                        List<Direction> selectableDirections, SkillTargets completeTargets,
                        boolean canSubmit, ActionError error) {}
```

`SelectionDraft.direction`, `completeTargets` and `error` may be null. An empty list is never null. Draft pieceIds has at most 3 entries (anchor/source plus up to two selections), destinations at most 2. A draft is local, not a wire DTO; it may be incomplete. Order follows the target table: source before enemies; anchor before moves; each relocation selects its piece then destination. For optional second targets, `canSubmit` may be true while more choices remain. `completeTargets` exists iff `canSubmit`; None effects complete from empty draft. Bad/stale drafts return empty options plus error rather than throwing. Inactive/wrong phase/insufficient mana reports appropriate error. Foresight preview choices use ownChoice, not skillOptions. Bots can build actions with the same helpers; they cannot query unknown skill instances. Server rechecks using full state.

## 7. Foresight commitment and atomicity

Host-only `PendingForesight` stores actor, skill instance, reserved cost, choiceId and ordered preview IDs. Use the revision of the accepted begin action as choiceId (unique within the match); rematches have a new matchId. Retain card in hand, mana in balance, and cards in deck while reserved. Reserved values are never available for another cast. The engine is locked to CHOICE anyway.

On begin: verify held card/cost/phase, snapshot preview, enter CHOICE and increment revision once, emitting SkillCommitted. With 0/1 cards, finish immediately in the same action instead. On resolve: actor/choiceId must match; submitted instance IDs must be an exact permutation without duplicates/omissions/extras. Reorder only the preview prefix, pay once, spend reserved card, draw replacement, clear pending and enter END. Increment revision once. No second SkillCommitted. Invalid resolution changes nothing. Resign/forfeit clears pending and ends game without resolving the abandoned cast; no extra draw/payment, reservation released. Match result is already terminal.

No cancellation after reveal. Reconnect restores the same pending choice. Disconnect pauses gameplay in MatchSession, not by changing core phase; it never resets choice or turn-start mana.

## 8. Wire contract, retries and compatibility

Java object stream creation order remains output+flush, then input+filter. The first application message sent by the server is existing `ErrorMessage("GRID_DUEL_PROTOCOL:2")` as a backward-decodable greeting; the new client consumes this exact marker internally, never displays it as an error. Client responds with new `ProtocolHello(int version)`, server returns `ProtocolReady(int version)` when version=2. Do not send login/register/reconnect/game commands before readiness. Use a 5-second handshake deadline, then close with a clear incompatibility message. Old clients can decode the greeting and see a version hint; a new server rejects their pre-handshake login with an existing ErrorMessage. A new client talking to an old server times out locally with an upgrade message. This avoids sending an unknown class to an old deserializer merely to negotiate a version.

New messages under `onitama.net`:

```java
record ActionRequest(String matchId, long sequence, long expectedRevision,
                     GameAction action) implements Message {}
record MatchSync(String matchId, String roomCode, PlayerView view,
                 boolean paused, long nextSequence) implements Message {}
record ActionReply(String matchId, long sequence, ActionReceipt receipt,
                   MatchSync current) implements Message {}
record ActionReceipt(boolean accepted, long revision, ActionError error) {}
record GameUpdate(MatchSync current, List<PublicEvent> events) implements Message {}
```

`ActionReceipt.error` is null iff accepted; receipt.revision is the resulting revision for acceptance or observed revision for rejection, which may differ from the current snapshot on a retry.

matchId is a host-generated UUID string (not the reusable room code); new on every rematch. A new match resets both sequence streams to 1 and discards old receipts; clients clear pending requests/selections when adopting a different matchId. nextSequence is recipient-specific. Actor comes from the current authenticated handler's MatchSession membership, never payload. Use MatchSync for start/reconnect and snapshots in replies/updates. Retain existing GameOver's ratings delivery but change its live result type to EndReason during protocol migration; terminal GameUpdate carries outcome. Legacy MatchStart/MoveApplied/MoveRejected/PassTurn/MoveRequest/ResignRequest become unused gameplay messages and are removed in the coordinated switch. Rematch requests and account/lobby messages remain, gated by handshake.

Per match and per player, sequence starts at 1. UI permits one unacknowledged gameplay request. Session keeps nextSequence and the last consumed request/receipt (bounded constant memory), including across reconnects:

1. Verify current handler, matchId and authenticated membership. A stale handler cannot send on behalf of a reconnected player.
2. sequence=nextSequence-1 and exactly identical payload: resend saved receipt with **current** permitted snapshot, never reapply or replay events. Different payload for that sequence: SEQUENCE_CONFLICT. Older sequences: OLD_SEQUENCE. Greater-than-next: SEQUENCE_GAP. These do not consume a sequence.
3. sequence=nextSequence: consume once, then check paused state, expectedRevision and engine legality. Cache accepted/rejected receipt and increment nextSequence even if rejected. Rejection does not mutate core state/revision/RNG. Session transport bookkeeping is separate from core mutation.
4. On acceptance, replace state on the match executor. Send GameUpdate to both recipients and ActionReply to requester. Event lists occur only in GameUpdate, so the acknowledgement does not animate twice. GUI ignores older views; an equal-revision MatchSync may still update paused/sequence metadata.
5. Reconnect preserves sequence history and sends current MatchSync. If a saved unacknowledged request exists, resend it with its original sequence/payload to discover its receipt; do not assign a new sequence to the same uncertain action. Older-than-last requests get OLD_SEQUENCE plus current snapshot, never execute. A restarted client with no pending request starts from supplied nextSequence.

For stale match/membership failures return existing ErrorMessage without another player's view. For sequence errors/current match use ActionReply with accepted=false and current revision/snapshot. Define `ActionError` as an enum with constants WRONG_PHASE, NOT_YOUR_TURN, CARD_NOT_HELD, INSUFFICIENT_MANA, INVALID_TARGETS, INVALID_CHOICE, GAME_FINISHED, MATCH_PAUSED, STALE_REVISION, OLD_SEQUENCE, SEQUENCE_GAP, SEQUENCE_CONFLICT. Structural malformed input is rejected/closed at the boundary with safe generic text. Error strings cannot explain contents of hidden zones.

Host forfeit updates revision and emits terminal GameUpdate once. While paused, ordinary actions reject; Resign remains permitted. Reconnect/paused metadata does not increment core revision or replay turn-start events. Match completion/persistence is guarded once on the executor.

Replace the broad core wildcard in `Message.WIRE_FILTER` with enumerated wire DTO/value types and required JDK serialization collection support. Specifically reject GameState, GameRandom, Board and PendingForesight. Preserve depth protection, add per-array length bounds for supported payloads; do not mistake cumulative stream byte/reference limits for per-message limits. Round-trip representative collection implementations and inspect recipient object graphs in tests.

## 9. Balance configuration contract

`onitama.config.RulesConfigLoader.load(Path overrideFile)` returns RulesConfig or throws `ConfigException` identifying key/value/reason; null path loads bundled defaults. It reads UTF-8 via Reader with `java.util.Properties`. Public defaults at `src/main/resources/config/game-balance.properties` are created in the config implementation task; Step 3 supplies [the exact template](examples/game-balance.properties). Optional override file overlays supplied keys; omitted keys inherit defaults. Unknown or duplicate keys and malformed supplied values fail, not silently fallback. Use a small Properties subclass overriding `put` to detect duplicates during load, reset duplicate detection separately for defaults/overrides, and reject duplicate keys in either file.

Validate before creating the match; never start a partially configured game. Canonical definition order is Appendix order, independent of Properties iteration order. Effect identity, geometry, ownership and phase are code-defined, not user scripts. Recognized keys and bounds:

| Key | Supported value |
| --- | --- |
| `config.schemaVersion` | Exactly 1 |
| `rules.version` | Exactly `grid-duel-v1` |
| `movement.handSize` | Exactly 2 (Refocus depends on this) |
| `skill.handSize` | 1..6, and twice this <= total enabled skill copies |
| `mana.initial` | 0..mana.cap |
| `mana.income` | 1..mana.cap |
| `mana.cap` | 1..20 |
| `turn.limit` | 2..500 completed player turns |
| `skill.<id>.cost` | 0..mana.cap |
| `skill.<id>.copies` | 0..4; default 1; 0 removes from deal, not definition catalog |
| `skill.<id>.name`, `.description`, `.artwork` | Nonblank, <=64 / 1024 / 256 chars respectively |
| `skill.<id>.distance` | 1..6; only Sidestep, Dash, Pierce, Vault |
| `skill.<id>.range` | 1..12 for Blink, Exchange, Transposition; 1..6 for Crossfire |
| `skill.<id>.maxTargets` | 1..2; only Sweep, Gust, Rally, Scatter |
| `skill.surround.requiredAllies` | 1..4 |
| `skill.mana-surge.manaGain` | 1..mana.cap |
| `skill.foresight.lookahead` | 1..5 |

Board/setup, 16 movement patterns, one copy per movement card, geometry, Advance's two allies, Chain Strike's optional second enemy, Pull's distance and one-step pushes/diagonals stay structural in this version. Unsupported parameter keys fail validation. A cost/gain above a lowered mana cap fails explicitly. All 24 definitions remain present even if copies=0. No configuration can cause an underfilled starting deal. Non-default copies can change the 24-card deck total and repeat an effect across different instances; defaults retain the adopted single-copy rules.

Description values are plain text templates with placeholders `{distance}`, `{range}`, `{maxTargets}`, `{requiredAllies}`, `{manaGain}`, `{lookahead}` only where the definition has that parameter. Loader rejects unknown/unresolved placeholders; `SkillText.render(SkillDefinition)` substitutes from the same snapshot used by effect code. Names/descriptions never supply executable rules. Swing must escape plain text before any HTML tooltip rendering. Artwork paths are classpath-relative with no leading slash or `..`, e.g. `assets/skills/sidestep.png`; missing art uses a drawn fallback and warning, not a game failure. Template paths are future asset-integration destinations, not currently existing files.

## 10. SQLite and result migration

Add columns to the existing matches table without rebuilding or reinterpreting historical rows:

```sql
ALTER TABLE matches ADD COLUMN rules_version TEXT NOT NULL DEFAULT 'legacy-onitama-v1';
ALTER TABLE matches ADD COLUMN started_turns INTEGER;
ALTER TABLE matches ADD COLUMN completed_turns INTEGER;
```

Database startup first ensures the existing base tables, inspects `PRAGMA table_info(matches)` through PreparedStatement under the existing connection monitor, then adds only absent columns in a transaction. Use constant SQL with PreparedStatement, never interpolate user input. Existing rows retain legacy version, original move_count and null new counters. Fresh databases receive the same schema through the same path. No migration runs midway through a match. Roll back/fail startup on migration error; do not erase tables.

For new grid-duel-v1 records, write started_turns and completed_turns from final state and set legacy NOT NULL `move_count=0` as a documented not-applicable sentinel. Readers must branch on rules_version and must never display that sentinel as the new game's move/turn count. Legacy rows continue displaying their original half-moves. Historical result reason remains a String so STONE/STREAM still load; new live EndReason does not accept them.

Evolve `server.MatchResult`: retain existing player/rating/room/result fields, replace moveCount with `String rulesVersion, int startedTurns, int completedTurns` for new matches. Evolve `MatchDao.MatchRecord`: retain old fields for reads and append `String rulesVersion, Integer startedTurns, Integer completedTurns` (nullable only for legacy rows). The migrated game writes only version 2 rules; no new legacy match producer is required. Update DAO consumers/tests in the same persistence task. A terminal action can leave completedTurns one below startedTurns; draws reach equality. Resignation/forfeit uses the current counters with no artificial EndTurn.

Maintain one atomic transaction for match insert + both rating/W/L updates; keep K=32 and rollback behavior. No board-state/replay persistence or server-restart reconnect recovery is introduced. Reconnect continues to use in-memory session state.

## 11. Implementation handoffs and required proof

| Next task | Files/boundary | Acceptance before dependent work |
| --- | --- | --- |
| Core state + movement lifecycle | core and affected core tests | 7×7 setup, phases/counters, deterministic recycling, immutable transitions, private projection, no obsolete win/transit assumptions |
| Config loader | config package, bundled properties, focused tests | Exact template validates; bad keys/costs/duplicates fail; frozen snapshots and rendered text agree |
| Skill framework + examples | RulesEngine / SkillEffects / Targeting | Sidestep, Strike, Exchange demonstrate action/target/shared lifecycle; invalid input is atomic |
| Remaining effects | SkillEffects and per-effect tests | Use target table; all 24 rules, both colors, edge cases; no independent payment/draw logic |
| Server/protocol and persistence | net/server/db with coordinated client-state adapter | Handshake, retry/revision, private object graphs, reconnect CHOICE, one match transaction, legacy DB preserved |
| UI | ui + state against PlayerView | Canonical targeting, one in-flight request, private preview, live text, phase controls, no hidden host access |
| Bots | PracticeBot / load bots and host adapter | Legal games with only player information and separate policy randomness |

Required edge-case evidence: illegal second target leaves first untouched and RNG unchanged; end turn then retransmit does not grant mana twice; repeated Foresight begin cannot preview twice; resolution with an invented/duplicate ID rejects; reconnect restores pending reservation and expected sequence; opponent view contains no hidden card identities even after serialization; 60th-turn elimination beats draw; configured duplicate skill instances dispose independently; existing DB history loads unchanged. Tests run on localhost/temp DBs with `mvn test`; clean verify for final release.

No runtime work is claimed complete by this document. These contracts now let later tasks use one vocabulary and one target format. Any necessary contract change must update this document and its dependent task briefs together.
