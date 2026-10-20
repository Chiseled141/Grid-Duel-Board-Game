# Card artwork removal — 2026-10-20

Removed 106 old artwork files (176,379,713 bytes): 35 Unit sources, 11 Spell sources,
6 Landmark sources, one shared back source, and 53 runtime copies. Menu animation,
match-room background and Atlas artwork remain unchanged. Historical UI screenshots
remain as verification evidence; they are not runtime card assets.

The existing procedural renderer remains in use while replacement art is pending.
Updated existing artwork tests to expect missing images and exercise all 52 catalog
faces, suppression and the shared concealed back. No gameplay rules changed.

| Check | Expected | Actual | Status |
| --- | --- | --- | --- |
| `mvn clean verify` | Full suite passes | 73 reported tests: 55 passed, 18 environment errors; fork crashed in MenuPetalsTest | Blocked |
| `mvn -Dtest=CardArtworkTest -DargLine=-Djava.awt.headless=true test` | Fallback renders without old images | 4 tests, 0 failures/errors/skips | PASS |
| `mvn -DskipTests package` | Build jar after removal | BUILD SUCCESS | PASS |
| Compare retained runtime assets by SHA-256 | Identical source and jar bytes | All 25 match pre-removal hashes | PASS |
| Inspect jar entries | No old card image entries | No `assets/cards/` entries | PASS |

Full verification was blocked by sandbox restrictions: localhost sockets returned
`Operation not permitted`, JavaFX could not write its native cache or initialize its
toolkit, and the desktop test fork exited with code 134. These checks require a normal
graphical session with localhost socket access. No permissions or test-suite settings
were changed. See `check-results.txt` for executed-check evidence.
