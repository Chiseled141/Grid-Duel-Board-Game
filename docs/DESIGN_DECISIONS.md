# Design Decisions

Every place where the project brief left a detail open, we chose the simplest
option consistent with the course rubric and recorded it here.

## Stamp colors of the 16 cards (M1)

Per-card stamp colors are not publicly standardized, as the brief notes. We
assigned a **fixed 8-blue / 8-red split** in `CardDeck` (blue: Tiger, Rabbit,
Crab, Goose, Monkey, Ox, Boar, Eel; red: Dragon, Frog, Elephant, Rooster,
Mantis, Horse, Crane, Cobra) instead of simplifying to "Red always moves
first". The stamp mechanism is the official rule, costs one field per card,
and keeps first-player selection faithful to the physical game.

## Orientation of card offsets (M1)

Card offsets are stored in Blue's native orientation (as printed in Appendix
A); Red uses every offset rotated 180°. `Card.destinationsFrom(square, color)`
applies the rotation, so no caller can forget it.

## Stone beats Stream (M1)

If one move both captures the enemy Master and lands the mover's Master on the
enemy Temple Arch, the game is scored as a STONE win (capture is evaluated
first). The official rules do not distinguish this ultra-rare case; fixing an
order makes the engine deterministic.

## FORFEIT lives in `WinCondition` (M1)

The brief defines `WinCondition` as STONE/STREAM/DRAW, while the `GameOver`
message also carries a FORFEIT way (resign / disconnect / protocol abuse). We
added FORFEIT to the enum so the engine, protocol and database all share one
type instead of a parallel server-side enum.

## Passing still advances the half-move counter (M1)

A pass toggles the turn and increments `moveNumber` like any half-move, and it
counts toward the 300-half-move draw cap. The alternative (not counting
passes) would require a second counter for no benefit.

## Draw cap at 300 half-moves (M1)

House rule from the brief: a game that reaches `RulesEngine.MOVE_LIMIT` (300)
half-moves without a winner is finished as a draw. The counter covers both
regular moves and passes.

## Encapsulation via package-private mutators (M1)

`Board` and `GameState` are mutable, but all mutating methods are
package-private: only `onitama.core` (the rules engine and core tests) can
change a position. Client and server code receives effectively read-only
objects — enforced by the compiler, not by convention.

## `Message` is a sealed interface, not an abstract class (M2)

The brief sketches "abstract Message implements Serializable", but every
message is a pure data carrier, and Java records — the cleanest fit for that —
cannot extend a class. `Message` is therefore a **sealed interface extending
Serializable**; all 24 message records implement it and are named in the
permits clause. This keeps the protocol hierarchy closed (adding a message is
a conscious, reviewed change) while keeping each message a two-line record
with value equality, which UT09 uses directly. Records serialize by component
name; the `serialVersionUID = 1L` declaration required by the brief is kept on
every message for convention.

## Extra messages beyond the brief's table (M2)

Two needs are not covered by the Section 4 message table, so two small
messages were added: `ResignRequest` (the game screen requires a resign
button; the opponent wins by FORFEIT) and `opponentUsername` as an extra
`MatchStart` field (the game screen and game-over dialog must name the
opponent). `RematchAccept` is sent to the *other* player when a rematch is
offered; once both players have sent `RematchRequest`, the server deals a
fresh game and sends `MatchStart`. `MatchCreated` acknowledges
`CreateMatchRequest` with the room code (the brief's table implies the host
learns the code somehow; an explicit acknowledgment is simplest).

## Every socket message is a full snapshot: `ObjectOutputStream.reset()` (M3)

Java serialization writes a repeated object reference as a back-handle, not
as data. The server broadcasts the *same live* `GameState` in `MatchStart`
and in every `MoveApplied`; without `out.reset()` before each
`writeObject`, clients would receive the back-handle and keep the stale
initial copy forever (found by IT01: the first `MoveApplied` still reported
`moveNumber = 0`). Resetting the stream's handle table per message makes
every message an independent snapshot; `writeUnshared` would not suffice
because it does not unshare nested references.

## Match session lifecycle: retire, don't die (M3)

A finished match must stay alive as a passive session (players may vote for
a rematch, or reconnect to receive the final `GameOver`). The session's
single-thread executor is therefore retired only when the match is over
**and** both player colors are disconnected (`maybeRetire()`); a running
game forfeits a disconnected player after the grace period instead. An idle
finished session costs one parked thread, which is acceptable at this scale
and far simpler than reference-counting handlers.

## In-memory accounts before SQLite (M3)

Until M5 the server runs with `InMemoryUserDao`, which implements the same
`UserDao` interface (same validation, same PBKDF2 hashing, same errors) as
the SQLite implementation. The server code is written against the interface
throughout, so M5 is a drop-in swap.

## Client threading: model on the EDT, two network threads (M4)

The client uses exactly three threads for the UI path: the Swing EDT (owns
all model state), a socket reader thread and a socket writer thread. The
reader hands each message to the model via `SwingUtilities.invokeLater`; UI
actions enqueue outgoing messages into a `LinkedBlockingQueue` drained by the
writer. The EDT never touches sockets and the network threads never touch
Swing — the two rules from the brief, enforced structurally. Connecting also
happens off the EDT (a background thread spawned by the model's login flow).

## `ServerConnection` interface between model and socket (M4)

`ClientModel` depends on a small `ServerConnection` interface (send /
isConnected / ensureConnected) that `OnitamaClient` implements. The panels
never see the socket at all; tests could swap in a fake connection.

## Mandatory passes are sent automatically by the client (M4)

When the client detects (with the local rules engine) that it has no legal
move on its turn, it sends `PassTurn` with its first hand card instead of
requiring the user to press anything. The server re-verifies the pass, and
both engines agree deterministically because they run the same `core`.

## Captured pieces are derived, not broadcast (M4)

The protocol broadcasts full `GameState`s only. The client derives the
captured-piece trays by diffing the destination square of each `MoveApplied`
against the previous state — no extra protocol fields needed.

## Replay files record the opening deal in the header (M4)

`ReplayFile` writes the dealt card ids and first player into the header so a
replay is fully self-contained: the viewer reconstructs the exact initial
`GameState` and re-applies every recorded half-move through `RulesEngine`,
which validates the file against the real rules (corrupted files fail with a
friendly message, and `initialState` is a deep snapshot taken before the
first move is applied).

