# Minimal original battlefield update

## Final request

Restore the original background. Remove only four small flags behind the large
foreground flag, and lightly weather its large wooden mast as after one year.
Keep original landscape, lighting, grass, armor, flowers, resolution and animation.

## Changes

- `battlefield-animation/clean-plate.png`: four narrow background masts removed;
  subtle weathering on the main mast/crossbar/ropes. Built-in image-generation edit.
- `RenderBattlefieldLoop.java`: remove only the four corresponding cloth layers
  at (27,230), (77,270), (264,362), (346,392). Original 1280x720 output, all other
  flags, flower sway, windmill rotors, clouds, seeds and wisps remain.
- `menu-battlefield.gif`: regenerated original sixteen-second, 400-frame, 25 fps
  infinite loop. `battlefield.png` is its matching first-frame fallback.

## Image-edit prompt

Use the original 1672x941 clean animation plate. Make only two localized edits:
remove exactly four narrow background masts on the left hillside (far-left pole
near x35,y185..400; small pole x55,y325..415; and the pair to the right of the main
mast near x268,y343..490 and x350,y370..517), reconstructing matching original
sky/meadow. Retain the larger diagonal small pole at x143,y313 and all other masts.
Lightly age only the huge main mast and crossbar: subtle sun-bleached gray-brown
wood, tiny cracks and slightly worn rope, a small hint of moss at its base, still
sturdy. Preserve the exact x218,y48 attachment and geometry. Keep all original
sunset lighting, clouds, palette, meadow, flowers, original armor patina and terrain.
No new objects, global repaint, sharpening, upscaling, flag fabric or windmill rotors.

## Reproduction

Use the original render and ffmpeg commands in `golden-fields-menu-theme.md`.
The MP4 preview is exported to ignored `artifacts/menu-battlefield.mp4`.
