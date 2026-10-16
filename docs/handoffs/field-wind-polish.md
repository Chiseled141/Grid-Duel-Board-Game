# Golden fields wind polish and default

User requests: remove the artificial added grass, add a small wind effect, and
make Golden fields the default rather than Mountain valley.

- RenderBattlefieldLoop.java removes all grass-atlas loading and clump drawing.
  The painted meadow remains. Five low-opacity curved wind wisps travel across
  the field; drifting seeds, cloth flutter, rotating sails and clouds remain.
- Re-rendered the 400-frame, sixteen-second, encoded-25-fps GIF and MP4 preview.
- ClientSettings and MenuTheme default absent/invalid/null choices to Golden
  fields. Valid explicitly saved choices (including Mountain valley) still work.
- Updated preference and UI tests for the new default. Painted switching test
  explicitly starts in Valley so it verifies switching both directions.
- docs/architecture.md records current grass removal, wisps and default behavior.

Validation: `mvn test` and independent `mvn clean verify` passed all 313 tests,
zero failures/errors/skips. Independent read-only review PASS, no actionable defects.
GIF probe confirms 400 frames, sixteen seconds, encoded 25 fps. Inspected authored
frame50 and independently decoded frame50: artificial foreground clumps removed.
Verified old client working directory before restart. Selected Golden fields in the
user preference file as explicitly requested, preserving other settings, and relaunched.
No gameplay/protocol changes. Actual full-window smoothness remains device-dependent.
