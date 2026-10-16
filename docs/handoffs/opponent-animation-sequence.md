# Prevent opponent animation cutoff — 2026-10-17

The bot acts every 300ms while movement lasts 480ms, a skill play lasts 540ms,
and draws last 600ms plus staggering. GamePanel canceled draw/play effects at
every accepted action and replaced board movement at the next moving skill.

GamePanel now queues immutable permitted presentation batches. The visible board,
hands and badges follow the active batch while ClientModel adopts the newest
accepted state immediately. One one-shot Swing timer advances after the longest
move/capture, skill flight or staggered draw plus a 48ms timer grace. Phase-only
updates drain without extra delay. No networking or bot decision timing changed.

Deferred draw callbacks compare against the displayed revision, so a newer model
revision cannot discard the earlier draw or substitute its card face. Opponent
skills stay identity-free backs. Last-move markers follow the active batch.
Gameplay selection/actions wait for playback to finish; Menu/Surrender remain.
BoardPanel no longer finishes a move before GamePanel's busy input guard runs.

Reconnect/new match, leaving, disconnect, pause, resize, disposal and reduced
motion clear queued work and timers. Terminal outcome updates retain the existing
immediate snap. Reduced motion displays the newest state without queued playback.

Changed runtime: GamePanel and BoardPanel. Architecture documents sequencing.
AnimationSequenceTest delivers real move/dash/end transitions immediately for both
opponent orientations, verifies uninterrupted movement and older draw completion,
then skill flight, concealed draw and final snapshot; blocked clicks cannot stop
the animation. Reset cases verify queue/timer/flight cleanup.

Native full mvn test: 504 passed; zero failures/errors/skips.
Independent Agent 2 native full suite: 504 passed; no blocking findings.
Final native mvn clean verify: 504 passed; shaded target/onitama.jar rebuilt.
Updated client launched for the user.
