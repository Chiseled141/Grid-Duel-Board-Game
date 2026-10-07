# Grid-Duel-Board-Game

**Grid-Duel-Board-Game** is a networked two-player abstract strategy game
played on a 5x5 board with rotating movement cards. Two human players on
different machines play a full game through a central authoritative Java
server: the
server manages user accounts, lobbies, live games, disconnections, Elo
ratings and match history; a Swing desktop client provides the
login, lobby, game board and leaderboard screens.

**Features**

* Full Grid-Duel-Board-Game rules: 16 movement cards with rotation, card cycle with the
  transit card, mandatory passing, Way of the Stone / Way of the Stream wins,
  300-half-move draw cap.
* Authoritative TCP server (raw sockets + Java object serialization) with
  multiple concurrent matches, one single-thread executor per match.
* Accounts with salted PBKDF2 password hashes, Elo ratings (K = 32),
  SQLite persistence (JDBC), transactional match recording.
* Matchmaking by 5-character room code, rematch flow, resign,
  disconnect grace period with forfeit, token-based reconnect.
* Swing client: login, lobby, game board drawn from your own side, legal-move
  highlights, move history, piece counts, leaderboard.
* Single-player practice mode: local games against the built-in `PracticeBot`
  at three difficulties — **Rookie** (forgiving heuristic with beginner
  mercy), **Senior** (2 half-move search) and **Legend** (5 half-move
  alpha-beta search) — no server, no Elo at stake, instant rematch.
* Headless load-test harness (`bots` mode) that measures per-move latency and
  games/minute into CSV files.

## Software requirements

* JDK 17 or newer (tested with JDK 17/25/26)
* Maven 3.8+
* No other dependencies: the only libraries are `org.xerial:sqlite-jdbc`
  (driver) and `org.junit.jupiter:junit-jupiter` (tests, compile-only).

## Build

```bash
mvn package          # builds and runs the test suite
# -> target/onitama.jar (runnable fat jar)
mvn clean package    # full clean build
```

## Run

All modes are dispatched by the first argument of the jar.

**Server** (headless; Ctrl-C shuts it down cleanly):

```bash
java -jar target/onitama.jar server --port 5555 --db data/onitama.db
# or
scripts/run-server.sh            # scripts\run-server.bat on Windows
```

**Client** (Swing GUI):

```bash
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
# or
scripts/run-client.sh 127.0.0.1 5555
```

Two clients on one machine: start the server, then run the client twice,
register two accounts, create a match in one window and join with the room
code in the other.

**Single-player** (no opponent needed): log in, then in the lobby's
**SINGLE PLAY** card press **Play** and pick a difficulty — **Rookie** for
beginners, **Senior** for players who know the game, **Legend** for hardcore
masters. The game runs locally in the client against the built-in bot —
nothing is sent to the server, ratings are untouched, and the game-over
dialog offers an instant rematch at the same level.

**Load-test bots** (headless; writes CSV files into `results/`):

```bash
java -jar target/onitama.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4
# or
scripts/run-bots.sh 8 4
```

**Demo** (prints a dealt board and all legal first moves, no network):

```bash
java -jar target/onitama.jar demo --seed 42
```

## Tests

```bash
mvn test
```

The suite covers: every card pattern against the authoritative offset table
(UT01), legality rules (UT02), win conditions (UT03), the card cycle and
passes (UT04), dealing (UT05), Elo math (UT06), SQLite accounts (UT07),
transactional match recording incl. rollback (UT08), protocol serialization
round-trips (UT09), a full scripted game over real sockets (IT01), disconnect
→ reconnect → forfeit (IT02), heartbeat silence drop, and a bot smoke run of
8 bots / 16 games (LT01). Tests use ephemeral ports and temporary database
files only.

## Project structure

```text
pom.xml                  Maven build (Java 17, shade plugin -> onitama.jar)
scripts/                 run-server / run-client / run-bots (.sh and .bat)
src/main/java/onitama/
├── Main.java            entry point: demo | server | client | bots
├── core/                PURE game logic: board, cards, rules engine,
│                        game state, Elo, the practice-mode Difficulty
│                        ladder + PracticeBot — no I/O, shared by
│                        server & client
├── net/                 protocol: sealed Message interface + one record per
│                        message type (Java object serialization over TCP)
├── server/              GameServer (accept loop), ClientHandler, LobbyManager,
│                        MatchSession (authoritative, thread-confined),
│                        SessionRegistry, MatchPersistence
├── db/                  Database (SQLite/JDBC), UserDao + MatchDao and their
│                        SQLite implementations, PBKDF2 password hashing
├── client/              OnitamaClient (socket lifecycle), state/ (EDT model +
    │                    observer events), ui/ (Swing panels & renderers),
    └── bot/             headless load-test bot + harness (CSV results)
src/main/resources/      fonts/ (display font, OFL license) and assets/
                         (hand-designed board/piece/card art — optional,
                         each falls back to the drawn baseline when absent)
src/test/java/onitama/   JUnit 5 tests (UT01–UT09, IT01–IT02, LT01)
results/                 (created by bot runs) latency + summary CSV files
data/                    (created by the server) SQLite database file
```

## How to play Grid-Duel-Board-Game

Each player has 5 pieces (1 Master, 4 Students) starting on their home row.
On your turn, play **one of your two hand cards** and move **one piece** to a
square allowed by that card's pattern — patterns are relative to *your*
viewing direction, and the same physical card is used rotated by the
opponent. The played card swaps with the **transit card** beside the board,
so the card you use today becomes available to your opponent two half-moves
later — always check what you hand over. If you have no legal move you must
pass (but still swap a card). **Win** by capturing the enemy Master (Way of
the Stone) or by moving your Master onto the enemy Temple Arch, the marked
center square of their home row (Way of the Stream).

## Known limitations

* The UI is the custom retro-pop × sumi-e redesign (M9): hand-designed card
  faces, piece styles and painting backgrounds load from `assets/` with drawn
  fallbacks when a file is absent.
* Matchmaking is only "create room → share 5-character code → join"; there is
  no automatic matchmaking queue, no spectator mode and no in-game chat.
* Elo has no provisionality, rating floor or decay; a small player pool can
  drift (a single win moves a rating by ±16 for equal ratings, less when the
  favorite wins, more for an upset).
* SQLite serializes all database access through one connection — by design;
  heavy concurrent write load was not a goal.
* The load-test harness measures client-observed round-trip latency and
  games/minute; server CPU/memory are not sampled automatically.

## Team members

| Student ID | Name | Main responsibility |
| ---------- | ---- | ------------------- |
| TODO(team) | TODO(team) | core engine + tests |
| TODO(team) | TODO(team) | server + concurrency |
| TODO(team) | TODO(team) | database + persistence |
| TODO(team) | TODO(team) | Swing client + UX |
| TODO(team) | TODO(team) | load testing + benchmarking |
