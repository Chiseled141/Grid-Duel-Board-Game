# Skip turn and larger hand cards — 2026-10-20

The user approved skipping one turn at the cost of one of the ten round plays, then requested larger hand cards. No card is played, drawn or discarded by a skip. Automatic empty-hand skipping keeps its existing behavior.

## Executed checks

Environment: macOS 27.0, Apple Silicon, OpenJDK 26.0.1; Maven compiles for Java 17; JavaFX 21.0.5. Desktop and local socket checks ran with desktop/localhost access. Initial restricted runs could not initialize JavaFX; the complete rerun succeeded.

`mvn test`: **150 tests, 0 failures, 0 errors, 0 skipped**. Per-suite results are in `test-results.txt`.

`mvn -DskipTests package`: **BUILD SUCCESS** after the full test run; rebuilt `target/deck-duel.jar`. `git diff --check`: passed.

| Input / scenario | Expected result | Actual result |
| --- | --- | --- |
| Skip after playing and manually drawing | One allowance consumed; turn changes; cards, board, scores and draws stay unchanged | PASS |
| Both players repeatedly skip | Each round ends after ten turns each; three rounds bank exactly once; finished match rejects a skip | PASS |
| Skip during Landmark choice, opponent turn or pending Reconnaissance | Rejected without a state change | PASS |
| A player's tenth skip | Only the opponent can finish their remaining allowance | PASS |
| Real socket skip, stale revision, out-of-turn request, retry | Both peers see one accepted skip; rejected requests and retry consume nothing extra | PASS |
| Serialization and protocol handshake | SkipTurn crosses the explicit filter; protocol 10 is rejected by protocol 11 | PASS |
| Click Skip turn / press S | Sends the move; no local gameplay mutation; blocked while reconnecting, choosing, keeping, waiting or finished | PASS |
| Existing card click, drag, rearrange and keyboard scenarios with enlarged hand faces | Correct card and destination remain selectable | PASS |

## Visual review

The actual JavaFX table was rendered at 1280×720 and 1470×920. The hand uses 130×195 and 156×234 card faces respectively, compared with row faces of 86×130 and 104×156. Both snapshots were inspected for spacing, bottom clearance, and visibility of Draw and Skip turn. The room, row and Landmark layout remain intact.

- `table-1280.png`
- `table-1470.png`
- `RenderSkipHand.java.txt`: snapshot harness; copy to a temporary `RenderSkipHand.java`, compile against the application and Maven test classpath, then run `deckduel.client.ui.RenderSkipHand OUTPUT_DIRECTORY` in a graphical session. It uses a deterministic real match state and no account database.

Protocol 11 introduces the SkipTurn move; restart the server and clients together after rebuilding.
