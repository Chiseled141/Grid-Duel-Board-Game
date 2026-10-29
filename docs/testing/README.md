# Verification

Run from the repository root with Java 17 or newer and Maven. Desktop checks require a graphical
session. Tests use temporary databases and ephemeral loopback ports, not the live account database.

```sh
mvn test
mvn clean verify
```

On Windows, close processes using the packaged JARs before `clean verify`. If the server must stay
running, build a fresh copy of the current sources in a separate directory instead. Do not copy
personal data, logs, old build output or deleted files into that directory.

## Regression coverage

| Input / scenario | Expected result | Test classes |
| --- | --- | --- |
| Slow drag, held press, invalid drop, Escape, hide and new match | Visible lift/return; no ghost card or unintended move | `CardTableViewTest` |
| Unit hover / Spell hover beside deployed Kings | No Unit popup; readable Spell rules above King artwork | `CardTableViewTest` |
| King offer, hidden choice and deadline | Private simultaneous confirmations; first offered King after 30 seconds | `KingChoiceDeadlineTest`, `CardMatchIntegrationTest` |
| King details, icons, points and aura | Compact original artwork; correct King colors and labels | `KingPresentationTest`, `KingVisualsTest` |
| Newly public King selection | Painting and aura fly into the proper round-one/round-two slot; cancellation restores slots | `KingDeploymentTest` |
| All seven supplied King images | Original SHA-256 and aspect ratio preserved | `KingArtTest` |
| Last play of a round or match | Complete effects and scoring before the conclusion; authoritative banked totals | `RoundScoringSequenceTest`, `RoundClosingViewTest` |
| Quick guide | Every step shows its tip and bundled clip; Back leaves without sending anything | `HowToPlayPanelTest` |
| Replay seek, round jumps and filtered action journal | Seek pauses, original frame indices stay correct, missing frames are explicit, hidden pages stop timers | `ReplayNavigationTest`, `ReplayReviewTest` |
| Replay score comparison | Raw components and multiplier effect reconcile exactly; banked totals and per-King changes remain separate | `ReplayScoreChangeTest` |
| History filters and refresh | Search/result/mode combine locally, practice stays outside W/L, replay selection follows its id | `HistoryFilterTest`, `HistoryFilteringTest` |
| Recorded practice through real server and client | Review, seek, journal, Back and filter/clear succeed without another match | `ReplayJourneySystemTest` |
| Audio mute/resume and recorded cues | Devices close on mute; clips run at a positive rate and complete | `MenuMusicTest`, `ScoringSoundsTest`, `ScoringSoundsMediaTest` |
| Accounts, rooms, reconnect, history and replay access | Authentication, privacy, transactions and protocol filters remain enforced | Full suite |

## Latest run

On 2026-10-28 the current checkout passed `mvn clean verify`: **404 tests,
zero failures, errors or skips**, BUILD SUCCESS in 3m42s (Windows, Temurin 21.0.10,
Maven 3.10.0, Java 17 target, OpenJFX 21.0.5). This includes 34 new replay/history tests,
the existing real-server journey extended to review/filter controls, and the two King deployment
tests from the preceding change. All three packaged JARs were rebuilt. The local server/client
were stopped for the clean build; personal account/history data was retained.

See [replay review verification](replay-review.md) for scenario inputs, expected outcomes,
observed results, screenshot paths and limitations. The complete run is retained locally in
`data/guide-simple-build.log` and machine reports in `target/surefire-reports/`.

Keep the latest machine reports in ignored `target/surefire-reports/` and build logs in
`target/verification/`. Superseded screenshots, diagnostic recordings, duplicate reports and
historical build trees are disposable. Guide clips and runtime artwork/audio
under `src/main/resources/` are application assets and must be retained.
