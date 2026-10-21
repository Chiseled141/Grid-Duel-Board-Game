# Java-rendered pixel card trial — 2026-10-21

Requested scope: a Balatro-inspired sample rendered by the Java game, followed by screenshots
of spells and other nations. Implemented samples: Poland, Hungary and Austria Knights
(`pl-knight`, `hu-knight`, `au-knight`), plus neutral Draw Two and Spy (`draw-two`, `spy`).
Only these five faces use the new art. No rules, card definitions, or protocol changes.

## Source review

Read these public sources on 2026-10-21:

- [CardBuilder.tsx](https://github.com/DFATPUNK/balatro-card-generator/blob/main/src/components/CardBuilder.tsx):
  composes ordered image layers on a 69×95 canvas with CSS `imageRendering: pixelated`.
- [layerPositions.json](https://github.com/DFATPUNK/balatro-card-generator/blob/main/src/config/layerPositions.json):
  fixed native coordinates for background, face, collar, facial features and hat.
- [Joker Forge BalatroCard.tsx](https://github.com/Jaydchw/joker-forge/blob/b30c7ed9d9ed51fcda6795a1c6e232ac76af23fb/src/components/generic/BalatroCard.tsx):
  base art and overlay layers use pixelated image presentation.
- [JokerCard.tsx](https://github.com/Jaydchw/joker-forge/blob/b30c7ed9d9ed51fcda6795a1c6e232ac76af23fb/src/components/pages/jokers/JokerCard.tsx):
  displays the supplied image preview, with a placeholder when absent.

Applied the fixed-grid/layer composition approach, with original Java drawing instructions,
bitmap lettering and heraldry. No upstream code, sprites or fonts were copied.

## Rendering flow

`CardDef → PixelCardFace / PixelSpellArt (80×120 ARGB, at most 16 colours including transparent) → CardFace sprite cache
→ FxKit JavaFX image cache → match canvas / hover preview / Spy inspector / animated card`.

Java2D uses nearest-neighbour sampling for these faces. Their JavaFX canvas draws, inspector and
animated ImageViews disable smoothing. The native aspect ratio matches the game's 2:3 cards.
Integer enlargement preserves uniform square pixels; arbitrary window sizes and animated
rotation can produce uneven pixel widths. Existing selection and suppression overlays remain.
The exported PNGs are evidence only: the renderer does not read them or the ImageGen concept.

## Reproduce

From the repository root, after `mvn package`:

```sh
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard art/cards/concepts/java-pixel
```

Append `--show` to keep the JavaFX preview window open, or `--native` to export only Java2D
images without starting JavaFX. The table screenshot uses a deterministic core match with a
legal Poland Knights play; it is not a live network match. No account or settings are saved.
The tool also exports `nations-preview.png` (three Knights) and `spells-preview.png` (two neutral
spells), using the same JavaFX draw path. Spell captions use the catalog's full effect text.
Each sample also gets its own native and 4× rendered PNG. Exported files are not runtime inputs.

## Executed checks

Environment: macOS 27.0, Apple arm64; Maven 3.9.15 on Homebrew Java 25.0.2, Java 17 compile
target; preview executed with OpenJDK 26.0.1 and bundled OpenJFX 21.0.5.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Native render | 80×120, hard palette, transparent corners | Verified by `PixelCardFaceTest` | PASS |
| 3× game sprite, including after existing JPEG loads | All interior pixels remain exact 3×3 blocks; same cached sprite | Verified pixel by pixel | PASS |
| Printed points changed 5→6 in a test definition | Only numeral pixels change | Verified | PASS |
| Other catalog cards / face-down cards | Trial renderer never selected | Verified | PASS |
| Selection and points/tags disabled | Existing highlight and disable presentation visible | Unit check plus JavaFX screenshot inspected | PASS |
| Actual JavaFX match table, 1280×800 | Trial face visible on row after legal play | `match-table.png` inspected | PASS |
| `mvn test` after five-card expansion | No failures/errors/skips | 150 tests, 0 failures, 0 errors, 0 skipped; 31.922 s | PASS |
| Five sample faces at 1× / 3× | Opaque interiors, hard palette, no blended pixels | Palette and pixel-by-pixel scaling checks run for all five faces | PASS |
| Nation/spell JavaFX galleries | Distinct crests/cloth; neutral spells with catalog names and effects | Both screenshots visually inspected | PASS |

The first sandboxed test attempt could not bind localhost sockets or start the GUI toolkit.
The complete suite was rerun successfully with graphical-session and localhost access.
No performance comparison was made.

Screenshots: `art/cards/concepts/java-pixel/javafx-preview.png`, `match-table.png`,
`pl-knight-rendered.png`; native sprite: `pl-knight-native.png`.
