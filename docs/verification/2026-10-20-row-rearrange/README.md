# Board-card rearrangement animation — 2026-10-20

Own-row cards now use the same 70 ms time-based easing as hand rearrangement. While dragging over another space, its card slides toward the source space. On release, the held card settles from the pointer to its destination; an invalid drop returns to its original space. This changes presentation only, retaining the existing swap behavior and real server slot identifiers. Reduced motion skips interpolation.

## Executed checks

Environment: Windows 11 amd64, Microsoft OpenJDK 21.0.10, Maven 3.9.12, OpenJFX 21.0.5, Java 17 compilation target.

`mvn test -q`: **158 tests, 0 failures, 0 errors, 0 skipped**, including **24 CardTableView tests**. Per-suite results are retained in `test-results.txt`. The first restricted run could not connect to ephemeral localhost servers; the successful full run used localhost access.

`mvn -q -DskipTests package`: succeeded and rebuilt `target/deck-duel.jar`. `git diff --check`: passed.

| Input / scenario | Expected | Actual |
| --- | --- | --- |
| Drag a row card over an occupied space | Displaced card's destination changes immediately, but its visible position starts at its old space | PASS |
| Repeated drag events at the same destination | Stable preview without alternating the target | PASS |
| Release slightly below the target's center | Held card starts at the pointer, then both cards settle exactly into the swapped spaces | PASS |
| Click a settled swapped card; receive the same server view | Hit maps to the original authoritative slot; arrangement survives update; no move sent | PASS |
| Leave the row, then drop outside it | Preview cancels; card starts gliding back to its original space | PASS |
| Enable reduced motion, then rearrange | Existing motion settles immediately; preview and drop show final positions without easing | PASS |
| Existing card targeting, keyboard plays, draws, skips, Landmark/Spy inspection and server integration | Existing behavior preserved | PASS |

## Visual evidence

`RenderRowRearrange.java.txt` renders the real JavaFX table using a seeded match and throws if rearrangement sends a gameplay move. Before/dragging/settled captures at 1280×720 and 1470×920 are retained as `table-WIDTH-PHASE.png`. The dragging capture shows the displaced card between its source and target; after 1.5 seconds the harness checks that the released card exactly reaches its destination. The 1280 dragging and 1470 settled captures were visually inspected for card overlap, row visibility, intact disabled-card markings and unchanged hand/score/Landmark layout.

Reproduce after compiling tests: copy the harness to `target/RenderRowRearrange.java`, compile it into `target/test-classes` with the Maven test classpath, then run `deckduel.client.ui.RenderRowRearrange docs/verification/2026-10-20-row-rearrange` with that classpath in a graphical session. No account database is used.
