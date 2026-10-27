# Audio playback and mute behavior

The header music button controls `MenuMusic`; Settings → Game sounds controls
`ScoringSounds`. Both use their own audio worker and keep device I/O off the
Swing EDT and JavaFX Application Thread.

`MenuMusic` stops, flushes and closes its native `Clip` when muted. It remembers
the position and resumes the bundled track when enabled. Muting during loading
prevents playback from starting.

Eight original MP3 recordings are bundled in `src/main/resources/audio/sfx/`:
Card tap, Pair bell, Gain, Impact, Chain link, Spell shimmer, Tally coin and
Flame crackle. Other cues use synthesis; failed recordings fall back to synthesis.
Redundant import copies outside the resource tree are not required.

Recorded cues use `AudioClip.setRate(...)` before `play()`. The five-argument
`AudioClip.play` overload accepts volume, balance, rate, pan and priority in that
order. A zero rate pauses playback. Pair bells and tally coins rise with the
scoring step. Flame crackle accompanies Random Discard.

Synthesized cues drain their audible tail, then stop and flush the audio line.
`stop()` cancels queued cues, stops recordings and releases the device. Muting
Game sounds or leaving the match table stops pending sounds and effects;
subsequently enabling sound permits fresh playback.

## Checks

| Scenario | Expected result |
| --- | --- |
| Deal cue finishes | Drain precedes stop; output is not left running |
| Mute with queued cues | Pending cues are discarded and the device closes |
| Enable after mute | Fresh output plays newly requested cues |
| Mute/resume menu music | Old clip closes; new clip resumes at the saved position |
| Mute while loading | Prepared clip closes without starting |
| Leave during opening deal | Hidden table emits no remaining deal cues |
| Bundled cue mapping | Eight recordings exist; other cues retain synthesis |
| Native recorded playback | Positive rate and natural completion, including rising cues |

PCM tests inject a recording device and cannot send recorded clips to the
speakers. Native `ScoringSoundsMediaTest` submits each recording at zero volume
and checks its rate and natural completion; this verifies playback rather than
audible speaker output. The user confirmed that the earlier clicking stopped
with both in-app audio preferences off and Windows speakers enabled on 2026-10-25.

See [verification notes](testing/README.md) for current suite results. Superseded
recordings, temporary probes and historical diagnostic logs are not runtime assets.
