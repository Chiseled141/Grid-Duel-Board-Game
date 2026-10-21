# Axemen and pixel pack v2 — 2026-10-21

Axemen (4 points) replace Battering Ram (6) for all seven nations (catalog `cards-neutral-4`),
and pixel pack v2 (`art/cards/exports/unit-pixel-v2-axemen`) is drawn in the game by
`PixelCardFace`. No new rules: Axemen have no ability; Draw Two and Spy keep their pixel faces.

Environment: macOS 27.0 arm64, OpenJDK 26.0.1 (Java 17 target), JavaFX 21.0.5, graphical session.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Catalog card data vs `catalog-axemen-preview.txt` | identical Unit/Spell lines | `diff` empty | PASS |
| `PixelCardFace.render` vs pack `native/*.png` (7 cards) | identical pixels | 7/7 identical | PASS |
| `mvn -o clean verify` | no failures, errors or skips | 152 tests, 0 failures, 0 errors, 0 skipped (60.0 s) | PASS |
| New test: Riders 4 vs Axemen 4 | no pair from equal points; Riders + Axemen pairs make Two Companies; Royal Line needs Axemen | as expected | PASS |
| New test: pixel Axemen vs Riders | same numeral 4, different label and figure | as expected | PASS |
| Pixel scope | exactly 9 IDs (`supports()`) | 9 IDs; `hu-axemen` not supported | PASS |
| Retired art | no `*-battering-ram.jpg` shipped | 7 runtime copies removed | PASS |
| JavaFX table (`table-1470.png`) | 7 pixel Units on the row, pixel Spells in hand, non-Polish Axemen falls back | as expected; Banner Host (28 + 39 + 40) × 3 = 321 | PASS |
| Hover preview (`axemen-hover-preview.png`) | large Poland Axemen, nearest-neighbour | crisp, no blending | PASS |

Screenshots: `table-1470.png` and `axemen-hover-preview.png` are the real `CardTableView` showing
a staged recipient view (not a live network match). `poland-preview.png`, `nations-preview.png`,
`spells-preview.png`, `match-table.png` and the `*-native.png`/`*-rendered.png` sprites come from
`tools/RenderPixelCard.java` through `CardFace`/`FxKit`.

Still missing: pixel art for the six non-Polish Axemen (procedural face meanwhile) and for the
other nations' Pikemen, Bowmen, Riders and Knights; painted Riders still print 7 against the
rules' 4.
