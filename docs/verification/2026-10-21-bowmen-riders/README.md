# Bowmen and Riders silhouette redesign — 2026-10-21

User feedback: Bowmen and Riders were difficult to recognize on small cards. This is a visual
change to both class drawings for all seven nations (14 Units), without changes to card data,
points (Bowmen 3, Riders 4), nation colours, crests, game rules or protocol.

Bowmen: side-on shooting stance, separate legs and short tunic, clear face/nose, quiver behind
the shoulder, thick longbow, two taut diagonal string segments, high-contrast arrow with a
large metal head. Open paper between the body, string and bow separates the main shapes.

Riders: broad horizontal horse body, separated near/far legs and hooves, visible head/ears,
muzzle, blaze and mane. The rider sits high over the saddlecloth, with a visible bent leg,
cape, lance, pennant and rein. All illustrations remain native 80×120 pixel art drawn from
Java code in `PixelCardFace`, shown through production `CardFace` / `FxKit` JavaFX rendering.

## Executed checks

Environment: macOS 27.0 arm64; Maven 3.9.15 on Java 25.0.2, Java 17 source target;
JavaFX exporter OpenJDK 26.0.1 / OpenJFX 21.0.5.

| Input/check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Scope against previous native exports | Only Bowmen/Riders in seven nations change | Exactly 14 native PNGs changed; 23 other faces byte-identical by SHA-256 | PASS |
| Pixel/palette/scaling/cache tests | All 37 code faces retain binary alpha, ≤16 colours, crisp enlargement and stable cache | Existing tests pass | PASS |
| Native/3× and 104×156 view | Bow/string/arrow and mounted horse distinguishable at actual card size | `before-after.png` visually inspected at both scales | PASS |
| Nation variations | Same readable class silhouette, matching nation colours and correct crests | `bowmen-preview.png` / `riders-preview.png` inspected for all seven nations | PASS |
| Production match view | Redesigned units visible in client hand/row | `match-table.png` exported from `CardTableView` | PASS |
| `mvn test` | Zero failures/errors/skips | 153 tests, 0 failures, 0 errors, 0 skipped; 28.033 s | PASS |
| `mvn -DskipTests clean package` after passing tests | Fresh runnable JAR | BUILD SUCCESS; 6.792 s | PASS |
| Whitespace | No errors | `git diff --check` passed | PASS |

This establishes visual inspection and renderer invariants, not a measured usability or
performance improvement. No new tests asserting arbitrary drawing coordinates were added.

## Reproduce

After compiling/packaging, from repo root:

```sh
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard docs/verification/2026-10-21-bowmen-riders
```

`before/pl-bowmen.png` and `before/pl-riders.png` are the previous native exports copied from
`../2026-10-21-all-unit-pixel/`. The preview tool loads those two evidence images before entering
the JavaFX thread, then composes `before-after.png`; the AFTER cards use the production renderer.
The game never loads the before images. If they are absent, the tool omits only the comparison.

All other galleries/native/4× outputs follow `tools/RenderPixelCard.java`. Native images are
code-drawn; PNGs here are outputs, not runtime artwork. `match-table.png` uses a real table
component with a deterministic core state after a legal play; it is not a live online match.
