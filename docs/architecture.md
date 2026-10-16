# Architecture — Onitama Online

## Package map

```
onitama.Main              factory dispatcher (server/client/bots/demo), file logging
onitama.core              shared PlayerColor, Square, Offset, Card, CardDeck, Elo,
                          Difficulty, PracticeBot
onitama.core.duel         active 7×7 engine, skill effects, targeting, immutable views,
                          rules snapshot, decks, mana and private Foresight state
onitama.config            RulesConfigLoader, ConfigException, SkillText
onitama.net               sealed Message + version-2 action/view records
onitama.server            GameServer (accept loop), ClientHandler (dispatch table),
                          LobbyManager (room codes), SessionRegistry, MatchSession
                          (per-match executor), MatchResult, MatchPersistence, ServerConfig
onitama.db                Database (monitor-guarded Connection), UserDao/MatchDao
                          interfaces, SqliteUserDao/SqliteMatchDao, PasswordHasher (PBKDF2)
onitama.client            OnitamaClient (reader/writer + heartbeat), ClientSettings
  ├─ state                ClientModel (EDT model), ClientModelListener (Observer),
  │                       Screen, ServerConnection, MessageSender
  ├─ ui                   ClientFrame, LoginPanel, LobbyPanel, GamePanel, BoardPanel,
  │                       CardPanel, LeaderboardPanel, AnimalIcon, CardArt,
  │                       AssetStore, Theme, UiKit
  └─ bot                  LoadTestBot, BotHarness (CSV results)
```

Resources: `src/main/resources/{fonts/outfit-*.ttf, assets/*}`, `scripts/*.sh|bat`, `data/onitama.db`, `results/*.csv`.

## Thread & concurrency

- Accept thread → fixed pool for `ClientHandler` → `MatchSession` owns one `ExecutorService(1)`; match state is thread-confined.
- `SessionRegistry`/`LobbyManager` → `ConcurrentHashMap`. Single-writer per socket.
- `Database` single `Connection` synchronized — SQLite serializes writes by design; not a bottleneck at this scale.

## Gameplay and protocol

All runnable routes (server, Swing client, practice, bots and demo) use
`onitama.core.duel`. The [Step 3 contracts](step-03-engine-contracts.md) define
its state, action, view and targeting APIs. Obsolete 5×5 engine classes and mechanics
have been removed.

Authoritative state: 7×7, six identical pieces per side; both shared decks and
hands; spent skills; mana; turn and phase; completed-turn count; pending choice;
result; per-match rules snapshot and deterministic random state. Server/practice
hosts own this state. Client and bot policies receive only permitted player views.

Keep account, lobby, leaderboard, rematch and heartbeat flows. Gameplay adds
movement, skip movement, cast, resolve private choice and End Turn operations.
After acceptance, send each player their own full view; public fields agree,
private hands/previews differ. The wire filter explicitly permits live DTOs and required collection types;
legacy full-state gameplay messages are not permitted on the wire. Rejection
is atomic; stale/duplicate requests cannot repeat payment, draw or turn advance.

Foresight validates before a private preview and enters a non-cancellable pending
choice. No other gameplay proceeds while pending. Reconnect restores the choice
and phase without rerunning turn start. Ping 30s, timeout 90s, reconnect grace 60s.
Detailed semantics and target protocol requirements are in spec §§2–4.

## Database

Existing SQLite users/matches schema is in spec §5. Keep prepared statements,
monitor-guarded shared Connection, atomic match/rating transaction, PBKDF2 and
zero-sum Elo. Preserve legacy STONE/STREAM records. New games require
ELIMINATION/FORFEIT/DRAW; Step 3 §10 defines additive rules-version and explicit turn-count
columns, historical nulls and the new-row move_count sentinel. Existing `move_count` must not silently change meaning.

## Client UI

Retain login, lobby, leaderboard, practice and rematch. Game screen uses a
7×7 board, a unified bottom hand (movement cards followed by private skills),
public opponent movement faces and identity-free skill backs,
two player crests with mana, phase, two shared deck stacks with public counts,
targeting and End Turn controls. The Move Log panel is removed; the model retains
public history and last-move coordinates for existing consumers and board feedback.
Remove transit/temple/Master presentation. Red flips canonical board coordinates.
Movement offset diagrams may remain compact 5×5 diagrams; they are not the board.

ClientModel consumes permitted views on EDT; panels observe it. Targeting uses
shared constraints with visible data and server validation. Cancel selections
before submission, but not a revealed Foresight choice. Socket writes remain
queued; never block EDT. Use Swing timers for visual effects. Render current
names/costs/rules from match definitions over art. Existing full-face skill PNGs
have baked draft text and are not balance configuration. `SkillCardPanel` samples
only their illustration region, then paints current names, mana costs and rules.
All 24 illustrations and the shared back are bundled under `assets/skills/`.
`CardHandPanel` overlaps cards and raises the hovered card in layer order. Hand
height adapts to smaller windows; board geometry uses actual available bounds
instead of forcing a minimum drawing size that could clip the last rank.
`CardBackPanel` accepts no card identity or definition; only opponent hand count
creates these components. Skill text truncated on the face remains available in
the tooltip.

Accepted event batches are delivered separately from ordinary snapshot/selection
refreshes. `ClientModel` filters stale, wrong-match and duplicate batches before
history and animation; equal-revision events after a reply are still deliverable.
The left rail defaults to a scrollable Played cards timeline above turn actions.
It records only accepted MovementPlayed/SkillCommitted public events, with artwork,
actor and turn, newest first. Local history survives same-match reconnect and resets
for a fresh match; missed plays are not reconstructed from hidden hands. The Card
details tab retains the scrollable inspector. Hovering an
own card or public opponent movement card previews it without changing selection
or sending actions. The last preview stays available for scrolling until the next
hover or selection; changing selection restores the selected own card. Full descriptions and
mana costs use the match rules snapshot. The inspector also highlights turn flow,
shows draft target counts/readiness and browses only the public spent-skill pile.
Opponent skill backs have no inspection handlers or identities. Reference snapshots
are decoration, not additional interactive card components; lifecycle reset clears them.

The hand always reflects the latest permitted view. Departing own cards are painted
as temporary copies above the live hand, with no input-catching child component.
Replacement cards now fly from `DeckStackPanel` to either hand through a full-scene
`DeckDrawOverlay`. `CardDraws` reconciles public events and permitted view changes,
including same-card movement redraw, Refocus's two draws and Foresight resolution
with an empty event list. Own skill faces and public movement faces may reveal;
opponent skill flights contain only the shared back and an anonymous destination slot.
Destination cards are paint-masked during flights but keep their live bounds and
input handling. Generation, match and revision checks guard deferred layout work.
Presentation resets, lifecycle exits, resize and input discard transient flights;
completion never mutates gameplay. Reduced motion propagates to existing cards,
cancels effects and retains static targeting feedback. `PlayerBadgePanel` maps
mana, capacity and public piece counts by viewer color, with local-only reserved
mana feedback. Its abstract crest is rendered with Java2D and uses no portrait asset.

## Configuration boundary

`RulesConfigLoader` loads and validates `.properties` outside core. Pass immutable
validated definitions into the match and send their public presentation data to
clients. Freeze config for the match; file edits affect later matches only.
Step 3 §9 fixes keys, bounds, templates and snapshot semantics; the complete
example is `docs/examples/game-balance.properties`. IDs, effect parameters,
costs/copies, deck and hand constraints require validation.
No runtime JSON dependency is authorized by the artwork manifest.

## Patterns

Strategy (Card offsets + RulesEngine), Observer (ClientModel→panels), sealed Message + handler registry, DAO interfaces, Factory (Main).

## Where agents most often break things

- Off-by-one / rotation sign: Red must negate offsets. Appendix is law.
- EDT violations: touching Swing off EDT or blocking EDT on I/O.
- Forgetting `reset()` after `writeObject` → stale state on receiver.
- Mutating `core` from server/client directly instead of via `RulesEngine`/`GameState`.
- Concurrent map / DB monitor misuse; adding locks inside `core`.
- Elo zero-sum rounding; draw W/L handling; preserve old stored result meanings.
- Confusing completed turns with actions: movement and skill belong to one turn;
  cap is 60 completed player turns, elimination checked before draw.
- Recycling used skills, drawing on skip, or accidentally retaining transit logic.
- Leaking opponent hands/deck order/RNG through snapshots, errors or bot inputs.
- Partial multi-target mutation, replayed requests, or duplicate turn-start mana.
- Treating old green tests as proof that new gameplay exists; replace only the
  obsolete assertions in the implementing migration and preserve other coverage.

## Menu music (implemented)

`client.MenuMusic` loads `/audio/menu-music.wav` using the JDK audio Clip API on
one daemon worker. ClientFrame plays it in login, lobby and leaderboard, pauses
it on the game screen, and closes it on disposal. The existing persisted sound
toggle mutes/resumes it. Audio device failures log once and leave the game usable.
The WAV is converted from the user-supplied `client.mp3`; no runtime decoder or
external player is required. Audio loading and mixer work never run on EDT.

## Review fixes and continuation

See [review fixes and step 5 readiness](handoffs/step-05-readiness.md) for the
regression fixes, validation evidence, cleanup scope and continuation guidance.
Foresight uses a non-modal, non-cancellable preview so resignation stays available;
the panel owns and disposes the preview on terminal views, navigation or disconnect.
Same-match reconnect resends the original unacknowledged action; new matches clear it.

Cleanup before Step 6 removed obsolete panel-level deal/spend/recycle timers.
Card panels own hover/selection rendering; `CardHandPanel` owns outgoing copies
and `DeckDrawOverlay` owns incoming flights. Menu resignation uses the live
recipient view, and Help renders current balance values.

The game and lobby share `assets/cinematic-valley.png`, a colorful painted
mountain landscape. Jade surfaces/buttons, ivory captions, coral danger accents
and gold actions use `Theme`; small caption plates preserve contrast without
bleaching the background. The previous `menu-background.png` is retained as source art.

The board is drawn directly in Swing as ivory/jade ceramic tiles inside a
lacquered jade frame with brass inlay. Decorative brackets stay outside the
7×7 playable grid; board geometry, rotation, hit testing and targeting are unchanged.

## In-match chat

ChatRequest/ChatMessage extend the version-2 wire allowlist. ClientHandler routes
requests to MatchSession's executor, which checks current membership, match ID,
live/unpaused status, text bounds and a per-player 500 ms cooldown before echoing
the authenticated username and text to both players. No gameplay state or action
sequence changes. ClientModel retains at most 200 messages for the current match
and notifies ChatPanel through onChatChanged on the EDT. The right sidebar uses
plain-text Swing components; Enter/Send queues through the existing connection.
Same-match reconnect keeps local history; new matches clear it. There is no chat
persistence or missed-message replay. Practice chat is disabled.

Chat now uses a rounded jade conversation surface, a live/practice/offline status,
plain-text message bubbles distinguished by sender, and an ivory message composer.
Blank/invalid messages disable Send; Enter and server validation remain unchanged.

## Animated valley menu

Login and lobby inherit ValleyMenuPanel, which plays the selected bundled
sixteen-second menu GIF through the JDK's native animated-image decoder. MenuTheme
maps the two scene choices (Mountain valley and Golden fields) to their clip/still
resources. ClientSettings persists menuTheme, defaulting to Golden fields for absent or
unknown values. Login and lobby share that settings instance.

Clips and first frames load once per theme on a worker and are cached. Painting
scales the frame uniformly to cover the window, anchored left. Theme changes
request the new clip without blocking the EDT. Reduced motion shows its first
frame. Hidden/inactive menus and callbacks from a previously selected clip decline
further image-observer updates. No landscape mesh deformation runs in the client.

Both clips are authored offline with 400 frames at an encoded 25 fps and infinite
looping. Valley layers use rigid branch rotation, drifting clouds, leaves and
waterfall highlights. Golden fields uses a clean battlefield plate, isolated torn
red flag cloth with a pinned attachment edge, two rotating windmill sail assemblies,
drifting clouds and seeds, and five faint travelling wind wisps. The added
foreground grass layer was removed after visual feedback; the painted meadow remains. The
scene's poles, buildings and ground stay fixed. Tools RenderValleyLoop.java and
RenderBattlefieldLoop.java render the respective frames. MP4 previews live under
artifacts/. No runtime video library or additional dependency is required. Encoded
frame rate is not a guarantee of sustained native decoder/full-window performance.
The lobby title sticker and navigation wordmark are removed; login says Welcome
and the native window/error title is Grid Duel. Onitama remains only as attribution
in About and internal package/resource names.

## Compact menu navigation

LobbyPanel shows a compact column of unboxed text actions directly over the
animated scenery. Home contains Play, Settings and Quit game, a small
account caption and leaderboard link, and Resume match when a match is parked.
A small Change theme button opens a focused Menu theme page with Mountain valley
and Golden fields choices. Selection previews immediately, marks the current scene,
and saves using the existing settings worker without network traffic.
Play opens Create match, Join a game and Practice; each uses a focused page with
Back navigation. Creation retains its pending code across page changes and does
not submit another create request when returning. The room browser uses typed
MatchSummary values and polls only while its page is showing. Its empty scroll
area collapses completely when no rooms exist. Room rows use translucent dark
surfaces, ivory text and gold selection; the viewport stays transparent. The room
code input has a matching translucent fill, gold focus underline, ivory entry text
and a placeholder, with a named accessible label and the same Enter/validation rules. Practice keeps
Rookie/Senior/Legend choices. Settings owns the three piece styles; updates apply
immediately and persistence runs on a worker. ClientSettings.save serializes file
writes against the frame's preference saves. Quit delegates to closeAndSave.

The cream panel, button fills, borders and navigation icons are removed. Warm ivory
labels, champagne Play text and pale gold hover/selected underlines work across
both scenes. A soft right-edge dark scrim and text shadows preserve legibility
without a boxed surface. The account caption and leaderboard stack to avoid
truncation. Focus and selected states stay visible; menu pages retain their routing.
The earlier procedural mist/leaves and canopy mesh have been superseded by the
prerecorded clip described above. Only media playback runs in the menu.
