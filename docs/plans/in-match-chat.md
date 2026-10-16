# In-match chat

Add text chat between the two connected players of a live network match.

Acceptance criteria:
- Server derives sender identity and only relays to the same active match.
- Reject stale match IDs, blank/control-character/oversized text, disconnected opponents,
  and messages faster than one per 500 ms per sender; gameplay state/sequences never change.
- Swing observer panel supports Enter/Send, plain-text history (last 200 messages), and
  disables sending outside a connected live multiplayer match.
- Same-match reconnect preserves local history; new matches, rematches and leaving clear it.
  Chat is ephemeral: missed messages are not replayed and no database schema changes.
- Wire serialization/filter, socket integration, client lifecycle and UI tests pass.

Implementation: add ChatRequest/ChatMessage records to the existing version-2 protocol;
route through ClientHandler and the MatchSession executor; add model history/events and
ChatPanel in the right sidebar. Both client and server must be updated together.

Validation: full mvn test, clean verify, then independent Agent 2 read-only review and
required fixes/re-check.
