# Claude handoff — card game on the neutral catalog

Read `AGENTS.md` first. The old chess-like game is gone; the card game described there is
implemented end to end on catalog `cards-neutral-4` (`docs/card-catalog.md`): the approved
35-Unit library (Axemen replaced Battering Ram on 2026-10-21) and 45-card automatic deck with 10 neutral Spells, scored with Balatro-style
formations. Landmarks and Sabotage were removed on 2026-10-21. Formation values, the
seven-nation bonus and some edge cases are provisional (AGENTS.md lists them).

## Where things live

- `core/card`: immutable contracts (`CardDef`, `Effect`, `Row`, `RowCard`, the `Move` family
  `PlayCard`/`DrawCard`/`KeepCard`/`SkipTurn`, `PlayerView`, `SideView`, `ScoreBreakdown`,
  `Formation`, …), `Scoring` (§5.3: pairs, the row's best `Formation` and its multiplier, the
  seven-nation bonus), `Rules` (legal plays and the public board part of every resolution,
  shared by server, bots and previews), `DeckRules` (the 45-card automatic deck), `MatchState`
  (deals, manual draws, hidden-card Spells with seeded server-side randomness, Reconnaissance
  keep, Spy knowledge, turn passing, banking, forfeit), `GreedyBot` (easy) and `PlannerBot`
  (hard: plays out its known hand, holds disruption Spells; 254 wins to 146 against `GreedyBot`
  over 400 seeded matches on this catalog, 2026-10-21).
- `cards/CatalogLoader`: parses `resources/cards/catalog.txt` into a validated `Catalog`.
- `server`: `ClientHandler` dispatch, `LobbyManager` (hosted rooms), `MatchRegistry`
  (one match per account), `MatchSession` (per-match executor, privacy, revisions, retries,
  grace, rematch, practice bot and recording), `MatchPersistence` (+ `match_rounds`,
  and per-account history reads for the Match history page).
- `client`: `ClientModel` (match state, request ids, one pending play, in-memory reconnect),
  `CardTableView` (live table, JavaFX: seven-space rows, row-card hover preview, Reconnaissance
  overlay, Draw and Skip turn buttons (D/S), larger hand cards, Spy-revealed cards), `ScoreRail`
  (rail, JavaFX, with the formation line), `CardDetails` (read-only modal for current
  Spy-revealed cards), `TableEffects` (performs a
  `PlayAnimator` plan), `FxScreens` (shows the JavaFX window in place of the Swing frame for the
  table; marshals between the EDT and the JavaFX thread),
  `client/bot/CardBot` (headless protocol bot for `demo` and `bots`).

## Routes

The Atlas section was removed at the user’s request (2026-10-20), including its menu entry,
client route, screens, map/heraldry assets, design sources and dedicated export tools.
The card factions and their procedural face colours remain unchanged.

Elo ratings are removed from profiles, history, the UI, match recording and the database.
On database startup, an idempotent transactional migration drops retired rating columns
while preserving accounts, win/loss counters, match history and round scores.

In-match chat was removed entirely: no table controls, client history/callbacks, server relay,
or chat protocol messages remain. Reconnect, errors/notices and rematch remain supported.

The deck builder and Deck menu were removed at the user’s request. No client sends a deck
(protocol 13 has no deck field or matchmaking queue, supports voluntary skips and carries formations); `MatchSession` builds the same 45-card deck for both seats every
game and shuffles each independently. Deck entries saved in `client.properties` by older clients
are preserved unchanged and never read.

Play → Create match / Join a game / Practice → Easy bot or Hard bot all start real matches;
the matchmaking queue was removed at the user's request. Home → History shows the account's recorded matches. The old leaderboard was
removed at the user's request. `demo` runs two bots against a throwaway local server and database; `bots` connects
an even `--count` of bots to a running server. Bots pair up through hosted rooms (one hosts, its
partner joins by code) and play further games as rematches.

## Remaining work

- Confirm or change the provisional values (AGENTS.md "Provisional implementation values"):
  formation bonuses and multipliers, all seven nations +15, empty-hand skipping with plays
  kept, own-turn manual draws, and the Spy/Reconnaissance/Exchange edge cases.
- An eleventh Spell to fill Sabotage's slot is still to be designed.
- Card-specific combo interactions beyond the base pair rule need new `Effect` types.
- Card artwork: all 45 card faces and the shared back now render from Java drawing data.
  The 35 Unit figures, nation palettes and crests remain in `PixelCardFace`. All ten neutral
  Spells and the back are pixel drawing data in `PxlCardArt.java`. `PixelSpellArt` composes catalog names
  and effect captions; full rules remain in the inspector/details. The pxl designs supersede
  Draw Two/Spy's original illustrations and the other eight Spells' painted faces. Legacy
  No card JPEG ships any more. No runtime Node, PNG decoding or asset I/O is
  required for the current catalog.
  Unit figures are the hand-pixel v2 character maps (Axemen with a one-handed axe and round
  shield) inside four-pixel nation borders; earlier iterations are in git history.
- The hard bot cannot see the opponent's hand, so it does not anticipate replies; a sampling
  or search bot over the opponent's possible cards would be the next step.
- The first opening deal of a session can pause up to about 0.2 s while card faces render for
  the first time (measurements in `docs/design/card-table-design.md`).
- The JavaFX native libraries in the shaded jar are those of the building machine's platform.
- The table shows the Exchange discard and Random Steal/Discard cards as simple card flights;
  there is no dedicated Spy or Reconnaissance animation beyond the cast and the private overlay.
- Reconnect relies on in-memory credentials; a client restart re-enters the held seat by
  logging in again within the grace period.
