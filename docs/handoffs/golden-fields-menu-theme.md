# Golden fields animated menu theme

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

## Request and acceptance

Add a Change theme button and a second scene from the supplied sunset battlefield
image. Its torn flags must wave in the wind, both windmills must turn and foreground
grass must sway. Preserve the smoother/slower pacing and allow switching back to
the valley. Keep the compact menu and save the selected theme across launches.

## Files and behavior

- MenuTheme.java defines the two local choices and media names; unknown saved
  values fall back to Valley. ClientSettings.java persists menuTheme. Path-based
  package-private load/save helpers let tests use temporary files.
- ValleyMenuPanel.java caches each clip asynchronously, picks the shared selected
  theme, shows its first frame for Reduced motion and rejects inactive/old clip
  callbacks. LoginPanel.java and LobbyPanel.java pass their shared settings.
- LobbyPanel.java adds Change theme at home and a focused two-choice picker with
  selected styling, Back and automatic saving through its existing worker.
- RenderBattlefieldLoop.java authors 400 frames over sixteen seconds at 25 fps.
  Isolated red fabric ripples with a pinned root; two lattice rotors turn; grass
  clumps sway at roots, cloud banks drift and seeds travel. Only the isolated cloth
  deforms; poles, towers, debris and landscape stay on the fixed background plate.
- assets/battlefield.png preserves the user's original. battlefield-animation/
  contains clean-plate.png, flag.png and grass.png. assets/menu-battlefield.gif is
  the native Swing loop; artifacts/menu-battlefield.mp4 is the preview.
- ClientSettingsTest covers persistence and old/unknown/null fallback. LobbyMenuTest
  covers theme navigation, selected states, saving and no network traffic.
  ValleyMenuPanelTest verifies both actual GIF assets, timing/looping and switching
  the painted scene back and forth with shared preferences.

## Artwork prompts

Used the built-in image-generation tool, with the user's battlefield as reference:

1. Clean plate: remove red cloth but keep all poles/crossbars, remove only windmill
   rotating sails while retaining towers/roofs, shorten tall foreground grass to a
   quiet ground layer. Preserve composition, flowers, helmet, debris, landscape,
   sunset, clouds and painterly style.
2. Transparent flag: isolate the large weathered rust-red torn fabric, with holes,
   ragged streamers blowing right and left attachment edge; warm sunset highlights,
   same painterly style. No poles, ropes, sky, ground or text.
3. Transparent grass atlas: three isolated long meadow-grass/daisy clumps arranged
   horizontally, upright roots, warm golden right-hand light and the same painterly
   style. No dirt, rocks, sky or background.

Existing transparent valley cloud artwork is reused. The loop is authored locally;
no paid video generation, new runtime libraries or account changes.

## Reproduction

    java -Djava.awt.headless=true tools/RenderBattlefieldLoop.java /tmp/grid-duel-field-loop
    ffmpeg -framerate 25 -i /tmp/grid-duel-field-loop/%03d.png -filter_complex '[0:v]split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle' -loop 0 -y src/main/resources/assets/menu-battlefield.gif
    ffmpeg -framerate 25 -i /tmp/grid-duel-field-loop/%03d.png -c:v libx264 -crf 19 -pix_fmt yuv420p -movflags +faststart -an -y artifacts/menu-battlefield.mp4

## Validation

- Focused menu/settings tests: seven tests passed.
- Full `mvn test`: 313 tests, zero failures/errors/skips, BUILD SUCCESS.
- Independent read-only review: PASS, no actionable defects. Independent
  `mvn clean verify`: 313 tests, zero failures/errors/skips; shaded JAR built.
- ffprobe verified 1280x720, 400 frames, encoded 25 fps, sixteen seconds. Tests
  verify every frame delay and infinite looping for both bundled scenes.
- Inspected authored frames at 0 and 4 seconds and rendered home/theme-picker
  previews at 1440x820. Independent review inspected decoded GIF frames too.
- Encoded frame rate is not a promise of sustained full-window playback; user
  visual check remains useful. Login/menu theme preference is shared and saved;
  no actual user preference was changed by tests.
