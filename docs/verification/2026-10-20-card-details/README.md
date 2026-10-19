# Landmark rows and card inspection — 2026-10-20

Replaced the cramped two-column Landmark tiles with four full-width rows below the score rail. Complete names, including Watchtower, fit beside a thumbnail. Each row has an active/disabled indicator and an info icon. Larger table and hand cards remain in place.

Click a Landmark row or press L to inspect a large card image, exact rules text, active/Sabotaged status and, for scoring buffs, its current marginal public row contribution using core Scoring. Existing provisional scoring values are labelled. Information/draw effects are not represented as points. While targeting Sabotage, the row still casts it; clicking its info icon inspects without casting.

Click a Spy-revealed face to enlarge that specific card and read its points/tags or Spell text. This uses only PlayerView.revealed. When an inspected card leaves that list, the inspector closes and clears its image and private accessibility description. Inspection does not send a move, reveal other cards, steal or discard. Close, Escape or clicking the backdrop dismisses it.

## Executed checks

- `mvn test`: **156 tests, 0 failures, 0 errors, 0 skipped**. Per-suite results in `test-results.txt`.
- Exact buff text and known current contributions: Embassy +3, Armory +2 and Ruined Keep +10 for the tested two-Unit row; disabling each produces +0. PASS.
- Archive and Watchtower retain exact names/rules without fabricated points or private opponent deck data. PASS.
- Click and keyboard inspection on the opponent's turn, no actions from inspection, modal input blocking, Close/Escape and not reopening on later updates. PASS.
- A server-applied Sabotage refreshes an already-open inspector; clearing the match closes it. PASS.
- Sabotage info-icon click opens details without casting; closing and clicking the row still casts the correct target. PASS.
- Spy inspection displays the exact revealed card, sends no move, closes and clears when that card leaves the enemy hand, and cannot inspect unrevealed card backs. PASS.
- Existing layout checks at 1280×720 and 1470×920 ensure Landmarks fit, targets remain distinct and separate from Units and the hand. PASS.
- `git diff --check`: passed.

Actual JavaFX snapshots show the dock, Watchtower inspector and Spy inspector at both window sizes. The snapshot harness is saved as `RenderCardDetails.java.txt`; compile it in a temporary directory against the application and Maven test classpath, then run `deckduel.client.ui.RenderCardDetails OUTPUT_DIRECTORY` in a graphical session.

Environment: macOS 27.0, Apple Silicon, OpenJDK 26.0.1, Java 17 compilation target, JavaFX 21.0.5. Desktop and localhost checks ran with the required access. These are layout and correctness checks, not performance measurements.

`mvn -DskipTests package`: BUILD SUCCESS after the full test run; rebuilt `target/deck-duel.jar` and reopened the updated client on the existing local server.

Final complete-project check before commit: `mvn clean verify` succeeded with 156 tests, 0 failures, 0 errors and 0 skipped.
