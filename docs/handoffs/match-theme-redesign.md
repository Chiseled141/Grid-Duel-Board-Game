# Match UI aligned with the floating menu

## Request and acceptance

Match the in-game scene and UI to the menu; remove Card Details, VS Practice Bot,
Your Hand and bottom choose-card instruction banners. Enlarge board/own cards,
make live skill text clearer, and replace two labeled/counted deck displays with
one decorative draw-animation source. Preserve gameplay rules, privacy and actions.

## Implementation

- GamePanel inherits ValleyMenuPanel and ClientFrame injects shared ClientSettings:
  the selected menu GIF also plays in-game, with reduced-motion/lifecycle behavior.
- Removed CardInspectorPanel and its hover-reference wiring, details tab, opponent
  VS banner, hand label and bottom instruction strip. Existing context messages
  remain on board/Cast Skill tooltips; legal-target highlights/directions stay.
- Narrower rails, smaller opponent thumbnails and removed banners free board space.
  Own hand height adapts232..310px; cards grow up to236px wide instead of176px, with
  hover-to-front when a narrow window needs overlap. Skill illustration consumes
 34% rather than46% of card height; wider cards use13px description text and15px
  title, with measured line spacing. Full live tooltip remains available.
- One DeckStackPanel is the common source of both movement/skill flights. No count,
  title or deck-empty labels. Hidden opponent skill flights remain identity-free.
  Both authoritative decks/counts/recycling remain unchanged in engine/protocol.
- MatchStyle supplies floating ivory/gold controls and text shadows. Board uses
  warm ivory/olive with bronze frame. Badges, history and chat use translucent dark
  surfaces; chat bubbles/composer use matching colors. Player accents remain clear.
- Played history, mana, turn, chat, cast/direction/skip/end/menu/resign and Foresight
  behavior remain. No networking, DB or engine changes.

## Files

GamePanel, ClientFrame, MatchStyle, DeckStackPanel, CardHandPanel, SkillCardPanel,
BoardPanel, PlayerBadgePanel, PlayedCardsPanel, ChatPanel; removed CardInspectorPanel.
Tests: DeckPresentationTest now checks one visual origin while retaining all
privacy/retry/lifecycle assertions; GamePanelLayoutTest expects one pile;
CardInspectorTest replaces six obsolete inspector assertions with card tooltip,
privacy/live-balance, removed-banner, cast readiness and direction/pause regressions;
TargetingPresentationTest checks the same guidance through board/action tooltips.
Spec presentation section and architecture updated to reflect explicit user changes.

## Validation

`mvn test`:313 tests, zero failures/errors/skips. Rendered1280x660,1440x820 and
1960x1180 scenes plus selected Mountain valley scene. Inspected large and minimum
layouts: cards stay below board; dark translucent rails retain scenery and controls.
Independent review found and fixed a light default background behind the played-card
scroll pane; scroll.setOpaque(false) and a regression assertion now cover it.
Review also exposed an existing off-EDT practice presentation test race: callbacks
were queued while the test immediately asserted delivery. Its scenario/assertions
now run on the EDT, matching model ownership. Full fix suite:313 tests, zero
failures/errors/skips. Final independent `mvn clean verify`:313 tests, zero failures/errors/skips,
shaded JAR built. Independent scoped review PASS after targeted fixes; no unresolved
actionable findings. Opened the rebuilt game client for the user to inspect.
