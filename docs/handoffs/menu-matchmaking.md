# Menu refinement and Find match

Plan: preserve the menu art and Settings; compact Play/Practice/Join/Create
where needed using described action rows. Add authenticated, cancellable FIFO
matchmaking to the local server, sharing lobby admission synchronization with
room creation/join. Client model owns search status on EDT; UI observes it.
No gameplay, balance, DB schema or new dependency changes.

Acceptance: distinct waiting accounts match once; duplicate search is idempotent;
cancel/disconnect/account replacement remove waiting entries; room admission
cannot race queue admission; active/parked games cannot search. Existing loading
screen handles initial MatchSync normally. Wire filter, socket lifecycle, model
and UI interaction coverage plus full native tests and independent review.

Latest home-menu request: Play / Settings / Leaderboard / Quit game; removed
How to Play and About from home, retained in-game Help. Account name floats
beside the sound icon without a header bar; Elo remains in its tooltip.

Independent review identified an auth/pairing race. Search now cancels before
authentication DB work, and identity replacement detaches a reserved session
through its disconnect/forfeit path. Old session broadcasts/token cleanup are
restricted to handlers still owned by that session. Latch-controlled tests
cover both queue cancellation during blocked login and a room claimed during
blocked login; a timed forfeit test verifies new match/token isolation.

Validation: final native mvn test, independent read-only recheck plus native
mvn test, and final mvn clean verify each passed 559 tests with zero failures,
errors or skips. Inspected rendered Home/Play/Practice/Create/Join/Find pages.
Updated server and client restarted on localhost:5555 with existing DB intact.

## Leaderboard, account header and exit follow-up

Leaderboard now uses the menu's cream Outfit typography and charcoal rows,
with gold leading ranks, a marked local row, bold Elo, themed scrolling and a
matching empty state. The account header shows Elo beside the username.
Quit opens the existing themed confirmation; its negative/ESC path keeps the
menu open. Native tests exercise both negative and positive quit choices while
using an injected close callback, so they never exit the test process.

## Shared menu navigation follow-up

Home no longer draws a flag/banner or underline. One active action uses brighter
gold and larger bold Outfit type. Play/Practice cards use a gold fill for the
active action, with dark titles and descriptions. Mouse, focus and scoped
arrow/WASD bindings share one selection; Enter activates and Escape returns
from subpages. Disabled/hidden actions are skipped; room-code editing and
Settings preferences retain their normal input. Petals follow actual text
fonts without the old horizontal hover offset.

Navigation validation: full native mvn test, independent read-only review with
native mvn test, and final mvn clean verify each passed 561 tests, zero failures,
errors or skips. Inspected home, Play and Practice previews, including dark
route descriptions on gold selection. Updated client reopened against the
unchanged local server.

Arrow-decoration follow-up: removed route chevrons and the Back label's leading
arrow; selection color and weight remain. Escape uses the plain Back label.
Settings radio marks are preserved. Full native test and independent review each
passed 561 tests with no failures/errors/skips; inspected Practice preview.

## Room readability and return keys

Create places the code and waiting status on a dark outlined surface; Copy code
uses a cream action. Join darkens the code field and groups the open-room browser
on the same dark surface, with readable Join and Refresh actions. Disabled
actions use .75 opacity rather than .45 so their text remains legible.
Escape/Backspace return from submenu pages and Leaderboard; room-code editing
keeps the native focused Backspace deletion binding. Non-navigation pages
request an appropriate focus target after display to keep ancestor bindings live.

Room-screen validation: full native mvn test, independent read-only review with
native mvn test and final mvn clean verify each passed 562 tests, zero failures,
errors or skips. Inspected Create/Join previews; refreshed the local client.
