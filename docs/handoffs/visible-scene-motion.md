# Visible canopy and cloud motion

Corrects the previous effect's limitation: added decorative tips and low-opacity
mist were not enough to make the painted background look alive.

## Behavior and files changed

- `src/main/java/onitama/client/ui/CanopyWarp.java`: caches two regions of the
  original painting and deforms their pixels with a bounded affine triangle mesh.
  Painted pine canopies sway; fixed patch edges avoid shifted ghost copies.
- `src/main/java/onitama/client/ui/ValleyMenuPanel.java`: replaces procedural twig
  decorations with painted-canopy warps, renders five continuously travelling cloud
  banks at distinct depths/speeds, and strengthens water/light motion. All layers
  still share the visibility/reduced-motion clock; static mountains and camera remain.
- `src/test/java/onitama/client/ui/CanopyWarpTest.java`: verifies substantial painted
  pixel movement, no spill outside the patch, and restored caller graphics state.
- `src/test/java/onitama/client/ui/ValleyMenuPanelTest.java`: samples a stationary
  cliff rather than foliage for the fixed-camera assertion, since the canopy now moves.
- `docs/architecture.md`: describes painted-canopy deformation and cloud travel.
- `docs/handoffs/visible-scene-motion.md`: change inventory and validation evidence.

## Validation

- Focused scene/canopy tests passed.
- `mvn test`: 310 tests, zero failures/errors/skips, BUILD SUCCESS.
- Independent `mvn clean verify`: 310 tests, zero failures/errors/skips; shaded JAR
  built. Scoped independent review PASS, no actionable defects.
- Rendered a six-second scene sequence at twelve frames per second and inspected
  frames at 0, 2 and 4 seconds; a GIF preview was displayed in the conversation.
- Confirmed the saved client preference is reducedMotion=false; no setting changed.
- Real-device frame rate has not been benchmarked. The shared EDT timer coalesces
  frames; source patches and cloud texture are cached, and hidden menus stop animating.
- No new dependencies, downloaded assets, gameplay/server/protocol/DB changes.
