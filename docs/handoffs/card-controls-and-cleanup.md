# Click-or-slide controls, cleanup and replacement music

Request: allow either a click or upward drag to choose a card, offer a setting,
trim obsolete screenshots/preview files/dead code, and replace Golden fields music
with /Users/chiseled/Downloads/Golden .mp3. Preserve raised targeting, accepted-only
play flights, rules, privacy and all active runtime artwork.

ClientSettings persists clickToSelectCards (default true, including old files).
Lobby Settings offers Click or slide up and Slide up only, with exclusive selection
and asynchronous save. GamePanel passes the live setting to the common movement
and skill gesture binding. A left click released inside the card with at most6px
pointer travel selects in both-gesture mode; the38px upward threshold works in
both modes. Wrong buttons, pending requests, invalid phase, masked draws and
accepted flight blockers retain existing guards. Canceled replacement gestures
refresh the previous selection when their return finishes.

Removed82 individually inventoried, non-symlink obsolete files totaling71,298,354
bytes:62 historical screenshots,3 MP4 previews,12 generated flower exports and5
unreferenced resource images (menu-background, board-empty, both master pieces,
synthetic grass). Verified no source/config/tool/build reads the exports or those
resource paths. Retained current GIFs/stills, cards, student fallback pieces,
compositor plates/layers, generator tools and test-required legacy rules. Removed
unused CardPanel.hovered state and debug-only flower-export code; renamed obsolete
CardInspectorTest to MatchPresentationTest. Ignore future generated QA exports.
Historical text reports remain with retirement notes. No personal Downloads files
or user-provided originals removed. Runtime Golden fields WAV now comes from
Golden .mp3, converted to32kHz stereo16-bit PCM for built-in Java Sound playback.

Full mvn test:326 tests,0failures/errors/skips. Added settings default/round-trip,
menu choices/save/no-network/layout checks, and real GamePanel click/drag submission
coverage in both modes. Existing slide-only controller tests remain. Asset decode
check validates replacement audio. Independent review/clean verify pending.
