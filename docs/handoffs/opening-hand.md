# Opening skill selection implementation

Scope: pure OPENING transition + private confirmation flags, sequenced server action,
practice/fleet bot readiness, three-card preview with local replace toggles and
confirmation-triggered flights. Existing in-turn simulations may use newGame;
live routes use newOpeningGame and grant turn-one rewards only after both confirm.

Validation required: engine atomicity/conservation/determinism, wire privacy/retry,
practice startup, preview toggling/confirmation/lifecycle, native full suite and
independent read-only review. Keep the already approved menu changes.

Implementation complete: protocol 5 negotiates the new phase; confirmations use
existing sequence receipts and independent opening revisions. Three native UI checks
cover local toggle/undo, no speculative mutation, loading/parked lifecycle and
actual return -> replacement -> final hand flight endpoints. Pure and socket checks
cover deterministic replacements, conservation, privacy, idempotent retry and
confirmed-choice reconnect. Native mvn test: 573 passed, 0 failures/errors/skips.
Independent read-only review and its separate full native suite: PASS, 573 passed.
Visual preview inspected: /tmp/deck-duel-opening-preview.png.
Final clean mvn verify passed 573 tests, zero failures/errors/skips, and produced
target/onitama.jar with protocol 5. The local server and client were reopened.

Follow-up: hold practice gameplay until the opening presentation fully closes.
Previously both engine confirmations could schedule the bot while the user's
replacement/final-hand animation was still running. A separate presentation gate
now permits bot readiness only during OPENING and blocks normal bot actions until
HAND finishes. Both the timer and direct stepBot path honor the gate. Badges stay
inactive during OPENING. Added model and real UI/timer regression coverage.
Follow-up validation: native mvn test and independent review passed 575 tests;
final clean mvn verify passed 575 tests, zero failures/errors/skips. The practice
UI regression observes the real bot timer during return/draw/hand animations and
checks that normal actions resume only after the preview closes.
