# Skill card artwork — first 24

24 distinct effects, one copy each. This is a proposed card set for the discussed 7×7 redesign, not an implementation of those rules in the existing game.

## Files

- `index.html`: visual gallery; open locally in a browser.
- `faces/`: full-face PNG assets, generated with the built-in image generation tool. Nature, stones, weapons and structures; no human figures or faces.
- `cards.json`: editable names, mana costs, rules and asset paths. Not currently loaded by the Java game.
- `prompts.json`: exact generation prompts and reference path.

The current PNGs include printed costs and rules. Editing JSON alone does not update that printed text. For game integration, render text with Swing over separate illustrations so balancing does not require regenerating images.

## Proposed shared definitions

These conventions clarify this draft and must be agreed before implementing it. The existing authoritative game spec and engine are unchanged.

- Distance means Manhattan distance: |dx| + |dy|. Adjacent means horizontally or vertically adjacent unless stated otherwise.
- Every selected piece is on the board. All destinations must be on the board. A skill cannot leave two pieces on one square.
- Choose one or two targets for “up to 2”; never zero. Multiple selected pieces must be different and have distinct destinations.
- Unless a rule explicitly eliminates an enemy, a skill never captures by landing on one.
- “Both destinations” for an up-to-two effect means every selected destination, including when only one piece is selected.
- Gust and Scatter resolve simultaneously; destinations must be empty before the skill. Rally resolves in the player's chosen order, with the anchor stationary and distance checked against that anchor. Advance moves both selected pieces one row toward the opposing starting edge.
- Crossfire's two allies must lie on opposite sides of the target, on the same row or column. Chain Strike measures the second hit from the first target's original square.
- “Starting row” means a player's initial board edge, regardless of where their pieces are now.
- Pay mana, resolve the skill, permanently discard its single copy, then draw one replacement if the skill deck has cards. Foresight rearranges the deck before that replacement draw. Movement-card recycling is separate from skill-card disposal.

For a first playtest, a mana cap of 5 and at most one skill per turn support this set's 0–5 costs. These are balance proposals, not verified values. Starting hand size, mana income, scoring, piece types and victory rules still need to be settled against the redesign; the artwork does not establish them.

## Style reference

Inspected existing `src/main/resources/assets/cards/blue/tiger-blue.png` and `src/main/resources/assets/cards/red/dragon-red.png`. Generation uses the tiger card only as a parchment/ink/layout reference, with new symbolic subjects and no movement grids. Existing card assets are untouched.


## Balance note for the first playtest

Dash and Vault currently cost 2 for the same exact two-square move, but Vault additionally allows jumping. This deliberately remains a known draft imbalance: evaluate a lower Dash cost or a different restriction after testing hand utility. Neither the unique effects nor the printed costs should be treated as a balanced final deck.
