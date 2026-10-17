# Existing grass movement and third windmill

User request: animate more grass from the background itself and add a third mill
using background details, with individual treatment rather than repeated new grass.

## Implementation

RenderBattlefieldLoop traces 32 individual blades with root/control/tip coordinates.
The Bezier paths are selection masks only, never drawn as grass. Every opaque
pixel is copied from the source clean plate; a shared ownership mask prevents
one original pixel from appearing in multiple moving cutouts. Original locations
are cleared to nearby painted foliage and unrotated reconstruction is asserted
exact. Row deformation bends the tip while displacement vanishes at the root.
The last root pixels remain on the fixed plate. Each blade has its own phase and
2.5–3.7 source-pixel amplitude with a small secondary periodic term.

Third mill: source (901,540), scale .36. Tower copied from the main mill in the
clean plate; sails copied from the main mill in the old battlefield.png frame.
The retained RGBA sail sprite contains only sampled source colors. Its fixed
body and separately rotating sails are drawn at .84 opacity for distant haze.
The previous two mills, flower sway, flag, clouds and other motion retain their
original algorithms and timing. Source clean-plate.png is not modified.

## Authoring and provenance

```bash
java -Djava.awt.headless=true tools/RenderBattlefieldLoop.java /tmp/grid-duel-meadow-motion artifacts/meadow-motion/details
ffmpeg -framerate 25 -i /tmp/grid-duel-meadow-motion/%03d.png -filter_complex '[0:v]split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle' -loop 0 -y src/main/resources/assets/menu-battlefield.gif
cp /tmp/grid-duel-meadow-motion/000.png src/main/resources/assets/battlefield.png
```

For cutout validation without rendering all frames:

```bash
java -Djava.awt.headless=true tools/RenderBattlefieldLoop.java --verify artifacts/meadow-motion/verified
```

Export includes all 32 transparent blades, a CSV inventory, the prepared plate
and both copied mill parts. The sail extraction can be reproduced from the
original frame with --extract-mill-sails INPUT.png OUTPUT.png. Originals were
preserved in artifacts/meadow-motion/original/ before this edit.

## Validation

The source-pixel probe checked all 32 blades: 1,617 opaque pixels match the original
clean plate exactly. The sail sprite reproduces byte-for-byte from the preserved
original frame. Constructor validation verifies exact unrotated grass reassembly.
Inspected full frames 0 and 67, all blade cutouts and both mill pieces. The native
clip remains 1280×720, 400 frames, 25 fps, 16 seconds, with infinite looping.
Full native mvn test passed 543 tests, zero failures/errors/skips. Added a decoded
GIF region check for grass motion and the third rotor; existing timing/loop,
reduced-motion and hidden-screen tests remain green. Video preview is under
artifacts/meadow-motion/menu-preview.mp4.

Independent read-only review verified the same 32 blades/1,617 source pixels and
frames 000/067, with no actionable findings. Its full native suite and the final
clean mvn clean verify each passed 543 tests with zero failures/errors/skips.
Updated client reopened against the unchanged local server.

## Subsequent user revision

The added third mill was later removed at the user's request. Its compositor code
and source sail asset were removed; the two original mills remain. See
flower-beds-and-distant-village.md for the current scene and rendering instructions.
