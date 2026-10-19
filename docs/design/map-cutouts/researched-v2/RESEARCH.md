# Research and boundary decisions

Prepared 19 October 2026 for Deck Duel. This replaces the unsupported silhouettes of draft v1 with a reproducible geographic construction. **The alignment is exact within this digital model; the historical borders are generalized reconstructions, not exact surveyed frontiers.** Increasing PNG dimensions does not increase the source boundary precision.

## Dated geographic base

The four outer realm geometries in `sources/cliopatria-1444.geojson` are extracted, without selecting aggregate relationship geometries, from Seshat's Cliopatria dataset. All four records have `FromYear=1440`, `ToYear=1449`, covering 1444: Kingdom of Poland, Kingdom of Bohemia, Kingdom of Hungary, and House of Habsburg. The last is a dynastic territory collection, not a modern Austrian national boundary.

- Dataset: https://github.com/Seshat-Global-History-Databank/cliopatria
- Methods: Bennett et al. (2025), *Cliopatria – A geospatial database of world-wide political entities from 3400BCE to 2024CE*, Scientific Data 12, 247. https://doi.org/10.1038/s41597-025-04516-9 ; open article https://pmc.ncbi.nlm.nih.gov/articles/PMC11822181/
- Attribution: James S. Bennett, Erin Mutch, Andrew Tollefson, Ed Chalstrey, Majid Benam, Enrico Cioni, Jenny Reddish, Jakob Zsambok, Jill Levine, C. Justin Cook, Daniel Hoyer, Peter Turchin / Seshat Global History Databank. CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . Changes here: selection, projection, regional subdivision, land clipping, overlap removal, rasterization and artwork compositing. Original license is included.
- Downloaded repository ZIP member: `cliopatria_polities_only_v021.geojson`. This is a decadal representation, not a day-specific reconstruction before or after the Battle of Varna.

Do not treat a download or an academic citation as proof of every vertex. Cliopatria explicitly acknowledges competing reconstructions and border uncertainty. Its generalized segments remain visible in `boundaries.svg`; they have not been disguised by invented coastline-like jitter.

## Scope of the seven game layers

| Layer | Outer source and grouping | Remaining uncertainty |
| --- | --- | --- |
| Poland | Complete selected 1440–1449 Kingdom of Poland polygon; no union with Lithuania | Coarse source vertices and its treatment of dependent territories are inherited; this is not a reconstruction of each local fief |
| Bohemia | Selected Bohemian realm polygon, including its crown-land extent | Generalized crown-land outline, not the present-day Czech Republic |
| Austria | Hand-reconstructed Upper/Lower Austrian regional cut intersected with the Habsburg parent geometry | Internal boundaries are approximate; Tyrol, Carinthia and Carniola are not silently included as Austria |
| Styria | Hand-reconstructed ducal regional cut intersected with the same Habsburg parent | Internal boundaries and the Cilli/Celje area require specialist refinement; not modern Austrian state borders |
| Croatia | Hand-reconstructed Croatia/Slavonia game grouping inside the Hungarian parent | Combines distinct historical regions; southern/frontier and island detail is generalized and not a claim about every lordship |
| Transylvania | Hand-reconstructed regional cut including the broader geographic Transylvanian grouping inside the Hungarian parent | Includes areas with distinct voivodal, Saxon and Székely institutions; does not assert an independent country in 1444 |
| Hungary | The Hungarian parent minus the two separately exported game regions | This residual layer is not the entire historical Hungarian realm by itself |

The four regional cuts are **our reconstruction**, saved transparently in `sources/regional-cuts.geojson`. They are not misrepresented as independent 1444 GIS records. The dataset's separate Styria entry covers about 277 km², far too little to use as the ducal extent, so that record was rejected. The older Ourednik year-1400 dataset and its combined Poland–Lithuania shape were also rejected for this task.

## Regional cross-checks

- Czech diplomatic historical outline identifies Bohemia, Moravia, Silesian principalities and Lusatia among the Bohemian Crownlands: https://mzv.gov.cz/pretoria/en/information_about_the_czech_republic/history/index.html . Used to avoid substituting modern Czech borders.
- Schönbrunn's *World of the Habsburgs* explains the division into Danube Austria, Inner Austria (Styria, Carinthia, Carniola), and Tyrol/Swabian lands: https://www.habsburger.net/en/chapter/fraternal-strife-and-territorial-partitioning . This establishes the need for regional subdivision; it does not supply our individual cut vertices.
- Hungarian historical synthesis, edited by József Szentpéteri, discusses Transylvania's voivodal government and the separately privileged Saxon and Székely administrations: https://arpad.abtk.hu/images/munkatarsak/psalmus_i_1_4_fejezet.pdf , printed pp. 144–147, 242, 363. Used for the distinction between game grouping and historical sovereignty.
- Hungarian geographical institute's *Hungary in Maps*, chapter on territorial development, provides regional map comparisons and explains changes in the Hungarian state: https://hungarian-geography.hu/inmaps/pdf/Hungary-in-Maps_21.pdf . Its sequence is not an exact 1444 border survey.
- Polish government educational resource distinguishes Poland from Lithuania and documents the later 1466 incorporation of Pomerelia: https://zpe.gov.pl/a/the-new-place-of-poland-and-lithuania-in-europe/DU9yhEqkS and https://zpe.gov.pl/a/lesson-plan-english/DUQJCuQZd . Used to reject a modern Polish silhouette and a combined Commonwealth layer.
- Croatian national encyclopedia describes Venice's fifteenth-century acquisition of Dalmatia: https://www.enciklopedija.hr/clanak/dalmacija . Do not treat the whole modern Croatian coastline as Croatian territory in 1444. The available coarse parent geometry cannot resolve all coastal holdings.
- R. Ratkus's illustrated Europe 1444 map was inspected as a secondary visual comparison, not used as exported pixels or as a traced outline: https://commons.wikimedia.org/wiki/File:Map_Of_Europe_In_1444.jpg . The attribution metadata there is inconsistent; its claimed public-domain upload was not relied on to license these assets.

## Terrain and coastline

The physical reference is Natural Earth I with shaded relief and water, version 3.2.0, 1:50m. It was reprojected before being given to the built-in image generation tool for a painted terrain interpretation. Modern Natural Earth 1:10m land geometry supplies physical coast clipping. Neither modern terrain nor a generated repaint proves the location of a fifteenth-century river channel, settlement or forest.

- Relief: https://www.naturalearthdata.com/downloads/50m-raster-data/50m-natural-earth-1/
- Land data: https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_10m_land.geojson
- Natural Earth terms: https://www.naturalearthdata.com/about/terms-of-use/ (public domain).
- Generation used the built-in image generation tool, with `relief-reference.png` as its input. Initial square experiment: `generation-prompt.txt` / `terrain-illustrated.png`. Final wide plate: `context-generation-prompt.txt` / `terrain-context.png`, repainted from `context-reference.png` with the square experiment as a style reference; exported dimensions do not imply native artwork at that resolution. Small painted terrain features remain illustrative.

## Digital guarantees and exports

All seven layers use the same 3840×3840 canvas and spherical Lambert azimuthal equal-area projection, centred at 20° E / 49° N. There is one shared pixel owner mask, with no pairwise overlap or rescaling of individual countries. Full-canvas layers can be stacked at (0,0). Cropped layers retain that identical scale and have exact placement offsets in `manifest.json`. Unselected neighbouring lands are intentionally absent from the transparent cutouts; the seven game regions do not partition the whole of Central Europe.

`tools/ExportAtlasLayers.java` constructs the regions using geometric intersections/differences, dissolves land tile seams before projection, and exports the country layers from one continuous terrain plate. Natural Earth land tiles were dissolved at 1e-7 degree precision, projected with the same LAEA equations, and closed across sub-pixel seam gaps with a 20-metre buffer/unbuffer operation. The prepared projected land polygons are included in `sources/land-projected.tsv`. The final SVG contours are traced from the disjoint pixel-owner grid, removing numerical zero-width seams and matching the exported PNG edges exactly. `tools/VerifyAtlasLayers.py` independently checks the saved PNG files, their union, binary transparency, colors and cropped offsets. Border lines and names are added only to `preview.png`; reusable layers remain unlabelled. `verification.txt` records the digital checks. Runtime uses the same exported SVG geometry for clicks, hover and selection.

Rebuild after downloading the Natural Earth TIFF:

```sh
java -Xmx2g -Djava.awt.headless=true tools/ExportAtlasLayers.java docs/design/map-cutouts/researched-v2 /path/to/NE1_50M_SR_W.tif docs/design/map-cutouts/researched-v2/terrain-context.png --wide
```

The honest status is **researched, source-based circa-1444 game reconstruction with exact digital layer alignment**. It is not a claim of exact historical boundaries.

The wide background plate occupies [-1600, -400, 8192, 4608] in the same pixel coordinate plane as the 3840×3840 cutouts. The Atlas draws this plate and the clickable boundaries through one camera transform; it does not stretch a second backdrop behind a differently scaled map.
