# AGENTS.md — Deck Duel Harness and Authoritative Specification

> Read this file at the start of every task. It is the only agent harness entry point.
> README.md is the short human-facing overview; this file is authoritative for agents.

## 1. Project status and authority

The approved game design is now a two-player, Gwent-inspired card duel with Balatro-style scoring synergies. The repository has **not** been migrated yet: the runnable Java application and its tests still implement the former 7×7 piece-and-skill game.

Treat the card-game rules in §5 as the target specification for future implementation. Do not describe them as implemented, and do not replace working legacy routes or features until a bounded migration task changes them. During migration, update this file and relevant tests with each intentional rules change. Preserve unrelated account, lobby, matchmaking, practice, bot, rematch, chat, reconnect, leaderboard, rating, security, and persistence behavior.

This file replaces older gameplay rules, movement-card appendices, skill catalogs, and claims that the card-game migration is complete. The technical constraints below remain authoritative.

## 2. Stack and hard constraints

- Java 17, one Maven module.
- Runtime dependencies are limited to org.xerial:sqlite-jdbc; tests use org.junit.jupiter:junit-jupiter.
- Swing for the desktop UI. Use raw ServerSocket / Socket and Java object serialization for networking.
- No Spring, JavaFX, Netty, Gson/Jackson, Lombok, game engine, animation library, cloud SDK, or cloud service.
- Everything runs on localhost.
- mvn package builds target/deck-duel.jar. deckduel.Main dispatches server, client, bots, or demo from the first argument.
- Use java.util.logging; do not add System.out.println.
- Keep methods short and explainable. Public types and methods need concise Javadoc. Comments explain why, not what the code already says.
- Do not add dead or commented-out code, fabricated values, or unrelated refactors.

## 3. Commands

Run these from the repository root:

    mvn test
    mvn clean verify
    mvn package
    java -jar target/deck-duel.jar demo --seed 42
    java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
    java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
    java -jar target/deck-duel.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4

Tests use ephemeral localhost ports and temporary database files. They must not require a separately running server. After code changes, run the relevant tests and report the actual result. Do not claim a build or test passed without evidence.

## 4. Technical invariants

### 4.1 Core and authority

1. deckduel.core is pure game logic: no file I/O, sockets, Swing, or database access. Server, client legality helpers, practice, and bots share the same rule definitions where appropriate.
2. The server owns authoritative match state, decks, hands, random state, phases, scoring, and results. Clients submit actions; they do not mutate authoritative state.
3. Validate a complete action before state mutation or committing random state. Rejections must leave the match unchanged and must not disclose hidden information.
4. Send recipient-specific player views after accepted actions, match start, and reconnect. Never serialize the full authoritative state to a client. Opponent hands and undrawn deck order remain private.
5. Practice bots receive only the information their player is allowed to know.

### 4.2 Threads and networking

- Use an accept loop, handler pool, and one single-thread executor per MatchSession; game logic is thread-confined and needs no internal locks.
- Shared registries use ConcurrentHashMap.
- Each client socket has one writer. Flush and reset the ObjectOutputStream after every message.
- Keep the sealed Message hierarchy, final message records, and serialVersionUID = 1L. Maintain a strict deserialization allowlist and reject unknown or foreign objects safely.
- Preserve the existing ping interval, socket timeout, clean shutdown hook, reconnect token, and reconnect grace behavior.
- Send Ping every 30 seconds and use a 90-second socket timeout. A disconnect parks the match and notifies the opponent with a 60-second reconnect grace. The reconnect token is issued in LoginResponse; expiry is a forfeit.
- Reconnect restores the same permitted view and pending phase/choice. It must not repeat a draw, score, payment, or other turn-start/action effect.
- Gameplay protocol changes must update client and server together. The current protocol version is DECK_DUEL_PROTOCOL:7 for the legacy game. The card-game migration must use a deliberate protocol-version change; mixed incompatible gameplay peers must fail clearly.
- Keep per-player action sequence/revision checks and retry-safe accepted-action receipts when replacing gameplay messages. Exact new payload names and fields must be designed before implementation; do not invent parallel contracts.
- Preserve login/registration, lobby listing, room create/join, leaderboard, rematch, matchmaking, ping/pong, and error reporting. Find-match remains a server-owned FIFO queue on the same local server; cancellation and disconnect remove waiting players, and no fake bot opponent or ETA is shown.
- Preserve ephemeral in-match chat if it remains in the current routes: derive sender identity from the authenticated connection, reject stale/blank/oversized/control-character messages, rate-limit sends, and do not persist chat or let it change gameplay revisions.

#### Match and message lifecycle

- The accept loop may not perform gameplay or database work on the accept thread. A connected handler owns its socket and its single-writer output path.
- Each MatchSession owns exactly two player slots, the authoritative game state, per-player sequence receipts, connection/reconnect status, and one single-thread game executor. A match mutation runs on that executor; shared account/room/queue registries remain concurrency-safe.
- After an accepted request, return the latest full view to each participant. A rejection returns a useful error only to the requester and changes no authoritative state, random state, sequence receipt, hand, score, or phase.
- Reconnect authenticates the existing token, attaches the connection to the existing participant slot, and sends that player’s permitted current view. It does not create a new match, redraw a hand, replay an action, or repeat a round-start effect.
- When a disconnect grace expires, resolve the match as a forfeit once. A stale handler must not send into a newer session for the same account or clear the newer reconnect token.
- Matchmaking and room admission share a short synchronization boundary so a queued account cannot also enter a hosted room. Do not hold that boundary while running gameplay, network I/O, or database transactions.
- If in-match chat remains enabled, it is outside gameplay sequencing: it must not change turn, revision, score, RNG, or reconnect deadlines. The current UX contract limits messages to 280 UTF-16 code units, rejects blank/control/format/line-separator text, rate-limits each sender to one accepted message per 500 ms, and keeps at most 200 accepted messages in local client history. Chat is ephemeral and is not replayed from storage after reconnect.

#### Target gameplay message semantics

The following are required behaviors for the future protocol, not existing message class names. Specify the exact records, fields, sequence rules, and allowlist in the migration task before implementing them.

| Operation | Required behavior |
| --- | --- |
| Match start | Send each player the match identity, starting player, public rule/card definitions, and that recipient’s initial private hand/view. |
| Play a card | Submit one card identity and any typed target/choice required by that card. Server verifies ownership, turn, play limit, row capacity/replacement choice, and effect legality. |
| Accepted play | Commit exactly once, apply card/effect/score changes, draw one replacement if available, advance turn, and send fresh recipient-specific views plus public action details. |
| Rejected play | Return a useful error only to the requester. Do not spend a card, draw, change score/board/turn, or commit random state. |
| Round completion | Send both players the final row-based scores, round winner or draw, and next-round setup if the match continues. |
| Match completion | Send the match winner or draw and final ratings/results. Persist the result once. |
| Reconnect | Restore the same match, turn, hand, current score, row, and any pending effect/choice without resubmitting or repeating an accepted play. |

Full recipient views are sufficient; deltas are optional. Public action details may reveal a played Unit, Landmark, or Spell only when the card’s play is public. Never infer or announce hidden draws. A spell’s target choices and result must be validated by the server even if the client only offers legal choices.

### 4.3 Database and account security

- SQLite is accessed through one shared Connection guarded by a monitor. Use PreparedStatement for all SQL.
- Recording a result, including the match row and both players’ rating/win/loss updates, is one transaction with rollback on failure.
- Elo remains a pure function in core, K=32 and zero-sum unless a later approved rule changes it.
- Passwords use the existing PBKDF2 approach and are never stored or logged in plaintext.
- Preserve historical match rows and legacy result meanings. Add schema migrations; do not reinterpret old records. Store a rules version for new game results where persistence is changed.
- Inspect the live schema and DAO before persistence edits. The existing account data includes username, password hash, Elo, wins, losses, and creation time. Match history stores both player IDs, optional winner, result reason, legacy turn/move count, ratings after the match, and played time. The card-game migration must add any rules-version or round/play-count data without destroying legacy STONE/STREAM or other existing rows.
- Preserve the single-transaction boundary: insert the result and update both players’ records/ratings together, or roll the whole operation back.

#### Persistence shape to preserve

The existing schema is conceptually:

- users: integer ID; unique username; password hash; Elo defaulting to 1000; wins; losses; creation timestamp.
- matches: integer ID; Blue and Red user IDs; nullable winner ID for a draw; result reason; legacy move count; both post-match ratings; played timestamp.

The historical database may contain STONE and STREAM result reasons. Preserve those values and their meaning. Other existing result reasons, including elimination, score, forfeit, and draw, must not be reinterpreted merely because the new game uses rounds.

Use foreign keys and the current DAO conventions. All SQL values are bound through PreparedStatement; never interpolate user data into SQL. If the new game needs a rules version, rounds won, or played-card count, add those fields through a versioned/additive migration. Existing rows and old result labels retain their historical meaning. Decide the new result vocabulary before changing persistence, then update migration and DAO tests together.
- Keep authentication, lobby admission, room handling, matchmaking, and reconnect races safe. Gameplay remains confined to its match executor.

### 4.4 Swing client

- The socket reader parses messages, then uses SwingUtilities.invokeLater to update ClientModel.
- ClientModel notifies listeners; panels observe it. Panels do not access sockets or block the EDT on I/O.
- Render from the recipient-specific view. Local highlights may use visible information, but server validation is final.
- Animations may not mutate authoritative state or delay network handling. Lifecycle changes must reset transient UI state.
- Keep fallbacks for optional artwork, fonts, and audio. Runtime card text comes from the match’s public definitions, not text baked into images.
- Preserve the current application’s lobby, login/register, leaderboard, loading, practice, reconnect, result, and rematch screens while replacing the in-match board. Do not block the EDT to wait for game/network work.
- The card-game presentation should make the two eight-space rows, both live scores, current player, hand, remaining plays, and public active effects readable. Private hands and any unrevealed deck contents remain private. This is a target UI requirement, not a claim that the current UI already has these screens.
- Keep reduced-motion/accessibility settings and presentation fallbacks where they exist. UI animation is cosmetic and must not delay or determine the authoritative result.

### 4.5 Balance configuration and randomness

- Load and validate balance data outside core, then inject an immutable per-match snapshot. A change affects future matches only.
- Reject malformed supplied configuration with a useful error; do not silently fall back.
- Seeded randomness supports reproducible tests. Never send a live match seed, random state, or undrawn deck sequence to clients or bots.
- Keep game rules and card definitions as data where practical. Do not introduce a general scripting engine for a finite card set.
- The current RulesConfigLoader and docs/examples/game-balance.properties belong to the legacy board/skill game. Do not claim they already describe the new card library. Adapt or replace their data model in a bounded task, with validation tests and immutable per-match definitions.

### 4.6 Existing product routes and regression coverage

- Keep the current local server, Swing client, practice bot, load-test bot fleet, and deterministic demo working through the migration. All routes must eventually use the same card-game core rules.
- Preserve tests for card-deck lifecycle, turn/action validation, match privacy, seeded randomness, message serialization and allowlisting, account/password behavior, SQLite rollback, Elo, server/client integration, matchmaking, chat, bot legality, and reconnect/forfeit behavior.
- Current gameplay and presentation tests assert the legacy board game. Replace those assertions only as their corresponding migration task lands; do not delete unrelated coverage or disable a test to conceal a failure.
- There is no fixed historical test-count target. Report the actual result from the command run.

### 4.7 Runtime configuration and files

- Preserve the current command-line entry points and supported server/client flags while migrating. Server and demo currently accept a balance configuration path; retain a clear override path for the new card definitions.
- Preserve the existing client.properties settings that are still used by the client, including host/port and window or presentation preferences. Inspect the current loader before changing its format.
- Keep user databases and generated bot output in the established data/results locations. Do not commit personal databases, credentials, local logs, generated build output, or machine-specific files.
- Do not parse configuration or perform file I/O from core. Parse and validate at the boundary, then pass immutable definitions into a match.

## 5. Approved card-game design

This is the target ruleset for the migration. Card names, art, and the full card library are not finalized; do not fabricate card definitions or balance values beyond the values below.

### 5.1 Match and deck setup

- Two players play exactly three rounds. Add each player's final round score to their match total; the higher total after round three wins. A tied total is a match draw.
- Randomly choose who starts the first round. After a round with a winner, that round’s loser starts the next round. After a tied round, alternate the starting player from the previous round.
- Each player chooses three regions/nations from the Atlas. They contribute Unit cards, tags, and their own Landmark and Control Spell library; they provide no separate resource or play bonus.
- The game builds a fixed Unit package of 21 Units: seven from each chosen region. A Unit may carry multiple region tags and multiple class tags.
- Each Unit has a fixed printed value from 1 to 5 points, plus its region and class tags.
- Each player chooses 16 non-Unit cards only from the libraries of their three selected regions/nations. Cards from unselected regions are unavailable. The 16 may be any mix of Landmarks and Control Spells, including all of one type. A player may include up to two copies of a given Landmark or Spell.
- Each player’s deck therefore contains 37 cards: 21 Units and 16 non-Unit cards.
- There is no spendable resource or play cost. Cards are played for their effects and scoring value.

### 5.2 Round flow, hand, and board

- Each player starts a round with eight cards in hand. Players alternate turns and play one card per turn.
- After each card play, draw one replacement if a card remains in the deck. This applies to Units, Landmarks, and Control Spells.
- Each player has one row with eight spaces. A Unit or Landmark occupies one space. A Control Spell uses no space.
- Players may play a Control Spell while their row is full. A Unit or Landmark played to a full row replaces one card already there; the replaced card leaves play and no longer contributes to the score or combos.
- Every card play counts toward a limit of ten plays per player per round, including Spells and replacements.
- If both rows fill before both players use ten plays, the round ends. Otherwise, the round ends when both players have used ten plays. A player who fills their row first may still use remaining plays on Spells or replacements.
- Between rounds, clear both rows, reshuffle the deck, and deal eight fresh cards to each player.
- There is no pass action. The row replacement rule and the ten-play limit prevent a full-row player from being stuck without a legal card play.

### 5.3 Scoring and Unit combos

- Score is derived from the cards and effects currently active on each player’s row. The displayed score updates live as cards are played, replaced, disabled, or otherwise affected. No points are permanently banked merely because a card was played.
- At the end of a round, bank each player's final score once into their match total. A tied round adds equal points and does not end the match. After round three, the higher cumulative total wins; a tied total is a match draw.
- Each active Unit contributes its printed 1–5 points.
- Evaluate every pair of active Units once. For each pair:
  - Shared region tag(s), but no shared class tag: +3 combo points.
  - Shared class tag(s), but no shared region tag: +3 combo points.
  - At least one shared region and at least one shared class: +9 total combo points for that pair, replacing the separate +3 bonuses.
  - Multiple overlapping tags within the same category do not repeat that category’s bonus. A Unit matching several different Units can earn a separate pair bonus against each one.
- Combos are based on the Units currently on the row. Replacing or removing a Unit removes its printed points and all pair bonuses involving it; remaining combos are recalculated.
- A disable lasts until the round ends. The Spell or Landmark text defines what it suppresses: printed points, region/class tags, combo contribution, or another explicitly named ability.

### 5.4 Landmarks

- A Landmark has no printed score. It occupies one space and has one clear Joker-style effect.
- A Landmark becomes active as soon as it is played and affects later plays or combos during that round. It remains active until the round ends unless removed.
- Its single effect may award bonus points or apply a multiplier. The card text defines its trigger and whether it affects a Unit’s full score or only combo points.
- Landmark effects stack. Identical copies each contribute their effect. Multipliers multiply together; for example, ×2 and ×3 produce ×6.
- Some Control Spells may remove a Landmark. A removed Landmark stops affecting future/live score calculations; scoring is not banked independently of the current board and effects.

### 5.5 Control Spells

- A Control Spell has no printed score, uses no row space, resolves one effect, and is then discarded.
- Spells may affect either player, as specified by each card.
- Some Spells may remove or disable Units, affect points or combos, or remove Landmarks. Every Spell must have one clear effect with explicit target, score impact, and duration where relevant.
- A removed Unit leaves the row. Its printed points and any pair combos involving it no longer count. A disabled Unit remains in its space; its contribution is determined by the card’s effect text.
- Spells remain playable while a player’s row is full, subject to the ten-play round limit.

### 5.6 Open design work

The following content still needs to be designed before implementation can be considered complete:

- The named Unit, Landmark, and Control Spell libraries, including each card’s exact effect text and legal targets. Card-library design is deferred; do not invent the catalog as part of unrelated implementation work.
- Any additional card-specific combo effects or interactions. The base pair-scoring rule in §5.3 is already adopted: +3 for a shared region only, +3 for a shared class only, and +9 when both match.
- How a chosen three-region set determines the available 21 Unit definitions and how multi-region tags are distributed.
- Final balance and score display details beyond the adopted score rules above.

Do not silently settle deferred content by adding made-up cards or effects. Ask the user or present a clearly labeled proposal.

## 6. Migration workflow

- Read the relevant source and tests before changing code. Work in bounded, reviewable steps; the approved design does not authorize rewriting the entire engine in one change.
- Keep existing login, registration, rooms, find-match queue, leaderboard, practice, bots, rematch, chat, reconnect, Elo, and persistence capabilities unless a user-approved change explicitly affects them.
- Replace obsolete gameplay tests only when behavior is intentionally migrated. Add meaningful tests for each new rule in the same implementation task. Never remove or disable tests just to make the suite pass.
- Preserve security, serialization-filter, concurrency, reconnect, and database-transaction coverage even while replacing gameplay assertions.
- Update both peers and all game routes together: server, online client, practice, bot fleet, and demo. Do not leave a route using legacy rules.
- On a documentation-only change, do not claim the new game is implemented or tested. On code changes, run mvn test; use mvn clean verify for release verification and report actual counts/results.
- Keep changes local-first. No web/mobile client, HTTP/WebSocket service, spectator system, cloud service, or cross-server matchmaking is part of the approved scope.

### Suggested bounded migration order

1. Define immutable Unit, Landmark, Spell, region, class-tag, score, and player-view data contracts in core. Add focused tests for deck composition, tag overlap, pair scoring, Landmark stacking, replacement, disable, and round completion.
2. Add authoritative round/hand/deck/turn state and validate play, draw, replacement, Spell targets, and ten-play accounting. Make score calculation deterministic from the current board and active effects.
3. Define the new protocol records and allowlist, deliberately increment the protocol version, then update the server and client together. Verify recipient-specific privacy, rejected-action atomicity, sequence retries, and reconnect.
4. Migrate persistence additively, preserving old accounts and results. Record the new rule version and enough round data to report the result accurately.
5. Update Swing presentation, practice policy, bot fleet, demo, rematch, and every route to use the shared card-game core. No route may keep the old game after migration is declared complete.
6. Remove obsolete gameplay assets/tests only when no active route references them and the replacement coverage exists. Retain shared account, protocol-filter, concurrency, reconnect, and transaction tests.

### Regression coverage expected during migration

- Setup and deck composition: three selected regions, seven Units contributed by each, 21 Units total, 16 non-Unit cards drawn only from those regions with any Landmark/Spell mix, duplicate caps, and eight-card starting hands.
- Rules: one play per turn; one replacement draw after a play; ten-play accounting; eight row spaces; full-row Spell play and replacement; early round completion when both rows fill; three-round reset.
- Score: printed values, overlapping region/class tags, one combined region+class bonus per Unit pair, multiple matching pairs, live recalculation after replacement/removal/disable, and round/match ties.
- Effects: Landmark triggers, score-vs-combo multiplier scope, multiplicative stacking, duplicate stacking, removed Landmark behavior, one-shot Spell discard, target legality, and effect-defined Unit disable behavior.
- Security and consistency: validation before mutation, no score or draw on rejected actions, private hands/deck order, serialization allowlist, action retries/revisions, reconnect, and deterministic seeded setup.
- Product services: login/password storage, room and match admission, FIFO matchmaking, leaderboard/Elo, rematch, ephemeral chat, bot legality, and atomic database rollback.

Use temporary databases and ephemeral localhost ports. Tests must not depend on a live server, external network, or developer-specific files. Keep the suite meaningful; never lower coverage by removing assertions unrelated to the changed rule.

## 7. Source map

- src/main/java/deckduel/core — shared pure logic; currently contains the legacy board-game implementation.
- src/main/java/deckduel/net — serialized protocol.
- src/main/java/deckduel/server — authoritative sessions and server routes.
- src/main/java/deckduel/db — SQLite persistence and account data.
- src/main/java/deckduel/config — current configuration loading and legacy skill text.
- src/main/java/deckduel/client/state and client/ui — Swing client state and views.
- src/main/java/deckduel/client/bot — practice and bot policies.
- src/test/java/deckduel — core, database, network, server, and client regression coverage.
- src/main/resources — bundled configuration, artwork, fonts, and audio.
- docs/examples/game-balance.properties — current legacy balance template; it is not yet the card-game library.
