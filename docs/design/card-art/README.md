# Card artwork

**Current set (catalog `cards-neutral-1`):** the 35 Units use this manuscript set (Footmen,
Pikemen, Bowmen, Riders and Knights of each nation); the 11 neutral Spells and 6 neutral
Landmarks use [`drafts/neutral-v4/`](drafts/neutral-v4/). Chaplain, Envoy and the national
Landmarks and Spells were retired; their sources are archived in [`retired/`](retired/README.md).
The description below records the original manuscript pass.

## Manuscript set

78 individual PNG assets: **77 fronts and one shared card back**, each 1024 × 1536 pixels. The fronts cover every ID in the existing `cards-proposal-1` catalog: seven regions with seven Units, two Landmarks and two Control Spells per region.

- [Browse all cards](gallery.html), with nation and type filters.
- [PNG asset directory](../../../src/main/resources/assets/cards/manuscript-v1/).
- [Manifest and generation prompts](manifest.json).
- [Heraldry references and historical choices](heraldry-sources.md).

Generated with the built-in image generation tool, using the approved blacksmith illustration as the style reference. Each front has its own illustration, title and nation crest. The neutral back is shared across every nation and card type. Poland uses an ivory outer cover; Hungary bronze, Bohemia plum, Austria crimson, Styria green, Croatia charcoal/ivory and Transylvania blue/gold.

Transylvania intentionally uses a simplified later seventeenth-century-inspired shield, as approved by the user. Frame palettes are creative game identifiers; they are not historical uniform colors. Heraldry is painted interpretation rather than a facsimile of one dated seal.

The filenames match the existing catalog IDs. Images contain the short title, but no baked-in point values or effect text. The proposal catalog, card effects and balance have not been changed or approved by this art pass. The game uses this art. These PNGs are the source copies and are left out of the built jar; the client loads 320×480 JPEGs from `src/main/resources/assets/cards/runtime/`. After changing or adding a PNG, regenerate them with:

```sh
java tools/ResizeArt.java src/main/resources/assets/cards/manuscript-v1 src/main/resources/assets/cards/runtime 320 480 jpg
```

On the table each front shows its illustration, painted title and crest; the game adds the points badge (Units), class tags and any second region, a short effect line (Landmarks and Spells) and disable markers. Full rules text stays in the hover and detail line. A card without art falls back to the procedural emblem face; `CardArtworkTest` fails if any catalog card lacks art.

Validation checks PNG integrity, dimensions, unique file hashes, exact catalog ID coverage and gallery image paths. Automated title recognition is recorded in `title-check.json`. Visual reviews and any correction prompts are recorded in the manifest; these are draft game assets for the user's review.
