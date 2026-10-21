# Hand-pixel v2 Unit figures in the game — 2026-10-21

The user found the polygon figures "too AI-looking" and asked for the redesign drafted in
`art/cards/drafts/hand-pixel-v2/` to be run. `PixelCardFace` now draws each Unit figure from a
hand-placed 30×38 sprite map of 2×2-pixel cells (see `CARD_RENDERING_GUIDE.md`). Frame, header,
crests, labels, catalog data, rules, Spells and menu are unchanged. Old polygon figure methods,
the class icon and the cross-stitch backdrop were removed from the renderer.

Environment: macOS arm64, Maven 3.9.15 / Java 25.0.2 (Java 17 target), OpenJFX 21.0.5.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Runtime `PixelCardFace` vs draft `native/*.png` (35 Units) | identical | 35 identical, 0 different | PASS |
| `PixelCardFaceTest` shield test | Knights shield matches tabard cloth; 7 distinct colours | updated sample points (64,66)/(40,62); passes | PASS |
| `mvn -o test` (repository) | no failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped | PASS |
| `mvn -o clean verify` on a source copy outside iCloud | same, runnable JAR | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 36.0 s; 0 duplicate class entries | PASS |
| Installed JAR | same bytes as tested build | build, repository and installed JARs share SHA-256 `80bc9f9d…` | PASS |
| Installed JAR vs draft natives | 35 identical | 35 identical, 0 different | PASS |
| Restart | same database | server on `cards-neutral-4`; `deck-duel.db` kept (1 account), backup beside it | PASS |
| `live-table-1470.png`, `live-hover-riders.png` | real `CardTableView` from the installed JAR | v2 figures on both rows and in hand; score unchanged at Royal Line + seven nations = 141 | PASS |

Screenshots use a staged recipient view, not a live online match.

## Axemen fix after feedback — 2026-10-21

The user called the two-handed grip "lol": at 2×2-pixel cells a fist is two or three cells, so
every attempt (vertical haft, 45° haft across the body, leather fists) read as clamps or bars.
References consulted: AGS forum advice that an idle two-handed axe needs hands spaced wide
(<https://www.adventuregamestudio.co.uk/forums/index.php?msg=521318>); Combat Archaeology on the
Dane axe — 100–180 cm haft, small thin asymmetric head with a drooping toe, not used with a
shield (<https://combatarchaeology.org/artefact-of-the-month-the-dane-axe/>); Lospec and itch.io
on silhouette-first readability. Axemen now raise a one-handed bearded axe and carry a round
nation shield with the crest; the other figures are unchanged.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Runtime vs draft natives | 35 identical | 35 identical, 0 different | PASS |
| `mvn -o clean verify` outside iCloud | no failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; 34.7 s; 0 duplicate classes | PASS |
| Installed JAR | same bytes | build, repository and installed share SHA-256 `5f7c2544…`; 35/35 identical | PASS |
| Restart | same database | `cards-neutral-4`; 1 account kept; backup beside the DB | PASS |
| `live-table-1470.png`, `live-hover-axemen.png` | real `CardTableView` from the installed JAR | new Axemen on both rows; Royal Line score unchanged at 141 | PASS |
