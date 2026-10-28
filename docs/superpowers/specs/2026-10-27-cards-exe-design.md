# Cards.exe launcher — design

Date: 2026-10-27
Status: approved and implemented (2026-10-27)

## Intent

Make the Windows game start with one double-click without changing how the project is built.
`Cards.exe` is a thin native launcher that starts a loopback server in the background and then
opens the existing client, using the Java 17+ runtime already installed on the machine.

## Goals

- A small `Cards.exe` (tens of KB) compiled from source kept in the repository; no bundled JRE.
- Double-click starts a local server on `127.0.0.1:5555` when the port is free, waits until it
  accepts connections, then opens the client against it.
- An already-running local server is reused. A server the launcher did not start is never stopped.
- When the last client started through the launcher exits, the launcher-started server is stopped.
- Clear message boxes for a missing/old Java runtime, a missing `target/deck-duel.jar`, and a
  server that fails to start; server output is appended to `data/server.log`.
- No game-code, protocol, Maven dependency, or packaging changes.
- No conflict with the official launcher, updater or release flow: a jar packaged with a
  production server or a release channel is refused.

## Non-goals

- An installer, Start-menu entry, bundled runtime, or a cross-platform executable.
- Building the jar automatically, auto-login, or bypassing the existing startup journey.
- Changing the packaged server endpoint, the client's saved settings, or game behavior.
- Replacing `deck-duel-launcher.jar`, installing updates, or being included in a release package.

## Relationship to the existing launcher and updates

`deck-duel-launcher.jar` remains the official per-user launcher and update helper
(`docs/updates.md`). `Cards.exe` is a Windows development convenience for the repository's
`target/deck-duel.jar` only:

- It passes the documented development route `--host 127.0.0.1 --port 5555`; it never writes
  `~/.deckduel/client.properties`, `%LOCALAPPDATA%\DeckDuel`, or any installation pointer.
- It reads the jar's packaged `deckduel/connection.properties` and `deckduel/update.properties`.
  If `host` or `metadataUrl` is non-empty, the jar is a production or release-channel build and
  `Cards.exe` refuses to run it, pointing at the installed launcher.
- It is not built by `mvn package`, not added to the release zip, and not referenced by
  `tools/build-client-release.sh`.
- Default builds (empty production host and release channel) are the supported case, matching
  `docs/startup-and-updates.md`.

## Constraints

- Windows 10/11. The launcher is compiled with the `csc.exe` (C# 5) shipped with .NET Framework 4.x.
- Java 17 or newer must be on `PATH` or in `JAVA_HOME`.
- `target/deck-duel.jar` must already exist (`mvn package`).
- `Cards.exe` lives at the repository root, next to `target/`, `data/` and `scripts/`.
- The generated exe is build output and is ignored by git, as required by `AGENTS.md`.

## Approaches considered

1. **C# launcher compiled with the bundled `csc.exe` (chosen).** Small, no new dependencies,
   full control over Java discovery, server start-up, readiness wait and cleanup.
2. **Launch4j Maven plugin.** A conventional jar-to-exe wrapper, but adds a build-time plugin
   download and can only start a fixed main class; it cannot manage the server life cycle.
3. **IExpress self-extracting exe.** No code, but extracts to a temp folder, is prone to antivirus
   warnings, and would carry the jar inside the exe; rejected.

## Behavior

### Startup

1. Resolve the repository root from the exe location, the jar at `target/deck-duel.jar`, and the
   data directory at `data/`.
2. Read the jar's `deckduel/connection.properties` `host` and `deckduel/update.properties`
   `metadataUrl`. If either is non-empty, show the "use the installed launcher" dialog and exit.
   Unfiltered Maven placeholders such as `${deckduel.server.host}` count as empty.
3. Find `java.exe`: `JAVA_HOME\bin\java.exe` first, then `PATH`. Parse `java -version` and require
   a major version of 17 or newer; use `javaw.exe` from the same directory for the client.
4. Register a session marker under `data/.cards-launchers/` (under a named mutex), so a concurrent
   launcher cannot stop the server while this one is still opening its client.
5. Probe `127.0.0.1:5555`. Under the same mutex:
   - **Free:** start `java -jar target\deck-duel.jar server --port 5555 --db data\deck-duel.db`
     hidden through `cmd.exe`, with stdout/stderr appended to `data/server.log`. Record the
     `cmd.exe` wrapper PID in `data/.cards-server.pid`. Wait up to 20 seconds for the port,
     polling every 200 ms.
   - **In use:** reuse the server and do not write a marker.
6. Start `javaw -jar target\deck-duel.jar client --host 127.0.0.1 --port 5555` from the repository
   root and wait for it to exit.
7. Cleanup: remove this launcher's session marker. If the marker exists, no other launcher session
   is active, and no other `deck-duel.jar client` process is running, terminate the marked process
   tree and delete the marker. A manually started server has no marker and is left running. Cleanup
   failure never shows a startup-error dialog.

### Failure handling

- Missing or too-old Java: message box naming the requirement.
- Missing jar: message box with the `mvn package` instruction.
- Production or release-channel jar: message box saying `Cards.exe` is for the local development
  build and to use the installed launcher; no server or client starts.
- Server exits or the port never opens within 20 seconds: message box with the last lines of
  `data/server.log`.
- Port held by an unrelated program: treated as a running server; the client reports the
  connection failure, and the launcher leaves that process alone.

## Files

- `launcher-win/CardsLauncher.cs` — launcher source.
- `launcher-win/build-cards-exe.bat` — compiles it to `..\Cards.exe` with `csc.exe`.
- `launcher-win/test-cards-exe.ps1` — integration checks.
- `Cards.exe` — generated at the repository root, ignored by git.
- `data/.cards-launchers/*.session` — runtime launcher session markers, untracked with `data/`.
- `docs/cards-exe.md` — usage and troubleshooting.
- `.gitignore` — ignores the generated `/Cards.exe`.

## Testing

- Build script produces `Cards.exe` from a clean checkout with the jar present.
- Missing Java, missing jar, production-only jar, release-channel-only jar, server exit and an
  already-running server each show the intended result; dialog text is read, not only the title.
- Double-click starts server then client; closing the client removes the server process.
- A second launcher reuses the server; closing the first client leaves the server for the second,
  and the last client closes it. A live sibling session marker keeps the server until it ends.
- A manually started `scripts\run-server.bat` is not stopped by the launcher.
- An unrelated program holding port 5555 is left alone.

## Calls made by the builder (correct these if wrong)

- Dialog text is English, matching the game UI.
- The exe uses the default Windows icon; no icon asset is added.
- The jar is not rebuilt automatically.
- The port is fixed at 5555, matching the existing scripts.
- Cleanup stops the server only when the launcher wrote the PID marker, and only after the last
  client process exits.
