# Deck Duel

A two-player card duel being built around Gwent-inspired turns and Balatro-style scoring combinations.

Start directly from **Play**: everyone plays the same automatic 45-card deck (all 35 Units and one of each of the 10 Spells), dealt as the match opens. Then find an online opponent, host or join a room, or practise against an easy or hard bot. **History** lists your recorded matches. Matches run on an authoritative local server with privacy-filtered views, reconnect and rematch; recorded matches update win/loss records.

The cards are listed in [the catalog notes](docs/card-catalog.md): seven nations (Ashkar, Asterion, Dravorn, Nerath, Solmara, Sylvaren, Vaelora) × Assassin 2, Bowman 3, Guardman 4, Knight 5 and Wizard 6 points. Each row's best Balatro-style formation (such as Full Muster, +20 ×2) adds points and a multiplier. The Unit library and deck are approved; formation values and a few edge cases are provisional. All 45 cards and the shared card back use painted art (`src/main/resources/assets/cards/`).

## Build and run

Use JDK 17 or newer and Maven:

```sh
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
java -jar target/deck-duel.jar demo --games 1          # two bots, throwaway server
java -jar target/deck-duel.jar bots --port 5555 --count 4 --games 2
```

Tests use temporary databases and local ephemeral ports. Swing and JavaFX tests need a graphical session. Client and server use protocol 20; older applications cannot connect.

For an online release (server hosting, update channel and the Windows installer with bundled Java), follow [the short release path](docs/online-release.md).

On Windows, `launcher-win\build-cards-exe.bat` builds `Cards.exe`, which starts the local development server and the client from one double-click; see [the Cards.exe guide](docs/cards-exe.md).

On the table, click a hand card and then a highlighted space or target (or drag it there); **Swap** (or the deck, or W/D) starts a swap: click up to three hand cards in all per round, then **Swap** again to discard them and draw as many (**Cancel** or Esc backs out). **Menu** (or Esc) pauses the table with Resume, Settings, How to play, Surrender and Quit game. **Skip turn** (or S) keeps your cards and board unchanged and uses one of your ten round plays. Hand cards are larger than row cards; Spell hover shows its rules, while Units have no hover magnifier. Your formation and its multiplier head the score breakdown. Click a Spy-revealed card for a large image and full details (Esc closes). Arrow keys pick a card, 1–7 choose a space, Enter casts a Spell that needs no target (the **Cast** button sits under the score rail), Esc deselects.

Match history supports opponent search and result/online/practice filters over the loaded entries.
Personal replays have a seek bar, round jumps and a Details drawer with a
searchable action journal and score changes. Guide examples use only Next and compact Details.
Home/End in personal replays seek to the ends; Page Up/Page Down jump
between recorded rounds. Seeking pauses playback. See [replay controls](docs/replays.md).

## Rules

The approved rules, architecture and open design work are in [AGENTS.md](AGENTS.md). The [board and score-panel notes](docs/design/card-table-design.md) describe the table design; [verification notes](docs/testing/README.md) list reproducible checks and current results.
