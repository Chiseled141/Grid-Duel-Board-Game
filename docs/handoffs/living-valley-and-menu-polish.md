# Living valley and menu polish

Keeps the compact Play / Settings / Quit structure while replacing the flat teal
box with an ivory frame, fine brass borders, vector navigation icons, a jade Play
action, consistent secondary rows and a separated account/leaderboard footer.
Submenus use the same light palette and retain all existing routing.

## Background motion

- Four soft cloud/mist layers now drift farther with distinct phases.
- Fourteen small windblown leaves tumble across the scene and wrap outside its edges.
- Four groups of native foliage tips sway within painted foreground tree canopies.
- Existing waterfall motion, sunlight and fixed camera remain.
- All layers share the menu's clock and painting transform; Reduced motion freezes
  the complete scene, and hidden/off-screen menus stop their timer.

## Files changed this iteration

- `src/main/java/onitama/client/ui/ValleyMenuPanel.java`: larger cloud drift,
  flying leaves, wind-driven foliage and 33 ms animation clock.
- `src/main/java/onitama/client/ui/LobbyPanel.java`: ivory/brass surface, jade primary
  action, vector icons, selected-style treatment, light submenu palette and footer.
- `src/test/java/onitama/client/ui/ValleyMenuPanelTest.java`: regression coverage
  that wind changes foreground foliage and valley cloud regions.
- `docs/architecture.md`: documents the current visual treatment and motion layers.
- `docs/handoffs/living-valley-and-menu-polish.md`: delivery/validation record.

## Validation

- `mvn test`: 309 tests, zero failures/errors/skips, BUILD SUCCESS.
- Independent `mvn clean verify`: 309 tests, zero failures/errors/skips; final
  shaded JAR built. Scoped independent review PASS with no actionable defects.
- Rendered and inspected home at 1440×820 and Settings/Join/Play at 1280×660.
  Existing navigation, compact geometry, settings persistence and quit tests remain green.
- No new dependencies, asset downloads, gameplay changes or protocol changes.
