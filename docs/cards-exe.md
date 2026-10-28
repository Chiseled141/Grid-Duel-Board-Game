# Cards.exe local launcher

`Cards.exe` is a small Windows launcher for this repository's **local development build**. One
double-click starts the loopback development server when port 5555 is free and then opens the
client against it. It is not an installer, it does not update the game, and it is not part of a
release package.

## Relationship to the official launcher

`deck-duel-launcher.jar` remains the official per-user launcher and update helper
(`docs/updates.md`). `Cards.exe` only runs `target/deck-duel.jar` from this repository:

- It passes the documented development route `--host 127.0.0.1 --port 5555`
  (`docs/connections.md`); it never writes `~/.deckduel/client.properties` or
  `%LOCALAPPDATA%\DeckDuel`.
- It reads the jar's `deckduel/connection.properties` (`host`) and `deckduel/update.properties`
  (`metadataUrl`). If either is non-empty, the jar was built for a production server or a release
  channel and `Cards.exe` refuses to run it, pointing at the installed launcher.
- It is not built by `mvn package`, not included in release zips, and `Cards.exe` is ignored by
  git.

## Prerequisites

- Windows 10 or 11.
- Java 17 or newer on `PATH` or in `JAVA_HOME`. The project targets Java 17; the checks below ran
  with Oracle JDK 26.0.1.
- `target\deck-duel.jar` already built with `mvn package`.
- The default local build (empty production host and release channel), as described in
  `docs/startup-and-updates.md`.

## Build

```bat
launcher-win\build-cards-exe.bat
```

The script uses the `csc.exe` shipped with .NET Framework 4.x and writes `Cards.exe` at the
repository root. Rebuild it only when `launcher-win\CardsLauncher.cs` changes; a normal
`mvn package` is enough after game changes because the exe only starts the jar.

## Use

Double-click `Cards.exe`. It:

1. Checks the jar and refuses production/release-channel builds.
2. Finds Java 17+ and starts `java -jar target\deck-duel.jar server --port 5555
   --db data\deck-duel.db` hidden, unless port 5555 already accepts connections.
3. Waits up to 20 seconds for the server, then opens `javaw ... client --host 127.0.0.1
   --port 5555`.
4. When no other launcher session or client remains, stops the server it started.

Running `Cards.exe` twice opens two clients against the same local server, which is useful for a
local two-player match. Closing the first client leaves the server for the second; closing the last
one stops it. Short-lived session markers in `data\.cards-launchers\` let two launchers coordinate,
so one cannot stop the server while the other is still opening its client. A server started by
`scripts\run-server.bat` or by hand has no launcher marker and is never stopped by `Cards.exe`.

## Files and logs

| File | Purpose |
|---|---|
| `target\deck-duel.jar` | The game the launcher starts; build with `mvn package`. |
| `data\deck-duel.db` | Local development accounts, history and replays. |
| `data\server.log` | Appended server output; the error dialog shows its last lines. |
| `data\.cards-server.pid` | Internal marker naming the launcher's `cmd.exe` wrapper, whose child is the server process. |
| `data\.cards-launchers\*.session` | Internal per-launcher session markers used to protect a shared server; removed on exit. |

The launcher does not write the client's saved host/port, the official installation folders or the
update settings. The client itself still saves its own window, music and input preferences as usual.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Java 17 or newer is required" | No Java 17+ was found. Install a JDK 17+ and set `JAVA_HOME`, or put its `bin` on `PATH`. Java 8 is skipped. |
| "Deck Duel is not built" | `target\deck-duel.jar` is missing. Run `mvn package` in the repository root. |
| "packaged for a production server or a release channel" | The jar was built with `deckduel.server.host` or `deckduel.update.url`. Use the installed Deck Duel launcher instead; `Cards.exe` is for the default local build. |
| Client shows "Connection failed" while 5555 is in use | Another program owns port 5555. The launcher treated it as a server and left it alone; close that program or use the server it provides. |
| "The local server exited" or the 20-second timeout | Read the last lines in `data\server.log`; the dialog also shows them. Check Java, the database file and port 5555. |
| The server keeps running after the client closes | It was not started by `Cards.exe` (no marker), or another client is still open. Stop a manual server with its own console or `taskkill`. |

## Checks

```powershell
powershell -ExecutionPolicy Bypass -File launcher-win\test-cards-exe.ps1
```

The script stops only Deck Duel client/server processes running this checkout's
`target\deck-duel.jar` before and after the run; it does not touch another installation. It checks
a fresh launch, the two-client life cycle, a manually started server, an unrelated port owner, a
missing jar, Java 8 only, Java 8 before Java 17 on `PATH`, production-only and release-channel-only
jars, missing Java, a live sibling launcher session, and a server that exits during start-up.
Dialog checks read the real message text, not only the window title. When the optional Java 8
runtime is absent, the two Java 8 sections are reported as skipped.

Latest run on 2026-10-27: **56 checks, all PASS, exit 0** (Windows, Oracle JDK 26.0.1, Maven
3.10.0, `target\deck-duel.jar` 139,511,004 bytes, `Cards.exe` 14,848 bytes).

The launcher changes no Java sources, protocol messages, Maven configuration or release tooling.
The project's regular checks remain `mvn test` and `mvn clean verify`.

Design: `docs/superpowers/specs/2026-10-27-cards-exe-design.md`.
Plan: `docs/superpowers/plans/2026-10-27-cards-exe.md`.
