# Objective rules migration

User-authorized changes supersede the former six-piece/no-objective invariants.
Implement seven pieces per side, kill points plus center control, two mana squares,
and selected three-square movement jumps. Default score target: 8 (balance setting).

1. Keep the pure authoritative engine and immutable recipient-specific snapshots.
   Store both public scores in state/view; copy them on all transitions.
2. Center (3,3): +1 score at the occupying player's own-turn start. Mana squares
   (1,3), (5,3): +1 mana each at that same boundary, respecting the existing cap.
   Landing alone gives no reward; removal/displacement prevents the next reward.
3. Every enemy eliminated by movement or a completed skill gives the acting player
   +1 score. Resolve multi-target skills atomically, then check elimination first,
   score second. Rejects, retries and reconnects never repeat rewards.
4. First to target wins with SCORE; elimination and forfeit remain wins. Existing
   completed-turn draw limit remains; no new turn rewards after a terminal draw.
5. Advance rules version to grid-duel-v2 and wire protocol to 3 so older clients
   cannot silently play using incompatible movement, setup or score fields.
6. Extend Lance (Tiger) forward to 3, Left/Right Feint (Frog/Rabbit) sideways to
   3. Preserve the other 13 patterns. Render a 7×7 relative diagram when needed.
7. Draw distinct crown/mana inlays, show score/target on both player badges,
   explain rewards in help and score victories in the result dialog. Bots value
   center occupation, useful mana occupation and capture points using public data.
8. Update authoritative spec/harness and relevant fixtures. Cover turn reward timing,
   skill/capture scoring, terminal priority, rejection/copy/privacy, serialization,
   score persistence/Elo, bot objective choices and both board orientations.
9. Run full native tests, independent read-only review, fix/recheck, clean verify.
   Open the rebuilt client and replace the old local server when safely idle.

## Implementation and verification

Implemented the rules above. Source IDs remain stable (new Blue 12, Red 13).
Scores are included in recipient snapshots; no hidden deck/skill data is added.
Public transition events retain their existing shapes; complete views carry scores.
Rules snapshots include `scoreToWin`; `score.target` validates 1..100 in configuration.
SCORE results use the existing transactional match/Elo storage with rules version v2;
historical v1 and legacy result records remain readable.

Native `mvn test`: 521 passed, zero failures/errors/skips. Independent read-only
review and native full suite: 521 passed, no blocking findings. New tests include
both movement orientations, turn-boundary mana/cap, center timing, atomic skill
scoring, elimination/score/draw priority, bot center defense and filtered views.
Real-socket coverage verifies a center reward, exact action retry, reconnect without
an extra reward, subsequent SCORE win and persisted winner/Elo. Updated UI checks
verify every card diagram for both actors/viewers; setup, Recall and Banish checks
now explicitly account for filled starting rows.

Visual inspection: `artifacts/objective-rules/game.png` and movement-card sheet.
The existing medieval card style and larger-board spacing remain; crown and mana
inlays, both score labels and seven-piece rows fit the supported layouts.
