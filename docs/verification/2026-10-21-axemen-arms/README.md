# Axemen arms correction — 2026-10-21

The user's screenshot showed bare forearms in the same brown as the axe haft,
with two flat horizontal shapes and ambiguous grips. The correction changes
only the arms in the retained original Axemen design, across all seven nations.

`PixelCardFace.axemanArms()` now draws full nation-coloured sleeves, bent elbows,
steel bracers and two separate gauntlets wrapping the existing vertical haft at
different heights. The upper near arm folds back upward; the far arm bends down
to the lower grip. The chest crest and belt are painted behind the arms rather
than over them. No bare-skin or wood colour is used for the corrected forearms.

The selected head, axe, lower body and four-pixel nation border remain unchanged.
No gameplay, card metadata, menu or Spell artwork changes are part of this task.

## Executed checks

Environment: macOS arm64; Maven 3.9.15 / Java 25.0.2 with Java 17 source target;
preview exporter OpenJDK 26.0.1 / OpenJFX 21.0.5.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Native output versus previous thicker-border export | Only seven Axemen change, within their arm region | Exactly seven changed, all differing pixels within x=13–58/y=61–87; 30 other faces identical | PASS |
| Preserve selected Axemen features | Head, axe, lower body, border, names and points unchanged | All seven match outside the arm region | PASS |
| Renderer palette / alpha / scaling / cache | Existing invariants remain valid for all 37 code faces | Existing PixelCardFace tests passed | PASS |
| Visual inspection | Sleeves and distinct steel grips readable at enlarged and 104×156 size | `before-after.png` and `axemen-preview.png` opened and inspected | PASS |
| Clean isolated `mvn clean verify` | Full tests and shaded JAR succeed | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 32.381 s | PASS |
| Publish JAR/classes | Repository artifacts equal tested isolated build | SHA-256 equality passed; JAR renderer entry also matches tested class | PASS |
| Whitespace | No errors | `git diff --check` exit 0 | PASS |

The source snapshot was built at `/tmp/deckduel-axemen-arms-clean-build/` to avoid
the stale duplicate class files previously observed in the synced repository
build directory. The tested JAR and renderer classes were copied atomically into
`target/`. JAR SHA-256:
`b220554945dab05540c6a551320b3162ee8c36936b03d97dcb743e98e9b0d802`.

All final exported faces and JavaFX galleries were rendered from
`target/deck-duel.jar` itself. These PNGs are output evidence, not runtime inputs.
No running game or separate installed JAR was replaced.

## Reproduce

From the repository root with a fresh packaged JAR:

```sh
javac --release 17 -cp target/deck-duel.jar -d /tmp/deckduel-axemen-packaged-preview tools/RenderPixelCard.java
java -cp /tmp/deckduel-axemen-packaged-preview:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-axemen-arms
javac --release 17 -d /tmp/deckduel-axemen-packaged-preview docs/verification/2026-10-21-axemen-arms/source/CheckAxemenArms.java
java -Djava.awt.headless=true -cp /tmp/deckduel-axemen-packaged-preview CheckAxemenArms docs/verification/2026-10-21-thicker-nation-borders docs/verification/2026-10-21-axemen-arms
```

The JavaFX exporter requires a graphical session; the scope checker is headless.
`scope-checks.txt` retains changed and unchanged IDs. `match-table.png` uses the
production table with a deterministic recipient-visible state, not a live match.
