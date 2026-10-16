# Match sidebar and help — 2026-10-17

Replaced the glass-style right rail with parchment and brass, matching the printed
cards and medieval strategy room. PlayerBadgePanel uses a small heraldic shield,
full-width serif name, explicit piece/status text and segmented mana. Names shrink
from 16pt to 11pt before ellipsis; the full name remains in the tooltip. Available
and held Foresight mana remain distinct, including custom mana caps.

Turn phase and the completed-turn counter share the top header. Chat is styled as
plain-text correspondence with an ivory composer and matching send pill. Live chat
is bounded to 300px. Practice shows a 122px notice instead of a tall empty chat box
or disabled composer; multiplayer restores the transcript and composer.

How to Play now sits in the upper-left of the match. ClientFrame shares its existing
help dialog using the live match rules. Opening/closing help preserves the selected
card, permitted view and gameplay state and submits no action.

Changed UI: PlayerBadgePanel (identity/mana layout), MatchStyle (shared parchment
painting), ChatPanel (journal/composer/practice presentation), GamePanel (rail layout,
turn header and help control), ClientFrame (shared live-rule help entry point).
No gameplay, protocol, persistence schema, dependencies or raster assets changed.

Native mvn test: 344 passed; zero failures/errors/skips. New coverage exercises the
real in-match help dialog with an 80-turn/7-mana configuration, selection retention,
no network action; compact practice chat followed by online composer restoration;
and the top-left help bounds across all supported window sizes. Existing literal
chat rendering, wrapping/limits/cooldown, private-card and reserved-mana tests pass.
Independent Agent 2 native full-suite review: 344 passed, no blocking findings.

Visual QA: artifacts/medieval-preview/game.png (multiplayer layout) and
artifacts/sidebar-practice.png (full Practice Bot name and compact practice notice).

Final native mvn clean verify: 344 passed; clean shaded target/onitama.jar rebuilt.

## Follow-up placement, help color and draw pacing

Moved the opponent slip into the left rail, directly below How to Play, in both
practice and multiplayer. Help now uses a dedicated parchment-fill/brass-border/
dark-lettering pill variant. Own identity, turn status and correspondence retain
their existing right-side positions. Native responsive coverage checks the moved
slip against the board/deck and expanded Gust direction controls at 1280×660.

Replacement draws now last 600ms instead of 420ms. Smoothstep travel provides a
gentler acceleration/deceleration; face reveal, hidden opponent backs, stagger,
reduced motion and cancellation rules remain unchanged. Deterministic tests keep
movement flights visible at 500ms and verify completion/unmasking at 650ms.
Native full mvn test: 344 passed, zero failures/errors/skips.
Independent follow-up Agent 2 native suite: 344 passed, no actionable findings.
Final clean mvn clean verify: 344 passed; target/onitama.jar rebuilt successfully.
