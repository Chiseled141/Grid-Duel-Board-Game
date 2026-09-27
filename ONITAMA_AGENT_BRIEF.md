# ONITAMA ONLINE — AI Agent Build Brief

**Project:** *Onitama Online* — a networked multiplayer implementation of the board game Onitama, in Java, with a server deployable to AWS (deployment is performed **manually by the team**).

**Audience:** an AI coding agent that will build the entire project from this document.
**Course context:** Java Software Development final project (see `Instructions.md`, `Rubric.md`, `Topics.md` in this folder). This project maps to **Topic 3 (Client/Server Application — Multiplayer Game)** combined with **Topic 2 (Game Development)** elements: Swing GUI, game state management, concurrency, database, file I/O.

> **If anything in this brief conflicts with the course documents, the course documents win.**

---

## 0. Rules for the Agent (read first)

1. **Read this whole document before writing any code.**
2. **Language is Java 17 (LTS).** Build tool is Maven. Nothing else.
3. **Allowed dependencies (complete list):**
   - `org.xerial:sqlite-jdbc` (database driver)
   - `org.junit.jupiter:junit-jupiter` (testing)
   - Nothing else. **No** Spring, JavaFX, Netty, Gson/Jackson, Lombok, game engines, or cloud SDKs. GUI is **Swing** (built into the JDK). Networking is **raw TCP sockets** (`ServerSocket`/`Socket`).
4. **AWS/cloud: DO NOTHING.** Do not deploy, do not create cloud resources, do not download cloud CLIs, do not ask for or use cloud credentials. You only guarantee the code runs on `localhost` out of the box and produce a deployment runbook (`docs/AWS_DEPLOY.md`) that a human follows manually. The server must be a plain headless Java process runnable as `java -jar ...`.
5. **The students must be able to explain every line in an oral exam (viva).** Therefore:
   - Prefer clarity over cleverness. Short methods, meaningful names, no golfed streams, no exotic tricks.
   - Javadoc on every public class and public method (1–3 sentences is enough).
   - Comments explain *why*, not *what*.
   - Keep the class count reasonable (~30–50 classes total). Do not generate filler classes to look big.
6. **Never fabricate results.** Where the report needs experimental numbers, put explicit `TODO(team)` placeholders. You provide the *measurement tools*; the humans run them and record real numbers.
7. **Build order:** implement milestone by milestone (Section 12). After each milestone the project must compile and `mvn test` must pass. Do not start a milestone with the previous one failing.
8. Leftover `System.out.println` debugging is forbidden. Use `java.util.logging.Logger`. No commented-out code, no dead code in the final deliverable.
9. Where this brief leaves a design detail open, choose the simplest option consistent with the rubric, and record the decision as a one-paragraph note in `docs/DESIGN_DECISIONS.md`.

---

## 1. What You Are Building

**Onitama Online**: two human players, on different machines, play a full game of Onitama against each other through a central Java server.

- **Server** (headless): manages user accounts, open matches (lobbies), live game state, disconnections, match history, Elo ratings, replay files. Multiple simultaneous matches.
- **Client** (Swing GUI): login/register, lobby (create/join matches), the game board with the five movement cards, move history, game-over and rematch flow, leaderboard, replay viewer.
- **Database** (SQLite via JDBC): users (with salted password hashes + Elo), match history.
- **Replays**: every match is persisted to a replay file that the client can load and step through.
- **Load-test harness**: a headless bot client used to produce real benchmark numbers for the report.

### Out of scope (do NOT build)
- Web or mobile clients, WebSocket/HTTP, matchmaking beyond "create room → share code → join".
- Spectator mode, in-game chat (optional stretch if everything else is done and stable).
- AWS automation of any kind (see rule 4).

---

## 2. Onitama — Complete Game Rules (implement exactly this)

Onitama is a perfect-information abstract strategy game for 2 players on a 5×5 board.

### 2.1 Board and pieces
- 5×5 grid. Canonical engine coordinates: `x` = column 0–4 (left→right), `y` = row 0–4. **Blue's home row is y=0; Red's home row is y=4.** "Forward" for a player = toward the opponent's home row.
- Each player has **5 pieces: 1 Master + 4 Students**.
- **Starting position (all pieces on the home row):**
  - Blue: Master at (2,0); Students at (0,0), (1,0), (3,0), (4,0).
  - Red: Master at (2,4); Students at (0,4), (1,4), (3,4), (4,4).
  - The center square of each home row — (2,0) and (2,4) — is that player's **Temple Arch**.
- All pieces move identically; piece type only matters for the Master (win conditions).

### 2.2 Movement cards (the core mechanic)
- There are **16 movement cards** (full data in Appendix A). Each card shows, relative to the moving piece's square, a set of destination offsets `(dx, dy)` where `+x` = right and `+y` = **forward (toward the opponent)**, from the perspective of the player holding the card.
- **Rotation rule:** the same physical card is used by both players. When the card is used by the player for whom the pattern is not written (i.e., the opponent of the card's "native" orientation), all offsets are rotated 180°: `(dx, dy) → (−dx, −dy)`. In the engine: for Blue use offsets as stored; for Red use the rotated form (or vice versa — just be consistent, and unit-test it).
- A move to an empty square relocates the piece. **Pieces do not block movement** — a piece may "jump over" any pieces on intermediate squares (the pattern defines landing squares only; there is no pathing).
- A piece may **not** land on a square occupied by a friendly piece.
- A piece **may** land on an enemy piece: the enemy piece is **captured** (removed).

### 2.3 Card deal and the card cycle
- At game start, shuffle the 16-card deck and deal: 2 cards to each player (face up; both players see all cards — there is no hidden information) and 1 extra card set aside as the **transit card**.
- Each card in the deck has a fixed **stamp color** (RED or BLUE, 8 of each). **The stamp color of the initial transit card decides who moves first** (that color's player). Simplification allowed if documented: fix "Red moves first" — but the stamp mechanism is preferred because it is the official rule and trivial to implement.
- **Each turn:** the player to move has exactly **2 hand cards**. They must:
  1. Choose **one of their 2 hand cards** and move **one** of their pieces to a legal destination of that card's pattern (for their color, after rotation). If at least one legal move exists with any of their cards, **they must move** — passing is illegal.
  2. If they have **no legal move at all** (with either card, any piece), they **pass** the move but still perform the card exchange of step 3.
  3. **Card exchange:** the used card (or, on a pass, a hand card of their choice) leaves their hand and becomes the new **transit card**; the player picks up the previous transit card. Net effect: every hand always has 2 cards, and **the card you use today becomes available to your opponent two half-moves later** — always check what you are handing over.

### 2.4 Win conditions
- **Way of the Stone:** capture the opponent's Master. Immediate win.
- **Way of the Stream:** move your Master onto the opponent's Temple Arch square ((2,4) for Blue, (2,0) for Red). Immediate win.
- There are no draws in practice; a game where both players only pass forever is theoretically possible but practically excluded — still, cap a match at 300 half-moves and declare it a draw if reached (document this house rule).

---

## 3. System Architecture

### 3.1 Overview

```text
+------------------+        TCP (Java-serialized          +---------------------------+
|  Client (Swing)  |        message objects)              |        Server             |
|                  | <----------------------------------> |                           |
|  GamePanel       |                                      |  AcceptLoop (main thread) |
|  LobbyPanel      |                                      |  ClientHandler  (pool)    |
|  LoginPanel      |                                      |  LobbyManager   (shared)  |
|  ReplayViewer    |                                      |  MatchSession   (own exec)|
+------------------+                                      +-------------+-------------+
                                                                        | JDBC
                                                                  +-----v-----+
                                                                  |  SQLite   |
                                                                  | users,    |
                                                                  | matches   |
                                                                  +-----------+
```

### 3.2 Maven layout (single module)

```text
project/
├── pom.xml                     # Java 17, shade plugin -> runnable jar, 2 deps only
├── README.md                   # build/run/test/deploy instructions (agent writes fully)
├── scripts/
│   ├── run-server.sh / .bat    # java -jar target/onitama.jar server --port 5555 --db data/onitama.db
│   ├── run-client.sh / .bat    # java -jar target/onitama.jar client --host <ip> --port 5555
│   └── run-bots.sh / .bat      # load-test harness
├── src/main/java/onitama/
│   ├── Main.java               # entry: dispatches "server" | "client" | "bots" by args[0]
│   ├── core/                   # PURE game logic — no I/O, no sockets (used by server AND client)
│   │   ├── Board.java          # 5x5 grid, piece placement, queries
│   │   ├── Piece.java          # color + master/student
│   │   ├── PlayerColor.java    # BLUE, RED
│   │   ├── Move.java           # from, to, cardId, capturing?
│   │   ├── Card.java           # id, name, stamp color, immutable move-offset list
│   │   ├── CardDeck.java       # the 16 cards, shuffle+deal (2/2/1), stamp->first player
│   │   ├── RulesEngine.java    # legal destinations for (board, piece, card, color); apply(Move)
│   │   ├── GameState.java      # board + hands + transit card + turn + moveNumber + status
│   │   └── WinCondition.java   # enum STONE/STREAM/DRAW + evaluation hook
│   ├── net/                    # protocol shared by client & server
│   │   ├── Message.java        # abstract Serializable root, serialVersionUID fixed
│   │   └── ...Message classes  # one small final class per message (Section 4)
│   ├── server/
│   │   ├── GameServer.java     # ServerSocket accept loop, shuts down cleanly
│   │   ├── ClientHandler.java  # one per connected client: reads messages, dispatches
│   │   ├── LobbyManager.java   # open matches, join-by-code, matchmaking bookkeeping
│   │   ├── MatchSession.java   # one live game: validates moves, owns authoritative GameState
│   │   └── SessionRegistry.java# authenticated connections, reconnect tokens
│   ├── db/
│   │   ├── Database.java       # connection management, schema init (DDL), transactions
│   │   ├── UserDao.java        # register, login, update elo/wins (PreparedStatement)
│   │   └── MatchDao.java       # insert finished match, list history, leaderboard query
│   └── client/
│       ├── OnitamaClient.java  # socket lifecycle, reader thread, reconnect logic
│       ├── ui/                 # Swing: LoginPanel, LobbyPanel, GamePanel, BoardRenderer,
│       │                       # CardRenderer, ReplayViewer, dialogs, theme constants
│       ├── state/              # client-side model + Observer listeners (UI never touches socket)
│       └── bot/                # LoadTestBot: headless client playing random legal games
└── src/test/java/onitama/      # JUnit 5 tests (Section 9)
```

### 3.3 Why this architecture (state it in the report)
- `core` is pure and dependency-free → fully unit-testable, reused verbatim by server (authority) and client (rendering/prediction).
- Server is **authoritative**: clients never mutate game state; they send `MoveRequest` and receive validated `MoveApplied`/`GameState`. Prevents cheating and desync.
- One **single-thread executor per match** ⇒ game state is thread-confined; no locks inside game logic at all.
- Shared registries use `ConcurrentHashMap`; per-client state lives only inside its `ClientHandler`.

---

## 4. Network Protocol

- **Transport:** TCP, one persistent connection per client (`Socket`). Server listens on a configurable port (default **5555**).
- **Framing & format:** `ObjectOutputStream` / `ObjectInputStream` — each message is one Java-serialized object (this deliberately demonstrates Object I/O + serialization for the rubric). Every message class declares `private static final long serialVersionUID = 1L;` and is final. Never write two objects per flush; flush after every message.
- All messages extend `abstract Message implements Serializable`. One class per purpose:

| Message | Direction | Fields | Purpose |
|---|---|---|---|
| `RegisterRequest` | C→S | username, password | Create account |
| `LoginRequest` | C→S | username, password | Authenticate |
| `LoginResponse` | S→C | ok, errorText?, userProfile (elo/wins/losses) | Result; also carries a random **reconnect token** |
| `CreateMatchRequest` | C→S | — | Open a lobby; server returns a 5-char room code |
| `JoinMatchRequest` | C→S | roomCode | Join an open match |
| `ListMatchesRequest` / `ListMatchesResponse` | C→S / S→C | — / open matches + users | Lobby browser |
| `MatchStart` | S→C | yourColor, roomCode, initial `GameState` (board, both hands as card ids, transit card id, first player) | Game begins |
| `MoveRequest` | C→S | cardId, from, to | Attempt a move |
| `MoveApplied` | S→C | `GameState` (full authoritative state after the move), lastMove | Broadcast to both players after every half-move |
| `MoveRejected` | S→C | reason | Illegal/out-of-turn move (protocol error → also logged) |
| `PassTurn` | C→S | cardIdToDiscard | Only legal when the player truly has no legal move (server verifies) |
| `GameOver` | S→C | winnerColor, way (STONE/STREAM/DRAW/FORFEIT), updated Elo for both | Match end; server persists match + replays |
| `RematchRequest` / `RematchAccept` | C→S / S→C | — | New game same players (fresh card deal) |
| `OpponentLeft` | S→C | graceSeconds | Opponent disconnected; 60 s reconnect grace, then forfeit |
| `ReconnectRequest` | C→S | reconnectToken | Resume session after drop |
| `LeaderboardRequest` / `LeaderboardResponse` | C→S / S→C | — / top 20 users | Client leaderboard screen |
| `Ping` / `Pong` | both | timestamp | Heartbeat every 30 s; 90 s silence ⇒ disconnect |
| `ErrorMessage` | S→C | text | Friendly failure for anything not covered above |

- **Flow rules:** server rejects moves from the wrong player, wrong turn, unknown card (not in that player's hand), or illegal pattern — the game state never changes on rejection. Every state change broadcasts the **full** `GameState` (simple and robust; the board is tiny, bandwidth is irrelevant at this scale).

---

## 5. Database (SQLite via JDBC)

File-based DB — zero setup locally and on the EC2 host. Driver: `org.xerial:sqlite-jdbc`. Connection: single shared `Connection` guarded by a lock, or a small pool — keep it simple and explain the choice.

Schema (created idempotently at server start):

```sql
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,              -- PBKDF2WithHmacSHA256, random 16-byte salt, format: iterations:saltB64:hashB64
  elo           INTEGER NOT NULL DEFAULT 1000,
  wins          INTEGER NOT NULL DEFAULT 0,
  losses        INTEGER NOT NULL DEFAULT 0,
  created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS matches (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  blue_user_id   INTEGER NOT NULL REFERENCES users(id),
  red_user_id    INTEGER NOT NULL REFERENCES users(id),
  winner_user_id INTEGER REFERENCES users(id),  -- NULL on draw
  end_reason     TEXT NOT NULL,                 -- STONE | STREAM | FORFEIT | DRAW
  move_count     INTEGER NOT NULL,
  elo_blue_after INTEGER NOT NULL,
  elo_red_after  INTEGER NOT NULL,
  replay_path    TEXT,                          -- relative path to replay file
  played_at      TEXT NOT NULL
);
```

Requirements:
- **All** SQL through `PreparedStatement` (no string concatenation — SQL injection must be structurally impossible).
- **Transactions:** recording a finished match (insert row + update both users' Elo/wins/losses) runs in one transaction with rollback on failure — this is the report's transaction example.
- **Elo:** standard Elo, K=32, two-player zero-sum update; implement in `core` or `db` as a pure static function and unit-test it.
- Passwords: never stored or logged in plaintext; hashing via `javax.crypto` (JDK built-in).

---

## 6. Client (Swing GUI)

> **Note (added after M8):** the screens below are the *functional baseline*
> built in milestone M4. The team plans a visual redesign pass (M9) once they
> supply a UI design — see **Section 17** for the rules that redesign must
> follow. Keep the views swappable: panels render from `ClientModel`, never
> from sockets.

### 6.1 Screens
1. **Login/Register** — username + password fields, buttons, error dialog on failure.
2. **Lobby** — user info (name, Elo), "Create match" (shows room code), "Join by code" input, list of open matches, "Leaderboard" button, "Replay viewer" button.
3. **Game** — the board (center), your two hand cards (bottom), opponent's two cards (top, readable — patterns shown from *your* perspective so you can plan what you'll receive), transit card (side), turn indicator, move history list, captured-pieces tray, "Resign" button, game-over dialog with rematch offer.
4. **Leaderboard** — JTable of top players.
5. **Replay viewer** — open a `.onitama-replay` file, step forward/back through moves.

### 6.2 Board interaction (make it feel good but keep it simple)
- The board is always drawn **from your own side** (your pieces at the bottom); renderers map canonical coordinates through a flip for Red.
- Click flow: select one of your hand cards → select one of your pieces → legal destinations highlight → click a destination to send `MoveRequest`. Also support reverse order (piece first, then card). ESC or re-click clears selection.
- Clicking a destination that the *opponent's* view implies is not highlighted should be impossible — highlights come from `RulesEngine` running locally on the authoritative `GameState` you received.
- Rendering: custom `JPanel` + `paintComponent` with `Graphics2D` (no layout-manager grid tricks for the board): squares, pieces (distinct master vs student shapes + colors), selection ring, legal-move dots, last-move highlight. Card patterns are drawn as mini 5×5 grids from the same `Card` data used by the engine (single source of truth).
- Movement is instant; a short (≤200 ms) last-move arrow/highlight is enough "animation" — do not build a tweening engine.

### 6.3 Client threading (document it for the report)
- **Swing EDT** owns all UI state. **Socket reader thread** only parses messages and hands them to the model via `SwingUtilities.invokeLater` (or a `BlockingQueue` drained by a `javax.swing.Timer`). UI actions enqueue outgoing messages. **Never** block the EDT on network I/O; **never** touch Swing components from the reader thread.

---

## 7. Server Internals & Concurrency Model

- **Main thread:** `ServerSocket.accept()` loop; hands each socket to the pool.
- **Client handlers:** fixed `ExecutorService` (e.g. 32 threads). One `ClientHandler` per connection: blocking read loop → dispatch to lobby/match → write responses. Writes to a client's socket happen only from that client's handler (single-writer rule) or via a synchronized send queue — pick one, document it.
- **Match sessions:** each live match gets `Executors.newSingleThreadExecutor()`. All state mutations for a match are tasks submitted to that executor ⇒ game state is **thread-confined, zero locks** in game logic. `MatchSession` validates the move with `RulesEngine`, applies it, broadcasts `MoveApplied` to both handlers, checks win conditions, finishes the match (DB write in transaction + replay file + Elo).
- **Shared state:** `SessionRegistry`, `LobbyManager` → `ConcurrentHashMap` + atomic counters where needed. Document what is shared, who writes it, and why races cannot occur (this paragraph goes straight into the report's concurrency section).
- **Disconnections:** reader gets EOF/exception → if in a match, notify opponent with `OpponentLeft(60)` and park the session; a `ReconnectRequest` with the correct token restores the session and full `GameState`; grace expiry ⇒ forfeit → `GameOver`.
- **Lifecycle:** clean shutdown on SIGINT (`Runtime shutdown hook`): close sockets, stop executors, close DB.

---

## 8. Design-Pattern & OOP Map (for the rubric — apply these, don't just claim them)

| Where | Pattern / principle | How |
|---|---|---|
| `core` | **Strategy** | Each movement card is an immutable strategy object supplying destination offsets; `RulesEngine` is strategy-agnostic. |
| Client model | **Observer** | `ClientModel` fires typed events (state changed, game over, opponent left); panels subscribe. UI never polls the socket. |
| Messages | **Polymorphism + Visitor-lite** | Server dispatches on message type via pattern/`instanceof`-free design (e.g., abstract `handle(ServerContext)` per message class or a `Map<Class, Handler>` functional registry — choose one, document it). |
| `db` | **Composition + interface segregation** | DAOs are interfaces (`UserDao`, `MatchDao`) with SQLite implementations — an alternate DB could be swapped in. |
| `Main` | **Factory method** | `Main` builds the right `Runnable` application (server/client/bots) from args. |
| General | **SOLID** | SRP: rules vs networking vs persistence vs UI are separate packages. DIP: server depends on DAO interfaces. OCP: adding a message type adds a class, doesn't edit a switch spanning concerns. |
| Exceptions | Custom hierarchy | `OnitamaException` (base) → `IllegalMoveException`, `ProtocolException`, `AuthenticationException`, `PersistenceException`. UI shows friendly messages; server logs stack traces; nothing crashes the process. |

Functional programming (required by rubric — use naturally, not decoratively): stream pipelines for legal-move filtering (`RulesEngine`), leaderboard sorting/top-N, move-history mapping in the replay viewer, lobby list formatting.

Meta-programming: **skip** (optional per course rules; not needed — do not fake it).

---

## 9. Testing Plan (JUnit 5 — you must write ALL of these)

| ID | Test class | What it proves |
|---|---|---|
| UT01 | `CardPatternTest` | For **each of the 16 cards** (parameterized): unrotated destinations from center of an empty board equal Appendix A data; rotated = 180° mirror. This test is the guardian against card-data typos. |
| UT02 | `RulesEngineTest` | Jumping over pieces allowed; landing on friendly piece rejected; capture removes enemy; off-board rejected. |
| UT03 | `WinConditionTest` | Win by capturing master (STONE); win by master reaching opponent temple arch (STREAM); non-master on temple arch does NOT win. |
| UT04 | `CardCycleTest` | After a turn: used card in transit, previous transit in hand, hands always size 2; opponent's view of a card equals rotated pattern; pass only permitted with zero legal moves. |
| UT05 | `CardDeckTest` | Deal returns 5 distinct cards; stamp color of transit card picks first player. |
| UT06 | `EloTest` | Elo update math (winner/loser/draw, K=32). |
| UT07 | `UserDaoTest` | Register → login ok; wrong password fails; duplicate username fails; Elo/wins update persists. (Uses a temp DB file.) |
| UT08 | `MatchDaoTest` | Record match in transaction; rollback leaves no partial data; leaderboard query ordered correctly. |
| UT09 | `MessageSerializationTest` | Every message type round-trips through `ObjectOutput/ObjectInput` streams byte-identical (fields preserved). |
| IT01 | `ServerClientIntegrationTest` | Start server on an ephemeral port; two headless socket clients register/login, create/join, play a scripted full game to a forced STONE win; assert both receive identical `MoveApplied` sequence and `GameOver`. |
| IT02 | `DisconnectReconnectTest` | Client drops mid-match; opponent gets `OpponentLeft`; client reconnects with token and receives identical `GameState`; grace expiry forfeits. |
| LT01 | `BotSmokeTest` | 8 bots play 4 concurrent random games against the server without errors (small, CI-friendly). |

Rules: tests must not depend on network reachability beyond localhost, no fixed ports (ephemeral), DB tests use temp directories, everything runs in `mvn test`.

---

## 10. Experimental Evaluation Tooling (humans run it, you build it)

`bots` mode (`Main.java args[0] = "bots"`): N bot clients (`--bots N --games G`) that login as generated users, pair up, and play **random legal moves** as fast as the server allows. It measures and writes **CSV** to `results/`:
- per-move round-trip latency (client-send → `MoveApplied`-received), with p50/p95/max, per bot count;
- completed games per minute (server throughput);
- server memory (parse from `/proc` or `Runtime` totals — document method) and thread count.

Provide a `docs/EXPERIMENTS.md` describing exactly which experiments to run (bots = 1/2/5/10/20, e.g.) and empty result tables for the team to fill with **real** measured numbers. **Do not invent numbers anywhere.**

---

## 11. File I/O (rubric coverage)

- **Replay files** (text I/O, `BufferedReader`/`BufferedWriter`, UTF-8): header lines (players, date, dealt cards, first player) then one line per half-move: `moveNumber;cardId;fromX,fromY;toX,toY`. Extension `.onitama-replay`, stored under `replays/`. The replay viewer parses these with streams and validates them against `RulesEngine` (a corrupted replay is reported, not crashed on).
- **Client settings** (`~/.onitama/client.properties` via `Properties`): last host/port, window size.
- **Server config**: CLI args override `server.properties` file. Log to console + rotating file via `java.util.logging`.

---

## 12. Milestones (build in this order; each must compile + pass tests before the next)

| # | Milestone | Acceptance criteria |
|---|---|---|
| M1 | `core` game engine | All UT01–UT06 pass. A tiny `main` can print a board and legal moves. No I/O, no sockets. |
| M2 | Protocol (`net`) | Message hierarchy + UT09 passes. |
| M3 | Server (headless) | Lobby + match flow works; IT01, IT02 pass. Two scripted clients finish a real game through sockets. |
| M4 | Swing client | Two GUI clients on one machine can log in, meet in a lobby, and play a full game on localhost. UT/IT suites still green. |
| M5 | DB + Elo + replays | Registration/login persisted; match recorded in transaction; replay file written and viewable in client. UT07/UT08 pass. |
| M6 | Resilience & polish | Reconnect works (IT02); heartbeats; friendly error dialogs everywhere; menus/dialogs complete; logging clean. |
| M7 | Benchmark harness + docs | `bots` mode works; LT01 passes; README.md final; `docs/AWS_DEPLOY.md`, `docs/EXPERIMENTS.md`, `docs/DESIGN_DECISIONS.md`, `report/REPORT_SKELETON.md` written. |
| M8 | Final QA | `mvn clean verify` green; runnable-jar instructions verified from a clean clone; course checklist (Instructions.md §15) reviewed item by item. |
| M9 | UI design pass (**planned, blocked on the team's design**) | Implement the supplied design under the rules of Section 17; all existing tests stay green; behavior unchanged. |

---

## 13. AWS Deployment Runbook — content of `docs/AWS_DEPLOY.md` (you WRITE it, never RUN it)

A short, numbered, human-followable runbook the team executes manually. The runbook must state these constraints up front: this is a low-traffic university project, so keep AWS spend minimal — a single small EC2 instance is enough (`t4g.small` is sufficient; nothing larger), EC2 only with **no additional AWS managed services** (SQLite + systemd already cover persistence and process supervision — no RDS, load balancers, containers, etc.), and the team's AWS credits are budgeted for their separate CV project, so include stopping the instance whenever it is not in use.
1. Launch an EC2 instance (Ubuntu 22.04/24.04, `t4g.small` — ARM; the pure-Java jar runs unchanged on Graviton, but pin a recent `sqlite-jdbc` version so the bundled Linux/ARM64 native library is included), key pair.
2. Security group: inbound TCP **5555** (game) from 0.0.0.0/0; SSH (22) restricted to the team's IPs.
3. Install JDK 17: `sudo apt update && sudo apt install -y openjdk-17-jre-headless`.
4. Copy the built jar: `scp target/onitama.jar ubuntu@<EC2_IP>:~/`.
5. Run under `systemd` (provide the full `onitama.service` unit file in the doc: `ExecStart=/usr/bin/java -jar /home/ubuntu/onitama.jar server --port 5555 --db /home/ubuntu/data/onitama.db`, `Restart=always`).
6. `sudo systemctl enable --now onitama`; verify: `ss -tlnp | grep 5555`.
7. Clients connect with `java -jar onitama.jar client --host <EC2_PUBLIC_IP> --port 5555`.
8. Backup note: the DB and replays are plain files — `tar` the `data/` and `replays/` directories.
Also include a troubleshooting table (port closed → security group; class-version error → JDK 17; DB locked → two server processes).

---

## 14. README.md (you write it completely) must contain

1. One-paragraph project description + feature list. 2. Software requirements (JDK 17+, Maven 3.8+). 3. Build: `mvn package` → `target/onitama.jar`. 4. Run server / run client / run bots (with the scripts and both localhost and EC2 examples). 5. How to run tests (`mvn test`) and what they cover. 6. Project structure tree with one-line directory explanations. 7. How to play Onitama (4–6 lines, referencing the cards). 8. Known limitations. 9. Team member placeholders table.

## 15. Report skeleton — `report/REPORT_SKELETON.md`

Produce the section headings exactly following Instructions.md §11 (Title → Appendix) with, under each: 3–6 bullet points of *what to write there* and `TODO(team)` placeholders (names, real measurements, screenshots). Do **not** write report prose — the team writes it (Compilatio similarity ≤ 20% is their responsibility; a pre-written report would endanger that).

---

## 16. Definition of Done (check before declaring victory)

- [ ] `mvn clean verify` passes from a clean clone; jar builds via shade plugin.
- [ ] Two GUI clients + server on localhost: register → lobby → full game → game over → rematch works end-to-end.
- [ ] All tests from Section 9 exist and pass; `mvn test` < 2 minutes.
- [ ] Server survives: illegal moves, unknown cards, malformed/foreign objects on the socket (reject + log, never crash), client disconnects mid-game, and Ctrl-C (clean shutdown).
- [ ] No plaintext passwords, no fabricated experimental numbers, no dead/debug code, no `println` debugging.
- [ ] `git` history meaningful (agents: commit per milestone with clear messages; humans: branch/merge practice on top).
- [ ] README + docs complete and accurate; runbook written but **nothing deployed to any cloud**.

---

## 17. Planned Follow-up: UI Design Pass (M9) — read before any UI work

**Status: design direction chosen.** The team selected the fireship.dev
"retro-pop neo-brutalism on dark" direction; it is concretized into the
implementation contract **`docs/UI_DESIGN.md`** (tokens, UI kit, per-screen
specs, checklist). M9 = implement that document. The current Swing UI is a
*functional placeholder*: it was built for correctness and testability, not
looks. Agents must **not** restyle or restructure the UI preemptively —
only as an explicit M9 execution of `docs/UI_DESIGN.md`.

When the design is provided, implement it under these rules:

1. **Scope: `onitama.client.ui` only.** Theme constants, panel layouts,
   `BoardPanel`/`CardPanel` painting, dialogs, and the menu bar. Every other
   package (`core`, `net`, `server`, `db`, `replay`, `client.state`, and the
   `client` root classes) must remain unchanged — the redesign is a view
   swap, not a behavior change. If the design appears to require a model or
   protocol change, **stop and ask the team**; it almost never does (the
   Observer-based `ClientModel` was built precisely so panels are
   replaceable views).
2. **No new dependencies.** Swing and the JDK only (rule 3 of this brief) —
   no look-and-feel libraries, icon packs, or animation frameworks. Custom
   `Graphics2D` painting is fine and expected for board/cards. The team
   additionally supplies **hand-designed art assets** (board, pieces, card
   faces, icon) per the contract in **`docs/ASSET_CHECKLIST.md`** — these
   are resource files, not dependencies, and each one must gracefully fall
   back to the drawn baseline when absent.
3. **Behavior invariants that must survive the redesign:**
   - the board is always drawn from the player's own side, and the
     opponent's card patterns are pre-rotated to the player's perspective
     (Section 6.2);
   - the click flow stays: card + piece (either order) → legal destinations
     highlighted → destination click sends `MoveRequest`; ESC clears the
     selection (Section 6.2);
   - all state keeps flowing through `ClientModel` and
     `ClientModelListener`; the EDT/reader-thread separation (Section 6.3)
     is untouched;
   - auto-pass behavior, rematch dialog flow, resign, reconnect and error
     dialogs keep working exactly as before.
4. **Acceptance criteria:** `mvn clean verify` green; all five screens still
   fully usable (login, lobby, game, leaderboard, replay viewer); IT01,
   IT02 and LT01 pass unchanged; a new DESIGN_DECISIONS.md entry documents
   what the design changed and why; README screenshots/structure updated if
   panel names change.
5. **Deliverables:** the updated `ui` classes, before/after screenshots for
   the report (the team inserts them into
   `report/REPORT_SKELETON.md` §13), and the design-decision note.

# Appendix A — The 16 Movement Cards (authoritative data)

Convention: offsets `(dx, dy)` from the moving piece; **+x = right, +y = forward toward the opponent** (Blue's native orientation). For the other player, rotate 180°: `(dx,dy) → (−dx,−dy)`. `M` = moving piece, `X` = legal destination. Diagrams show the native orientation (forward = up).

| Card | Offsets (dx, dy) |
|---|---|
| Tiger | (0, +2), (0, −1) |
| Dragon | (−2, +1), (+2, +1), (−1, −1), (+1, −1) |
| Frog | (−2, 0), (−1, +1), (+1, −1) |
| Rabbit | (+2, 0), (+1, +1), (−1, −1) |
| Crab | (0, +1), (−2, 0), (+2, 0) |
| Elephant | (−1, 0), (+1, 0), (−1, +1), (+1, +1) |
| Goose | (−1, 0), (+1, 0), (−1, +1), (+1, −1) |
| Rooster | (−1, 0), (+1, 0), (−1, −1), (+1, +1) |
| Monkey | (−1, +1), (+1, +1), (−1, −1), (+1, −1) |
| Mantis | (0, −1), (−1, +1), (+1, +1) |
| Horse | (0, +1), (0, −1), (−1, 0) |
| Ox | (0, +1), (0, −1), (+1, 0) |
| Crane | (0, +1), (−1, −1), (+1, −1) |
| Boar | (0, +1), (−1, 0), (+1, 0) |
| Eel | (−1, +1), (−1, −1), (+1, 0) |
| Cobra | (+1, +1), (+1, −1), (−1, 0) |

> Data verified against two independent implementations that agree 100%. `UT01` must encode exactly this table. Stamp colors (RED/BLUE, 8 each) are not publicly standardized per card — assign them as a fixed constant map in `CardDeck` (document the choice), or simplify to "Red always moves first" (also document).

### Tiger
```text
. . X . .
. . . . .
. . M . .
. . X . .
. . . . .
```
### Dragon
```text
. . . . .
X . . . X
. . M . .
. X . X .
. . . . .
```
### Frog
```text
. . . . .
. X . . .
X . M . .
. . . X .
. . . . .
```
### Rabbit
```text
. . . . .
. . . X .
. . M . X
. X . . .
. . . . .
```
### Crab
```text
. . . . .
. . X . .
X . M . X
. . . . .
. . . . .
```
### Elephant
```text
. . . . .
. X . X .
. X M X .
. . . . .
. . . . .
```
### Goose
```text
. . . . .
. X . . .
. X M X .
. . . X .
. . . . .
```
### Rooster
```text
. . . . .
. . . X .
. X M X .
. X . . .
. . . . .
```
### Monkey
```text
. . . . .
. X . X .
. . M . .
. X . X .
. . . . .
```
### Mantis
```text
. . . . .
. X . X .
. . M . .
. . X . .
. . . . .
```
### Horse
```text
. . . . .
. . X . .
. X M . .
. . X . .
. . . . .
```
### Ox
```text
. . . . .
. . X . .
. . M X .
. . X . .
. . . . .
```
### Crane
```text
. . . . .
. . X . .
. . M . .
. X . X .
. . . . .
```
### Boar
```text
. . . . .
. . X . .
. X M X .
. . . . .
. . . . .
```
### Eel
```text
. . . . .
. X . . .
. . M X .
. X . . .
. . . . .
```
### Cobra
```text
. . . . .
. . . X .
. X M . .
. . . X .
. . . . .
```

# Appendix B — Reference Sources (cite in the report)

- Onitama, Shimpei Sato, Arcane Wonders (2014) — official rules (Way of the Stone / Way of the Stream, card cycle, stamp-color first player).
- Card patterns cross-verified between two independent open-source implementations (a Java engine and a Rust engine) — list the actual repos in the final report.
