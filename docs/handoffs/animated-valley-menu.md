# Animated valley menu and title cleanup

Adds quiet motion to the existing painted valley on login and lobby: low mist
moves through three cloud bands, highlights flow down two existing waterfalls,
and sunlight softly shimmers around the painted sun. The camera stays fixed.
The lobby's old ONITAMA title sticker/seal and the top navigation wordmark are
removed. Login now says WELCOME; the native window and error titles use Grid Duel.
The About dialog retains its movement-game attribution.

## Files changed this iteration

- `src/main/java/onitama/client/ui/ValleyMenuPanel.java`: shared animated painting,
  cached mist texture, monotonic EDT clock and visibility/reduced-motion lifecycle.
- `src/main/java/onitama/client/ui/LobbyPanel.java`: adopts living valley, retains
  action-card contrast scrim, removes title block and unused seal renderer.
- `src/main/java/onitama/client/ui/LoginPanel.java`: adopts living valley and neutral
  welcome heading instead of obsolete branding.
- `src/main/java/onitama/client/ui/ClientFrame.java`: removes navigation wordmark
  and changes native window/error titles to Grid Duel.
- `src/test/java/onitama/client/ui/ValleyMenuPanelTest.java`: verifies atmosphere
  changes with time, fixed camera, deterministic still frame, motion preference,
  hiding/screen/removal timer stops, and absence of old entry-menu titles.
- `docs/architecture.md`: documents animation implementation and title scope.
- `docs/handoffs/animated-valley-menu.md`: delivery and validation record.

## Validation

- `mvn test`: 304 tests, zero failures/errors/skips, BUILD SUCCESS.
- Independent `mvn clean verify`: 304 tests, zero failures/errors/skips; final
  shaded JAR built. Scoped independent review PASS, no actionable defects.
- Rendered and inspected login/lobby at 1440×820, with lobby frames at 0 and 8 seconds.
  Original artwork is 1586×992 and effects share its left-anchored cover transform.
- No new dependencies, remote media, gameplay, server, database or protocol changes.

## Operational behavior

The animation is rendered directly with Java2D; no GIF/video file or decoder is
required. Its 66 ms Swing timer only runs while the relevant menu is actually
showing and Reduced motion is off. Hiding/removing the menu or selecting another
screen stops it; returning resumes its local clock. Reduced motion uses a static
frame. Existing menu music remains governed by the sound setting.
