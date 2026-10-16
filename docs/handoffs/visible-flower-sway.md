# Visible flower sway

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

User feedback: existing flowers do not visibly move at normal menu size.

- RenderBattlefieldLoop.java increases flower-root pivot distance from18to34
  source pixels and slow rotation from.075to.20 radians, secondary motion from
  .018to.035. Neighboring phases change by.35 rather than.61, producing a more
  coherent passing gust. No new cutouts or artwork; exactly the same eleven
  source flowers are used once each.
- Calculated head-center horizontal travel across all400 frames is11.49to12.11
  pixels at1280x720 GIF resolution, replacing the previous roughly2pixel sway.
- Re-rendered the400frame, sixteen-second GIF and matching MP4. Existing
  flags, windmills, clouds, seeds and wind wisps remain unchanged.
- docs/architecture.md records the visible travel. No runtime or gameplay changes.

Validation: full `mvn test` and independent `mvn clean verify` PASS313tests,
zero failures/errors/skips; shaded JAR built. Independent read-only review PASS,
no actionable defects. Source-pixel probe again verified11cutouts3274originalpixels
and exactunrotatedreassembly. Independent calculation confirms11.49to12.11pixel
headtravel; decoded frames0/33 visibly differ in foregroundflowerregions. GIF
remains400frames16sec1280x720. Added artifacts/flower-wind-closeup.mp4 preview
and displayed it to the user. Restarted verified project client with rebuilt JAR.
