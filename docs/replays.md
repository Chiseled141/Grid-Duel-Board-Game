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
- Jumping to a round or seeking with a slider: playback steps one action at a time.
- Re-reading old frames after an incompatible change to the `core.card` records: such replays
  are refused, not migrated.
- A retention limit: online replays grow without bound.
