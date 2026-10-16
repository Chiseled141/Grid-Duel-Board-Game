# Review fixes and step 5 readiness

## Scope and acceptance

Fix the seven independently reproduced migration defects, add regression coverage,
and remove obsolete files without discarding art, music or unrelated user work.
Acceptance: legal practice startup; exact reconnect retry; accessible resignation
through Foresight; correct payment order; targeting/engine agreement; full-payload
retry checks; enumerated wire types; green full suite and runnable clean package.

## Changed areas

- `ClientModel`: opening bot scheduling, seeded practice test entry, retained pending
  requests on reconnect, new-match reset and protection against older snapshots.
- `GamePanel` / `ForesightDialog`: modeless committed preview, explicit Resign,
  confirmation gated while pending/paused, owned-window cleanup.
- `RulesEngine`: pay on a disposable candidate before resolving a skill effect.
- `Targeting`: validate Advance, Rally and Scatter prefixes/final selections;
  enforce Sweep/Gust configured target limits and reject duplicate selections.
- `MatchSession`: compare the entire request for retry identity.
- `Message`: explicit protocol/value/JDK collection allowlist, preserving depth/array bounds.
- Regression tests: client reconnect/startup, desktop preview lifecycle, custom mana
  and target limits, stale/invalid grouped drafts, revision conflicts, wire types.

## Removed or consolidated

Six retired message files: MatchStart, MoveRequest, MoveApplied, MoveRejected,
PassTurn and ResignRequest. Their server handlers and superseded serialization
cases are gone. All gameplay now uses ActionRequest and recipient-specific views.
Three `.DS_Store` files removed. Repeated checkpoint sections consolidated and
stale architecture descriptions updated. Stale duplicate build files were cleared by clean verify; the redundant
`target/original-onitama.jar` backup was removed after validation. The runnable
`target/onitama.jar` and test reports remain.

Preserved `output/skill-cards-v1`: the 24 original generated faces, gallery and
prompts are useful source assets. Their baked card text is historical; runtime text
comes from match configuration. Audio, research and historical handoffs also remain.
Legacy core tests remain as regression coverage; no runnable route uses that engine.

## Next task boundary

The historical Step 4 foundation and the later A–F runtime migration are already
implemented. Continue from this application; do not create another rules engine,
card catalog or parallel protocol. The user calls the next task step 5; this file
records its starting state without introducing new gameplay requirements.

For UI/animation work, consume PlayerView and sanitized PublicEvent; never animate
by mutating authoritative state. Use Swing timers and preserve EDT/socket boundaries.
Retain the pending-choice, retry, configurable-hand and Red-view behavior when
changing presentation. Use `docs/research/balatro-animation-audit.md` as reference.

## Verification

Verified 2026-10-13:

- `mvn test`: 240 tests, 0 failures/errors/skips.
- `mvn clean verify`: 240 tests, 0 failures/errors/skips; executable jar rebuilt.
- `java -jar target/onitama.jar demo --seed 42`: 7×7 demo ran successfully.
- Independent targeted re-check: PASS; no blocking finding remains in the fixes.
- Follow-up fix: removed the sticky Resign latch. Both Resign controls are gated
  by the outstanding request, and rejection restores availability. Desktop regression covers it.

The count decreased from the intermediate 245 to 240 only because five obsolete
message serialization samples were removed with their message classes.
Desktop tests run on machines with a display and explicitly skip in headless CI.
