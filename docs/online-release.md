# Small online release: the short path

This is the order of work for putting Deck Duel online for a small group of players. Each step
links to the detailed procedure. Nothing in the repository creates cloud resources, uploads files
or deploys anything; you run every step yourself.

You supply these values. None are in the repository:

| Value | Example shape | Where it is used |
|---|---|---|
| `<GAME_HOST>` | `play.<your-domain>` | DNS name of the server; on its certificate; packaged into the client |
| `<GAME_PORT>` | `5555` | Server port; packaged into the client; firewall rule |
| `<RELEASE_URL>` | `https://<bucket>.s3.<region>.amazonaws.com/stable/latest.properties` | Update channel packaged into the client |
| `release.pub` / `release.key` | made by `release keygen` | Public key packaged into the client; private key signs releases, kept offline |

## 1. Host the server (once)

The documented host is a small Linux ARM64 EC2 instance with Ubuntu and Java 17. Any Linux server
with Java 17, a public IP and a DNS name works the same way. Cost is your hosting provider's.

1. **Build** on your computer: `tools/build-server-release.sh <VERSION>` → `dist/deck-duel-server-<VERSION>.tar.gz`.
2. **Copy** it to the server and check its SHA-256 ([deployment](deployment.md) part C).
3. **Install**: `sudo deploy/setup-ec2.sh`, then `sudo /opt/deckduel/bin/deckduel-install-release <folder>` (part D).
4. **DNS and certificate**: point `<GAME_HOST>` at the server, then get a Let's Encrypt certificate
   with certbot and install it with `deckduel-install-certificate` (part E). Clients refuse
   self-signed certificates.
5. **Configure and start**: write `/etc/deckduel/server.properties` from the template
   (`port=<GAME_PORT>`), make the backup key, run `deckduel-switch-release <VERSION>`, and run the local `probe` (part F).
6. **Open** only TCP `<GAME_PORT>` in the firewall/security group, then probe from your computer:
   `java -jar target/deck-duel-server.jar probe --host <GAME_HOST> --port <GAME_PORT>` (part G).

Backups run daily on the server (part J). Server updates drain first and never cut a match short
unless you force them (part K).

## 2. Set up the release channel (once)

1. **Signing key**: `java -jar target/deck-duel.jar release keygen --private-key ~/deck-duel-keys/release.key --public-key ~/deck-duel-keys/release.pub`.
   Back up `release.key` offline. If you lose it, installed clients can never be updated again
   without a new installer.
2. **Bucket folder**: make only `<bucket>/<CHANNEL>/*` publicly readable ([deployment](deployment.md) part H.2).
   Downloads are billed by your storage provider as outbound traffic. Each player downloads
   the installer once and each update package (about 140 MB) once.

## 3. Build and publish a release (every release)

1. **Windows build** (on Windows x64; [Windows installer](windows-installer.md)):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\build-windows-installer.ps1 -Version <VERSION> `
       -ServerHost <GAME_HOST> -ServerPort <GAME_PORT> -UpdateUrl <RELEASE_URL> `
       -PublicKeyFile <path>\release.pub -JdkHome <JDK 21 folder>
   ```
   → `dist\client-<VERSION>\deck-duel-<VERSION>-windows-x64.zip` and `DeckDuel-Setup-<VERSION>.exe`.
2. **Other platforms** (optional; on each platform): `tools/build-client-release.sh <VERSION> <GAME_HOST> <GAME_PORT> <RELEASE_URL> release.pub`.
   These players have no installer; see [updates](updates.md).
3. **Upload the packages first**, then **sign and upload the metadata last** ([deployment](deployment.md) parts H.4–H.5):
   ```sh
   java -jar target/deck-duel.jar release sign --private-key ~/deck-duel-keys/release.key \
       --minimum-version <OLDEST_CLIENT_ALLOWED> --minimum-protocol 20 --out dist/site dist/client-<VERSION>/*.zip
   ```
   Put every platform's zip for the release in `dist/client-<VERSION>/` on the computer that holds the
   private key before signing. Signed metadata lists exactly the packages you sign.
4. **Give players the installer**: upload `DeckDuel-Setup-<VERSION>.exe` next to the packages (or any
   download page) and share the link. Players run it once. After that, the game updates itself.

The first release needs both the installer and the signed metadata. Later releases need only new
packages and metadata. Players get them automatically. Build a new installer only when the bundled
Java must change, or so new players download a current version.

## 4. Check with two real players

Follow [deployment](deployment.md) part I: two players on different networks install, register,
play a match through a room code, then open Match history and a replay.

## What players see

Start → "Checking for updates" → connects to `<GAME_HOST>` over TLS → login. If the server cannot
be reached, they can retry or play offline against the bot. If a required update exists, it installs
before login. Unsigned installer: SmartScreen asks them to choose **More info → Run anyway**.
