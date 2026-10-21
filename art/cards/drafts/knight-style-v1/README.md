# Four Unit drafts — Knights as reference

**Review outcome:** the user approved Pikemen, Bowmen and Riders on 2026-10-21.
Those three figures are integrated across all seven nations in the runtime renderer.
Knights stay unchanged. The draft Axemen was rejected in favour of the previous runtime
Axemen; do not integrate the diagonal-axe draft below.

This folder preserves the original review snapshot, including the rejected Axemen.
The final selected set is recorded in `docs/verification/2026-10-21-approved-unit-art/`.

The four new Poland figures are Pikemen (2), Bowmen (3), Riders (4) and Axemen (4).
Names, classes and points are read from the current game catalog. Knights (5) are
an unchanged reference, verified pixel for pixel against the existing export.

- Pikemen: broad kettle helmet, full armour, long pike and tall national shield.
- Bowmen: hood, leather over full sleeves, quiver, upright resting bow and a visible string.
- Riders: mounted portrait with a large horse head, bridle, saddlecloth and compact lance flag.
- Axemen: unplumed helmet, mail collar, full sleeves and a two-handed diagonal battleaxe.

All figures use the existing 80×120 Java2D pixel grid, bitmap text, national palette,
frame and material colours. Every shape is authored in Java; PNGs are offline previews.
`source/DraftUnitRenderer.java` is an isolated snapshot, not imported by Maven or the game.
The original draft task left the runtime untouched. Integration is a later task;
use the runtime renderer and final verification folder for the selected set.

## Review files

- `preview.png`: Knights reference and the four drafts at exact 3× scale.
- `hand-size.png`: all five at 104×156 with nearest-neighbour sampling.
- `native/`: transparent 80×120 card faces.
- `png-4x/`: exact 320×480 enlargements.
- `manifest.json`: current catalog hash, card metadata and image hashes.
- `VERIFICATION.md`: executed exporter checks.

## Reproduce from the repository root

```sh
javac --release 17 -d /tmp/deckduel-draft-classes art/cards/drafts/knight-style-v1/source/*.java
java -Djava.awt.headless=true -cp /tmp/deckduel-draft-classes ExportDrafts art/cards/drafts/knight-style-v1 src/main/resources/cards/catalog.txt
```

The approved three figures have been ported into
`src/main/java/deckduel/client/ui/PixelCardFace.java`. Keep both original Knights and
original Axemen unchanged; this snapshot's Axemen is not selected.
