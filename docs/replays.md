# Personal match replays

Player journey: **Play → Match history → select a completed match → Watch replay**. A replay
shows the match exactly as that player saw it, one recorded state per accepted action, with
Previous, Play/Pause, Next, a speed button (0.5×, 1×, 2×, 4×), the round and action position,
both sides' score breakdowns, and Back to match history.

## Responsibilities

| Part | Responsibility |
|---|---|
| `server/MatchSession` | After every accepted change (`changed()`), records each human seat's own `PlayerView` once per new state revision, whether or not it was delivered, and finishes the recordings when the game ends. Bots are not recorded. |
| `server/ReplayStore` | Boundary for recording and serving replays. `NONE` is for servers without a database. It never throws into a match thread. |
| `server/MatchPersistence` | Implements `ReplayStore` next to results and history: encodes and decodes frames, works out the owner's result, and merges practice replays into history. |
| `db/ReplayDao`, `db/SqliteReplayDao` | SQL for the `replays` and `replay_frames` tables. Every read is keyed by owner username. |
| `server/ClientHandler` | Answers `ReplayRequest` only after login, for the signed-in account. |
| `net/ReplayRequest`, `net/ReplayResponse`, `HistoryEntry.replay/replayId/practice` | Wire contracts (added in protocol 14), listed in `Message.WIRE_FILTER`. |
| `client/state/ClientModel` | Sends `ReplayRequest` from History, accepts only the reply it asked for while History is open, and switches to `Screen.REPLAY`. |
| `client/state/ReplayPlayback` | Playback position, play/pause and speed. It has no connection and no access to the live match. |
| `client/ui/HistoryPanel` | REPLAY column (Watch / Incomplete / Unavailable), Watch replay button, double-click. |
| `client/ui/ReplayPanel` | Swing screen over `match-room.png`, using the painted `CardFace` sprites. A Swing timer steps playback; reduced motion turns off the fading highlight. |

## Data flow

1. **Recording (server, match thread).** `newGame()` calls `ReplayStore.begin(owner, "<session>-<game>", opponent, practice, catalog)`
   for each human seat. Each `changed()` then compares `state.revision()` with the seat's last
   recorded revision and stores `state.view(seat)` once if it is new: the same view that seat's
   `MatchUpdate` carries. Reconnect broadcasts and answers to retried request ids do not change the
   revision, so they add nothing; `PRIMARY KEY (replay_id, revision)` with `INSERT OR IGNORE`
   guards the same rule in the database. When the phase becomes `FINISHED`, the last frame (with
   the outcome) is stored and `finish` writes the owner's result, end reason and round scores.
2. **Linking.** Online results get the same `<session>-<game>` key in the additive
   `matches.match_key` column. This happens inside the existing atomic result + W/L transaction.
   Replay rows are written separately and never take part in that transaction.
3. **History.** `historyFor` reads `matches` as before and attaches the requester's own replay by
   `(owner, match_key)`. It adds the requester's finished practice replays (practice never creates
   `matches` rows or changes W/L), then sorts everything newest first.
4. **Retrieval.** `ReplayRequest(id)` → `SELECT … WHERE id = ? AND owner_user_id = (the requester)`.
   A missing id and someone else's id get the same "Replay unavailable." reply. Frames are decoded
   with `Message.WIRE_FILTER`, the same allowlist as the socket.
5. **Playback (client, EDT).** `ReplayPlayback` holds the frames. `ReplayPanel` paints the
   current frame and never calls the connection; Back calls `ClientModel.closeReplay()`.

## Statuses

| Status | When |
|---|---|
| Watch (`AVAILABLE`) | Recording finished and every frame stored. |
| Incomplete (`INCOMPLETE`) | A recording exists, but a frame write failed or the result was never written. It can still be watched; the screen says "Incomplete recording: some actions are missing". |
| Unavailable (`UNAVAILABLE`) | No recording: matches from before replays existed (`match_key` is NULL), or the server had no database. |

A practice game cut off by a server stop never finishes, so it is not listed. An online game cut
off the same way has no result row, so it does not appear in history either. Frames written by an
incompatible build are refused with "This replay was recorded by another version and cannot be
shown." rather than partly shown.

## Live selection deadline (introduced in protocol 18)

Protocol 19 additionally carries the recipient's final pre-bank round view in live `MatchUpdate`
metadata, so the table finishes scoring before bank/deal/result presentation. This does not add
or change stored replay frames. See [round presentation](round-presentation.md).

The server's 30-second King deadline is carried in live `MatchUpdate` metadata; it does not
change serialized `PlayerView` frames. Earlier King replays remain readable. Automatic first-card
confirmations are recorded through normal accepted revisions, preserving the same recipient
privacy and replay ownership rules. The client countdown never chooses a King locally.

## King compatibility (2026-10-25, protocol 17)

Frames now include shared offers, the owner's private confirmation, public King ownership,
round Spell/Edict counters and per-King score contributions. Pending Secret Edict cards are
recorded only in their owner's view and labelled separately from Reconnaissance in playback.
The opponent's recording has no pending card faces or unannounced King identity. Retry and
reconnect still do not add revisions. Old serialized frames lack required King/choice fields,
and are refused through the existing incompatible-version response, preserving all stored
replay data, accounts and results. See [King Cards](kings.md).

## Privacy

- Frames are the owner's own `PlayerView`: their hand, their Reconnaissance cards and the cards
  their own Spy revealed, plus public rows and scores. `PlayerView` carries no deck order, seeds,
  RNG state or other opponent hand cards. The integration test checks, revision by revision, that
  each player's frames equal what they were sent and hold no opponent hand card that was not
  revealed to them.
- Retrieval requires login and ownership. No other account, spectator or public link can load a
  replay. Passwords and credentials are never part of a replay.

## Online and offline

Online replays persist in the server database. Offline replays are written by the private
`OfflineServer` into its temporary database, which `ClientFrame.dispose()` deletes when the
client closes. The offline menu and history say so.

## Measured size

A seeded greedy game (Apple M2, JDK 27, `ClientFixtures.replay`) has 65 frames per player. Stored
frames average 4,979 bytes (maximum 6,206), about 316 KB per player per match. The
`ReplayResponse` for that game serializes to 48,412 bytes, because one stream shares class
descriptors. Recording adds one SQLite insert per human seat per action on the match thread. This
has not been measured under concurrent load.

## Not done

- Sharing, spectating, exporting or deleting replays.
- Re-reading old frames after an incompatible change to the `core.card` records: such replays
  are refused, not migrated.
- A retention limit: online replays grow without bound.

## Reviewing a recorded match

History filters work on the entries already returned by the server. Type part of an opponent's
name, choose All results / Win / Loss / Draw, and choose All matches / Online / Practice.
The controls combine, ignore letter case using a locale-independent comparison, and treat search
text literally. They preserve newest-first order and never issue an additional history request.
Clear restores the loaded list. A filtered summary counts practice apart from online W/L;
filtering does not change the profile's records. An empty filtered list has a separate message
from an account with no matches. The server's existing history limit still applies: these controls
do not search older database rows beyond the loaded response.

Selection follows a replay's identity if refreshing or filtering moves its row. Filtering out the
selected match clears the selection and disables Watch replay. Unavailable recordings remain
disabled; incomplete recordings remain watchable and labelled. Filtering never changes replay
ownership checks, account state or database rows.

### Timeline and chapters

The seek bar addresses recorded frame positions, starting at zero. Dragging pauses playback
immediately and previews the selected position while dragging; releasing leaves it paused. First,
Last and the round selector also pause. Previous round goes to this round's first recorded frame,
then to the preceding round on a second press. Next round goes to the next recorded round and
does not wrap. A shortened training excerpt may begin halfway through a round; its chapter starts
at the first frame present, rather than inventing the missing beginning. A one-frame excerpt has
a disabled slider. Play at the end restarts at the opening recorded position.

| Key | Action |
| --- | --- |
| Space | Play / pause |
| Left / Right | Previous / next recorded action |
| Home / End | First / last recorded position |
| Page Up / Page Down | Previous / next recorded round start |
| Escape / Backspace | Return through the page's existing Back action |

Keys belong to the replay page, not the entire application window. Focused text fields and
standard Swing controls retain their editing/navigation keys. Leaving the page stops its timers;
opening another recording detaches the previous playback observer and resets the journal filters.

### Action journal

Details replaces the right-hand score rail with two tabs. Actions lists each stored position with
its original frame index, round and added public log lines. Selecting a row seeks to that exact
frame and pauses. Filters for Spells, Kings, round changes, result and recording gaps combine
with Key moments and literal text search. Changing the filter never moves playback. When the
current position is absent from the filtered journal, no row is selected.

`ReplayIndex` builds this metadata once from the recording. Spell markers use a changed public
`lastPlay`, King markers use recipient-visible confirmations/ownership, and round/result markers
use structured phase and round data. It does not classify gameplay by guessing from translated
log text. Revision gaps are marked without synthesizing actions. A private enemy King or unseen
hand card cannot be inferred by this index: it has no access to another recipient's recording.

### Score changes

Score changes compares each side's stored live score to the preceding stored frame:

- Unit printed points and pair combo points.
- Formation name, its bonus and the multiplier before/after.
- The extra points caused by multiplication: `(Units + Combos + Formation bonus) * (multiplier - 1)`.
- The unmultiplied seven-nation bonus and aggregate/per-King contributions.
- Live total change and the separate change in banked match total.

The six aggregate component differences reconcile exactly with the live score difference.
Per-King lines explain the Kings aggregate and are not added a second time. The opening frame
shows no fabricated gain. Round boundaries explicitly include clearing, banking and dealing;
their negative live-score changes must not be interpreted as a Spell penalty. Stored replay
frames do not necessarily include the separate final pre-bank snapshot supplied to the live
table, so this view explains snapshot differences rather than reconstructing that missing state.
If revisions are missing, the drawer warns that a difference may cover multiple actions.

### Client responsibilities and verification

`ReplayPlayback` owns pause/seek/round navigation and the immutable `ReplayIndex`.
`ReplayScoreChange` compares stored scores without evaluating game rules or mutating frames.
`ReplayTimeline` and `ReplayDetailsPanel` observe that same playback through `ReplayPanel`;
neither receives a connection. `HistoryFilter` and `HistoryFiltersPanel` filter the authorized
response on the EDT. No core, wire contract, schema or server authorization change is required.

How to Play uses a simpler presentation of this surface: only Next and Details, a compact action
and score explanation, and no journal, search, timeline, round jumps or automatic playback.
Personal replay controls described above remain available in Match history.

Regression tests include `ReplayNavigationTest`, `ReplayScoreChangeTest`, `HistoryFilterTest`,
`ReplayReviewTest` and `HistoryFilteringTest`. The real-server `ReplayJourneySystemTest` follows
Login → History → Replay → seek/journal → Back → filter/clear. It uses a temporary database,
ephemeral loopback port and a real recorded practice game. UI checks write reproducible images
to `target/replay-qa/` at 1280×720 / 1960×1080 (replay) and 1280×660 / 1960×1080 (history).
