# Deck Duel

Deck Duel is being redesigned as a short, two-player card game with Gwent-style turns and Balatro-style scoring combos.

## The planned game

Each player chooses three Atlas regions. The game builds 21 Units—seven from each region. Each region has its own Landmark and Control Spell library; players choose 16 non-Unit cards only from their three selected regions, in any mix of Landmarks and Spells. Cards from unselected regions are unavailable. Decks have 37 cards total, with up to two copies of any named Landmark or Spell.

Each round, players start with eight cards and take turns playing one card. They draw a replacement after each play and can play up to ten cards per round. Each player has one row with eight spaces. Units and Landmarks use a space; Spells do not. If your row is full, you can play a Spell or replace one of your cards. The round ends when both rows fill or both players use their ten plays.

Units have printed values from 1 to 5, plus region and class tags. Each matching Unit pair adds +3 for a region match, +3 for a class match, or +9 when both match. Count a pair once even if it shares several tags of the same kind. The score updates live from cards currently on the rows, so replacing a card can change the score and combos.

Landmarks have no printed points. Each has one Joker-style effect, and Landmark multipliers multiply together—for example, ×2 and ×3 make ×6. Control Spells have one effect, then are discarded. Effects can score, multiply, disable, or remove cards as described by each card.

The card library and extra card-specific combo effects are future design work. The base Unit-pair bonuses above are already defined.

Play exactly three rounds. Each round’s final score is added to your match total; the higher total after round three wins. A tied round simply adds equal points, and a tied match total is a draw.

## Current implementation status

The card-game rules above are the approved redesign, but they are **not implemented yet**. The current Java application still runs the previous 7×7 board game. The commands below build and run that existing version.

## Run the current application

Install JDK 17 or newer and Maven. From the folder containing pom.xml:

    mvn package
    java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db

Open a second terminal and run:

    java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555

Register or log in. Choose Practice to play a bot, or start a second client to play another person through Find match or a room.

The demo and bot fleet are also available:

    java -jar target/deck-duel.jar demo --seed 42
    java -jar target/deck-duel.jar bots --host 127.0.0.1 --port 5555 --bots 8 --games 4

## Project notes

The app uses Java 17, Swing, localhost TCP, and SQLite. AGENTS.md contains the detailed agent instructions, technical constraints, and authoritative target rules. Run mvn test after code changes.
