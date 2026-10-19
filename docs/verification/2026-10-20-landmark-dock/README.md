# Landmark dock and larger Unit rows — 2026-10-20

The user requested relocating Landmarks so the board cards could grow, following removal of the score rail's Landmarks & Effects section.

Both players' Landmarks now occupy separate labelled shelves below the compact score rail. Each tile has an undistorted art thumbnail and card name. Hovering shows the full card description in the existing table detail line. Enemy tiles remain Sabotage targets by click and keyboard, and suppression remains visible. Unit rows have seven spaces across the central table. Scores and all gameplay rules remain authoritative server values.

## Executed checks

- `mvn test`: **151 tests, 0 failures, 0 errors, 0 skipped**. See `test-results.txt`.
- New layout scenario checks both 1280×720 and 1470×920: all four Landmark targets fit in the viewport, remain distinct, map to the correct hit targets, and never overlap the Unit rows; enlarged rows never overlap the hand. PASS.
- Existing card click, drag, rearrangement, keyboard, Landmark selection, Sabotage targeting and animations continue to pass.
- Actual JavaFX table snapshots at both sizes were inspected for readable names, larger Units, separate shelves, turn/detail-line clearance, draw/skip access and hand clearance. PASS.
- `git diff --check`: passed.

| Window | Previous Unit face | New Unit face | Hand face |
| --- | --- | --- | --- |
| 1280×720 | 86×130 | 94×142 | 129×194 |
| 1470×920 | 104×156 | 140×211 | 170×256 |

The larger window allows Unit cards about 35% wider and taller. Smaller windows reserve room for the hand and controls rather than clipping them. These are rendered layout measurements, not performance claims.

Environment: macOS 27.0, Apple Silicon, OpenJDK 26.0.1, Java 17 target, JavaFX 21.0.5. Tests and renders used desktop/localhost access. Snapshots use the deterministic match harness saved in `../2026-10-20-skip-hand/RenderSkipHand.java.txt`, with this directory as output.

`mvn -DskipTests package`: BUILD SUCCESS after the test run; rebuilt `target/deck-duel.jar`.
