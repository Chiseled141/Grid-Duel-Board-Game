# Automatic client updates

Player journey: **Launch → Check for updates → Update → download and verify → the game closes →
install → the game starts again**, with no website, installer download or reinstall. Updates
change only that player's application; the multiplayer server keeps running.

No release bucket, endpoint or signing key is configured in this repository. Default builds say
"Updates not set up" and check nothing. Everything below runs locally and is tested; publishing to
S3 is done manually by the operator (`docs/deployment.md` part H). The game connection's TLS and
the packaged production endpoint are described in `docs/connections.md`.

## Parts

| Part | Package / file | Responsibility |
|---|---|---|
| Packaged channel | `src/main/filtered-resources/deckduel/update.properties`, `client/update/UpdateConfig` | Metadata address and trusted Ed25519 public key, set at build time with `-Ddeckduel.update.url` and `-Ddeckduel.update.publicKey`. Only `https://` is accepted, except `http://` to 127.0.0.1/localhost for local development. Empty means not configured; half-set or invalid values are reported as a failed check. |
| Update check | `client/update/HttpUpdateService`, `ReleaseMetadata` | Fetches `latest.properties` and `latest.properties.sig` (64 KiB / 1 KiB limits, 10 s connect, 30 s read timeouts, no credentials), verifies the Ed25519 signature over the exact bytes, and only then parses and decides. |
| Download | `client/update/PackageDownloader`, `Http` | Streams the package to `staging/<name>.part` with progress, resumes with HTTP Range after an interruption (or starts again if the server ignores Range), stops at the signed size (hard cap 768 MiB), and renames the file only when size and SHA-256 match. Three attempts, two seconds apart, with messages a player can act on. |
| Staging | `client/update/StagedInstaller` | Requires a launcher-started installation, checks the platform, downloads, unpacks into a new `versions/<version>` folder and verifies every file. Nothing is activated. |
| Hand-over | `client/update/HelperRestarter`, `UpdateFinisher` | Once verified and no match is in progress, starts the new version's `deck-duel-launcher.jar activate …` with only the installation folder, the version and this process id, then the game saves its preferences and exits. |
| Launcher and helper | `launcher/LauncherMain` (its own jar, JDK classes only) | `launch` starts the active version (with rollback). `activate` waits for the game to close, verifies the version again, switches the pointer and starts the game. `install` sets up an installation from a package (development and tests). `setup` does the same for the Windows installer but keeps a newer active version (`docs/windows-installer.md`). |
| Install layout | `launcher/InstallLayout` | Per-user folders, the atomic pointer, verification, activation, rollback and pruning (below). |
| Packages | `launcher/PackageManifest`, `PackageArchive` | Package format and safe extraction. |
| Versions | `launcher/ReleaseVersion`, `PlatformId` | `MAJOR.MINOR.PATCH[-label]` ordering (`1.10.0` > `1.9.0`, `1.2.0-rc.1` < `1.2.0`) and platform ids such as `mac-aarch64`, `windows-x64`, `linux-x64`. |
| Release tooling | `release/ReleaseTool`, `NativeLibraries`; `tools/update-demo.sh`, `tools/DemoUpdate.java` | Keys, packages, signed metadata, and a local end-to-end demonstration. |

## Release metadata

`latest.properties`, signed in `latest.properties.sig` (Base64 Ed25519 signature of the file's exact bytes):

```properties
format=1
version=1.1.0
minimumVersion=1.0.0
minimumProtocol=16
package.mac-aarch64.url=deck-duel-1.1.0-mac-aarch64.zip
package.mac-aarch64.size=118700000
package.mac-aarch64.sha256=<64 hex digits>
```

Package addresses may be absolute or relative to the metadata, so a bucket folder holding the
metadata, signature and packages works as it is. The client decides:

- **Required** when it is older than `minimumVersion` or speaks an older protocol than `minimumProtocol`.
- **Optional** when `version` is newer than the installed version.
- **Up to date** otherwise. Older metadata never offers a downgrade, so replaying an old signed
  file cannot downgrade anyone.

A release without a package for this platform is still reported, and Update then says the release
has no package for this computer yet. A development build (no release version) refuses to update.

## Package format

`deck-duel-<version>-<platform>.zip` contains exactly:

| Entry | Contents |
|---|---|
| `release.properties` | `format=1`, `version`, `platform`, and `file.<name>=<sha256>` for each file |
| `deck-duel.jar` | The game, with JavaFX natives for that one platform |
| `deck-duel-launcher.jar` | The launcher and helper |

Extraction refuses entries with absolute paths, `..`, folders, backslashes, drive letters,
hidden names, names the manifest does not list, duplicates, and content beyond 1 GiB. It refuses
missing files and any file whose SHA-256 differs from the manifest. A failure removes the partly
written folder. The package's own SHA-256 is in the signed metadata, so the manifest inside is
trusted once that hash has matched.

## Installation layout

```text
<root>/current.properties     active=…, previous=…, launches=…, failed=…   (replaced atomically)
<root>/versions/<version>/    one complete, verified folder per release; never changed afterwards
<root>/launcher/deck-duel-launcher.jar    the stable copy that shortcuts start
<root>/staging/               downloads (*.part) and verified packages
<root>/logs/                  launcher.log, updater.log, client.log
```

`<root>` is per-user and writable: `%LOCALAPPDATA%\DeckDuel` on Windows,
`~/Library/Application Support/DeckDuel` on macOS, and `$XDG_DATA_HOME/deckduel` or
`~/.local/share/deckduel` on Linux. Preferences (`~/.deckduel/client.properties`), offline data and
server databases live elsewhere and are never touched.

- **Windows file locks.** A new release always goes into a new folder, so files of the running
  game are never overwritten. Activation replaces only the pointer file (write a temporary file,
  then an atomic move). The helper runs from the new folder's launcher jar, and replaces the stable
  launcher copy only after the game has exited. If that copy is locked anyway, the old launcher
  keeps working, because it reads the same pointer.
- **Verification before activation.** The helper re-hashes every file of the new folder against
  its manifest immediately before switching the pointer. A mismatch leaves the previous version
  active and starts it.
- **Rollback.** The previous version is kept. A started client confirms its start by writing
  `versions/<v>/.started-ok`. The launcher counts launches of an unconfirmed version; the third
  launch of a version that never confirmed rolls back to the previous one and records it as
  `failed`. Folders other than the active and previous versions are pruned after each activation.
- **Interruptions.** A cut download resumes from its `.part` file. A half-extracted
  `<version>.incoming` folder is deleted and extracted again. A complete version folder that still
  verifies is reused. If the game does not close within 120 seconds, the helper gives up and
  nothing changes.
- **No credentials.** The helper and the restarted game receive only `--root`, `--version`,
  `--wait-pid` and `--install-root`. The player signs in again after the restart, because
  passwords are only ever kept in memory.

## Session rules

- An update is never installed and the game never closes during a match. A verified update waits
  until the match finishes or is left ("Update X is ready. Deck Duel restarts when this match ends.").
- While an update downloads, verifies or waits, Create match, Join a game, Practice and Rematch are
  refused, and a waiting room is closed with `CancelRoomRequest` (added in protocol 15). A room whose
  creation was already on its way is closed as soon as it arrives. The same happens when a
  required update is found from Settings.
- Match history and replays stay available throughout.
- The pre-login gate is unchanged: a required update blocks login until it is installed.

## Release tooling

All commands run locally; nothing is uploaded.

```sh
# 1. A signing key, kept outside any source tree (the tool refuses folders with .git or pom.xml).
java -jar target/deck-duel.jar release keygen --private-key ~/deck-duel-keys/release.key --public-key ~/deck-duel-keys/release.pub
# 2. Build on each target platform (JavaFX natives come from the build machine), packaging the channel in.
mvn clean package -Ddeckduel.version=1.1.0 \
    -Ddeckduel.update.url=https://<bucket>.s3.<region>.amazonaws.com/stable/latest.properties \
    -Ddeckduel.update.publicKey=$(cat ~/deck-duel-keys/release.pub)
java -jar target/deck-duel.jar release package --version 1.1.0 --platform "$(java -jar target/deck-duel.jar release platform)" \
    --app target/deck-duel.jar --launcher target/deck-duel-launcher.jar --out dist
# 3. Sign the metadata for all platform packages of the release.
java -jar target/deck-duel.jar release sign --private-key ~/deck-duel-keys/release.key \
    --minimum-version 1.0.0 --minimum-protocol 20 --out site dist/deck-duel-1.1.0-*.zip
# 4. Upload site/ as it is (metadata, signature, packages): not done by this repository.
```

`release package` refuses a game jar whose stamped version differs, whose JavaFX glass library
was built for another platform (it reads the Mach-O, PE or ELF header), or which contains iCloud
duplicate files such as `card-back 2.png`. Always build with `mvn clean package`. Building for
another platform needs that platform, or OpenJFX's `-Djavafx.platform=<win|mac|mac-aarch64|linux|linux-aarch64>`
plus network access for those artifacts. That cross-build has not been tried here.

The launcher starts the game with the Java that runs the launcher. The Windows installer bundles
that Java runtime (`docs/windows-installer.md`); elsewhere it is the player's own Java 17+.

### Local demonstration

`tools/update-demo.sh [gui|headless]` builds 1.0.0 and 1.1.0 with a local channel packaged in,
packages and signs them with a throwaway key, serves them from 127.0.0.1, installs 1.0.0 into a
temporary installation and updates it:

- **gui:** you press Update on the startup screen.
- **headless:** `tools/DemoUpdate.java` makes the same calls as the Update button.

It waits until 1.1.0 is active and has confirmed its start. It runs `mvn clean package` twice.

## Verification

See "Verification evidence" below.

## Not done

- No S3 bucket, upload, CloudFront, AWS credentials or release pipeline; no production key.
- No production endpoint, certificate or bucket is configured in the repository; the operator supplies them at build and deploy time.
- A Windows x64 installer with a bundled Java runtime and shortcuts exists (`docs/windows-installer.md`),
  but it has not been run on Windows yet. There is no macOS or Linux installer (the first installation
  there is made with `deck-duel-launcher.jar install`) and no code signing of the application itself.
- Automatic updates never replace the installer's bundled Java runtime; a new Java version needs a new installer.
- A version that was rolled back is recorded as `failed` but may be offered again by the next
  check. Skipping it automatically is not implemented.
- No delta updates, and no metadata expiry. Replayed old metadata cannot downgrade, but it can
  hide a newer release until fresh metadata is fetched.
- Windows and Linux were not run; see the evidence below.

## Verification evidence

Environment: macOS 27.0 on Apple M2 (platform `mac-aarch64`), Temurin OpenJDK 27 (compiling for
Java 17), Maven 3.10.0, 2026-10-23. **Only macOS was run.** Windows and Linux paths (per-user
folders, `java.exe`, PE/ELF native checks, Windows file locking) are covered by design and unit
tests of the header parsing, but no process has run on those systems.

**Automated tests** (`mvn clean verify`: 252 tests, 0 failures, 0 errors, 0 skipped):

| Test class | Tests | Covers |
|---|---|---|
| `launcher.UpdateProcessTest` | 3 | Real JVMs: the launcher jar installs and starts the game; the helper waits for a running game process, then activates the new version and restarts it with no account data on its command line; preferences outside the installation unchanged; a tampered staged version refused and the old one restarted; a crashing version rolled back by the launcher after two tries. |
| `launcher.InstallLayoutTest` | 6 | Atomic pointer, keeping the previous version, pruning, reuse of a verified copy, re-extraction after damage or an interrupted extraction, no activation of a tampered version, rollback rules. |
| `launcher.PackageArchiveTest` | 5 | Path traversal, absolute, backslash, drive and hidden names, unlisted, duplicate and missing entries, checksum mismatch, expansion limit, malformed manifests. |
| `launcher.ReleaseVersionTest` | 3 | Version ordering (numeric, pre-release), rejected strings, platform names. |
| `client.update.UpdateChannelTest` | 8 | Local HTTP server: signed metadata (wrong key, changed byte, bad or missing signature, malformed, oversized), required/optional/up-to-date/no-downgrade/protocol decisions, missing platform package, HTTPS-only configuration, resumed and restarted downloads, corrupt, truncated and oversized downloads discarded, staging into an installation without activating it. |
| `client.update.UpdateFinisherTest` | 5 | No closing during a match (restart after the match ends), immediate restart without a match, failed hand-over keeps the game open, fixtures never close the game, a waiting room closes when installing starts. |
| `release.ReleaseToolTest` | 4 | Mach-O, PE and ELF header recognition; packaging refuses the wrong version, the wrong natives and iCloud copies; keys refused inside a source tree and written owner-only; signed metadata verifies with the generated public key; the launcher package imports no other `deckduel` package. |
| `client.state.ClientModelTest` | +2 | Rooms closed by a required update, including one whose creation was in flight; deferred work runs after the match. |
| `server.CardMatchIntegrationTest` | +1 | A cancelled room cannot be joined and no match starts. |
| `client.ui.StartupPanelTest` | +1 | A verified real update shows "Restarting" with no buttons and never connects. |
| `net.MessageSerializationTest` | (updated) | `CancelRoomRequest` crosses the wire under the allowlist. |

**End-to-end demonstration** with the real game jars (`WORK=… tools/update-demo.sh headless`, exit 0):
both versions built with a local channel and a throwaway key, packaged as
`deck-duel-1.0.0-mac-aarch64.zip` and `deck-duel-1.1.0-mac-aarch64.zip` (117,822,151 and
117,822,152 bytes), with 1.1.0 signed and served from 127.0.0.1:8765. 1.0.0 was installed with the
launcher jar. The check reported `OPTIONAL 1.1.0`, the download reached 100% and verified, and the
game handed over to the helper and exited. The helper logged `activated 1.1.0, keeping 1.0.0 for
rollback` and `starting Deck Duel 1.1.0`. The pointer ended as `active=1.1.0 previous=1.0.0`, and
the restarted process ran `versions/1.1.0/deck-duel.jar` (stamped `version=1.1.0`) and wrote
`.started-ok`. The GUI mode, where a person presses Update, was not run in this session.
