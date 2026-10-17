# AGENTS.md — Harness for Grid Duel

> Read this file at the start of every task. It is the only harness entry point.

## Stack & hard constraints

- Java 17, Maven single module. Only deps: `org.xerial:sqlite-jdbc` + `org.junit.jupiter:junit-jupiter` (test). Swing for UI, raw `ServerSocket`/`Socket` + Java object serialization for networking. No Spring/JavaFX/Netty/Gson/Lombok/cloud SDKs.
- Everything runs on localhost. `mvn package` → `target/onitama.jar` dispatched by `onitama.Main` on `args[0]`: `server` | `client` | `bots` | `demo`.
- `GRID-DUEL-BOARD-GAME_SPEC.md` (renamed from `ONITAMA_AGENT_BRIEF.md`) is the authoritative spec (rules, protocol, DB schema, test inventory, Appendix offsets). Treat it as ground truth.

## Migration status and authority

- The 7×7 skill-card Grid Duel gameplay migration (Milestones A–F) is complete. The runnable engine in `onitama.core.duel` powers all application routes: server, client, practice mode, bot fleet, and demo CLI.
- Configuration is loaded via `RulesConfigLoader` from bundled defaults with optional external `.properties` override.
- Protocol version `GRID_DUEL_PROTOCOL:4` handles recipient-specific views, action sequence retries, and reconnect.
- Swing client (`ClientModel`, `BoardPanel`, `GamePanel`, `SkillCardPanel`, `ForesightDialog`) is fully integrated and playable.

## Commands

```bash
mvn test          # all tests (UT01-UT09, IT01-02, LT01, PracticeBotTest) — must be green after every change
mvn clean verify  # clean shade build; report actual test count
mvn package       # builds target/onitama.jar
java -jar target/onitama.jar demo --seed 42
java -jar target/onitama.jar server --port 5555 --db data/onitama.db
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
java -jar target/onitama.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4
```

Tests use ephemeral ports + temp DB files. Never require a running server.

## Invariants — do not violate

1. `onitama.core` is pure logic: no I/O, no sockets. Shared server↔client verbatim.
2. Target board: 7×7, `x/y=0..6`; Blue starts at `y=0`, Red at `y=6`, each at `x=0..6`. Seven identical pieces; no Master or temples. Public objectives: two mirrored scoring squares on y=3 each give +1 score at own-turn start; two mirrored mana squares, one each on y=2/y=4, give +1 mana each at own-turn start within cap. Layout is randomized once per match and included in views. Blue uses `(dx,dy)`, Red `(-dx,-dy)`. Use the updated 16 Appendix offsets (Tiger forward 3; Frog/Rabbit sideways 3; remaining patterns unchanged).
3. Movement jumps; friendly landing illegal, enemy landing eliminates. Shared 16-card movement deck, two cards each: played → return → shuffle → draw (same card may return immediately). Shared 24-skill deck, one copy per effect, three skills each: successful cast → permanent spent pile → replacement if available. No transit card or draw on skip. Start mana 2, gain 1 each own-turn start including first, cap 5. Turn: optional movement → optional one skill → End Turn. Optional passing is legal. Each enemy eliminated gives 1 score. Eliminate all opponents or reach the configured score target (default 8) to win; otherwise 60 completed player turns draws. Elimination has priority over score, both before the draw check. No new turn/rewards after a turn-limit draw. See spec for atomic multi-target effects and Foresight.
4. Authoritative server owns decks, hands, RNG and phases. Validate before mutation, including RNG. Send full recipient-specific views after accepted actions, start and reconnect; never send full authoritative state containing opponent skills, hidden deck order or seed. Rejections change nothing. Foresight preview is private and commits the player to resolving its pending choice. Step 3 documents the live message/view contracts; legacy gameplay messages have been removed.
5. Thread model: accept loop → handler pool → **one single-thread executor per `MatchSession`** => game logic lock-free. Shared registries via `ConcurrentHashMap`. Per-client writes only from its handler (single-writer). Shutdown hook for clean exit.
6. Serialization: one object per message via `ObjectInput/OutputStream`, `flush()` + `reset()` every write, `serialVersionUID=1L`, `final` records, sealed `Message`.
7. Heartbeats: `Ping` every 30s, `soTimeout` 90s. Reconnect: token in `LoginResponse`, `OpponentLeft(60)`, `ReconnectRequest` restores the recipient view and pending phase/choice without extra mana/draw; grace expiry = forfeit.
8. DB: single shared SQLite `Connection` under monitor. All SQL via `PreparedStatement`. Match recording (insert + Elo/W/L) is one transaction with rollback. Elo K=32 zero-sum in `core.Elo`. PBKDF2 hashes. Preserve legacy STONE/STREAM records; new results use ELIMINATION/SCORE/FORFEIT/DRAW. Version/turn-count storage migration is specified in Step 3 before persistence changes.
9. Client EDT: socket reader parses then `SwingUtilities.invokeLater` to `ClientModel`; `ClientModel` fires `ClientModelListener`; panels are observers — never touch socket, never block EDT on I/O. Board drawn from own side (Red flips canonical coords).

10. Balance configuration: load validated `.properties` outside core, inject an immutable per-match snapshot; no mid-match hot reload. Runtime card text uses those definitions, not baked artwork. Practice bots get only permitted player information.

## Where to find things

- `docs/handoffs/step-06-readiness.md` — cleanup inventory and validation before Step 6.

- `docs/step-03-engine-contracts.md` — target state/action/view/targeting APIs, retry/privacy rules, configuration and DB migration.
- `docs/examples/game-balance.properties` — complete loader template; not active runtime configuration.
- `docs/architecture.md` — package map, thread/protocol/DB/UI detail, patterns, risky areas.
- `docs/agent1-pipeline.md` — step-by-step implementation workflow (follow it).
- `docs/agent2-testing-pipeline.md` — independent review workflow + severity taxonomy.
- `src/main/java/onitama/{core,net,server,db,client/{state,ui,bot}}`
- `src/test/java/onitama/` — `core/*`, `db/*`, `net/*`, `server/*`

## Workflow

- Coding task → follow `docs/agent1-pipeline.md` exactly.
- After Agent 1 finishes → Agent 2 follows `docs/agent2-testing-pipeline.md` (read-only review).
- Fixes → Agent 2 targeted re-check. Done when blocking defects resolved & required tests green.

## Rules for agents

- Explainable code: short methods, Javadoc on public types/methods (1-3 lines), comments explain *why*.
- No dead/commented-out code, no `System.out.println` (use `java.util.logging`), no fabricated numbers.
- Prefer existing patterns; no new abstractions without reason; no unrelated refactors.
- Do not claim success without `mvn test` evidence.
