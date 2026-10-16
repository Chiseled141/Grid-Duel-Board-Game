# Bot skills and menu identity — 2026-10-17

PracticeBot previously handled zero-target or one-piece/one-square skill drafts,
which missed attacks requiring source+enemy, swaps and multi-piece effects. It now
walks shared Targeting options for ordered pieces, destinations and directions,
including optional extra targets. The per-card limit is 192 unique drafts and 24
complete candidates. Policy receives only PlayerView, never hidden skills, deck
order or authoritative state. No new dependencies or protocol changes.

Rookie chooses a varied legal cast rather than randomly skipping the skill phase.
Senior/Legend favor eliminations and more complete group effects. Insufficient
mana or missing legal targets ends the skill phase; Mana Surge is saved at full
mana. Foresight still resolves the committed preview. Practice and load bots both
use this policy. Movement/capture policy is unchanged.

LobbyPanel shows the 21pt serif username below Play / Settings / Quit, with Elo
on its own smaller line, followed by Leaderboard, How to Play and About. Resume
remains visible below these links for a parked match. Rendering stays literal.

Runtime changes: PracticeBot, LobbyPanel. Tests: PracticeBotSkillsTest (all 24
skills × both colors × three levels, actual engine acceptance, seed reproducibility,
multi-target attack priority, Foresight, mana limits), PracticeBotTest (12 complete
simulations spanning all levels), ClientModelTest (real practice skill event/spent
pile/turn completion with no network), LobbyMenuTest (name prominence/order).

Native full mvn test: 494 passed; zero failures/errors/skips.
Visual QA: artifacts/full-menu.png uses a local sample profile, no account edits.
Independent Agent 2 full native suite: 494 passed, no actionable findings.
Final native mvn clean verify: 494 passed; shaded target/onitama.jar rebuilt.
Requested local server started on port 5555 and updated game client opened.
