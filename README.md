# Deck Duel

A two-player card duel being built around Gwent-inspired turns and Balatro-style scoring combinations.

Start directly from **Play**: everyone plays the same automatic 46-card deck (all 35 Units and one of each Spell) and chooses two of five Landmarks at the start of a match. Then find an online opponent, host or join a room, or practise against an easy or hard bot. **History** lists your recorded matches. Matches run on an authoritative local server with privacy-filtered views, reconnect and rematch; recorded matches update win/loss records.

The cards are listed in [the catalog notes](docs/card-catalog.md): seven nations × Pikemen 2, Bowmen 3, Riders 4, Knights 5 and Battering Ram 6 points. The Unit library and deck are approved; Landmark numbers and a few edge cases are provisional. Unit art lives in `art/cards/units/`; Spells, Landmarks and the card back use drawn faces until their art is added.

## Build and run

Use JDK 17 or newer and Maven:

```sh
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
java -jar target/deck-duel.jar demo --games 1          # two bots, throwaway server
java -jar target/deck-duel.jar bots --port 5555 --count 4 --games 2
```

Tests use temporary databases and local ephemeral ports. Swing and JavaFX tests need a graphical session. Client and server use protocol 11; older applications cannot connect.

At the start, tick two Landmarks and keep them. On the table, click a hand card and then a highlighted space or target (or drag it there); **Draw** (or the deck, or D) makes one of your three manual draws per round. **Skip turn** (or S) keeps your cards and board unchanged and uses one of your ten round plays. Hand cards are larger than row cards and sit higher above the bottom edge. Click a Landmark row or a Spy-revealed card for a large image and full details (L opens Landmark details; Esc closes). The Landmark info icon remains available while targeting Sabotage. Arrow keys pick a card, 1–7 choose a space (1–2 an enemy Landmark for Sabotage), Enter casts a Spell that needs no target, Esc deselects.

## Rules

The approved rules are in [AGENTS.md](AGENTS.md); the [handoff](docs/claude-handoff.md) maps them to code and lists what remains. The [board and score-panel notes](docs/design/card-table-design.md) describe the table design.
