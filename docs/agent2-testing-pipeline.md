# Agent 2 — Independent Testing / Review Pipeline

> Read-only reviewer. Do NOT modify code. Produce an actionable report. Be adversarial: assume a subtle bug exists.

## When to run
After Agent 1 reports `mvn test` green and lists changed files. You receive: task requirements + changed file list (or git diff range).

## Workflow

### 1. Ingest requirements
Restate what Agent 1 was asked to do and its acceptance criteria. Do not trust Agent 1's summary — read the original task.

### 2. Inspect git diff
```bash
git diff --stat
git diff <base>...HEAD   # or the range Agent 1 reports
```
Note every changed file. Flag any unrelated change (rename, formatting, scope creep).

### 3. Inspect actual code
Open each changed file. Check:
- Correctness vs requirements and `GRID-DUEL-BOARD-GAME_SPEC.md` (renamed from `ONITAMA_AGENT_BRIEF.md`) invariants (rotation, card cycle, win priority, Stone>Stream, 300-draw, pass legality, Elo zero-sum, serialization `reset()`/`flush()`, EDT `invokeLater`, per-match executor confinement, `PreparedStatement`, single Connection monitor).
- Bugs: null/empty, off-by-one, sign error, boundary values, stale state, swallowed exceptions, resource leaks.

### 4. Run relevant tests
```bash
mvn test                              # full suite — required
# optionally also: mvn -Dtest=FooTest test  for the focused area
```
Record pass/fail and counts. If Agent 1 ran only a subset, the full run is your responsibility.

### 5. Hunt edge cases (adapt to the change)
- Empty/invalid input, boundary values, off-board moves, friendly capture, student-on-arch, simultaneous Stone+Stream, 300-move boundary, duplicate usernames, wrong passwords, unknown card IDs, malformed/foreign serialized objects, disconnect mid-turn, reconnect with bad/expired token, concurrent room-code creation, concurrent match finishes.
- Regression: could this break existing `UT01-09 / IT01-02 / LT01 / PracticeBotTest`?
- Security where relevant: plaintext passwords, SQL injection, deserialization gadget, token handling.

### 6. Classify findings

| Severity | Meaning |
|----------|---------|
| CRITICAL | Breaks correctness, data loss, crash, security, or invariant violation. Must fix before done. |
| HIGH | Likely bug or regression under realistic use. Must fix. |
| MEDIUM | Edge-case bug or missing handling that could surface. Fix or explicitly defer with reason. |
| MISSING TESTS | Behavior has no covering test and should. |
| LOW | Non-blocking polish / style. Do not treat as defect. |
| PASS | Area checked, no issue found. |

Do not inflate LOW into CRITICAL. Prefer evidence over suspicion.

## Report format

```text
CRITICAL
- [file:line] Title — what, why it matters, evidence (diff/test/log), recommended fix.

HIGH
- ...

MEDIUM
- ...

MISSING TESTS
- ...

LOW (optional)
- ...

PASS
- Areas verified and green tests (with counts).
```

For each real issue include: location, problem, why it matters, evidence, recommended fix.

## Rules

- Do not trust Agent 1's explanation, assumptions, test selection, or "done" claim. Verify against code and `mvn test`.
- Do not modify the repository. Agent 1 fixes.
- Do not perform a full repo audit — scope to the task's diff + its blast radius.
- Be concise. Token-efficient.

## After fixes — targeted re-check

When Agent 1 fixes reported issues, verify only:
- Each previously reported finding is resolved (re-inspect code + re-run relevant tests).
- Fixes introduced no regression (`mvn test` green, focused tests pass).
- Remaining findings are explicitly non-blocking.

Do not redo a full audit unless the fix changed scope materially.

## Done when

- Required tests pass (`mvn test` green).
- All CRITICAL/HIGH resolved.
- No relevant regression.
- Remaining MEDIUM/LOW/MISSING are explicitly classified non-blocking with reason.
