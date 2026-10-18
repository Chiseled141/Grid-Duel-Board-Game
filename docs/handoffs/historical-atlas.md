# Historical atlas preview

## Scope

The home menu's Leaderboard entry is replaced by Atlas. Screen.ATLAS is local:
ClientModel.openAtlas sends no network messages. The ranking protocol and its
standalone panel remain available to existing consumers and tests.

AtlasPanel loads the bundled image in a SwingWorker, supports pointer-anchored
1–4× zoom, bounded panning, hover, map picking, seven keyboard-focusable faction
buttons, Reset view and Back/Escape/Backspace. It never changes a match or its
borders. Passive abilities and card catalogs are intentionally unassigned.

## Artwork and historical limits

`assets/atlas/carpathian-1444-reference.png` is a copy of the user's supplied
illustration, not a newly authenticated historical map. The original legend is
cropped from the canvas and replaced by an inspector. AtlasRegion hit polygons
are approximate interaction geometry in native image coordinates, not measured
frontiers. The on-screen footer identifies the reference status.

This preview must not be described as historically finalized. The supplied
artwork includes schematic boundaries and heraldic issues: the Transylvanian
shield and Croatia/Savonia grouping, for example, need replacement in a final
historical edition. The inspector explicitly distinguishes game factions from
sovereign states: Transylvania is a voivodeship inside Hungary, Croatia shares
the Hungarian crown, and Austria/Styria are Habsburg imperial duchies.

Historical reading used for the text (paraphrased):
- https://en.wikipedia.org/wiki/Voivode_of_Transylvania — office within Hungary,
  including the 1441–1446 Hunyadi/Újlaki period.
- https://en.wikipedia.org/wiki/W%C5%82adys%C5%82aw_III_of_Poland — Polish and
  Hungarian crowns and death at Varna in November 1444.
- https://pressto.amu.edu.pl/index.php/bp/article/view/54681 — scholarly study
  of the Battle of Varna, 10 November 1444.
- https://www.aeiou.at/aeiou.encyclop.i/i524193.htm;internal&action=_setlanguage.action?LANGUAGE=en
  — division of the Habsburg lands and the Inner Austrian grouping.
- https://en.wikipedia.org/wiki/Timeline_of_Croatian_history — shared monarchy.
- https://wnhs.uksw.edu.pl/wp-content/uploads/sites/7/2023/01/Konflikt_Eng_Spread-na-strone-www.pdf
  — Bohemian Hussite wars and regional political context.

## Verification

AtlasPanelTest covers offline routing/back, seven geographic picks, excluded
neighbor territory, pointer-centered zoom picking, faction buttons, asset loading
and desktop-size rendering. Optional `-Datlas.preview=/absolute/file.png` saves
an offscreen render for visual inspection. Existing LobbyMenuTest expects Atlas.

Final validation: `mvn clean verify` and subsequent final `mvn test` both passed
637 tests, zero failures/errors/skips. The final UI was packaged with
`mvn -DskipTests package` after those tests. An independent read-only reviewer
confirmed the music-control spacing fix and the actual pan/pick test; no blocking
findings remain. Screenshot: output/atlas/atlas-preview.png. A standalone Atlas
preview window was opened for interactive inspection.

Changed files for this task:
- AtlasPanel.java: local interactive viewport, inspector and navigation.
- AtlasRegion.java: seven reference-map hit areas and historical reading text.
- ClientFrame.java / LobbyPanel.java: replace the home ranking entry with Atlas.
- ClientModel.java / Screen.java: offline Atlas entry and safe return to lobby.
- AtlasPanelTest.java / LobbyMenuTest.java: interactions, assets and menu labels.
- assets/atlas/carpathian-1444-reference.png: supplied reference illustration.
- docs/architecture.md / this handoff: integration and historical limitations.

## Sourced banner artwork

The inspector now uses downloaded Wikimedia banner files instead of cropping
crests from the supplied map. AtlasFlag loads local artwork and catalog metadata
in the existing background worker. A proportional high-resolution flag canvas
preserves each banner's shape, and Source & license shows copyable attribution,
source URL, license and historical context offline. See the packaged
assets/atlas/flags/ATTRIBUTION.md for individual sources and licenses.

Transylvania explicitly uses a Hungarian crown reference, not an invented 1444
flag. Croatia is labeled as a Talovac campaign banner. The original map's baked
symbols are still reference artwork. No passive, match, protocol or DB changes.

Flag update validation: `mvn test` and `mvn clean verify` passed **639 tests**,
zero failures/errors/skips. Final JAR includes all downloaded banners and credits.
The review confirmed metadata, asset dimensions, aspect-ratio rendering and the
source button's explicit width. Snapshot: output/atlas/atlas-flags-preview.png.

Files changed by this update: AtlasPanel.java (sourced banner inspector and
credits), AtlasRegion.java (remove obsolete generated-crest crop coordinates),
AtlasFlag.java (offline image/catalog loader), AtlasFlagTest.java and
AtlasPanelTest.java (asset/attribution/selection checks), assets/atlas/flags/*
(downloaded originals, renders, provenance and catalog), and this handoff.
