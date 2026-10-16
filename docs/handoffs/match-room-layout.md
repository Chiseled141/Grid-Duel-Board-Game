# Match room and card layout — 2026-10-17

The match screen has a dedicated medieval campaign-room background. Login and
lobby keep the original animated battlefield. The new still is loaded off the EDT
and fills the match viewport; no menu GIF is loaded by GamePanel.

- Removed the Played Cards history panel. ClientModel still records accepted public
  history and resets it on new matches; privacy and protocol behavior are unchanged.
- One neutral decorative draw pile sits at the lower left, close to hand-card size.
  Skip Movement, Cast Skill (when available), direction selection and End Turn sit
  above it. Both actual gameplay decks keep their original independent rules.
- Latest own/opponent plays use larger side slots, outside all board squares.
- Movement and skill hover listeners work with CardPlayOverlay gesture binding,
  including its null legacy click callback. Existing reduced-motion and removal
  cancellation remain active.
- `DeckStackPanel.topBounds()` still supplies the live draw-flight origin after
  resizing or docking. Hidden opponent skills still carry no identity.

## Artwork and visual QA

`src/main/resources/assets/match-room.png` was generated for this task with the
imagegen skill: a quiet medieval stone strategy room, worn walnut table, narrow
left window, restrained afternoon light and flat quiet areas behind the interface.
No lettering, playing cards, pieces, people or battlefield landscape were baked in.
Original generation: `exec-64eac904-b54b-4d4c-9502-123792fccd48.png` in this thread's
generated_images directory. The project bundles its own copy.

`tools/RenderMedievalPreview.java` renders the full interface, including sample
public played-card faces, to `artifacts/medieval-preview/game.png`. These sample
faces are confined to the offline preview tool.

## Verification

Native `mvn test` and `mvn clean verify`: 331 tests, zero failures/errors/skips.
Independent Agent 2 native full-suite review: 331 passed, no blocking findings.
Clean JAR inspection confirms match-room.png is bundled and PlayedCardsPanel is absent.
Responsive layout coverage: 1280×660, 1440×820, 1960×1180; Blue/Red viewers;
3/6-skill hands; deck/action placement and size; larger played-card slots; all
board ranks, chat, badges and cards fit. Hover regression exercises the actual
GamePanel hand and stops its timers under reduced motion. Existing draw/play,
privacy, reconnect and lifecycle tests remain green.
