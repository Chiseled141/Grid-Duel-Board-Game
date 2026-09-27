# Asset Design Checklist — for the M9 visual redesign

**Who this is for:** you (the designer) — working manually or in any
third-party tool (Figma, Illustrator, Procreate, Aseprite, Krita, Photoshop —
anything that exports PNG). This checklist lists **every asset the game
needs**, with exact file names, sizes, and style rules. When your exports
follow this list, integrating them into the game is a drop-in operation.

**How integration works (important):**

* Assets live in `src/main/resources/assets/` inside the project and are
  loaded by an `AssetStore` class at runtime. Assets are plain files in the
  jar — **not** Maven dependencies, so the two-dependency rule still holds.
* M9 first implements the **code-drawn baseline** (current spec in
  `docs/UI_DESIGN.md`). Every asset below is then a **drop-in replacement**
  for its drawn counterpart: if a file exists in `assets/`, the game uses
  your art; if it is missing, the game falls back to the drawn version.
  That means you can deliver assets **in any order, any number at a time** —
  the game never breaks.
* Delivery: put the files in `src/main/resources/assets/` with the exact
  names below (or hand me a zip / Figma export and I'll wire them in), then
  say "integrate assets" — I rebuild, verify all screens, and take
  screenshots for the report.

---

## 1. Global art rules (apply to every asset)

| Rule | Value |
| --- | --- |
| Format | **PNG-24 with transparency** (no JPEG, no white background baked in) |
| Color space | sRGB |
| Resolution | provide the **2× size** listed per asset (the game scales down — stays crisp on HiDPI) |
| Style | retro-pop neo-brutalism (see `docs/UI_DESIGN.md`): flat fills, thick black outlines, 70s palette |
| Palette | bg `#0D0D0D` · cream `#F5EFE0` · ink `#141414` · amber `#E8B23F` · sky `#5BA8D9` · coral `#E2696B` · teal `#4E9E8A` · tile cream `#F0E6D2` · tile charcoal `#221E1A` |
| Outlines | black, 4–6 px at 2× (reads as 2–3 px in game) |
| Naming | kebab-case, **exactly** the names in the tables below (the loader looks these up literally) |
| Weight | keep each file under ~500 KB; whole set under ~5 MB |

> **Card patterns are NOT part of your artwork** — see §3 for why, and what
> to design instead. This is the one rule that prevents mistakes.

---

## 2. REQUIRED assets (the game's core look)

### 2.1 Board

- [ ] **`board-tile-light.png`** — 144×144. Light board square. Flat
      `#F0E6D2` (or your textured take on it), subtle wear/grain welcome.
- [ ] **`board-tile-dark.png`** — 144×144. Dark square, warm charcoal
      `#221E1A`. Must contrast with light tile clearly but gently.
- [ ] **`board-frame.png`** — 816×816. The board's surrounding "sticker"
      frame/backing: cream field with the 5×5 window cut out **fully
      transparent** in the middle (window area = 720×720 centered, i.e.
      48 px cream border all around). The code draws tiles into the window.

*Alternative:* instead of the three files above you may deliver **`board-full.png`**
(720×720, the complete 5×5 checker baked into one image). If you do, still
deliver `board-frame.png`, and tell me which variant you chose.

### 2.2 Pieces (transparent background, disc-shaped, thick outline)

- [ ] **`piece-blue-student.png`** — 104×104
- [ ] **`piece-blue-master.png`** — 104×104 — must be clearly distinct from
      the student (crown, hat, stamp mark… your call)
- [ ] **`piece-red-student.png`** — 104×104
- [ ] **`piece-red-master.png`** — 104×104
- [ ] **`piece-blue-master-captured.png`** / **`piece-red-master-captured.png`**
      — 104×104 — *(optional but recommended)* fallen/defeated variant shown
      in the capture tray; if skipped, the normal master art is used.

Sky `#5BA8D9` = Blue, coral `#E2696B` = Red. The game tints nothing — what
you draw is what shows.

### 2.3 Movement cards

**Do not draw the movement patterns.** The 5×5 pattern of X marks is game
data (Appendix A of the brief); the code stamps it onto every card face so a
typo is impossible and the shown pattern always matches the rules engine.
You design the **face template** the pattern gets stamped onto.

- [ ] **`card-face-template.png`** — 190×234. One cream sticker-style card
      face. Layout contract (so the stamped pattern lands correctly):
      - **Safe top area**: first 44 px (2×) of height = card **name** zone —
        decorate freely but keep it ink-friendly (the name is drawn in ink
        over it).
      - **Pattern window**: the remaining area must contain a clean,
        uncluttered zone of exactly 5×5 cells of 30×30 px each (150×150),
        horizontally centered, starting 54 px from the top. Keep this zone
        low-detail — teal X marks and an ink M marker get stamped on top.
      - Bottom 20 px: free for flavor (stamp circle, filigree…).
- [ ] **`card-stamp-blue.png`** — 48×48 — small circular stamp/seal shown in
      a corner of blue-stamp cards.
- [ ] **`card-stamp-red.png`** — 48×48 — red variant.

*(If you want per-card unique artwork for all 16 cards — e.g. a Tiger
illustration per card — deliver `card-art-<id>.png` at 190×234 for each id:
tiger, dragon, frog, rabbit, crab, elephant, goose, rooster, monkey, mantis,
horse, ox, crane, boar, eel, cobra. The pattern stamp still comes from the
engine. Mark these clearly as OPTIONAL — the template alone is enough.)*

### 2.4 App icon

- [ ] **`app-icon.png`** — 256×256, square, transparent margins — used as
      the window/dock icon (scaled to 64/128 by the code).

---

## 3. OPTIONAL assets (nice-to-have; drawn fallbacks exist)

- [ ] **`overlay-select.png`** — 144×144 — amber selection ring/frame for the
      chosen piece's tile.
- [ ] **`overlay-dot.png`** — 144×144 — teal legal-move dot (code centers it).
- [ ] **`overlay-capture.png`** — 144×144 — teal double-ring shown over an
      enemy piece when it is a legal capture target.
- [ ] **`motif-starburst.png`** — 256×256 — amber burst; title + game-over.
- [ ] **`motif-lightning.png`** — 128×128 — capture flash.
- [ ] **`bg-pattern.png`** — 512×512 tileable, very subtle dark texture for
      the app background (must stay ≤ 10 % visible contrast).
- [ ] **`logo-wordmark.png`** — 600×200 — "ONITAMA ONLINE" lettering for the
      login screen (otherwise rendered in the display font).
- [ ] **`banner-lobby.png`** — 900×160 — optional decorative strip for the
      lobby header.

Skip any of these — the code-drawn versions from M9 remain in charge
whenever a file is absent.

---

## 4. Style guardrails for third-party tools

1. Keep the palette within the §1 hex list (± small shade variation). One
   accent color per asset; cream + ink do the talking.
2. Outlines: consistent black weight across all assets — mixed outline
   weights look broken next to each other.
3. Flat fills only: **no gradients, no drop shadows baked into the art** —
   the UI kit draws its own hard offset shadows.
4. Draw at 2× and design in sRGB; check the piece/board contrast by viewing
   your art on both `#0D0D0D` and `#1A1A1A` backgrounds before exporting.
5. Round shapes may exceed their canvas edge slightly — leave ≥ 4 px
   transparent margin so outlines don't clip.

---

## 5. What I do on "integrate assets"

1. Copy your exports into `src/main/resources/assets/` (exact names).
2. Add the `AssetStore` loader (cache, 2× → 1× scaling, per-asset fallback to
   the drawn version, one INFO log per asset found/missing).
3. Rebuild + run all tests (must stay green — assets touch nothing else).
4. Launch both screens of each type, take before/after screenshots, add the
   DESIGN_DECISIONS entry, and update the report skeleton.

## 6. Progress tracker

| Phase | Status |
| --- | --- |
| Board tiles + frame (§2.1) | ☐ designed ☐ exported ☐ integrated |
| Pieces ×4 (+captured ×2) (§2.2) | ☐ designed ☐ exported ☐ integrated |
| Card template + stamps (§2.3) | ☐ designed ☐ exported ☐ integrated |
| App icon (§2.4) | ☐ designed ☐ exported ☐ integrated |
| Optional overlays/motifs (§3) | ☐ designed ☐ exported ☐ integrated |
