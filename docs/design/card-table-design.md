# Card table direction

See `card-table-preview.png` for the original composition concept. Its sample cards and values are layout placeholders, not a catalog. The live screen built from it is the JavaFX `CardTableView`.

Use the existing `/assets/match-room.png` across the entire game screen. Keep the room and wooden tabletop visible; cards sit directly over the scene. Remove the chess grid when the card-game screen replaces the legacy screen. Use subtle brass outlines, warm ivory text, soft card shadows, and restrained local shading for contrast rather than a large opaque board panel.

- Top: round number, opponent's concealed hand, and quiet settings/leave controls.
- Table: opponent's seven-space Unit row above the player's. Both players' two Landmarks sit on separate, labelled lists of full-width rows beneath the score panel. The Unit rows use the full central table width, with larger card faces. Mark empty spaces faintly.
- Middle: a small current-turn indicator and fine dividing lines.
- Left: one fixed Balatro-inspired rail with stacked inset score wells, a large player total, a smaller opponent total, and two adjacent calculation tiles. Keep remaining plays, round, and cumulative total together below the score.
- Right: deck/discard access. In implementation, distinguish each player's piles and counts; never reveal hidden draws or opponent card faces.
- Bottom: the player's hand (ten cards at the start of a round; it overlaps when it grows wider than the table). Raise and outline the selected card; show readable card details on hover and keyboard focus. In-match chat is removed; do not add a chat panel or toggle.

Scale spacing and card size with the window while preserving readable text, all seven row spaces and both Landmark shelves. Use cover scaling without stretching the background. The image demonstrates composition; exact sizes and typography can adapt to the toolkit. Runtime names and effects remain real text, independent of artwork.

`CardTableView` implements this layout for live matches and feeds `ScoreRail` from each recipient-specific view. Selecting a card highlights its legal spaces or targets; hovering one shows a labeled preview of the score change computed from public rows. An accepted card play or **Skip turn** advances the turn. Skip turn (also S), below Draw, uses one of the ten plays and preserves the hand, rows and draw allowance. Its cost is shown below the button. It is unavailable outside your turn, while reconnecting, and during Landmark or Reconnaissance choices.

Match start: the five Landmark offers fill the middle of a dimmed table; the player ticks two and keeps them, then waits for the opponent, whose picks stay hidden until both have chosen. The ten-card opening deal follows. Under each player's own deck a **DRAW n/3** button makes a manual draw (also clicking the deck, or D); a Watchtower shows the deck's top card face up with a TOP CARD label. Reconnaissance shows its cards privately over a dimmed table until one is kept. Cards a Spy revealed appear as small faces marked SPIED beside the opponent's hand count. Clicking a revealed face opens the same large inspector with its points, nation/class or Spell text. Only cards in the recipient's current revealed list are inspectable; an open Spy inspector closes and clears its private description when that card leaves the enemy hand or the round ends. A sabotaged Landmark is shaded and labelled until the round ends. Card faces add only what the art lacks: Unit points badges and disable markers (the neutral cards print their own kind, title and description).

Hand cards use separate dimensions up to 1.5 times the row cards, limited by available vertical room. They sit higher with bottom clearance for the fan and shadows; overlapping spacing accommodates larger hands. Drawing, hit testing, dragging and deal animations share these hand dimensions.

Players can arrange their own hand and row for easier reading: drag a hand card sideways within the hand (the others make room) or drag a card on your row to another space (swapping with any card there). This is display only. The server's rows and order are unchanged, nothing is sent, the opponent's view is unaffected, and scoring does not depend on position. Number keys count row spaces as the player sees them. A drag still plays the card when it is dropped on a legal destination. The arrangement lasts for the game and resets with a new one.

## Scoring should feel rewarding

The score is the main visual reward, inspired by Balatro. Make the cause of each change visible: highlight the contributing cards, trace matching pairs, show their bonuses, then count the resulting change into the large score. Keep scoring feedback beside the cards that caused it: a short ivory gain, a small gold breakdown, and thin warm-gold links between matching cards. Keep the center clear for turn status rather than adding a dashboard banner. Card details separate printed points, combo bonuses, and any actual Landmark multiplier. Do not imply that every score uses a global multiplier: scope follows each real effect.

The sample shows three 3-point Units sharing both tags: 9 printed points + 27 pair bonuses = 36. Adding the third Unit changes 15 to 36: +3 printed points and two +9 pairs, for +21. The opponent's sample 18 assumes three 3-point Units sharing only region; its Landmark is an unspecified layout placeholder and supplies no illustrated bonus.

For implementation, use a short count-up, brief score enlargement, a floating signed change, and optional scoring sounds. Larger combos can receive stronger emphasis. Animate only accepted public results; previews must be labeled and use permitted information. Negative changes should be equally readable. Keep the true score available immediately, allow overlapping updates without blocking input, and honor reduced motion and sound settings. These are presentation effects, not new scoring rules.

## Theme revision

Keep the medieval room cohesive: aged brass, ivory lettering, charcoal-brown card backs, and warm candlelight highlights. The two inset calculation tiles use muted blue and vermilion, framed by brown leather and brass. Avoid neon team score blocks and a floating central dashboard strip. The reward should come from timing and clear cause-and-effect: matching cards catch a brief warm light, pair bonuses appear, then the large score responds. Sound should feel like cards, coins, or a soft bell. Keep persistent decoration quiet so a scoring moment has somewhere to grow.

## Implemented score rail and preview

`src/main/java/deckduel/client/ui/ScoreRail.java` (JavaFX) takes a presentation-only `Display` snapshot on the JavaFX Application Thread. It displays supplied totals without computing game rules. The two tiles are **Units + Combos**, not Chips × Mult: the approved base rules are additive. The rail contains only scores and counters: opponent score, your live round score, Units + Combos, plays left, round, and match total. The former Landmarks & Effects well is removed; counters sit directly below the breakdown in a shorter frame. The left dock uses four full-width rows with art thumbnails, full names, status indicators and info icons. Click a row (or press L) to open a large card inspector with exact buff text, active/Sabotaged status and the current public score contribution for scoring effects. The inspector refreshes from the latest view and closes with Close, Escape or a backdrop click. While targeting Sabotage, clicking the enemy row casts it; the info icon still opens details without casting. Numbered keyboard targeting is preserved. The dock is separate from the seven-space rows. Full score descriptions remain available to assistive technology; hovering the rail no longer opens an oversized tooltip over the scores.

The rail includes a brief gold pulse and signed gain/loss on score changes, immediate authoritative totals, reduced-motion behavior, accessibility descriptions, and feedback cleanup/reset. The table adds thin links under matching Units (hover a Unit to see its pair bonuses). Each accepted change is traced card by card (`ScoringTrace`): changed cards glow, each new pair lights in turn with its bonus and a bell that rises through the combo, affected Landmarks flash, then the rail counts up with a coin (or a falling tone for a loss) and a fading signed change above the row. Sounds are synthesized, follow Settings → Game sounds, and reduced motion skips straight to the result. The standalone preview below uses explicitly labeled sample data.

Run from the repository root after compiling:

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java --class-path "target/classes:$(cat target/cp.txt)" docs/design/CardTablePreview.java
```

Use the preview's replay button to see the score change from 15 to 36. Regenerate the screenshot (JavaFX still needs a graphical session to start):

```sh
java --class-path "target/classes:$(cat target/cp.txt)" docs/design/CardTablePreview.java --render
```

## Play animations

Every accepted play is staged by `PlayAnimator` as a short `Staging` timeline (plain data) over the already-final view, and `TableEffects` performs it as JavaFX nodes, following common "game juice" practice: layer motion, numbers, particles and sound; show one cause at a time; spring overshoot on landings and pops; shake scaled to the size of the moment; a brief hit-stop on heavy impacts. The server has already resolved the play, the public `PlayRecord` in the view says what happened, and a newer update plays alongside any sequence still running (so a quick reply never cuts a card off mid-flight); only a new game or reduced motion clears them.

- **Motion model (`CardMotion`):** cards move like Balatro's: a short pick-up (rise, slight pull-back), then a critically damped spring to the destination (quick to leave, soft to arrive); the card leans into its sideways speed with a slight lag (capped near 12°), rises while carried (a little larger, its shadow drifting away) and is set down. Flips are real turns about the card's vertical axis under a perspective camera; the turning face is lifted and shaded, and the back is shown while it faces away. The spring's small final remainder is spread over the trip, so a card arrives exactly in its space instead of snapping the last few pixels. The hand is fanned along an arc; hover and selection ease in lift and scale with a small rotation wobble, and a dragged card leans with the pointer. All motion is a closed-form function of time, so it is the same at any frame rate, and the images a sequence needs are prepared before its clock starts so the motion never skips ahead.
- **Unit or Landmark:** carried from the hand to its space (the opponent's card turns over on the way) and set down with a soft scale punch; on contact a soft ring spreads flat across the tabletop and a little dust puffs out from under it; a replaced card is lifted out to the discard; a Landmark gets a light sweep.
- **Spell:** rises to the table across from its target and charges in a rune ring while the table dims; a bolt strikes the target Unit, which shatters with a flash, shake and hit-stop (Destroy), shudders under a grey veil and chains (Disable), or is lifted clear with a light sweep (Clear Disable). Sabotage turns the Spell's power into a war hammer that winds up over the enemy Landmark and strikes it with a thud, flash, dust and a heavy shake; cracks spread across it and it stays shaded until the round ends (Landmarks are never destroyed). Spells without a table target dissolve into embers, and the cards they move fly between decks and hands: drawn cards glide in, an Exchange or random discard goes to the discard pile, a stolen card crosses between hands, and the opponent's gains arrive face down.
- **Combos:** after the landing, each new pair link draws itself with a spark and pops its +3/+9; several pairs at once call out "Combo ×N" with a burst, shaking harder for bigger bonuses; the rail then counts up.
- **Draws and rounds:** drawn and dealt cards glide from the deck (about 1.3 s: eased in and out along a shallow arc, lifted mid-flight, turning over across the middle of the trip) and settle exactly at their fanned place and tilt in the hand, so nothing jumps when the real card appears; a round-ending play still lands, then the rows sweep away, a round banner shows the banked score and the new hand is dealt; a victory brings confetti.
- Sounds (Settings → Game sounds): whoosh, spell shimmer, shatter, chain clink, deal tick and round bell, all synthesized. Reduced motion skips the staging and plays only the result sound.
- Pacing and smoothness: plans are written at base speed and `Staging.TEMPO` (1.8) slows every sequence evenly. Frames follow the JavaFX pulse, synchronised with the display. Card faces are the shared `CardFace` sprites converted once to JavaFX images (`FxKit`).

## JavaFX screens

The match table (`CardTableView`, `ScoreRail`, `TableEffects`) and the Atlas (`AtlasView`, `AtlasMapView`) are JavaFX; the menu and history stay Swing. `FxScreens` shows a JavaFX window at the Swing frame's place and size while the table or Atlas is open, and brings the frame back afterwards. `ClientModel` stays on the EDT: its events reach the JavaFX views as immutable snapshots via `Platform.runLater`, and plays, rematch and leave go back through `SwingUtilities.invokeLater`. Effects with depth (card turns) live in a `SubScene` with a `PerspectiveCamera`; glows, shadows and blurs are JavaFX effects, sparks and flashes use additive blending.

Why a separate window rather than a `JFXPanel` inside Swing: measured on the same machine and workload (below), a full-size transparent `JFXPanel` overlay ran at 48 frames per second (36 at 900 px wide); a native JavaFX window holds the display's 60.

### Measured frame pacing (2026-10-19)

- Environment: Apple M2, macOS, built-in 2560×1664 Retina display at 60 Hz, JDK 26 running the Java 17 build, OpenJFX 21.0.5.
- Workload: the real `CardTableView` in a shown window, playing the staged sequence of a real seeded play from the proposal catalog; card art preloaded.
- Method: an `AnimationTimer` records every pulse from the moment the update is shown until staging ends; frames per second = (pulses − 1) / elapsed; a gap over 25 ms counts as a late frame. Harness: a scratch `MeasureFxTable` class, not shipped.

| Sequence | Window | Frames per second | Longest gap | Gaps over 25 ms |
|---|---|---|---|---|
| Opening deal (cold) | 1280×760 | 54.5 | 197.7 ms | 3 |
| Placement with two new pairs | 1280×760 | 59.5 | 50.5 ms | 1 |
| Removal Spell | 1280×760 | 60.1 | 25.2 ms | 1 |
| Opening deal (cold), run 1 | 1920×1080 | 51.9 | 203.5 ms | 5 |
| Opening deal (cold), run 2 | 1920×1080 | 59.1 | 49.9 ms | 1 |
| Placement with two new pairs | 1920×1080 | 60.1 | 17.6 ms | 0 |
| Removal Spell | 1920×1080 | 60.1 | 26.7 / 17.7 ms | 1 / 0 |

Placements and Spells hold the display rate. The first opening deal of a session can pause for up to about 0.2 s while the opening hand's card faces are rendered for the first time (measured with eight-card hands); the animation clock starts after that work, so the motion itself does not skip. Pre-rendering faces in the background would remove the pause; it is not done yet.
