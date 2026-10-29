# Manual deployment: Deck Duel server on EC2, client releases on S3

You run every step here yourself. The repository contains code, scripts and templates; it never
connects to AWS, uploads anything or changes security groups. Each command block says where it runs:

- **Run on my computer:** your workstation with this repository, Java 17+, Maven, and (for S3
  steps) the AWS CLI signed in as you.
- **Run inside EC2:** a Session Manager shell on the instance (`sudo` available).
- **Change in AWS Console:** a click-path in the AWS web console.

Technical background: `docs/connections.md` (TLS, version policy, drain), `docs/updates.md`
(updater and release format), `docs/windows-installer.md`, `docs/replays.md`. The order of work for a
first small release is summarised in `docs/online-release.md`.

## 0. Inputs you must supply

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

## A. Verify identifiers (do this first)

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

## B. Build the server release

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

## C. Transfer the release to the instance

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

## D. Prepare the instance (once; re-run after each release to refresh scripts)

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

## E. TLS certificate for `<GAME_HOST>`

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

## F. Configure and start

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

## G. Open the game port (only after F's probe succeeds)

**Change in AWS Console:** EC2 → Security Groups → the instance's group → Inbound rules → add
**Custom TCP `<GAME_PORT>`** from `0.0.0.0/0` (and `::/0` if you use IPv6), description "Deck Duel
game (TLS)". Open no other port. Port 80 is needed only for HTTP-01 certificates. Session Manager
needs no inbound port.

**Run on my computer** (from your own network, not the instance)
```sh
java -jar target/deck-duel-server.jar probe --host <GAME_HOST> --port <GAME_PORT>
```

## H. Production client release (direct S3, no CloudFront)

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
   For Windows, build on a Windows x64 computer with `tools\build-windows-installer.ps1` instead
   (`docs/windows-installer.md`). It writes the Windows package to the same folder, together with
   `DeckDuel-Setup-<VERSION>.exe`. Copy every platform's zip into `dist/client-<VERSION>/` on the
   computer that holds the signing key before step 5.
6. **First installation for players.** Windows players run `DeckDuel-Setup-<VERSION>.exe` once
   (bundled Java, no administrator rights). Upload it next to the packages, for example:
   `aws s3 cp dist/client-<VERSION>/DeckDuel-Setup-<VERSION>.exe s3://<RELEASE_BUCKET>/<CHANNEL>/ --content-type application/vnd.microsoft.portable-executable`,
   and share its URL, or host it anywhere else. Players on other platforms need Java 17+, the
   launcher and a first package (`deck-duel-launcher.jar install --root <per-user folder> --package <zip>`;
   see `docs/updates.md`).

Players receive only the packages, which hold the client jars, the public key and public
addresses: never AWS credentials, the signing key, the server certificate key or a database.

## I. Two real clients from different networks

1. Give the client package to two testers on different networks (for example home broadband and a
   phone hotspot).
2. Each launches the game. It checks for updates, connects securely to `<GAME_HOST>` with no host
   entry needed, then registers an account.
3. One creates a match; the other joins by code. Play a full match, then check Play → Match
   history and Watch replay on both.
4. **Run inside EC2** during the match: `sudo /opt/deckduel/bin/deckduel-ctl status`
   (`activeMatches=1`) and `journalctl -u deckduel -f`.

## J. Backups

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

## K. Deploying a later server release

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

## L. Rollback and database compatibility

`sudo /opt/deckduel/bin/deckduel-rollback` switches to `/opt/deckduel/previous` with the same
draining. Schema changes are additive and idempotent: new tables and nullable columns, with
earlier columns kept. So an older release keeps working on a database a newer one has opened.
Every release so far follows this rule. If a future release breaks it, its notes must say so;
then roll back by restoring the backup taken in K.2 together with the previous release. Client
rollbacks are separate: republish older metadata only if the server still accepts that client.

## JVM memory on the 2 GiB instance

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

## What was verified, and what was not

Verified locally on macOS arm64 only ("Verification evidence" in `docs/connections.md`): TLS,
version policy, drain, backups and the headless server jar, on Java 17.0.21 and Java 27.
`tools/build-server-release.sh` builds from a copy outside the repository, because the
iCloud-synced folder gains duplicate files in `target/`, and it refuses jars that contain any.

Not verified: Ubuntu 26.04, Linux ARM64, systemd units, AWS, certbot, real DNS, the security
group, and connections across the internet. You verify those by following this guide.
