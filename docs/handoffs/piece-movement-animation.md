# Visible piece movement — 2026-10-17

BoardPanel now renders accepted piece moves over 480ms instead of 260ms, with
smooth acceleration/deceleration, the existing small arc and a soft moving shadow.
It uses the same medieval token sprites and viewer-coordinate conversion for both
colors and simultaneous multi-piece moves. A test clock allows deterministic
intermediate-frame verification without timing sleeps.

GamePanel no longer cancels an active board move for an accepted update that does
not move/eliminate pieces. Turn-end, resource and phase-only updates can therefore
arrive without cutting the slide short. A new board-changing update replaces the
old animation. Existing explicit-input, reduced-motion, reconnect/reset/disposal
cleanup remains; authoritative state is adopted immediately, independent of visuals.

Changed runtime: BoardPanel and GamePanel. Tests: BoardMovementAnimationTest checks
Blue/Red travel frames, final state, one completion callback, timer shutdown and
reduced-motion snapping. PresentationRegressionTest delivers a real opponent move
then an End Turn and verifies uninterrupted travel with the latest permitted view.
Existing capture-callback cancellation, skill/group events and lifecycle tests pass.

Native full mvn test: 497 passed; zero failures/errors/skips.
Independent Agent 2 native full suite: 497 passed; no actionable findings.
Final native mvn clean verify: 497 passed; shaded target/onitama.jar rebuilt.
Existing localhost server verified on port 5555; updated client launched.
