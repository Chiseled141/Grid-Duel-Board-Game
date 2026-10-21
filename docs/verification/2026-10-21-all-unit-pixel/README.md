# All 35 Units rendered from Java — 2026-10-21

Problem: the previous integration rendered seven Units and two Spells from Java. Other
Units still used painted JPEGs or the generic procedural face, producing mixed styles.
Hungary and Austria Knights also inherited a red shield despite their different cloth colours.

Change: `PixelCardFace` now draws all 35 catalog Units from Java2D primitives at 80×120,
through the existing `CardFace` sprite cache and `FxKit` conversion/draw path. Five class
silhouettes share seven distinct nation palettes and stylized crests. Cloth, shield fields,
banner and accent colours follow the nation palette; armour/skin/wood retain material colours.
All Unit labels/points use catalog metadata. Transylvania's full name occupies two header lines.
No catalog, gameplay, protocol or persistence change was needed. Draw Two/Spy keep their
code art; the other eight Spells and the procedural back retain their current presentation.

## Source evidence

- `src/main/java/deckduel/client/ui/PixelCardFace.java`: no file/network/image loading;
  `render()` creates a BufferedImage and draws rectangles/polygons/bitmap lettering.
- `CardFace.renderFace()` selects supported pixel art before consulting `CardImages`.
- `PixelCardFaceTest` verifies all 35 catalog Unit IDs and two Spell IDs are supported,
  while mismatched ID/nation/class combinations and null are rejected.
- Asset JPEGs and old export packs are retained; they are not the rendered Unit source.
  The unrelated painted Spell art still intentionally uses JPEG loading.

## Executed verification

Environment: macOS 27.0 arm64. Maven 3.9.15, Maven runtime Homebrew Java 25.0.2,
Java target 17; screenshot tool OpenJDK 26.0.1, OpenJFX 21.0.5.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Catalog coverage | 35 Units plus Draw Two/Spy render from code | 37 supported catalog IDs | PASS |
| Native sprites | 80×120; binary alpha; transparent corner; ≤16 colours incl. transparency | All 37 checked by tests | PASS |
| Sprite enlargement | All interior native pixels retain exact 3×3 colour blocks | All 37 checked pixel by pixel | PASS |
| Cached face after JPEG decode | Pixel face is unchanged | Existing cache identity test passes for every supported card | PASS |
| Nation shields | Each Knights shield field matches cloth and differs across all seven nations | Seven distinct colours; exact shield/cloth pixel equality | PASS |
| Metadata and four-point class distinction | Points from catalog; Axemen and Riders retain different art and labels | Existing points/label/figure checks pass | PASS |
| Long nation header | Full Transylvania visible, no score/crest overlap | Inspected native-derived JavaFX gallery | PASS |
| JavaFX screenshots | 35 distinct nation/class faces, hand/row visible through production renderer | `all-units.png`, `nations-preview.png`, `axemen-preview.png`, `match-table.png` inspected | PASS |
| `mvn test` | Zero failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; 28.716 s | PASS |
| `mvn clean verify` | Fresh runnable JAR and passing tests | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 33.635 s | PASS |
| Whitespace | `git diff --check` succeeds | No errors | PASS |

The first sandboxed focused test attempt aborted because the graphics session was unavailable.
The full suite then passed with graphics and localhost access. No performance claim is made.
An incremental package attempt waited on stale duplicate class files in `target/classes`.
It was interrupted; `mvn clean verify` removed stale build output and completed successfully.
The renderer class inside the shaded JAR was compared byte for byte with the newly compiled class.

## Reproduce

From the repo root after compiling/packaging:

```sh
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-all-unit-pixel
```

The tool exports all 37 native/4× faces, galleries of seven Knights and seven Axemen, the
35-Unit grid, the five Poland classes, the two Spells and the existing selection/disable preview.
`match-table.png` is the real `CardTableView` given a deterministic recipient view after a legal
core move; it is a staged local preview, not a live network match. No account/settings are saved.
The shaded JAR must be rebuilt and an existing client restarted before the live app loads the
new classes; this task does not terminate an ongoing client/server or replace a separate installed copy.

## Game restart check — 2026-10-21 13:07 build

`ClientFrame` now preloads JPEGs only for cards without pixel art, so the old Unit images are
never decoded at startup; every Unit face still comes from `PixelCardFace`.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| `mvn -o clean verify` | Zero failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 35.5 s | PASS |
| `live-table-1470.png` (real `CardTableView`, staged view) | All Units pixel art in nation colours; scores from catalog | 7 nations on your row, mixed nations on the opponent's row and in hand, all pixel; Royal Line + seven nations = (26 + 6 + 30) × 2 + 15 = 139 | PASS |
| Restart | New JAR, same `deck-duel.db` | Server logs catalog `cards-neutral-4`; database file kept (backup beside it) | PASS |
