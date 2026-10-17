# Deck Duel menu title

User-selected product name: Deck Duel. Subtitle: A Card-Based Strategy Board Game.
Placed on the blue upper sky, above the central clouds between the flag and menu.
Only home paints it; the authored background and its animation remain unchanged.

MenuTitle paints native vector lettering with Cinzel Decorative Bold,
a pale champagne-gold gradient fill, fine outline/shadow, overlapping-card ornament and a restrained
separator. The title is now smaller with tighter tracking to sit more quietly in
the landscape. At the user's request, the subtitle is restored to its original
15-pixel serif, .06 tracking, ivory-white fill and one-pixel offset shadow; the
enlarged outlined variant has been removed.
Scales from a 560x168 logical area
with center x=.55 window width, top=.025 window height; no click overlay or timer.
Outfit action fonts and navigation remain unchanged. Home accessibility exposes
both name and subtitle.

Font provenance: Google Fonts repository, ofl/cinzeldecorative/CinzelDecorative-Bold.ttf.
Source: https://github.com/google/fonts/tree/main/ofl/cinzeldecorative
Bundled font: src/main/resources/fonts/cinzel-decorative-bold.ttf.
License: src/main/resources/fonts/CinzelDecorative-OFL.txt (SIL Open Font License).
No image generation used; all ornaments and lettering are editable Java2D shapes.

Validation: full native mvn test passed 563 tests with no failures/errors/skips.
MenuTitleTest checks actual bundled typeface and sky-region drawing at three
supported sizes. Inspected home and Play previews for placement/home-only display.
Independent read-only review and its full native suite passed 563 tests. Final
mvn clean verify passed 563 tests, zero failures/errors/skips. Client reopened
against the unchanged local server. Final title preview: /tmp/grid-duel-menu-home.png.
