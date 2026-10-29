# Windows installer

`DeckDuel-Setup-<version>.exe` is what Windows players download and run once. It needs no Java
installation and no administrator rights. After installing, players start Deck Duel from the Start
menu (or the optional desktop shortcut). Updates then arrive through the automatic updater
(`docs/updates.md`), and the game connects to the production server packaged into the build
(`docs/connections.md`).

## Parts

| Part | File | Responsibility |
|---|---|---|
| Build script | `tools/build-windows-installer.ps1` | Run on Windows x64. Builds and tests a copy of the repository with the server address and update channel packaged in, makes the update package, links the Java runtime, checks that runtime against the package, and compiles the installer. Nothing is uploaded or signed. |
| Runtime modules | `deploy/windows/runtime-modules.txt` | The Java modules `jlink` puts in the bundled runtime: all of Java SE plus the `jdk.*` modules JavaFX, Swing interop and TLS need. Modules that the linking JDK lacks are skipped. |
| Installer script | `deploy/windows/DeckDuel.iss` (Inno Setup 6.3+) | Per-user install, shortcuts, the setup step, upgrade and uninstall. Its `AppId` must never change. |
| Icon | `deploy/windows/deck-duel.ico` | The compass emblem from the card back (`assets/cards/card-back.png`), 16–256 px. |
| Setup step | `launcher.InstallLayout.setUp`, `deck-duel-launcher.jar setup` | Installs and activates the bundled package, unless a newer version that still verifies is already active. A rerun repairs a damaged pointer or version folder. |

## What gets installed

```text
%LOCALAPPDATA%\Programs\Deck Duel\        the installer's program folder
    runtime\                              bundled Java runtime (java.exe, javaw.exe, cacerts)
    deck-duel-launcher.jar                used once, for the setup step
    deck-duel-<version>-windows-x64.zip   the update package for this version
    deck-duel.ico
%LOCALAPPDATA%\DeckDuel\                  the updater's installation (docs/updates.md)
    launcher\deck-duel-launcher.jar       what the shortcuts start
    versions\<version>\, current.properties, staging\, logs\
%USERPROFILE%\.deckduel\                  preferences (unchanged by installing, updating or uninstalling)
```

The flow:

1. **Install.** Inno Setup copies the program folder, then runs
   `runtime\bin\java.exe -jar deck-duel-launcher.jar setup --root %LOCALAPPDATA%\DeckDuel --package <zip>`
   with no window. If it fails, the installer names `%LOCALAPPDATA%\DeckDuel\logs\launcher.log`
   and does not offer to start the game.
2. **Start.** The shortcut runs
   `runtime\bin\javaw.exe -jar %LOCALAPPDATA%\DeckDuel\launcher\deck-duel-launcher.jar launch --root %LOCALAPPDATA%\DeckDuel`.
   The launcher starts the active version with the same runtime and exits. The game's output goes to
   `logs\client.log`.
3. **Update.** The game downloads, verifies and stages a new version, then the helper activates it
   after the game closes (`docs/updates.md`). The helper also refreshes the launcher copy the
   shortcuts start. Both use the bundled runtime.
4. **Reinstall or upgrade.** A newer installer upgrades the same installation (same `AppId`) and
   replaces the whole runtime. Running an older installer never downgrades: if the updater has
   already activated a newer version, the setup step keeps it. A reinstall of the same version
   repairs damaged files.
5. **Uninstall.** Settings → Apps removes the program folder and `%LOCALAPPDATA%\DeckDuel`
   (installed versions, downloads, logs). Preferences in `%USERPROFILE%\.deckduel` stay.
   Close the game first.

Why the shortcuts start `javaw.exe` and not a jpackage launcher: the jpackage Windows launcher
can run the application inside a Windows job object that ends child processes when the
launcher exits. The Deck Duel launcher exits after starting the game, and the game starts the
update helper, so both would be at risk. Inno Setup is used because `jpackage` installers also
need the WiX toolset.

## Building

**Run on Windows x64**, once per release, from the repository:

Prerequisites (install once):

- A JDK for Windows x64. Temurin 21 is recommended (the client targets Java 17, and the
  project's Windows tests ran on Temurin 21.0.10). Its `jlink` makes the bundled runtime, so the
  runtime players get is that Java version.
- Maven on `PATH`.
- Inno Setup 6.3 or newer: `winget install JRSoftware.InnoSetup`.
- The release public key file from `release keygen` (`docs/updates.md`). The private key is not
  needed here.

```powershell
powershell -ExecutionPolicy Bypass -File tools\build-windows-installer.ps1 -Version <VERSION> `
    -ServerHost <GAME_HOST> -ServerPort <GAME_PORT> `
    -UpdateUrl https://<RELEASE_BUCKET>.s3.<REGION>.amazonaws.com/<CHANNEL>/latest.properties `
    -PublicKeyFile <path>\release.pub -JdkHome 'C:\Program Files\Eclipse Adoptium\<jdk-21 folder>'
```

It writes `dist\client-<VERSION>\deck-duel-<VERSION>-windows-x64.zip` (the update package, the
same one the installer bundles) and `dist\client-<VERSION>\DeckDuel-Setup-<VERSION>.exe`, and
prints their SHA-256 hashes. It runs `mvn clean verify`, so the full test suite runs first and
needs a desktop session. It refuses to run elsewhere than Windows x64, a version that is not
`MAJOR.MINOR.PATCH[-label]`, and an update address that is not `https://`.

The values come from your own server and release bucket. The repository contains none of them.

## Code signing and SmartScreen

The installer and the game are not code-signed. Windows SmartScreen will show "Windows protected
your PC" for a new unsigned download. Players choose **More info → Run anyway**. The warning fades
only with a code-signing certificate (a paid product) and its reputation. To sign later, add
`SignTool` to `DeckDuel.iss` and sign `DeckDuel-Setup-<version>.exe`. The update packages are
protected separately by the Ed25519 release signature.

## Limits

- **The runtime is fixed per installer.** Automatic updates replace the game and launcher jars,
  never the bundled Java runtime. A release that needs a newer Java version, or a Java module
  missing from `runtime-modules.txt`, must ship a new installer, and players must run it.
- **Windows x64 only.** No Windows ARM64, macOS or Linux installer. Those players can still use the
  update package with `deck-duel-launcher.jar install` and their own Java 17+ (`docs/updates.md`).
- **The game process is `java.exe`.** Task Manager lists the bundled runtime's
  "OpenJDK Platform binary". A pinned taskbar shortcut may be shown apart from the running game's window.
- **Per-user only.** Each Windows account installs its own copy. There is no all-users install.

## Verification

**Not run on Windows.** The build script, the Inno Setup script and the installed shortcuts have not
been executed or compiled: this change was made on macOS without Windows, PowerShell or Inno
Setup. Run the build script, then the checklist below, before giving the installer to players.

Checked on macOS arm64 (2026-10-29, Temurin 27):

| Check | Input | Expected | Actual | Result |
|---|---|---|---|---|
| Setup step tests | `InstallLayoutTest` (two new tests) | Fresh install, no downgrade by an older installer, upgrade by a newer one, restored launcher copy, repair of a damaged pointer and version folder | As expected, 8/8 tests in the class | PASS |
| Runtime size | `jlink` with `runtime-modules.txt`, `--strip-debug --no-man-pages --no-header-files` | A runtime with `bin/java` | 108 MB, 29 modules | PASS |
| Game on the runtime | Release build 1.0.0 → `release package` → `setup` → stable launcher `launch`, all on the linked runtime | The game opens, runs on the linked runtime's `java` and confirms its start | Window opened, process `rt/bin/java`, `.started-ok` written, client connected to 127.0.0.1:5555 | PASS |
| `setup` run twice | Same package | Exit 0, the same version stays active | Exit 0, `active=1.0.0` | PASS |
| TLS on the runtime | `tools/server-smoke.sh` with `JAVA=<linked runtime>` | TLS 1.3 with an EC P-256 certificate, bots play a full match over TLS, default trust refuses the test CA, backup/restore | "smoke test passed" | PASS |
| Public CA trust | `keytool -list -cacerts` on the runtime | Let's Encrypt roots present | ISRG Root X1 and X2 present, 120 roots | PASS |
| Full suite | `mvn clean verify` on a copy of the sources | No failures | 405 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS in 3m20s | PASS |

Windows checklist (to record when run):

1. The build script finishes and prints both hashes.
2. On a Windows account without Java, run the installer: there is no administrator prompt, and the
   Start menu has Deck Duel.
3. Start the game: there is no console window, it says "Up to date" (or offers the newer
   release), and it connects to `<GAME_HOST>`.
4. `%LOCALAPPDATA%\DeckDuel\current.properties` names the version, and `versions\<version>\.started-ok` exists.
5. Publish a newer version (`docs/deployment.md` part H): Update installs it and the game restarts.
6. Run the older installer again: the newer version stays active.
7. Uninstall: the program folder and `%LOCALAPPDATA%\DeckDuel` are gone, and `%USERPROFILE%\.deckduel` remains.
