# Deck Duel documentation

Every technical document for the project, in one file. Use the contents to jump to a section.

- **Hosting, connections and updates**
  - [Small online release: the short path](#online-release)
  - [Manual deployment: Deck Duel server on EC2, client releases on S3](#deployment)
  - [Secure connections, version policy and maintenance](#connections)
  - [Startup, updates and offline play](#startup-and-updates)
  - [Automatic client updates](#updates)
  - [Windows installer](#windows-installer)
  - [macOS app](#macos-app)
- **Game rules and screens**
  - [Card catalog — `cards-neutral-5`](#card-catalog)
  - [King Cards — implemented 2026-10-25](#kings)
  - [How to Play](#how-to-play)
  - [Personal match replays](#replays)
  - [Card table direction](#card-table-design)
- **Testing**
  - [Verification](#testing)

---

<a id="online-release"></a>

## Small online release: the short path

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

### 1. Host the server (once)

**Quick path (from a clone, on the instance).** Give the instance an Elastic IP, point
`<GAME_HOST>` at it, open TCP 80 and `<GAME_PORT>` in its security group, then:

```sh
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/Chiseled141/Grid-Duel-Board-Game.git && cd Grid-Duel-Board-Game
sudo deploy/install-server.sh <GAME_HOST> <your-email> <GAME_PORT>
```

`deploy/install-server.sh` installs Java 17, Maven and certbot, builds the headless server from the
checkout (tests run on your computer), runs `deploy/setup-ec2.sh`, gets and installs the Let's
Encrypt certificate (renewals reinstall it), writes `/etc/deckduel/server.properties`, makes the
backup key (printed once: store it), starts the service and probes it. After `git pull`, run it
again: the new build installs as `<version>-git.<commit>` and the restart waits for running matches.
The manual steps below remain the reference.

The documented host is a small Linux ARM64 EC2 instance with Ubuntu and Java 17. Any Linux server
with Java 17, a public IP and a DNS name works the same way. Cost is your hosting provider's.

1. **Build** on your computer: `tools/build-server-release.sh <VERSION>` → `dist/deck-duel-server-<VERSION>.tar.gz`.
2. **Copy** it to the server and check its SHA-256 ([deployment](#deployment) part C).
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

### 2. Set up the release channel (once)

1. **Signing key**: `java -jar target/deck-duel.jar release keygen --private-key ~/deck-duel-keys/release.key --public-key ~/deck-duel-keys/release.pub`.
   Back up `release.key` offline. If you lose it, installed clients can never be updated again
   without a new installer.
2. **Bucket folder**: make only `<bucket>/<CHANNEL>/*` publicly readable ([deployment](#deployment) part H.2).
   Downloads are billed by your storage provider as outbound traffic. Each player downloads
   the installer once and each update package (about 140 MB) once.

### 3. Build and publish a release (every release)

1. **Windows build** (on Windows x64; [Windows installer](#windows-installer)):
   ```powershell
   powershell -ExecutionPolicy Bypass -File launcher\windows\build-installer.ps1 -Version <VERSION> `
       -ServerHost <GAME_HOST> -ServerPort <GAME_PORT> -UpdateUrl <RELEASE_URL> `
       -PublicKeyFile <path>\release.pub -JdkHome <JDK 21 folder>
   ```
   → `dist\client-<VERSION>\deck-duel-<VERSION>-windows-x64.zip` and `DeckDuel-Setup-<VERSION>.exe`.
2. **macOS build** (on a Mac; [macOS app](#macos-app)):
   `launcher/macos/build-app.sh <VERSION> <GAME_HOST> <GAME_PORT> [<RELEASE_URL> release.pub]`
   → `dist/client-<VERSION>/deck-duel-<VERSION>-mac-<arch>.zip` and `DeckDuel-<VERSION>-mac-<arch>.dmg`.
3. **Other platforms** (optional; on each platform): `tools/build-client-release.sh <VERSION> <GAME_HOST> <GAME_PORT> <RELEASE_URL> release.pub`.
   These players have no installer; see [updates](#updates).
4. **Upload the packages first**, then **sign and upload the metadata last** ([deployment](#deployment) parts H.4–H.5):
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

### 4. Check with two real players

Follow [deployment](#deployment) part I: two players on different networks install, register,
play a match through a room code, then open Match history and a replay.

### What players see

Start → "Checking for updates" → connects to `<GAME_HOST>` over TLS → login. If the server cannot
be reached, they can retry or play offline against the bot. If a required update exists, it installs
before login. Unsigned installer: SmartScreen asks them to choose **More info → Run anyway**.

---

<a id="deployment"></a>

## Manual deployment: Deck Duel server on EC2, client releases on S3

You run every step here yourself. The repository contains code, scripts and templates; it never
connects to AWS, uploads anything or changes security groups. Each command block says where it runs:

- **Run on my computer:** your workstation with this repository, Java 17+, Maven, and (for S3
  steps) the AWS CLI signed in as you.
- **Run inside EC2:** a Session Manager shell on the instance (`sudo` available).
- **Change in AWS Console:** a click-path in the AWS web console.

Technical background: `docs/README.md#connections` (TLS, version policy, drain), `docs/README.md#updates`
(updater and release format), `docs/README.md#windows-installer`, `docs/README.md#replays`. The order of work for a
first small release is summarised in `docs/README.md#online-release`.

### 0. Inputs you must supply

Nothing below is filled in for you. Earlier transcribed identifiers may be wrong; part A verifies them.

| Placeholder | Meaning |
|---|---|
| `<AWS_ACCOUNT_ID>` | 12-digit account. Verify it in part A; do not trust a copied value. |
| `<INSTANCE_ID>` | The t4g.small instance (ap-southeast-1). |
| `<ELASTIC_IP>` | The Elastic IP associated with it. |
| `<GAME_HOST>` | The public DNS name players connect to, for example `play.<your-domain>`. It must resolve to `<ELASTIC_IP>` and be the name on the certificate. |
| `<GAME_PORT>` | The TCP game port (the template uses 5555). |
| `<RELEASE_BUCKET>` | The existing client-release bucket; only its `<CHANNEL>/` objects become publicly readable. |
| `<BACKUP_BUCKET>` | The existing private backup bucket. |
| `<CHANNEL>` | The release folder in the release bucket, for example `stable`. |
| `<VERSION>` | The release version you are deploying, for example `1.1.0`. |
| Signing key | Ed25519 private key for release metadata, kept outside Git (part G). |
| Backup key | AES-256 key file for backups, kept on the instance and in your password manager (part J). |

### A. Verify identifiers (do this first)

**Run on my computer**
```sh
aws sts get-caller-identity          # "Account" is <AWS_ACCOUNT_ID>; "Arn" is who you are signed in as
aws configure get region             # should be ap-southeast-1 for the commands below (or pass --region)
aws s3api get-bucket-location --bucket <RELEASE_BUCKET>   # LocationConstraint: ap-southeast-1
aws s3api get-bucket-location --bucket <BACKUP_BUCKET>
```

**Change in AWS Console:** the account menu (top right) shows the account ID. Under EC2 →
Instances, check the instance's ID, architecture `arm64`, and "Elastic IP addresses" → the address
associated with it.

**Run inside EC2** (IMDSv2; prints the account, region, instance and architecture as AWS reports them)
```sh
TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/dynamic/instance-identity/document
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4; echo   # must equal <ELASTIC_IP>
uname -m; java -version; lsb_release -ds                                                                   # aarch64, Java 17, Ubuntu
```

If any value differs from your notes, use what these commands show.

### B. Build the server release

**Run on my computer**
```sh
tools/build-server-release.sh <VERSION>
```
This runs `mvn clean verify` (all tests) with `-Ddeckduel.version=<VERSION>`, checks the server jar
(no JavaFX or client code, a Linux aarch64 SQLite library present, Java 17 class files, the
version stamp), and writes:

- `dist/deck-duel-server-<VERSION>.tar.gz`: the jar, `SHA256SUMS`, `VERSION`, `deploy/` (scripts,
  systemd units, config template) and this guide.
- `dist/deck-duel-server-<VERSION>.tar.gz.sha256`: note the hash; you compare it inside EC2.

Optional local check (headless server, TLS, bots, drain, backup/restore):
`tools/server-smoke.sh` (use `JAVA=/path/to/jdk17/bin/java tools/server-smoke.sh` for Java 17).

### C. Transfer the release to the instance

Session Manager has no file upload. Use a short-lived presigned download, which needs **no
permission on the instance**, only outbound HTTPS.

**Run on my computer** (your identity needs `s3:PutObject` and `s3:GetObject` on `arn:aws:s3:::<BACKUP_BUCKET>/transfer/*`)
```sh
aws s3 cp dist/deck-duel-server-<VERSION>.tar.gz s3://<BACKUP_BUCKET>/transfer/ --sse AES256
aws s3 presign s3://<BACKUP_BUCKET>/transfer/deck-duel-server-<VERSION>.tar.gz --expires-in 900   # copy the printed URL
```

**Run inside EC2** (paste the URL inside single quotes)
```sh
cd /tmp
curl -fSo deck-duel-server-<VERSION>.tar.gz '<PRESIGNED_URL>'
sha256sum deck-duel-server-<VERSION>.tar.gz          # must equal the .sha256 from part B
tar xzf deck-duel-server-<VERSION>.tar.gz
(cd server-<VERSION> && sha256sum --check SHA256SUMS)
```

**Run on my computer** (afterwards): `aws s3 rm s3://<BACKUP_BUCKET>/transfer/deck-duel-server-<VERSION>.tar.gz`

Alternatives:

- **Instance role.** Attach a role allowing `s3:GetObject` on
  `arn:aws:s3:::<BACKUP_BUCKET>/transfer/*`; then, inside EC2, run
  `aws s3 cp s3://<BACKUP_BUCKET>/transfer/... .` (needs the AWS CLI on the instance).
- **Build on the instance** from your Git hosting: `git clone`, `sudo apt install maven`, then
  `tools/build-server-release.sh <VERSION>` inside EC2. This needs outbound access to your Git
  host and Maven Central.

### D. Prepare the instance (once; re-run after each release to refresh scripts)

**Run inside EC2**
```sh
sudo /tmp/server-<VERSION>/deploy/setup-ec2.sh
sudo /opt/deckduel/bin/deckduel-install-release /tmp/server-<VERSION>
```

The setup creates:

| Path | Owner, mode | Holds |
|---|---|---|
| `/opt/deckduel/releases/<VERSION>/` | root, 0755 | Replaceable, versioned server jars (never edited in place). |
| `/opt/deckduel/current`, `/opt/deckduel/previous` | root | Symlinks to the running and the previous release. |
| `/opt/deckduel/bin/` | root, 0755 | `deckduel-ctl`, `-install-release`, `-switch-release`, `-rollback`, `-install-certificate`, `-backup-upload`. |
| `/etc/deckduel/server.properties` | root:deckduel, 0640 | Configuration (you write it in part F). |
| `/etc/deckduel/tls/` | root:deckduel, 0750 / files 0640 | `fullchain.pem`, `privkey.pem`. |
| `/etc/deckduel/backup.key` | root:deckduel, 0640 | Backup encryption key. |
| `/var/lib/deckduel/` | deckduel, 0700 | Persistent SQLite database. |
| `/var/backups/deckduel/` | deckduel, 0700 | Encrypted backups. |
| `/run/deckduel/control.sock` | deckduel, 0660 | Operator socket (created by systemd at each start). |
| journald (`journalctl -u deckduel`) | | Logs. Temporary files go to a private `/tmp` (`PrivateTmp`). |

The service runs as the unprivileged `deckduel` account with `NoNewPrivileges`, a read-only system
(`ProtectSystem=strict`) except its data and backup folders, and no capabilities. It is enabled at
boot (`WantedBy=multi-user.target`) and restarts on failure.

### E. TLS certificate for `<GAME_HOST>`

Requirements: a certificate from a public authority trusted by Java's default `cacerts` (Let's
Encrypt qualifies). The SAN must contain `<GAME_HOST>`. The chain must be complete, leaf first
(certbot's `fullchain.pem`). The key must be ECDSA P-256 (certbot's default) or RSA 2048+, as
unencrypted PKCS#8 (`BEGIN PRIVATE KEY`, certbot's `privkey.pem`). Self-signed and local test
certificates are refused by players' clients, by design.

1. **Change in your DNS provider:** an `A` record `<GAME_HOST>` → `<ELASTIC_IP>`. Then **run inside EC2**:
   `getent hosts <GAME_HOST>` must print `<ELASTIC_IP>`.
2. **Change in AWS Console** (HTTP-01 validation only): EC2 → Security Groups → the instance's
   group → Inbound rules → add TCP 80 from `0.0.0.0/0`, description "certbot HTTP-01". Let's
   Encrypt does not publish its validation addresses. Remove the rule after issuance if you renew
   by another method; certbot's standalone renewals also need port 80 open. (Alternative: DNS-01
   validation with your DNS provider, which needs no inbound port.)
3. **Run inside EC2**
   ```sh
   sudo apt-get update && sudo apt-get install -y certbot
   sudo certbot certonly --standalone -d <GAME_HOST> --agree-tos -m <your-email> --no-eff-email \
        --deploy-hook '/opt/deckduel/bin/deckduel-install-certificate "$RENEWED_LINEAGE/fullchain.pem" "$RENEWED_LINEAGE/privkey.pem"'
   sudo /opt/deckduel/bin/deckduel-install-certificate /etc/letsencrypt/live/<GAME_HOST>/fullchain.pem /etc/letsencrypt/live/<GAME_HOST>/privkey.pem
   sudo certbot renew --dry-run            # renewal works; certbot's systemd timer renews automatically
   ```
   On each renewal the deploy hook copies the files and runs `deckduel-ctl reload-tls`: new
   connections get the new certificate; running matches are untouched; no restart.

### F. Configure and start

**Run inside EC2**
```sh
sudo install -o root -g deckduel -m 0640 /etc/deckduel/server.properties.example /etc/deckduel/server.properties
sudo nano /etc/deckduel/server.properties      # set port=<GAME_PORT>; minimumClientVersion if wanted; keep the TLS paths
sudo -u deckduel /usr/bin/java -jar /opt/deckduel/releases/<VERSION>/deck-duel-server.jar backup-keygen --key-file /tmp/backup.key
sudo install -o root -g deckduel -m 0640 /tmp/backup.key /etc/deckduel/backup.key && sudo shred -u /tmp/backup.key
sudo cat /etc/deckduel/backup.key              # store this value in your password manager; without it backups cannot be restored
sudo /opt/deckduel/bin/deckduel-switch-release <VERSION>
sudo systemctl enable --now deckduel-backup.timer
```

Health, logs and readiness. **Run inside EC2** (the port is still closed to the internet):
```sh
systemctl status deckduel --no-pager
journalctl -u deckduel -n 50 --no-pager                 # "listening on 0.0.0.0:<GAME_PORT> (TLS)" and "Deck Duel server ready ... protocol 20"
sudo /opt/deckduel/bin/deckduel-ctl status               # state=open ... certificateExpires=...
ss -ltnp | grep <GAME_PORT>
/usr/bin/java -jar /opt/deckduel/current/deck-duel-server.jar probe --host <GAME_HOST> --port <GAME_PORT> --connect-address 127.0.0.1
#   tls ok TLSv1.3 certificate CN=<GAME_HOST> ...   then   ok protocol 20 ready ...
```
This local probe dials loopback, so the security group's public game port can stay closed.
It still verifies the certificate against `<GAME_HOST>` and uses that name for TLS SNI; it does
not weaken certificate verification. The probe uses Java's default trust, exactly like players,
and sends no credentials. After opening the game port in G, repeat the external probe there
without `--connect-address` to verify public routing and firewall access. If it fails
with a certificate message, fix part E before opening the port. Reboot test (optional):
`sudo reboot`, then reconnect and repeat `systemctl status deckduel`.

### G. Open the game port (only after F's probe succeeds)

**Change in AWS Console:** EC2 → Security Groups → the instance's group → Inbound rules → add
**Custom TCP `<GAME_PORT>`** from `0.0.0.0/0` (and `::/0` if you use IPv6), description "Deck Duel
game (TLS)". Open no other port. Port 80 is needed only for HTTP-01 certificates. Session Manager
needs no inbound port.

**Run on my computer** (from your own network, not the instance)
```sh
java -jar target/deck-duel-server.jar probe --host <GAME_HOST> --port <GAME_PORT>
```

### H. Production client release (direct S3, no CloudFront)

1. **Signing key, once.** **Run on my computer**:
   `java -jar target/deck-duel.jar release keygen --private-key ~/deck-duel-keys/release.key --public-key ~/deck-duel-keys/release.pub`.
   The tool refuses folders inside a Git or Maven tree. Back the private key up offline. It never
   goes to the instance, S3 or players.
2. **Public read for release objects only.**
   - **Change in AWS Console:** S3 → `<RELEASE_BUCKET>` → Permissions.
   - Object Ownership: "Bucket owner enforced" (ACLs off).
   - Block public access: turn off only the two *policy* settings for this bucket, keeping both
     ACL settings on. The account-level setting must allow it too.
   - Bucket policy:
     ```json
     {"Version":"2012-10-17","Statement":[{"Sid":"PublicReadDeckDuelReleases","Effect":"Allow","Principal":"*",
       "Action":"s3:GetObject","Resource":"arn:aws:s3:::<RELEASE_BUCKET>/<CHANNEL>/*"}]}
     ```
   - Keep **all** Block public access settings **on** for `<BACKUP_BUCKET>`; it has no bucket
     policy granting public access.
3. **Build per platform.** **Run on my computer**, on each OS/CPU you ship (JavaFX natives come
   from the build machine):
   ```sh
   tools/build-client-release.sh <VERSION> <GAME_HOST> <GAME_PORT> \
       https://<RELEASE_BUCKET>.s3.ap-southeast-1.amazonaws.com/<CHANNEL>/latest.properties ~/deck-duel-keys/release.pub
   ```
   This produces `dist/client-<VERSION>/deck-duel-<VERSION>-<platform>.zip`. The tool refuses a jar
   with the wrong natives or iCloud duplicate files.
4. **Upload immutable packages first** (your identity needs `s3:PutObject` on
   `<RELEASE_BUCKET>/<CHANNEL>/*`). **Run on my computer**:
   ```sh
   aws s3 cp dist/client-<VERSION>/deck-duel-<VERSION>-<platform>.zip s3://<RELEASE_BUCKET>/<CHANNEL>/ \
       --content-type application/zip --cache-control "public, max-age=31536000, immutable"
   ```
   Never overwrite a published package; a new build gets a new version.
5. **Sign and publish the metadata last.** **Run on my computer**:
   ```sh
   java -jar target/deck-duel.jar release sign --private-key ~/deck-duel-keys/release.key \
       --minimum-version <OLDEST_SUPPORTED_CLIENT> --minimum-protocol 20 --out dist/site dist/client-<VERSION>/*.zip
   aws s3 cp dist/site/latest.properties.sig s3://<RELEASE_BUCKET>/<CHANNEL>/ --content-type text/plain --cache-control no-cache
   aws s3 cp dist/site/latest.properties     s3://<RELEASE_BUCKET>/<CHANNEL>/ --content-type text/plain --cache-control no-cache
   curl -fsS https://<RELEASE_BUCKET>.s3.ap-southeast-1.amazonaws.com/<CHANNEL>/latest.properties | head -3   # public
   curl -sI  https://<BACKUP_BUCKET>.s3.ap-southeast-1.amazonaws.com/ | head -1                                # 403: private
   ```
   Between the two metadata uploads a client may see a signature mismatch. It reports "Update
   check failed" with Retry; it never accepts unsigned data.
   For Windows, build on a Windows x64 computer with `launcher\windows\build-installer.ps1` instead
   (`docs/README.md#windows-installer`). It writes the Windows package to the same folder, together with
   `DeckDuel-Setup-<VERSION>.exe`. Copy every platform's zip into `dist/client-<VERSION>/` on the
   computer that holds the signing key before step 5.
6. **First installation for players.** Windows players run `DeckDuel-Setup-<VERSION>.exe` once
   (bundled Java, no administrator rights). Upload it next to the packages, for example:
   `aws s3 cp dist/client-<VERSION>/DeckDuel-Setup-<VERSION>.exe s3://<RELEASE_BUCKET>/<CHANNEL>/ --content-type application/vnd.microsoft.portable-executable`,
   and share its URL, or host it anywhere else. Players on other platforms need Java 17+, the
   launcher and a first package (`deck-duel-launcher.jar install --root <per-user folder> --package <zip>`;
   see `docs/README.md#updates`).

Players receive only the packages, which hold the client jars, the public key and public
addresses: never AWS credentials, the signing key, the server certificate key or a database.

### I. Two real clients from different networks

1. Give the client package to two testers on different networks (for example home broadband and a
   phone hotspot).
2. Each launches the game. It checks for updates, connects securely to `<GAME_HOST>` with no host
   entry needed, then registers an account.
3. One creates a match; the other joins by code. Play a full match, then check Play → Match
   history and Watch replay on both.
4. **Run inside EC2** during the match: `sudo /opt/deckduel/bin/deckduel-ctl status`
   (`activeMatches=1`) and `journalctl -u deckduel -f`.

### J. Backups

- **Daily, automatic.** `deckduel-backup.timer` runs at 03:30 (±15 min). It makes a consistent
  snapshot with SQLite `VACUUM INTO` (safe while the server runs; never a file copy), checks
  `integrity_check`, `foreign_key_check` and the tables, encrypts it with AES-256-GCM, writes it
  owner-only to `/var/backups/deckduel/deckduel-<time>.db.enc`, and keeps the newest 14.
- **On demand.** **Run inside EC2**:
  `sudo systemctl start deckduel-backup && ls -l /var/backups/deckduel/ && journalctl -u deckduel-backup -n 5 --no-pager`.
- **Optional upload to `<BACKUP_BUCKET>`.** Give the instance role only `s3:PutObject` on
  `arn:aws:s3:::<BACKUP_BUCKET>/deckduel-backups/*`, install the AWS CLI, then **run inside EC2**:
  `sudo /opt/deckduel/bin/deckduel-backup-upload <BACKUP_BUCKET>`. Without an instance role:
  generate a presigned PUT URL on your computer (the AWS CLI's `presign` covers GET only; for
  example with Python boto3 `generate_presigned_url('put_object', ...)`), then
  `curl -fT /var/backups/deckduel/<file> '<URL>'` inside EC2.
- **Retention in S3.**
  - **Change in AWS Console:** S3 → `<BACKUP_BUCKET>` → Management → Lifecycle rule for prefix
    `deckduel-backups/`:
    - expire current versions after (for example) 35 days;
    - if versioning is on, permanently delete noncurrent versions after (for example) 7 days and
      remove expired delete markers;
    - abort incomplete multipart uploads after 1 day.
  - Add a rule for `transfer/` expiring objects after 1 day.
  - Default encryption (SSE-S3) stays on; the files are already encrypted with the backup key.
- **Restore** (stop admission first; the database must not be in use). **Run inside EC2**:
  ```sh
  sudo /opt/deckduel/bin/deckduel-ctl drain && sudo /opt/deckduel/bin/deckduel-ctl wait-safe
  sudo systemctl stop deckduel
  sudo -u deckduel /usr/bin/java -jar /opt/deckduel/current/deck-duel-server.jar restore \
       --backup /var/backups/deckduel/<file>.db.enc --key-file /etc/deckduel/backup.key \
       --database /var/lib/deckduel/deck-duel.db --replace true
  sudo systemctl start deckduel && sudo /opt/deckduel/bin/deckduel-ctl status
  ```
  The restore decrypts, re-checks integrity, foreign keys and tables, then moves the file into
  place, keeping the old one as `deck-duel.db.before-restore-<time>`. Practise it on your computer
  against a temporary path first: `tools/server-smoke.sh` does this, and so does the
  `ServerOperationsTest` suite.

Never commit, bundle or upload a live database anywhere except encrypted to `<BACKUP_BUCKET>`.

### K. Deploying a later server release

1. Parts B and C for the new `<VERSION>`, then, **inside EC2**:
   `sudo /tmp/server-<VERSION>/deploy/setup-ec2.sh` (refreshes scripts and units) and
   `sudo /opt/deckduel/bin/deckduel-install-release /tmp/server-<VERSION>`.
2. Take a backup: `sudo systemctl start deckduel-backup`.
3. `sudo /opt/deckduel/bin/deckduel-switch-release <VERSION>`. It drains (players see the
   maintenance message; no new rooms, joins, practice or rematches), waits until no match is in
   progress and no seat is held, then switches `current` and restarts. If the wait times out it
   refuses and stays in maintenance: run it again later, or `deckduel-ctl resume` to cancel.
4. **Forced restart** (explicit; ends every match in progress):
   `sudo /opt/deckduel/bin/deckduel-switch-release <VERSION> --force`, or
   `sudo systemctl restart deckduel`.

**Server and client versions together.**
- If the protocol is unchanged, deploy the server and client in either order.
- If the protocol changes, prepare everything, then publish in this order:
  1. Upload the client packages.
  2. Drain and switch the server (with `minimumClientVersion=<new version>`).
  3. Publish `latest.properties` with `--minimum-protocol <new protocol>`.

  Players then see a required update and connect afterwards. The server refuses older clients
  before login regardless.

### L. Rollback and database compatibility

`sudo /opt/deckduel/bin/deckduel-rollback` switches to `/opt/deckduel/previous` with the same
draining. Schema changes are additive and idempotent: new tables and nullable columns, with
earlier columns kept. So an older release keeps working on a database a newer one has opened.
Every release so far follows this rule. If a future release breaks it, its notes must say so;
then roll back by restoring the backup taken in K.2 together with the previous release. Client
rollbacks are separate: republish older metadata only if the server still accepts that client.

### JVM memory on the 2 GiB instance

`ExecStart` uses `-Xms128m -Xmx512m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`:

- **512 MiB heap.** Match state, views and replays are small objects. The heap leaves more than
  1 GiB for the OS, SQLite's page cache, thread stacks (32 handlers, one thread per match), the
  daily backup JVM (`-Xmx256m`) and certbot.
- **Serial collector.** Fits a small heap on two vCPUs with little overhead.
- **Exit on out-of-memory.** systemd then restarts the service. That ends active matches, but it
  never leaves a broken server running.

None of this capacity has been measured. Watch `systemctl status deckduel` (Memory) and
`sudo -u deckduel jcmd $(pgrep -f deck-duel-server.jar) GC.heap_info` under real play before
raising limits.

### What was verified, and what was not

Verified locally on macOS arm64 only ("Verification evidence" in `docs/README.md#connections`): TLS,
version policy, drain, backups and the headless server jar, on Java 17.0.21 and Java 27.
`tools/build-server-release.sh` builds from a copy outside the repository, because the
iCloud-synced folder gains duplicate files in `target/`, and it refuses jars that contain any.

Not verified: Ubuntu 26.04, Linux ARM64, systemd units, AWS, certbot, real DNS, the security
group, and connections across the internet. You verify those by following this guide.

---

<a id="connections"></a>

## Secure connections, version policy and maintenance

This page describes how the client reaches the game server, how the server protects that
connection, and how it decides who may play. Operator steps are in `docs/README.md#deployment`.

### Responsibilities

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

### Choosing the server

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

### TLS

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

### Protocol 16 and the version policy

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

### Maintenance (drain) and restarts

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

### Not done

- No client-certificate authentication, and no certificate pinning (public CA trust is used).
- A slow-handshake flood can occupy the handler pool (32 threads) for up to the handshake timeout.
  There is no per-address rate limiting.
- Sessions are not persisted, so a restart, crash or forced stop ends every match in progress.
- `minimumClientVersion` changes need a restart.

### Verification evidence (2026-10-23)

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

#### Maintenance and local readiness corrections (2026-10-23)

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

---

<a id="startup-and-updates"></a>

## Startup, updates and offline play

The client's journey is **Launch → Check for updates → Connect to server → Login → Main menu**.
This page covers the startup screen, offline play and the session rules around updates. The
updater itself (signed metadata, downloads, installation layout, helper process, release tooling)
is described in `docs/README.md#updates`.

### What is implemented

| Part | Where | Notes |
|---|---|---|
| Startup screen | `client/ui/StartupPanel` (Swing, `Screen.STARTUP`) | Iron plate on the animated menu valley, like the login card; menu music plays as on the other menus. It only renders states and forwards button presses. |
| Update steps | `client/startup/UpdateFlow` | Check → up to date / not configured / check failed / optional / required → installing (progress) → install failed / installed. Shared by the startup screen and Settings. |
| Startup journey | `client/startup/StartupFlow` | Runs `UpdateFlow`, then connects; Retry, Continue, Update, Play offline. Blocks entry while a required update is not installed. |
| Service seams | `UpdateService`, `UpdateInstaller`, `UpdateRestarter`, `ServerConnector`, `OfflineLauncher` | Blocking calls run on the `deckduel-startup` worker thread. `client/update/ReleaseChannel` supplies the real implementations (`HttpUpdateService`, `StagedInstaller`, `HelperRestarter`) when a release channel is packaged. |
| Installed version | `InstalledVersion` | Read from `deckduel/version.properties`, which Maven fills from `deckduel.version` (the pom version unless a release build overrides it). |
| Finishing an update | `client/update/UpdateFinisher` | Holds new matches while an update downloads or waits, closes a waiting room, and once the update is verified hands over to the helper and closes the game, but only when no match is in progress. |
| Server route | `ServerRoute`, `Endpoints` | Development overrides, then the packaged production server (TLS), then the saved setting or the local development server 127.0.0.1:5555 (`docs/README.md#connections`). |
| Offline play | `client/OfflineServer` | After a failed connection, **Play offline** starts the ordinary `GameServer` inside the client (loopback only, temporary database), registers the account `offline` with a random password for that launch, and opens the main menu, labelled "Offline play". The Play page hides Create match and Join a game, keeps Practice and Match history, and says that history and replays last only for this session. Closing the client stops the server and deletes its folder. |
| Settings | `LobbyPanel` Settings page, UPDATES section | Check for updates, the installed version, the result, and Install update / Retry update while a release is offered; a verified update restarts the game once no match is in progress. |
| Required update after login | `ClientModel.requireUpdate` | A required release found later (from Settings) is remembered for the session. A match in progress continues untouched; Create match, Join a game, Practice and Rematch are refused with "Version X is required…", and the Play page says so. Match history and replays stay available. A waiting room is closed with `CancelRoomRequest` (protocol 15), including a room whose creation was already on its way. |
| Installing after login | `ClientModel.setUpdating` | While an update downloads, verifies or waits to restart, new matches and rematches are refused the same way and a waiting room is closed. A failed install allows matches again. |
| Fixtures | `StartupFixture` | Controlled local stand-ins that show each state on demand (below). |

#### States and buttons

| State | Buttons |
|---|---|
| Checking for updates | none; a brass bar sweeps while any step with no buttons is working (still with reduced motion) |
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
| Connection failed ("Cannot connect to the server."; the technical reason, such as a certificate problem, is written to the client log, not shown) | Retry, Play offline, Quit |
| Server requires a newer client | the update step again, without Continue; if no update is offered, a failure message with Retry |
| Starting offline play / Offline play | none; opens the main menu |

The gate before login is unchanged: a required update found at launch still blocks login.

Once the client has connected, the login screen opens. Login and registration reuse that
connection. If the connection drops before login, the login form reconnects as before.

#### Threads

`StartupFlow` and `UpdateFlow` belong to the Swing EDT, as `ClientModel` does: actions and
listener callbacks happen there. Service calls, the settle pause and offline server startup run on
one daemon worker that `ClientFrame` owns. Results come back through `SwingUtilities.invokeLater`.
Work handed over after the window closes is discarded. An action that does not fit the
current state is ignored, so a double click never starts a second check, install or connection.

### Seeing each state locally

This repository's default build packages no release channel, so a normal launch shows **Updates
not set up** and then connects to the local development server:

```sh
java -jar target/deck-duel.jar server --port 5555 --db data/deck-duel.db
java -jar target/deck-duel.jar client --host 127.0.0.1 --port 5555
```

Without the server running, the client shows **Connection failed**, with Retry and Play offline.

A real update between two local versions is shown by `tools/update-demo.sh` (`docs/README.md#updates`).

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

### Open items

- **Release channel.** No bucket or endpoint is configured, so default builds report "Updates not
  set up". The real check, download, verification, installation and restart exist and are tested
  locally (`docs/README.md#updates`).
- **Production server.** Builds may package a production endpoint (always TLS; `docs/README.md#connections`);
  without one the client uses the saved setting or the local development server. The operator
  supplies the hostname and certificate (`docs/README.md#deployment`).
- **Policy choices to confirm:** whether an unreachable update service should allow Continue (it
  does now, because the server's protocol handshake still rejects incompatible clients), and
  whether Settings should push a player out when a required update appears mid-session (it only
  blocks the next match now).
- **Offline play limits:** the offline account, its history and its replays are discarded when the
  client closes, on purpose for now.
- **Room cancellation race:** a join that the server handles before the cancellation arrives still
  starts a match; that match is then played to the end, like any match in progress.

### Deployment boundaries

- **Client updates** download from a static HTTPS location such as an S3 bucket and restart only
  that player's application. They never require restarting the multiplayer server. The updater is
  implemented (`docs/README.md#updates`); no bucket, upload or signing key exists yet.
- **Server restarts lose active matches.** Every `MatchSession`, held seat and reconnect grace timer
  lives in memory, so restarting the server process ends every match in progress. Those matches get
  no result row, and their replays stay unfinished. Drain mode stops new matches, lets existing ones
  finish, and `deckduel-switch-release` restarts only when that is safe (`docs/README.md#connections`,
  `docs/README.md#deployment`). Zero-downtime server updates are not supported.

---

<a id="updates"></a>

## Automatic client updates

Player journey: **Launch → Check for updates → Update → download and verify → the game closes →
install → the game starts again**, with no website, installer download or reinstall. Updates
change only that player's application; the multiplayer server keeps running.

No release bucket, endpoint or signing key is configured in this repository. Default builds say
"Updates not set up" and check nothing. Everything below runs locally and is tested; publishing to
S3 is done manually by the operator (`docs/README.md#deployment` part H). The game connection's TLS and
the packaged production endpoint are described in `docs/README.md#connections`.

### Parts

| Part | Package / file | Responsibility |
|---|---|---|
| Packaged channel | `src/main/filtered-resources/deckduel/update.properties`, `client/update/UpdateConfig` | Metadata address and trusted Ed25519 public key, set at build time with `-Ddeckduel.update.url` and `-Ddeckduel.update.publicKey`. Only `https://` is accepted, except `http://` to 127.0.0.1/localhost for local development. Empty means not configured; half-set or invalid values are reported as a failed check. |
| Update check | `client/update/HttpUpdateService`, `ReleaseMetadata` | Fetches `latest.properties` and `latest.properties.sig` (64 KiB / 1 KiB limits, 10 s connect, 30 s read timeouts, no credentials), verifies the Ed25519 signature over the exact bytes, and only then parses and decides. |
| Download | `client/update/PackageDownloader`, `Http` | Streams the package to `staging/<name>.part` with progress, resumes with HTTP Range after an interruption (or starts again if the server ignores Range), stops at the signed size (hard cap 768 MiB), and renames the file only when size and SHA-256 match. Three attempts, two seconds apart, with messages a player can act on. |
| Staging | `client/update/StagedInstaller` | Requires a launcher-started installation, checks the platform, downloads, unpacks into a new `versions/<version>` folder and verifies every file. Nothing is activated. |
| Hand-over | `client/update/HelperRestarter`, `UpdateFinisher` | Once verified and no match is in progress, starts the new version's `deck-duel-launcher.jar activate …` with only the installation folder, the version and this process id, then the game saves its preferences and exits. |
| Launcher and helper | `launcher/LauncherMain` (its own jar, JDK classes only) | `launch` starts the active version (with rollback). `activate` waits for the game to close, verifies the version again, switches the pointer and starts the game. `install` sets up an installation from a package (development and tests). `setup` does the same for the Windows installer but keeps a newer active version (`docs/README.md#windows-installer`). |
| Install layout | `launcher/InstallLayout` | Per-user folders, the atomic pointer, verification, activation, rollback and pruning (below). |
| Packages | `launcher/PackageManifest`, `PackageArchive` | Package format and safe extraction. |
| Versions | `launcher/ReleaseVersion`, `PlatformId` | `MAJOR.MINOR.PATCH[-label]` ordering (`1.10.0` > `1.9.0`, `1.2.0-rc.1` < `1.2.0`) and platform ids such as `mac-aarch64`, `windows-x64`, `linux-x64`. |
| Release tooling | `release/ReleaseTool`, `NativeLibraries`; `tools/update-demo.sh`, `tools/DemoUpdate.java` | Keys, packages, signed metadata, and a local end-to-end demonstration. |

### Release metadata

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

### Package format

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

### Installation layout

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

### Session rules

- An update is never installed and the game never closes during a match. A verified update waits
  until the match finishes or is left ("Update X is ready. Deck Duel restarts when this match ends.").
- While an update downloads, verifies or waits, Create match, Join a game, Practice and Rematch are
  refused, and a waiting room is closed with `CancelRoomRequest` (added in protocol 15). A room whose
  creation was already on its way is closed as soon as it arrives. The same happens when a
  required update is found from Settings.
- Match history and replays stay available throughout.
- The pre-login gate is unchanged: a required update blocks login until it is installed.

### Release tooling

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
that Java runtime (`docs/README.md#windows-installer`); elsewhere it is the player's own Java 17+.

#### Local demonstration

`tools/update-demo.sh [gui|headless]` builds 1.0.0 and 1.1.0 with a local channel packaged in,
packages and signs them with a throwaway key, serves them from 127.0.0.1, installs 1.0.0 into a
temporary installation and updates it:

- **gui:** you press Update on the startup screen.
- **headless:** `tools/DemoUpdate.java` makes the same calls as the Update button.

It waits until 1.1.0 is active and has confirmed its start. It runs `mvn clean package` twice.

### Verification

See "Verification evidence" below.

### Not done

- No S3 bucket, upload, CloudFront, AWS credentials or release pipeline; no production key.
- No production endpoint, certificate or bucket is configured in the repository; the operator supplies them at build and deploy time.
- A Windows x64 installer with a bundled Java runtime and shortcuts exists (`docs/README.md#windows-installer`),
  but it has not been run on Windows yet. There is no macOS or Linux installer (the first installation
  there is made with `deck-duel-launcher.jar install`) and no code signing of the application itself.
- Automatic updates never replace the installer's bundled Java runtime; a new Java version needs a new installer.
- A version that was rolled back is recorded as `failed` but may be offered again by the next
  check. Skipping it automatically is not implemented.
- No delta updates, and no metadata expiry. Replayed old metadata cannot downgrade, but it can
  hide a newer release until fresh metadata is fetched.
- Windows and Linux were not run; see the evidence below.

### Verification evidence

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

---

<a id="windows-installer"></a>

## Windows installer

`DeckDuel-Setup-<version>.exe` is what Windows players download and run once. It needs no Java
installation and no administrator rights. After installing, players start Deck Duel from the Start
menu (or the optional desktop shortcut). Updates then arrive through the automatic updater
(`docs/README.md#updates`), and the game connects to the production server packaged into the build
(`docs/README.md#connections`).

### Parts

| Part | File | Responsibility |
|---|---|---|
| Build script | `launcher/windows/build-installer.ps1` | Run on Windows x64. Builds and tests a copy of the repository with the server address and update channel packaged in, makes the update package, links the Java runtime, checks that runtime against the package, and compiles the installer. Nothing is uploaded or signed. |
| Runtime modules | `launcher/runtime-modules.txt` | The Java modules `jlink` puts in the bundled runtime: all of Java SE plus the `jdk.*` modules JavaFX, Swing interop and TLS need. Modules that the linking JDK lacks are skipped. |
| Installer script | `launcher/windows/DeckDuel.iss` (Inno Setup 6.3+) | Per-user install, shortcuts, the setup step, upgrade and uninstall. Its `AppId` must never change. |
| Icon | `launcher/windows/deck-duel.ico` | The compass emblem from the card back (`assets/cards/card-back.png`), 16–256 px. |
| Setup step | `launcher.InstallLayout.setUp`, `deck-duel-launcher.jar setup` | Installs and activates the bundled package, unless a newer version that still verifies is already active. A rerun repairs a damaged pointer or version folder. |

### What gets installed

```text
%LOCALAPPDATA%\Programs\Deck Duel\        the installer's program folder
    runtime\                              bundled Java runtime (java.exe, javaw.exe, cacerts)
    deck-duel-launcher.jar                used once, for the setup step
    deck-duel-<version>-windows-x64.zip   the update package for this version
    deck-duel.ico
%LOCALAPPDATA%\DeckDuel\                  the updater's installation (docs/README.md#updates)
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
   after the game closes (`docs/README.md#updates`). The helper also refreshes the launcher copy the
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

### Building

**Run on Windows x64**, once per release, from the repository:

Prerequisites (install once):

- A JDK for Windows x64. Temurin 21 is recommended (the client targets Java 17, and the
  project's Windows tests ran on Temurin 21.0.10). Its `jlink` makes the bundled runtime, so the
  runtime players get is that Java version.
- Maven on `PATH`.
- Inno Setup 6.3 or newer: `winget install JRSoftware.InnoSetup`.
- The release public key file from `release keygen` (`docs/README.md#updates`). The private key is not
  needed here.

```powershell
powershell -ExecutionPolicy Bypass -File launcher\windows\build-installer.ps1 -Version <VERSION> `
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

### Code signing and SmartScreen

The installer and the game are not code-signed. Windows SmartScreen will show "Windows protected
your PC" for a new unsigned download. Players choose **More info → Run anyway**. The warning fades
only with a code-signing certificate (a paid product) and its reputation. To sign later, add
`SignTool` to `DeckDuel.iss` and sign `DeckDuel-Setup-<version>.exe`. The update packages are
protected separately by the Ed25519 release signature.

### Limits

- **The runtime is fixed per installer.** Automatic updates replace the game and launcher jars,
  never the bundled Java runtime. A release that needs a newer Java version, or a Java module
  missing from `runtime-modules.txt`, must ship a new installer, and players must run it.
- **Windows x64 only.** No Windows ARM64, macOS or Linux installer. Those players can still use the
  update package with `deck-duel-launcher.jar install` and their own Java 17+ (`docs/README.md#updates`).
- **The game process is `java.exe`.** Task Manager lists the bundled runtime's
  "OpenJDK Platform binary". A pinned taskbar shortcut may be shown apart from the running game's window.
- **Per-user only.** Each Windows account installs its own copy. There is no all-users install.

### Verification

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
5. Publish a newer version (`docs/README.md#deployment` part H): Update installs it and the game restarts.
6. Run the older installer again: the newer version stays active.
7. Uninstall: the program folder and `%LOCALAPPDATA%\DeckDuel` are gone, and `%USERPROFILE%\.deckduel` remains.

<a id="macos-app"></a>

## macOS app

`DeckDuel-<version>-mac-<arch>.dmg` is what Mac players download once: they drag **Deck Duel** to
Applications and open it. Like the Windows installer it carries its own Java runtime, so no Java
installation is needed, and the game server address is packaged in, so the game connects to the
server (over TLS) as soon as it opens.

Build it on a Mac; the app runs on that Mac's processor (Apple silicon `mac-aarch64` or Intel
`mac-x64`). The update channel is optional; without it the game reports "Updates not set up".

```sh
launcher/macos/build-app.sh <VERSION> <GAME_HOST> <GAME_PORT> [<RELEASE_URL> <release.pub>]
```

| Part | File | What it does |
|---|---|---|
| Build script | `launcher/macos/build-app.sh` | Builds and tests a copy of the repository with the server address (and the release channel) packaged in, writes the update package, links the runtime with `jlink`, checks that runtime can set up the package and load the game, then makes `Deck Duel.app` and the `.dmg` with `jpackage`. |
| Runtime modules | `launcher/runtime-modules.txt` | Shared with the Windows installer. |
| Icon | `launcher/windows/deck-duel.ico` | Converted to `.icns` during the build. |

On every start the app runs `deck-duel-launcher.jar start`: it sets up its bundled package in
`~/Library/Application Support/DeckDuel` (keeping a newer version an automatic update installed,
as the Windows `setup` step does), then starts the active version. Updates replace only that
folder, never the app.

Limitations: the app is not signed or notarized, so the first time players must right-click it and
choose **Open** (or allow it in System Settings → Privacy & Security). Verified on macOS arm64
(2026-10-29): build, bundled-runtime setup, and the app starting the packaged game, which dialled the
packaged host over TLS. Not verified: Intel Macs, a real server, notarization.

---

---

<a id="card-catalog"></a>

## Card catalog — `cards-neutral-5`

The game reads one file, `src/main/resources/cards/catalog.txt`; no code names a card. Its
version, `cards-neutral-5`, is recorded with every match result. `CatalogLoader` validates it
at startup: every nation has exactly one Unit of each class, a class prints the same points in
every nation, and Spells are neutral. Landmarks and the Sabotage Spell were removed on
2026-10-21; matches recorded under `cards-neutral-2` and earlier had them.

### Units — from the painted card art

Seven nations (Ashkar, Asterion, Dravorn, Nerath, Solmara, Sylvaren, Vaelora) × five classes =
35 Units. Each Unit has exactly one nation, one class and no ability. Its id is
`<nation>-<class>` in lower case (for example `nerath-knight`), which also names its art file.

| Class | Printed points |
| --- | --- |
| Assassin | 2 |
| Bowman | 3 |
| Guardman | 4 |
| Knight | 5 |
| Wizard | 6 |

The nations, classes and points are those printed on the painted card art, adopted at the
user's request on 2026-10-22 (`cards-neutral-5`). They replaced Hungary, Poland, Bohemia,
Austria, Styria, Croatia and Transylvania with Pikemen 2, Bowmen 3, Riders 4, Knights 5 and
Axemen 4 (`cards-neutral-4`). Earlier versions: `cards-neutral-1` used Footmen, `-2`/`-3`
Battering Ram. Pairs and formations compare class tags, never points.

Chaplain and Envoy, and the earlier Infantry/Cavalry/Archer/Clergy/Noble tags, are retired.

### Deck — approved

Every player receives the same 45 cards: all 35 Units and one copy of each of the 10 Spells,
shuffled independently by the server (`DeckRules.deck`). Players never build or choose a deck.
The eleventh Spell slot, left by Sabotage, is open until a new Spell is designed.

### Spells — neutral, one copy each

| Card | Effect (engine) | Target |
| --- | --- | --- |
| Disable Tags | `DISABLE OPPONENT TAGS` until the round ends | an enemy Unit |
| Disable Points | `DISABLE OPPONENT POINTS` this round; its combos still count | an enemy Unit |
| Destroy Unit | `DESTROY OPPONENT`: the Unit leaves the row with its points and combos | an enemy Unit |
| Random Discard | `RANDOM_DISCARD 1` from the enemy hand (server picks) | none |
| Random Steal | `RANDOM_STEAL 1` from the enemy hand into yours (server picks) | none |
| Draw Two | `DRAW 2` from your deck (not a swap) | none |
| Exchange | `EXCHANGE 1 2`: discard the chosen card, then draw 2 | another card in your hand |
| Reconnaissance | `RECONNAISSANCE 3`: look at the top 3, keep 1, the rest to the bottom | none; then a private keep choice |
| Spy | `SPY 3`: three random enemy hand cards are revealed to you only | none |
| Clear Disable | `CLEAR_DISABLE`: removes the disabled points and tags | one of your disabled Units |

A Spell whose target type has no legal target may still be cast, to no effect, so a hand of
Spells never leaves a player without a play.

### Formations — Balatro-style; values PROVISIONAL

Like a poker hand in Balatro, each row is checked for its best formation; only that one counts.
Every nation formation has a class twin of equal value; when both apply, the strongest wins.
Units with disabled tags do not count toward a formation. Values are in `core/card/Formation`.

| Formation | Needs | Bonus | Mult |
| --- | --- | --- | --- |
| Company | 3 Units of one class | +10 | ×1 |
| Retinue | 3 Units of one nation | +10 | ×1 |
| Two Companies | 2 Units each of two classes | +15 | ×1 |
| Two Banners | 2 Units each of two nations | +15 | ×1 |
| Three Companies | 2 Units each of three classes | +25 | ×1 |
| Three Banners | 2 Units each of three nations | +25 | ×1 |
| Full Regiment | 3 of one class + 2 of another | +20 | ×2 |
| Full Muster | 3 of one nation + 2 of another | +20 | ×2 |
| Regiment | 4 Units of one class | +25 | ×2 |
| Royal Line | one Unit of each class | +30 | ×2 |
| Banner Host | all 5 Units of one nation | +40 | ×3 |

### Score order

Row total = (printed points + pair bonuses + formation bonus) × formation multiplier, then the
all-seven-nations combo (+15, `Scoring.ALL_NATIONS_BONUS`, provisional) and King bonuses are added unmultiplied.
A Unit with disabled tags makes no pairs, does not count toward a formation and does not count
toward the seven nations; disabled points remove its printed points. Example: Ashkar's
Assassin, Bowman and Guardman with Nerath's Assassin and Bowman make a Full Muster: 14 printed
points and 18 pair points (three Ashkar pairs, one Nerath pair, two class pairs) score
(14 + 18 + 20) × 2 = 104.

### Kings — separate catalog, never in the deck

`core/card/King` defines the seven Kings, IDs, nations, ability names, descriptions and numerical
parameters copied from the supplied paintings. `KingStatus` stores at most two distinct owned
Kings and accepted round counters; `KingScoring` derives the live per-King breakdown. Kings
are offered after the deals of rounds 1 and 2 and remain in round 3. See [King Cards](#kings)
for all formulas, simultaneous selection, Secret Edict and privacy. The draw catalog version
remains `cards-neutral-5` because its 35 Units and 10 Spells are unchanged; wire protocol is 18 (including the 30-second selection deadline).

### Artwork

Card faces are chosen in `CardFace`:

1. **Painted Units** (`PaintedCardArt`): `src/main/resources/assets/cards/<id>.png`, 744×1039,
   one per Unit, plus `card-back.png` for the shared back. Each nation has its own cloth colour,
   ribbon and crest; each class has its own figure. The painting prints the nation, class and
   points, so a Unit uses it only while its id, class and points match what is printed;
   otherwise it falls back to the procedural face. Paintings are centre-cropped to the table's
   2:3 card shape (a few pixels of ribbon at each side are trimmed), given rounded corners and
   scaled down in halving steps. The client decodes them on a background thread at start-up.
2. **Painted Spells** (`PaintedCardArt`): `src/main/resources/assets/cards/<id>.jpg`, one per
   Spell (delivered 2026-10-23), each printing its name and effect text. A Spell uses its
   painting only while its catalog effect matches the printed one (count and target included).
   They are fitted to the card like the Units. The Clear Disable and Reconnaissance files have a
   thin black band along their top edge (25 and 17 rows), which shows on the table.
3. Any other card falls back to a procedural face (`CardArt`).

King paintings are unmodified PNG copies in `assets/cards/kings/`, fitted at their original
aspect ratio, cached and decoded off the UI threads by `KingArt`. No image text is parsed at runtime.

The Unit paintings add about 28 MB to the jar and the Spell paintings about 4 MB.
The menu and match-room background artwork are retained. The Atlas section is removed.

---

<a id="kings"></a>

## King Cards — implemented 2026-10-25

Kings are a separate seven-entry immutable catalog (`core/card/King`), outside the unchanged
45-card draw deck, hand and seven-space Unit row. There is no currency, shop, reroll, upgrade,
generic authority bonus. A server-owned selection deadline is described below. Values follow the supplied paintings and
still need playtesting; this change makes no balance or performance claim.

### Acquisition and visibility

After dealing the ten-card opening hand of rounds 1 and 2, `MatchState` enters `KING_SELECTION`.
The server derives a seeded permutation of all seven Kings: the first three form the shared
round-one offer; the next three, drawn from the four not previously offered, form round two's
shared offer. The seventh is not offered. Both seats choose independently, including the same
King if desired. Each seat owns at most two distinct Kings. Choices spend no plays or draws.

`ChooseKing` records a private confirmation on that seat. `PlayerView.selectedKing` contains
only the recipient's confirmation; the opponent's public `KingStatus` retains only earlier
rounds' Kings. Public events say only that a confirmation occurred. Both new Kings become public
together when both confirmations exist. Ordinary play/draw/skip is blocked until then. The
normal round starter is preserved. Round 3 retains both Kings and has no selection.

Disconnect holds this state through the existing grace period; reconnect projects it again.
A forfeit can end a match during selection. A rematch starts a new state with new offers, empty
ownership and reset counters. The deadline continues during the reconnect grace period; a
forfeit that ends the match cancels it.

### Selection deadline and hand preview (approved 2026-10-25)

Each selection phase lasts 30 seconds from the server's opening deal. `KingChoiceDeadline`
opens one shared deadline for that game/round. A confirmation, hidden overlay, rejected request
or reconnect never resets it. `MatchSession` schedules its callback onto the match executor;
at expiry each unconfirmed seat confirms the first (leftmost) offered King through the normal
immutable engine transition. Existing confirmations remain unchanged. Automatic choices spend
no plays/draws and each accepted revision is recorded. Leaving selection or ending a game
cancels the timer; game/round/deadline guards discard obsolete callbacks after rematches.
Late actions also check expiry on the executor before ordinary validation.

Protocol 18 adds `MatchUpdate.kingChoiceDeadlineMillis` (epoch milliseconds, zero outside
selection). The client uses it only for a countdown, never to submit a default choice itself.
It is live session metadata: the existing `PlayerView` replay format is unchanged, and earlier
King replays remain compatible. Automatic confirmations appear in normal recorded views.

The centered teal eye-icon button below the offer toggles only the overlay, without a visible caption.
While hidden, the dealt hand stays visible. Unit/Spell hover popups are disabled throughout
King selection (including while hidden); King inspection remains available. Play/draw/skip remain blocked and
the countdown continues, with a small time display at the top. The three original paintings rise, scale and fade in from left to
right when shown, with no new artwork or sound. Reduced motion bypasses this animation.
Select a painting, then use its `CONFIRM` button; the button contains no King name and is enabled
only for the selected painting. Offered paintings glow in their King's color on hover/focus or selection.
King slots match Unit-row height, keeping each original aspect ratio. Empty positions have no
numbered label; occupied positions show only `+N` in the score rail's Outfit display font and
the King's color. Each deployed painting has a persistent, static edge aura of that color, with
clearance from the Unit rows and controls. `KingVisuals` owns these presentation colors outside core.
King hover shows only the original painting, capped at 480 logical pixels high (58% of the table
height on smaller windows). The click inspector has the same 480-pixel height cap and still provides
contribution/status. Aura does not animate, including with reduced motion enabled.

### Abilities and score order

Only Units without `Suppression.TAGS` contribute to King row conditions. Disabled printed points
do not disable tags. Bonuses are recalculated from the current row after destruction, replacement,
tag disables and clearing; repeated calculation never accumulates points.

| King | Nation / ability | Current round contribution |
| --- | --- | --- |
| Varkhan | Ashkar / Assault | +2 per active Assassin or Bowman, cap +10 |
| Altharion | Asterion / Royal Council | +2 per distinct active class, cap +10 |
| Mordraven | Dravorn / Legion | `min(10, 3 × max(0, largest active class group − 2))`; tied groups count once |
| Nethrion | Nerath / Secret Edict | First accepted swap each round privately looks at two for its last replacement, keeps one, bottoms the other; no score bonus |
| Aurendor | Solmara / Royal Decree | +3 per accepted Spell played this round, cap +9, while at least one friendly Unit has active tags |
| Thalvaren | Sylvaren / Alliance | +2 per distinct active nation, cap +10 |
| Vaelthor | Vaelora / Twin Banners | +5 per nation with at least two active Units, cap +10; a larger group still counts once |

`ScoreBreakdown.kings` lists every owned King's contribution, including zero. `KingScoring`
calculates these separately from formations:

```
(Unit points + pair points + formation bonus) × formation multiplier
    + all-seven-nations bonus + sum(King bonuses)
```

The provisional +15 seven-nation bonus and formation values are unchanged. End-of-round banking
uses this complete score once. Winner, result transaction, W/L and historical rows are unchanged.

### Secret Edict and counters

`KingStatus` stores public ownership, accepted Spells played and whether Secret Edict has been
used this round. Dealing a new round preserves ownership and resets both counters. A valid
no-target Spell counts; a rejected action or an accepted-request retry does not count again.

The first accepted swap (`SwapCards`) with Nethrion spends its swapped cards from the allowance
as usual and draws all but its last replacement normally. With at least two cards then left, it
removes the top two into the owner's private `looking` list and sets
`ChoiceKind.SECRET_EDICT`. `KeepEdictCard` adds the selected card to hand, puts the remaining
card at the bottom, clears the pending choice and retains the same turn. Normal play, swap,
skip and the Reconnaissance-specific `KeepCard` are rejected during this choice. With one card
left it draws normally and marks the ability used. A swap larger than the deck is rejected
without spending a swap or the ability. Spell draws and the deal never trigger it. Reconnaissance
continues to use `ChoiceKind.RECONNAISSANCE` and `KeepCard`, ending its cast's turn on completion.

### Modules and routes

- `core/card`: immutable state, `King`, `KingStatus`, `KingBonus`, `KingScoring`, `ChooseKing`,
  `KeepEdictCard`, `ChoiceKind`; no UI, I/O, DB or networking. Easy and hard bots use only their
  own view for both new choices and score King contributions in public-board evaluations.
- `server/MatchSession`: existing single executor, revision checks, accepted-request-ID retry,
  disconnect grace, rematch and replay capture also cover the new moves. Its `awaits` check
  schedules bots for either seat's outstanding King confirmation independently of normal turns.
  `KingChoiceDeadline` and the existing scheduler enforce the 30-second fallback on that executor.
- `net/Message`: protocol **19**, explicit allowlist entries for every new wire type, unchanged
  serialVersionUID 1, socket/TLS, heartbeat, flush/reset and silence rules. Version 17 and earlier are rejected.
- `client/state/ClientModel`: permits the owner's King choice through the normal pending-action
  seam; selection counts as an active match for reconnect and deferred updates.
- `client/bot/CardBot`: protocol `bots`/`demo` handles both outstanding King selection and ordinary
  turns. Practice and offline play use the same authoritative engine/server.
- `client/ui/KingTableControls`: two slots per side beside the Unit rows, shared three-card offer
  with separate selection/confirmation, original-art hover and click inspector with current
  contribution and Edict status. It consumes snapshots and emits moves only. Opening-deal
  staging completes before the offer appears. Reduced motion shows the offer immediately.
- `KingArt`: cached background image decoding; paints the full original aspect ratio without
  cropping. The supplied originals are stored unchanged in `src/main/resources/assets/cards/kings/`;
  redundant import copies are not retained. SHA-256 checks cover all seven resources.
- `ScoreRail`/`ScoringTrace`/`PlayAnimator`: separate King contribution after formation, with a
  King tally pocket and existing sound cues. `ReplayPanel` renders the recorded King facts and
  labels Edict's private choice separately.

### Replay compatibility and privacy

The existing recorder stores each human recipient's `PlayerView` per accepted revision, so it
records offers, that owner's private confirmation, public ownership/counters/score breakdown and
only that owner's pending Edict cards. The opponent sees neither that confirmation nor those
cards. No seed, hidden draw pile or additional enemy hand cards enter the view. Ownership checks
on replay retrieval, practice-only replay storage and account/result persistence are unchanged.

The new required King/choice fields deliberately reject pre-King serialized frames through the
existing "recorded by another version" response. Existing accounts, result rows and stored
replay bytes are retained; no destructive migration or database reset is performed.

See [verification notes](#testing) for King selection, deadlines, presentation and privacy checks.

After both choices become public, each newly acquired King flies from its offered painting to
its owner's next King position in 650 ms, shrinking with its colored aura attached. The actual
slot and its points appear when the flight finishes. Newer match revisions wait for deployment;
draw, skip and card plays are held during the flight. Reduced motion, reconnecting, resizing or
leaving the table finishes the flight immediately. An initial snapshot with already owned Kings
does not replay it. Hidden offers still provide the same flight origins; private confirmations
alone never reveal the opponent's choice.

`KingDeploymentTest` checks both owners, the second-round position, repeated/initial snapshots,
hidden offers and cancellation. It writes `target/kings-qa/deployment-in-flight.png` as visual evidence.

---

<a id="how-to-play"></a>

## How to Play

The main menu's **How to Play** entry opens the **Quick guide**: seven steps down the left
(play cards, combos, formations, Spells, swapping, Kings, winning), each one line of text over a
short GIF clip. Previous/Next or the arrow keys move between steps; reduced motion shows each
clip's first frame. `GuideSteps` lists the steps and is shared with the match's pause menu
(Menu → How to play), so both teach the same thing.

The clips in `src/main/resources/assets/guide/` were recorded on 2026-10-29 from a real practice
match (seed 7) on the real `CardTableView`: the engine (`MatchState`) applied every move, the
greedy bot played the opponent, and scripted pointer input pressed, dragged and released on the
table as a player would. Frames were snapshot at 10 per second and encoded with ffmpeg
(palette + Bayer dither). The drawn arrow pointer is added to the frames; no
screenshot of the desktop was taken.

Back (or Escape anywhere on the page) returns to the menu. The page reads only bundled files and
sends nothing to the server. The Recorded lessons view (17 `.duel` training recordings shown in a
read-only replay) was removed at the user's request on 2026-10-29; personal replays stay under
History → Replay ([replay controls](#replays)).

### Reproducing the quick-guide clips (macOS/Linux, graphical session)

```sh
mvn -q -DskipTests compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
javac -cp "target/classes:$(cat target/cp.txt)" -d target/guide-tools tools/RecordGuideClips.java
java -cp "target/guide-tools:target/classes:$(cat target/cp.txt)" deckduel.client.ui.RecordGuideClips target/guide-frames 7
java -cp "target/guide-tools:target/classes:$(cat target/cp.txt)" deckduel.client.ui.RecordGuideClips target/guide-win 7 win
```

The first run writes `kings`, `play`, `combos`, `formation`, `cast`, `swap` and `win` frame folders;
the second re-records only the ending (the bot passes in round 3 so the clip ends in a victory) and
was used for `win.gif`. Encode each folder with
`ffmpeg -framerate 10 -i <dir>/f%04d.png -vf "scale=1920:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" -loop 0 <name>.gif`
(`play.gif` keeps its first 95 frames). Frames are rendered at 2x (2560×1440) and the GIFs are
1920×1080, about 36 MB in total; the How to Play screen shrinks a clip to fit but never draws it
larger than one clip pixel per screen pixel, so it stays sharp on HiDPI displays.

Separately, Unit/Spell hover popups are disabled during the entire live King-selection phase,
including when the eye button hides the offer. The hand remains visible and King inspection is
preserved; ordinary in-progress hover behavior is unchanged.

See [verification notes](#testing) for reproducible checks of the guide and its recordings.

---

<a id="replays"></a>

## Personal match replays

Player journey: **Play → Match history → select a completed match → Watch replay**. A replay
shows the match exactly as that player saw it, one recorded state per accepted action, with
Previous, Play/Pause, Next, a speed button (0.5×, 1×, 2×, 4×), the round and action position,
both sides' score breakdowns, and Back to match history.

### Responsibilities

| Part | Responsibility |
|---|---|
| `server/MatchSession` | After every accepted change (`changed()`), records each human seat's own `PlayerView` once per new state revision, whether or not it was delivered, and finishes the recordings when the game ends. Bots are not recorded. |
| `server/ReplayStore` | Boundary for recording and serving replays. `NONE` is for servers without a database. It never throws into a match thread. |
| `server/MatchPersistence` | Implements `ReplayStore` next to results and history: encodes and decodes frames, works out the owner's result, and merges practice replays into history. |
| `db/ReplayDao`, `db/SqliteReplayDao` | SQL for the `replays` and `replay_frames` tables. Every read is keyed by owner username. |
| `server/ClientHandler` | Answers `ReplayRequest` only after login, for the signed-in account. |
| `net/ReplayRequest`, `net/ReplayResponse`, `HistoryEntry.replay/replayId/practice` | Wire contracts (added in protocol 14), listed in `Message.WIRE_FILTER`. |
| `client/state/ClientModel` | Sends `ReplayRequest` from History, accepts only the reply it asked for while History is open, and switches to `Screen.REPLAY`. |
| `client/state/ReplayPlayback` | Playback position, play/pause and speed. It has no connection and no access to the live match. |
| `client/ui/HistoryPanel` | REPLAY column (Watch / Incomplete / Unavailable), Watch replay button, double-click. |
| `client/ui/ReplayPanel` | Swing screen over `match-room.png`, using the painted `CardFace` sprites. A Swing timer steps playback; reduced motion turns off the fading highlight. |

### Data flow

1. **Recording (server, match thread).** `newGame()` calls `ReplayStore.begin(owner, "<session>-<game>", opponent, practice, catalog)`
   for each human seat. Each `changed()` then compares `state.revision()` with the seat's last
   recorded revision and stores `state.view(seat)` once if it is new: the same view that seat's
   `MatchUpdate` carries. Reconnect broadcasts and answers to retried request ids do not change the
   revision, so they add nothing; `PRIMARY KEY (replay_id, revision)` with `INSERT OR IGNORE`
   guards the same rule in the database. When the phase becomes `FINISHED`, the last frame (with
   the outcome) is stored and `finish` writes the owner's result, end reason and round scores.
2. **Linking.** Online results get the same `<session>-<game>` key in the additive
   `matches.match_key` column. This happens inside the existing atomic result + W/L transaction.
   Replay rows are written separately and never take part in that transaction.
3. **History.** `historyFor` reads `matches` as before and attaches the requester's own replay by
   `(owner, match_key)`. It adds the requester's finished practice replays (practice never creates
   `matches` rows or changes W/L), then sorts everything newest first.
4. **Retrieval.** `ReplayRequest(id)` → `SELECT … WHERE id = ? AND owner_user_id = (the requester)`.
   A missing id and someone else's id get the same "Replay unavailable." reply. Frames are decoded
   with `Message.WIRE_FILTER`, the same allowlist as the socket.
5. **Playback (client, EDT).** `ReplayPlayback` holds the frames. `ReplayPanel` paints the
   current frame and never calls the connection; Back calls `ClientModel.closeReplay()`.

### Statuses

| Status | When |
|---|---|
| Watch (`AVAILABLE`) | Recording finished and every frame stored. |
| Incomplete (`INCOMPLETE`) | A recording exists, but a frame write failed or the result was never written. It can still be watched; the screen says "Incomplete recording: some actions are missing". |
| Unavailable (`UNAVAILABLE`) | No recording: matches from before replays existed (`match_key` is NULL), or the server had no database. |

A practice game cut off by a server stop never finishes, so it is not listed. An online game cut
off the same way has no result row, so it does not appear in history either. Frames written by an
incompatible build are refused with "This replay was recorded by another version and cannot be
shown." rather than partly shown.

### Live selection deadline (introduced in protocol 18)

Protocol 19 additionally carries the recipient's final pre-bank round view in live `MatchUpdate`
metadata, so the table finishes scoring before bank/deal/result presentation. This does not add
or change stored replay frames.

The server's 30-second King deadline is carried in live `MatchUpdate` metadata; it does not
change serialized `PlayerView` frames. Earlier King replays remain readable. Automatic first-card
confirmations are recorded through normal accepted revisions, preserving the same recipient
privacy and replay ownership rules. The client countdown never chooses a King locally.

### King compatibility (2026-10-25, protocol 17)

Frames now include shared offers, the owner's private confirmation, public King ownership,
round Spell/Edict counters and per-King score contributions. Pending Secret Edict cards are
recorded only in their owner's view and labelled separately from Reconnaissance in playback.
The opponent's recording has no pending card faces or unannounced King identity. Retry and
reconnect still do not add revisions. Old serialized frames lack required King/choice fields,
and are refused through the existing incompatible-version response, preserving all stored
replay data, accounts and results. See [King Cards](#kings).

### Privacy

- Frames are the owner's own `PlayerView`: their hand, their Reconnaissance cards and the cards
  their own Spy revealed, plus public rows and scores. `PlayerView` carries no deck order, seeds,
  RNG state or other opponent hand cards. The integration test checks, revision by revision, that
  each player's frames equal what they were sent and hold no opponent hand card that was not
  revealed to them.
- Retrieval requires login and ownership. No other account, spectator or public link can load a
  replay. Passwords and credentials are never part of a replay.

### Online and offline

Online replays persist in the server database. Offline replays are written by the private
`OfflineServer` into its temporary database, which `ClientFrame.dispose()` deletes when the
client closes. The offline menu and history say so.

### Measured size

A seeded greedy game (Apple M2, JDK 27, `ClientFixtures.replay`) has 65 frames per player. Stored
frames average 4,979 bytes (maximum 6,206), about 316 KB per player per match. The
`ReplayResponse` for that game serializes to 48,412 bytes, because one stream shares class
descriptors. Recording adds one SQLite insert per human seat per action on the match thread. This
has not been measured under concurrent load.

### Not done

- Sharing, spectating, exporting or deleting replays.
- Re-reading old frames after an incompatible change to the `core.card` records: such replays
  are refused, not migrated.
- A retention limit: online replays grow without bound.

### Reviewing a recorded match

History filters work on the entries already returned by the server. Type part of an opponent's
name, choose All results / Win / Loss / Draw, and choose All matches / Online / Practice.
The controls combine, ignore letter case using a locale-independent comparison, and treat search
text literally. They preserve newest-first order and never issue an additional history request.
Clear restores the loaded list. A filtered summary counts practice apart from online W/L;
filtering does not change the profile's records. An empty filtered list has a separate message
from an account with no matches. The server's existing history limit still applies: these controls
do not search older database rows beyond the loaded response.

Selection follows a replay's identity if refreshing or filtering moves its row. Filtering out the
selected match clears the selection and disables Watch replay. Unavailable recordings remain
disabled; incomplete recordings remain watchable and labelled. Filtering never changes replay
ownership checks, account state or database rows.

#### Timeline and chapters

The seek bar addresses recorded frame positions, starting at zero. Dragging pauses playback
immediately and previews the selected position while dragging; releasing leaves it paused. First,
Last and the round selector also pause. Previous round goes to this round's first recorded frame,
then to the preceding round on a second press. Next round goes to the next recorded round and
does not wrap. A shortened training excerpt may begin halfway through a round; its chapter starts
at the first frame present, rather than inventing the missing beginning. A one-frame excerpt has
a disabled slider. Play at the end restarts at the opening recorded position.

| Key | Action |
| --- | --- |
| Space | Play / pause |
| Left / Right | Previous / next recorded action |
| Home / End | First / last recorded position |
| Page Up / Page Down | Previous / next recorded round start |
| Escape / Backspace | Return through the page's existing Back action |

Keys belong to the replay page, not the entire application window. Focused text fields and
standard Swing controls retain their editing/navigation keys. Leaving the page stops its timers;
opening another recording detaches the previous playback observer and resets the journal filters.

#### Action journal

Details replaces the right-hand score rail with two tabs. Actions lists each stored position with
its original frame index, round and added public log lines. Selecting a row seeks to that exact
frame and pauses. Filters for Spells, Kings, round changes, result and recording gaps combine
with Key moments and literal text search. Changing the filter never moves playback. When the
current position is absent from the filtered journal, no row is selected.

`ReplayIndex` builds this metadata once from the recording. Spell markers use a changed public
`lastPlay`, King markers use recipient-visible confirmations/ownership, and round/result markers
use structured phase and round data. It does not classify gameplay by guessing from translated
log text. Revision gaps are marked without synthesizing actions. A private enemy King or unseen
hand card cannot be inferred by this index: it has no access to another recipient's recording.

#### Score changes

Score changes compares each side's stored live score to the preceding stored frame:

- Unit printed points and pair combo points.
- Formation name, its bonus and the multiplier before/after.
- The extra points caused by multiplication: `(Units + Combos + Formation bonus) * (multiplier - 1)`.
- The unmultiplied seven-nation bonus and aggregate/per-King contributions.
- Live total change and the separate change in banked match total.

The six aggregate component differences reconcile exactly with the live score difference.
Per-King lines explain the Kings aggregate and are not added a second time. The opening frame
shows no fabricated gain. Round boundaries explicitly include clearing, banking and dealing;
their negative live-score changes must not be interpreted as a Spell penalty. Stored replay
frames do not necessarily include the separate final pre-bank snapshot supplied to the live
table, so this view explains snapshot differences rather than reconstructing that missing state.
If revisions are missing, the drawer warns that a difference may cover multiple actions.

#### Client responsibilities and verification

`ReplayPlayback` owns pause/seek/round navigation and the immutable `ReplayIndex`.
`ReplayScoreChange` compares stored scores without evaluating game rules or mutating frames.
`ReplayTimeline` and `ReplayDetailsPanel` observe that same playback through `ReplayPanel`;
neither receives a connection. `HistoryFilter` and `HistoryFiltersPanel` filter the authorized
response on the EDT. No core, wire contract, schema or server authorization change is required.

How to Play uses a simpler presentation of this surface: only Next and Details, a compact action
and score explanation, and no journal, search, timeline, round jumps or automatic playback.
Personal replay controls described above remain available in Match history.

Regression tests include `ReplayNavigationTest`, `ReplayScoreChangeTest`, `HistoryFilterTest`,
`ReplayReviewTest` and `HistoryFilteringTest`. The real-server `ReplayJourneySystemTest` follows
Login → History → Replay → seek/journal → Back → filter/clear. It uses a temporary database,
ephemeral loopback port and a real recorded practice game. UI checks write reproducible images
to `target/replay-qa/` at 1280×720 / 1960×1080 (replay) and 1280×660 / 1960×1080 (history).

---

<a id="card-table-design"></a>

## Card table direction

The live screen is the JavaFX `CardTableView`.

Spell hover details render above deployed Kings and their aura, including at the right edge of
the hand. Result/confirmation dialogs and the Spy inspector remain above the hover layer;
the details remain mouse-transparent so they do not intercept card input.

Dragging uses total distance from the original press, so many small mouse events still pick up
the card. Pressing wakes the hand's lift immediately. The dragged face/shadow renders in a
mouse-transparent layer above Kings/HUD and below modal dialogs. Cancelled/reordered hand cards
ease back from the actual release point; reduced motion snaps them. Esc, hidden/finished/new
matches, removed held cards and a fresh press discard obsolete gestures. Cards still hidden in
the deal or a play cannot be picked up before they are visible. Unit hover enlargement is removed;
Spell hover text, King previews and explicit Spy inspection remain.

King Cards (2026-10-25): a reserved strip between the seven-space rows and the deck/discard
piles contains two King positions per side, each matching the Unit-row height. `KingTableControls` uses the original uncropped art,
a three-card selection/confirmation overlay and an original-art inspector with current flat
contribution and Nethrion's round status. Selection waits for opening-deal staging; reduced
motion displays it immediately. The choice rises/fades in with staggered original paintings;
a centered teal eye-icon button below it hides/shows the offer to inspect the hand. It has no visible
caption; its eye/slashed-eye icon changes with the action. A small top countdown remains while hidden.
Unit/Spell hover popups are disabled for the entire King-selection phase, including while the offer is hidden.
The shared server deadline continues while hidden, with a 30-second fallback to the first offered King for every
unconfirmed seat. Offered cards have a King-colored edge aura on hover/focus and selection;
confirmation buttons say only `CONFIRM`. Deployed Kings retain a static colored edge aura and show
only their `+N` contribution, using the rail's Outfit display font and the matching King color.
Empty positions have no labels. Hovering a King shows only its painting, capped at 480 logical pixels
high (58% of table height on smaller windows); it has no text tooltip. The click inspector uses
the same 480-pixel cap. Gutters leave clearance for the glow. The
hand uses the available width below the Unit/King rows with clearance for the toggle. `KingArt` caches background decoding. The score rail has a
separate Kings line below formation; scoring chips tally it after the multiplier. Secret Edict
uses the private keep overlay with its own heading and `KeepEdictCard` move, preserving the
turn; Reconnaissance remains separately labelled and ends the cast's turn. All these facts come
from the recipient's server view. See [King Cards](#kings).

Use the existing `/assets/match-room.png` across the entire game screen. Keep the room and wooden tabletop visible; cards sit directly over the scene. Remove the chess grid when the card-game screen replaces the legacy screen. Use subtle brass outlines, warm ivory text, soft card shadows, and restrained local shading for contrast rather than a large opaque board panel.

- Top: round number, opponent's concealed hand, and quiet settings/leave controls.
- Table: opponent's seven-space Unit row above the player's (Landmarks were removed on 2026-10-21). The Unit rows use the full central table width, with larger card faces. Mark empty spaces faintly. Row headings show only their names; scores stay in the rail, whose PLAYS: YOU / FOE counter shows both players' remaining plays.
- Middle: a small current-turn indicator and fine dividing lines. Hover details remain available; the play-preview line and last-move announcement are removed.
- Results: show the outcome, final match totals and rematch/menu buttons without the round-by-round score line. A forfeit still shows its reason.
- Left: one fixed Balatro-inspired rail with stacked inset score wells, a large player total, a smaller opponent total, and two adjacent calculation tiles. Keep remaining plays, round, and cumulative total together below the score.
- Right: deck/discard access. In implementation, distinguish each player's piles and counts; never reveal hidden draws or opponent card faces.
- Bottom: the player's hand (ten cards at the start of a round; it overlaps when it grows wider than the table). Raise and outline the selected card; show readable card details on hover and keyboard focus. In-match chat is removed; do not add a chat panel or toggle.

Scale spacing and card size with the window while preserving readable text, all seven row spaces. Use cover scaling without stretching the background. The image demonstrates composition; exact sizes and typography can adapt to the toolkit. Runtime names and effects remain real text, independent of artwork.

`CardTableView` implements this layout for live matches and feeds `ScoreRail` from each recipient-specific view. Selecting a card highlights its legal spaces or targets; hovering one keeps the placement prompt without a play-preview line. An accepted card play or **Skip turn** advances the turn. Skip turn (also S), below Draw, uses one of the ten plays and preserves the hand, rows and draw allowance. Its cost is shown below the button. It is unavailable outside your turn, while reconnecting, and during a Reconnaissance choice.

Match start: the ten-card opening deal follows as soon as the table has appeared; the shared King offer appears after the opening deal animation in rounds 1 and 2. Under each player's own deck a **SWAP n/3** button starts a swap (also clicking the deck, or W/D): hand clicks mark up to the swaps left (coral outline, lifted), **SWAP k** confirms and **CANCEL** (in Skip's place) or Esc backs out. The Cast button for a selected Spell spans the score rail's width just below it. The Menu button (top left) or Esc opens the pause menu (`PauseMenu`): Resume, Settings (game sounds, reduced motion, music), How to play (the quick guide's clips), Surrender and Quit game, each destructive choice confirmed first; it holds new server views and table input until Resume. Reconnaissance shows its cards privately over a dimmed table until one is kept. Cards a Spy revealed appear as small faces marked SPIED beside the opponent's hand count. Clicking a revealed face opens the same large inspector with its points, nation/class or Spell text. Only cards in the recipient's current revealed list are inspectable; an open Spy inspector closes and clears its private description when that card leaves the enemy hand or the round ends. Card faces add only what the art lacks: the selection outline and disable markers (the art prints points, nation, class and Spell text).

Hand cards use separate dimensions up to 1.5 times the row cards, limited by available vertical room. They sit higher with bottom clearance for the fan and shadows; overlapping spacing accommodates larger hands. Drawing, hit testing, dragging and deal animations share these hand dimensions.

Players can arrange their own hand and row for easier reading: drag a hand card sideways within the hand (the others make room) or drag a card on your row to another space (swapping with any card there). This is display only. The server's rows and order are unchanged, nothing is sent, the opponent's view is unaffected, and scoring does not depend on position. Number keys count row spaces as the player sees them. A drag still plays the card when it is dropped on a legal destination. The arrangement lasts for the game and resets with a new one.

Row rearrangement uses the hand's time-based easing: hovering a destination slides its occupant toward the dragged card's original space, and releasing settles the dragged card from the pointer into its destination. Leaving the row restores the preview; an invalid drop glides back to the original space. Drop targets stay fixed while cards slide, and card hit testing and combo links follow the visible positions. Reduced motion shows the arrangement immediately. Card identities keep replacement cards from inheriting old motion, and a new round clears pending row drags and poses.

### Scoring should feel rewarding

The score is the main visual reward, inspired by Balatro. Make the cause of each change visible: highlight the contributing cards, trace matching pairs, show their bonuses, then count the resulting change into the large score. Keep scoring feedback beside the cards that caused it: a short ivory gain, a small gold breakdown, and thin warm-gold links between matching cards. Keep the center clear for turn status rather than adding a dashboard banner. Card details separate printed points, combo bonuses and the formation; only the formation's multiplier scales a row, and the seven-nation bonus is never multiplied.

The sample shows three 3-point Units sharing both tags: 9 printed points + 27 pair bonuses = 36. Adding the third Unit changes 15 to 36: +3 printed points and two +9 pairs, for +21. The opponent's sample 18 assumes three 3-point Units sharing only region. (Under the 2026-10-21 formations both samples' three-of-a-nation rows would also earn a Retinue, +10.)

For implementation, use a short count-up, brief score enlargement, a floating signed change, and optional scoring sounds. Larger combos can receive stronger emphasis. Animate only accepted public results; previews must be labeled and use permitted information. Negative changes should be equally readable. Keep the true score available immediately, allow overlapping updates without blocking input, and honor reduced motion and sound settings. These are presentation effects, not new scoring rules.

### Theme revision

Keep the medieval room cohesive: aged brass, ivory lettering, dark card backs, and warm candlelight highlights. The 35 Units and the shared back are paintings: one figure per class, recoloured per nation with its cloth, ribbon and crest. The ten neutral Spell cards are paintings in the same frame, with a dark SPELL banner, a crest on an iron ribbon and their effect written out. The two inset calculation tiles use muted blue and vermilion, framed by brown leather and brass. Avoid neon team score blocks and a floating central dashboard strip. The reward should come from timing and clear cause-and-effect: matching cards catch a brief warm light, pair bonuses appear, then the large score responds. Sound should feel like cards, coins, or a soft bell. Keep persistent decoration quiet so a scoring moment has somewhere to grow.

### Implemented score rail and preview

`src/main/java/deckduel/client/ui/ScoreRail.java` (JavaFX) takes a presentation-only `Display` snapshot on the JavaFX Application Thread. It displays supplied totals without computing game rules. The breakdown lists Units and Combos, then the row's formation with its bonus and multiplier (for example Full Muster +20 ×2), or "No formation". The score is (Units + Combos + formation bonus) × multiplier, plus the unmultiplied seven-nation bonus. The rail contains only scores and counters: opponent score, your live round score, Units + Combos, plays left, round, and match total. Counters sit directly below the breakdown in a shorter frame. Since 2026-10-22 the rail is an iron tally plate lying on the table (`TablePlaque`), matched to the painted dark fantasy card art and chosen to avoid the generic dark-card look: blackened iron with clipped corners, soft painted light, a vignette, worn gold trim and studs; an aged-gold nameplate over your score in ink on a recessed vellum plate; the opponent's score in a sunken slot with a gold/red tug-of-war lead bar; Units and Combos lines with steel-blue and blood-red enamel tokens (each colour has one job); the formation on a blood-red ribbon lettered in gold; then Plays left, Round and Match total. Labels use the plain sans-serif for easy reading. Table notices (round toasts and formation callouts) use the same plate, with the formation value on a ribbon tag. Chips land on the matching row, which lights briefly; the rail no longer jolts or throws rays. Numbered keyboard targeting is preserved. Full score descriptions remain available to assistive technology; hovering the rail no longer opens an oversized tooltip over the scores.

The rail tells each change in steps: every scoring chip that lands from the table flashes its well (Units, Combos or the opponent's score) and moves it toward the accepted value in proportion to the chips landed, your round score climbs with the Units and Combos wells, and then everything settles on the accepted values (whatever the chips added up to) with a springy punch, a glow, rays for a gain, a signed "+N POINTS" pill and a short jolt of the rail for big changes. It keeps immediate authoritative totals for assistive technology, reduced-motion behavior, accessibility descriptions, and feedback cleanup/reset. The table adds thin links under matching Units (hover a Unit to see its pair bonuses). Each accepted change is traced card by card (`ScoringTrace`): changed cards glow, each new pair lights in turn with its bonus and a bell that rises through the combo, a new formation is called out, then the rail counts up with a coin (or a falling tone for a loss) and a fading signed change above the row. Sounds are synthesized, follow Settings → Game sounds, and reduced motion skips straight to the result.

### Play animations

Every accepted play is staged by `PlayAnimator` as a short `Staging` timeline (plain data) over the already-final view, and `TableEffects` performs it as JavaFX nodes, following common "game juice" practice: layer motion, numbers, particles and sound; show one cause at a time; spring overshoot on landings and pops; a brief hit-stop on heavy impacts. To limit eye strain (user request, 2026-10-21) the table never shakes, flashes are soft (at most about a third of full brightness) and card shudders are small. The server has already resolved the play, the public `PlayRecord` in the view says what happened, and a newer update plays alongside any sequence still running (so a quick reply never cuts a card off mid-flight); only a new game or reduced motion clears them.

- **Motion model (`CardMotion`):** cards move like Balatro's: a short pick-up (rise, slight pull-back), then a critically damped spring to the destination (quick to leave, soft to arrive); the card leans into its sideways speed with a slight lag (capped near 12°), rises while carried (a little larger, its shadow drifting away) and is set down. Flips are real turns about the card's vertical axis under a perspective camera; the turning face is lifted and shaded, and the back is shown while it faces away. The spring's small final remainder is spread over the trip, so a card arrives exactly in its space instead of snapping the last few pixels. The hand is fanned along an arc; hover and selection ease in lift and scale with a small rotation wobble, and a dragged card leans with the pointer. All motion is a closed-form function of time, so it is the same at any frame rate, and the images a sequence needs are prepared before its clock starts so the motion never skips ahead.
- **Unit:** carried from the hand to its space (the opponent's card turns over on the way) and set down with a soft scale punch; on contact a soft ring spreads flat across the tabletop and a little dust puffs out from under it; a replaced card is torn in two and leaves the game (paper scraps, a rip sound).
- **Spell:** rises to the table across from its target and charges while the table dims: motes of light in its own colour spiral in toward it and a glow swells behind it (the ring-shaped sigil that once drew itself around it was removed at the user's request on 2026-10-23). Then it plays its own signature, so no two Spells look alike. Since 2026-10-22 the signatures follow common 2D game-VFX practice (`VfxKit`): additive light with a white-hot core and a coloured falloff, hard weapon smears, sparks streaked along their motion, bursts with sharp glints, thin fast shockwaves, a short hit-stop and a table shake for heavy blows; since 2026-10-23 the props are paintings in the cards' style (`VfxSprites`, `src/main/resources/assets/vfx/`: chain links, a war axe, flame tongues, a brass hook, smoke puffs, the Spy's eye, the binding seal and the Exchange vortex). The glowing props (flame, eye, seal, vortex) were painted on black; their brightness was turned into transparency when they were added, because the effects draw on a transparent layer where black would show as black, and the effect tones are kept close to the paintings' colours. The props are decoded once while the table is built (51–57 ms on an Apple M2, JDK 27, three runs). Destroy Unit (crimson): a war axe winds up beside the Unit and chops down behind a hard ember smear, bites and holds under a burst of light, a shockwave and a spray of sparks while the table shakes, and the Unit splits in two along a glowing cut, the halves sliding apart and falling. Disable Points (steel) flings an iron chain that binds the Unit under a dark veil and locks with a flash and sparks. Disable Tags (violet) lowers a painted seal of binding over the Unit that turns, closes in and slams into it with a burst, a shockwave and a small shake, briefly draining its colour. Clear Disable (cream) drops a shaft of warm candlelight with rising gold motes and a light sweep. Random Discard (ember) tears the card from the enemy hand and burns it beside the Spell: a charred edge with flame tongues climbs the card under rising smoke. Random Steal (gold) casts a brass wire with a hook on its end that bites a card in the enemy hand with a glint and pulls taut before the card crosses. Draw Two (green) dives into its caster's deck, which ripples and fountains gold motes. Exchange (blue) opens a vortex: a painted swirl of light turns round a dark well under a faint turning sigil, swallowing the Spell and the card given up and giving the new cards back. Reconnaissance (teal) fans the deck's top cards up face down in candlelight and sinks them back. Spy (teal and gold) opens a painted eye of light beside the spied hand as if a lid were lifting; it breathes while a soft beam of sight sweeps back and forth across the cards, lighting where it lands, then it closes. A Spell without a legal target chars and dissolves into embers. Other drawn cards glide in from the deck, a stolen card crosses between hands, and the opponent's draws arrive face down, one per card drawn.
- **Combos (reworked 2026-10-23 to feel more rewarding):** after the landing, the row's change in Unit points pops as a coin with a steel-blue enamel face (iron for a loss) over the changed card. Each new pair link leaps from one card to the other as a crackling filament of ember light arcing over the row (higher for cards further apart, kept within the gap below the opponent's cards), gold, thicker and brighter for a pair sharing both tags. As it lands, both cards hop up from their bottom edge with a warm flash, and the pair's bonus pops from the top of the arc as a blood-red coin (gold and larger for a pair sharing both tags); each coin of a chain is a little larger than the last. Coins are struck in the rail's brass with a milled rim; a formation's "+bonus ×mult" rides a clipped-corner brass tag. Each chip hangs for a beat, then is flung in an arc onto its well on the rail (your opponent's chips go to their score) with a coin chink that rises in pitch chip by chip. A row that makes a new formation names it on an iron plate resting on the row's top edge with its "+bonus" on a ribbon tag and a chime; a multiplier above 1 is then stamped beside it: a big gold "×2" drops in large and tilted, lands with a burst of light, a shockwave, sparks, an impact sound and a small table shake (stronger for a larger multiplier). The rail keeps the previous formation on its ribbon until the new formation's chip lands. The score settles once the last chip lands, and the play's total pops over the row in large type (gold for a gain of 40 or more).
- **Draws and rounds:** drawn and dealt cards glide from the deck (about 1.3 s: eased in and out along a shallow arc, lifted mid-flight, turning over across the middle of the trip) and settle exactly at their fanned place and tilt in the hand, so nothing jumps when the real card appears; a round-ending play still lands, then the rows sweep away, a round toast in the gap between the rows (the status line waits while it shows) shows only the new round heading, without a subtitle, and the new hand is dealt; a victory brings falling gold leaf and scraps of banner red.
- Sounds (Settings → Game sounds): whoosh, spell shimmer, shatter, chain clink, deal tick and round bell, plus a seal thud, chime, flame crackle, vortex warble and glint for the Spells, and a paper rip for a replaced Unit, all synthesized. Reduced motion skips the staging and plays only the result sound.
- Pacing and smoothness: plans are written at base speed and `Staging.TEMPO` (1.8) slows every sequence evenly. Frames follow the JavaFX pulse, synchronised with the display. Card faces are the shared `CardFace` sprites converted once to JavaFX images (`FxKit`).
- Calm board: row cards carry a light dark veil unless pointed at or offered as a target, and Unit cards do not open enlarged hover details. Hand Units keep their pickup/selection lift without an extra detail panel. Resting on a Spell in the hand, Reconnaissance choices or Spy-revealed cards for 350 ms shows its catalog name and rules text in a dark, bordered panel with wrapped 17 px rules text and a 20 px title. The panel stays within the window, accepts no pointer input, and disappears when the pointer leaves, a card is picked up, the visible view changes or the table is hidden.

### JavaFX screens

The match table (`CardTableView`, `ScoreRail`, `TableEffects`) is JavaFX; the menu and history stay Swing. `FxScreens` shows a JavaFX window at the Swing frame's place and size while the table is open, and brings the frame back afterwards. `ClientModel` stays on the EDT: its events reach the JavaFX views as immutable snapshots via `Platform.runLater`, and plays, rematch and leave go back through `SwingUtilities.invokeLater`. Effects with depth (card turns) live in a `SubScene` with a `PerspectiveCamera`; glows, shadows and blurs are JavaFX effects, sparks and flashes use additive blending.

The menu's account name uses Cinzel lettering in brass on a compact iron plate. The adjacent music toggle uses matching trim and brass musical notes, crossed out when muted; the JavaFX table carries the same music-control styling.

**Loading screen (2026-10-23):** entering a match from the menus, `LoadingScreen` covers the table for 1.6 s while it gets ready: the castle hall picture (`assets/loading-castle-hall-animation-ready-v2.png`), "Loading" and a bar that fills; then it closes and the opening deal begins. The picture loads in the background when the table is built. The other `loading-*.png` pictures are not used yet.

Why a separate window rather than a `JFXPanel` inside Swing: measured on the same machine and workload (below), a full-size transparent `JFXPanel` overlay ran at 48 frames per second (36 at 900 px wide); a native JavaFX window holds the display's 60.

#### Measured frame pacing (2026-10-19)

- Environment: Apple M2, macOS, built-in 2560×1664 Retina display at 60 Hz, JDK 26 running the Java 17 build, OpenJFX 21.0.5.
- Workload: the real `CardTableView` in a shown window, playing the staged sequence of a real seeded play from the proposal catalog; card art preloaded.
- Method: an `AnimationTimer` records every pulse from the moment the update is shown until staging ends; frames per second = (pulses − 1) / elapsed; a gap over 25 ms counts as a late frame. Harness: a scratch `MeasureFxTable` class, not shipped.

| Sequence | Window | Frames per second | Longest gap | Gaps over 25 ms |
|---|---|---|---|---|
| Opening deal (cold) | 1280×760 | 54.5 | 197.7 ms | 3 |
| Placement with two new pairs | 1280×760 | 59.5 | 50.5 ms | 1 |
| Removal Spell | 1280×760 | 60.1 | 25.2 ms | 1 |
| Opening deal (cold), run 1 | 1920×1080 | 51.9 | 203.5 ms | 5 |
| Opening deal (cold), run 2 | 1920×1080 | 59.1 | 49.9 ms | 1 |
| Placement with two new pairs | 1920×1080 | 60.1 | 17.6 ms | 0 |
| Removal Spell | 1920×1080 | 60.1 | 26.7 / 17.7 ms | 1 / 0 |

Placements and Spells hold the display rate. The opponent's moves wait their turn on screen: an update for the opponent's move that arrives while a play is still animating is held, oldest first, until that animation (including its scoring) has finished, so the practice bot's quick reply never plays over a Spell's effects. Your own moves, a new game and reduced motion show at once, and a held move is shown at once if the table leaves the screen. The first opening deal of a session pauses while the opening hand's card faces are rendered for the first time; the animation clock starts after that work, so the motion itself does not skip. Decoding a painting takes about 28 ms, so the client decodes all 36 paintings on a background thread at start-up (about 1.4 s). Measured 2026-10-22 on an Apple M2 with JDK 27, rendering ten painted hand faces at 140×210 and scale 2 after one warm-up card, three runs each: 70, 91 and 93 ms after the background decode, against 386, 442 and 594 ms without it. Rendering faces themselves in the background is not done.

---

<a id="testing"></a>

## Verification

Run from the repository root with Java 17 or newer and Maven. Desktop checks require a graphical
session. Tests use temporary databases and ephemeral loopback ports, not the live account database.

```sh
mvn test
mvn clean verify
```

On Windows, close processes using the packaged JARs before `clean verify`. If the server must stay
running, build a fresh copy of the current sources in a separate directory instead. Do not copy
personal data, logs, old build output or deleted files into that directory.

### Regression coverage

| Input / scenario | Expected result | Test classes |
| --- | --- | --- |
| Slow drag, held press, invalid drop, Escape, hide and new match | Visible lift/return; no ghost card or unintended move | `CardTableViewTest` |
| Unit hover / Spell hover beside deployed Kings | No Unit popup; readable Spell rules above King artwork | `CardTableViewTest` |
| King offer, hidden choice and deadline | Private simultaneous confirmations; first offered King after 30 seconds | `KingChoiceDeadlineTest`, `CardMatchIntegrationTest` |
| King details, icons, points and aura | Compact original artwork; correct King colors and labels | `KingPresentationTest`, `KingVisualsTest` |
| Newly public King selection | Painting and aura fly into the proper round-one/round-two slot; cancellation restores slots | `KingDeploymentTest` |
| All seven supplied King images | Original SHA-256 and aspect ratio preserved | `KingArtTest` |
| Last play of a round or match | Complete effects and scoring before the conclusion; authoritative banked totals | `RoundScoringSequenceTest`, `RoundClosingViewTest` |
| Quick guide | Every step shows its tip and bundled clip; Back leaves without sending anything | `HowToPlayPanelTest` |
| Replay seek, round jumps and filtered action journal | Seek pauses, original frame indices stay correct, missing frames are explicit, hidden pages stop timers | `ReplayNavigationTest`, `ReplayReviewTest` |
| Replay score comparison | Raw components and multiplier effect reconcile exactly; banked totals and per-King changes remain separate | `ReplayScoreChangeTest` |
| History filters and refresh | Search/result/mode combine locally, practice stays outside W/L, replay selection follows its id | `HistoryFilterTest`, `HistoryFilteringTest` |
| Recorded practice through real server and client | Review, seek, journal, Back and filter/clear succeed without another match | `ReplayJourneySystemTest` |
| Audio mute/resume and recorded cues | Devices close on mute; clips run at a positive rate and complete | `MenuMusicTest`, `ScoringSoundsTest`, `ScoringSoundsMediaTest` |
| Accounts, rooms, reconnect, history and replay access | Authentication, privacy, transactions and protocol filters remain enforced | Full suite |

### Latest run

On 2026-10-28 the current checkout passed `mvn clean verify`: **404 tests,
zero failures, errors or skips**, BUILD SUCCESS in 3m42s (Windows, Temurin 21.0.10,
Maven 3.10.0, Java 17 target, OpenJFX 21.0.5). This includes 34 new replay/history tests,
the existing real-server journey extended to review/filter controls, and the two King deployment
tests from the preceding change. All three packaged JARs were rebuilt. The local server/client
were stopped for the clean build; personal account/history data was retained.

The complete run is retained locally in
`data/guide-simple-build.log` and machine reports in `target/surefire-reports/`.

Keep the latest machine reports in ignored `target/surefire-reports/` and build logs in
`target/verification/`. Superseded screenshots, diagnostic recordings, duplicate reports and
historical build trees are disposable. Guide clips and runtime artwork/audio
under `src/main/resources/` are application assets and must be retained.
