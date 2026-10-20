# Spell artwork

The 11 PNGs directly in this directory are the current Spell source artwork.
Filenames match the Spell IDs in `src/main/resources/cards/catalog.txt`
(`cards-neutral-2`). Each source is 1024 × 1536 pixels.

This set was redrawn using the actual Unit illustrations as style references:
clean dark outlines, painted shading, stronger colors and the matching gold frame.
Spells use a neutral charcoal/plum frame, scroll symbols and large effect text.
Every illustration uses wildlife, objects or scenery instead of people or hands.
Symbolic subjects add no gameplay rules.

- [View all 11 cards](gallery.html)
- [Exact generation prompts and reference paths](generation-prompts.json)
- [Asset verification](verification.json)

Created with built-in image generation. Older Spell sets and draft subfolders were
removed at the user's request. Runtime copies are generated at 480 × 720 in
`src/main/resources/assets/cards/runtime/` using `tools/ResizeArt.java`.
