# Grid Duel — Authoritative specification

**Status (2026-10-15): gameplay migration complete; Step 5 presentation integrated.**
This is the source of truth for gameplay, technical constraints, protocol and tests.
The live engine is `onitama.core.duel` across server, client, practice, bots and demo.
[Step 3 contracts](docs/step-03-engine-contracts.md) supplement these rules with
implemented APIs, configuration and persistence contracts. Obsolete 5×5 engine
classes and unused legacy mechanics have been removed.

## 0. Agent rules and migration policy

- Java 17, single-module Maven. Dependencies: `org.xerial:sqlite-jdbc` and
  test-only `org.junit.jupiter:junit-jupiter`. Swing UI, raw TCP sockets, Java
  object serialization. No Spring, JavaFX, Netty, Gson/Jackson, Lombok, game
  engine, animation library, cloud SDK, or cloud service.
- Everything runs on localhost. Keep the current accounts, lobby, Elo,
  leaderboard, practice, rematch, and reconnect capabilities.
- Short explainable methods, public API Javadoc, meaningful comments, logging
  via `java.util.logging`. No dead code or invented validation results.
- Implement one bounded migration task at a time. This documentation change
  does not authorize rewriting the entire engine immediately.
- Keep `mvn test` green. When implementing an intentionally changed rule, replace
  obsolete assertions with meaningful new-rule coverage in the same change.
  Never remove or disable tests solely to conceal a failure. Unrelated security,
  concurrency, serialization, and persistence coverage remains mandatory.
- The 125-test baseline is historical, not a fixed expected count. Report actual
  counts. Delegated implementations must use the state/action/view interfaces
  and exact message names in Step 3; do not invent competing contracts.

## 1. Product scope

Two players control six identical pieces each on a plain 7×7 board. Reusable
movement cards establish positioning; 24 distinct single-use skills and mana
create tactical variety. Eliminate all enemy pieces to win; 60 completed player
turns without a winner is a draw. No points, objectives, terrain, random battle
boards, Masters, temples, HP, armor, reactions, or ongoing statuses in version 1.

Server: headless authoritative matches, accounts, five-character room codes,
multiple matches, reconnect grace, match records and Elo. Client: Swing
login/register, lobby, game, leaderboard, and practice against a bot. Headless
bots remain a load-test tool with CSV output. SQLite stores users and matches.

`mvn package` builds `target/onitama.jar`; `onitama.Main` dispatches `server`,
`client`, `bots`, or `demo`. Existing shell/batch launch scripts remain supported.
Out of scope: web/mobile clients, HTTP/WebSocket, spectators, replay
recording, cloud automation, and matchmaking beyond room codes.

### Played-card history (2026-10-16)

The left game panel defaults to a local timeline of publicly played movement and
committed skill cards from either player, showing owner, turn and artwork newest
first. Record only accepted public events; never infer unseen opponent skills or
private Foresight previews. Duplicate/stale updates do not add entries. Same-match
reconnect preserves locally observed plays; new matches/rematches clear them.
The separate Card details tab is removed; enlarged cards and live tooltips provide
card text. This applies in practice too.

### In-match chat (2026-10-16)

Live multiplayer matches support ephemeral text chat between their two connected
players. ChatRequest(matchId, text) is client-to-server; ChatMessage(matchId,
sender, text) is server-to-client, with identity derived from the authenticated
handler. Both records use serialVersionUID=1L and the version-2 wire allowlist;
update client and server together. Chat does not consume gameplay sequences or
change revision, turn, phase, RNG or reconnect deadlines. It is allowed during
private choices but not while paused or finished. Reject stale matches, blank
text, more than 280 UTF-16 code units, control/format/line-separator characters,
and sends less than 500 ms apart per player. Accepted text is stripped and echoed
to both players. Client history keeps the last 200 accepted messages; same-match
reconnect retains that local history, while a fresh match/rematch or leaving a
finished match clears it. No storage or replay of missed chat; practice has no chat.

## 2. Gameplay rules

### 2.1 Setup

The following defaults define the first playable version.

- Coordinates: `x=0..6`, `y=0..6`. Blue begins on `y=0`; Red begins on `y=6`.
- Place Blue pieces at `(0,0), (1,0), (2,0), (4,0), (5,0), (6,0)`.
- Place Red pieces at `(0,6), (1,6), (2,6), (4,6), (5,6), (6,6)`.
- Leaving the middle starting square empty gives a symmetric six-piece formation. Every piece has a stable identity, but identical abilities.
- There are no temples, special pieces, HP, armor, upgrades, or passive statuses.
- Use the existing 16 movement-card patterns, one copy each. Preserve their offsets; enlarge the board, not the patterns. Native offsets are Blue-relative; Red negates both components.
- Shuffle each shared deck separately. Deal alternately Blue then Red: two movement cards each and three skill cards each.
- This leaves 12 movement cards and 18 skill cards in their respective decks.
- Choose the starting player randomly, independently of card stamps. Use seeded randomness for repeatable tests.
- Both players begin with 2 mana. At their first turn start, they gain 1, so each first acts with 3 mana.

### 2.2 Turn sequence

A player turn contains these ordered phases:

1. **Start:** gain 1 mana, capped at 5. This happens exactly once per turn, including after reconnecting.
2. **Movement:** play at most one movement card, or skip movement.
3. **Skill:** play at most one skill card, or skip the skill.
4. **End:** finish the turn, increase the completed-turn count, then give the opponent the turn unless the match has ended.

Once movement is played or skipped, the player cannot return to that phase. A skill cannot be played before movement has been played or skipped. A replacement skill cannot be played again in the same turn.

An End Turn action skips any remaining optional phases. A player may end a turn even when legal actions exist. If no movement is legal, they may still cast a legal skill. Doing nothing does not cycle or draw cards. The turn limit prevents indefinite passing.

### 2.3 Movement and captures

- Select a movement card in your hand, one friendly piece, and a destination matching one of the card's oriented offsets.
- Pieces jump over intervening pieces. The destination must be on the board and cannot contain a friendly piece.
- Landing on an enemy eliminates that enemy and occupies its square.
- After successful movement, remove the used card from the hand, insert it into the shared movement deck, shuffle the deck, then draw one replacement.
- The played card may be drawn again immediately. This follows the **return → shuffle → draw** ordering and must not be silently changed to a discard-pile system.
- Each player keeps two movement cards. Skipping movement draws nothing.
- Skill-generated movement does not consume or replace a movement card unless the skill explicitly says so; Refocus is such an exception.

### 2.4 Skill use, mana, and replacement draws

- A cast requires the player's skill phase, the card in their hand, sufficient mana, and all legal target selections.
- Validate the complete action before committing it. Illegal casts change no board state, mana, hand, deck, turn phase, or random-generator state.
- On success: pay the mana cost, resolve the entire effect, move the used card to the public spent pile, then draw one replacement if the skill deck is nonempty.
- Spent skills never return to play. With one copy per skill, each effect can be cast at most once across the whole match.
- When the shared skill deck is empty, casts still work but hands shrink. Having no skills does not cause a loss.
- No automatic skill draw occurs at turn start or when movement is performed.
- Mana persists between turns. Captures grant no mana. There are no refunds after a successful cast.
- Mana Surge costs 0, obeys the cap, and consumes the one skill opportunity. It can be played at the cap even though its mana gain is zero.
- No reactions, stacks, opponent-turn casts, or ongoing status effects in the first set.

### 2.5 Targeting conventions and special cases

- Distance means Manhattan distance: `abs(dx) + abs(dy)`.
- Adjacent means horizontally or vertically adjacent unless a card explicitly says diagonal.
- Empty means unoccupied at the point the rule checks it; all destinations must be on the board.
- Any two-piece swap requires two distinct pieces. Swaps exchange positions without elimination or checking intermediate squares.
- For “up to 2,” choose one or two distinct pieces/targets, never zero. Destinations must be distinct; no skill leaves two pieces on one square.
- Skills do not capture by landing on an occupied square unless their rule explicitly allows it. Flank explicitly does.
- “Starting row” always means the initial edge for that color, regardless of current piece locations.
- Advance moves both chosen allies one row toward the enemy starting edge into squares empty before the cast; resolve together.
- Gust and Scatter resolve together, into squares empty before the cast. “Both destinations” means every selected destination even when selecting only one target.
- Rally chooses a stationary friendly anchor and one or two other allies. The player supplies their resolution order; each move reduces distance to the anchor and lands on a square empty at that step. Validate that entire sequence on a temporary state first.
- Sweep removes its selected enemies together. Chain Strike's second target is adjacent to the first target's original square and must be a different enemy.
- Crossfire requires two allies on opposite sides of the enemy in the same row or column, each at most distance 3 from the target, with clear intervening squares.
- Refocus returns both of the caster's movement cards to the shared movement deck, shuffles once, then draws two. It works even after a normal movement this turn and does not reopen the movement phase.
- Foresight privately reveals up to three top skill cards to the caster, accepts an ordering of exactly those cards, and puts them back before the normal replacement draw. Thus the first ordered card becomes the replacement. Opponents learn neither the preview nor the chosen order. An empty-deck cast is allowed and draws nothing.

### 2.6 Winning and ending a match

- After a complete movement or skill resolves, zero opposing pieces means an immediate win. Do not wait for End Turn.
- Resolve mandatory card bookkeeping as part of that successful action before marking it complete; no further player actions follow a win.
- If an elimination win happens on the 60th player turn, that win takes precedence over the turn-limit draw.
- Otherwise, End Turn on completed turn 60 ends the match as a draw. This means 30 turns each in a normal alternating match, not 60 turns each.
- Remaining-piece count and remaining mana do not break a draw.
- Resignation loses immediately. Keep the existing 60-second reconnect grace and forfeit behavior for the online game.
- No objective points, kill points, temple victory, or Master-capture victory in this version.

### 2.7 Public and private information

Public: board, turn and phase, both mana totals, both movement hands, skill-hand counts, remaining deck counts, spent skills, completed-turn count, and match result.

Private to a player: their skill hand and any active Foresight preview. Undrawn deck identities/order and the match random seed stay server-private during play.

The existing full-state network broadcast cannot be reused unchanged under these rules. The server retains the authoritative state and sends each player only their permitted view. Reconnect must restore the same phase, hand, and pending Foresight choice without granting extra mana or another draw. Practice bots receive only the information their player is allowed to know.


## 3. Architecture requirements

- `onitama.core` stays pure: no file I/O, sockets, or Swing. Server, practice, and
  client-visible legality checks share rule definitions. Keep mutations confined
  to engine-controlled paths; do not let UI code mutate received views.
- The server owns both decks, all hands, RNG state, phase, mana, pending choices,
  and result. Validate all actions against that state. Rejections mutate nothing,
  including random state. Multi-target skills validate completely before commit.
- Send a full **player-specific view**, not full authoritative GameState, after
  each accepted action/phase transition and on match start/reconnect. Include
  public state and only that recipient's permitted private data. Full snapshots
  remain sufficient; delta synchronization is not required.
- Accept loop → handler pool → one single-thread executor per `MatchSession`.
  Match state is thread-confined; no locks inside game logic. Shared registries
  use `ConcurrentHashMap`; each client socket retains a single writer. Preserve
  clean shutdown behavior.
- Java serialization: sealed `Message`, final message records, explicit
  `serialVersionUID=1L`; flush and reset for every write so references cannot
  deliver stale state. Maintain and test the deserialization allowlist for all
  new payloads; reject unknown/foreign objects safely.
- Ping every 30 seconds; socket timeout 90 seconds. Reconnect token comes from
  LoginResponse. On disconnect, park the match and notify the opponent with
  the 60-second grace. Restore the same permitted view and pending choice;
  never repeat turn-start mana or a draw. Grace expiry forfeits.
- Load and validate `.properties` balance definitions outside core, then inject
  an immutable rules snapshot at match creation. Edits affect future matches.
  Clients display the match's definitions, never independently selected local
  balance values. Only public definitions may be distributed to clients.
- Seeded randomness makes tests reproducible. Never send the live match seed,
  RNG state, or undrawn deck sequence to clients or practice-bot policy code.

## 4. Protocol requirements (target, not implemented APIs)

Keep login/registration, lobby listing/create/join, leaderboard, ping/pong,
rematch, error reporting, and token reconnect. Replace legacy gameplay payloads
when their migration task lands; `PassTurn(cardIdToDiscard)` and broadcasts of
full `GameState` are legacy behavior, not valid redesign contracts.

| Operation | Direction | Required semantics |
| --- | --- | --- |
| Match start | Server → each player | Color, room, public rules snapshot, recipient-specific initial view |
| Movement | Client → server | Selected held card, friendly piece/from square, destination; only movement phase |
| Skip movement | Client → server | Enter skill phase without drawing or spending a movement card |
| Cast skill | Client → server | Held skill and complete typed targets/selections; verify phase, mana and legality |
| Resolve private choice | Client → server | Submit exactly a permutation of the active Foresight preview |
| End turn | Client → server | Skip remaining optional phases and advance once; unavailable during a pending choice |
| Accepted action | Server → each player | Updated permitted view and public action details; never opponent draw identities |
| Rejected action | Server → requester | Useful error; no state mutation or disclosure of hidden information |
| Game over | Server → each player | Winner or draw, ELIMINATION/FORFEIT/DRAW, updated ratings |
| Reconnect | Client → server → recipient | Token verification; restore view and phase/private pending choice, not a fresh turn |

Step 3 §8 defines match IDs, per-player request sequences, revision checks,
receipts and the protocol handshake so stale/duplicate requests cannot spend
cards twice or advance two turns. Broadcast views share
public state, but their private payloads intentionally differ. Update both peers,
serialization tests and filters together; mixed legacy/new gameplay peers are
not supported and must fail clearly rather than silently deserialize incompatible
state.

Foresight requires a two-stage interaction: validate the cast before revealing
anything, then enter a server-owned pending choice. Once a preview is revealed,
the cast cannot be cancelled and no other gameplay action can run until its
ordering is completed (resignation and disconnect handling still apply). Reserve
the card and required mana; finish payment, reorder, disposal and replacement
exactly once when a valid ordering arrives. Invalid order submissions preserve
the pending choice. Reconnect restores it. With zero cards, resolve immediately
without a choice. A final one-card ordering is deterministic. This commitment
prevents repeatedly previewing the deck for free.

## 5. Database (SQLite via JDBC)

Single shared `Connection` guarded by a monitor (deliberate: SQLite serializes
writes anyway; heavy concurrent write load is not a goal). Schema created
idempotently at server start:

```sql
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,   -- PBKDF2WithHmacSHA256, iterations:saltB64:hashB64
  elo INTEGER NOT NULL DEFAULT 1000,
  wins INTEGER NOT NULL DEFAULT 0,
  losses INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS matches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  blue_user_id INTEGER NOT NULL REFERENCES users(id),
  red_user_id INTEGER NOT NULL REFERENCES users(id),
  winner_user_id INTEGER REFERENCES users(id),  -- NULL on draw
  end_reason TEXT NOT NULL,                     -- new: ELIMINATION|FORFEIT|DRAW; legacy: STONE|STREAM
  move_count INTEGER NOT NULL,
  elo_blue_after INTEGER NOT NULL,
  elo_red_after INTEGER NOT NULL,
  played_at TEXT NOT NULL
);
```

- **All** SQL through `PreparedStatement` (injection structurally impossible).
- Recording a finished match (insert + both Elo/W/L updates) is **one
  transaction** with rollback on failure.
- **Elo:** standard, K=32, zero-sum, pure static function in `core`, unit-tested.
- Passwords never stored/logged in plaintext (`javax.crypto`, JDK-built-in).


Migration requirement: preserve historical STONE/STREAM rows. New matches use
ELIMINATION, FORFEIT, or DRAW (resignation is FORFEIT). Do not reinterpret legacy
records. The existing `move_count` field remains a legacy field; Step 3 §10
defines the additive `rules_version`, `started_turns`, `completed_turns` migration
and the version-aware `move_count=0` sentinel for new rows. A winning action may end a turn before End Turn; persist the
number of turns started, including that terminal turn, separately from the
completed-turn counter used for the draw check. These documented schema/API changes
are not yet implemented. Preserve transaction and rating behavior.

## 6. Swing client and artwork

- Game screen: enlarged 7×7 board; both public movement hands; enlarged own skill
  cards with live descriptions; concealed opponent skills; mana and turn/phase;
  played-card history, skip movement, End Turn, resign and rematch. Use the same
  selected animated background as the menu, warm translucent surfaces and floating
  ivory/gold actions, with the previous prominent pill design for Skip Movement,
  End Turn and Cast Skill. Remove the Card details tab, VS banner, Your hand label and
  bottom instruction strip. Keep context guidance on tooltips and board highlights.
  A single unlabeled decorative pile anchors both kinds of draw animation; hide
  deck titles/counts. The two authoritative decks, counts and recycling rules remain
  distinct in engine/protocol. Never reveal concealed opponent skill faces.

- Own cards support clicking or a left-button upward drag and release to select.
  Settings offers Click or slide up (default) and Slide up only; persist the choice.
  In slide-only mode clicks do not select. Short drags and Esc cancellation return
  the card smoothly. Keep
  selected cards raised during targeting. Only accepted actions launch card-play
  flights to own-left/opponent-right presentation areas beside the board. Block
  conflicting input during these flights, retain hand reflow, and reset visual
  state on lifecycle changes. Areas never cover board squares and do not change
  movement exchange or permanent skill consumption. Opponent skill faces may
  appear only after their public committed event.
- Draw from the player's side; Red flips canonical coordinates. Movement
  diagrams come from offset data, not painted rules. A compact 5×5 offset
  diagram is still valid for the existing patterns; it is not the game board.
- Movement selection highlights legal landings. Skill selection follows shared
  target constraints, supports ordered/multiple targets, and allows cancellation
  before submission. Foresight's committed private choice cannot be cancelled.
- Read only the player view. Local highlighting can use visible information;
  server validation remains final. Never require hidden decks for UI legality.
- Socket reader parses, then `SwingUtilities.invokeLater` updates ClientModel.
  Panels observe ClientModelListener; no socket access or blocking I/O on EDT.
  Animations use Swing timers and cannot mutate authoritative state or delay
  network handling.
- Retain parchment/sumi-e visual style and asset fallbacks. Skill art uses nature,
  objects and structures, avoiding humans/faces. The existing 24 full-face PNGs
  are design assets with baked text, not live balance data. Render card names,
  costs and descriptions from the match definitions over separate artwork.

## 7. Design and package boundaries

Preserve `core`, `net`, `server`, `db`, `client/state`, `client/ui`, and
`client/bot`. Keep Observer for ClientModel/panels, the sealed message handler
registry, DAO interfaces/composition, and Main dispatch. Add only abstractions
needed for skill effects, target validation, rules snapshots and restricted
player views. Reuse shared effect operations where behavior matches; do not
build a general scripting engine for this finite card set.

## 8. Test inventory and migration acceptance

The original 125-test baseline covered the legacy game. The table below records
the migration requirements; current validation is recorded in the handoff reports.

| Existing area | Required treatment during implementation |
| --- | --- |
| CardPatternTest (UT01) | Preserve all 16 Appendix offsets and Red rotation; adapt board-boundary expectations only where necessary |
| RulesEngineTest (UT02) | Preserve jumping/friendly-landing/capture checks; expand to 7×7, phase and rejection atomicity |
| WinConditionTest (UT03) | Replace Master/temple assertions with elimination, 60-turn draw and win-before-draw precedence |
| CardCycleTest (UT04) | Replace transit and forced-pass tests with return/shuffle/draw and optional skip/end semantics |
| CardDeckTest (UT05) | Replace 2/2/1 deal and stamp start with two public movement hands, shared decks, three private skills each and seeded random start |
| EloTest (UT06), UserDaoTest (UT07), MatchDaoTest (UT08) | Preserve security, math, rollback and account behavior; cover new result/version/turn fields and legacy records when migrated |
| MessageSerializationTest (UT09), MessageWireFilterTest | Round-trip new views/actions; allowed payloads work, foreign classes fail; verify no hidden data in serialized opponent views |
| ServerClientIntegrationTest (IT01) | Full two-client elimination game; matching public state, distinct private views; stale/duplicate and illegal casts rejected |
| DisconnectReconnectTest (IT02) | Preserve grace/forfeit; restore phase, hand and pending Foresight without extra mana/draw |
| BotSmokeTest (LT01), PracticeBotTest | Complete games with legal movement/skills using permitted information; retain load-test and practice coverage |

Additional coverage required with the implementing tasks:

- Setup: six identical pieces per side at specified squares, 12/18 undealt
  movement/skill cards, first active player's mana 3 and opponent's mana 2.
- Card conservation: movement cards stay in hands/deck; skills stay in
  hands/deck/spent pile, no duplicates or replenishment after exhaustion.
- Every one of 24 effects: legal resolution, illegal targets and relevant
  boundaries, both colors, correct payment/draw and phase consumption.
- Simultaneous effects versus Rally's ordered movement; all-or-nothing rejection,
  including unchanged RNG. Skills cannot eliminate by pushing off-board.
- Mana cap, income exactly once, zero-cost cast, no mana from kills; no second
  skill from a replacement draw and no movement-phase return.
- Foresight valid permutations, malformed/duplicate choices, non-cancellable
  preview, privacy and reconnect; Refocus's two-card reshuffle; empty skill deck.
- Configuration validation, stable per-match snapshots, and UI text matching
  those definitions. Baseline balance defaults in Appendix B load correctly.
- Elimination after movement or skill, terminal action bookkeeping, 60th-turn
  win precedence, optional all-pass draw, resignation and grace forfeits.

Tests use ephemeral localhost ports, temporary DBs/directories and no external
running server. Run `mvn test` after changes; clean release verification uses
`mvn clean verify`. Report actual test counts and limitations.

## 9. File I/O and balance configuration

Retain `client.properties` settings (host/port, window, piece style), server CLI
overrides and `java.util.logging`. Future balance `.properties` loader must
validate IDs, effect parameters, costs, copy counts and deck/hand constraints
before creating a match; errors must identify the bad setting. Do not silently
fall back from malformed supplied values. Never parse files inside core.

First-version structural rules remain 7×7 with the specified six-piece setup.
Balance defaults: two movement cards, three skills initially, starting mana 2,
income 1, cap 5, 60-turn limit, one copy of each skill. Costs/ranges/counts must be
editable within supported effect constraints. Typed fields, file layout and
validation boundaries are defined in Step 3 §9 and its complete example; they
do not permit inventing new mechanics. Artwork manifest JSON requires no new runtime JSON dependency.

## 10. Release verification (pending implementation)

- [ ] `mvn clean verify` passes and the shaded jar runs.
- [ ] Two localhost clients play the new 7×7 rules through lobby, full game,
      result and rematch; all 24 skills can be exercised.
- [ ] Practice and load bots use legal actions without hidden information.
- [ ] Private hands/deck order/preview never reach the wrong client's object
      graph, logs, action history or error payloads.
- [ ] Reconnect restores pending choices; malformed and duplicate requests do
      not corrupt state; clean shutdown and persistence rollback still work.
- [ ] Live card text matches match configuration; no transit/temple UI remains.

## Appendix — the 16 movement cards (authoritative offsets)

`(dx, dy)` from the moving piece; `+x` = right, `+y` = forward (Blue's native
orientation); rotate 180° for the other player. `UT01` encodes exactly this.

| Card | Offsets (dx, dy) |
|---|---|
| Tiger | (0,+2), (0,−1) |
| Dragon | (−2,+1), (+2,+1), (−1,−1), (+1,−1) |
| Frog | (−2,0), (−1,+1), (+1,−1) |
| Rabbit | (+2,0), (+1,+1), (−1,−1) |
| Crab | (0,+1), (−2,0), (+2,0) |
| Elephant | (−1,0), (+1,0), (−1,+1), (+1,+1) |
| Goose | (−1,0), (+1,0), (−1,+1), (+1,−1) |
| Rooster | (−1,0), (+1,0), (−1,−1), (+1,+1) |
| Monkey | (−1,+1), (+1,+1), (−1,−1), (+1,−1) |
| Mantis | (0,−1), (−1,+1), (+1,+1) |
| Horse | (0,+1), (0,−1), (−1,0) |
| Ox | (0,+1), (0,−1), (+1,0) |
| Crane | (0,+1), (−1,−1), (+1,−1) |
| Boar | (0,+1), (−1,0), (+1,0) |
| Eel | (−1,+1), (−1,−1), (+1,0) |
| Cobra | (+1,+1), (+1,−1), (−1,0) |


## Appendix B — Initial skill definitions

These are the authoritative first-playtest defaults, clarified by §2.5. Category
labels describe presentation, not turn phases: a MOVEMENT-category skill still
uses the skill phase. All 24 skills have one copy. Costs are not claimed to be
balanced; Dash/Vault at equal cost is a known playtest concern. Exchange swaps
identical allies and therefore changes identity rather than tactical position;
its practical benefit is currently only cycling a skill. Keep this adopted
effect for now and reassess it during the balance pass. Later balance
changes must update definitions and tests, not merely PNG text. The asset
manifest at `output/skill-cards-v1/cards.json` mirrors this initial set; it is not
a loaded runtime config and cannot override this specification.

| ID | Name | Mana | Category | Effect |
| --- | --- | --- | --- | --- |
| `sidestep` | SIDESTEP | 1 | MOVEMENT | Move one friendly piece 1 square horizontally or vertically to an empty square. |
| `dash` | DASH | 2 | MOVEMENT | Move one friendly piece exactly 2 squares horizontally or vertically. Both squares must be empty. |
| `blink` | BLINK | 3 | MOVEMENT | Teleport one friendly piece to an empty square within distance 3. Ignore pieces between. |
| `exchange` | EXCHANGE | 2 | MOVEMENT | Swap two friendly pieces within distance 3 of each other. |
| `advance` | ADVANCE | 3 | MOVEMENT | Move two different friendly pieces 1 square each toward the enemy starting edge. Both destinations must be empty. |
| `recall` | RECALL | 1 | MOVEMENT | Teleport one friendly piece to any empty square on your starting row. |
| `strike` | STRIKE | 3 | ATTACK | Choose a friendly piece. Eliminate 1 enemy horizontally or vertically adjacent to it. Your piece stays still. |
| `pierce` | PIERCE | 4 | ATTACK | Choose a friendly piece. Eliminate an enemy exactly 2 squares away horizontally or vertically. The square between must be empty. |
| `surround` | SURROUND | 2 | ATTACK | Eliminate 1 enemy horizontally or vertically adjacent to at least two friendly pieces. |
| `sweep` | SWEEP | 5 | ATTACK | Choose a friendly piece. Eliminate up to 2 enemies horizontally or vertically adjacent to it. |
| `shove` | SHOVE | 1 | CONTROL | Choose a friendly piece. Push an adjacent enemy 1 square directly away. The destination must be empty and on the board. |
| `pull` | PULL | 1 | CONTROL | Choose a friendly piece. Pull an enemy exactly 2 squares away horizontally or vertically into the empty square between. |
| `vault` | VAULT | 2 | MOVEMENT | Move one friendly piece exactly 2 squares horizontally or vertically to an empty square. You may jump over a piece. |
| `flank` | FLANK | 3 | ATTACK | Move one friendly piece 1 square diagonally. The destination must be empty or contain an enemy. Eliminate that enemy if present. |
| `transposition` | TRANSPOSITION | 4 | CONTROL | Swap one friendly piece with one enemy within distance 2. Neither piece is eliminated. |
| `gust` | GUST | 3 | CONTROL | Move up to 2 enemies 1 square each in the same horizontal or vertical direction. Both destinations must be empty before the skill. |
| `rally` | RALLY | 3 | MOVEMENT | Choose a friendly anchor. Move up to 2 other friendly pieces 1 square each horizontally or vertically toward it. Each move must reduce distance and land empty. |
| `scatter` | SCATTER | 3 | MOVEMENT | Move up to 2 different friendly pieces 1 square diagonally each. Both destinations must be empty before the skill. |
| `crossfire` | CROSSFIRE | 4 | ATTACK | Eliminate an enemy between two friendly pieces in one row or column. Each ally must be within distance 3 with no pieces between it and the target. |
| `chain-strike` | CHAIN STRIKE | 5 | ATTACK | Choose a friendly piece. Eliminate an adjacent enemy, then optionally 1 enemy adjacent to the first target. Adjacency is horizontal or vertical. |
| `mana-surge` | MANA SURGE | 0 | RESOURCE | Gain 2 mana, up to the mana cap. This uses your skill for the turn. |
| `refocus` | REFOCUS | 1 | RESOURCE | Return both movement cards to the movement deck. Shuffle, then draw 2 movement cards. |
| `foresight` | FORESIGHT | 1 | RESOURCE | Look at the top 3 skill cards, or all remaining cards if fewer. Return them to the top in any order before drawing your replacement. |
| `banish` | BANISH | 4 | CONTROL | Choose a friendly piece. Send an adjacent enemy to an empty square on that enemy's starting row. Adjacency is horizontal or vertical. |
