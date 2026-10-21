# Bowmen clothing and arrow correction — 2026-10-21

User feedback: the previous Bowmen appeared shirtless and the weapon resembled a hoe.
The exposed arms and thick horizontal outlined shaft made those shapes ambiguous at card size.

Correction in `PixelCardFace`: both sleeves now use the nation cloth colours all the way to
the wrists, ending in contrasting cuffs/gloves. Only the face exposes skin. The bow grip
is wood/leather coloured; the arrow is a one-pixel shaft with a small point, separated from
the sleeve, instead of a heavy outlined bar and large metal head. Longbow curvature, taut
string, quiver, hood, nation crest and existing full tunic remain. All seven Bowmen inherit
the correction; catalog points (3) and gameplay are unchanged.

The images are code-drawn Java2D sprites displayed through the production JavaFX renderer.
They are exported evidence, not image inputs to the live game.

## Checks

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Native output comparison | Only seven Bowmen change | Exactly seven Bowmen PNGs changed; the other 30 native faces byte-identical to previous export | PASS |
| 4× face and seven-nation gallery | Sleeves/cuffs readable; arrow separated from arm; wooden curved bow | `pl-bowmen-rendered.png` and `bowmen-preview.png` opened and inspected | PASS |
| Native/card size comparison | Pixel silhouette remains readable at 104×156 | `before-after.png` exported at 3× and card size | PASS |
| `mvn test` | No failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; 1:10 min | PASS |
| Fresh package after tests | Runnable shaded JAR | `mvn -DskipTests clean package`: BUILD SUCCESS, 6.769 s | PASS |
| Whitespace | No errors | `git diff --check` passed | PASS |

Environment: macOS 27.0 arm64; Maven 3.9.15 on Java 25.0.2, source target 17;
exporter OpenJDK 26.0.1 with OpenJFX 21.0.5. Visual readability was inspected, not measured in
a user study. Existing palette/alpha/scaling/cache/UI tests cover the renderer; no new
coordinate-based drawing tests were added for this visual correction.

## Reproduce

```sh
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-bowmen-clothed
```

`before/` contains the two previous Bowmen/Riders exports from
`../2026-10-21-bowmen-riders/` for the comparison tool; the Riders before/after are identical
in this correction. Native/4× images, galleries and the real table snapshot are exported
using the same tool. The table shows a deterministic recipient view after a legal core move,
not a live online match. This task does not restart a user's ongoing match or change accounts.
