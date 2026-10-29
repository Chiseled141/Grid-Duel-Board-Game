# Replay review verification

Scope: client navigation and review of personal recordings and local filtering of the history response. No game rules, serialized records, protocol version,
database schema or server authorization behavior changed.

## Environment and commands

Executed on Windows with Temurin JDK 21.0.10, Maven 3.10.0, Java 17 target and OpenJFX 21.0.5.
Swing/JavaFX checks use the graphical desktop. Database/network scenarios use temporary SQLite
files and ephemeral loopback ports; the development account database is not a fixture.

Targeted verification:

```powershell
mvn '-Dtest=ReplayNavigationTest,ReplayScoreChangeTest,HistoryFilterTest,ReplayReviewTest,HistoryFilteringTest,ReplayJourneySystemTest,HowToPlayPanelTest' test
```

On 2026-10-28 this command completed with **37 tests, 0 failures, 0 errors, 0 skipped**,
BUILD SUCCESS in 29.516 s. The full build's results are recorded in the verification index.
These durations describe these runs only; no performance improvement is claimed.

## Inputs, expected outcomes and observed results

| Input / scenario | Expected result | Actual result |
| --- | --- | --- |
| Seek while playing, including seeking the current frame | Playback pauses, speed is retained, timer stops | PASS |
| Negative frame, frame past the end, absent round | Reject before changing index or playing state | PASS |
| First / Last and Home / End | Reach the requested recorded endpoint and pause | PASS |
| Previous round from its middle, then from its start | First returns to this start, second to the prior start | PASS |
| Next round in the last recorded round | No wrap or invented round | PASS |
| One-frame excerpt starting in round 2 | Only R2 exists, slider disabled, absent R1 rejected | PASS |
| Slider is adjusting when playback observers run | Selected index stays synchronized without recursive seeking | PASS |
| Spell-only journal followed by seeking to the opening | No selected journal row; playback stays at the opening | PASS |
| Journal row after filtering | Seek to the row's original frame index, rather than its filtered row number | PASS |
| Search + Spell filter + Key moments | All criteria combine without changing position | PASS |
| Journal filter with no matches | Empty count is explicit; no move or seek occurs | PASS |
| Two stored positions separated by missing revisions | Mark the gap and warn that score change may include multiple actions | PASS |
| Seek backward from the result to the opening | Restore only the opening's recorded private facts | PASS |
| Every pair of formation multipliers, including loss of formation | Component differences sum exactly to live total difference | PASS |
| Seven-nation and King gains under a ×3 formation | Flat gains are not multiplied | PASS |
| Individual King changes | Detail sum equals the aggregate King change; zero-point Kings remain identifiable | PASS |
| Round reset and banking | Negative live-score reset and positive banked-total change are separate | PASS |
| Score fixture with empty rows but nonzero stored scores | Compare the stored scores; do not recalculate from rows | PASS |
| Opponent search under Turkish system locale | Case-insensitive matching uses Locale.ROOT | PASS |
| Practice wins in filtered history | Visible practice count increases; online W/L summary does not | PASS |
| Selected history entry moves after filtering or refresh | Selection keeps the same replay id | PASS |
| Selected history entry is filtered out or removed | Clear selection and disable Watch replay | PASS |
| `<html>` in an opponent name or journal search | Literal matching and plain text rendering | PASS |
| Clear history filters | Restore loaded rows without another request | PASS |
| Leave a replay then activate its keyboard actions | No playback navigation and no timer restart | PASS |
| How to Play controls | Only Next and Details are visible; full replay shortcuts cannot seek or start playback | PASS |
| How to Play Details | Compact action and score explanation; no action journal, search or tab controls | PASS |
| Real recorded practice game through the client window | Login, review, seek, return, filter and clear succeed; one history row remains | PASS |

## Visual evidence

The Swing regression tests write:

- `target/replay-qa/review-1280-tab0.png` and `review-1280-tab1.png`.
- `target/replay-qa/review-1960-tab0.png` and `review-1960-tab1.png`.
- `target/replay-qa/history-filter-1280.png` and `history-filter-1960.png`.

Replay sizes are 1280×720 and 1960×1080; history sizes are 1280×660 and 1960×1080.
The images check the shared painted background, readable journal/score drawer, timeline clearance
from cards, and history filters fitting inside their row. Initial inspection caught clipped history
controls, an unsupported arrow glyph in the custom font and cards touching the timeline; these
were corrected. Images and machine reports remain ignored generated output.

The guide simplification was checked with `mvn '-Dtest=HowToPlayPanelTest,ReplayReviewTest,ReplayPanelTest' test`:
**12 tests, 0 failures/errors/skips**, BUILD SUCCESS in 36.415 s on 2026-10-28.
Guide screenshots in `target/how-to-play-qa/` cover 1280×720, 1470×923 and 1920×1080;
the 1280×720 King/Secret Edict example was visually inspected with just Next and Details.

The full run initially exposed a timing assumption in `PracticeMatchSystemTest`: it clicked a
Unit after a fixed 500 ms, while King deployment takes 650 ms. The test now explicitly waits
for King deployment, card effects and scoring to finish before its existing hand-settling delay.
The focused practice scenario then passed (1 test, no failures/errors/skips, 12.28 s test time).

## Limits that remain

- History filters operate on the server's currently loaded limited response, not all database rows.
- Score deltas compare adjacent stored frames. The live protocol's separate final pre-bank view is
  not guaranteed to be present in those recordings; no missing final score state is reconstructed.
- A recording gap can combine several actions in one comparison. It is never labelled as a
  precise single-card score impact.
- This work does not add replay sharing, exporting, deletion, retention or incompatible-frame migration.
