# UI Design — the look we're going for

**Inspiration:** [fireship.dev](https://fireship.dev) — dark background, warm
retro colors, chunky headlines, sticker-style cards, playful little details.
Think "cozy retro board-game night", not "corporate dashboard".

This doc describes the new look for the game's five screens (login, lobby,
game, leaderboard, replay viewer). The redesign only touches the visuals —
how the game *plays* stays exactly the same.

> Implemented in milestone M9. The color and font values below were
> extracted directly from fireship.dev's own CSS, so the look is faithful.

---

## The vibe in one line

Near-black room, cream board and cards on top, gold highlights, coral-red
vs sky-blue players, everything outlined in ink like a printed board game.

## Colors

| Use | Color |
|---|---|
| App background ("coal") | `#0F0D0E` |
| Panels & list rows ("charcoal muted") | `#1B1918` |
| Text, card faces ("beige") | `#F9F4DA` |
| Outlines, text on cream ("charcoal") | `#231F20` |
| Highlight: buttons, selection, banner ("gold") | `#FCBA28` |
| Blue player ("brand blue") | `#12B5E5` |
| Red player + errors ("brand red") | `#ED203D` |
| Move hints ("brand green") | `#0BA95B` |
| Board squares | cream `#F0E6D2` + charcoal `#221E1A` |

## Font

* **Titles, buttons, banners:** **Outfit** weight 900 (with 400/700 for
  body and bold text) — always UPPERCASE for headings. The font files are
  bundled inside the game (`src/main/resources/fonts/`, SIL OFL license
  included), so it looks the same on every computer.
* **Everything else:** the same Outfit at regular weight, for easy reading.

## The signature elements

* **Sticker look** — cards and panels sit like stickers: flat fill, black
  outline, and a solid shadow offset a few pixels (no soft blurs anywhere).
* **Pill buttons** — fully rounded, black outline, hard shadow; when clicked
  they "sink" into the shadow.
* **Heading banners** — cream rectangle with big black uppercase text
  ("YOUR TURN", "GAME OVER").
* **Little flourishes** — an amber starburst on masters and the game-over
  screen, a tiny lightning flash when a piece is captured.

---

## The five screens

### 1. Login
A cream "membership card" centered on the dark background, holding the
title, the input fields, and two buttons: **REGISTER** (amber pill) and
**LOGIN** (outlined pill). Errors show in coral below the fields.

### 2. Lobby
Your name + Elo in a cream badge at the top. **CREATE MATCH** as an amber
pill; the room code then appears as a big dashed **ticket** you share with
your friend. Joining is a code field + **JOIN** pill, with the list of open
matches below it (dark rows, cream text). Leaderboard and Replay viewer
buttons at the bottom.

### 3. Game (the main screen)
* **Top:** opponent's name and their two cards — shown from *your* point of
  view so you can plan with what you'll get next.
* **Center:** the board on a cream sticker frame. Squares are cream/charcoal
  with ink outlines. Pieces are sky/coral discs; masters wear a little
  starburst. Picking a card + a piece shows teal dots on squares you can
  reach (teal ring = you'd capture that piece). Amber marks the square that
  was just moved.
* **Bottom:** your two big cards — the selected one lifts with an amber
  frame — plus the small transit card and the turn banner ("YOUR TURN" /
  "WAITING…" / "GAME OVER").
* **Side:** move history and captured pieces as tiny colored discs.
* **Resign** is always available as a coral-outlined button.

### 4. Leaderboard
A simple dark table, amber header row, medal chips for the top three, back
button.

### 5. Replay viewer
Same board, with round ◀ ▶ transport buttons and a "MOVE 3 / 27" counter.
Replays always play from Blue's side.

---

## Rules for whoever builds it

* Only the UI code changes — game rules, networking, and saved data stay
  untouched.
* No new libraries: Swing and Java only. All decorative shapes (starburst,
  lightning, pills) are drawn in code.
* If your hand-made artwork files exist, they replace the drawn versions
  automatically — one file at a time, no pressure (see
  [ASSET_CHECKLIST.md](ASSET_CHECKLIST.md)).
* After building: all tests must still pass, and every screen must be
  clickable and usable.
