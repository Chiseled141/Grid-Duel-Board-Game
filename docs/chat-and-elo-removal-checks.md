# Chat and Elo removal checks — 2026-10-19

Removed match chat UI, callbacks, history, server relay, rate limiter and chat protocol
messages/allowlist entries. Removed Elo UI, calculations and fields from profiles,
history and match results. Win/loss records, authentication, match history, round
scores, reconnect and rematch remain supported. Existing database rating columns are
dropped transactionally and idempotently on startup; other data remains intact.

Environment: macOS arm64, OpenJDK 26.0.1, Java 17 compiler target, graphical session,
temporary SQLite files and ephemeral localhost sockets.

| Check/input | Expected | Actual | Result |
| --- | --- | --- | --- |
| Match table (`CardTableViewTest`) | No chat input or chat action; card interactions still work | All 12 scenarios passed | PASS |
| Menu/history UI | No Elo label or history column; navigation and scores remain | Menu and history tests passed | PASS |
| Legacy database with populated Elo columns, accounts, W/L, match and round scores | Remove rating columns; reopen twice; retain login/history/scores; record another match | All assertions passed in `removingLegacyRatingsPreservesAccountsHistoryRoundsAndFutureResults` | PASS |
| Persistence | Result + W/L/round scores commit together; rollback and legacy history remain supported | All 6 match DAO tests and 7 account DAO tests passed | PASS |
| Multiplayer, protocol and bots | Matches, profile records, rematch/reconnect and serialization continue to work | Full integration and protocol suites passed | PASS |
| `mvn clean verify` | Successful suite and packaged jar | 127 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS | PASS |
| Packaged jar inventory | No ChatRequest, ChatMessage or Elo classes | Absent from target/deck-duel.jar | PASS |

The initial sandboxed `mvn test` hit desktop/localhost permission restrictions.
Full verification ran with desktop/localhost access. The 6 removed Elo math tests are
not counted as current coverage; a database migration scenario replaces that feature's
obsolete persistence expectations. No performance change is claimed.

Reproduce: `mvn clean verify` in a graphical session with localhost socket access.
