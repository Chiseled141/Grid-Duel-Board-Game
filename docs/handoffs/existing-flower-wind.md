# Wind on existing foreground flowers

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

Request: cut out the little flowers already in the background and animate them
in the GIF. Do not add new elements or flowers.

- tools/RenderBattlefieldLoop.java extracts eleven existing flower-head regions
  from the current clean-plate.png using bounded petal-color masks. Every opaque
  cutout pixel is copied verbatim from that plate, with original coordinates.
- Their vacated pixels use nearby source foliage so stationary duplicate heads
  do not remain. Each cutout is drawn once, gently rotating about a stem-root
  pivot, with periodic slow wind and a smaller secondary motion. No grass layer,
  added flowers, generated artwork or runtime scene deformation.
- Existing flag, windmill, cloud, seed and small-wisp motion remains unchanged.
- Re-exported assets/menu-battlefield.gif and artifacts/menu-battlefield.mp4 with
  400 frames, sixteen seconds and encoded25fps as before.
- Optional second argument to the offline renderer exports exact transparent
  cutouts and the prepared plate under artifacts/flower-cutouts/.
- docs/architecture.md records the source-pixel extraction and offline playback.

## Validation

Source-pixel probe verified eleven nonempty cutouts, 3274 copied pixels, and exact
whole-image reconstruction when cutouts are restored without rotation: every
pixel matches the original clean plate. This checks no flower pixels were invented
and cutouts are removed/replaced rather than added as duplicates.

Inspected the first authored frame; independent reviewer inspected frame33 and
reran the source-pixel reconstruction probe. Full `mvn test` and independent
`mvn clean verify`: PASS, 313 tests, zero failures/errors/skips; final shaded JAR
built. Scoped independent review PASS, no actionable defects. Media probe verifies
400frames, sixteen seconds, encoded25fps, 1280x720. Restarted verified project
client with rebuilt JAR at the user's request.

## Reproduction

    java -Djava.awt.headless=true tools/RenderBattlefieldLoop.java /tmp/grid-duel-field-flowers artifacts/flower-cutouts

Encode the resulting PNGs at25fps using the same palettegen/paletteuse infinite-GIF
and libx264 MP4 commands documented in golden-fields-menu-theme.md, with the new
frame-directory path. No additional runtime libraries or generated art required.
