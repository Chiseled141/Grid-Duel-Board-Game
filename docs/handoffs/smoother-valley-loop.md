# Smoother valley loop

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

Request: reduce visible animation stutter with more frames and/or slower motion.
Acceptance: retain the authored scene, increase actual distinct frames, keep GIF
frame delays uniform and the loop seamless, preserve native playback/reduced
motion, rebuild and relaunch for the user to inspect.

## Change

- tools/RenderValleyLoop.java renders 400 frames at 25 fps over sixteen seconds,
  replacing 120 frames at 12 fps over ten seconds. This samples the same complete
  motion cycle more densely and reduces motion speed by 37.5%.
- Waterfall phase now derives from normalized cycle position so it slows with
  the rest of the scene and still wraps by an exact number of highlight spacings.
- Re-encoded assets/menu-valley.gif with uniform four-centisecond delays and
  infinite looping. Replaced artifacts/menu-valley.mp4 with the matching preview.
- ValleyMenuPanelTest asserts the frame count and each frame's uniform 40 ms delay.
- Runtime playback code, generated artwork, menu navigation and gameplay unchanged.

## Reproduction

    java -Djava.awt.headless=true tools/RenderValleyLoop.java /tmp/grid-duel-smooth-loop
    ffmpeg -framerate 25 -i /tmp/grid-duel-smooth-loop/%03d.png -filter_complex '[0:v]split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle' -loop 0 -y src/main/resources/assets/menu-valley.gif
    ffmpeg -framerate 25 -i /tmp/grid-duel-smooth-loop/%03d.png -c:v libx264 -crf 19 -pix_fmt yuv420p -movflags +faststart -an -y artifacts/menu-valley.mp4

## Validation

- `mvn test`: PASS, 309 tests, zero failures/errors/skips.
- Independent read-only review and `mvn clean verify`: PASS, 309 tests, zero
  failures/errors/skips; shaded JAR built.
- Independent decoding found all 400 frames distinct; ffprobe confirms 1280x800,
  encoded 25 fps, sixteen seconds. Tests verify uniform four-centisecond delays.
- Native JDK decoder probe: fifty frame updates averaged 54.91 ms (18.21 fps)
  during Maven, and 52.94 ms (18.89 fps) afterward. Encoded frame rate is not a
  promise of sustained full-window frame rate. User visual check remains useful.
- Independent review verified periodic cloud/branch/leaf motion and waterfall
  phase closure at twelve highlight spacings. No actionable defects.
- Updated client relaunched after verifying its process working directory.
