# Card table direction

See `card-table-preview.png` for the proposed composition. This is a visual concept for the gameplay migration, not an implemented match screen. Sample cards and values are layout placeholders, not a proposed catalog.

Use the existing `/assets/match-room.png` across the entire game screen. Keep the room and wooden tabletop visible; cards sit directly over the scene. Remove the chess grid when the card-game screen replaces the legacy screen. Use subtle brass outlines, warm ivory text, soft card shadows, and restrained local shading for contrast rather than a large opaque board panel.

- Top: round number, opponent's concealed hand, and quiet settings/leave controls.
- Table: opponent's eight-space row above the player's eight-space row. Mark empty spaces faintly. Units and Landmarks share the same row.
- Middle: a small current-turn indicator and fine dividing lines.
- Left: one fixed Balatro-inspired rail with stacked inset score wells, a large player total, a smaller opponent total, and two adjacent calculation tiles. Keep remaining plays, round, and cumulative total together below the score.
- Right: deck/discard access. In implementation, distinguish each player's piles and counts; never reveal hidden draws or opponent card faces.
- Bottom: the player's eight-card hand. Raise and outline the selected card; show readable card details on hover and keyboard focus. Keep chat secondary and collapsible.

Scale spacing and card size with the window while preserving readable text and all eight row spaces. Use cover scaling without stretching the background. The image demonstrates composition; exact sizes and typography can adapt to Swing. Runtime names and effects remain real text, independent of artwork.

The reusable Swing rail is implemented in `DuelScorePanel`; `CardTablePanel` exposes an empty table through Play → Practice → Preview table. The old game has been removed. The new card-game screen and rules are not implemented. Connect the rail to recipient-specific views when building the new game. There is no pass or End Turn button: an accepted card play advances the turn.

## Scoring should feel rewarding

The score is the main visual reward, inspired by Balatro. Make the cause of each change visible: highlight the contributing cards, trace matching pairs, show their bonuses, then count the resulting change into the large score. Keep scoring feedback beside the cards that caused it: a short ivory gain, a small gold breakdown, and thin warm-gold links between matching cards. Keep the center clear for turn status rather than adding a dashboard banner. Card details separate printed points, combo bonuses, and any actual Landmark multiplier. Do not imply that every score uses a global multiplier: scope follows each real effect.

The sample shows three 3-point Units sharing both tags: 9 printed points + 27 pair bonuses = 36. Adding the third Unit changes 15 to 36: +3 printed points and two +9 pairs, for +21. The opponent's sample 18 assumes three 3-point Units sharing only region; its Landmark is an unspecified layout placeholder and supplies no illustrated bonus.

For implementation, use a short count-up, brief score enlargement, a floating signed change, and optional scoring sounds. Larger combos can receive stronger emphasis. Animate only accepted public results; previews must be labeled and use permitted information. Negative changes should be equally readable. Keep the true score available immediately, allow overlapping updates without blocking input, and honor reduced motion and sound settings. These are presentation effects, not new scoring rules.

## Theme revision

Keep the medieval room cohesive: aged brass, ivory lettering, charcoal-brown card backs, and warm candlelight highlights. The two inset calculation tiles use muted blue and vermilion, framed by brown leather and brass. Avoid neon team score blocks and a floating central dashboard strip. The reward should come from timing and clear cause-and-effect: matching cards catch a brief warm light, pair bonuses appear, then the large score responds. Sound should feel like cards, coins, or a soft bell. Keep persistent decoration quiet so a scoring moment has somewhere to grow.

## Implemented score rail and preview

`src/main/java/deckduel/client/ui/DuelScorePanel.java` takes a presentation-only `Display` snapshot on the EDT. It displays supplied totals without computing game rules. The two tiles are **Units + Combos**, not Chips × Mult: the approved base rules are additive. Actual scoped Landmark effects appear in the effects well; the eventual engine supplies the resulting score.

The rail includes a brief gold pulse and signed gain/loss on score changes, immediate authoritative totals, reduced-motion behavior, accessibility descriptions, and feedback cleanup/reset. Card-trigger animation and sound remain future presentation work. In the application's empty table it shows unknown scores; the standalone preview below uses explicitly labeled sample data.

Run from the repository root after compiling:

```sh
java --class-path target/classes docs/design/CardTablePreview.java
```

Use the preview's replay button to see the score change from 15 to 36. Regenerate the screenshot without a window:

```sh
java -Djava.awt.headless=true --class-path target/classes docs/design/CardTablePreview.java --render
```
