# Agent 1 — Implementation Pipeline

> The ONLY coding agent. Follow steps in order. Do not skip validation.

## 1. Read task
Restate the requirement in one sentence. List acceptance criteria explicitly. If ambiguous, pick the simplest interpretation and note it.

## 2. Read harness
Read `AGENTS.md` fully. Read `docs/architecture.md` sections relevant to the task. Read `ONITAMA_SPEC.md` (renamed from `ONITAMA_AGENT_BRIEF.md`) sections relevant to the task (rules/protocol/schema). Do not re-read unrelated sections to save tokens.

## 3. Inspect relevant code
Open only files the task touches. Trace: entry point → affected packages → tests that cover them. Note existing patterns to reuse (naming, error handling, logging, `final` records, `PreparedStatement`, `invokeLater`, `reset()`). Check `src/test/java/onitama/` for the covering test (see brief §8).

## 4. Minimal plan (in your head, 5–10 lines)
- Files to change (prefer ≤3).
- Approach reusing existing patterns.
- Tests that must stay green.
- Risks (EDT, rotation, executor confinement, serialization).

Do not create a separate planning doc unless the task spans >2 packages.

## 5. Implement
- Smallest change that satisfies the requirement.
- Reuse existing helpers; no new abstractions without reason.
- No unrelated refactors or renames.
- Keep `core` pure; keep per-match executor confinement; keep `PreparedStatement` and `reset()` discipline.
- Javadoc on new public types/methods (1–3 lines), comments explain *why*.

## 6. Run tests
```bash
mvn test
```
If the task is isolated, you may run a subset first (`mvn -Dtest=FooTest test`), but before finishing always run the full `mvn test`. Capture output; do not claim green without it.

## 7. Diagnose failures
If red: read the failure message, locate the offending file:line, check whether the failure is in your changed code, a harness invariant, or a pre-existing flake. Do not edit unrelated tests to make them pass.

## 8. Fix failures
Fix one cause at a time, re-run `mvn test` after each fix. If a fix requires changing scope, note why.

## 9. Validate against requirements
- Re-read the task's acceptance criteria; check each one off.
- Manual sanity if UI/protocol: describe what you verified (or what a human should click).
- Ensure `ONITAMA_SPEC.md` invariants still hold (pure core, authoritative server, single-writer, EDT, etc.).

## 10. Stop
Report exactly:
- Files changed (with one-line why each).
- Tests run + result (`mvn test` green, N tests).
- What was validated, what remains non-blocking.
Do not optimize further after the task is complete. Do not loop.
