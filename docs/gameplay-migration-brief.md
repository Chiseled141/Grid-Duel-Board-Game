# Deck Duel — Gameplay Change Brief

Build the approved Gwent-inspired card duel with Balatro-style scoring synergies on the cleaned account/lobby shell. The old chess-like engine and gameplay protocol have been removed. Implement the gameplay, rather than only changing the presentation.

Read `AGENTS.md` first. It contains the authoritative rules and project constraints; this brief describes the direction without prescribing an implementation.

## Core experience

Players take turns building a row of cards, creating scoring combinations and disrupting their opponent. There is no piece movement, mana, or card play cost. The interesting decisions should come from which card to play, which combinations to build, and when to replace or disrupt a card.

## Gameplay to implement

- Play exactly three rounds. Bank each round's final score into a match total; the higher total wins, with a draw if tied.
- Each player selects three regions through Deck. Their deck contains 21 Units (seven per region) and 16 chosen Landmarks/Control Spells from those regions, with at most two copies of a named non-Unit card.
- Start each round with eight cards. Alternate one card play per turn and draw one replacement if available. Each player has at most ten plays per round; there is no pass action.
- Each player has an eight-space row. Units and Landmarks occupy spaces; Spells do not. On a full row, playing a Unit or Landmark requires replacing an existing card. End the round when both rows are full or both players have used ten plays.
- Units have printed points and region/class tags. Each Unit pair scores +3 for matching region only, +3 for matching class only, or +9 when both match. Repeated matching tags do not repeat the pair bonus.
- Landmarks provide ongoing Joker-style scoring effects. Their effects stack, and multipliers multiply together. Spells resolve one clear effect and are discarded.
- Scores update from the current row and active effects. Removing, replacing, or disabling cards changes their points and combinations immediately. Between rounds, clear rows, reshuffle decks, and deal fresh hands.

## Scope and freedom

Choose the code structure and implementation steps after inspecting the repository and `docs/claude-handoff.md`. Keep changes bounded and preserve working accounts, menus, room directory, ratings, and historical persistence. Implement matchmaking, chat, reconnect, and rematch for the new game. Carry gameplay through online play, practice, bots, and demo before calling the implementation complete.

Keep the existing Java/Swing/local-server stack. Prioritize working rules and a readable playable interface; polished artwork is not required for this stage.

The board direction and runnable score-panel preview are in `docs/design/card-table-design.md`. Reuse `DuelScorePanel` when wiring up the new match screen; its preview values are not live game state.

The full card catalog and exact effects are still undecided. Do not silently turn placeholder cards or invented effects into approved design. Identify missing design decisions and propose a small starter catalog separately if needed.

Verify the new rules with meaningful tests, run the required project checks, and report what is implemented, what remains, and the actual test results.
