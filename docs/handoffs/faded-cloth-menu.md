# Faded cloth menu detail

Follow-up: the user requested removal of the small linen strip. Its render layer,
mast occlusion pass and unused sprite were removed; the weathered pole remains.
The notes below record the earlier artwork generation, before that removal.
The follow-up board enlargement reclaims 24 pixels above the hand without changing
card-face, rail or deck sizes. Updated layout coverage passes at all supported
sizes. Native implementation and independent review suites each pass 504 tests.

Removed the remaining three small animated flag cloths; the large red flag stays.
Added a short muted linen strip at the nearby left pole. Its left edge is anchored,
its loose end ripples, and the main mast occludes it. Weathered only the small
pole's wood and binding; surrounding scene pixels are retained. Existing flower
coordinates, amplitudes and phase timing remain unchanged from the flower update.

Assets: `src/main/resources/assets/battlefield-animation/faded-cloth.png` (transparent
generated sprite, trimmed canvas); `battlefield-animation/clean-plate.png` (localized
pole edit); regenerated `menu-battlefield.gif` and `battlefield.png`.

Built-in image generation was used, with these final prompts:

## Pole edit prompt

Use case: precise-object-edit. Edit target: supplied game background clean plate.
Change ONLY the small bare wooden pole on the left, immediately left of the tall
main mast (small pole runs from about x165 y285 down-left to x99 y480 in the
1672x941 reference). Weather this SMALL pole like wood left outdoors for one year:
slightly grey-brown dry grain, a few fine splits, worn tip and faded old binding.
Keep its exact silhouette, lean, dimensions and location. No cloth yet; no flags,
no added objects. Preserve ALL other scenery, sky, lighting, flowers, main mast,
weapons, background and composition unchanged. Same wide aspect ratio, full
canvas, no crop. Painterly textured pixel-art game illustration matching original,
not photoreal.

Only the pole silhouette and binding were composited from the generated result.

## Cloth sprite prompt

Use case: historical-scene. Asset type: isolated game sprite, painterly textured
pixel-art medieval European battlefield illustration. A SINGLE short narrow strip
of old faded cream / grey-beige linen, tied into a tiny tight knot at the far LEFT
edge and trailing horizontally RIGHT in a mild breeze, gently sagging. Long-to-short
proportion about 4:1. Frayed irregular tapering free end split into two short fine
threads, restrained old folds and a few worn holes, weathered after a year outdoors.
Subtle warm afternoon rim lighting, muted brown shadow texture, crisp painted detail
matching a vintage storybook battlefield background. No emblem, no red, no
flag/banner shape, no lettering, NO POLE or other objects. Exactly one ribbon,
fill the canvas horizontally with minimal padding; left knot centered vertically.
Actual transparent background, no ground, no shadow outside fabric.

## Validation

Inspected composited frames 000 and 100: anchored knot, moving free end, fixed mast
occlusion and no small flag cloths. GIF: 1280×720, 400 frames, 16 seconds; existing
tests check 40 ms delays and indefinite looping. Native `mvn test`: 504 passed,
zero failures/errors/skips. Independent read-only review and native suite: 504
passed, no blocking findings.
