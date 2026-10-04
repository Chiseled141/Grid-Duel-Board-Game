# AGENTS.md — Harness for Onitama Online

> Read this file at the start of every task. It is the only harness entry point.

## Stack & hard constraints

- Java 17, Maven single module. Only deps: `org.xerial:sqlite-jdbc` + `org.junit.jupiter:junit-jupiter` (test). Swing for UI, raw `ServerSocket`/`Socket` + Java object serialization for networking. No Spring/JavaFX/Netty/Gson/Lombok/cloud SDKs.
- Everything runs on localhost. `mvn package` → `target/onitama.jar` dispatched by `onitama.Main` on `args[0]`: `server` | `client` | `bots` | `demo`.
- `ONITAMA_SPEC.md` (renamed from `ONITAMA_AGENT_BRIEF.md`) is the authoritative spec (rules, protocol, DB schema, test inventory, Appendix offsets). Treat it as ground truth.

## Commands

```bash
mvn test          # all tests (UT01-UT09, IT01-02, LT01, PracticeBotTest) — must be green after every change
mvn clean verify  # clean shade build, 125 tests expected
mvn package       # builds target/onitama.jar
java -jar target/onitama.jar demo --seed 42
java -jar target/onitama.jar server --port 5555 --db data/onitama.db
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
java -jar target/onitama.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4
```

Tests use ephemeral ports + temp DB files. Never require a running server.

## Invariants — do not violate

1. `onitama.core` is pure logic: no I/O, no sockets. Shared server↔client verbatim.
2. Board: 5x5, `x` 0-4, `y` 0-4. Blue home `y=0`, Red `y=4`. Master at `(2,0)`/`(2,4)` = Temple Arch. Offsets `(dx,dy)` native to Blue; Red uses `(-dx,-dy)`. Appendix offsets are authoritative; `CardPatternTest` guards them.
3. Pieces jump; friendly landing illegal; enemy landing captures. Card cycle: played → transit, old transit → hand, hands stay size 2. Pass only when zero legal moves, still cycles. Wins: Stone (capture Master) > Stream (Master on enemy arch), 300 half-moves = draw.
4. Authoritative server: clients send `MoveRequest`/`PassTurn`, receive `MoveApplied` (full `GameState`) or `MoveRejected`. Rejection never mutates state. Full-state broadcast each half-move.
5. Thread model: accept loop → handler pool → **one single-thread executor per `MatchSession`** => game logic lock-free. Shared registries via `ConcurrentHashMap`. Per-client writes only from its handler (single-writer). Shutdown hook for clean exit.
6. Serialization: one object per message via `ObjectInput/OutputStream`, `flush()` + `reset()` every write, `serialVersionUID=1L`, `final` records, sealed `Message`.
7. Heartbeats: `Ping` every 30s, `soTimeout` 90s. Reconnect: token in `LoginResponse`, `OpponentLeft(60)`, `ReconnectRequest` restores `GameState`, grace expiry = forfeit.
8. DB: single shared SQLite `Connection` under monitor. All SQL via `PreparedStatement`. Match recording (insert + Elo/W/L) is one transaction with rollback. Elo K=32 zero-sum in `core.Elo`. PBKDF2 hashes.
9. Client EDT: socket reader parses then `SwingUtilities.invokeLater` to `ClientModel`; `ClientModel` fires `ClientModelListener`; panels are observers — never touch socket, never block EDT on I/O. Board drawn from own side (Red flips canonical coords).

## Where to find things

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
