# Audio playback and mute behavior

The header music button controls `MenuMusic`; Settings → Game sounds controls
`ScoringSounds`. Both use their own audio worker and never perform device I/O on
the Swing EDT or JavaFX Application Thread.

`MenuMusic` stops, flushes and closes its native `Clip` when muted. It remembers
the playback position and reopens the same bundled track at that position when
enabled again. A mute request made while the clip is loading prevents playback.

`ScoringSounds` plays bundled recordings for Card tap, Pair bell, Gain, Impact,
Chain link, Spell shimmer, Tally coin and Flame crackle. The matching MP3 files live under
`src/main/resources/audio/sfx/` and use JavaFX Media playback. Other cue types
keep their synthesized sounds. If a recording cannot load, that cue falls back
to its synthesized sound. Synthesized cues drain their audible tail, then stop
and flush the `SourceDataLine`. `stop()` cancels pending cues, stops recordings
and releases the device; later enabled cues can play again. The settings panel
invokes this when Game sounds is turned off. Leaving the match table also stops
its animation timer, remaining effects and queued audio so those cues do not
continue in the menu.

Recordings use `AudioClip.setRate(...)` followed by `play()`. The five-argument
`AudioClip.play` overload takes volume, balance, rate, pan and priority in that
order. Passing the pitch as its first argument and zero as its third argument
paused the recordings at rate zero in the first integration. The resource-only
check did not catch this. `ScoringSoundsMediaTest` now submits each recording to
the native player at zero volume, verifies its rate and waits for natural
completion; separate checks cover rising pair bells and tally coins. The
injected PCM test device bypasses recorded playback so those tests cannot send
recordings to the real speakers.

Already executing device writes finish on the audio worker before it releases
the line. Closing normally still allows previously accepted cues to finish.

## Regression scenarios

Tests use a recording audio-line stub rather than the real speakers:

| Input | Expected result |
|---|---|
| One deal cue completes | Drain precedes stop; the output is not left running |
| Mute with two cues queued behind a current write | Pending cues are discarded; the device closes |
| Enable sound after mute | A fresh output plays newly requested cues |
| Mute menu music, then enable it | The old clip closes; the new clip resumes at the saved position |
| Mute while the clip is being opened | The prepared clip closes without starting |
| Click Game sounds twice | The playback callback sees Off, then On; both choices are saved |
| Leave during the opening deal | No remaining deal cues play on the hidden table |
| Check bundled cue mapping | All eight recordings exist; unmapped cue types retain synthesis |

The reported clicking has not been identified from the recording alone. User
checks on 2026-10-25 found that system-wide Windows mute silences it, while the
reported per-application mute, Reduced motion and a temporary software-rendering
launch did not. These changes address observable audio lifecycle defects; whether
they eliminate that particular noise was checked by the user on the affected
machine after the rebuild (see listening result below). No graphics defaults or
Windows audio settings were changed.

## Executed checks — 2026-10-25

Environment: Windows, Temurin JDK 21.0.10, Java 17 target, OpenJFX 21.0.5;
Swing/JavaFX tests run in a graphical session.

- `mvn test`: 294 executed, 1 failure and 7 errors. Four errors came from an
  obsolete compiled `PixelSpellArtTest` absent from the source tree; that number
  is not current source coverage. The other issues were server tests using UNIX
  control sockets on Windows (two errors), a UNIX path assertion (one failure),
  and a locked temporary TLS database during cleanup (one error). All 48 tests
  in the four audio/navigation test classes passed. These server-only failures
  were not changed as part of the audio fix. Reports were retained under the
  ignored `artifacts/audio-diagnostic/full-suite-2026-10-25/` before cleaning.
- `mvn clean package -DskipTests`: BUILD SUCCESS; regenerated the game, server
  and launcher jars without obsolete compiled files. This command skips tests.
- After the clean build,
  `mvn "-Dtest=ScoringSoundsTest,MenuMusicTest,CardTableViewTest,LobbyMenuTest" test`:
  **48 tests, 0 failures, 0 errors, 0 skipped**. The scenarios in the table above
  passed. Reports are in `target/surefire-reports/`.

The rebuilt local server and normal client were relaunched. Both in-app audio
preferences were initially turned off through `ClientSettings` for the user's
listening check; other preferences were retained. The system-wide speaker mute
must be off for that check to be meaningful.

## User listening result — 2026-10-25

With the rebuilt client running, both in-app audio preferences off and Windows
speakers enabled, the user reported: **the clicking has stopped** (PASS).
This confirms the reported muted-playback symptom is resolved in that check;
it does not identify which individual lifecycle change removed the noise or
verify every enabled-sound scenario.

## Recorded sound playback correction — 2026-10-25

Environment: Windows, Temurin JDK 21.0.10, Java 17 target, OpenJFX 21.0.5.

- Input: each of the seven bundled MP3 recordings at its normal playback rate,
  then Pair and Tally at step six. Expected: native playback starts, the rate is
  1.0 or 2.0 respectively, and playback finishes naturally within eight seconds.
  Actual: all seven recordings and both rising-cue scenarios completed (PASS).
  The native checks mute each clip instead of changing Windows audio settings.
- `mvn "-Dtest=ScoringSoundsTest,ScoringSoundsMediaTest" test`: **9 tests,
  0 failures, 0 errors, 0 skipped**. The PCM injection tests also passed after
  restoring their isolation from real recorded playback.
- `mvn test`: **293 tests, 1 failure, 3 errors, 0 skipped**. Both sound test
  classes passed. The remaining results match the pre-existing Windows issues:
  a UNIX path assertion, two POSIX-permission checks and cleanup of a locked
  temporary TLS database. The full log and sound reports are retained under
  ignored `artifacts/sound-recording-fix/`.
- `mvn clean package -DskipTests`: **BUILD SUCCESS**. Tests were run above;
  this command rebuilt all three jars after the idle local server was stopped.
- The retained `PackagedSoundProbe.java` was compiled against the rebuilt game
  jar and submitted all seven `jar:` resources through `playRecording` with
  clip volume zero. Expected: each starts at a positive rate and completes
  naturally. Actual: **7 PASS**, with normal cues at rate 1.0 and step-six Pair
  and Tally at rate 2.0. Results are in `packaged-playback.log` beside the probe.
  This verifies the native playback path and completion, not speaker output.

## Recording replacements and Flame crackle — 2026-10-25

The delivered `chain_link.mp3` replaces the Disable Points chain sound.
`spell_shimmer (2).mp3` replaces the spell energy sound and is bundled under the
existing name `spell_shimmer.mp3`. The new `flame_crackle.mp3` maps to `FLAME`,
played when Random Discard ignites its target card. Both the source copies in
`sound/` and the bundled files in `src/main/resources/audio/sfx/` were updated.
There are now eight recorded cue types; other cues retain synthesis.

Checks in the Windows/JDK/OpenJFX environment above:

- Source and packaged SHA-256 checks: all three changed recordings match the
  delivered files byte for byte (PASS).
- `ScoringSoundsTest` and `ScoringSoundsMediaTest`: **9 tests, 0 failures,
  0 errors**; native playback now covers all eight recordings.
- `mvn test`: **293 tests, 1 failure, 3 errors, 0 skipped**, with the same
  UNIX-path, POSIX-permission and locked-TLS-database issues listed above.
- `mvn clean package -DskipTests`: **BUILD SUCCESS**. The packaged native
  playback probe completed all **8 recordings** (PASS), including Flame crackle.

Logs, native test reports and source/packaged hash checks are retained in ignored
`artifacts/sound-assets-update/`. The local server and updated client were reopened.
