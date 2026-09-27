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
fresh game and sends `MatchStart`.

