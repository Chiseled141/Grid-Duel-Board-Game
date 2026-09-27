# Art Checklist — what to design for the game

You're making the game's artwork: the board, the pieces, the cards, and the
icon. Design by hand or in any tool you like (Figma, Illustrator, Procreate,
Aseprite, Photoshop…). When you're done, hand the files to me and I put them
into the game.

**Good to know:** the game already has simple built-in graphics for
everything. Your art replaces them file by file — anything you haven't made
yet just keeps using the built-in version. So you can deliver in small
batches, in any order.

---

## The rules (short version)

* **File format:** PNG with a transparent background.
* **Design at double size** — the sizes below already include that, just
  follow them and everything stays sharp.
* **Colors:** stick to this palette:
  | Color | Hex |
  |---|---|
  | Near-black background | `#0D0D0D` |
  | Cream (cards, text) | `#F5EFE0` |
  | Ink (outlines, text on cream) | `#141414` |
  | Amber (highlights, buttons) | `#E8B23F` |
  | Sky (Blue player) | `#5BA8D9` |
  | Coral (Red player) | `#E2696B` |
  | Teal (move hints) | `#4E9E8A` |
  | Board light square | `#F0E6D2` |
  | Board dark square | `#221E1A` |
* **Style:** flat colors, thick black outlines, retro 70s feel. No
  gradients, no soft drop shadows — outlines do the work.
* **File names matter.** Use the exact names below (all lowercase, dashes),
  because the game looks them up literally.

---

## Must-have assets

### 1. Board

- [ ] **`board-tile-light.png`** — 144×144 — one light square.
- [ ] **`board-tile-dark.png`** — 144×144 — one dark square.
- [ ] **`board-frame.png`** — 816×816 — the cream frame around the board,
      with the middle 720×720 completely transparent (that's where the 5×5
      squares go). In short: a cream border 48 px thick around a see-through
      center.

### 2. Pieces (4)

Round discs, transparent background:

- [ ] **`piece-blue-student.png`** — 104×104
- [ ] **`piece-blue-master.png`** — 104×104 — make it clearly fancier than
      the student (crown, hat, mark… your call)
- [ ] **`piece-red-student.png`** — 104×104
- [ ] **`piece-red-master.png`** — 104×104

### 3. Card

**One important thing: do NOT draw the movement patterns.** The arrows/X
marks on each card are game data — the game stamps them onto your card
automatically, so they can never be wrong. You only design the empty card
template:

- [ ] **`card-face-template.png`** — 190×234 — a cream card. Keep these
      zones free of detail:

```text
┌──────────────────┐
│   NAME AREA      │  ← top 44 px (card names are written here)
│  ┌────────────┐  │
│  │  5×5 grid  │  │  ← 150×150 pattern zone, 30 px per cell
│  │   zone     │  │     keep it plain — marks get stamped on top
│  └────────────┘  │
│  bottom flavor   │  ← last 20 px: decorate freely
└──────────────────┘
```

- [ ] **`card-stamp-blue.png`** — 48×48 — small round seal for blue-stamp
      cards.
- [ ] **`card-stamp-red.png`** — 48×48 — red version.

*Feeling ambitious?* You can also draw a unique illustration per card
(16 cards: tiger, dragon, frog, rabbit, crab, elephant, goose, rooster,
monkey, mantis, horse, ox, crane, boar, eel, cobra) — name them
`card-art-tiger.png` etc., same 190×234 size. Totally optional.

### 4. Icon

- [ ] **`app-icon.png`** — 256×256 — becomes the window/dock icon.

---

## Nice-to-have assets (skip any — built-in fallbacks exist)

- [ ] `overlay-select.png` — 144×144 — amber ring shown on the selected
      piece's square.
- [ ] `overlay-dot.png` — 144×144 — teal dot showing where a piece can move.
- [ ] `overlay-capture.png` — 144×144 — teal ring shown on an enemy that can
      be captured.
- [ ] `motif-starburst.png` — 256×256 — amber burst for titles.
- [ ] `motif-lightning.png` — 128×128 — little flash when a piece is
      captured.
- [ ] `bg-pattern.png` — 512×512 — very subtle repeating dark texture for
      the app background.
- [ ] `logo-wordmark.png` — 600×200 — "ONITAMA ONLINE" lettering.
- [ ] `banner-lobby.png` — 900×160 — decorative strip for the lobby.

---

## How to hand them over

1. Put the files in `src/main/resources/assets/` inside the project
   (exact names from above), **or** just send me a zip / folder — I'll place
   them.
2. Tell me "integrate assets".
3. I'll rebuild the game, check every screen, and show you before/after
   screenshots.

---

## Progress

| Part | Designed | Delivered |
|---|---|---|
| Board (tiles + frame) | ☐ | ☐ |
| Pieces (4) | ☐ | ☐ |
| Card template + stamps | ☐ | ☐ |
| App icon | ☐ | ☐ |
| Optional extras | ☐ | ☐ |
