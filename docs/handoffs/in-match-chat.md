# In-match chat — implementation and validation

Adds ephemeral text chat to the right sidebar during live multiplayer matches.
Enter or Send submits; the server echoes accepted text with authenticated identity.
Practice, disconnected/paused matches and finished matches cannot send chat.

## Changed files

- `GRID-DUEL-BOARD-GAME_SPEC.md`: brings chat into scope and defines limits/lifecycle.
- `docs/architecture.md`: documents routing, state boundaries and observer UI.
- `docs/plans/in-match-chat.md`: acceptance criteria and bounded implementation plan.
- `src/main/java/onitama/net/ChatRequest.java`: request and shared single-line text validation.
- `src/main/java/onitama/net/ChatMessage.java`: server-authored sender/text notification.
- `src/main/java/onitama/net/Message.java`: sealed protocol types and deserialization allowlist.
- `src/main/java/onitama/server/ClientHandler.java`: authenticated chat dispatch.
- `src/main/java/onitama/server/MatchSession.java`: match membership, 500 ms rate limit and relay.
- `src/main/java/onitama/client/state/ClientModel.java`: queue sends and retain last 200 messages.
- `src/main/java/onitama/client/state/ClientModelListener.java`: chat-change observer callback.
- `src/main/java/onitama/client/ui/ChatPanel.java`: plain-text transcript and bounded composer.
- `src/main/java/onitama/client/ui/GamePanel.java`: sidebar integration and compact deck layout.
- `src/test/java/onitama/net/MessageSerializationTest.java`: both chat messages round-trip.
- `src/test/java/onitama/net/MessageWireFilterTest.java`: both messages pass the wire filter.
- `src/test/java/onitama/server/MatchChatTest.java`: socket validation, membership, cooldown,
  reconnect, paused/finished handling and gameplay isolation.
- `src/test/java/onitama/client/state/ClientModelTest.java`: history limits/reset, server echo,
  stale-match rejection and send availability.
- `src/test/java/onitama/client/ui/ChatPanelTest.java`: Enter-to-send, plain-text rendering,
  input length and lifecycle behavior on the EDT.
- `src/test/java/onitama/client/ui/GamePanelLayoutTest.java`: readable chat and composer
  bounds at supported sizes alongside existing board/hand/privacy checks.
- `docs/handoffs/in-match-chat.md`: change inventory, evidence and operational limits.

## Validation

- Full `mvn test`: 297 tests passed before the review regression was added.
- Final `mvn clean verify`: 298 tests, zero failures/errors/skips, BUILD SUCCESS.
- Focused chat/server/layout tests passed after the privacy and layout fixes.
- Rendered and visually inspected the complete game panel at 1280×660 with sample
  chat messages; transcript and composer fit beside the board without overlap.
- Local socket tests use ephemeral ports and temporary databases; required execution
  outside the restricted sandbox because its networking policy prevents socket bind.
- Independent Agent 2 read-only review: PASS, no unresolved actionable findings;
  independent full `mvn test` passed 298 tests with zero failures/errors/skips.

## Limits and use

Deploy the rebuilt `target/onitama.jar` to both client and server together. New chat
records extend version 2; older clients do not understand them. No remote deployment
was performed. Chat has no database storage and does not replay missed messages.
Same-match reconnect preserves the client's existing transcript; new matches and
rematches start empty. Test a live pair by joining a room, sending from either side,
then reconnecting one player. Automated socket tests and headless UI rendering were
performed; no interactive two-window desktop session was performed.
