# AWS Deployment Runbook (manual — the team executes this)

**Constraints — read first.**

* This is a low-traffic university project. Keep AWS spend minimal: a single
  small EC2 instance is enough — `t4g.small` is sufficient; **nothing larger**.
* EC2 only, **no additional AWS managed services**: SQLite (a plain file on
  the instance) and systemd already cover persistence and process
  supervision. No RDS, no load balancer, no containers.
* The team's AWS credits are budgeted for their separate CV project —
  **stop the instance whenever it is not in use** (see step 9).

All commands are run by a human; nothing in this repository deploys anything
automatically.

## 1. Launch the EC2 instance

1. EC2 → Launch instance.
2. AMI: **Ubuntu 22.04 or 24.04 LTS**.
3. Instance type: **t4g.small** (ARM/Graviton — the pure-Java jar runs
   unchanged; pin a recent `sqlite-jdbc` version so the bundled
   Linux/ARM64 native library is included — the project pins 3.46.1.3).
4. Key pair: create/select one (you need the `.pem` for SSH).
5. Storage: 8 GB gp3 is plenty.

## 2. Security group

| Type | Port | Source | Purpose |
| ---- | ---- | ------ | ------- |
| Custom TCP | **5555** | 0.0.0.0/0 | game clients |
| SSH | 22 | team IPs only | administration |

## 3. Install the JDK

```bash
ssh -i <key.pem> ubuntu@<EC2_PUBLIC_IP>
sudo apt update && sudo apt install -y openjdk-17-jre-headless
java -version    # must print 17.x or newer
```

## 4. Copy the built jar

Built locally with `mvn package` (or `mvn package -DskipTests` for a deploy
build; the test suite runs elsewhere):

```bash
scp -i <key.pem> target/onitama.jar ubuntu@<EC2_IP>:~/
```

## 5. Run it under systemd

Create `/etc/systemd/system/onitama.service`:

```ini
[Unit]
Description=Onitama Online game server
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/onitama.jar server --port 5555 --db /home/ubuntu/data/onitama.db
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

## 6. Start and verify

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now onitama
systemctl status onitama                 # active (running)
ss -tlnp | grep 5555                     # server must be listening
```

## 7. Connect with clients

```bash
java -jar onitama.jar client --host <EC2_PUBLIC_IP> --port 5555
```

Client settings are remembered in `~/.onitama/client.properties`, so the
host only has to be typed once per machine.

## 8. Backup

The database and all replays are plain files: periodically `tar` them.

```bash
tar -czf onitama-backup-$(date +%F).tar.gz data/ replays/ logs/
# copy off the instance (scp to your machine) — do not keep backups only on EC2
```

## 9. Stop when not in use

```bash
sudo systemctl stop onitama        # soft stop (clean shutdown, no forfeits pending)
```

EC2 console → Instance state → **Stop instance** when nobody is playing
(stopped instances cost nothing but storage). Start it again before a play
session; the public IP may change after stop/start unless you allocate an
Elastic IP.

## Troubleshooting

| Symptom | Likely cause | Fix |
| ------- | ------------ | --- |
| Client cannot connect; `ss` shows nothing | server not running | `sudo systemctl status onitama`, check `journalctl -u onitama` |
| Client cannot connect; server listening | security group | inbound TCP 5555 must allow 0.0.0.0/0 |
| `UnsupportedClassVersionError` | old JDK | install `openjdk-17-jre-headless`, re-check `java -version` |
| `SQLITE_BUSY` / database locked in the log | two server processes on the same db file | `pgrep -af onitama`, kill the extra process |
| Server restarts in a loop | port already bound | check `ss -tlnp | grep 5555` before starting systemd |
