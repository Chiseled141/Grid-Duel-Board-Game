# Unified hand and card art — 2026-10-13

## Request and acceptance

Place every own card, including both movement cards, in an overlapping bottom
hand. Show the existing skill art with live rules. Keep opponent movement public
and opponent skills unknown, represented by one shared generated back. Keep all
seven board ranks visible, hand selection usable, and existing targeting intact.

## Changed files

- `GamePanel`: unified bottom hand, public opponent row with anonymous backs,
  less busy background, configured mana cap and responsive hand height.
- `CardHandPanel` (new): centered overlapping hand with hover layer priority.
- `SkillCardPanel`: illustration viewport, live name/cost/rules, full tooltip.
- `CardBackPanel` (new): shared face-down art with no identity or hidden data.
- `CardPanel`: transparent overlapping-card surroundings and adaptive scale.
- `BoardPanel`: fit the actual component bounds; no clipped last rank.
- `GamePanelLayoutTest`: default/configured six-skill hands, Blue/Red views,
  minimum/larger windows, small board geometry, privacy and bundled resources.
- `assets/skills`: all 24 existing illustrations plus `card-back.png`.
- `docs/architecture.md`: current UI rendering and privacy behavior.

Original full-face images remain in `output/skill-cards-v1/faces`; their baked
balance text is excluded from the runtime illustration viewport. Live rules
still come from the match configuration. The shared back was generated with the
built-in image tool; exact prompt is `output/skill-cards-v1/card-back-prompt.md`.

## Validation

Final `mvn clean verify` (2026-10-14): 246 tests, zero failures/errors/skips. Independent
Agent 2 `mvn clean test`: 246 tests, zero failures/errors/skips; scoped review
PASS with no blocking defect. The runnable JAR was rebuilt from a clean output
directory after duplicate numbered generated files reappeared between sessions.
No source changes were needed for this build cleanup.
Offscreen renders inspected at 1440×820 and 1280×660: complete board, all own
cards at the bottom, opponent movement faces and anonymous skill backs.
This is presentation work; gameplay and recipient-view protocol are unchanged.
