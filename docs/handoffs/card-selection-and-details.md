# Card selection and public details — 2026-10-17

- Both public played-card areas bind the same parchment detail popup as hand
  cards. GamePanel copies live details only from accepted public movement or
  committed skill events. Animation entries retain their matching face and text;
  arriving/concealed cards expose no tooltip. Reset/replacement dismisses old popups.
- Selecting a second card switches directly in click and slide modes. A tap on
  the selected card or a downward slide deselects it. Raised cards are painted
  copies, so CardPlayOverlay routes their visible hit area to the live hand card.
  Selection remains local and respects phase, paused state, pending actions and
  accepted-play input blocking. Insufficient replacement drags restore the old
  selection. Escape remains available.
- Menu and Surrender are stacked full-width pill buttons matching turn actions.
  Confirmation, native Game menu and Foresight use the Surrender label. The wire
  action and its confirmation/in-flight guards remain unchanged.

Changed UI: CardPlayOverlay, PlayedCardArea, CardDetails, GamePanel,
ClientFrame, ForesightDialog. Tests: GameCardDragTest, PlayedCardAreaTest,
PresentationRegressionTest, GamePanelLayoutTest, ForesightDialogTest.

Native full `mvn test`: 340 passed, zero failures/errors/skips. Coverage includes
both card types and input preferences, hit-testing/deselecting at the raised
visible position, real public-card hover popups, accepted public-event details,
reset cleanup, responsive button labels and existing privacy/reconnect flows.

Independent read-only Agent 2 review: no actionable findings; native full suite
340 passed, zero failures/errors/skips (`/tmp/grid-duel-agent2-card-fixes.log`).

Final clean `mvn clean verify`: 340 passed, zero failures/errors/skips; rebuilt
`target/onitama.jar`.
