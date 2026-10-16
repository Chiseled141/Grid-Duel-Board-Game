# Floating menu actions

Request: remove the cream menu container and place actions directly on the
background, with colors that suit both Golden fields and Mountain valley.

- LobbyPanel.java removes the painted card surface and home eyebrow label. Main
  actions are unboxed text with larger type; secondary links remain small. Buttons
  retain hit areas, keyboard activation and visible focus/hover/selected underlines.
- Uses warm ivory, champagne primary text and pale gold highlights, backed by a
  subtle full-height right-edge gradient and text shadows. No icons, filled button
  rectangles or panel border. Same palette works on both scenes.
- Account and leaderboard stack vertically to avoid clipping. All existing Play,
  Settings, theme selection, join/create/practice and parked-match routes remain.
- docs/architecture.md describes the current presentation. No asset, animation,
  protocol, server or gameplay changes.

Validation:
- Full `mvn test`: 313 tests, zero failures/errors/skips, BUILD SUCCESS.
- Independent read-only review and `mvn clean verify`: PASS, 313 tests, zero
  failures/errors/skips; shaded JAR built. No actionable defects.
- Rendered and inspected 1440x820 previews on Golden fields and Mountain valley;
  account/leaderboard text stays visible and no boxed menu remains. Reviewer
  independently inspected both previews and keyboard/focus/navigation behavior.
- Restarted the verified project client with the rebuilt JAR for user inspection.
