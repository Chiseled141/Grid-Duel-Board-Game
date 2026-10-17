# Randomized objective layout and engraved board details

User-approved scope: two scoring squares in the center row (y=3), plus two mana
squares, one in each adjacent row (y=2 and y=4). Use the entire board width and
180-degree mirrored placements for equal access. Keep rewards at own-turn start.

- Generate the public layout once from authoritative match RNG. Score columns
  are x and 6-x with x in 0..2; mana pair is (x,2)/(6-x,4) with x in 0..6.
- Store a validated immutable serializable layout in state and every recipient
  view. State copies/reconnect/retries retain it. Never reveal seed/deck order.
- Rewards and bots use the actual match layout, including +2 score for holding
  both scoring squares. Score target and existing kill scoring remain unchanged.
- Replace rectangular color blocks and printed reward labels with engraved
  brass medallions, crown and blue gem details. Hover explains the reward timing;
  marks remain identifiable around occupying pieces and rotate with the board.
- Bump protocol for the changed view shape; update help/spec/harness, cover layout
  constraints, seeded variation, copies/wire/reconnect, both reward squares, bots,
  and both viewer orientations. Run full tests, independent review, clean verify.

## Verification

Implemented with immutable `ObjectiveLayout` in state and every public view,
rules v3/protocol 4, and no additional dependencies. Existing score target, kill
points and own-turn reward timing are unchanged. Art is drawn directly in the
existing Swing board renderer; no raster generation was needed.

Native full suite: 530 passed, zero failures/errors/skips. Independent read-only
review and native full suite: 530 passed, no actionable findings. New tests cover
seed variation, symmetry/central rows, immutable/invalid layouts, both scoring and
mana rewards for both sides, rejection/copy stability and bot choices. Wire and
reconnect tests preserve the generated layout. UI tests verify exactly four
marked tiles and reward tooltips in both orientations, occupied-square corner
markings and clearing the layout with a null view.

Visual QA: `artifacts/random-objectives/game.png`. Replaced colored rectangular
blocks and reward text with brass crown/gem medallions and corner fittings.

Follow-up visual change: replaced medallions with full-tile translucent gold (score)
and blue (mana) highlights, subtle edge glow, and no icons or text. Stone texture,
randomized positions, reward timing and hover explanations remain. Help names the
highlight colors. Rendering tests now check the tinted tile surface as well as
occupied visibility, exact objective locations and both orientations.
