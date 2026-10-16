# Step 5 — Playtest and Visual Review Report

> Historical screenshot/video exports named below were removed during the requested cleanup; runtime GIFs and regeneration sources remain.

**Date:** 2026-10-14  
**Reviewer:** Agent 2 (Independent Testing & Playtest Review)  
**Harness / Spec:** `AGENTS.md`, `GRID-DUEL-BOARD-GAME_SPEC.md`, `docs/agent2-testing-pipeline.md`, `docs/handoffs/step-05-animation.md`  
**Working Tree Identification:** `dfe164373eee0c0efabd7c23358f40afe375c82e` (with uncommitted Step 5 animation and review changes preserved)  
**Active Server Process:** Background daemon process on `localhost:5555` backed by `data/local-test.db` (Task `task-603`)  

---

## Executive Verdict

**NEEDS FIXES BEFORE FINISHING STEP 5**

While core piece movement, turn progression, deck counters, history deduplication, and privacy boundaries are functionally robust (269/269 tests green), interactive playtesting revealed **two HIGH-severity functional defects** and **two MEDIUM-severity UI defects** in targeting prompt flow, card bounds initialization under reduced motion/initial deals, glyph rendering, and label clipping.

---

## Top Three Findings

### 1. [HIGH] Misleading Targeting Prompts & Overridden Cast-Readiness in `GamePanel.java:932-951`
- **Location:** `src/main/java/onitama/client/ui/GamePanel.java:932-951`
- **Problem:** `GamePanel.updateTargetingAndInstructions()` checks `!opt.selectablePieceIds().isEmpty()` before evaluating `model.canCastSkill()` and before `!opt.selectableSquares().isEmpty()`. Because `Targeting.skillOptions()` keeps `selectablePieceIds` populated so players can switch pieces, `GamePanel` assumes that any non-empty `selectablePieceIds` with `selectedCount > 0` means a second piece is needed.
- **Why it matters:** 
  1. For single-piece movement skills (e.g. `DASH`), after the player selects a piece, the UI instructs: `"Select second target piece on board for DASH"` rather than prompting for the target destination square.
  2. For multi-target skills that can be cast with 1 or 2 pieces (e.g. `SCATTER`), once 1 piece has a valid destination assigned, the skill is valid and castable. However, the interface continues to insist `"Select second target piece on board for SCATTER"`.
  3. When a complete valid targeting selection is made (`model.canCastSkill() == true`), the prompt `"Ready to cast <Skill> — click Cast Skill"` is NEVER displayed because the earlier `else if` branch consumes it. The cast button tooltip also erroneously insists `"Select second target piece"`.
- **Evidence:** Visual screenshots `targeting-dash-piece-selected.png`, `targeting-dash-ready-to-cast.png`, `targeting-scatter-ready-to-cast.png`.
- **Recommended Fix:** In `GamePanel.updateTargetingAndInstructions()`, check `model.canCastSkill()` first (or verify if a destination square or direction is pending) before falling back to multi-piece prompts, and only prompt for a second piece if the skill actually requires multiple targets to be valid.

---

### 2. [HIGH] Uninitialized Card Bounds (`0x0`) in `CardHandPanel.java:158-166` When `shouldAnimate` is False
- **Location:** `src/main/java/onitama/client/ui/CardHandPanel.java:158-166`
- **Problem:** `CardHandPanel` extends `JLayeredPane` (which uses a `null` layout manager and relies on its own overridden `doLayout()`). In `setCards(List next)`, when `shouldAnimate` evaluates to `false`:
  ```java
  reflowProgress = 1f;
  revalidate();
  repaint();
  ```
  `doLayout()` is never invoked synchronously.
- **Why it matters:** In Swing, calling `revalidate()` on a `JLayeredPane` whose size inside its parent `BoxLayout` did not change will NOT trigger an automatic layout of its children. Newly added `CardPanel` and `SkillCardPanel` instances retain initial bounds `Rectangle[x=0, y=0, width=0, height=0]`.
- **Under Realistic Use:**
  1. When **Reduced Motion** is enabled (`reducedMotion == true`), `shouldAnimate` is always `false`. The entire hand renders with width=0 and height=0 (completely invisible and unclickable).
  2. On initial hand deal or whenever `startXMap.isEmpty()` (e.g. after screen switches or complete hand replacements), `shouldAnimate` is `false`. Cards remain at `0x0` until the window is manually resized.
- **Evidence:** Confirmed programmatically and visually: `Card 1 bounds after setCards: java.awt.Rectangle[x=0,y=0,width=0,height=0]`.
- **Recommended Fix:** Call `doLayout()` directly inside `CardHandPanel.setCards()` when `shouldAnimate` is `false` (or unconditionally before initiating reflow).

---

### 3. [MEDIUM] Unicode Arrow `→` Displays as Missing Glyph Rectangle `[]` in `lastMoveLabel`
- **Location:** `src/main/java/onitama/client/ui/GamePanel.java:867`
- **Problem:** `lastMoveLabel` formats the last move badge as `last.fromSquare() + " → " + last.toSquare()` using the Unicode rightwards arrow `\u2192`. The configured font `Theme.normal(11f)` (`outfit-400.ttf`) lacks glyph U+2192.
- **Why it matters:** On-screen text renders as `Last: MOVE A1 [] B1` with an unprintable missing glyph square box `[]`, breaking visual presentation. Meanwhile, the move history list uses ASCII `->` and renders cleanly (`02. MOVE G7->E6`).
- **Evidence:** Screenshots `practice-turn-0-moved.png`, `practice-after-10-turns.png`.
- **Recommended Fix:** Change Unicode `" → "` to ASCII `" -> "` in `lastMoveLabel.setText(...)` on line 867.

---

## Additional Findings

### 4. [MEDIUM] Text Truncation and Clipping Across Standard Resolutions
- **Location:** `GamePanel.java:491, 497, 648`, `ForesightDialog.java:64-67`
- **Details:**
  - `instructionLabel` (`Theme.display(12f)`): The chunky black font `outfit-900.ttf` has advance widths that exceed the container allocation or label preferred size:
    - `"Choose a movement card from your hand, then pick a piece"` clips to `"Choose a movement card from your hand, then pick ("`
    - `"Waiting for opponent..."` clips to `"Waiting for opponen"`
    - `"Ready to cast MANA SURGE — click Cast Skill"` clips to `"Ready to cast MANA SURGE — clic"`
  - `handLabel`: `"YOUR HAND  ·  MOVEMENT PUBLIC / SKILLS PRIVATE"` clips the right edge to `"...PRIVAT"`.
  - `MOVE LOG`: Header label in the right column card is clipped on the right, rendering as `"MOVE LOC"`.
  - `ForesightDialog`: Subtitle clips the rightmost word, reading `"Arrange the top 3 cards. The first card on the left is drawn n"`.
- **Recommended Fix:** Add horizontal margin padding to label containers, enable HTML wrapping where multi-line instructions occur, or scale font sizes appropriately.

### 5. [LOW] Foresight Dialog Visual Header Inset
- **Location:** `src/main/java/onitama/client/ui/ForesightDialog.java:53-57`
- **Details:** Header container exhibits a faint top white seam against dark theme backgrounds, and the dialog title `"FORESIGHT — REORDER SKILL DECK"` slightly clips the descender of `"K"`.
- **Recommended Fix:** Adjust insets and border on `header` in `ForesightDialog`.

---

## Test & Playtest Execution Summary

### Window Sizes Tested
1. **1024 × 600** (Minimum supported desktop resolution):
   - All 7 board rows remain visible and clickable (`cell=30`, origin within panel bounds).
   - Bottom hand fits movement and 3 skills.
   - Minor label clipping observed on instruction label and hand header.
2. **1280 × 660** (Standard baseline resolution):
   - Board `cell=38`, comfortable layout. All buttons (Skip Movement, End Turn, Cast Skill, Menu, Resign) reachable.
3. **1440 × 820** (Large window resolution):
   - Board `cell=48`, spacious board area.
4. **Configured 6-Skill Hand Layout (8 cards total)**:
   - Hand successfully holds 2 movement cards + 6 skill cards across 1280×660 without overflowing into board boundaries.

### Actions & Skills Exercised in Live Practice Play
- **Completed Turns:** 10 full player turns (21 total turns including opponent bot).
- **Movement Actions:** `DRAGON`, `OX`, `COBRA`, `BOAR`, `MONKEY`, `CRANE`, `GOOSE`, `EEL`.
- **Skips:** Skip Movement exercised; verified no cards drawn upon skip.
- **Skills Tested:**
  - *Resource Skill:* `MANA SURGE` — cast successfully, increased mana, departure and deal pop verified.
  - *Movement Skill:* `DASH` — selectable piece highlight, destination highlight verified; instruction defect noted.
  - *Multi-Target Skill:* `SCATTER` — tested 1-piece destination selection; cast readiness defect noted.
  - *Directional Control Skill:* `SHOVE` / `GUST` — direction buttons verified.
  - *Attack Skill:* `CROSSFIRE` — targeting constraints verified.
  - *Deck Reordering:* `FORESIGHT` — modeless dialog, `DO_NOTHING_ON_CLOSE` commitment, confirm order, and resign access verified.
- **Selection Cancellation:** `Escape` key verified to clear in-flight piece, destination, and skill selections.

### Perspectives & Privacy Verification
- **Perspectives:**
  - Blue viewer sees home row $y=0$ at bottom; clicks at display row 6 map to canonical $y=0$.
  - Red viewer sees home row $y=6$ at bottom; clicks at display row 6 map to canonical $y=6$.
  - Relative direction controls (`FORWARD`, `BACKWARD`, `LEFT`, `RIGHT`) correctly transform to canonical `Direction` relative to player orientation.
- **Network Privacy:**
  - Opponent unplayed skills display as anonymous card backs (`CardBackPanel`).
  - `CardBackPanel.getToolTipText()` returns `null` (no name, parameters, or rule leaks).
  - Socket verification on `localhost:5555`: Server protocol `GRID_DUEL_PROTOCOL:2` verified.

### Interruption & Lifecycle Scenarios
- **Reduced Motion Mid-Animation:** Toggled `model.setReducedMotion(true)` while board movement animation timer was running. Timer stopped immediately; callbacks completed safely without lingering animations.
- **Window Resize Mid-Game:** Resized dynamically from 1280×660 to 1024×600. Panel and board geometry adapted without crash.
- **Screen Leave & Match Parking:** `model.leaveToLobby()` parks active practice match. Verified that returning to parked match restores game state cleanly.
- **Uncommon Engine Cases:**
  - Same movement card immediate redraw verified (smooth replacement without duplicate instance or stuck selection).
  - Empty skill deck spending verified (hand reflows down to 2 movement cards without exception).

---

## Test Suite Results

```text
mvn clean test
Tests run: 269, Failures: 0, Errors: 0, Skipped: 0
Build Success
```

All 269 unit and integration tests passed. Note that existing automated tests verify underlying engine contracts and offscreen renders, but missed the dynamic layout failure (`0x0` bounds in `CardHandPanel` when `shouldAnimate == false`) and the instruction prompt override in `GamePanel`.

---

## Visual Artifacts Recorded

All playtest screenshots have been saved to `docs/screenshots/playtest/`:
- `layout-1024x600-normal.png` — 1024×600 layout and row visibility.
- `layout-1280x660-normal.png` — 1280×660 standard view.
- `layout-1440x820-normal.png` — 1440×820 spacious view.
- `layout-1280x660-6skills.png` — 6-skill + 2-movement card hand layout fit.
- `layout-card-hovered.png` — Card hover z-order and lift effect.
- `practice-turn-0-moved.png` — Practice turn 1 move execution.
- `practice-after-10-turns.png` — State after 10 completed player turns (Turn 21).
- `targeting-dash-selected.png` — Initial selection of DASH skill.
- `targeting-dash-piece-selected.png` — DASH piece selected (showing misleading prompt defect).
- `targeting-dash-ready-to-cast.png` — DASH with destination selected.
- `targeting-scatter-selected.png` / `targeting-scatter-ready-to-cast.png` — SCATTER multi-target flow.
- `targeting-mana-surge-selected.png` — MANA SURGE instant-cast flow.
- `targeting-foresight-dialog.png` — Foresight dialog layout and controls.
- `perspective-BLUE.png` & `perspective-RED.png` — Board orientation comparisons.
- `same-card-redraw.png` — Immediate redraw of identical movement card.
- `interrupted-resized-1024x600.png` — Resize interruption.

---

## Running Processes
- Background server process listening on port 5555 (`task-603`):
  `java -jar target/onitama.jar server --port 5555 --db data/local-test.db`
