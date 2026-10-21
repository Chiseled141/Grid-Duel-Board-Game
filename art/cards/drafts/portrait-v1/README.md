# Portrait v1 draft (review only)

One Poland Knights card in a card-game portrait style: chest-up figure in a large
framed art window with a background (dusk sky, castle, Polish banner). Every native pixel
is placed by `source/PortraitDraft.java` at 80×120 (no 2×2 cells), 15 colours plus
transparency, binary alpha. Frame, header, crest and lettering match the runtime card.

Not used by the game. If approved, the same layout extends to the other four classes and
seven nations before integration into `PixelCardFace`.

Run from `art/cards/drafts/`:

    java portrait-v1/source/PortraitDraft.java portrait-v1 hand-pixel-v2/native/pl-knight.png

Outputs: `native/pl-knight.png`, `compare-3x.png` (current vs draft), `hand-size.png`.

References used for the approach: drawing directly at low resolution and dropping
non-essential detail; one shade plus outline over muddy multi-tone shading; light from one
side; silhouette distinct from the background; framing that keeps the chin and hands whole.
