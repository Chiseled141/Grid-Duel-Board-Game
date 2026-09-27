# Report Skeleton — GroupXX_OnitamaOnline_Report

This file is a **skeleton**: it lists what to write under each section
(heading structure per Instructions.md §11) with `TODO(team)` placeholders.
The team writes the actual prose — a pre-written report would endanger the
Compilatio similarity threshold (≤ 20 %). Submit the finished report as
`GroupXX_OnitamaOnline_Report.docx` via Compilatio and the LMS.

---

## 1. Title

* Title: "Onitama Online — a networked board game with an authoritative Java server" (adjust).
* Group number, course, academic year, instructor, members with student IDs.
* TODO(team): final title wording, date.

## 2. Abstract

* 150–200 words: what the system is (two-player Onitama over TCP with an authoritative server and Swing clients), the headline technologies (sockets + object serialization, thread-confined match sessions, SQLite/JDBC, Swing), and the headline measured result from docs/EXPERIMENTS.md.
* TODO(team): one real measured number (e.g. p95 latency for 5 concurrent games).

## 3. Keywords

* Suggest: multiplayer game, client/server, TCP sockets, Java serialization, concurrency, SQLite, JDBC, Elo, Swing, load testing.
* TODO(team): finalize 5–7 keywords.

## 4. Introduction

* Why Onitama: small rule set, deep strategy, no hidden information — ideal for demonstrating a clean authoritative-server architecture rather than game-scale complexity.
* What "authoritative server" buys: cheating resistance, desync-freedom, single point of truth for rules and persistence.
* One paragraph on how the work was split among members.
* TODO(team): team split paragraph, motivation specifics.

## 5. Problem Definition

* Problem: playing a board game across machines needs state synchronization, identity, ratings, and resilience to disconnections; ad-hoc peer-to-peer designs leak cheating and desync risks.
* Functional requirements (numbered): register/login; create/join lobby by code; play a full Onitama game with live state broadcast; record matches with Elo; persist replays; leaderboard; reconnect after drop.
* Non-functional requirements: local run out of the box; latency low enough for turn-based play; accounts safe (salted hashes, no plaintext); server must survive malformed input; tests runnable offline.
* Scope: no web/mobile clients, no matchmaking beyond room codes, no spectator mode, no chat (see README "Known limitations").
* TODO(team): finalize requirement numbering and any course-specific scope notes.

## 6. Requirements

* Traceability table: requirement ID → where implemented (class) → where tested (test ID).
* Point at README §Tests and the test IDs UT01–UT09, IT01–IT02, LT01.
* TODO(team): fill the traceability table.

## 7. System Architecture

* Component diagram: Client (Swing) ⇄ TCP object serialization ⇄ Server (accept loop, client pool, lobby, match sessions) ⇄ SQLite; replay files on disk.
* Data flow of a move: UI click → ClientModel → outgoing queue → writer thread → server handler → MatchSession executor (validate, apply, broadcast) → both clients' reader threads → EDT model → panels.
* Explain the one-single-thread-executor-per-match model and the single-writer send rule.
* TODO(team): redraw the diagram with your own tool, add a sequence diagram for one move and for reconnect.

## 8. Design

* Class responsibilities: `core` (Board, Card, RulesEngine, GameState…), `net` (sealed Message + 24 records), `server` (GameServer, ClientHandler, LobbyManager, MatchSession, SessionRegistry, MatchPersistence), `db` (Database, UserDao/MatchDao + SQLite impls), `client` (OnitamaClient, ClientModel, panels).
* OOP: encapsulation via package-private mutators in core; polymorphism via the sealed message hierarchy; interfaces for DAOs; composition everywhere (no inheritance hierarchies beyond the exception tree — explain why).
* SOLID: SRP (rules/networking/persistence/UI packages), DIP (server depends on UserDao/MatchDao interfaces; ClientModel on ServerConnection), OCP (new message = new record in the permits clause), ISP (narrow DAO and connection interfaces), LSP (InMemory/SQLite DAO equivalence argument — note the in-memory DAO was removed after M5; describe the interface contract instead).
* Design patterns: Strategy (cards as immutable strategy objects), Observer (ClientModelListener), Factory Method (Main dispatch), Visitor-lite via a functional handler registry (why we rejected instanceof chains).
* Error handling: OnitamaException hierarchy → IllegalMove / Protocol / Authentication / Persistence; UI shows friendly messages, server logs stack traces.
* TODO(team): class diagram of core + server, and the message-protocol table from the brief's Section 4 (adjusted for the two extra messages, see docs/DESIGN_DECISIONS.md).

## 9. Implementation

* Technical details worth explaining, each with a file/method reference:
  - card rotation for Red (Card.destinationsFrom),
  - mandatory pass detection (RulesEngine.mustPass) and auto-pass in the client,
  - the ObjectOutputStream back-reference pitfall and the per-message reset() (a genuinely earned lesson — tell it),
  - thread-confined match state with zero locks,
  - PBKDF2 password hashing format,
  - the one-transaction match record + Elo update (Database.inTransaction),
  - replay text format and validation-on-load,
  - heartbeat design (client ping 30 s, server soTimeout 90 s),
  - reconnect token flow.
* TODO(team): pick 4–6 of these with short code excerpts (max ~10 lines each) and explain them in your own words.

## 10. Technical Details

* Concurrency model summary: thread inventory (accept, client pool 32, match executors, timers, reader/writer per client) and what state each touches; why races cannot occur (ConcurrentHashMap registries, thread confinement, monitor-guarded sends).
* Networking: framing = one serialized object per flush; why full-state broadcasts keep clients simple.
* Database: schema (users, matches), PreparedStatement everywhere, transactions, FK enforcement.
* TODO(team): fill in the actual thread counts observed in E3 as evidence.

## 11. Testing

* Test inventory table: ID (UT01…LT01) → what it proves → result. Map to the classes in src/test/java.
* Explain the test strategy: pure-core unit tests, DB tests on temp files, ephemeral ports, scripted full-game integration test with a seeded deck, bot smoke run.
* TODO(team): run `mvn test`, paste the summary line (e.g. "Tests run: 116, Failures: 0, Errors: 0"), note the JDK used.

## 12. Experimental Results

* Present docs/EXPERIMENTS.md's measured tables (E1, E2, E3) as charts/tables.
* One chart: p50/p95 latency vs. number of concurrent games; one table: games/minute per configuration.
* Interpretation: what the numbers say about the single-connection SQLite choice and the per-match executor model.
* TODO(team): ALL numbers from real runs; three repetitions, median; never invent values.

## 13. Discussion

* Why the architecture is appropriate for the problem (authoritative server, full-state broadcasts, thread confinement).
* Trade-offs actually made (serialized DB access, no partial-state protocol, rematch-parked sessions) and their measured cost.
* Threats to validity of the measurements (same machine, JVM warmup, SQLite file caching).
* TODO(team): write this after the experiments.

## 14. Novelty and Contributions

* Baseline: the board game Onitama exists; no official online Java implementation was used or copied.
* Our contributions: 1) authoritative match-server design with thread-confined sessions and full-state broadcasts; 2) replay format validated by re-simulating through the rules engine; 3) measurement harness producing reproducible CSV evidence; 4) the serialization-snapshot fix and its regression test as a documented pitfall.
* TODO(team): confirm the list, add anything you built beyond this skeleton.

## 15. Limitations

* Copy README "Known limitations" and expand: no matchmaking queue/spectators/chat, Elo without floors/decay, single-connection SQLite, replay viewer Blue-side only, manual server resource sampling.
* TODO(team): finalize.

## 16. Future Work

* Ideas: automatic matchmaking by rating, spectator mode, NIO or virtual threads for the client pool, a web client, Elo floors/provisional ratings, server resource sampling built into the harness.
* TODO(team): pick 3–4 with one sentence each.

## 17. Conclusion

* What was built, what was learned (thread confinement vs. locks; serialization pitfalls; transactional persistence), and whether the requirements of §5 were met.
* TODO(team): 150 words with one measured number.

## 18. References

* Onitama, Shimpei Sato, Arcane Wonders (2014) — official rules.
* The two independent open-source Onitama implementations the card data was cross-checked against: TODO(team) — list the actual repository URLs.
* Oracle Java documentation: Object Serialization Specification, java.net, javax.crypto (PBKDF2WithHmacSHA256), java.util.logging.
* SQLite JDBC driver: org.xerial/sqlite-jdbc (version 3.46.1.3).
* Course materials: Instructions.md, Rubric.md, Topics.md.
* TODO(team): verify every citation and add access dates.

## 19. Appendix

* A: full card table (16 cards with offsets) — copy from the brief's Appendix A.
* B: network protocol message table.
* C: how to build/run/test (condensed README).
* D: database schema DDL.
* TODO(team): keep appendices for material that would break the flow of the main text.
