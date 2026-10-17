# Match loading screen

Approved scenes: castle passage, campaign map, fortress courtyard, bundled under
assets/loading. The workshop remains a preview only. Prompts are recorded in
artifacts/loading-concepts/prompts.md.

Login/menu open directly. Practice and network match entry, rematches, reconnects
show the loading screen. Return to Match opens an existing parked match immediately. A background worker
warms the match artwork, card faces and pieces using the current match rules.
The bottom progress counts actual completed steps. Hints rotate and scenes cycle;
reduced motion keeps the chosen scene still. Enter Match appears at completion
and entry waits for a click or Enter key. Menu music continues until board entry.

Practice bot turns wait behind the gate, including a red opening turn. Live
network state continues updating without changing authoritative game phases.
Result dialogs wait until entry. Leaving/disconnecting cancels the screen and
its timers; callbacks cannot reopen it. Native tests cover startup bypass,
practice/online entry, current rules, repeated sync, resume/cancellation, button
readiness/exactly-once entry, artwork selection, wrapping and bot suspension.

Validation: full native mvn test and clean mvn clean verify passed 539 tests,
zero failures/errors/skips. Independent read-only review and its full native
suite passed. Visually checked the complete hint, bottom progress and Enter Match
button. Reopened the updated client against the running local server.

Return to Match bypasses loading via the explicit onMatchResumed observer callback;
other observers receive the normal GAME notification through its default method.
Fresh practice/online matches and rematches retain their loading screen.
