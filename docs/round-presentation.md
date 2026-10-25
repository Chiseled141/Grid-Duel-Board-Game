# Finishing scoring before round and match summaries

The live table presents accepted revisions in order. A newer revision waits while card/Spell
effects, scoring links/chips, formation and King callouts, gain feedback or the score rail's
count/pulse are still running. Waiting for card movement alone was insufficient: it could end
before the score count and let a later update replace it.

## Authoritative final-round data

The server still resolves and banks a round atomically, without waiting for client animation.
`MatchState.endRound` also retains an immutable pre-bank state after the accepted final action,
before the rows clear, the next hand is dealt or King round counters reset. It includes the exact
final Spell count and actual drawn/kept cards, rather than inferring them from the reset state.
`closingRoundView(seat)` applies the same recipient projection as the normal view: the owner's
hand and private inspected/revealed cards, public rows, authoritative scoring and public counters.
It has no playable turn, result or newly banked round. It is absent on ordinary moves and forfeits.
The retained state has no earlier closing-state chain and is dropped by the next accepted move.

Protocol **19** adds optional `MatchUpdate.closingRoundView`, using the existing allowlisted
`PlayerView` contract. `MatchSession` sends each human only that seat's closing view. Older protocol
peers are rejected; serialVersionUID stays 1. The existing King deadline remains live metadata.

## JavaFX sequencing

When the table has the preceding revision, `CardTableView` first presents the supplied closing
view. The last Unit/Spell/keep animation and score trace use its final rows and counters. The
rail counts fully while the previous match total stays visible. Only after all presentation work
finishes does the table apply the actual banked update, announce the round, clear/deal the new
hand or open the match result. A separate transition plan avoids playing the final card twice;
victory confetti starts with the result after scoring.

This also covers a final skip after a previous scoring play and metadata/retry updates queued
behind it. New matches/rematches and reduced motion clear obsolete queues; a hidden screen
fast-forwards to the latest accepted state and stops both table effects and rail feedback. A
fresh/reconnect snapshot without a preceding revision shows the latest authoritative state.

Scoring, banking, starting-player rules, results, W/L and persistence are unchanged. The client
does not apply moves or calculate a replacement final score. The ordinary `PlayerView` and
stored replay format are unchanged; the extra pre-bank snapshot is live presentation metadata.
The King-selection timer continues to follow its existing server deadline during presentation.
