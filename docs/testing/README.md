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
| All seven supplied King images | Original SHA-256 and aspect ratio preserved | `KingArtTest` |
| Last play of a round or match | Complete effects and scoring before the conclusion; authoritative banked totals | `RoundScoringSequenceTest`, `RoundClosingViewTest` |
| Guide playback | All 17 recordings validate; private choices stay recipient-visible | `TutorialLibraryTest`, `HowToPlayPanelTest` |
| Audio mute/resume and recorded cues | Devices close on mute; clips run at a positive rate and complete | `MenuMusicTest`, `ScoringSoundsTest`, `ScoringSoundsMediaTest` |
| Accounts, rooms, reconnect, history and replay access | Authentication, privacy, transactions and protocol filters remain enforced | Full suite |

## Latest run

On 2026-10-25, a fresh copy of the cleaned project passed `mvn clean verify`: **368 tests,
zero failures, errors or skips**, BUILD SUCCESS in 3m55s (Windows, Temurin 21.0.10,
Java 17 target, OpenJFX 21.0.5). All 375 source/resource files matched the verified build.
All 107 runtime resources retained their original bytes, local Markdown links resolved,
and the retained server answered the protocol-19 probe on localhost:5555.

Cleanup removed 1,858 old/generated/duplicate files and consolidated nine superseded Markdown
documents. The latest reports replace historical report trees and screenshots. The primary
client and launcher packages were refreshed from the verified build; the active server JAR
and the account/history database were retained.

Keep the latest machine reports in ignored `target/surefire-reports/` and build logs in
`target/verification/`. Superseded screenshots, diagnostic recordings, duplicate reports and
historical build trees are disposable. Tutorial `.duel` recordings and runtime artwork/audio
under `src/main/resources/` are application assets and must be retained.
