# Atlas removal — 2026-10-20

Removed the Atlas menu entry, model route and screen enum, five implementation classes,
its dedicated tests/CSS, runtime map/heraldry resources, design sources/archives and export
and verification tools. 85 Atlas-specific files removed. Retained existing card faction
colours in CardArt so procedural card faces no longer depend on the removed map model.
The home menu now offers Play, Settings and Quit game. Match/history navigation remains.

| Check | Expected | Actual | Status |
| --- | --- | --- | --- |
| `mvn test` | Full suite passes | 73 reported: 55 pass, 18 environment errors; desktop fork exits 134 | Blocked |
| Headless focused attempt (12 tests) | Cards/model/menu work | 11 pass; home-menu quit dialog raises HeadlessException after menu assertions | Partly blocked |
| Clean headless focused verification below | Cards/model/keyboard navigation pass; jar rebuilt | 11 tests, zero failures/errors/skips; BUILD SUCCESS | PASS |
| Search source and tools | No Atlas references | No matches | PASS |
| Inspect rebuilt jar | No Atlas classes/assets or Heraldry class | None present | PASS |
| Hash retained artwork | Menu/match assets unchanged in source and jar | All 7 match original SHA-256 hashes | PASS |
| `git diff --check` | No whitespace errors | None | PASS |

Successful clean build command:

```sh
mvn clean verify -DargLine=-Djava.awt.headless=true '-Dtest=CardArtworkTest,ClientModelTest,LobbyMenuTest#mouseAndDirectionalKeysShareSelectionAndActivatePlayAndPractice'
```

Full-suite limitations: sandbox rejects localhost sockets, JavaFX native-cache writes and
desktop initialization. Quit-dialog and Swing/JavaFX window handoff scenarios still need
execution in a normal graphical session. Test settings and permissions were not changed.
Existing historical verification reports are retained. See check-results.txt for actual
counts and output summaries.
