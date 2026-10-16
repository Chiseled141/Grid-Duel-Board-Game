# Grid Duel

A two-player strategy game on a 7×7 board, built with Java 17 and Swing.
Each player controls six identical pieces and combines public movement cards
with private skill cards. The authoritative server handles accounts, rooms,
match rules, reconnects, Elo and SQLite persistence.

## Gameplay

- Each turn: optional movement, optional one skill, then End Turn. Passing is legal.
- Shared movement deck: 16 cards, two in each player's hand. A played card returns
  to the deck, which is shuffled before drawing a replacement; the same card may return.
- Shared skill deck: 24 unique effects, three starting skills per player. Successfully
  cast skills are spent permanently; draw a replacement while the deck has cards.
- Start with 2 mana, gain 1 at each own-turn start (including the first), cap 5.
- Movement jumps. Friendly landing is illegal; landing on an enemy eliminates it.
- Eliminate all enemy pieces to win. Otherwise 60 completed player turns is a draw.
  Elimination takes priority over the turn limit.

These are bundled defaults. The [authoritative spec](GRID-DUEL-BOARD-GAME_SPEC.md)
defines effects, targeting and configurable balance.

## Build and run locally

Requires JDK 17+ and Maven. Dependencies are SQLite JDBC and test-only JUnit 5.

```bash
mvn clean verify
java -jar target/onitama.jar server --port 5555 --db data/onitama.db
```

In another terminal:

```bash
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
```

Register/login, then create a room or join using its code. For two players on one
computer, launch two clients. The lobby also offers local practice against Rookie,
Senior or Legend heuristic bots; practice does not change Elo.

The Swing UI includes illustrated cards in a bottom hand, public movement and
face-down opponent skills, two deck stacks with draw animations, both players'
avatars and mana, legal targets, reduced motion, and a leaderboard.

Additional modes:

```bash
java -jar target/onitama.jar demo --seed 42
java -jar target/onitama.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4
```

Server/demo accept `--config path/to/balance.properties`. See
[the balance template](docs/examples/game-balance.properties). Matches retain an
immutable rules snapshot; editing a file does not change an ongoing match.
Skill text and costs are rendered from that snapshot.

## Validation and source map

`mvn test` covers the Grid Duel engine and skills, atomic rejection, privacy,
protocol retries/reconnects, SQLite/Elo, bots and Swing presentation. Tests use
local ephemeral ports and temporary databases; no running server is required.
Some older engine classes remain solely for regression and wire-rejection tests.

- `src/main/java/onitama/core/duel`: live engine, effects and recipient views.
- `src/main/java/onitama/config`: validated balance loader and skill text.
- `src/main/java/onitama/client`: Swing client, state observers and bot harness.
- `src/main/java/onitama/{server,net,db}`: sessions, protocol and persistence.
- `src/main/resources`: bundled config, artwork, fonts and audio.
- `data/`, `results/`: local databases and bot output; do not commit personal data.

Read [AGENTS.md](AGENTS.md) before coding and
[the Step 6 readiness handoff](docs/handoffs/step-06-readiness.md) before continuing.
