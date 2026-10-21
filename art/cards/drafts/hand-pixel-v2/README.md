# Hand-pixel v2 — redesign draft (2026-10-21)

**Status: integrated on 2026-10-21** — `PixelCardFace` now draws these figures (verified pixel for
pixel against `native/`). This folder keeps the review snapshot and the before/after evidence.

The user found the current Java pixel Units "too AI-looking". Causes identified in the runtime
art: free polygons with odd vertices give uneven, jagged diagonals; faces are blank tan blocks
(skin borrowed the wood colour); the same grey steel and muted tones everywhere; a busy
cross-stitch backdrop; and many tiny details at 1-pixel scale.

This draft keeps everything around the figure and replaces only the figure:

- Each figure is a hand-placed **character map** (30 × 38 cells), one cell = **2 × 2 native
  pixels**, so every pixel is deliberate and edges step cleanly.
- One-cell dark outline, light from the top left, 2–3 tones per material.
- Real skin tones (`#E8B48A` / `#B97A57`) with eyes; nation cloth palettes unchanged.
- Nation crest in paper colour on the Knights' and Axemen's shields and on the chest of Pikemen
  and Bowmen; Riders rely on the header crest and border. Plain paper backdrop with a flat shadow.
- Kept from runtime: 80×120 grid, 4px nation border, points, nation label (Transylvania on two
  lines), header crest and class label, all read from catalog metadata. The class icon under the
  points is dropped because the figures now use that space.

Figures: Pikemen (kettle hat, gambeson, long pike), Bowmen (hood, quiver over the shoulder,
bow with nocked arrow), Riders (rider seated on a side-view horse, lance pennant), Knights
(great helm, plume, sword raised, crest shield, cape), Axemen (nasal helmet, beard, mail
collar, a one-handed bearded axe raised and a round nation shield with the crest — replacing a
two-handed grip that could not read at this resolution).

## Files

- `before-after.png`: current runtime (top) and this draft (bottom), Poland, 3×.
- `hand-size.png`: the draft at the in-hand size 104×156, nearest neighbour.
- `all-units.png`: all 35 Units, 2×.
- `native/`: 80×120 transparent faces for all 35 Units.
- `source/HandPixelRenderer.java`, `source/ExportHandPixel.java`: standalone Java 17, default
  package, not compiled by Maven or used by the game.

## Checks (exporter assertions)

All 35 faces: 80×120, transparent corner, alpha only 0 or 255, at most 16 colours.
No PNG is read to make a face; the "before" row reads the runtime's exported natives from
`docs/verification/2026-10-21-axemen-arms/` for comparison only.

## Reproduce

```sh
javac --release 17 -d /tmp/hand-pixel-v2 art/cards/drafts/hand-pixel-v2/source/*.java
java -Djava.awt.headless=true -cp /tmp/hand-pixel-v2 ExportHandPixel art/cards/drafts/hand-pixel-v2 src/main/resources/cards/catalog.txt docs/verification/2026-10-21-axemen-arms
```

Integration (after approval) means moving the maps and `sprite()`/`badge()`/`shadow()` into
`PixelCardFace`, updating its tests and guide, then rebuilding and restarting the game.
