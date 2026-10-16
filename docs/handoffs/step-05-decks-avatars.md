# Step 5 — Shared decks, draw flights and player badges

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

Completed implementation, reviewed 2026-10-14; final packaging 2026-10-15.

## Result

The match sidebar now shows both players' abstract crests, names, mana/capacity,
remaining pieces and turn/paused status. Two shared stacks show movement and skill
deck counts, with an explicit empty state. Move Log and its large empty surface
are removed. Own movement and skill cards remain in the unified bottom hand;
opponent movement faces and anonymous skill backs remain above the board.

Accepted draws travel from the matching stack to the receiving hand with easing,
scale, a small arc/rotation and a late reveal for permitted faces. Opponent skill
draws remain face down. The visible state is adopted immediately; visuals never
delay rules or send actions. A clicked card snaps to the live hand before acting.

## Source changes

- `GamePanel`: sidebar layout, opponent component reuse, draw dispatch after layout,
  generation/match/revision guards, paint-mask binding, lifecycle cleanup. Removes
  Move Log-only fields/renderers while retaining model history and board feedback.
- `DeckStackPanel`: public deck counts, empty state and cached shared-back artwork.
- `PlayerBadgePanel`: Blue/Red-aware mana and identity, configured cap, local reserved
  mana, abstract Java2D crest and cancellable mana feedback.
- `CardDraws`: presentation-only reconciliation from recipient-specific views and
  public accepted events. No authoritative GameState or hidden deck access.
- `DeckDrawOverlay`: supplied example integrated into the real UI, one bounded
  timer/batch, transparent hit testing and paint-only destination masks.
- `CardPanel`, `SkillCardPanel`, `CardBackPanel`: skip destination painting during a
  flight while preserving layout and input. Opponent backs still accept no identity.
- `DeckPresentationTest`, `DeckPresentationFixtures`, `GamePanelLayoutTest`:
  accepted engine transitions, public/private projections, draw destinations,
  layout, badge mapping, configurable mana and lifecycle regressions.
- `PresentationRegressionTest`: outgoing-card checks retained; former replacement
  pop checks now prevent a redundant pop alongside the new deck flight.

## Draw rules and edge cases

- Movement uses `MovementPlayed`, so an immediate redraw of the same ID still flies.
- REFOCUS creates two movement flights, including when the same pair returns, plus
  a skill replacement only if the skill deck actually supplies one.
- Skill consumption is detected using public spent/deck deltas. The opponent's
  hand count can stay unchanged; no opponent skill identity is needed.
- FORESIGHT entering CHOICE does not consume/draw early. Its resolution can emit
  an empty event list; the view delta still produces the correct replacement.
  Zero/one-card preview cases use the same consumption logic.
- Deck exhaustion produces hand shrink/reflow without a phantom replacement.
- Skip, End Turn, ordinary refresh, stale/duplicate/wrong-match updates do not draw.
- Cancellation covers reduced motion, pause, screen leave, reconnect, new match,
  disconnect, disposal and resize. Queued layout callbacks cannot revive old effects.

## Validation

- Baseline: 273 tests passed.
- Full implementation run: `mvn clean test`, **308 tests, 0 failures/errors/skips**.
- Independent read-only review: **PASS**, independent `mvn test` also 308 passing.
- Final `mvn clean verify`: **308 tests, 0 failures/errors/skips**, BUILD SUCCESS.
  `target/onitama.jar` contains the new deck/avatar classes and no numbered duplicate entries.
- Controlled real JFrame renders saved under `docs/screenshots/deck-avatar-ui/`:
  own skill, concealed opponent draw, Red movement/same-card redraw, REFOCUS,
  FORESIGHT resolution and empty skill deck, each with layout/midpoint/reveal/final
  snapshots. Midpoint images were visually inspected for deck-to-hand travel.
- Layout tests cover 1280×660 and 1440×820 panels, both player colors, and a six-skill
  hand. Desktop renders use a controllable visual clock and real engine transitions;
  they do not claim a new full multiplayer playtest or measured frame rate.

Some skill screenshots intentionally use a test mana cap of 7 to verify configuration
handling. The shipped balance defaults remain unchanged (cap 5). Initial deal flourish
is not added; draws during gameplay are animated. There is no new dependency, server
protocol, database or balance change. The pre-existing blank EOF line reported by
`git diff --check` in `PracticeBotTest.java` is outside this change.

## Run

From the repository root, use the existing local server if it is already listening:

```bash
java -jar target/onitama.jar server --port 5555 --db data/local-test.db
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
```

Restart the client to load this build. The running old process cannot hot-reload it.
