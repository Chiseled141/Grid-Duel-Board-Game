# ONITAMA ONLINE — Demo Brief (lean)

**Status:** complete, working demo (2026-10-04). All milestones (M1–M8), the M9
visual redesign and the single-player practice mode are implemented; `mvn clean
verify` = 123 tests green. This file is the compact spec of the technical
requirements the project must keep satisfying and how to run it. It replaced the
long build brief deliberately (original recoverable from git history) — no
course, report, or deployment material.

## 0. Ground rules for agents

1. **Java 17, Maven, nothing else.** Allowed dependencies: `org.xerial:sqlite-jdbc`
   and `org.junit.jupiter:junit-jupiter`. GUI is **Swing** (JDK-built-in);
   networking is **raw TCP** (`ServerSocket`/`Socket`). No Spring, JavaFX, Netty,
   Gson/Jackson, Lombok, game engines, or cloud SDKs.
2. **No cloud, ever.** Everything runs on localhost out of the box; the server is
   a plain headless `java -jar` process.
3. Clarity over cleverness — every line must be explainable: short methods,
   Javadoc on public classes/methods (1–3 sentences), comments explain *why*.
   Keep ~30–50 classes; no filler.
4. No dead code, no commented-out code, no `System.out.println` debugging (use
   `java.util.logging`), no fabricated numbers or results anywhere.
5. After every change the project must compile and `mvn test` must pass; commit
   per logical change with a clear message.
6. Where a design detail is open, pick the simplest option and record the why in
   the class Javadoc or the commit message.

## 1. What the project is

Two players play Onitama through a central **authoritative** Java server.

- **Server** (headless): accounts, open lobbies (5-char room codes), live matches,
  disconnection handling (60 s reconnect grace), Elo ratings, match history.
  Multiple simultaneous matches.
- **Client** (Swing): login/register, lobby, game board with the five movement
  cards, move history, game-over/rematch flow, leaderboard — plus
  **single-player practice** against a built-in bot (rookie / senior / legend).
- **Bots** (headless): load-test fleet playing random legal games; writes CSV
  stats to `results/`.
- **Database** (SQLite via JDBC): users (salted password hashes + Elo), matches.

Run: `mvn package` → `target/onitama.jar`. `Main` dispatches on `args[0]`:
`server` / `client` / `bots` / `demo`. Scripts: `scripts/run-server.sh [port]
[db]`, `run-client.sh`, `run-bots.sh [bots] [games]` (`.bat` twins exist).

Out of scope (do NOT add): web/mobile clients, WebSocket/HTTP, matchmaking beyond
room codes, spectators, in-game chat, replay recording, cloud automation.

## 2. Game rules (the engine implements exactly this)

- 5×5 grid; `x` = column 0–4, `y` = row 0–4. **Blue home row y=0, Red y=4**;
  "forward" = toward the opponent's home row. Each side: **1 Master + 4 Students**
  on the home row; Master at (2,0)/(2,4) — that square is the **Temple Arch**.
  All pieces move identically; type matters only for win conditions.
- **16 movement cards** (Appendix). Each shows destination offsets `(dx, dy)`
  relative to the moving piece, `+x` = right, `+y` = forward, in the card's
  native orientation. **Rotation rule:** when the non-native player uses the
  card, rotate 180°: `(dx,dy) → (−dx,−dy)`. Blue uses stored offsets, Red uses
  rotated (be consistent; UT01 guards it).
- Pieces **jump** (no pathing/blocked lines); may not land on a friendly piece;
  landing on an enemy **captures** it.
- **Deal:** shuffle the 16-card deck, deal 2/2/1 (hand/hand/**transit**). All
  information is public. Each card has a fixed **stamp color** (8 RED / 8 BLUE);
  the transit card's stamp picks the first player (implemented as a fixed map in
  `CardDeck` — documented deviation: stamps are not publicly standardized).
- **Turn:** move one piece to a legal destination of one of your 2 hand cards —
  passing is illegal while any legal move exists. With **no legal move**, pass
  but still perform the **card exchange** (used/chosen card → transit, old
  transit → hand). Your used card reaches the opponent two half-moves later.
- **Win:** Way of the Stone (capture the enemy Master) or Way of the Stream
  (your Master reaches the enemy Temple Arch). House rule: 300 half-move cap →
  draw.

## 3. Architecture requirements

- `onitama.core` is **pure** game logic: no I/O, no sockets; shared verbatim by
  server (authority) and client (rendering/prediction). Package-private mutators.
- **Authoritative server:** clients never mutate state — they send `MoveRequest`
  and receive validated `MoveApplied` (full `GameState`) / `MoveRejected`.
  Rejection never changes state. Full-state broadcast every half-move (simple,
  robust; bandwidth irrelevant at this scale).
- **Thread model:** accept loop (main) → fixed client-handler pool → **one
  single-thread executor per match** ⇒ match state is thread-confined, **zero
  locks** in game logic. Shared registries (`SessionRegistry`, `LobbyManager`)
  use `ConcurrentHashMap`. Per-client socket writes happen only from that
  client's handler (single-writer rule). Clean shutdown via shutdown hook.
- **Serialization:** one Java-serialized object per message over
  `Object(ObjectOutput|Input)Stream`, flush after every message; every message
  class is `final` with `serialVersionUID = 1L`; `ObjectOutputStream.reset()`
  before every write (back-reference pitfall — otherwise receivers keep stale
  copies).
- **Heartbeats:** client `Ping` every 30 s; server `soTimeout` 90 s ⇒ disconnect.
- **Reconnect:** `LoginResponse` carries a random token; after a drop the
  opponent sees `OpponentLeft(60)` and the session parks; `ReconnectRequest`
  with the token restores the full `GameState`; grace expiry ⇒ forfeit.
- Maven single module, shade plugin → runnable jar. Package layout:
  `core` (Board, Piece, PlayerColor, Move, Card, CardDeck, RulesEngine,
  GameState, WinCondition, Elo, PracticeBot, Difficulty) · `net` (sealed
  `Message` interface + one record per message) · `server` (GameServer,
  ClientHandler, LobbyManager, MatchSession, SessionRegistry,
  MatchPersistence) · `db` (Database, `UserDao`/`MatchDao` interfaces + SQLite
  impls) · `client` (OnitamaClient, ClientSettings,
  `state` model + listeners, `ui` panels, `bot` load-test).

## 4. Network protocol

| Message | Dir | Fields / purpose |
|---|---|---|
| `RegisterRequest` / `LoginRequest` | C→S | username, password |
| `LoginResponse` | S→C | ok, errorText?, profile (Elo/W/L) + reconnect token |
| `CreateMatchRequest` / `JoinMatchRequest` | C→S | — / roomCode |
| `MatchCreated` / `MatchStart` | S→C | room code / yourColor, code, initial full `GameState` |
| `ListMatchesRequest` / `ListMatchesResponse` | C→S / S→C | lobby browser data |
| `MoveRequest` | C→S | cardId, from, to |
| `MoveApplied` | S→C | full `GameState` + lastMove, broadcast to both players |
| `MoveRejected` | S→C | reason (illegal / out-of-turn / card not in hand) |
| `PassTurn` | C→S | cardIdToDiscard — only legal with zero legal moves (server verifies) |
| `GameOver` | S→C | winnerColor, way (STONE/STREAM/DRAW/FORFEIT), both Elos after |
| `RematchRequest` / `RematchAccept` | C→S / S→C | new game, fresh deal |
| `OpponentLeft` | S→C | graceSeconds (60) before forfeit |
| `ReconnectRequest` | C→S | reconnect token |
| `LeaderboardRequest` / `LeaderboardResponse` | C→S / S→C | top 20 users |
| `Ping` / `Pong` | both | heartbeat every 30 s |
| `ErrorMessage` | S→C | friendly failure text |

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
  end_reason TEXT NOT NULL,                     -- STONE|STREAM|FORFEIT|DRAW
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

## 6. Client (Swing)

Five screens: **login/register**, **lobby** (profile, create/join, open matches,
leaderboard button, settings, practice entry), **game** (board, two
hand cards, opponent's two cards pre-rotated to *your* perspective, transit
card, turn banner, move history, piece counts, resign, rematch dialog),
**leaderboard**.

- Board always drawn **from your own side** (own pieces at the bottom; Red's
  view flips canonical coordinates). Custom `JPanel` + `paintComponent` +
  `Graphics2D`; card patterns render as mini 5×5 grids from the same `Card`
  data the engine uses (single source of truth).
- Click flow: card + piece (either order) → legal destinations highlight
  (from `RulesEngine` running locally on the authoritative state) → click
  destination sends `MoveRequest`; ESC / re-click clears selection.
- **EDT discipline:** all UI state on the Swing EDT; the socket **reader thread**
  only parses and hands messages to the model via `SwingUtilities.invokeLater`;
  UI actions enqueue outgoing messages. Never block the EDT on I/O; never touch
  Swing from the reader thread. Panels are swappable views over `ClientModel`
  (Observer via `ClientModelListener`) — UI never touches the socket.
- The visual redesign (M9) is implemented: sumi-e painting backgrounds, theme
  tokens in `ui/Theme`, hand-designed card/piece art loaded via `AssetStore`
  with drawn fallbacks when a file is absent; piece-style preference persisted
  in `client.properties`.

## 7. Design-pattern & OOP map (apply, don't just claim)

| Where | Pattern / principle | How |
|---|---|---|
| `core` | Strategy | Each card = immutable strategy object (offset list); `RulesEngine` is strategy-agnostic. |
| Client | Observer | `ClientModel` fires typed events; panels subscribe; no socket polling in UI. |
| Messages | Polymorphism + handler registry | Sealed `Message` interface, one record per message, dispatched without `instanceof` chains. |
| `db` | Interfaces + composition | `UserDao`/`MatchDao` interfaces, SQLite impls swappable. |
| `Main` | Factory method | Builds the right application (server/client/bots/demo) from args. |
| General | SOLID | SRP via packages; DIP server→DAO interfaces; OCP new message = new record. |
| Errors | Exception hierarchy | `OnitamaException` → IllegalMove (core) / Authentication / Persistence (db). UI shows friendly text; server logs stack traces; nothing crashes. |

Functional style used naturally: stream pipelines for legal-move filtering,
leaderboard top-N sorting.

## 8. Test inventory (all must keep passing)

| ID | Test class | Proves |
|---|---|---|
| UT01 | `CardPatternTest` | All 16 cards: destinations + 180° rotation match the Appendix exactly. |
| UT02 | `RulesEngineTest` | Jumps allowed; friendly-landing rejected; capture removes; off-board rejected. |
| UT03 | `WinConditionTest` | STONE (master captured) and STREAM (master on temple arch) wins; student on arch does not win. |
| UT04 | `CardCycleTest` | Card exchange, hands always size 2, rotated view for opponent, pass only with zero legal moves. |
| UT05 | `CardDeckTest` | Deal = 5 distinct cards; transit stamp picks first player. |
| UT06 | `EloTest` | Elo math (win/loss/draw, K=32). |
| UT07 | `UserDaoTest` | Register/login, wrong password, duplicates, Elo/W/L persistence (temp DB). |
| UT08 | `MatchDaoTest` | Match insert in transaction; rollback leaves no partial data; leaderboard order. |
| UT09 | `MessageSerializationTest` | Every message round-trips byte-identical. |
| IT01 | `ServerClientIntegrationTest` | Two scripted clients register, create/join, play a full game to a STONE win; identical broadcast sequences. |
| IT02 | `DisconnectReconnectTest` | Drop mid-match → `OpponentLeft` → token reconnect with identical state → grace expiry forfeits. |
| LT01 | `BotSmokeTest` | 8 bots, 4 concurrent random games, no errors. |

Rules: localhost only, ephemeral ports, temp dirs for DB tests, everything in
`mvn test`. Practice-mode bot: `PracticeBotTest` covers the three difficulties.

## 9. File I/O

- **Client settings:** `client.properties` (last host/port, window size, piece
  style). **Server:** CLI args override defaults; logging via
  `java.util.logging` (console + rotating file).

## 10. Demo verification checklist

- [ ] `mvn clean verify` green from a clean clone; jar builds via shade plugin.
- [ ] Two GUI clients + server on localhost: register → lobby → full game →
      game over → rematch, end to end.
- [ ] Practice mode plays on all three difficulties and the leaderboard works.
- [ ] Server survives illegal moves, unknown cards, malformed/foreign socket
      objects (reject + log, never crash), mid-game disconnects, Ctrl-C.
- [ ] No plaintext passwords, no dead/debug code, no `println` debugging.

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

Rules source: Onitama, Shimpei Sato, Arcane Wonders (2014); offsets
cross-verified against two independent open-source implementations.
