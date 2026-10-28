# King Cards — implemented 2026-10-25

Kings are a separate seven-entry immutable catalog (`core/card/King`), outside the unchanged
45-card draw deck, hand and seven-space Unit row. There is no currency, shop, reroll, upgrade,
generic authority bonus. A server-owned selection deadline is described below. Values follow the supplied paintings and
still need playtesting; this change makes no balance or performance claim.

## Acquisition and visibility

After dealing the ten-card opening hand of rounds 1 and 2, `MatchState` enters `KING_SELECTION`.
The server derives a seeded permutation of all seven Kings: the first three form the shared
round-one offer; the next three, drawn from the four not previously offered, form round two's
shared offer. The seventh is not offered. Both seats choose independently, including the same
King if desired. Each seat owns at most two distinct Kings. Choices spend no plays or draws.

`ChooseKing` records a private confirmation on that seat. `PlayerView.selectedKing` contains
only the recipient's confirmation; the opponent's public `KingStatus` retains only earlier
rounds' Kings. Public events say only that a confirmation occurred. Both new Kings become public
together when both confirmations exist. Ordinary play/draw/skip is blocked until then. The
normal round starter is preserved. Round 3 retains both Kings and has no selection.

Disconnect holds this state through the existing grace period; reconnect projects it again.
A forfeit can end a match during selection. A rematch starts a new state with new offers, empty
ownership and reset counters. The deadline continues during the reconnect grace period; a
forfeit that ends the match cancels it.

## Selection deadline and hand preview (approved 2026-10-25)

Each selection phase lasts 30 seconds from the server's opening deal. `KingChoiceDeadline`
opens one shared deadline for that game/round. A confirmation, hidden overlay, rejected request
or reconnect never resets it. `MatchSession` schedules its callback onto the match executor;
at expiry each unconfirmed seat confirms the first (leftmost) offered King through the normal
immutable engine transition. Existing confirmations remain unchanged. Automatic choices spend
no plays/draws and each accepted revision is recorded. Leaving selection or ending a game
cancels the timer; game/round/deadline guards discard obsolete callbacks after rematches.
Late actions also check expiry on the executor before ordinary validation.

Protocol 18 adds `MatchUpdate.kingChoiceDeadlineMillis` (epoch milliseconds, zero outside
selection). The client uses it only for a countdown, never to submit a default choice itself.
It is live session metadata: the existing `PlayerView` replay format is unchanged, and earlier
King replays remain compatible. Automatic confirmations appear in normal recorded views.

The centered teal eye-icon button below the offer toggles only the overlay, without a visible caption.
While hidden, the dealt hand stays visible. Unit/Spell hover popups are disabled throughout
King selection (including while hidden); King inspection remains available. Play/draw/skip remain blocked and
the countdown continues, with a small time display at the top. The three original paintings rise, scale and fade in from left to
right when shown, with no new artwork or sound. Reduced motion bypasses this animation.
Select a painting, then use its `CONFIRM` button; the button contains no King name and is enabled
only for the selected painting. Offered paintings glow in their King's color on hover/focus or selection.
King slots match Unit-row height, keeping each original aspect ratio. Empty positions have no
numbered label; occupied positions show only `+N` in the score rail's Outfit display font and
the King's color. Each deployed painting has a persistent, static edge aura of that color, with
clearance from the Unit rows and controls. `KingVisuals` owns these presentation colors outside core.
King hover shows only the original painting, capped at 480 logical pixels high (58% of the table
height on smaller windows). The click inspector has the same 480-pixel height cap and still provides
contribution/status. Aura does not animate, including with reduced motion enabled.

## Abilities and score order

Only Units without `Suppression.TAGS` contribute to King row conditions. Disabled printed points
do not disable tags. Bonuses are recalculated from the current row after destruction, replacement,
tag disables and clearing; repeated calculation never accumulates points.

| King | Nation / ability | Current round contribution |
| --- | --- | --- |
| Varkhan | Ashkar / Assault | +2 per active Assassin or Bowman, cap +10 |
| Altharion | Asterion / Royal Council | +2 per distinct active class, cap +10 |
| Mordraven | Dravorn / Legion | `min(10, 3 × max(0, largest active class group − 2))`; tied groups count once |
| Nethrion | Nerath / Secret Edict | First accepted manual draw each round privately looks at two, keeps one, bottoms the other; no score bonus |
| Aurendor | Solmara / Royal Decree | +3 per accepted Spell played this round, cap +9, while at least one friendly Unit has active tags |
| Thalvaren | Sylvaren / Alliance | +2 per distinct active nation, cap +10 |
| Vaelthor | Vaelora / Twin Banners | +5 per nation with at least two active Units, cap +10; a larger group still counts once |

`ScoreBreakdown.kings` lists every owned King's contribution, including zero. `KingScoring`
calculates these separately from formations:

```
(Unit points + pair points + formation bonus) × formation multiplier
    + all-seven-nations bonus + sum(King bonuses)
```

The provisional +15 seven-nation bonus and formation values are unchanged. End-of-round banking
uses this complete score once. Winner, result transaction, W/L and historical rows are unchanged.

## Secret Edict and counters

`KingStatus` stores public ownership, accepted Spells played and whether Secret Edict has been
used this round. Dealing a new round preserves ownership and resets both counters. A valid
no-target Spell counts; a rejected action or an accepted-request retry does not count again.

The first accepted manual draw with Nethrion spends exactly one manual draw. With at least two
cards, it removes the top two into the owner's private `looking` list and sets
`ChoiceKind.SECRET_EDICT`. `KeepEdictCard` adds the selected card to hand, puts the remaining
card at the bottom, clears the pending choice and retains the same turn. Normal play, draw,
skip and the Reconnaissance-specific `KeepCard` are rejected during this choice. With one card
it draws normally and marks the ability used. With zero cards the request is rejected without
spending a draw or the ability. Spell draws and the deal never trigger it. Reconnaissance
continues to use `ChoiceKind.RECONNAISSANCE` and `KeepCard`, ending its cast's turn on completion.

## Modules and routes

- `core/card`: immutable state, `King`, `KingStatus`, `KingBonus`, `KingScoring`, `ChooseKing`,
  `KeepEdictCard`, `ChoiceKind`; no UI, I/O, DB or networking. Easy and hard bots use only their
  own view for both new choices and score King contributions in public-board evaluations.
- `server/MatchSession`: existing single executor, revision checks, accepted-request-ID retry,
  disconnect grace, rematch and replay capture also cover the new moves. Its `awaits` check
  schedules bots for either seat's outstanding King confirmation independently of normal turns.
  `KingChoiceDeadline` and the existing scheduler enforce the 30-second fallback on that executor.
- `net/Message`: protocol **19**, explicit allowlist entries for every new wire type, unchanged
  serialVersionUID 1, socket/TLS, heartbeat, flush/reset and silence rules. Version 17 and earlier are rejected.
- `client/state/ClientModel`: permits the owner's King choice through the normal pending-action
  seam; selection counts as an active match for reconnect and deferred updates.
- `client/bot/CardBot`: protocol `bots`/`demo` handles both outstanding King selection and ordinary
  turns. Practice and offline play use the same authoritative engine/server.
- `client/ui/KingTableControls`: two slots per side beside the Unit rows, shared three-card offer
  with separate selection/confirmation, original-art hover and click inspector with current
  contribution and Edict status. It consumes snapshots and emits moves only. Opening-deal
  staging completes before the offer appears. Reduced motion shows the offer immediately.
- `KingArt`: cached background image decoding; paints the full original aspect ratio without
  cropping. The supplied originals are stored unchanged in `src/main/resources/assets/cards/kings/`;
  redundant import copies are not retained. SHA-256 checks cover all seven resources.
- `ScoreRail`/`ScoringTrace`/`PlayAnimator`: separate King contribution after formation, with a
  King tally pocket and existing sound cues. `ReplayPanel` renders the recorded King facts and
  labels Edict's private choice separately.

## Replay compatibility and privacy

The existing recorder stores each human recipient's `PlayerView` per accepted revision, so it
records offers, that owner's private confirmation, public ownership/counters/score breakdown and
only that owner's pending Edict cards. The opponent sees neither that confirmation nor those
cards. No seed, hidden draw pile or additional enemy hand cards enter the view. Ownership checks
on replay retrieval, practice-only replay storage and account/result persistence are unchanged.

The new required King/choice fields deliberately reject pre-King serialized frames through the
existing "recorded by another version" response. Existing accounts, result rows and stored
replay bytes are retained; no destructive migration or database reset is performed.

See [verification notes](testing/README.md) for King selection, deadlines, presentation and privacy checks.

After both choices become public, each newly acquired King flies from its offered painting to
its owner's next King position in 650 ms, shrinking with its colored aura attached. The actual
slot and its points appear when the flight finishes. Newer match revisions wait for deployment;
draw, skip and card plays are held during the flight. Reduced motion, reconnecting, resizing or
leaving the table finishes the flight immediately. An initial snapshot with already owned Kings
does not replay it. Hidden offers still provide the same flight origins; private confirmations
alone never reveal the opponent's choice.

`KingDeploymentTest` checks both owners, the second-round position, repeated/initial snapshots,
hidden offers and cancellation. It writes `target/kings-qa/deployment-in-flight.png` as visual evidence.
