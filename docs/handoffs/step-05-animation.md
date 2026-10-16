# Step 5 — animation implementation and review fixes

Updated 2026-10-14. This replaces the initial Gemini handoff, whose passing tests
did not establish that card spending/recycling or mid-game reduced motion worked.

## Current implementation

The unified bottom hand still contains both movement cards and all own skills.
Opponent movement is public; unplayed opponent skills remain anonymous backs.
The board interpolates accepted piece movement, displays elimination rings and
public skill cues, and provides targeting highlights/direction controls. Hand
replacement uses reflow, a short outgoing-card fade/lift, and a replacement pop.
No gameplay, balance, server messages, database, or authoritative RNG was changed.

## Fixes following review

- `ClientModel.handleGameUpdate` filters wrong-match, stale and already-presented
  revisions before either history recording or presentation. A same-revision
  event update following an action reply remains accepted exactly once.
- `ClientModelListener.onPresentationReset` explicitly abandons effects on a
  reconnect/new-match baseline, including repeated practice sessions.
- `GamePanel` reconciles the interactive hand immediately with the permitted
  current view. `CardHandPanel` paints an outgoing copy from the prior **own**
  view above that hand; it is not an input-catching component. It survives the
  hand replacement that previously removed the card before it could animate.
- Completing a departure pops the actual replacement, including immediate redraw
  of the same movement card. A skill without a replacement produces no pop.
  Cancellation drops callbacks; it never submits gameplay or mutates model state.
- New accepted-event cleanup preserves freshly started hand reflow. Lifecycle
  cleanup additionally stops reflow, mana/turn pulses, card effects and board
  effects. Board cancellation no longer invokes a capture callback.
- Reduced motion propagates to all existing cards without requiring a hand
  rebuild. It cancels outgoing copies and timers, including selection pulses.
- Departing effects cancel on leaving the game, reconnect, new match, disconnect,
  game over, disposal, or another accepted action. The latest authoritative view
  always remains available for input. There is no unbounded effect queue.
- Reused cards no longer accumulate hand-hover listeners. Cards are not reused
  across a viewer/color or rules change.

## Files touched by the fixes

`ClientModel`, `ClientModelListener`: event acceptance, history deduplication and reset hook.
`GamePanel`: outgoing-card integration, preference propagation, lifecycle/timer ownership.
`CardHandPanel`: non-interactive outgoing rendering, cancellation and fresh-reflow preservation.
`CardPanel`, `SkillCardPanel`: immediate preference changes and disposal cleanup.
`BoardPanel`: callback-free cancellation and reduced-motion selection pulses.
`PresentationRegressionTest`: real accepted actions routed through model and panel.

## Validation

Final `mvn clean test` and `mvn clean verify`: **269 tests, zero failures,
errors or skips**, including the real visible-window reflow regression.
Independent targeted review: **PASS**, no remaining blocking finding.
An offscreen 1440×820 midpoint render was inspected for outgoing-card placement,
remaining hand visibility, and complete board bounds. The executable JAR was rebuilt.

New regressions cover reply-first movement, normal/same-card replacement, actual
accepted skill spending/replacement, duplicate/stale/wrong-match history,
existing-card reduced motion, lifecycle cancellation, and discarded capture
callbacks. They run on the EDT without sleep-based timing assertions. The visible
window case explicitly skips on headless hosts; report skips honestly.

Existing Gemini tests remain, but its manually constructed event-count test and
static screenshot assertions are not evidence of animation correctness. Offscreen
renders establish appearance at particular instants, not measured frame rate or
a full live match. No claim is made that every original Step 5 enhancement (such
as private draw flips or animated opponent backs) is implemented.

## Run locally

Build: `mvn clean verify`.
Server: `java -jar target/onitama.jar server --port 5555 --db data/local-test.db`.
Client: `java -jar target/onitama.jar client --host 127.0.0.1 --port 5555`.
Reuse an appropriate existing local server and preserve the test database.
This fix pass does not deploy to EC2 or change account data.
