# Deck builder removal verification — 2026-10-19

Desktop players start directly from Play. No Deck editor or saved selection is used.
The server fills an omitted deck from the first three catalog regions using existing
proposal auto-fill rules. This is a temporary default, not an approved final card library.
Explicit protocol/bot decks still undergo normal validation. Legacy preferences remain stored.

Environment: macOS arm64, OpenJDK 26.0.1, Maven compiler target Java 17; desktop session,
ephemeral localhost sockets and temporary SQLite files. No performance claim is made.

Executed checks:

| Input/scenario | Expected | Actual | Status |
| --- | --- | --- | --- |
| Home menu (`LobbyMenuTest`) | Play, Settings, Atlas, Quit game; working navigation | Expected menu and navigation; all 11 scenarios passed | PASS |
| Host/join with omitted decks (`CardMatchIntegrationTest`) | Server supplies valid decks; three rounds finish and result is stored | Three-round completion, scores/history/Elo assertions passed | PASS |
| Queue and practice with omitted decks (`CardMatchIntegrationTest`) | Pair players or start server bot without deck setup | All 8 match integration scenarios passed, including reconnect/chat/rematch | PASS |
| Explicit invalid decks (`AccountLobbyIntegrationTest`) | Reject without consuming room | All 7 account/lobby scenarios passed | PASS |
| Full suite (`mvn verify`, following a clean build) | No failed checks; packaged jar | 129 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS | PASS |

The initial sandboxed `mvn test` could not open localhost sockets or run desktop tests.
Verification was rerun with desktop/network access. The first clean verification exposed
an obsolete assertion requiring manual deck selection; it was updated to reject an
explicit invalid deck, and the complete verification then passed.

Reproduce: run `mvn clean verify` in a graphical session with localhost socket access.
