# Step 5 playtest fixes — 2026-10-14

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

Follow-up to `step-05-playtest.md`. Its original findings and screenshots are retained as review history.

## Changes

- `GamePanel`: targeting instructions now prioritize a complete, castable selection, then destination/direction selection, then another piece. SCATTER explicitly allows casting with one completed target. Instructions and Cast tooltips agree; paused matches disable Cast. The last-move label uses a supported ASCII arrow.
- `UiKit`: styled labels reserve enough width for the bundled font's rasterization and a small vertical margin. The instruction row uses a regular font and available row width. Full instructions remain available as tooltips.
- `ForesightDialog`: opaque header with more inset; reorder buttons use readable Left/Right labels because the bundled font cannot display arrow glyphs.
- `TargetingPresentationTest`: four engine-backed cases cover DASH and SCATTER for Blue and Red, destination prompts, ready-to-cast state, optional versus incomplete second targets, and paused state.
- `ForesightDialogTest`: verifies the readable reorder controls actually change the submitted order.

## Correction to the original report

The claimed persistent zero-size hand was not reproduced in a real JFrame. Immediately after `setCards`, bounds can be zero before Swing validation; on the next EDT pass they become normal (176 × 210 in the reproduction), without resizing. A synchronous bounds observation is insufficient evidence of a persistent invisible hand. No speculative `CardHandPanel` change was made. The existing visible-window regression covers replacement cards becoming laid out.

The original report also calls 1024 × 600 the supported minimum. The client normally requests a minimum of 1280 × 720, constrained by available screen space; smaller offscreen renders are stress cases.

## Validation

- Full `mvn test`: 273 tests, zero failures/errors/skips, including four new targeting cases.
- Independent Agent 2 review and full suite: 273 tests, zero failures/errors/skips; no blocking finding in the initial fixes.
- Final `mvn clean verify`: 273 tests, zero failures/errors/skips; rebuilt `target/onitama.jar`. No numbered duplicate entries in the packaged JAR.
- Independent targeted follow-up: PASS; `ForesightDialogTest` 2 tests, zero failures/errors/skips, confirming readable labels and reordered submission.
- Visually inspected rendered 1280 × 660 game panels: full hand header, MOVE LOG, destination instructions and SCATTER readiness text. Inspected the packed desktop Foresight dialog for full title/subtitle and readable controls. These are controlled UI renders, not a new end-to-end multiplayer playtest.
- Evidence: `docs/screenshots/playtest-fixes/` (`dash-destination.png`, `dash-ready.png`, `scatter-destination.png`, `scatter-ready.png`, `foresight.png`).

The previously noted blank line at EOF in `PracticeBotTest.java` remains outside this fix's scope.
