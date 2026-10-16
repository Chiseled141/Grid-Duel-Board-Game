# Opponent draw origin — 2026-10-17

Opponent top-row order is hidden skill backs, public movement faces, then a
neutral deck at the right. The opponent pile is 100×116 logical pixels, close to
the public movement-card size. Local hand and lower-left deck retain their layout.

GamePanel owns a second DeckStackPanel solely as an opponent visual source.
Both movement and skill replacement flights choose the actor's pile and resolve
its live topBounds after layout. Existing 600ms pacing, reveal/conceal policy,
reduced motion and lifecycle cancellation remain. Concealed skills still carry
only a neutral back and anonymous slot; no new hidden identity is constructed.
The engine still has its existing two shared gameplay decks, with no rules or
protocol changes.

Changed runtime: GamePanel. Tests: DeckPresentationTest checks both source piles
for movement, skills, Refocus and Foresight, all actor/viewer color combinations;
GamePanelLayoutTest checks ordering, deck size, single-row fit for 3/6 hidden
skills and original local-pile position across supported window sizes.
Architecture and spec explain the two decorative origins.

Native full mvn test: 494 passed; zero failures/errors/skips.
Visual QA: artifacts/medieval-preview/game.png.
Independent Agent 2 native full suite: 494 passed; no actionable findings.
Final native mvn clean verify: 494 passed; shaded target/onitama.jar rebuilt.
Updated client launched at the user's request.
