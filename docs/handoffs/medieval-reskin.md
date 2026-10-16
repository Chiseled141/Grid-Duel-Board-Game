# Medieval European presentation

## User decisions

Keep the original sunset battlefield, remove four small hillside flags and lightly
weather the large mast. Retain the original flower/flag/windmill/cloud animation.
Remove the East Asian scene, its music and all old card/piece artwork. Adopt the
approved hand-colored European woodcut study: large movement diagrams with small
heraldic badges; illustrated skill faces show name, live cost and category, with
full rules only on hover/click. Categories use both color and ornament.

## Implementation

- `CardArt`: shared printed parchment frames, neutral backs, movement faces and
  skill faces. Movement destinations are derived from actual offsets and oriented
  for actor **and viewer**, matching the board. Presentation names are tactical
  aliases; canonical IDs, offsets, engine and network contracts are unchanged.
  Cache keys include rule offsets and viewer, and skill keys include live definitions.
- `CardPanel`, `SkillCardPanel`, `CardDetails`: matching card proportions, clean
  faces, non-modal parchment inspection on hover or click. Full configured rules
  remain available; popup timers close on exit/removal and do not select cards.
- `PlayedCardsPanel`, `CardBackPanel`, `DeckStackPanel`: shared faces and identity-free
  backs, including history and draw/play snapshots. Existing overlay components and
  Foresight reuse these components. Opponent skill identities remain concealed.
- `BoardPanel`, `MedievalPieces`, `Theme`, `UiKit`: oak/brass frame, limestone/slate
  squares and three medieval piece styles (steel helms, shields, carved pawns).
  Responsive hand and opponent-row sizing preserves playable board space.
- `MenuTheme`, `ClientSettings`, `BattlefieldMenuPanel`, `LobbyPanel`, `ClientFrame`:
  only Golden fields remains. Old VALLEY preferences fall back safely. Retired
  theme picker and callback are removed; Golden fields music/toggle remains.
- Default and example configuration point to `assets/medieval/skills/`.
- `PrepareMedievalArtwork` splits the approved 6x4 woodcut atlas into 24 runtime
  illustrations. `RenderMedievalPreview` creates all-card and game-layout previews.
  `RenderBattlefieldLoop` now reads its retained cloud from battlefield-animation.

## Cleanup

Removed the old movement-card PNGs, skill-card PNGs/back, East Asian piece sprites,
valley GIF/still/layers/renderer, valley music, animal-icon fallback and old
`output/skill-cards-v1`/valley prompt exports. A clean build removes stale copies
from target before packaging. Historic handoff documents are reference history;
`docs/architecture.md` describes the current runtime.

## Artwork and QA

Built-in image generation supplied the approved woodcut skill atlas at
`output/medieval-cards/skill-atlas.png`; its art direction is recorded in the adjacent
`prompts.md`. Dynamic movement diagrams, card typography/frames/backs, board and
pieces are code-native. No dependencies, gameplay, database or protocol changed.

Preview command (after compilation):

    javac -cp target/classes -d /tmp/grid-duel-preview tools/RenderMedievalPreview.java
    java -Djava.awt.headless=true -cp target/classes:/tmp/grid-duel-preview onitama.client.ui.RenderMedievalPreview artifacts/medieval-preview

Use native macOS Maven tests rather than forcing headless mode: headless font
metrics cause the existing layout tests to differ. Test sockets need localhost
permission in this execution environment.

## Validation

- Final `mvn clean verify`: 330 tests, zero failures/errors/skips; shaded JAR built.
- Independent Agent 2 full `mvn test` and targeted re-check: 330 passed each.
  The review found and verified the fix for Red-view diagram orientation. Tests
  now compare all 16 cards against actual board coordinates for all four
  actor/viewer combinations.
- Inspected all 24 skill faces and the full 1440x900 game preview. Existing layout,
  privacy, Foresight, gesture, history and animation regression tests remain green.
- Clean JAR inspection: 24 new skill illustrations, only Golden fields WAV,
  no old valley/card/piece media or retired icon/menu classes.
- Battlefield GIF retains 400 frames, uniform 40 ms delays and infinite looping;
  flower cutouts and flag/windmill motion were inspected and checked offline.
