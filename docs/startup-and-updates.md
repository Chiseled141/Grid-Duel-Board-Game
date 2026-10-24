# Startup, updates and offline play

The client's journey is **Launch → Check for updates → Connect to server → Login → Main menu**.
This page covers the startup screen, offline play and the session rules around updates. The
updater itself (signed metadata, downloads, installation layout, helper process, release tooling)
is described in `docs/updates.md`.

## What is implemented

| Part | Where | Notes |
|---|---|---|
| Startup screen | `client/ui/StartupPanel` (Swing, `Screen.STARTUP`) | Iron plate on the animated menu valley, like the login card; menu music plays as on the other menus. It only renders states and forwards button presses. |
| Update steps | `client/startup/UpdateFlow` | Check → up to date / not configured / check failed / optional / required → installing (progress) → install failed / installed. Shared by the startup screen and Settings. |
| Startup journey | `client/startup/StartupFlow` | Runs `UpdateFlow`, then connects; Retry, Continue, Update, Play offline. Blocks entry while a required update is not installed. |
| Service seams | `UpdateService`, `UpdateInstaller`, `UpdateRestarter`, `ServerConnector`, `OfflineLauncher` | Blocking calls run on the `deckduel-startup` worker thread. `client/update/ReleaseChannel` supplies the real implementations (`HttpUpdateService`, `StagedInstaller`, `HelperRestarter`) when a release channel is packaged. |
| Installed version | `InstalledVersion` | Read from `deckduel/version.properties`, which Maven fills from `deckduel.version` (the pom version unless a release build overrides it). |
| Finishing an update | `client/update/UpdateFinisher` | Holds new matches while an update downloads or waits, closes a waiting room, and once the update is verified hands over to the helper and closes the game, but only when no match is in progress. |
| Server route | `ServerRoute`, `Endpoints` | Development overrides, then the packaged production server (TLS), then the saved setting or the local development server 127.0.0.1:5555 (`docs/connections.md`). |
| Offline play | `client/OfflineServer` | After a failed connection, **Play offline** starts the ordinary `GameServer` inside the client (loopback only, temporary database), registers the account `offline` with a random password for that launch, and opens the main menu, labelled "Offline play". The Play page hides Create match and Join a game, keeps Practice and Match history, and says that history and replays last only for this session. Closing the client stops the server and deletes its folder. |
| Settings | `LobbyPanel` Settings page, UPDATES section | Check for updates, the installed version, the result, and Install update / Retry update while a release is offered; a verified update restarts the game once no match is in progress. |
| Required update after login | `ClientModel.requireUpdate` | A required release found later (from Settings) is remembered for the session. A match in progress continues untouched; Create match, Join a game, Practice and Rematch are refused with "Version X is required…", and the Play page says so. Match history and replays stay available. A waiting room is closed with `CancelRoomRequest` (protocol 15), including a room whose creation was already on its way. |
| Installing after login | `ClientModel.setUpdating` | While an update downloads, verifies or waits to restart, new matches and rematches are refused the same way and a waiting room is closed. A failed install allows matches again. |
| Fixtures | `StartupFixture` | Controlled local stand-ins that show each state on demand (below). |

### States and buttons

| State | Buttons |
|---|---|
| Checking for updates | none |
| Up to date | none; connects after 0.7 s |
| Updates not set up (no service) | none; says nothing was checked, then connects |
| Update check failed | Retry, Continue |
| Optional update available | Update, Continue |
| Required update | Update, Quit (no Continue) |
| Downloading and verifying (percentage and bar) | none |
| Update failed | Retry, plus Continue (optional) or Quit (required) |
| Restarting (installed copy) | none; the game closes, the helper installs and the game starts again |
| Update installed (fixtures only) | Quit |
| Connecting | none |
| Connection failed (including certificate problems, explained) | Retry, Play offline, Quit |
| Server requires a newer client | the update step again, without Continue; if no update is offered, a failure message with Retry |
| Starting offline play / Offline play | none; opens the main menu |

The gate before login is unchanged: a required update found at launch still blocks login.

Once the client has connected, the login screen opens. Login and registration reuse that
connection. If the connection drops before login, the login form reconnects as before.

### Threads

`StartupFlow` and `UpdateFlow` belong to the Swing EDT, as `ClientModel` does: actions and
listener callbacks happen there. Service calls, the settle pause and offline server startup run on
one daemon worker that `ClientFrame` owns. Results come back through `SwingUtilities.invokeLater`.
Work handed over after the window closes is discarded. An action that does not fit the
current state is ignored, so a double click never starts a second check, install or connection.

## Seeing each state locally

This repository's default build packages no release channel, so a normal launch shows **Updates
not set up** and then connects to the local development server:

```sh
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
```

Without the server running, the client shows **Connection failed**, with Retry and Play offline.

A real update between two local versions is shown by `tools/update-demo.sh` (`docs/updates.md`).

`--startup-fixture <name>` replaces the update services with a labelled fixture. The screen and
Settings say "Test fixture". Fixtures contact nothing and install nothing: offered versions are
made up (`1.1.0-fixture`, `2.0.0-fixture`), and an install only waits while it reports progress.

| Fixture | Shows |
|---|---|
| `up-to-date` | Checking → Up to date → Connecting |
| `optional-update` | Update available → Update (progress → installed) or Continue |
| `required-update` | Update required; Continue never appears |
| `install-fails` | Required update whose first install fails at 55%; Retry succeeds |
| `check-fails` | First check fails; Retry → Up to date |
| `connection-fails` | First connection attempt fails without dialling; Retry dials the real server |

## Open items

- **Release channel.** No bucket or endpoint is configured, so default builds report "Updates not
  set up". The real check, download, verification, installation and restart exist and are tested
  locally (`docs/updates.md`).
- **Production server.** Builds may package a production endpoint (always TLS; `docs/connections.md`);
  without one the client uses the saved setting or the local development server. The operator
  supplies the hostname and certificate (`docs/deployment.md`).
- **Policy choices to confirm:** whether an unreachable update service should allow Continue (it
  does now, because the server's protocol handshake still rejects incompatible clients), and
  whether Settings should push a player out when a required update appears mid-session (it only
  blocks the next match now).
- **Offline play limits:** the offline account, its history and its replays are discarded when the
  client closes, on purpose for now.
- **Room cancellation race:** a join that the server handles before the cancellation arrives still
  starts a match; that match is then played to the end, like any match in progress.

## Deployment boundaries

- **Client updates** download from a static HTTPS location such as an S3 bucket and restart only
  that player's application. They never require restarting the multiplayer server. The updater is
  implemented (`docs/updates.md`); no bucket, upload or signing key exists yet.
- **Server restarts lose active matches.** Every `MatchSession`, held seat and reconnect grace timer
  lives in memory, so restarting the server process ends every match in progress. Those matches get
  no result row, and their replays stay unfinished. Drain mode stops new matches, lets existing ones
  finish, and `deckduel-switch-release` restarts only when that is safe (`docs/connections.md`,
  `docs/deployment.md`). Zero-downtime server updates are not supported.
