# Flower beds and distant village

User request: animate the remaining stationary flowers, add a small distant village,
then remove the recently added distant windmill. Two original mills remain.

The offline compositor selects 460 additional cream/gold petal groups in the
foreground meadow from the original clean plate. Masks exclude the original 47
flower regions, traced grass, major rocks, equipment, fences and the track.
Connected neighboring petal fragments share a root and phase. These extra groups
use gentler motion; the original 47 retain their exact sway formula and strength.
No new flower artwork or density is added. Exact unrotated reconstruction is
asserted, and inventories/cutouts export through --verify. A separate source-pixel
probe verified 10,166 added petal pixels and all 1,617 retained grass pixels.

The village is a transparent generated strip of five cottages, downsampled with
area averaging and composited at source x=1157, baseline y=557, width 102 pixels,
opacity .76. Source clean-plate.png remains unchanged. Removed the third mill's
compositor implementation and unused source sail sprite. Runtime uses only the
bundled GIF and matching still, with no added EDT work or audio.

Village asset: src/main/resources/assets/battlefield-animation/distant-village.png.
Mode: built-in image_gen, transparent background; clean-plate.png used only as a
style/lighting reference.

## Final generation prompt

Use case: historical-scene. Asset type: transparent distant village cutout for an existing game landscape. Reference image: supplied clean-plate landscape, style and lighting reference only. Create ONLY a compact cluster of five humble European medieval cottages with muted warm stone/plaster walls and subdued brown/red pitched roofs. Almost straight-on far landscape view, roofs staggered a little, common ground line. Match the painterly textured pixel-like brushwork of the reference; gentle warm late-afternoon sunlight from the right, hazy low-contrast colors for the far distance. Transparent background and transparent gaps between houses. No sky, no terrain base, no trees, no castle, no towers, no windmill, no people, no flags, no text, no outline, no isometric diorama. Wide low silhouette, intended to appear just 90 pixels wide in the distant countryside.

## Reproduce

```bash
java -Djava.awt.headless=true tools/RenderBattlefieldLoop.java /tmp/grid-duel-flower-village-final artifacts/remaining-flowers/final-details
ffmpeg -framerate 25 -i /tmp/grid-duel-flower-village-final/%03d.png -filter_complex '[0:v]split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle' -loop 0 -y src/main/resources/assets/menu-battlefield.gif
cp /tmp/grid-duel-flower-village-final/000.png src/main/resources/assets/battlefield.png
```

Original accepted assets are retained under artifacts/remaining-flowers/original/.
The animation remains 400 frames / 25 fps / 16 seconds with infinite looping.

## Validation

Native mvn test, independent read-only review with another full native mvn test,
and final mvn clean verify each passed 543 tests with zero failures/errors/skips.
Inspected final frames 000/067 and the source selection overlay. ffprobe confirmed
1280x720, 400 frames and 16.000 seconds; encoded timing/infinite loop assertions pass.
Source reconstruction and grass pixel checks passed. The updated client was
reopened against the unchanged local server. No gameplay/network changes.
