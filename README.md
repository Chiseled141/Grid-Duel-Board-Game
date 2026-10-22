# Deck Duel

A two-player card duel being built around Gwent-inspired turns and Balatro-style scoring combinations.

Start directly from **Play**: everyone plays the same automatic 45-card deck (all 35 Units and one of each of the 10 Spells), dealt as the match opens. Then find an online opponent, host or join a room, or practise against an easy or hard bot. **History** lists your recorded matches. Matches run on an authoritative local server with privacy-filtered views, reconnect and rematch; recorded matches update win/loss records.

The cards are listed in [the catalog notes](docs/card-catalog.md): seven nations × Pikemen 2, Bowmen 3, Riders 4, Knights 5 and Axemen 4 points. Each row's best Balatro-style formation (such as Full Muster, +20 ×2) adds points and a multiplier. The Unit library and deck are approved; formation values and a few edge cases are provisional. All 45 cards render as pixel art from Java code. The 35 Unit figures retain their coordinated nation colours and crests; all ten neutral Spells and the shared card back are pixel drawing data in `PxlCardArt`.

## Build and run

Use JDK 17 or newer and Maven:

```sh
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
java -jar target/deck-duel.jar demo --games 1          # two bots, throwaway server
java -jar target/deck-duel.jar bots --port 5555 --count 4 --games 2
```

Tests use temporary databases and local ephemeral ports. Swing and JavaFX tests need a graphical session. Client and server use protocol 13; older applications cannot connect.

On the table, click a hand card and then a highlighted space or target (or drag it there); **Draw** (or the deck, or D) makes one of your three manual draws per round. **Skip turn** (or S) keeps your cards and board unchanged and uses one of your ten round plays. Hand cards are larger than row cards; rest the pointer on a row card for a large preview. Your formation and its multiplier head the score breakdown. Click a Spy-revealed card for a large image and full details (Esc closes). Arrow keys pick a card, 1–7 choose a space, Enter casts a Spell that needs no target, Esc deselects.

## Rules

The approved rules are in [AGENTS.md](AGENTS.md); the [handoff](docs/claude-handoff.md) maps them to code and lists what remains. The [board and score-panel notes](docs/design/card-table-design.md) describe the table design.
