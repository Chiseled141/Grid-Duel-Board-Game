# Played cards and chat design

Replaces the default left instructions surface with a public played-card timeline
and redesigns multiplayer chat to match the game's jade/ivory/gold palette.

## Acceptance and behavior

- Latest card plays appear first with artwork, actor (You/Opponent), turn and type.
- Only accepted MovementPlayed/SkillCommitted public events create entries.
- Duplicate/stale/foreign updates add nothing. Opponent hands/private previews are
  never inspected. Existing card-detail inspection stays available in a second tab.
- Practice and online play share the same public history path. Same-match reconnect
  keeps locally observed plays; new matches/rematches clear them. Missed historical
  events are not replayed by the server.
- Chat uses a rounded jade surface, literal-text ivory/jade bubbles, live/practice/
  offline status, placeholder composer and disabled Send for blank/invalid text.
- Existing rate limits, message length, identity/membership validation and networking
  are unchanged. Message text never enters an HTML-capable tooltip.

## Files changed in this iteration

- `src/main/java/onitama/client/state/ClientModel.java`: typed public card-play history.
- `src/main/java/onitama/client/ui/PlayedCardsPanel.java`: newest-first illustrated timeline.
- `src/main/java/onitama/client/ui/GamePanel.java`: Played cards/Card details sidebar tabs.
- `src/main/java/onitama/client/ui/ChatPanel.java`: styled conversation, wrapped plain-text
  bubbles, responsive row heights, accessible message text and composer state.
- `src/test/java/onitama/client/state/ClientModelTest.java`: public play deduplication,
  lifecycle and opponent Foresight privacy regression tests.
- `src/test/java/onitama/client/ui/PlayedCardsPanelTest.java`: timeline order and reset.
- `src/test/java/onitama/client/ui/ChatPanelTest.java`: literal rendering, safe tooltip,
  width-dependent wrapping and existing send/reset checks.
- `src/test/java/onitama/client/ui/GamePanelLayoutTest.java`: redesigned chat viewport bounds.
- `src/test/java/onitama/client/ui/DeckPresentationTest.java`: replaces obsolete blanket
  JList ban with named card-history/chat list checks; privacy assertions remain.
- `GRID-DUEL-BOARD-GAME_SPEC.md`: defines the requested played-card history scope.
- `docs/architecture.md`: documents timeline data flow, tabs and new chat presentation.
- `docs/handoffs/played-cards-and-chat-design.md`: implementation and validation record.

## Validation

- Full `mvn test`: 301 tests, zero failures/errors/skips.
- Initial `mvn clean verify`: 301 tests and BUILD SUCCESS.
- Final independent `mvn clean verify` after tooltip fix: 301 tests, zero failures/
  errors/skips, shaded JAR built successfully. Independent review PASS with no
  unresolved actionable findings.
- Rendered the complete game panel at 1280×660 and 1440×820 with an authentic public
  movement/skill event sequence and sample messages; visually inspected both sidebars.
- No cloud deployment, database migration or protocol change in this iteration.
