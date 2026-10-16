# Step 1 — Gameplay rules for the skill-card redesign

Status: **historical design draft; recommended defaults adopted in Step 2 on 2026-10-10**.

The user requested the specification/harness update after this draft. The
[authoritative specification](../GRID-DUEL-BOARD-GAME_SPEC.md) now contains the
rules and implementation requirements. The text below preserves the original
proposal and decision rationale; its references to pending approval are
historical, not a new approval requirement. Do not implement from this snapshot
or edit it as a second specification. Gameplay code migration remains pending.

## 1. Design goal and confirmed decisions

Make skill cards the main source of tactical variety while keeping the board and pieces simple.

Confirmed in conversation:

- Two-player game on a **7×7** board.
- Separate movement and skill decks.
- Playing a movement card draws a replacement. Used movement cards return to the movement deck and are shuffled back in.
- Playing a skill draws a replacement. Each physical skill card is single-use and never returns to its deck.
- The first set has **24 different skill effects**, not 12 effects with two copies each.
- Keep mana so powerful effects can have higher costs.
- Balance values should be manually configurable.
- Card artwork should avoid humans and faces.

The request to simplify other systems establishes a direction; the specific removals and defaults below are proposals.

## 2. Decisions to settle before implementation

| Decision | Recommended first version | Basis / status |
| --- | --- | --- |
| Pieces | Six identical pieces per player; no Master | Retained from original design sheet; proposed carry-forward |
| Board features | Plain board; no objectives, terrain, obstacles, or random battlefields | Proposed simplification |
| Victory | Eliminate all opposing pieces; no points system | Proposed simplification of original victory conditions |
| Starting hands | Two movement cards and three skill cards each | Original design values; proposed carry-forward |
| Deck ownership | One shared movement deck and one shared skill deck | New proposal |
| Skill copies | One copy of each of the 24 skills per match | New proposal; distinct effects are confirmed, copy count was not |
| Information | Movement hands public; skill hands private; deck order private | New proposal |
| Turn order | Gain mana → optional movement → optional single skill → end turn | Original ordering; one-skill limit is proposed |
| Mana | Start at 2; gain 1 at every own-turn start, including first turn; cap 5 | Original values, explicit first-turn interpretation proposed |
| Match limit | Draw after 60 completed player turns total, unless an elimination win occurs first | Original limit retained as a playtest proposal |

Approval of this table, including any edits, is the Step 1 decision gate. Do not ask downstream coding agents to invent answers independently.

## 3. Proposed setup

All sections below describe the recommended ruleset, pending the decisions above.

- Coordinates: `x=0..6`, `y=0..6`. Blue begins on `y=0`; Red begins on `y=6`.
- Place Blue pieces at `(0,0), (1,0), (2,0), (4,0), (5,0), (6,0)`.
- Place Red pieces at `(0,6), (1,6), (2,6), (4,6), (5,6), (6,6)`.
- Leaving the middle starting square empty gives a symmetric six-piece formation. Every piece has a stable identity, but identical abilities.
- There are no temples, special pieces, HP, armor, upgrades, or passive statuses.
- Use the existing 16 movement-card patterns, one copy each. Preserve their offsets; enlarge the board, not the patterns. Native offsets are Blue-relative; Red negates both components.
- Shuffle each shared deck separately. Deal alternately Blue then Red: two movement cards each and three skill cards each.
- This leaves 12 movement cards and 18 skill cards in their respective decks.
- Choose the starting player randomly, independently of card stamps. Use seeded randomness for repeatable tests.
- Both players begin with 2 mana. At their first turn start, they gain 1, so each first acts with 3 mana.

## 4. Turn sequence

A player turn contains these ordered phases:

1. **Start:** gain 1 mana, capped at 5. This happens exactly once per turn, including after reconnecting.
2. **Movement:** play at most one movement card, or skip movement.
3. **Skill:** play at most one skill card, or skip the skill.
4. **End:** finish the turn, increase the completed-turn count, then give the opponent the turn unless the match has ended.

Once movement is played or skipped, the player cannot return to that phase. A skill cannot be played before movement has been played or skipped. A replacement skill cannot be played again in the same turn.

An End Turn action skips any remaining optional phases. A player may end a turn even when legal actions exist. If no movement is legal, they may still cast a legal skill. Doing nothing does not cycle or draw cards. The turn limit prevents indefinite passing.

## 5. Movement and captures

- Select a movement card in your hand, one friendly piece, and a destination matching one of the card's oriented offsets.
- Pieces jump over intervening pieces. The destination must be on the board and cannot contain a friendly piece.
- Landing on an enemy eliminates that enemy and occupies its square.
- After successful movement, remove the used card from the hand, insert it into the shared movement deck, shuffle the deck, then draw one replacement.
- The played card may be drawn again immediately. This follows the proposed **return → shuffle → draw** ordering and must not be silently changed to a discard-pile system.
- Each player keeps two movement cards. Skipping movement draws nothing.
- Skill-generated movement does not consume or replace a movement card unless the skill explicitly says so; Refocus is such an exception.

## 6. Skill use, mana, and replacement draws

- A cast requires the player's skill phase, the card in their hand, sufficient mana, and all legal target selections.
- Validate the complete action before committing it. Illegal casts change no board state, mana, hand, deck, turn phase, or random-generator state.
- On success: pay the mana cost, resolve the entire effect, move the used card to the public spent pile, then draw one replacement if the skill deck is nonempty.
- Spent skills never return to play. With one copy per skill, each effect can be cast at most once across the whole match.
- When the shared skill deck is empty, casts still work but hands shrink. Having no skills does not cause a loss.
- No automatic skill draw occurs at turn start or when movement is performed.
- Mana persists between turns. Captures grant no mana. There are no refunds after a successful cast.
- Mana Surge costs 0, obeys the cap, and consumes the one skill opportunity. It can be played at the cap even though its mana gain is zero.
- No reactions, stacks, opponent-turn casts, or ongoing status effects in the first set.

## 7. Targeting conventions and special cases

- Distance means Manhattan distance: `abs(dx) + abs(dy)`.
- Adjacent means horizontally or vertically adjacent unless a card explicitly says diagonal.
- Empty means unoccupied at the point the rule checks it; all destinations must be on the board.
- Any two-piece swap requires two distinct pieces. Swaps exchange positions without elimination or checking intermediate squares.
- For “up to 2,” choose one or two distinct pieces/targets, never zero. Destinations must be distinct; no skill leaves two pieces on one square.
- Skills do not capture by landing on an occupied square unless their rule explicitly allows it. Flank explicitly does.
- “Starting row” always means the initial edge for that color, regardless of current piece locations.
- Advance moves both chosen allies one row toward the enemy starting edge into squares empty before the cast; resolve together.
- Gust and Scatter resolve together, into squares empty before the cast. “Both destinations” means every selected destination even when selecting only one target.
- Rally chooses a stationary friendly anchor and one or two other allies. The player supplies their resolution order; each move reduces distance to the anchor and lands on a square empty at that step. Validate that entire sequence on a temporary state first.
- Sweep removes its selected enemies together. Chain Strike's second target is adjacent to the first target's original square and must be a different enemy.
- Crossfire requires two allies on opposite sides of the enemy in the same row or column, each at most distance 3 from the target, with clear intervening squares.
- Refocus returns both of the caster's movement cards to the shared movement deck, shuffles once, then draws two. It works even after a normal movement this turn and does not reopen the movement phase.
- Foresight privately reveals up to three top skill cards to the caster, accepts an ordering of exactly those cards, and puts them back before the normal replacement draw. Thus the first ordered card becomes the replacement. Opponents learn neither the preview nor the chosen order. An empty-deck cast is allowed and draws nothing.

## 8. Winning and ending a match

- After a complete movement or skill resolves, zero opposing pieces means an immediate win. Do not wait for End Turn.
- Resolve mandatory card bookkeeping as part of that successful action before marking it complete; no further player actions follow a win.
- If an elimination win happens on the 60th player turn, that win takes precedence over the turn-limit draw.
- Otherwise, End Turn on completed turn 60 ends the match as a draw. This means 30 turns each in a normal alternating match, not 60 turns each.
- Remaining-piece count and remaining mana do not break a draw.
- Resignation loses immediately. Keep the existing 60-second reconnect grace and forfeit behavior for the online game.
- No objective points, kill points, temple victory, or Master-capture victory in this proposed version.

## 9. Public and private information

Public: board, turn and phase, both mana totals, both movement hands, skill-hand counts, remaining deck counts, spent skills, completed-turn count, and match result.

Private to a player: their skill hand and any active Foresight preview. Undrawn deck identities/order and the match random seed stay server-private during play.

The existing full-state network broadcast cannot be reused unchanged with this proposal. The server retains the authoritative state and sends each player only their permitted view. Reconnect must restore the same phase, hand, and pending Foresight choice without granting extra mana or another draw. Practice bots receive only the information their player is allowed to know.

## 10. Initial 24 skills and balance status

The first card set lives in [the card manifest](../output/skill-cards-v1/cards.json) and [the visual gallery](../output/skill-cards-v1/index.html). Costs and printed effects are draft playtest values. The targeting conventions above clarify the card wording.

Known balance question: Dash and Vault both cost 2, while Vault additionally permits jumping. Consider reducing Dash's cost after the first playable test. Do not treat generated card text as immutable gameplay truth.

For implementation, names, descriptions, mana costs, effect parameters, and copy counts should come from validated definitions. Use Java-compatible `.properties` for the planned runtime config; the current JSON is an asset/design manifest, not a runtime loader. Snapshot configuration at match creation so edits affect new matches, not games already in progress. Render live text over artwork rather than using the baked PNG text as the UI's source of truth.

## 11. Acceptance examples for the future implementation

These are requirements to turn into tests in later steps; no engine changes are part of this document task.

- Starting deal: six pieces, two movement cards, three skills, and two mana per player; the first active player has three mana after turn start.
- Successful movement: exactly one board move, one recycled movement card, and one replacement; skill hand unchanged.
- Successful skill: cost paid once, effect resolved once, one permanently spent card, at most one replacement.
- Empty skill deck: casting reduces hand size by one and does not shuffle the spent pile.
- Rejected multi-target cast: no partial elimination, displacement, payment, draw, or phase advancement.
- Skipped movement: no card exchange; skill remains available. Played skill: movement cannot resume and replacement cannot be recast this turn.
- Red's movement and Advance direction mirror Blue correctly on the 7×7 board.
- Every elimination skill can win when it removes the last enemy; swaps and pushes cannot eliminate an enemy off-board.
- Foresight reveals no private information to the opponent and accepts only valid reorderings of the preview.
- Reconnect during a turn restores state without running Start again.
- Final elimination wins before the turn-limit draw; 60 completed turns without a win draws.

## 12. Handoff and next step

1. Review the decision table in section 2 and the proposed starting formation.
2. Record any agreed changes and mark the document approved only after user confirmation of the rules.
3. Step 2 updates the authoritative spec and harness to match, including replacing old gameplay tests deliberately rather than preserving obsolete assertions.
4. Then define stable engine and targeting interfaces before delegating configuration or UI work.

Keep Java 17, Maven, Swing, pure core logic, server authority, per-match execution, safe serialization, EDT rules, and existing persistence safeguards. Gameplay redesign does not authorize changing the technology stack.

Sources: the user's original Vietnamese GAME DESIGN SHEET, subsequent conversation corrections, the existing repository specification, and the first 24-card draft. Where these differ, this document labels confirmed corrections and proposed choices rather than treating older pasted rules as new instructions.
