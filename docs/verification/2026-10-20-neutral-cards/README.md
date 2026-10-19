# Verification — neutral cards and new round rules (2026-10-20)

Environment: Apple M2, macOS 27.0, OpenJDK 26.0.1 running the Java 17 build, Maven 3 offline,
OpenJFX 21.0.5, graphical session (desktop tests open windows).

## 1. Clean build and tests

Command: `mvn -o clean verify` → **BUILD SUCCESS, 142 tests, 0 failures, 0 errors, 0 skipped**,
`target/deck-duel.jar` built. Per class:

| Test class | Tests | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| cards.CatalogLoaderTest | 5 | 0 | 0 | 0 |
| client.ClientSettingsTest | 4 | 0 | 0 | 0 |
| client.MenuMusicAssetTest | 1 | 0 | 0 | 0 |
| client.ScoringSoundsTest | 2 | 0 | 0 | 0 |
| client.bot.CardBotTest | 1 | 0 | 0 | 0 |
| client.state.ClientModelTest | 6 | 0 | 0 | 0 |
| client.ui.AtlasViewTest | 8 | 0 | 0 | 0 |
| client.ui.BattlefieldMenuPanelTest | 5 | 0 | 0 | 0 |
| client.ui.CardArtworkTest | 4 | 0 | 0 | 0 |
| client.ui.CardTableViewTest | 16 | 0 | 0 | 0 |
| client.ui.ClientFramePresentationTest | 1 | 0 | 0 | 0 |
| client.ui.FxScreensTest | 1 | 0 | 0 | 0 |
| client.ui.HistoryPanelTest | 1 | 0 | 0 | 0 |
| client.ui.LobbyMenuTest | 11 | 0 | 0 | 0 |
| client.ui.MenuPetalsTest | 2 | 0 | 0 | 0 |
| client.ui.MenuTitleTest | 1 | 0 | 0 | 0 |
| client.ui.PlayAnimatorTest | 9 | 0 | 0 | 0 |
| client.ui.ScoreRailTest | 3 | 0 | 0 | 0 |
| core.card.GreedyBotTest | 3 | 0 | 0 | 0 |
| core.card.MatchStateTest | 15 | 0 | 0 | 0 |
| core.card.PlannerBotTest | 2 | 0 | 0 | 0 |
| core.card.ScoringTest | 9 | 0 | 0 | 0 |
| db.MatchDaoTest | 6 | 0 | 0 | 0 |
| db.UserDaoTest | 7 | 0 | 0 | 0 |
| net.MessageSerializationTest | 2 | 0 | 0 | 0 |
| net.MessageWireFilterTest | 2 | 0 | 0 | 0 |
| server.AccountLobbyIntegrationTest | 7 | 0 | 0 | 0 |
| server.CardMatchIntegrationTest | 8 | 0 | 0 | 0 |

What the new or rewritten classes check (input → expected; all passed):

- **MatchStateTest (rules, privacy):** Landmark choice (five private offers; wrong count,
  duplicates or another player's offer rejected; picks hidden until both chose) → round one
  deals 10 each with 36 cards left, Landmarks outside the deck; playing → no replacement draw;
  three manual draws keep the turn and use no play, a fourth and an off-turn draw are rejected;
  both rows full → the round continues and a full row replaces; ten plays each → round two
  deals ten fresh cards, draws reset, the same Landmarks stay; an empty hand with no draws →
  the turn passes, plays are kept, the round ends when neither can act; every Spell on its
  target (Destroy removes, Disable Tags suppresses, Sabotage keeps the Landmark but disables it
  until round end, Clear Disable clears both); Draw Two and Exchange draw without using manual
  draws; Random Discard/Steal are seeded and deterministic; Reconnaissance shows three cards to
  the caster only, blocks other moves until one is kept, others go to the bottom; Spy reveals
  three enemy cards to the caster only and no other enemy card reaches the caster's view;
  Archive draws on the first Spell only; Watchtower shows each player only their own top card,
  which is the next drawn; views never carry the opponent's hand or offers, and the view contract
  has no seed, RNG or deck field; same seed → identical match; three rounds bank into the
  match total; forfeit works while choosing.
- **ScoringTest:** pair bonus 3/3/9; the design example 15 → 36; disabled points keep combos,
  disabled tags drop them, clearing restores; all seven nations +15 and broken by a disabled tag
  or a removed Unit; Embassy/Guild Hall +3 on matching pairs, both on a +9 pair, copies stack;
  Armory raises printed 1–2 only and is cancelled by Disable Points; Ruined Keep counts empty
  spaces; a sabotaged Landmark contributes nothing.
- **CatalogLoaderTest:** 35 Units, one per nation × class, Footmen 1 … Knights 5, no Chaplain or
  Envoy; the 11 Spells and 6 Landmarks are neutral; the deck is 46 cards (35 + 11), no Landmarks;
  malformed lines and inconsistent class points are rejected with the line number.
- **CardMatchIntegrationTest (real sockets, temporary SQLite):** hosted room → private offers,
  simultaneous Landmark picks quoting the same revision both accepted, 10-card hands, 36-card
  decks; three rounds recorded atomically with `rules_version = cards-neutral-1`; history from
  the requester's side; a draw before choosing, an off-turn move, a stale revision and an unknown
  card are rejected and a retried accepted move is not applied twice; reconnect keeps hand and
  Landmarks; grace expiry forfeits; a rematch offers Landmarks again; practice against the hard
  bot completes without recording.
- **AccountLobbyIntegrationTest:** a version-9 peer is rejected with `PROTOCOL:10`; unknown room
  codes create no game; authentication is required for every lobby and match request.
- **CardBotTest:** two protocol bots choose Landmarks and play two full matches each.
- **CardTableViewTest (JavaFX):** clicking two offers then Keep sends one `PickLandmarks` (a
  third tick is refused); the Draw button sends `DrawCard` only on your turn; clicking a
  Reconnaissance card sends `KeepCard`; selecting Sabotage then an enemy Landmark sends that
  target; plus the existing play, drag, keyboard, arrangement, result and trace tests.
- **PlayAnimatorTest:** Sabotage → hammer, cracks, no shatter, the Landmark hidden only until the
  cracks finish; manual draws glide face up for the drawer and face down for the opponent.
- **CardArtworkTest:** every catalog card has 320×480 runtime art; retired art is not shipped;
  full-size sources stay out of the build; Spy and Embassy faces are pixel-identical to their art
  inside the card edge (no duplicated title or text overlay).

## 2. System run

`java -jar target/deck-duel.jar demo --games 2` (copy of the jar outside iCloud): two protocol
bots on a throwaway server and database. Output:

```
INFO: Card catalog cards-neutral-1 (Landmark numbers are provisional)
INFO: recorded match M263CFF50E3-1 as id 1
INFO: recorded match MBE7FC0F3FE-1 as id 2
INFO: demo_north won vs demo_south: 170-117 (R1 63-43 R2 74-39 R3 33-35)
INFO: demo_north lost vs demo_south: 162-219 (R1 56-68 R2 48-51 R3 58-100)
```

## 3. Bot strength (measured, not a balance claim)

`PlannerBotTest`: 200 seeded matches on the bundled catalog and deck, seats alternating:
PlannerBot 113 wins, GreedyBot 85, 2 draws.

## 4. Visual review

Rendered with the real `CardTableView` at 1366×800 by `RenderNeutral.java.txt` (a scratch
harness: compile it against `target/classes` and the Maven runtime classpath, run with an output
folder). Frames, checked by eye:

- `1-choose.jpg` — five Landmark offers over the dimmed table, CHOOSE 2 MORE.
- `2-table.jpg` — seven-space rows, both Landmark areas, DRAW 3/3, neutral Spells in hand
  without overlay text, provisional Landmark lines in the score rail.
- `3-recon.jpg` — the private Reconnaissance choice; a sabotaged Landmark (shaded, "until round end").
- `4-spy.jpg` — a Spy-revealed enemy card shown small beside the opponent's hand count.
- `5-sabotage-1900.jpg` — after the hammer strike, the enemy Embassy shaded as sabotaged.

Found and fixed during this review: the status line showed through the choice overlays.
Not covered: no screen recording permission, so on-screen frame pacing was not re-measured
for this change; earlier measurements are in `docs/design/card-table-design.md`.

## 5. Follow-up: "can't click Keep these two" (2026-10-20)

Report: the player could not confirm their two Landmarks. Not reproduced: \`LandmarkChoiceSystemTest\`
(real server with production settings, real client connection, the player's 1470×923 window)
picks two offers and Keep, and round one is dealt; OS-level clicks through the JavaFX \`Robot\`
also worked in three repeated runs. Real defects found and fixed: drawing the offers before the
window was laid out threw \`IllegalArgumentException\` (seen in the player's \`client.log\`), and a
rejected move's message was hidden under the choice overlay, so a refused Keep looked like a dead
button. Ticked offers now show a check badge and an "n of 2 chosen" count; 1–5 and Enter work;
rejected moves are logged on client and server. \`mvn -o clean verify\`: **144 tests, 0 failures**.
