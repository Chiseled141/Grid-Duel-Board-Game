# Compact score panel — 2026-10-20

Removed the Landmarks & Effects section from the score panel at the user's request. Plays left and Round now follow Units + Combos directly, with Match total beneath them. The nominal panel height is 550 instead of 620. The oversized panel tooltip is removed; the complete authoritative score and counters remain available to assistive technology.

This changes presentation only. Landmark card descriptions, scoring and other gameplay effects still come from the server.

## Checks

- `mvn test`: **150 tests, 0 failures, 0 errors, 0 skipped**. Per-suite results are in `test-results.txt`.
- Existing ScoreRail tests check immediate authoritative values during animation, reduced motion, reset, round transitions, counter validation and large values. The updated test checks the simplified score/counter description and renders widths 196, 260 and 360.
- `docs/design/CardTablePreview.java` compiled successfully against the updated presentation contract.
- `git diff --check` passed.
- JavaFX snapshots inspected at panel widths 196, 260 and 360 and full-table sizes 1280×720 and 1470×920. Expected and observed: no Landmark effects well, no empty gap in its place, readable scores and counters, compact frame and intact surrounding table layout. PASS.

Environment: macOS 27.0, Apple Silicon, OpenJDK 26.0.1, Java 17 compilation target, JavaFX 21.0.5. The complete test run and visual renders used desktop/localhost access after the restricted test run could not initialize the desktop toolkit.

`RenderScoreRail.java.txt` is the snapshot harness. Copy it to a temporary `RenderScoreRail.java`, compile against the application and Maven test classpath, then run `deckduel.client.ui.RenderScoreRail OUTPUT_DIRECTORY` in a graphical session. The sample (your score 39, opponent 47, Units 12, Combos 27, one play, round 1, total 0) matches the user's screenshot.

`mvn -DskipTests package`: BUILD SUCCESS after the test run; rebuilt `target/deck-duel.jar`.
