# Balatro presentation audit for Grid Duel

Date: 2026-10-12. Scope: identify presentation systems worth studying and adapting to the existing Java 17 Swing project. No game implementation changes.

## Evidence and limits

Read-only static inspection of the installed Steam package:

`/Users/chiseled/Library/Application Support/Steam/steamapps/common/Balatro/Balatro.app/Contents/Resources/Balatro.love`

SHA-256: `48c7a0791796a969d2cd0891ebdc9922b2988eb5aaad8ad7a72775a02772e24e`.

`globals.lua:1–2` identifies this package as `1.0.1o-FULL`. The application's bundle version is a separate value and is not the game-version evidence.

The package contains readable Lua and GLSL-style shader files. Files were read directly from the ZIP-compatible archive with Python; no extraction, modification or execution of Balatro occurred. REA MCP tools and the `rea` shell command were unavailable in this session; these are ordinary static-analysis observations, not REA Evidence records.

Function bodies were inspected for movement, hand arrangement, hovering, selection, flips, card disappearance/appearance, text pulse/pop, drawing events, particles, buttons and motion settings. Shader files were inventoried and their inputs inspected, not exhaustively analyzed or benchmarked. Runtime appearance, every call site, actual frame timings and performance remain unverified. This is a broad presentation inventory and prioritization, not an exhaustive reconstruction of every screen or effect. Recommendations and Swing effort below are engineering judgments.

## Highest-value systems

| Feature | Installed-package evidence | Application to Grid Duel | Swing effort |
| --- | --- | --- | --- |
| Smooth position following | `engine/moveable.lua:405`, `Moveable:move_xy` | Card draw, replacement, board-piece movement | Medium, reusable |
| Smooth scale and rotation | `engine/moveable.lua:423,447` | Hover enlargement and movement tilt | Low after shared motion support |
| Activation pulse/wobble | `engine/moveable.lua:250,267`; `card.lua:4333` | Successful skill cast, mana gain, button feedback | Low |
| Hover feedback | `card.lua:4306` | Lift/scale, brief feedback, readable skill tooltip | Low–medium |
| Selection elevation | `cardarea.lua:450–464`; `card.lua:4582` | Distinguish the selected movement/skill card | Low |
| Hand arrangement and reflow | `cardarea.lua:410`, `CardArea:align_cards` | Keep two movement cards and a configurable skill hand readable after a draw | Medium |
| Draw sequencing | `functions/common_events.lua:386`, `draw_card` | Show a replacement moving from deck to hand | Medium |
| Edge-aware tooltip placement | `card.lua:4275`; `engine/ui.lua:1022` | Keep descriptions/costs visible at screen edges | Low–medium |
| Small UI button feedback | `engine/ui.lua:965` | Confirm casting, end turn, and menu actions | Low |
| Reduced motion and speed controls | `functions/UI_definitions.lua:2302–2308`; `game.lua:2495` | Let players minimize delay and disable decorative wobble | Low if designed into the scheduler |

The movement code separates target transforms (`T`) from displayed transforms (`VT`) and maintains smoothing state. This is the most reusable concept to study. A single original Swing implementation can animate all card artwork rather than implementing separate behavior per skill.

The activation pulse uses decaying oscillations of scale and rotation; its base pulse window is 0.4 seconds in the inspected routine. This is a static source parameter, not measured elapsed gameplay time or a requirement to copy the value.

## Useful second pass

| Feature | Evidence | Adaptation | Judgment |
| --- | --- | --- | --- |
| Card face/back flip | `card.lua:4113–4140`; width pinching in `engine/moveable.lua:434` | Own skill draw or Foresight reveal: shrink horizontal scale, switch face, expand | High value; medium effort |
| Drag following and motion-linked rotation | `engine/moveable.lua:217,447`; hand sorting in `cardarea.lua:464` | Optional Foresight reordering; keyboard buttons can provide the same function | Useful; medium effort including hit testing |
| Deck thickness and shuffle movement | `cardarea.lua:412–423` | Show deck count and movement-card recycling | Useful; low–medium for an approximation |
| Dynamic text pop and pulse | `engine/text.lua:156,161,247` | Mana changes, “Your turn”, skill name | Useful; low–medium for whole-label animation |
| Text quiver | `engine/text.lua:239` | Brief warning or victory accent | Optional; avoid continuous movement on rules text |
| Small particles | `engine/particles.lua:63`; called by `card.lua:2079,2130,2183` | Capture sparks or a skill resolving | Useful; medium; use capped particle counts |
| Card dissolve | `card.lua:2130`; `resources/shaders/dissolve.fs` | Show a skill being permanently spent | Useful visual meaning; medium for fade/particles, higher for shader fidelity |
| Card materialization | `card.lua:2183` | Highlight a replacement/revealed skill | Optional; medium approximation |
| Shattering | `card.lua:2079` | Emphasize an eliminated board piece | Optional; medium approximation |
| Coordinated audio cues | `card.lua:4308`; `functions/common_events.lua:416`; `engine/ui.lua:989` | Hover, draw, cast and confirm sounds using original audio | Useful; modest effort, throttle repeated cues |
| Shadow/parallax | `engine/moveable.lua:459`; `engine/sprite.lua:73–105` | Add depth to lifted cards | Simple shadow is cheap; exact cursor-dependent shader tilt costs more |
| Sprite-sheet presentation | `engine/animatedsprite.lua:24–47` | Optional animated rune/icon or capture burst | Useful only if suitable original assets exist |

Use separate visual paths for movement recycling and permanent skill spending. Both are card animations, but they communicate different rules. A spent skill should remain identifiable in the public spent-pile UI even if its hand representation dissolves.

For Foresight, animate only the preview delivered to the owner. A face-down opponent-card representation must never contain the secret face under an overlay. Public cast identity may appear only once the host accepts commitment.

## Lower return for this Swing project

The following shader files exist, with dedicated effect inputs; their exact visual algorithms have not been fully analyzed in this pass:

- `foil.fs`, `holo.fs`, `polychrome.fs`: potential card surface treatments. High effort to reproduce faithfully in plain Graphics2D; a clipped moving highlight is a cheaper original approximation.
- `negative.fs`, `negative_shine.fs`, `gold_seal.fs`, `hologram.fs`: extra material/cosmetic variations. The current game has no editions/rarity mechanics requiring them.
- `CRT.fs`: exposes distortion, noise, bloom, glitch and scanline controls. Low gameplay benefit; can reduce readability of board coordinates and skill text.
- `background.fs`: time/spin, color and contrast inputs. A menu background could use a simpler cached animation; keep the tactical board visually quiet.
- `flame.fs`, `vortex.fs`, `splash.fs`, `booster.fs`, `voucher.fs`, `played.fs`, `debuff.fs`, `flash.fs`, `skew.fs`: candidate specialized effects. Inventory presence alone does not establish when/how each appears in gameplay; trace callers only if one specific effect is selected.

Room shake/sway is explicitly implemented in `functions/common_events.lua:1127`. For this game's precise square targeting, localized impact feedback is preferable to moving the whole board.

Skip scoring cascades, shop/economy presentation, pack-opening flows, Joker-specific machinery and a replacement UI layout engine unless a concrete feature in Grid Duel requires them. The shared motion, timing and text ideas cover the relevant benefits with a much smaller investigation.

## Recommended implementation order

1. One Swing presentation clock, target/display transforms, easing and a reduced-motion setting.
2. Hover, selection, tooltip placement and hand reflow.
3. Accepted movement/card replacement, activation pulse and permanent-spend animation.
4. Private flips and optional drag/reorder for Foresight; plain buttons remain accessible.
5. Small capture particles, mana/text pulses and original audio cues.
6. Optional surface shine or menu background only after a playable performance check.

This is a proposed adaptation architecture, not a claim that Balatro uses Swing or the proposed Java types. Use Graphics2D transforms/compositing and a shared Swing Timer. Cache artwork; avoid repeated image loading and full-resolution image processing on the EDT. Stop unnecessary repainting when idle.

The presentation scheduler must consume authoritative views and sanitized PublicEvents. Animation completion must never mutate rules, grant mana, draw cards, hold up the server or decide a capture. Reconnect/new-match sync should cancel outdated effects and display the current view. Visual randomness must not consume match RNG. Unlike a local single-player event queue, this project's networked lifecycle cannot be copied into animation callbacks.

## Narrow follow-up prompt

Study only these entries in the installed Balatro package: Moveable move_xy/move_scale/move_r/juice_up, Card hover/flip, CardArea align_cards, and draw_card. Use this audit's package identity to avoid repeating discovery. Explain the minimum state and timing relationships needed for hover, selected lift, hand reflow, draw, flip and activation pulse. Separate direct code observations from proposed Java adaptations. Write an original small Java 17 Swing presentation component using existing project patterns and no new dependencies. Keep server-authoritative rules and hidden information separate. Do not port the whole engine or analyze unrelated scoring/shops. Verify normal/reduced motion, interrupted animations, resized windows, Red orientation, reconnect and empty hands. Obtain the current gameplay/UI migration status before integrating; do not collide with another agent's edits.
