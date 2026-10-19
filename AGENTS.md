# AGENTS.md — Deck Duel

> Read this file at the start of every task. It is the harness entry point and authoritative target rules.

## Current state — read this first

The user explicitly requested removal of the entire old chess/piece-and-skill game before Claude implements the card game. That cleanup is intentional. **The new gameplay is not implemented.**

What works: the existing medieval menu and music, login/registration, local server, hosted-room creation/listing, rankings, Deck region browser, and a local empty card-table preview with `DuelScorePanel`.

What was removed: the old engine, board/chess rendering, movement and skill cards, mana/targeting/Foresight/opening-hand logic, old game configuration and artwork, bots, match-session executor, gameplay protocol, match reconnect/chat/rematch, and tests specific to those retired rules. Do not restore these as a starting point.

Join and Find match return an explicit gameplay-not-ready response. There is no waiting matchmaking queue or live match. Practice → Preview table opens a visual shell without a game. Demo and bots commands exit with status 2. Accounts and historical results remain intact; their database schema and transaction behavior have not been replaced.

See `docs/claude-handoff.md` for the remaining boundaries and `docs/gameplay-migration-brief.md` for the short implementation request. Do not describe target card rules below as implemented.

## Stack and constraints

- Java 17 target, one Maven module. Only sqlite-jdbc at runtime and JUnit Jupiter for tests.
- Swing desktop UI, raw localhost TCP sockets, Java object serialization. No new frameworks or cloud services.
- `mvn package` produces `target/deck-duel.jar`, dispatched by `deckduel.Main`.
- Preserve the existing menu artwork, layout, typography, animation, music, and navigation. Atlas was renamed to **Deck**. The historical map and flags remain region reference assets for that page; they do not imply saved deck selection.
- Explainable code, short methods, concise Javadoc on public types/methods, comments that explain why. No dead code or unrelated refactors. Use java.util.logging.

## Commands and checks

```sh
mvn test
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
```

Tests use temporary SQLite files and ephemeral localhost ports. Desktop tests require a graphical session. Run `mvn test` after code changes and clean verification for a completed migration; report actual counts. Do not claim the removed game's previous test count as current coverage. Keep account, password, DB rollback/history, Elo, protocol-filter, menu/navigation, and account/lobby integration coverage.

## Architecture and future engine boundaries

- `core` is pure logic, without I/O, Swing, networking, or database access. Currently it contains Elo, shared exceptions, and historical result/side identifiers only. `EndReason` and `PlayerColor` remain for DB compatibility, not new gameplay contracts.
- `client/state/ClientModel` owns account/lobby state on the EDT. Socket callbacks hand messages to it through SwingUtilities.invokeLater; panels observe `ClientModelListener`. Never block the EDT for I/O.
- `client/ui/CardTablePanel` is an empty preview over `assets/match-room.png`. `DuelScorePanel` is reusable presentation code. Its `Display` is a UI snapshot, not a core state or network contract. No sample scores or cards represent real matches.
- The current shell protocol is **DECK_DUEL_PROTOCOL:8**, shared through `Message.PROTOCOL_VERSION`. Legacy version 7 peers are rejected. The sealed Message hierarchy and explicit deserialization allowlist contain account/lobby messages only. Keep serialVersionUID=1L, flush/reset each write, heartbeat Ping every 30 seconds, and 90-second silence limits.
- `server/GameServer` owns an accept loop and a handler pool. `ClientHandler` owns each connection; its output monitor serializes writes. Room directory and account registry remain concurrency-safe. Duplicate login closes the previous connection; stale cleanup must not remove a newer identity or room. Require authentication before lobby/ranking operations. Never log passwords or authentication request records.
- New gameplay must define immutable card/state/action/view contracts first. Server owns decks, RNG, hidden hands, choices, validation, scoring and match results. Rejections change nothing. Clients and bots receive permitted player information only.
- When implementing matches, introduce one single-thread executor per match, version gameplay payloads deliberately, and implement privacy, revision/sequence checks, accepted-action retries, heartbeat, reconnect grace, chat validation, and rematch behavior for the new rules. These are future work, not existing shell functionality.
- Keep seeded setup reproducible without revealing live seeds, RNG state, deck order, or opponent hands. Parse/validate future card configuration outside core and inject an immutable per-match snapshot. No mid-match reload.
- SQLite uses one shared connection under its monitor and PreparedStatements. Preserve PBKDF2 password storage, historical STONE/STREAM and later result labels, foreign keys, and atomic result insert + Elo/W/L transaction with rollback. Elo stays K=32, zero-sum. Retained MatchResult/MatchPersistence/MatchDao describe historical storage; design new result metadata through an additive migration, not by reinterpreting old rows.
- Preserve client.properties host/port, window, music, reduced-motion and card-input preferences. Obsolete pieceStyle is no longer used or written. Never commit personal DBs, credentials, logs, or generated build output.

## Approved card-game design

This is the target ruleset for the migration. Card names, art, and the full card library are not finalized; do not fabricate card definitions or balance values beyond the values below.

### 5.1 Match and deck setup

- Two players play exactly three rounds. Add each player's final round score to their match total; the higher total after round three wins. A tied total is a match draw.
- Randomly choose who starts the first round. After a round with a winner, that round’s loser starts the next round. After a tied round, alternate the starting player from the previous round.
- Each player chooses three regions/nations from the Deck region browser. They contribute Unit cards, tags, and their own Landmark and Control Spell library; they provide no separate resource or play bonus.
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

## Implementation direction

Work in bounded changes. Build the new shared core and meaningful rule tests, then new protocol/server matches, additive persistence support, and the Swing match screen. Restore practice, bots, demo, reconnect, chat, and rematch against that same new core. Do not claim gameplay complete while any route is a placeholder.

The board should use the existing room as the full background, with two eight-space rows. The implemented score rail follows the approved Balatro-style composition; see `docs/design/card-table-design.md`. Keep effect scope accurate rather than assuming a universal multiplier.

The card catalog remains deferred. Do not invent names, effects, targets, region Unit assignments, or balance values as settled design. Present a clearly labeled proposal or ask for the missing card definitions.
