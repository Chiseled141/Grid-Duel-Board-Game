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
two player crests with mana, phase, two unlabeled decorative draw origins,
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
The left rail holds turn actions above a hand-sized neutral draw pile; the public
timeline panel is removed. Accepted public history remains in ClientModel. Same-match reconnect preserves local
history; a new match clears it. The Card details tab and inspector are removed.
Live card tooltips still use the match rules snapshot, and targeting guidance lives
on the board/Cast Skill tooltips. Opponent skills remain identity-free backs.

GamePanel loads a dedicated static medieval strategy-room backdrop asynchronously,
while login/lobby retain the animated battlefield. It receives shared ClientSettings
from ClientFrame. Warm ivory/olive board squares and a
bronze frame fit both backgrounds. Sidebars use translucent dark ink, and actions
use unboxed ivory text with gold focus/hover/selection. Skip Movement, End Turn
and Cast Skill retain the previous UiKit pill styling and its distinct states. Player colors and board
highlights remain distinct. The VS, Your hand and bottom instruction banners are
removed. Narrower rails and removed banners free space for a larger board and taller,
wider hand cards; public opponent movement cards use an enlarged 0.48 scale. Skill art takes less space;
live descriptions use larger type and more vertical room. Responsive fallback
retains hover-to-front on smaller windows. Cards never cover the board.
The hand reserves 24 fewer pixels above its cards, giving that height to the
board while preserving card-face and deck sizes.

Movement and skill draws use a neutral decorative pile on the drawing player's
side: lower-left locally, right of the opponent's top hand for their draws.
No deck titles/counts are painted; this only changes presentation. The engine and
protocol still own two distinct decks, including their original public counts.

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
bleaching the background. Obsolete menu-background, empty-board, master-piece and synthetic-grass images
were removed; active stills, GIFs, student fallbacks and compositor sources remain.

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

## Medieval battlefield presentation

Only Golden fields remains for menus. Login and lobby inherit BattlefieldMenuPanel,
which asynchronously loads the bundled sixteen-second 400-frame GIF and caches its
first frame for reduced motion. Unknown/retired menu preferences map to Golden
fields. The old valley scene, renderer and music are removed. Golden fields music
retains its non-blocking audio worker and persisted sound toggle.

The offline battlefield compositor retains flower sway, windmill rotors, the main flag,
clouds, seeds and wind wisps. Flower cutouts also cover the center and right meadow;
the original left-side blooms keep their existing sway strength and timing.
The small flag cloths and the subsequently added faded linen strip are removed.
The nearby weathered pole remains bare. The main mast is lightly weathered.
Output remains 1280x720 at encoded 25 fps. This encoding is
not a promise of sustained full-window decoder performance.

CardArt supplies printed European woodcut-style faces and neutral backs. Movement
cards render actual offsets in the viewing player's board orientation; IDs/rules
remain canonical while visible titles use tactical aliases. Own and public enemy
cards, draw flights and played-card snapshots share these faces. Skill
artwork lives in assets/medieval/skills; title/cost/category are rendered from live
rules, while full descriptions appear only through CardDetails hover/click popups.
Backs receive no hidden identity. Category color and ornament both convey type.

The board uses an oak/brass frame and limestone/slate tiles. MedievalPieces renders
steel helms, heraldic shields or carved pawns for the existing style preference.
All six pieces on each side remain identical in gameplay. See
handoffs/medieval-reskin.md for authored assets, cleanup and verification.

## Compact menu navigation

ClientFrame has no Game/Help menu bar or header strip. A transparent music icon
floats at the upper right of menu screens, without reserving layout height.
How to Play and About sit below the account and Leaderboard links; their existing
dialogs remain. Reduced motion is available in Settings, and Quit retains the
normal save-and-close path.

LobbyPanel shows a compact column of unboxed text actions directly over the
animated scenery. Home contains Play, Settings and Quit game, a small
account caption and leaderboard link, and Resume match when a match is parked.
Only the medieval battlefield remains; the retired theme picker is removed.
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
the battlefield scene. A soft right-edge dark scrim and text shadows preserve legibility
without a boxed surface. The account caption and leaderboard stack to avoid
truncation. Focus and selected states stay visible; menu pages retain their routing.
The earlier procedural mist/leaves and canopy mesh have been superseded by the
prerecorded clip described above. Only media playback runs in the menu.

### Physical hand interaction and scene music

GamePanel binds own cards to CardPlayOverlay without legacy click callbacks. An upward
left-button drag of at least38px selects on release. ClientSettings persists
clickToSelectCards, default true: Click or slide up permits a left click on release
as well; Slide up only requires the threshold. Short drags return over200ms.
The floating Settings page exposes both options; both movement and skill bindings
read the shared preference at gesture time. The hand reserves60px above its cards. Selected cards
stay raised while targeting and awaiting acceptance. Esc clears targeting and
returns the card. Tapping a selected card (at its raised position) or sliding it
down deselects it; selecting another card switches directly in either input mode.
Live rule details are available on hand cards and both public played-card areas.
Played-card details travel with accepted animation entries and clear on match reset.
Menu and Surrender use the same pill controls as the turn actions.
Accepted public MovementPlayed/SkillCommitted events queue540ms flights to
PlayedCardArea immediately left (own) and right (opponent) of the board. These
are latest-play visual references, not authoritative discard piles. Opponent
skills start concealed and reveal only the publicly committed face. Input is
blocked during confirmed flights; duplicate/stale events are filtered by the
model. Hand reconciliation and reflow retain all existing deck rules. Lifecycle
changes clear visual masks/timers; reduced motion snaps decoration.

MenuMusic loads the remaining Golden fields PCM WAV track on its audio worker.
Screen changes pause music in matches; the saved sound toggle remains. The old
valley music is removed. No external decoder or dependency is required.

### Match correspondence and heraldic player slips

The right rail uses opaque parchment slips with thin brass borders instead of
translucent glass. Player names occupy the full width with compact heraldic
shields, explicit piece/status labels and segmented mana; held Foresight mana
remains visible. Turn/phase and the completed-turn counter share the top header.
Chat uses plain-text journal entries, an ivory composer and a matching send pill.
The panel is bounded at 300px; practice shows a compact 122px multiplayer notice
and hides the inactive composer. Switching back to multiplayer restores it.
A How to Play pill at the top-left of the match opens the same live-rule help
as the main menu, preserving local selection and all gameplay state.

The opponent slip now sits directly below How to Play in the left rail in both
practice and multiplayer. Help uses parchment fill, brass border and dark lettering.
The right rail retains turn status, correspondence and the local player's slip.
Draw replacements travel over 600ms with smooth acceleration/deceleration (formerly
420ms with a fast initial ease-out); reveal/conceal rules and reduced motion remain.

### Leaderboard and skill-only public displays

LeaderboardPanel shares BattlefieldMenuPanel and ClientSettings with the main
menu. Its parchment ledger uses brass rules, serif titles, literal player names,
Elo/wins/losses and a highlighted local row. Empty/populated transitions retain
the same sheet. ClientModel.requestLeaderboard routes to Screen.LEADERBOARD so
visibility, animation and reduced-motion gates follow the actual screen.
Only accepted SkillCommitted events enter the two skill-play areas. Movement
keeps its board animation/last-position feedback and replacement draws, without
a side-card flight or replacing the previous skill. Skill hover details remain.

### Bot skill targeting and menu identity

PracticeBot follows shared SelectionOptions recursively for ordered pieces,
destinations and directions, including multi-piece/group skills and optional
extra targets. Per-card search is bounded to 192 drafts/24 complete choices and
uses only permitted PlayerView information. Rookie varies legal casts; stronger
levels favor eliminations and fuller multi-target effects. Unaffordable/untargetable
skills are retained; Mana Surge is saved at full mana. Foresight still resolves
its committed private preview. Practice and load bots use the same policy.
Home places a 21pt serif username below Play/Settings/Quit, with a separate small
Elo line, followed by Leaderboard and How to Play. Resume remains available below
the footer when a match is parked.

### Opponent draw origin

The opponent top row is ordered hidden skill backs, public movement cards, then
an identity-free decorative deck at the right, sized like the opponent cards.
Opponent replacement flights (movement and concealed skills, including Foresight)
start from that deck; local draws keep their original lower-left pile and hand.
These are two visual origins, not extra gameplay decks; shared authoritative deck
rules and counts are unchanged. Both sources use live bounds and existing timing.

### Visible piece travel

Accepted piece moves render as 480ms smooth slides with a small lift and shadow,
using viewer-relative coordinates for both sides and simultaneous skill moves.
Turn-only, mana-only and other non-board updates no longer truncate an active
slide. New board-changing events replace old motion; explicit input, resets,
disposal and reduced motion still cancel/snap transient visuals. State remains
authoritative immediately; animation completion never submits a gameplay action.

### Rapid-opponent animation sequencing

GamePanel queues accepted visual batches rather than canceling the previous
piece/card/draw effects on each accepted action. A Swing timer waits for the
longest movement/capture, skill flight or staggered draw in the current batch.
Rendering uses that batch's permitted snapshot; ClientModel adopts the newest
state immediately. This also keeps delayed draw faces and board markers matched
to the displayed action. Gameplay input waits until the visual backlog drains.
Pause, reconnect/new match, disconnect, resize, leaving, disposal and reduced
motion clear the queue and transient effects. Bot action timing and networking
are unchanged. Terminal game-over snapshots retain the existing immediate snap.

### Objective rules (grid-duel-v3, protocol 4)

Seven pieces fill each home row. Capture points and center-turn rewards are stored
in GameState and projected publicly, with the immutable configurable score target
(default 8). Two mirrored gold-highlighted scoring squares on y=3 each award +1 score at own-turn start;
one blue-highlighted mana square on y=2 and its mirrored partner on y=4 each award +1 capped
mana at that boundary. The server generates the layout once from match RNG and
includes it in every view; bots and rendering use the same immutable layout. Landing alone gives nothing.
Elimination wins before score; the turn-limit draw starts no further rewarded turn.
Movement/skill resolution awards each eliminated enemy once, after full validation.
Player badges display scores; translucent gold/blue tile highlights and hover details identify the objectives. The stone texture remains visible; there are no medallions or text on the tiles.
Bots value center defense and useful mana occupation without hidden-state access.
Tiger/Lance has forward range 3; Frog/Rabbit flank jumps have sideways range 3.
Their relative diagrams expand to 7×7, while other patterns stay at 5×5.

### Match loading and entry

Login and the main menu open directly. When ClientModel enters GAME (practice,
online, rematch or reconnect), ClientFrame displays LoadingPanel until the
player presses Enter Match. Loading uses the actual match's immutable rules
snapshot. A SwingWorker prepares the three approved scenes (castle passage,
campaign map, fortress courtyard), match backdrop, movement/skill faces and piece
styles. The bottom percentage counts completed preparation steps, without a fake
wait. The button appears only after completion. An error offers Retry.

A random scene loads first; visible scenes rotate every nine seconds and hints
every seven. Font-metric wrapping keeps all hint words visible. Reduced motion
keeps the chosen scene still. MenuMusic continues through the loading screen and
pauses only when Enter Match reveals the board; the floating mute icon remains
available during loading. The workshop preview is not bundled.

The entry screen is a presentation gate, not a server phase. Live network updates
continue to update the match model. Local practice bot turns are held until entry;
match result dialogs are deferred until the board is shown. Leaving/disconnecting
cancels preparation and timers; stale callbacks cannot enter another screen.

Return to Match bypasses loading via the explicit onMatchResumed observer callback;
other observers receive the normal GAME notification through its default method.
Fresh practice/online matches and rematches retain their loading screen.
