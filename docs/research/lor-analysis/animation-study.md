# Public-footage study: hover/drag, draw, play and targeting

## Scope and evidence quality

**Actual visual inspection, not a video-search summary.** Primary source:
[June — Legends of Runeterra Gameplay (No Commentary)](https://www.youtube.com/watch?v=be_3gVA4HiM),
published 2022-08-21, duration 12:16. The acquired format is video-only H.264,
1280×720, 30 fps, 120811885 bytes; SHA-256:
`5a761758a27bfcc0403a3e61e33615b22b8a31ac2f70710a7c0429fb90be04f1`.

298 timestamp-selected images were extracted into 20 contact sheets and visually
reviewed: a 15-second overview across the recording, 0.5-second focused windows,
and 0.1-second samples around selected transitions. This is **not** exhaustive
frame-by-frame viewing. Other previously found videos were not inspected and
are not corroborating evidence. Desktop-style cursor interaction is visible;
the recorder's device/input configuration and game build were not verified.

The source is historical footage, **not linked to the installed 2026 file hashes**.
These are supplemental video observations, not new REA runtime Evidence records.
LoR was not launched/attached to; no cookies, account credentials or game network
traffic were accessed. The earlier installed-file REA evidence is unchanged.

All video, images and helper utilities remain outside the repo:
`C:\Users\Kem\AppData\Local\Temp\opencode\lor-video-analysis-20261018\`.
Only this report and a metadata/hash index are added to the project.

## 1. Hover and neighbouring-card handoff — observed

Source [00:56–00:58](https://www.youtube.com/watch?v=be_3gVA4HiM&t=56s).
Evidence: `hover-detail-00.jpg` (56.0–57.5, 0.1-second samples).

- At 56.2 the hand is compact at the bottom. At 56.3 one card is enlarged above
  neighbouring faces while the hand opens; by 56.4 its position has settled.
- At 56.6 that card is the foreground focus; by 56.7–56.8 its neighbour has
  become the large foreground card. Adjacent cards remain partially visible.
- Lift, enlargement, fan/reflow and foreground ordering work together. The
  emphasized card preserves readable cost/title/rules rather than being only
  an outline around the small resting face.

**Timing limit:** this brackets visible state changes, not hover-entry latency.
The recording provides no mouse-enter/down telemetry. It does not establish a
specific delay, scale multiplier, spring model or easing equation. The observed
handoff occupies only a few tenths of a second at this sampling resolution.

**Grid Duel mapping:** spec §6 enlarged/live own cards and inspection details.
`CardHandPanel.setCards` raises hovered cards to layer 100; `updateLayers`
preserves selected-card priority. `CardPanel`/`SkillCardPanel` own hover rendering;
`CardDetails` provides full text. Enlargement/fan changes beyond the current
layout would be optional polish, **not a demonstrated new spec gap**. Never let
larger hover faces cover board squares or reveal opponent skill identities.

## 2. Drag/stage and return — observed visuals, inferred gesture

Source [01:29–01:37](https://www.youtube.com/watch?v=be_3gVA4HiM&t=89s).
Evidence: `drag-detail-00.jpg`, `return-detail-00.jpg`.

- 89.2: a large foreground hand card. 89.3: it appears as a smaller staged card
  above the hand, the play region glows blue, and the action reads “SUMMON”.
  89.4–89.7: its location changes before holding in that staging region.
- It remains staged for seconds: a sustained staged state consistent with
  player dwell, not evidence of a multi-second transfer animation. The cause
  of that hold is not established by video alone.
- 95.2: still staged. At 95.3–96.0 it travels down/across toward the hand.
  At 96.1 the same card is again inspectable in the hand; a different card is
  foregrounded by 96.3. No new summoned unit appears in this inspected sequence.

**Inference:** this is consistent with withdrawing/cancelling a staged play.
It does not prove the exact mouse-button sequence, cancellation key, invalid-drop
rule, drag threshold, or a server rejection. The roughly 0.7-second visible
return envelope in these samples must not be adopted as a fixed tween: some
motion could be pointer-driven. Likewise staging near 89.3–89.7 is not a measured
input-to-response latency.

**Grid Duel mapping:** §6 explicitly requires click-or-slide preferences,
left-button upward release selection, short-drag/Esc return, persistent raised
selection and cancellation before submission. `CardPlayOverlay.bind`,
`syncSelection`, `returnLift` and `tick` already implement those states; current
source constants are 38 px threshold, 52 px selected lift, up to 56 px drag lift,
and 200 ms cubic-out return. **Those numbers are Grid Duel implementation facts,
not LoR measurements.** Keeping cards within the hand reserve is required here;
do not reproduce LoR's lane staging over the board.

## 3. Draw, reveal and hand insertion — observed

Source [01:59–02:02](https://www.youtube.com/watch?v=be_3gVA4HiM&t=119s).
Evidence: `draw-detail-00.jpg` (119.2–120.7) and `draw-detail-01.jpg`
(120.8–122.3), sampled every 0.1 seconds.

- Opponent-side draw: absent as a travelling card at 119.2, visible near the
  upper-right deck at 119.3, large/rotated toward the centre at 119.5–119.9,
  then moving upward by 120.0–120.2. It stays a back through the inspected
  travel; the large travelling back is gone by 120.3.
- Own-side draw overlaps: a back enters from lower-left around 119.9–120.1;
  at 120.2 it is almost edge-on; by 120.3 the permitted face is visible. It is
  shown prominently around 120.4–120.8, then moves down/right toward the hand
  at 120.9–121.3. By 121.4–121.6 it has contracted into the hand region.
- The important sequence is **origin → travelling back → edge-on reveal →
  readable face → insertion/reflow**. Opponent travel retains concealment.
  Rotation, scale, translation and reveal are coordinated, not simply opacity.

**Timing limit:** the opponent travel is bracketed by first/last standalone-card
samples over roughly one second; own travel/reveal/insertion spans roughly
1.5–1.7 seconds in this example. These include distinct staging/reveal portions,
not one universal draw duration. Sampling is 100 ms and encoded frames are about
33 ms apart; no exact endpoints or easing function were fitted. The source's
round-start draws are LoR rules, **not** permission to add them to Grid Duel.

**Grid Duel mapping:** §§2.3–2.4, 2.7 and 6. `DeckDrawOverlay.paintFlight`
already combines smoothstep travel, an arc, rotation, a horizontal reveal flip,
and destination masking. `CardHandPanel` reflows remaining cards.
`Flight.concealed` has no face; `Flight.reveal` requires a permitted one.
Current defaults are 600 ms flights, up to 56 px arc, one-direction tilt peaking at −7°,
reveal window p=.60–.88, with the face used after its midpoint. These are
**Grid Duel source values**, not recovered LoR constants. `CardDraws` handles
same-card movement redraw, Refocus and Foresight without changing deck rules.
No requirement gap found; a longer reading hold would be optional and must not
stall network updates, expose hidden faces or draw on a skipped phase.

## 4. Play and resolution — observed

Sources [00:58–01:00](https://www.youtube.com/watch?v=be_3gVA4HiM&t=58s),
[02:21–02:24](https://www.youtube.com/watch?v=be_3gVA4HiM&t=141s).
Evidence: `hover-00.jpg`, `target-01.jpg`, `targeted-play-00.jpg`.

- Opponent play: a travelling back appears at 58.5; a public face appears by
  59.0; by 59.5 it is shrinking toward the opponent row; at 60.0 it is a unit
  in that row. The public-reveal phase is visibly different from a hidden draw.
- The targeted unit play at 142.0–142.5 presents a large card with target
  feedback before shrinking into the board row at 143.0–144.0. A separate
  effect indicator remains afterwards. Selection, confirmation and effect
  presentation are visually distinct states, not one generic flight.

**Limit:** video frames cannot identify server-acceptance boundaries, packet
order, rejection atomicity or whether input was blocked during any flight.
The roughly second-scale visual envelopes include staging/effects and do not
establish an automatic per-card animation duration.

**Grid Duel mapping:** §6 accepted-only flights, side skill references and input
gating; played-card-history amendment preserves the last skill across movement.
`GamePanel.presentPlayedCards` responds only to public `SkillCommitted` events;
`CardPlayOverlay` queues 540 ms cubic-out flights to the side area and blocks
conflicting input while busy. `GamePanel` queues accepted presentation batches
while `ClientModel` adopts current views immediately. Movement does **not** become
a side skill card or unit summon. Existing public reveal/privacy mechanisms meet
the relevant requirement; no new stack, reaction or summon lane is proposed.

## 5. Targeting — observed

Sources [01:36–01:38](https://www.youtube.com/watch?v=be_3gVA4HiM&t=96s),
[11:09–11:16](https://www.youtube.com/watch?v=be_3gVA4HiM&t=669s).
Evidence: `spelltarget-detail-00.jpg`, `spell-target-00.jpg`.

- 96.9: targeting prompt and “SELECT TARGET”. Around 97.0 a candidate unit
  receives a bright outline. At 97.1–97.3 a luminous connection joins the
  effect origin and that unit. By 97.7–97.9 the action reads “COMMIT”, while
  the connection persists.
- At 669.0 several enemy candidates are outlined; at 669.5–670.0 one is
  emphasized with a connecting line. Later another hand card can still be
  inspected while this selected-target relationship remains visible.
- Visual channels separate **candidate**, **selected relationship**, and
  **ready-to-confirm action**. The long hold of a line across later samples
  reflects a pending selection/response state, not the duration of its entrance.

**Unknown:** complete legal/illegal target sets, invalid-click behavior,
multi-target ordering, cancellation during private choices and input blocking.
These must not be invented from a selected valid-target example.

**Grid Duel mapping:** §6 shared legal highlights, ordered/multiple targets,
pre-submit cancellation and the Foresight commitment exception. `Targeting`
operates on `PlayerView`; `BoardPanel.drawSkillTargetingFeedback` draws candidate
piece outlines, selected-piece borders/order badges, legal destinations and
selected destination diamonds. `drawTargetConnectors` already draws movement
connections. `GamePanel` gates Cast Skill against complete legal selections.
A cursor-following curved skill tether would be optional polish, **not a spec
requirement**; copying LoR's targeting/stack semantics would conflict with this
game's finite effects and private-choice rules.

## Decision: preserve working behavior, do not invent an implementation gap

All four requested visual categories were examined, with drag input semantics
only partially established. Their matching mandatory Grid Duel behaviors already
exist. This follow-up changes research documentation only; it does not alter the
earlier uncommitted card-description fix or add speculative animation features.

Reusable design lessons are foreground emphasis, readable staged reveal,
identity-free concealed travel and distinct selection/confirmation feedback.
Exact LoR timings, bezier/spring coefficients, source code and assets were **not**
recovered. Optional polish needs a separate design decision and must preserve
the board-clearance, privacy, EDT, reduced-motion and accepted-event boundaries.

## Reproduction and validation

- [`video-evidence-index.json`](video-evidence-index.json) identifies the source,
  tools, hashes and the principal externally retained contact sheets.
- A portable, release-hash-checked `yt-dlp.exe` 2026.08.19 was placed only in the
  analysis directory. Existing `imageio_ffmpeg` supplied FFmpeg 7.1. No global
  installation, PATH/config change, login or browser-cookie access was used.
- Acquisition: `yt-dlp.exe --ignore-config --no-playlist --no-progress --no-warnings
  -f 136 -o june-720p.mp4 https://www.youtube.com/watch?v=be_3gVA4HiM`.
- `frames.py` uses FFmpeg input seeking and one decoded PNG at each requested
  video timestamp, then Pillow to label/reduce frames into 4-column contact
  sheets. For example: `python frames.py --start 96.4 --end 98 --step 0.1
  --name spelltarget-detail`. These are requested timestamps, not input-event
  timestamps or a fitted motion-capture track.
- The external `video-provenance.json` hashes all 20 sheets and scripts;
  `*.frames.json` record all 298 requested sample times. Stream URLs/raw download
  metadata remain external and are not copied into documentation.
- Relevant existing tests rerun:
  `mvn "-Dtest=CardPlayOverlayTest,GameCardDragTest,DeckPresentationTest,TargetingPresentationTest,AnimationSequenceTest,PresentationRegressionTest,Step05AnimationTest" test`
  — **91 tests passed**, 0 failures/errors/skips.
- Those tests validate Grid Duel code, **not** the recording or LoR internals.
  Real-screen readability and current-build equivalence remain unverified.
- Full `mvn test` rerun for this follow-up: **565 tests, 5 failures, 12 errors,
  0 skipped**, matching the earlier baseline failure signatures: five
  `GamePanelLayoutTest` assertions and twelve Windows SQLite temporary-file
  cleanup errors. Log:
  `C:\Users\Kem\.local\share\opencode\shell\136b1610903a2d9276e89b10fcd966f725f251b6\sh_14faad596001t77UruTVJZAg3a.out`.
  Full-suite/release limitations from [`validation.md`](validation.md) remain
  unresolved; this study does not waive them or claim green acceptance.
- Independent Agent 2 inspected the principal contact sheets and code mapping;
  no new CRITICAL/HIGH/MEDIUM defect found. Two wording notes were corrected:
  staged-hold cause is inferred, and the current draw tilt peaks at −7°, not
  ±7°. Its independent `mvn test` also returned **565 tests, 5 failures,
  12 errors, 0 skipped**, with the same baseline signatures. Review log:
  `C:\Users\Kem\AppData\Local\Temp\opencode\agent2-lor-video-review-20261018.log`.
