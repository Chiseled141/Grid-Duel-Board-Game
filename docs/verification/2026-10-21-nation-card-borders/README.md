# Nation-coloured Unit borders — 2026-10-21

All 35 Unit cards now use their nation cloth palette for the outer frame and the
two separator lines. The stepped dark outline, bright top/left bevel and dark
bottom/right bevel remain on the native pixel grid. The face remains cream so
the existing dark bitmap lettering and figures retain their contrast.

The change reuses the existing three nation colours; it adds no palette colours.
The approved Pikemen, Bowmen, Riders, Knights and original Axemen figures are
unchanged. Spells remain neutral and unchanged. The game draws the border in
`PixelCardFace.frame()`; exported PNGs are evidence, not runtime input assets.

## Executed checks

Environment: macOS arm64; Maven 3.9.15 on Java 25.0.2, Java 17 source target;
JavaFX exporter OpenJDK 26.0.1 / OpenJFX 21.0.5.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Compare all 35 Units against approved artwork | Only frame and separator pixels change | All 35 interiors match, apart from the two separator lines | PASS |
| Compare nation borders across all five classes | Same colour within a nation, seven distinct nation colours | Five classes agree in every nation; seven distinct colours | PASS |
| Neutral Draw Two and Spy | Pixel-identical to the prior export | Both unchanged | PASS |
| Existing palette / alpha / sprite / cache checks | At most 16 colours; binary alpha; crisp scaling | Existing PixelCardFace tests pass for all 37 code-rendered faces | PASS |
| Visual inspection | Coloured edges visible at enlarged and hand size; lettering and figures intact | Seven-nation gallery and before/after 104×156 comparison opened and inspected | PASS |
| `mvn clean verify` | Tests pass and shaded JAR builds | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 35.207 s | PASS |
| `git diff --check` | No whitespace errors | Exit 0 | PASS |

Final JAR: `target/deck-duel.jar`. This task does not restart an ongoing game or
replace any separate installed copy. No gameplay, catalog or protocol changes.

## Reproduce

From the repository root after building:

```sh
javac --release 17 -cp target/classes:target/deck-duel.jar -d /tmp/deckduel-nation-border-preview tools/RenderPixelCard.java
java -cp /tmp/deckduel-nation-border-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-nation-card-borders
javac --release 17 -d /tmp/deckduel-nation-border-preview docs/verification/2026-10-21-nation-card-borders/source/CheckNationBorders.java
java -Djava.awt.headless=true -cp /tmp/deckduel-nation-border-preview CheckNationBorders docs/verification/2026-10-21-approved-unit-art docs/verification/2026-10-21-nation-card-borders
```

The first exporter needs a graphical session. Its `match-table.png` uses the
production table with deterministic recipient-visible state, not a live online match.
`scope-checks.txt` records the comparison output. `before-after.png` is a Java2D
contact sheet of native Java-rendered faces, including 104×156 sampling.
