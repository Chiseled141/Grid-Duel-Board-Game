# Claude handoff — card game on the neutral catalog

Read `AGENTS.md` first. The old chess-like game is gone; the card game described there is
implemented end to end on catalog `cards-neutral-2` (`docs/card-catalog.md`): the approved
35-Unit library and 46-card automatic deck, 11 neutral Spells and 6 neutral Landmarks. Landmark
numbers, the seven-nation bonus and some edge cases are provisional (AGENTS.md lists them).

## Where things live

- `core/card`: immutable contracts (`CardDef`, `Effect`, `Row`, `RowCard`, `LandmarkCard`, the
  `Move` family `PickLandmarks`/`PlayCard`/`DrawCard`/`KeepCard`/`SkipTurn`, `PlayerView`, `SideView`,
  `ScoreBreakdown`, …), `Scoring` (§5.3–5.4, with Landmarks), `Rules` (legal plays and the public
  board part of every resolution, shared by server, bots and previews), `DeckRules` (the
  46-card automatic deck), `MatchState` (Landmark choice, deals, manual draws, hidden-card
  Spells with seeded server-side randomness, Reconnaissance keep, Spy knowledge, turn passing,
  banking, forfeit), `GreedyBot` (easy) and `PlannerBot` (hard: plays out its known hand, holds
  disruption Spells; 113 wins to 85 against `GreedyBot` over 200 seeded matches on this catalog).
- `cards/CatalogLoader`: parses `resources/cards/catalog.txt` into a validated `Catalog`.
- `server`: `ClientHandler` dispatch, `LobbyManager` (rooms + queue), `MatchRegistry`
  (one match per account), `MatchSession` (per-match executor, privacy, revisions, retries,
  grace, rematch, practice bot and recording), `MatchPersistence` (+ `match_rounds`,
  and per-account history reads for the Match history page).
- `client`: `ClientModel` (match state, request ids, one pending play, in-memory reconnect),
  `CardTableView` (live table, JavaFX: seven-space rows, Landmark areas, Landmark choice and
  Reconnaissance overlays, Draw and Skip turn buttons (D/S), larger hand cards, Spy-revealed cards, Watchtower top card), `ScoreRail` (rail, JavaFX), `CardDetails` (read-only modal for Landmark buffs and current Spy-revealed cards), `TableEffects` (performs a
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
(protocol 11 has no deck field and supports voluntary skips); `MatchSession` builds the same 46-card deck for both seats every
game and shuffles each independently. Deck entries saved in `client.properties` by older clients
are preserved unchanged and never read.

Play → Find match / Create match / Join a game / Practice → Easy bot or Hard bot all start
real matches. Home → History shows the account's recorded matches. The old leaderboard was
removed at the user's request. `demo` runs two bots against a throwaway local server and database; `bots` connects
`--count` bots to a running server.

## Remaining work

- Confirm or change the provisional values (AGENTS.md "Provisional implementation values"):
  Embassy/Guild Hall +3, Armory ≤2 +1, Ruined Keep +2, all seven nations +15, empty-hand
  skipping with plays kept, own-turn manual draws, private random offers of five, and the
  Spy/Reconnaissance/Exchange/Archive edge cases.
- Card-specific combo interactions beyond the base pair rule need new `Effect` types.
- Card artwork: the old images were removed (2026-10-20). The 35 Units use the new art from
  `art/cards/units/` (runtime copies in `assets/cards/runtime/`). Spell, Landmark and card-back
  art is still pending; those use the procedural faces until `<card-id>.jpg` files are added.
  Menu and match-room artwork are retained.
- The hard bot cannot see the opponent's hand, so it does not anticipate replies; a sampling
  or search bot over the opponent's possible cards would be the next step.
- The first opening deal of a session can pause up to about 0.2 s while card faces render for
  the first time (measurements in `docs/design/card-table-design.md`).
- The JavaFX native libraries in the shaded jar are those of the building machine's platform.
- The table shows the Exchange discard and Random Steal/Discard cards as simple card flights;
  there is no dedicated Spy or Reconnaissance animation beyond the cast and the private overlay.
- Reconnect relies on in-memory credentials; a client restart re-enters the held seat by
  logging in again within the grace period.
