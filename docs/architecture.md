# Architecture — Onitama Online

## Package map

```
onitama.Main              factory dispatcher (server/client/bots/demo), file logging
onitama.core              Board, Piece, Square, Offset, PlayerColor, Card, CardDeck,
                          Move, GameState, RulesEngine, WinCondition, Elo,
                          Difficulty, PracticeBot — pure, no I/O
onitama.net               sealed Message + ~20 final records (one per message type)
onitama.server            GameServer (accept loop), ClientHandler (dispatch table),
                          LobbyManager (room codes), SessionRegistry, MatchSession
                          (per-match executor), MatchResult, MatchPersistence, ServerConfig
onitama.db                Database (monitor-guarded Connection), UserDao/MatchDao
                          interfaces, SqliteUserDao/SqliteMatchDao, PasswordHasher (PBKDF2)
onitama.client            OnitamaClient (reader/writer + heartbeat), ClientSettings
  ├─ state                ClientModel (EDT model), ClientModelListener (Observer),
  │                       Screen, ServerConnection, MessageSender
  ├─ ui                   ClientFrame, LoginPanel, LobbyPanel, GamePanel, BoardPanel,
  │                       CardPanel, LeaderboardPanel, AnimalIcon, CardArt,
  │                       AssetStore, Theme, UiKit
  └─ bot                  LoadTestBot, BotHarness (CSV results)
```

Resources: `src/main/resources/{fonts/outfit-*.ttf, assets/*}`, `scripts/*.sh|bat`, `data/onitama.db`, `results/*.csv`.

## Thread & concurrency

- Accept thread → fixed pool for `ClientHandler` → `MatchSession` owns one `ExecutorService(1)`; match state is thread-confined.
- `SessionRegistry`/`LobbyManager` → `ConcurrentHashMap`. Single-writer per socket.
- `Database` single `Connection` synchronized — SQLite serializes writes by design; not a bottleneck at this scale.

## Protocol (C→S / S→C)

Register/Login → `LoginResponse` (profile+token) → `CreateMatch`/`JoinMatch` → `MatchCreated`/`MatchStart(GameState)` → `MoveRequest(cardId,from,to)` → `MoveApplied(GameState,lastMove)` or `MoveRejected` → `PassTurn` → `GameOver(winner,way,elos)` → `RematchRequest/Accept` → `OpponentLeft(60)` → `ReconnectRequest(token)` → `LeaderboardRequest/Response` → `Ping/Pong` + `ErrorMessage`. Heartbeat 30s/90s timeout.

## Database

```sql
users(id, username UNIQUE, password_hash, elo DEFAULT 1000, wins, losses, created_at)
matches(id, blue_user_id, red_user_id, winner_user_id NULL on draw,
        end_reason STONE|STREAM|FORFEIT|DRAW, move_count,
        elo_blue_after, elo_red_after, played_at)
```

All via `PreparedStatement`; match finish is one transaction.

## Client UI

5 screens: login → lobby (create/join, leaderboard, practice) → game (board, 2+2+transit cards, history, resign) → leaderboard. `BoardPanel.paintComponent` + `CardPanel` 5x5 mini-grids from `Card` data (single source of truth). Selection: card+piece (either order) → highlights via local `RulesEngine` → `MoveRequest`. ESC clears.

## Patterns

Strategy (Card offsets + RulesEngine), Observer (ClientModel→panels), sealed Message + handler registry, DAO interfaces, Factory (Main).

## Where agents most often break things

- Off-by-one / rotation sign: Red must negate offsets. Appendix is law.
- EDT violations: touching Swing off EDT or blocking EDT on I/O.
- Forgetting `reset()` after `writeObject` → stale state on receiver.
- Mutating `core` from server/client directly instead of via `RulesEngine`/`GameState`.
- Concurrent map / DB monitor misuse; adding locks inside `core`.
- Elo zero-sum rounding; draw W/L handling; 300-move cap.
- Card cycle invariant (hands size 2) after pass.
