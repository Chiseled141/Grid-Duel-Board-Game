#!/usr/bin/env bash
# Run inside EC2 with sudo, from an unpacked server bundle: sudo ./deploy/setup-ec2.sh
# Creates the service account and folders, installs the scripts and systemd units, and enables
# the service at boot. Safe to run again (it refreshes scripts and units, never data or config).
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "Run with sudo." >&2; exit 1; }
HERE=$(cd "$(dirname "$0")" && pwd)
JAVA=/usr/bin/java

"$JAVA" -version 2>&1 | head -1 | grep -Eq 'version "(1[7-9]|[2-9][0-9])' \
  || { echo "Java 17 or newer is required at $JAVA" >&2; exit 1; }

id deckduel >/dev/null 2>&1 || useradd --system --no-create-home --home-dir /var/lib/deckduel \
  --shell /usr/sbin/nologin --user-group deckduel

install -d -o root -g root -m 0755 /opt/deckduel /opt/deckduel/releases /opt/deckduel/bin /opt/deckduel/docs
install -d -o root -g deckduel -m 0750 /etc/deckduel /etc/deckduel/tls
install -d -o deckduel -g deckduel -m 0700 /var/lib/deckduel /var/backups/deckduel

for script in "$HERE"/bin/*; do install -o root -g root -m 0755 "$script" /opt/deckduel/bin/; done
for unit in "$HERE"/systemd/*; do install -o root -g root -m 0644 "$unit" /etc/systemd/system/; done
install -o root -g deckduel -m 0640 "$HERE/config/server.properties.template" /etc/deckduel/server.properties.example
[ -f "$HERE/../docs/deployment.md" ] && install -o root -g root -m 0644 "$HERE/../docs/deployment.md" /opt/deckduel/docs/

systemctl daemon-reload
systemctl enable deckduel.service >/dev/null
echo "Set up. Next: install a release (deckduel-install-release), write /etc/deckduel/server.properties,"
echo "install the TLS files (deckduel-install-certificate) and the backup key, then deckduel-switch-release <version>."
