# Deck Duel pxl card artwork

All ten neutral Spell illustrations and the shared card back are authored in
`spells.px.js` with [claude-pxl](https://github.com/crsolver/claude-pxl). The existing
35 Java Unit figures, nation colours and crests are retained. No rules or catalog values change.

The native canvas is 80×120 with at most 16 colours including transparent corners.
The source defines the frame and reusable outlined parts (face-down and face-up cards, chain
link, padlock, open shackle, great helm, hook). One visual language runs through the set: a
closed padlock means a disable, an open padlock means Clear Disable, and face-down cards stand
for hidden hands and decks. Names and two concise effect lines are composed from `CardDef` by
`PixelSpellArt` (title at y 8, art window y 17–91, captions at y 96 and 103, clear of the corner
ornaments); full rules remain in card details. The compact bitmap text keeps the complete
Reconnaissance title within the frame.

Install claude-pxl in `~/.claude/skills/pxl-pixel-art` with Node ≥22.18, then run from the
project root:

```sh
node ~/.claude/skills/pxl-pixel-art/bin/pxl.js build art/cards/pxl/spells.px.js --out /tmp/deck-duel-pxl --scale 4
node tools/export-pxl-cards.mjs
node tools/export-pxl-cards.mjs --check
mvn test
mvn -DskipTests package
```

The exporter also accepts the path to a different claude-pxl checkout as its first argument.
It validates dimensions, flat alpha and palette size, then writes `PxlCardArt.java` as
horizontal colour runs. That generated Java source is part of the application; Node, pxl
and PNG files are not runtime dependencies. Edit the pxl source and regenerate rather
than editing the exported data. Normal Maven builds use the checked-in generated Java.

For the live authoring viewer:

```sh
node ~/.claude/skills/pxl-pixel-art/bin/pxl.js serve art/cards/pxl/spells.px.js --no-out
```

To compare pxl output with runtime Java and export actual JavaFX card/table previews after packaging:

```sh
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java tools/VerifyPxlArt.java
java -Djava.awt.headless=true -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.VerifyPxlArt /tmp/deck-duel-pxl/sprites
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard /tmp/deck-duel-cards
```

The last command requires a desktop graphics session. `spells-preview.png` shows all ten
Spells and the back; `match-table.png` shows a deterministic legal sample match through
the production table renderer. The sample does not connect to a server or change accounts.
Java2D/JavaFX use nearest-neighbour sampling for faces and backs, including deck piles,
opponent hands and flying cards. The shared back receives no card identity or hidden state.
Legacy painted Spell resources remain available only as fallbacks.
