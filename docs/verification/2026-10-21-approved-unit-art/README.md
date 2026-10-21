# Approved Unit artwork selection — 2026-10-21

The user approved the Pikemen, Bowmen and Riders from
`art/cards/drafts/knight-style-v1/`, and chose the previous Axemen instead of the
draft's diagonal-axe design. Knights remain unchanged.

`PixelCardFace` now uses the three approved figures across all seven nations.
Native 80×120 sprites are drawn directly from Java; `CardFace`/`FxKit` display
the same source artwork in the client. These PNGs are verification outputs.
Catalog metadata, points, rules, protocol, menu and runtime image resources were
not changed by this task. The built JAR is `target/deck-duel.jar`.

## Executed checks

Environment: macOS arm64, Maven 3.9.15 / Java 25.0.2, Java 17 source target;
preview exporter OpenJDK 26.0.1 / OpenJFX 21.0.5.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Native PNG hashes versus previous clothed-Bowmen export | Only Pikemen, Bowmen and Riders change | Exactly 21 changed, all seven nations | PASS |
| Preserve previous Axemen and Knights | 14 native faces unchanged | All 14 byte-identical; both Spell faces also unchanged | PASS |
| Approved Poland drafts | Exact reproduction of three selected draft faces | All three native PNGs byte-identical to the approved draft | PASS |
| Source preservation | Keep original Axemen and shared Knights methods | Original source from Axemen through end-of-file identical; frame/Knights helpers preserved | PASS |
| Renderer palette / alpha / scaling / cache | Existing renderer invariants remain valid | Existing PixelCardFace tests passed for all 37 code faces | PASS |
| Final selected set and nation variations | Three new figures with original Knights and Axemen; correct nation colours | `poland-preview.png` and `all-units.png` opened and inspected | PASS |
| `mvn clean verify` | Full tests pass and runnable shaded JAR builds | 153 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 35.337 s | PASS |
| `git diff --check` | No whitespace errors | Exit 0 | PASS |

Machine-readable image hashes and changed/unchanged IDs: `scope-checks.json`.
No new tests asserting drawing coordinates were added for this visual change.

## Reproduce

From the repository root after building:

```sh
javac --release 17 -cp target/classes:target/deck-duel.jar -d /tmp/deckduel-approved-preview tools/RenderPixelCard.java
java -cp /tmp/deckduel-approved-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-approved-unit-art
```

The exporter needs a graphical session for JavaFX. `match-table.png` uses the
production table with a deterministic recipient-visible state, not a live online
match. This task did not restart an ongoing game or replace a separate installed JAR.

## Installed game check — 2026-10-21 13:39 build

The installed client (`~/.deckduel/run/deck-duel.jar`, built 13:07) predated the 13:33
`PixelCardFace`. Rendering every pixel card with the classes inside each JAR and comparing
against this folder's `*-native.png`:

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Installed 13:07 JAR vs approved natives | — | 16 identical, 21 different (every Pikemen, Bowmen and Riders) | stale |
| `mvn -o clean verify` | Zero failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 34.0 s | PASS |
| New 13:39 JAR vs approved natives | 37 identical | 37 identical, 0 different | PASS |
| Install and restart | Same JAR byte for byte; same database | `cmp` equal; server on `cards-neutral-4`; `deck-duel.db` kept (1 account), backup beside it | PASS |
| `live-table-1470.png`, `live-hover-riders.png` | Real `CardTableView` loaded from the installed JAR shows the approved figures | New Pikemen/Bowmen/Riders on your row and in hand, original Knights/Axemen on the opponent's row; Full Regiment + seven nations = (20 + 15 + 20) × 2 + 15 = 125 | PASS |

The screenshots use a staged recipient view, not a live online match. No source, rule or
menu change was made for this check.
