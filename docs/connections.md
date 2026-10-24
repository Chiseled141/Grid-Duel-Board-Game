# Secure connections, version policy and maintenance

This page describes how the client reaches the game server, how the server protects that
connection, and how it decides who may play. Operator steps are in `docs/deployment.md`.

## Responsibilities

| Part | Responsibility |
|---|---|
| `client/startup/ServerRoute` | Host, port and whether TLS is used. Plaintext is allowed only for loopback hosts (127.x, localhost, ::1); a non-loopback route without TLS cannot be constructed. |
| `client/startup/Endpoints` | Chooses the route at launch, in this order: development overrides, then the packaged production server, then saved settings or the local default. |
| `client/ClientTls` | Trust: the Java runtime's own trusted authorities (production), or one local test authority from a PEM file (`--trust-cert`, development only). Nothing trusts all certificates. |
| `client/DeckDuelClient` | Connect (bounded), TLS handshake with hostname verification (bounded), protocol greeting with the client version (bounded), then the reader, writer and heartbeat. Every failure closes the socket and starts no threads. |
| `server/ServerConfig`, `ServerSettings` | Bind address, TLS material, minimum client version and handshake timeout. The configuration file refuses unknown keys, and refuses a public bind without TLS. |
| `server/TlsMaterial` | Loads PEM `fullchain.pem` and `privkey.pem` (PKCS#8, EC or RSA) and reloads them for new connections without a restart. |
| `server/GameServer` | Listens with TLS 1.3/1.2 (or plaintext on loopback only), handles drain and resume, and reports status. |
| `server/ClientHandler` | Per connection: a bounded handshake and hello, the version check (`Compatibility`) before any login, and maintenance admission rules. |
| `server/ControlServer`, `ControlClient` | The local operator socket and `ctl` commands. |
| `server/ReadinessProbe` | `probe`: verifies TLS and protocol readiness without credentials. |
| `client/OfflineServer` | Offline practice. It always binds 127.0.0.1 without TLS and refuses any other address. |

## Choosing the server

| Priority | Source | Notes |
|---|---|---|
| 1 | `--host`, `--port`, `--tls`, `--trust-cert` | For development. A remote host always uses TLS; `--tls false` to a remote host is refused, not obeyed. |
| 2 | Packaged `deckduel/connection.properties` (`-Ddeckduel.server.host`, `-Ddeckduel.server.port` at build time) | Always TLS. Production builds set this, so players only launch and log in. |
| 3 | Saved `host`/`port` in `~/.deckduel/client.properties`, default 127.0.0.1:5555 | Used only when no production server is packaged. A saved localhost value never overrides a packaged server. |

The client never writes the chosen route back to `client.properties`. A command-line override
no longer changes the saved preference.

The journey is: Launch → check for updates (direct HTTPS to S3) → connect securely → login →
main menu. Startup work runs on the startup worker thread, and login reconnects run on their own
thread, never on the Swing or JavaFX threads. Retry and Play offline remain available after a
connection failure.

## TLS

- **Server.** The server presents the certificate for the public DNS name players use. It offers
  TLS 1.3 and 1.2 only, with the JDK's default cipher suites. Each new connection gets
  `handshakeTimeoutSeconds` (default 10) for its TLS handshake and protocol hello, then is
  dropped. Handshakes run on the handler pool, never on the accept thread.
- **Client.** The client uses `endpointIdentificationAlgorithm=HTTPS`, so the certificate must name
  the host (DNS SAN, or an IP SAN for IP literals), and it sends SNI for DNS names. Trust comes from
  the runtime's `cacerts`. Connecting, the handshake and the greeting each have a 10-second limit.
  Certificate failures are explained ("has expired", "is for a different host name", "is not
  issued by a trusted authority") and keep their cause. The client sends no credentials until the
  handshake and greeting have succeeded.
- **Certificate files.** These are PEM files without passphrases, protected by file permissions
  (`root:deckduel 0640`). They never go in Git or client packages. `ctl reload-tls` (also run by
  `deckduel-install-certificate`) swaps the certificate for new connections; existing connections
  and matches are unaffected. A broken file is refused, and the last good certificate stays in service.
- **Local test certificates.** `TestCertificates` (keytool) and `tools/server-smoke.sh` (OpenSSL)
  create throwaway test authorities. Players' default trust rejects them, and this is tested. They
  are never production material.

## Protocol 16 and the version policy

The handshake is: server greeting → `ProtocolHello(protocol, clientVersion)` → `ProtocolReady`,
or `UpdateRequired(minimumVersion, text)` followed by closing the connection. A protocol mismatch
is still refused with the greeting text. `minimumClientVersion` in `server.properties` is enforced
by the server whatever the client's update screen did. Once a minimum is set, a client without a
release version (a development build) is refused.

On `UpdateRequired`, the startup flow re-checks for updates and never offers Continue. If the
release channel offers nothing new enough, it says so instead of reconnecting in a loop. The
minimum is read at startup, so changing it means a drained restart. That keeps the earlier rule:
an update discovered during a match never interrupts that match.

New wire types: `UpdateRequired`, `MaintenanceNotice`, and the two-field `ProtocolHello`. All use
`serialVersionUID = 1L` and are listed in `Message.WIRE_FILTER`.

## Maintenance (drain) and restarts

| Command (`deckduel-ctl`, local socket only) | Effect |
|---|---|
| `status` | `state`, `connections`, `openRooms`, `activeMatches`, `heldSeats`, `safeToRestart` and certificate expiry. |
| `drain` | No new rooms, joins, practice matches or rematches. Waiting rooms close at once. Match registration and rematch reservation share the drain barrier: a game admitted before drain remains visible as active, and later admissions are refused. Everyone signed in gets `MaintenanceNotice`; players with a room or a match also see it as a message. Matches in progress and held reconnect seats continue normally. |
| `resume` | Admits new matches again. |
| `wait-safe [--timeout S]` | Polls until `safeToRestart=true` (draining, no match in progress or final replay/result write pending, no held seat, no open room). Exit 0 when safe, 3 when not. |
| `reload-tls` | Re-reads the certificate files. |

The control socket (`/run/deckduel/control.sock`, mode 0660, in the service's runtime folder) is
reachable only by root and the `deckduel` group. Nothing administrative listens on the network.

`deckduel-switch-release` refuses to restart until `wait-safe` succeeds. `--force` is the
separate, explicit way to restart anyway, and it ends whatever is still being played. Active
matches never survive a restart, because sessions live in memory. Zero-downtime restarts are not
supported.

## Not done

- No client-certificate authentication, and no certificate pinning (public CA trust is used).
- A slow-handshake flood can occupy the handler pool (32 threads) for up to the handshake timeout.
  There is no per-address rate limiting.
- Sessions are not persisted, so a restart, crash or forced stop ends every match in progress.
- `minimumClientVersion` changes need a restart.

## Verification evidence (2026-10-23)

Environment: macOS 27.0 on Apple M2 (arm64); Temurin OpenJDK 27 and Temurin OpenJDK 17.0.21
(downloaded locally for this check); Maven 3.10.0; OpenSSL 4.0.3.

| Run | Result |
|---|---|
| `mvn test` (Java 27) | 278 tests, 0 failures, 0 errors, 0 skipped |
| `mvn clean verify` (Java 27) | 278 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS |
| `mvn clean verify` (Java 17.0.21) | 278 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS |
| `tools/build-server-release.sh 1.0.0` | Bundle built outside iCloud (tests run again); server jar 14.4 MB with no JavaFX, client code or duplicate entries, a Linux aarch64 SQLite library, Java 17 class files (version 61) |
| `tools/server-smoke.sh` on the bundled server jar (Java 17) | 4 of 5 runs passed. The first run failed at the bot match; its output was not kept, and the cause is unknown. The script now prints the bot and server output on failure, and the next 4 runs passed. |
| `tools/server-smoke.sh` on `target/` jars (Java 17 ×2, Java 27 ×2) | All passed |

What the smoke test shows:

- A headless server on 127.0.0.1 with throwaway OpenSSL certificates.
- `openssl s_client` reports TLS 1.3 and "Verify return code: 0".
- `probe` reports "ok protocol 16 ready", and Java's default trust refuses the test certificate.
- Two bots play a full three-round match over TLS.
- `drain`, then `wait-safe` reports `safeToRestart=true`.
- An encrypted backup restores into a temporary database with identical row counts.
- A graceful stop on SIGTERM.

Test classes for this phase:

| Test class | Tests | Covers |
|---|---|---|
| `server.SecureConnectionTest` | 10 | Trusted TLS login and registration; rejection of a wrong host name, an untrusted authority (including players' default trust) and an expired certificate; no plaintext/TLS mixing; refusal of a public bind without TLS; a silent client dropped after the handshake timeout with its handler released; a silent server timing out with client cleanup and no reader thread; outdated and development clients refused before login; certificate reload for new connections without a restart, and a broken renewal refused. |
| `server.CardMatchIntegrationTest` | +3 (14) | Drain closes rooms and refuses joins, creation, practice and rematches while the running match finishes; a held seat keeps a restart unsafe until its grace ends; the control socket (mode 0660, status, drain, resume, reload-tls, wait-safe exit codes). |
| `server.ServerOperationsTest` | 5 | Configuration validation (unknown keys, public bind without TLS, TLS pairs, the template's keys); a backup taken while the server connection is open restores accounts, W/L, history, round scores and replays with identical counts; tampered files, wrong keys and non-Deck-Duel databases refused; retention. |
| `client.startup.EndpointsTest` | 4 | Precedence: overrides, then packaged production, then saved settings or local; obsolete saved localhost never overrides production; remote plaintext refused. |
| `client.startup.StartupFlowTest` | +2 (16) | A server refusal leads back to the update step without Continue; no update available gives a message and no reconnect loop. |
| `client.state.ClientModelTest` | +1 (12) | Maintenance notice blocks new matches, clears the room and informs affected players. |
| `client.OfflineServerTest` | +1 (2) | Offline play refuses a non-loopback bind and is unreachable at the machine's network addresses. |
| `net.MessageSerializationTest` | (updated) | `UpdateRequired`, `MaintenanceNotice` and the two-field `ProtocolHello` cross the allowlist. |

Not verified: Ubuntu 26.04, Linux ARM64, systemd units and hardening, certbot, real DNS, AWS
(EC2, security groups, S3 policies, presigned transfers) and connections over the internet.
Server capacity and memory under load were not measured.

Two notes on the build environment:

- This repository sits in iCloud-synced Documents, and even clean builds in `target/` collected
  "name 2.ext" duplicates during this session. That affected jar sizes and the per-file report
  count; Maven's own summary (278) is authoritative.
- The release scripts therefore build from a copy outside the repository and refuse jars that
  contain such duplicates.

### Maintenance and local readiness corrections (2026-10-23)

Match registration and rematch reservation now share a lock with drain and status snapshots.
A match admitted before drain stays visible as active; drain refuses later starts. A finished
match stays active until its final replay writes and result transaction return. A departing
account is released from the match registry only after its forfeit's persistence completes.
`activeMatches` therefore includes final persistence, not just unfinished gameplay.

Before opening the public game port, run the probe inside EC2 with
`probe --host <GAME_HOST> --port <GAME_PORT> --connect-address 127.0.0.1`.
It connects locally while verifying the production hostname and sending its TLS SNI. Only a
loopback dial override is accepted. After opening the port, probe externally without the override.

Executed locally on macOS arm64 with OpenJDK 27; no AWS resources were accessed:

| Check | Expected and actual result |
|---|---|
| `mvn -o clean test -Dtest=CardMatchIntegrationTest,SecureConnectionTest` | PASS: 29 tests, 0 failures/errors/skipped. Blocked replay/result writes keep restart unsafe; drain waits for registration; a reserved rematch stays active and later reservations are refused; loopback probing verifies the named certificate and rejects a wrong hostname. |
| `mvn -o clean verify` | PASS: 283 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| `bash tools/server-smoke.sh` | PASS: packaged headless server, TLS 1.3, loopback readiness, bot match, drain/wait-safe, encrypted backup and restore (2 users, 1 match, 3 rounds, 2 replays, 130 frames), then shutdown. |

These correction checks did not run on Java 17, Ubuntu, systemd or AWS; the earlier verification
entries above describe previous runs, not additional runs of these fixes.
