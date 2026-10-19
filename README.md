# Deck Duel

A two-player card duel being built around Gwent-inspired turns and Balatro-style scoring combinations.

The previous chess/piece-and-skill game has been removed. The current application is a clean **menu and account/lobby shell**; the new gameplay is not implemented yet.

The medieval menu, music, login/registration, hosted-room directory, rankings, historical account/results storage, and Deck region browser remain. Play → Practice → Preview table opens the new empty card-table layout with its score sidebar. Online join and find-match requests clearly report that gameplay is not ready. Demo and bots are unavailable.

## Build and run

Use JDK 17 or newer and Maven:

```sh
mvn clean verify
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
```

Tests use temporary databases and local ephemeral ports. Swing tests need a graphical session. Client and server use shell protocol 8; old protocol 7 applications cannot connect.

## New gameplay direction

Players select three regions and build a 37-card deck: 21 Units and 16 Landmarks/Control Spells. Alternate one play per turn, draw replacements, and build combinations on an eight-space row. Play exactly three rounds; the higher cumulative score wins. The complete approved target rules are in [AGENTS.md](AGENTS.md). The card library is still undecided.

For implementation, start with [the Claude handoff](docs/claude-handoff.md) and [the short gameplay brief](docs/gameplay-migration-brief.md). The [board and score-panel notes](docs/design/card-table-design.md) include a runnable visual preview. None of these target rules should be mistaken for implemented gameplay.
