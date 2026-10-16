# Minimal main menu

Replaces the tall stack of lobby/profile/settings cards with one compact,
translucent menu on the right, leaving most of the animated valley visible.

## Navigation and acceptance

- Home: Play, Settings, Quit game. Account and leaderboard are small footer items.
- Play: Create match, Join a game, Practice, with Back navigation.
- Create: sends a single request, displays the returned room code and offers Copy.
  Returning to this page reuses a pending code instead of creating another room.
- Join: five-character code input, typed open-room list, selection/Enter/Join and
  Refresh. Polling runs only while this page is visible in the lobby.
- Practice: Rookie, Senior, Legend. Existing parked-match Resume remains on home;
  new match options are disabled until that match is resumed/finished.
- Settings: Ink tokens, Seal stones, Ink silhouettes, with a selected indicator.
  Styles apply immediately; preference persistence runs on a worker.
- Quit delegates to ClientFrame.closeAndSave, preserving normal cleanup.

## Changed files this iteration

- `src/main/java/onitama/client/ui/LobbyPanel.java`: focused menu pages, compact
  typography, translucent surface, routing, room browser and settings controls.
- `src/main/java/onitama/client/ui/ClientFrame.java`: passes the normal quit callback.
- `src/main/java/onitama/client/ClientSettings.java`: synchronizes preference file
  writes so menu workers and frame saves cannot write concurrently.
- `src/test/java/onitama/client/ui/LobbyMenuTest.java`: primary-action geometry,
  quit callback, create/join/practice, pending creation, settings persistence seam,
  browser selection and parked-match Resume regression coverage.
- `src/test/java/onitama/client/ui/ValleyMenuPanelTest.java`: updates constructor call.
- `docs/architecture.md`: documents compact navigation and preference persistence.
- `docs/handoffs/minimal-main-menu.md`: change inventory and validation evidence.

## Validation

- Full `mvn test`: 308 tests, zero failures/errors/skips, BUILD SUCCESS.
- Focused menu tests passed after fixing a clipped account caption found in preview.
- Independent `mvn clean verify`: 308 tests, zero failures/errors/skips, shaded
  JAR built. Scoped independent review PASS with no actionable defects.
- Rendered home at 1440×820 and Play/Join/Settings at 1280×660; inspected for
  clipping and background exposure. The menu surface is 340 pixels wide and the
  home page occupies less than 360 pixels vertically at the tested small size.
- No gameplay, database, server or protocol changes.
