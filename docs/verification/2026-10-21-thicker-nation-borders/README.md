# Slightly thicker nation borders — 2026-10-21

The coloured band increases from three to four native pixels (plus the existing
dark outer outline). The cream panel moves in by one pixel on each side.
The header crest shifts left one pixel to keep its complete shape on cream.
Figures, card points, names, class labels and nation palettes are unchanged.
This applies to all 35 Units; neutral Spell faces remain unchanged.

## Executed checks

Environment: macOS arm64; Maven 3.9.15 / Java 25.0.2, Java 17 source target;
preview exporter OpenJDK 26.0.1 / OpenJFX 21.0.5.

| Check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Scope versus previous borders | 35 Unit frames thicken; figures and lettering stay intact except header crest shift | All 35 comparisons passed | PASS |
| Nation colours | Five classes agree per nation; seven distinct borders | All seven distinct and internally consistent | PASS |
| Neutral Spells | Draw Two and Spy stay pixel-identical | Both unchanged | PASS |
| Palette / alpha / scaling / cache tests | Existing invariants remain valid | Existing renderer tests passed | PASS |
| Visual review | Slightly thicker edges at enlarged and 104×156 hand size | `before-after.png` and `nations-preview.png` opened and inspected | PASS |
| Clean isolated build | Full tests and shaded JAR succeed | 153 tests, 0 failures/errors/skips; BUILD SUCCESS, 35.294 s | PASS |
| Publish build | Repository JAR and current renderer classes equal tested isolated outputs | SHA-256 comparison passed; JAR class entry equals tested renderer | PASS |
| Whitespace | No errors | `git diff --check` exit 0 | PASS |

The initial repository build had stale duplicate class files (`PixelCardFace 2.class`
beside the older canonical class). Its first export still showed the previous border.
A fresh source snapshot built under `/tmp/deckduel-thicker-isolated-build/` avoided
the duplicate output. The successful isolated JAR and renderer classes were copied
back atomically to `target/` and checked against their tested originals.

Final artifact: `target/deck-duel.jar`. No running game was restarted.

## Reproduce

With a fresh compiled JAR and classes, from repository root:

```sh
javac --release 17 -cp target/classes:target/deck-duel.jar -d /tmp/deckduel-thicker-preview tools/RenderPixelCard.java
java -cp /tmp/deckduel-thicker-preview:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-thicker-nation-borders
javac --release 17 -d /tmp/deckduel-thicker-preview docs/verification/2026-10-21-thicker-nation-borders/source/CheckNationBorders.java
java -Djava.awt.headless=true -cp /tmp/deckduel-thicker-preview CheckNationBorders docs/verification/2026-10-21-nation-card-borders docs/verification/2026-10-21-thicker-nation-borders
```

The JavaFX exporter needs a graphical session. PNGs are outputs from code, not game
input assets. `scope-checks.txt` retains the offline comparison results.

## Installed game update — 2026-10-21 13:52 build

The installed client (`~/.deckduel/run/deck-duel.jar`, 13:39) predated the 13:45 renderer;
the repository `target/` held stale duplicates (`Cards 2.class`, `ScoringTest 2.class`, …).
The 28 painted Unit runtime JPEGs (all Units except the art-less Axemen) were no longer
referenced and were removed; `assets/cards/runtime/` keeps only the ten Spell JPEGs.
`CardArtworkTest` now asserts that no Unit image ships.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Installed 13:39 JAR vs this folder's `*-native.png` | — | 2 identical, 35 different (every Unit) | stale |
| Source tree duplicates (`* 2.*`) | none | none | PASS |
| `mvn -o clean verify` on a source copy outside iCloud | Zero failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 33.1 s | PASS |
| JAR contents | no duplicate class entries; only Spell JPEGs | 0 duplicate entries; 10 runtime JPEGs | PASS |
| New JAR vs `*-native.png` | 37 identical | 37 identical, 0 different | PASS |
| Repository `target/` | cleaned, no duplicates | `mvn clean`, then only the tested JAR; 0 duplicates | PASS |
| Installed JAR | same bytes as the tested build | identical SHA-256 (`63b8ba86…`) for build, repository and installed copies | PASS |
| Restart | same database | server on `cards-neutral-4`; `deck-duel.db` kept (1 account), backup beside it | PASS |
| `live-table-1470.png`, `live-hover-riders.png` | real `CardTableView` loaded from the installed JAR | 4px nation borders on every Unit; new Pikemen/Bowmen/Riders, original Knights/Axemen; Royal Line + seven nations = (27 + 6 + 30) × 2 + 15 = 141 | PASS |

Screenshots use a staged recipient view, not a live online match. Gameplay, rules and menu
were not changed.
