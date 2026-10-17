# Grid Duel

A two-player strategy game on a 7×7 board, built with Java 17 and Swing.
Each player controls seven identical pieces and combines public movement cards
with private skill cards. The authoritative server handles accounts, rooms,
match rules, reconnects, Elo and SQLite persistence.

## How to run the game

Install **JDK 17 or newer** and **Maven**. Open a terminal in this project's folder
(the folder containing `pom.xml`).

### 1. Build the game

```bash
mvn package
```

Wait for `BUILD SUCCESS`. This creates `target/onitama.jar`.

### 2. Start the server

In the same terminal, run:

```bash
java -jar target/onitama.jar server --port 5555 --db data/onitama.db
```

Keep this terminal open while playing. The server saves accounts and results in
`data/onitama.db`; no separate database installation is needed.

### 3. Open the game

Open a **second terminal** in the same project folder and run:

```bash
java -jar target/onitama.jar client --host 127.0.0.1 --port 5555
```

In the game window:

- **Register** an account, or **Login** if you already have one.
- Choose **Play → Practice** to play against a bot.
- For two players, open another client with the same command, log in with a
  different account, and choose **Play → Find match** in both clients to pair
  automatically, or create/join a room from **Play**. Find match waits for another
  player on the same server; **Cancel search** stops waiting.
- In the main menu, Play and Practice, use the mouse or **arrow keys / WASD**
  to choose an action, **Enter** to open it, and **Esc / Backspace** to go back. Backspace still
  deletes text when editing a room code.
- When a new match finishes loading, press **Enter Match**. **Return to Match**
  opens an ongoing match immediately.

Next time, repeat steps 2 and 3. Rebuild only after changing or updating the code.
To stop, close the game windows and press **Ctrl+C** in the server terminal.

If the game cannot connect, check that the server is running and both commands
use port `5555`. If the server says the port is already in use, an existing server
may already be running.

## Gameplay

- Each turn: optional movement, optional one skill, then End Turn. Passing is legal.
- Shared movement deck: 16 cards, two in each player's hand. A played card returns
  to the deck, which is shuffled before drawing a replacement; the same card may return.
- Shared skill deck: 24 unique effects, three starting skills per player. Successfully
  cast skills are spent permanently; draw a replacement while the deck has cards.
- Start with 2 mana, gain 1 at each own-turn start (including the first), cap 5.
- Movement jumps. Friendly landing is illegal; landing on an enemy eliminates it.
- Win by eliminating every enemy or reaching 8 points. Each enemy eliminated
  earns 1 point; holding a gold square until your turn starts earns +1 point.
- Blue squares award +1 bonus mana at your turn start, within the 5-mana cap.
- Otherwise, 60 completed player turns is a draw. Elimination and score wins
  take priority over the turn limit.

These are bundled defaults. The [authoritative spec](GRID-DUEL-BOARD-GAME_SPEC.md)
defines effects, targeting and configurable balance.

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
