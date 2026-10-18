# Validation record

## Before edits

- `git status --short`: empty; no existing user changes.
- `mvn test`: **FAIL**, 563 tests, 5 failures, 12 errors, 0 skipped.
  Five parameterized `GamePanelLayoutTest.boardAndUnifiedHandFitAtSupportedWindowSizes`
  failures at line 139 (`assertInside`, line 163). Twelve errors in `UserDaoTest`
  (7) and `MatchDaoTest` (5) are JUnit temporary-directory cleanup failures because
  SQLite files remain open on Windows. No tests disabled or unrelated code changed.
- Baseline full output:
  `C:\Users\Kem\.local\share\opencode\shell\136b1610903a2d9276e89b10fcd966f725f251b6\sh_14f92369d001jEXdIANcdWMW23.out`.

## Changed behavior

- `mvn -Dtest=MedievalPresentationTest test`: **PASS**, 6 tests, 0 failures/errors/skips.
- Changed source compiles with Maven's `javac release 17` on the installed JDK 21.
- Regression assertions check live description pixels in the body, unchanged
  title/cost/art region, changed parameter-derived text, bounded long literal
  text, unchanged category/footer and complete escaped popup details.
- `mvn test` after changes: **FAIL**, 565 tests, 5 failures, 12 errors, 0 skipped.
  The failing classes and failure locations match the pre-edit baseline; the two
  additional description tests passed. No additional failing test was observed.
- `mvn clean verify`: **BLOCKED at clean**, before any tests or packaging.
  Windows refused deletion of `target/onitama.jar`. No process was stopped and
  no forced deletion or test bypass was attempted. No clean build/shaded-JAR
  success is claimed.
- `git diff --check`: passed (Git emitted only LF/CRLF conversion warnings).
- Rehashed all three installed inputs after inspection: all unchanged.

## Independent Agent 2 review

Read-only review followed `docs/agent2-testing-pipeline.md`, despite the green
gate being blocked by the pre-existing failures. Agent 2 independently ran
`mvn test`: **565 tests, 5 failures, 12 errors, 0 skipped**, same signatures;
`MedievalPresentationTest`: **6 passed**. No new CRITICAL/HIGH defect found.
Independent log:
`C:\Users\Kem\AppData\Local\Temp\opencode\agent2-lor-review-20261018.log`.

- **PASS:** scope, spec authority, unchanged dependencies/rules/privacy, full
  per-definition face cache, no proprietary payloads in exported REA metadata.
- **MEDIUM, resolved:** this validation record originally omitted the final
  full-suite/clean-build results; they are now recorded above.
- **MISSING TESTS, non-blocking/deferred:** current pixel assertions do not prove
  exact semantic text/wrapping, real-screen readability, or visible overflow-notice
  content. Automated coverage of every default at final hand sizes and fit-boundary
  cases remains a follow-up. The bounded rendering and complete escaped details
  have focused regression coverage; an offscreen montage of all 24 defaults was
  also visually inspected, but neither is represented as exhaustive UI proof.
- **Unresolved acceptance blocker:** required full-suite/clean verification is
  not green. These are not waived by the focused tests or independent review.

## Manual limits

No live LoR or Grid Duel match was manually played. A separate analyst-authored
Java program rendered only Grid Duel's 24 default faces into
`C:\Users\Kem\AppData\Local\Temp\opencode\lor-analysis-20261018\grid-duel-skill-faces.png`;
visual inspection found the default text contained within its body region with
titles/costs/art/category still visible. UI assertions use offscreen rendering
and existing Swing tests; real-screen readability/HiDPI inspection is not
established by this montage, PE manifest declarations or pixel comparisons.
