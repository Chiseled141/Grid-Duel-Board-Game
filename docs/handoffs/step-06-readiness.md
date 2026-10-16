# Step 6 readiness — cleanup (2026-10-15)

## Scope

Remove obsolete implementation scaffolding and misleading documentation after
Step 5, while preserving current gameplay, artwork and useful regression coverage.
This handoff does not define or implement Step 6 features.

## Cleaned up

- Removed unused `CardPanel` recycle/deal-pop and `SkillCardPanel` spend/deal-pop
  APIs, timers and rendering branches. Draw flights remain in `DeckDrawOverlay`;
  outgoing copies remain in `CardHandPanel`. Removed its unused completion callback.
- Removed `ClientModel.state()` (always null) and unused empty capture getters.
  Fixed Game-menu Resign to use the live view, reject duplicate/finished actions,
  and recheck after the confirmation dialog. Paused Foresight can still be resigned.
- Removed unused difficulty display/search-depth metadata; bot policies are unchanged.
- Updated Help, About, README and spec implementation status for the live 7×7 game.
  Help uses current match balance; before a match it describes bundled defaults.
- Deleted completed outsourcing prompts: `docs/prompts/step-05-gemini-decks-avatars.md`,
  `docs/prompts/step-05-gemini-ui-animation.md`,
  `docs/handoffs/remaining-gameplay-prompt.md`,
  `docs/handoffs/step-04-core-engine-prompt.md`.
- Deleted standalone pre-integration examples in `docs/examples/deck-animation/`:
  `DeckDrawOverlay.java`, `DeckDrawDemo.java`, `DeckDrawOverlayCheck.java`.
  The production implementation and regression tests supersede these copies.
- Removed Finder metadata from `src/main/resources/assets/skills/` and
  `output/skill-cards-v1/faces/` so it is not bundled as a game resource.

## Intentionally retained

Original artwork/gallery/prompts under `output/skill-cards-v1`, audio, font licenses,
local databases, screenshots and completed handoff reports remain. The balance
example remains useful and is not active runtime configuration.

Legacy 5×5 engine classes still support regression and negative wire-filter tests.
They are not used by live matches. Removing that engine requires a separately
scoped test-coverage migration; do not delete it just because it is legacy.

## Validation

Baseline: `mvn clean test` passed 308 tests, zero failures/errors/skips.
Final `mvn test`: 311 passed, zero failures/errors/skips.
Independent Agent 2 review: PASS; its own full `mvn test` also passed all 311.
Final `mvn clean verify`: BUILD SUCCESS, 311 tests, zero failures/errors/skips;
rebuilt `target/onitama.jar`. `git diff --check` is clean. The JAR contains no
`.DS_Store` entries. Logs: `/tmp/step6-cleanup-test.log`,
`/tmp/step6-cleanup-agent2-test.log`, `/tmp/step6-cleanup-verify.log`.

Automated Swing tests cover replacement hands, departure/draw presentation,
reduced motion, lifecycle cancellation, menu resignation and balance-aware Help.
No new interactive playtest was performed during cleanup; an already-running
client must be restarted to load the rebuilt JAR.
