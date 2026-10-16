# Prerecorded valley menu loop

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

## Request and acceptance

Replace rejected static-image bending with an actual GIF/short video. The menu
must show drifting clouds, falling leaves and wind in foreground tree branches,
keep the background recognizable and leave the compact navigation intact.

## Implementation

- Removed CanopyWarp and its implementation-specific test. Removed all live
  procedural scene effects from ValleyMenuPanel.
- Generated a clean background plate, isolated pine branches and a transparent
  cloud bank using the built-in image-generation tool. The original valley stays
  untouched. Sources live under src/main/resources/assets/valley-animation/.
- Authored a ten-second, 120-frame loop with tools/RenderValleyLoop.java. Separate
  branches rotate rigidly at their roots. Clouds drift, leaves cross the frame and
  waterfall highlights descend; buildings and cliffs stay fixed.
- Encoded assets/menu-valley.gif for native Swing playback and
  artifacts/menu-valley.mp4 for preview. No subscription or runtime dependency.
- ValleyMenuPanel loads GIF/first frame on a worker, uses uniform cover scaling,
  shows a still for Reduced motion and detaches inactive image observers.
- ValleyMenuPanelTest covers real multiple-frame media, infinite looping,
  visibility/reduced-motion/screen behavior, cover scaling and removed wordmark.

## Artwork prompt set

Built-in image generation, with cinematic-valley.png as the input reference:

1. Clean plate: remove only the large upper-left and lower-right foreground pines;
   reconstruct distant sky, mountains and valley mist behind them. Preserve temples,
   cliffs, sunset, palette, painterly style, camera and composition. No text/UI.
2. Transparent branch atlas: two separate long horizontal twisted pine branches,
   upper entering from left and lower entering from right, with dark jade needles
   and golden sunset highlights matching the valley. Transparent empty spacing;
   no sky, landscape, text or rendered checkerboard.
3. Transparent cloud bank: one long soft sunlit valley cloud, warm ivory/peach/gold
   edges and pale lavender undersides, painterly detail and airy transparent margins,
   matching the valley. No sky, landscape, mountains, ground or text.

These are still-art layers composited into an authored animation, not an AI-video
model output. The connected Runway Free workspace could not generate video; no
paid plan was purchased.

## Reproduction

From the repository root, render to a temporary directory:

    java -Djava.awt.headless=true tools/RenderValleyLoop.java /tmp/grid-duel-authored-loop

Encode the 12-fps PNG sequence with ffmpeg palettegen/paletteuse for GIF, infinite
loop, and libx264/yuv420p/faststart for the MP4 preview. ffmpeg is authoring-only.

## Validation

- `mvn test`: PASS, 309 tests, zero failures/errors/skips.
- Independent read-only review: PASS, no actionable defects.
- Independent `mvn clean verify`: PASS, 309 tests, zero failures/errors/skips;
  final shaded JAR built.
- ffprobe confirms GIF 1280x800, 120 frames, exactly ten seconds; infinite-loop
  metadata covered by the test. Independent review decoded four sampled frames.
- Native JDK playback probe received twelve animated FRAMEBITS updates within five
  seconds. Cover scaling and reduced-motion/hidden/screen state tests passed.
- Inspected authored first frame and MP4 preview. Native desktop inspection was
  unavailable because the computer-use app selector rejected the Java application;
  no claim of direct desktop screenshot verification.
- No gameplay, networking, database, account settings or runtime dependencies changed.
