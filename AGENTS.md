# AGENTS.md — Deck Duel

> Read this file at the start of every task. It is the harness entry point and authoritative target rules.

## Current state — read this first

The old chess/piece-and-skill game was removed at the user's request; do not restore it. The card game is implemented on the rules below with the catalog `src/main/resources/cards/catalog.txt` (`cards-neutral-4`, see `docs/card-catalog.md`): the approved 35-Unit library and 10 neutral Spells. Landmarks and the Sabotage Spell were removed at the user's request (2026-10-21). Some numbers and edge cases are **provisional implementation values** (see "Provisional implementation values" below), so balance is not final.

What works: the medieval menu and music, login/registration, automatic server-built 45-card decks (no deck builder), Balatro-style row formations, hosted rooms, the matchmaking queue, practice against an easy (greedy) or hard (planning) server-side bot, a Match history page, reconnect within a grace period, rematch, step-by-step scoring traces with synthesized sounds (Settings → Game sounds), card faces drawn from Java code for all 35 Units and ten Spells (`PixelCardFace` / `PixelSpellArt`), with coordinated nation cloth/shield palettes and distinct crests; all ten neutral Spell illustrations and the shared card back are pixel drawing data in `PxlCardArt` (no image I/O); no card images ship in the jar, and the `demo`/`bots` commands, which play real matches through the protocol. The leaderboard/rankings page was removed at the user's request; Elo ratings have also been removed entirely; retain match history and win/loss records.

See `docs/claude-handoff.md` for boundaries and what remains open.

**Implemented rules (2026-10-21):** each player is dealt ten cards as the match opens, and every
later round deals ten fresh cards; there is no setup choice. Every player's draw deck is all 35
Units (seven nations × Pikemen 2, Bowmen 3, Riders 4, Knights 5, Axemen 4) plus one copy of
each of the 10 neutral Spells: 45 cards, independently shuffled by the server. Unit rows have seven
spaces; ten plays per player per round (a voluntary skip uses one); no automatic replacement draw;
three manual draws per round; full rows do not end the round. Each row's best Balatro-style
formation adds bonus points and a multiplier (§5.3). Landmarks, Sabotage, Chaplain, Envoy, the
national Spells, the eight-space row and the 37-card deck are retired. The protocol is version 12.

## Development requirements

- Select technologies for an actual implementation need. Reflection, dynamic proxies, bytecode/AOP, NIO, and functional programming are optional. Do not add technologies or game features merely to increase complexity.
- Keep class responsibilities focused and dependencies clear. Use encapsulation, abstraction, interfaces, composition, and polymorphism; apply inheritance, SOLID principles, and design patterns where appropriate. Handle exceptions at suitable boundaries.
- Keep technical documentation accurate about module responsibilities, dependencies, and data/control flow. Identify unfinished behavior explicitly.
- Test inputs, expected results, actual results, and pass/fail status. Include unit, integration, and system scenarios appropriate to the GUI, multiple clients, concurrent state, networking, and database transactions. Retain reproducible evidence of executed checks.
- When evaluating performance, record the environment, workload, method, and actual measurements for relevant metrics such as response time, GUI responsiveness, concurrent-client handling, or error rate. Do not claim an improvement without supporting measurements.

## Stack and constraints

- JavaFX is allowed without requiring migration of existing Swing screens.
- Java 17 target, one Maven module. Runtime dependencies may include sqlite-jdbc and JavaFX (OpenJFX); use JUnit Jupiter for tests.
- Desktop UI: the menu and history are Swing; the match table is JavaFX, shown by `FxScreens` in its own window at the Swing frame's bounds. JavaFX is explicitly allowed for UI work, including Swing interoperability. Keep raw localhost TCP sockets and Java object serialization. No other new frameworks or cloud services.
- `mvn package` produces `target/deck-duel.jar`, dispatched by `deckduel.Main`.
- Preserve the existing menu artwork, layout, typography, animation, music, and navigation. The Atlas section, map assets and related tools were removed at the user’s request. The Deck menu and deck builder are also removed; do not restore these sections.
- Explainable code, short methods, concise Javadoc on public types/methods, comments that explain why. No dead code or unrelated refactors. Use java.util.logging.

## Commands and checks

```sh
mvn test
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
```

Tests use temporary SQLite files and ephemeral localhost ports. Desktop tests require a graphical session. Run `mvn test` after code changes and clean verification for a completed migration; report actual counts. Do not claim the removed game's previous test count as current coverage. Keep account, password, DB rollback/history, win/loss, protocol-filter, menu/navigation, and account/lobby integration coverage.

## Architecture and engine boundaries

- `core` is pure logic, without I/O, Swing, JavaFX, networking, or database access. `core/card` holds the immutable card, effect, row, action and view contracts, `Scoring`, `Rules`, `DeckRules`, the immutable `MatchState`, `GreedyBot` and `PlannerBot`. `EndReason` and `PlayerColor` remain for DB compatibility, not gameplay contracts.
- `cards/CatalogLoader` parses card configuration outside core into an immutable `Catalog` injected into the server; nothing reloads mid-match.
- `client/state/ClientModel` owns account, lobby and match state on the EDT. Socket callbacks hand messages to it through SwingUtilities.invokeLater; panels observe `ClientModelListener`. Never block the EDT for I/O.
- JavaFX screens update their scene graph on the JavaFX Application Thread and marshal updates across Swing/JavaFX thread boundaries. Keep I/O and long-running work off both UI threads; preserve the existing EDT ownership of `ClientModel` unless an explicit architecture change replaces it.
- `client/ui/CardTableView` (JavaFX) is the live match screen over `assets/match-room.png`; it renders only the latest server `MatchUpdate` and never applies a play locally. `ScoreRail` is reusable presentation code; its `Display` is a UI snapshot, not a core state or network contract.
- The protocol is **DECK_DUEL_PROTOCOL:12**, shared through `Message.PROTOCOL_VERSION`; older peers (version 11 had Landmarks and no formations; version 10 has no voluntary skip; version 9 sent deck choices and expected eight-space rows) are rejected. Every match action is a `MoveRequest` carrying a `core.card.Move` (`PlayCard`, `DrawCard`, `KeepCard`, `SkipTurn`); lobby requests carry no deck. The sealed Message hierarchy and explicit deserialization allowlist list every message and every `core.card` record/enum that crosses the wire. Keep serialVersionUID=1L, flush/reset each write, heartbeat Ping every 30 seconds, and 90-second silence limits.
- `server/GameServer` owns an accept loop and a handler pool. `ClientHandler` owns each connection; its output monitor serializes writes. Room directory and account registry remain concurrency-safe. Duplicate login closes the previous connection; stale cleanup must not remove a newer identity or room. Require authentication before lobby, match and history operations. Never log passwords or authentication request records.
- The server owns decks, RNG, hidden hands, validation, scoring and match results. Rejections change nothing. Clients and bots receive only their own `PlayerView`: their own hand and Reconnaissance cards, the opponent cards their own Spy revealed, and the public rows, scores and counters.
- `server/MatchSession` runs each match on its own single-thread executor; timers only post work onto it. Moves quote the view revision; a retried accepted request id returns the current view instead of applying twice. Disconnected seats are held for `reconnectGraceSeconds`, then forfeited. In-match chat has been removed, including UI, client state, server relay and protocol messages. Rematch needs both players (the bot always agrees).
- Keep seeded setup reproducible without revealing live seeds, RNG state, deck order, or opponent hands.
- SQLite uses one shared connection under its monitor and PreparedStatements. Preserve PBKDF2 password storage, historical STONE/STREAM and later result labels, foreign keys, and atomic result insert + W/L transaction with rollback. Retired Elo columns are removed by an idempotent transactional schema migration; preserve accounts, historical match rows, round scores and foreign keys. Card matches store the first seat as blue, the catalog version as `rules_version`, rounds started/completed in the turn columns, and banked round scores in the additive `match_rounds` table; legacy rows are not reinterpreted.
- Preserve client.properties host/port, window, music, reduced-motion, card-input and deck preferences. Obsolete pieceStyle is no longer used or written. Never commit personal DBs, credentials, logs, or generated build output.

## Approved card-game design

This is the target ruleset for the migration. Card names, art, and the full card library are not finalized; do not fabricate card definitions or balance values beyond the values below.

### 5.1 Match and deck setup

**Implemented.** Players never build decks or choose regions. `DeckRules.deck` returns the
45-card composition below for every player; the legacy auto-fill, 37-card validation and deck
messages are removed. Deck entries saved in `client.properties` by older clients are preserved
unchanged but never used.

- Two players play exactly three rounds. Add each player's final round score to their match total; the higher total after round three wins. A tied total is a match draw.
- Randomly choose who starts the first round. After a round with a winner, that round’s loser starts the next round. After a tied round, alternate the starting player from the previous round.
- **Landmarks are removed (user request, 2026-10-21).** There is no Landmark choice or Landmark area. Deal ten cards to each player as the match opens, then begin normal turns. The draw deck and hands contain Units and Control Spells only.
- The Unit library covers all seven nations with the same five Unit types/classes in every nation: seven nations × five types = 35 unique nation/type combinations. Each player's automatic draw deck contains one copy of each of these 35 Units plus one copy of each of the 10 neutral Spells, for 45 cards total.
- Both players receive the same 45-card composition, shuffled independently by the server. Do not add deck-building, region-selection, card-selection or saved-deck setup screens; there is no player selection before the opening hand.
- Each Unit has exactly one region/nationality tag and one class tag. Do not add multi-nation or multi-class Units to this initial design.
- Initial playtest classes and printed points: **Pikemen 2, Bowmen 3, Riders 4, Knights 5, Axemen 4**. Battering Ram replaced Footmen (1) on 2026-10-20, and Axemen (4) replaced Battering Ram (6) for all seven nations on 2026-10-21, both at the user's request. Riders and Axemen print the same points but are different classes; pairs and formations use class tags, never printed points. These values are identical across all seven nations. Units have no individual abilities; their differences come from points, tags, combos and formations.
- Retire Chaplain and Envoy from the target catalog and artwork set; reuse the retained classes' existing art where possible. Do not keep the 49 legacy Units or restore a three-nation restriction.
- Use distinct nation crests and class icons so matching nations and types are easy to recognize. Keep each type's basic card design consistent across nations.
- Spells are neutral: they have no nationality and are not restricted by Unit regions. The approved automatic deck has 35 Units and 10 Spells, one copy of each (the eleventh Spell slot left by Sabotage is open); the legacy 37-card and proposed 21-Unit builds do not apply to the target game.
- There is no spendable resource or play cost. Cards are played for their effects and scoring value.

### 5.2 Round flow, hand, and board

- Each player starts every round with ten fresh cards in hand. Players alternate turns and play one Unit or Control Spell per turn.
- Playing a card does not automatically draw a replacement. This applies to Units and Control Spells.
- Each player has an allowance of three manual draws per round, drawing one card from their own deck per use. This allowance resets to three at the start of each round.
- A manual draw does not use or end the player's turn and does not count toward the ten-card-play limit.
- Draw Spells draw the number of cards specified by their effect (for example, Draw 2 draws two cards). Spell-driven draws do not consume the manual-draw allowance; playing the Spell still uses the normal card play for that turn.
- Beyond the opening deal, cards are drawn only through the manual-draw allowance or a Spell effect, and only while cards remain in the deck.
- Each player has one Unit row with seven spaces. A Unit occupies one row space. A Control Spell uses no space. The seven-space row does not reduce the ten-play allowance.
- Players may play a Control Spell while their Unit row is full. A Unit played to a full row replaces one Unit already there; the replaced Unit leaves play and no longer contributes to the score or combos.
- Every card play or voluntary skip counts toward a limit of ten plays per player per round, including Spells and replacements.
- Filling one or both Unit rows does not end the round. The normal round ends when both players have used ten plays. Players with full rows may use their remaining plays on Spells or Unit replacements. Empty-hand handling is implemented provisionally (see "Provisional implementation values").
- Between rounds, clear both Unit rows, reshuffle the draw decks, and deal ten fresh cards to each player instead of carrying over the previous hand.
- **Skip turn (approved 2026-10-20):** on your turn, you may skip instead of playing a card. This uses one of your ten plays for the round, keeps your hand and board unchanged, and advances the turn normally. It does not draw, discard, trigger a Spell effect, or spend a manual draw. Finish any pending Reconnaissance choice first. A skip can end the round when neither player has an action remaining. A player whose hand is empty and who cannot draw still has their turn passed automatically under the provisional handling below, keeping unused plays.

### 5.3 Scoring and Unit combos

- Score is derived from the cards and effects currently active on each player’s row. The displayed score updates live as cards are played, replaced, disabled, or otherwise affected. No points are permanently banked merely because a card was played.
- At the end of a round, bank each player's final score once into their match total. A tied round adds equal points and does not end the match. After round three, the higher cumulative total wins; a tied total is a match draw.
- Each active Unit contributes its printed 2–6 points.
- Evaluate every pair of active Units once. For each pair:
  - Shared region tag(s), but no shared class tag: +3 combo points.
  - Shared class tag(s), but no shared region tag: +3 combo points.
  - At least one shared region and at least one shared class: +9 total combo points for that pair, replacing the separate +3 bonuses.
  - Multiple overlapping tags within the same category do not repeat that category’s bonus. A Unit matching several different Units can earn a separate pair bonus against each one.
- Combos are based on the Units currently on the row. Replacing or removing a Unit removes its printed points and all pair bonuses involving it; remaining combos are recalculated.
- Having all seven nations represented by active nationality tags on your current Unit row earns a separate base combo bonus. The exact bonus value and how it combines with other scoring effects remain deferred; do not invent them. Removing a required Unit or disabling its nationality tag breaks this condition.
- A disable lasts until the round ends unless removed by a clearing effect. The Spell text defines what it suppresses: printed points, region/class tags, combo contribution, or another explicitly named ability.
- **Formations (approved 2026-10-21; values provisional for balance):** like a Balatro poker hand, only a row's best formation counts. Its bonus joins the Unit and pair points and the sum is multiplied: row score = (Units + pair combos + formation bonus) × multiplier, then the all-seven-nations bonus is added unmultiplied. Units with disabled tags do not count toward a formation. Weakest to strongest: Company (3 Units of one class) +10 ×1; Retinue (3 of one nation) +10 ×1; Two Companies (2 + 2 of two classes) +15 ×1; Two Banners (2 + 2 of two nations) +15 ×1; Three Companies (2 + 2 + 2 of three classes) +25 ×1; Three Banners (2 + 2 + 2 of three nations) +25 ×1; Full Regiment (3 of one class + 2 of another) +20 ×2; Full Muster (3 of one nation + 2 of another) +20 ×2; Regiment (4 of one class) +25 ×2; Royal Line (one of each class) +30 ×2; Banner Host (all 5 of one nation) +40 ×3. Values live in `core/card/Formation`.

### 5.4 Landmarks (removed)

Landmarks were removed from the game at the user's request on 2026-10-21, together with the
Sabotage Spell that disabled them. Do not restore Landmark offers, areas or effects.

### 5.5 Control Spells

- A Control Spell has no printed score, uses no row space, resolves one effect, and is then discarded.
- Spells may affect either player, as specified by each card.
- Some Spells may remove or disable Units or affect points or combos. Every Spell must have one clear effect with explicit target, score impact, and duration where relevant.
- A removed Unit leaves the row. Its printed points and any pair combos involving it no longer count. A disabled Unit remains in its space; its contribution is determined by the card’s effect text.
- Spells remain playable while a player’s row is full, subject to the ten-play round limit.

#### Accepted additional Spells (implemented)

These effects (except the removed Sabotage) are implemented in the neutral Spell library alongside Disable Tags, Disable
Points, Destroy Unit, Random Discard, Random Steal and Draw Two. Edge cases marked open below are
implemented provisionally; do not treat them as approved.

- **Exchange:** Discard one card from your hand, then draw two cards from your deck, subject to cards remaining. These draws do not consume the manual-draw allowance.
- **Reconnaissance:** Privately look at the top three cards of your draw deck, choose one to add to your hand, and put the other two on the bottom of that deck. This Spell-driven draw does not consume the manual-draw allowance. The opponent does not see the inspected cards. Bottom-card ordering and behavior when fewer than three cards remain are not yet specified.
- **Sabotage:** removed with the Landmarks (2026-10-21). Its deck slot is open until a new Spell is designed.
- **Spy:** Reveal three random cards from the enemy's hand to the caster only. The cards remain in the enemy's hand; the rest of that hand stays hidden. Behavior when fewer than three cards remain is not yet specified.
- **Clear disable (working label):** Permanently remove the current disables on one friendly Unit's printed points and tags, restoring their contribution while keeping the Unit on the row. The cleared disables do not return at the end of the turn. This clears existing disables rather than granting immunity to later disable effects. Do not add Recall's return-to-hand effect.
- **Recall is not selected.** Do not implement it as part of these additions.

### 5.6 Open design work

The following content still needs to be designed before implementation can be considered complete:

- The Unit library and the 10 Spell names and effect directions are settled (§5.1, §5.5, catalog `cards-neutral-4`). An eleventh Spell to replace Sabotage is open. Further cards are deferred; do not invent them as part of unrelated work.
- Any additional card-specific combo effects or interactions. The base pair-scoring rule in §5.3 is already adopted: +3 for a shared region only, +3 for a shared class only, and +9 when both match.
- Future balance adjustments after playtesting, including the formation values. The initial five classes, points and 45-card composition are defined in §5.1; do not treat them as undecided.
- Final balance and score display details beyond the adopted score rules above.
- The base all-seven-nations combo bonus value and its interaction with pair scoring and multipliers.
- Empty-hand and round-ending behavior when a player exhausts their manual draws, including interactions with random discard and steal; whether manual draws are restricted to the player's own turn. Do not invent these rules during implementation.

Do not silently settle deferred content by adding made-up cards or effects. Ask the user or present a clearly labeled proposal.

The deck/class correction does not approve other unanswered proposals: the seven-nation bonus
value, empty-hand handling and remaining edge cases stay open unless explicitly confirmed separately.

### Provisional implementation values

Selected by the user during implementation (2026-10-19) so the game could run; they are
labelled provisional in the catalog, code and docs and remain open design items above. Change
the numbers in `Scoring.ALL_NATIONS_BONUS` or `Formation`.

- **All seven nations:** +15 points while all seven nations are active on the row; added after
  the formation multiplier, never multiplied.
- **Empty hand:** a player who has no card in hand and cannot draw (no manual draws left or an
  empty deck) is skipped; their unused plays are kept. A player with an empty hand but a draw
  available may draw or voluntarily skip (using one play). The round ends once neither player can act.
- **Manual draws** only on the player's own turn, not while a Reconnaissance choice is pending.
- **Spy / Reconnaissance with fewer than three cards:** reveal or look at as many as there are;
  Reconnaissance's other cards go to the bottom in their original order. The caster keeps
  seeing Spy-revealed cards while they stay in the enemy hand, until the round ends.
- **Exchange** targets another card in the caster's hand; with no other card it is cast with no
  effect, like any Spell without a legal target.
- **Random Discard / Steal** on an empty enemy hand do nothing but still use the play. The
  discarded or stolen card is shown to both players.
- **Clear Disable** targets one of your Units with a disabled part and clears both points and tags.
- **Disable Tags / Disable Points / Destroy Unit** target enemy Units only.

## Implementation direction

Work in bounded changes. Build the new shared core and meaningful rule tests, then new protocol/server matches, additive persistence support, and the JavaFX match screen. Restore practice, bots, demo, reconnect and rematch against that same new core. Do not claim gameplay complete while any route is a placeholder.

The board should use the existing room as the full background, with two enlarged seven-space Unit rows. Resting the pointer on a row card shows a large preview; clicking a Spy-revealed card opens a large read-only card inspector. Inspection uses only the latest recipient-visible view, and Spy inspection closes when the card is no longer revealed. The implemented score rail follows the approved Balatro-style composition, with the row's formation and multiplier heading the breakdown; see `docs/design/card-table-design.md`.

Cards beyond the current catalog remain deferred. Do not invent names, effects, targets or balance values as settled design. Present a clearly labeled proposal or ask for the missing card definitions.
