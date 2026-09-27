# UI Design Spec — M9 "Retro-Pop" Redesign

**Reference direction:** fireship.dev — *retro-pop neo-brutalism on a dark
theme*: near-black canvas, 70s vintage palette (cream / amber / coral / sky),
chunky uppercase display type, sticker-style cards, pill buttons with hard
offset shadows, playful motifs (starbursts, lightning bolts).

**Status:** design input for milestone M9 (agent brief §17). This document is
the design contract; M9 implements it in `onitama.client.ui` only. The
current M4 look is the functional baseline being replaced.

---

## 1. Non-negotiable constraints (from agent brief §17)

1. **Scope: `onitama.client.ui` only.** No changes to `core`, `net`, `server`,
   `db`, `replay`, `client.state`, or the client root classes. If the design
   seems to require a model/protocol change, stop and ask.
2. **No new Maven dependencies.** Swing + JDK only. Custom `Graphics2D`
   painting is expected. Bundling a font **file** under
   `src/main/resources/fonts/` is an asset, not a dependency — allowed under
   the rules below.
3. **Behavior invariants that must survive:** own-side board rendering;
   opponent card patterns pre-rotated to the player's perspective; card +
   piece click flow (either order) with ESC to clear; auto-pass; rematch /
   resign / reconnect / error-dialog flows; the EDT / reader-thread
   separation.
4. **Acceptance:** `mvn clean verify` green; all five screens fully usable;
   IT01/IT02/LT01 pass unchanged; DESIGN_DECISIONS entry added; before/after
   screenshots for the report.

---

## 2. Design tokens

### 2.1 Palette (replaces `Theme` constants 1:1)

| Token | Hex | Replaces | Usage |
| --- | --- | --- | --- |
| `BG` | `#0D0D0D` | `BACKGROUND #2B2B2B` | window / panel canvas |
| `SURFACE` | `#1A1A1A` | `BACKGROUND.brighter()` | cards-in-cards, list rows, table |
| `CREAM` | `#F5EFE0` | `FOREGROUND #EEEEEE` | primary text, card faces, heading boxes |
| `INK` | `#141414` | *(new)* | text/icon color on cream & amber, outlines |
| `AMBER` | `#E8B23F` | `ACCENT #C0392B`, `SELECTION #F2C037` | primary accent: pill buttons, selected card frame, turn indicator, last-move overlay tint |
| `SKY` | `#5BA8D9` | `BLUE_PIECE #2C5F8A` | Blue player pieces & Blue labels |
| `CORAL` | `#E2696B` | `RED_PIECE #B03A2E`, `ERROR #E74C3C` | Red player pieces, errors, Resign/danger |
| `TEAL` | `#4E9E8A` | `TARGET_DOT #3E8E5A` | legal-move dots & capture rings |
| `TILE_LIGHT` | `#F0E6D2` | `BOARD_LIGHT` (keep) | board light squares |
| `TILE_DARK` | `#221E1A` | `BOARD_DARK #D8C4A0` | board dark squares (warm charcoal — the checker flips to dark-mode) |
| `OUTLINE` | `#000000` | *(new)* | 2 px sticker outlines on tiles, cards, buttons |
| `SHADOW` | `#000000` (α≈160) | *(new)* | hard offset shadows (no blur) |

### 2.2 Typography

* **Display font** (titles, headings, buttons, room code): a chunky
  uppercase display face — **Archivo Black** (SIL OFL) bundled at
  `src/main/resources/fonts/ArchivoBlack-Regular.ttf` together with its
  `OFL.txt` license file. Loaded once via `Font.createFont(TRUETYPE_FONT, …)`
  in `Theme`, exposed as `Theme.FONT_DISPLAY` (derived sizes 28/20/14).
* **Body font**: keep the JDK logical font (`Font.SANS_SERIF`) — readable,
  zero assets. Only the *display* face is bundled.
* All headings render UPPERCASE with letter-spacing simulated by inserting
  thin spaces where needed (no custom `LabelView` machinery).

### 2.3 Shape language

* **Sticker**: cream or amber fill, 2 px `OUTLINE` border, **hard offset
  shadow**: a solid `SHADOW` rectangle offset (4, 4) behind the component —
  never a soft/blur shadow.
* **Pill**: fully rounded (corner radius = height/2) button, 2 px outline,
  hard shadow; pressed state removes the offset (button "sinks").
* **Heading box**: cream rectangle, ink text, 2 px outline, hard shadow —
  used for screen titles ("YOUR TURN", "GAME OVER").
* **Corner radius**: 18 px panels, 12 px cards, pill = height/2. No gradients
  anywhere; flat fills only.

---

## 3. Reusable UI kit (new, inside `client.ui`)

One small helper class, `UiKit`, so every screen reuses the same primitives
(viva-explainable, ~100 lines, pure Swing):

| Helper | Purpose |
| --- | --- |
| `stickerBorder(radius)` | `Border` painting outline + hard offset shadow around any JComponent |
| `pillButton(text, variant)` | amber-filled / cream-outlined / coral-outlined JButton with press-sink effect |
| `headingBox(text)` | cream box + ink display-font label (turn indicator, screen titles) |
| `stickerCard(...)` | shared base for movement-card faces |
| `loadDisplayFont()` | loads the bundled TTF once, caches derived sizes |

`Theme` keeps its role as the single constants file; the tokens in §2.1 are
its new values.

---

## 4. Screen specs

### 4.1 Login (`LoginPanel`)

* Near-black background; centered **cream sticker panel** (radius 18, hard
  shadow) holding the whole form — the "membership card".
* Title `ONITAMA ONLINE` in display font, ink on the cream card, with a small
  amber starburst motif drawn beside it (Graphics2D path, no image assets).
* Fields: cream background, ink text, 2 px outline, radius 12; labels in
  small uppercase cream text *outside* the card? No — labels sit on the card
  in ink, small caps.
* Buttons row: **REGISTER** = amber pill (primary), **LOGIN** = cream-outlined
  pill (secondary). Inline errors in coral below.

### 4.2 Lobby (`LobbyPanel`)

* Header strip: player name + Elo + W/L as a **cream badge** (sticker) at
  top-left.
* **Create match**: amber pill. The returned room code renders as a
  **ticket**: cream sticker, dashed inner border, room code in display font
  with letter spacing, caption "SHARE WITH YOUR OPPONENT".
* **Join**: code input styled like login fields + amber pill `JOIN`;
  open-match list = dark `SURFACE` rows with cream text, host name in amber;
  selected/hover row gets an amber left border.
* Bottom actions (Leaderboard / Replay viewer): cream-outlined pills.

### 4.3 Game (`GamePanel`) — the centerpiece

* **Opponent strip** (top): opponent name in cream; their two card stickers
  rendered pre-rotated to *my* perspective (unchanged logic), slightly
  desaturated/dimmed vs. my cards to signal "not yours".
* **Board** (center, `BoardPanel`): checker of `TILE_LIGHT`/`TILE_DARK`,
  every tile gets a 2 px ink outline; the whole board sits on one cream
  sticker frame with hard shadow.
  * Pieces: flat coral/sky discs with ink outline; Masters carry a small
    amber starburst mark (replaces the white ring + letter).
  * Selection: amber 3 px ring on the selected piece's tile.
  * Legal moves: teal filled dots on empty tiles, teal double-ring on
    capturable pieces.
  * Last move: amber translucent overlay on from/to tiles (α≈36).
* **My hand** (bottom): two large sticker cards; the selected card lifts —
  amber outline + hard shadow + 4 px upward offset. Transit card shown small
  beside the hand with a "TRANSIT" caption chip.
* **Turn indicator**: `headingBox` — cream box, ink display text:
  `YOUR TURN` / `WAITING…` / `GAME OVER`. Must-pass shows `NO MOVES —
  PASSING…`.
* **Move history + captures** (east): dark `SURFACE` panel; history lines in
  cream mono-spaced digits; capture trays render *mini piece discs* (12 px)
  instead of text labels.
* **Resign**: coral-outlined pill, top-right of the south panel. Rematch
  button appears in the game-over dialog as the amber primary pill.

### 4.4 Leaderboard (`LeaderboardPanel`)

* Title as `headingBox`. Table on `SURFACE`: cream text, amber header row
  (ink text), row height 28, top-3 ranks get a small medal chip (amber /
  silver-grey / bronze discs drawn in `UiKit`).
* Back to lobby: cream-outlined pill.

### 4.5 Replay viewer (`ReplayViewer`)

* Same board rendering as §4.3, fixed to Blue's side (unchanged rule).
* Transport: round pill buttons `◀` / `▶` (amber when enabled, `SURFACE`
  when not), move counter as a cream chip `MOVE 3 / 27`.
* Open/close: amber pill + coral-outlined pill.

### 4.6 Frame & menus (`ClientFrame`)

* Window title stays; content pane background `BG`.
* Menu bar: `BG` with cream text, amber highlight on hover/open; dialogs
  (How-to-play, About, errors) use cream sticker panels with ink text —
  implement via `UIManager` defaults + a small dialog-border helper, no new
  components outside `ui`.

---

## 5. Motifs (drawn, not imported)

Two tiny `Graphics2D` painters in `UiKit`, reused as flavor:

* `starburst(g, x, y, r, points)` — amber 8-point burst: game title, Master
  pieces, game-over dialog.
* `lightning(g, ...)` — capture flash on the board for ~200 ms after a
  capture (replaces nothing; purely decorative, capped at 200 ms per the
  brief's animation rule).

**Hand-designed assets.** The team additionally designs real artwork
(board, pieces, card faces, icon — manually or in a third-party tool) per
the contract in **`docs/ASSET_CHECKLIST.md`**. M9 ships the code-drawn
versions as the baseline; an `AssetStore` loader then replaces each drawn
element with the matching file from `src/main/resources/assets/` **only
when that file exists**, falling back silently otherwise. Assets are files
in the jar, not dependencies — the two-dependency rule is unaffected.

---

## 6. Implementation checklist for M9

1. Update `Theme` tokens to §2.1; add `FONT_DISPLAY` + font loading.
2. Add `UiKit` (§3); bundle Archivo Black TTF + `OFL.txt` under
   `src/main/resources/fonts/`.
3. Restyle panels in this order: Login → Lobby → Game → Leaderboard →
   Replay → menus (each keeps its current layout manager and listeners).
4. Repaint `BoardPanel` tiles/pieces/marks and `CardPanel` sticker faces.
5. Add the `AssetStore` loader (per-file override with drawn fallback) so
   hand-designed assets from `docs/ASSET_CHECKLIST.md` drop in without code
   changes.
6. Add the capture lightning flash (≤200 ms, EDT-timer).
7. Verify acceptance criteria (§1.4), add DESIGN_DECISIONS entry, take
   before/after screenshots for the report.

**Out of scope:** changing any listener, message, or model field; new
dependencies; touching anything outside `client.ui`.
