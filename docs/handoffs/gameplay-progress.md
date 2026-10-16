# Gameplay continuation record

Updated 2026-10-13. This replaces duplicated milestone logs and conflicting
checkboxes. Rules remain in the spec and Step 3 contracts.

## Implemented runtime

- A: validated bundled/sparse external balance configuration; runtime card text.
- B–C: common cast lifecycle, all 24 effects, targeting, private Foresight choice.
- D: protocol 2, recipient views, sequences/retries, reconnect and additive DB migration.
- E: Swing board, movement/skill selection, phase controls and Foresight preview.
- F: practice, bot fleet and demo use the same `onitama.core.duel` engine.

The original outsourced handoff claimed completion. Independent review reproduced
seven gaps despite its 228-test suite passing. Those fixes and cleanup are tracked
in [step 5 readiness](step-05-readiness.md), not in a new gameplay specification.

## Review corrections

- Schedule the bot when Red starts practice; deterministic startup regression.
- Preserve/retry the original unacknowledged action across same-match reconnect.
- Let the player resign during Foresight; retain the preview until acceptance and
  dispose it on terminal views, navigation or connection loss.
- Pay mana on the candidate state before capped gains, preserving rejection atomicity.
- Validate full multi-piece drafts against the current view and configured limits.
- Compare complete action requests when deciding retry versus sequence conflict.
- Enumerate supported wire types and collection implementations; reject unrelated types.

## Cleanup

Removed six obsolete gameplay message records and their unused server handlers.
Their old serialization cases were removed with the retired types; current action,
view, targeting and serialization safeguards retain regression coverage.
Removed three Finder metadata files. Preserved artwork/gallery/prompts, audio,
config templates, historical handoffs, research, database support and legacy core tests.
Updated stale architecture/status documentation; no package rename or new dependency.

## Verification

- Independent baseline review: `mvn test` — 228 passed.
- Fixes before obsolete-message cleanup: `mvn test` — 245 passed, none skipped,
  including two real desktop Foresight tests.
- Final verification (2026-10-13): `mvn test` and `mvn clean verify` —
  240 tests, 0 failures, 0 errors, 0 skipped, including both desktop Foresight checks.
- `java -jar target/onitama.jar demo --seed 42` — passed with the 7×7 rules/deal.
- Independent targeted re-check: PASS; no remaining blocking finding in these fixes.
- Test count changed from 245 to 240 when five serialization cases for deleted
  legacy messages were removed; new protocol regression tests remain in place.

## Continue here

Use the live engine and contracts. Do not rerun the old Step 4 foundation handoff.
Read `step-05-readiness.md` before the next implementation task. Keep the generated
card artwork: it is source material for later UI/art integration, not temporary output.
