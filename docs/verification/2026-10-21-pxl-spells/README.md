# claude-pxl Spell and back artwork — 2026-10-21

Scope: original pxl designs for all ten neutral Spells and the shared card back, integrated
into the actual game renderer. The 35 Unit figures, nation colours, crests, card names,
effects, points and rules are unchanged. Source and regeneration: `art/cards/pxl/README.md`.

`spells.px.js → export-pxl-cards.mjs → PxlCardArt → PixelSpellArt / CardFace → FxKit / TableEffects`.
Names and captions come from `CardDef`; the renderer validates the exact effect, target and
count before choosing an illustration. No runtime Node, pxl, PNG loading or new framework.

## Second pass (this revision)

Fixed from the first pxl pass (`spells-before.png`):

- The second caption line (y 109–113) overlapped the gold corner ornaments (y 112–114), so
  "POINTS" read as "BOINTS" and "RANDOM" as "BANDOM". Captions now sit at y 96 and 103.
- The repeated "NEUTRAL SPELL" line was removed; the art window grew from 60 to 73 px tall.
  Spells stay distinct from Units through the dark frame.
- All ten illustrations were redrawn with outlined, shaded parts and one visual language:
  closed padlock = disable (Disable Tags banner, Disable Points coin), open padlock = Clear
  Disable, split great helm = Destroy Unit, burning card = Random Discard, hooked card =
  Random Steal, cards rising from the deck = Draw Two, turning cycle = Exchange, lens over the
  top three cards = Reconnaissance, hooded watcher over three revealed cards = Spy.
- The back keeps the same frame, with a lattice panel and a larger crossed-swords medallion.

Environment: macOS 27.0 arm64 (Apple M2, 8 GB); Node 26.10.0; Maven 3.9.15 with Homebrew
OpenJDK 25.0.2, Java 17 target; previews with the packaged OpenJFX. Builds ran in an isolated
copy outside iCloud (iCloud duplicates class files inside the repo `target/`).

| Check/input | Expected | Actual | Result |
| --- | --- | --- | --- |
| pxl build of ten Spells and back | Eleven 80×120 sprites plus contact sheet | All eleven built | PASS |
| `node tools/export-pxl-cards.mjs --check` | Java data matches pxl source; size, flat alpha, ≤16 colours | All eleven match | PASS |
| `VerifyPxlArt` against pxl PNGs | Identical native pixels | 105,600 of 105,600 pixels match | PASS |
| `mvn -o test` | No failures, errors or skips | 155 tests, 0 failures, 0 errors, 0 skipped; 26.7 s | PASS |
| `mvn -o clean verify` | Fresh compile, all tests, shaded JAR | 155 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 2:11 min | PASS |
| `git diff --check` | No whitespace errors | None | PASS |

The face tests cover all 45 catalog cards (flat palette ≤16 including transparency, exact
3× nearest-neighbour scaling) and reject altered Spell effects; the back test checks its
opaque body, ≤15 opaque colours, scaling and cache reuse.

Visual evidence from `tools/RenderPixelCard.java`, which uses the production card and JavaFX
table renderers:

- `spells-preview.png`: all ten Spells and the back. Titles and both caption lines fit,
  including Reconnaissance; no caption touches an ornament.
- `spells-before.png`: the first pxl pass, for comparison.
- `match-table.png`: deterministic legal sample match on the production table; Spells in hand
  beside Units, the new back on both deck piles and the opponent hand. It is not a network
  capture and does not log in or change accounts.
- `card-back-rendered.png`, `poland-preview.png`: the back at 4×, and the unchanged Units.

PNGs are verification artifacts, not runtime input.
