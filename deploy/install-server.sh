#!/usr/bin/env bash
# Run inside EC2 (Ubuntu) from a clone of this repository:
#   sudo deploy/install-server.sh <game-host> <email> [port]
# One command from a fresh instance to a running server: installs Java 17, Maven and certbot,
# builds the headless server from this checkout, sets up the service (deploy/setup-ec2.sh),
# gets a Let's Encrypt certificate for <game-host> (renewed automatically), writes
# /etc/deckduel/server.properties, makes the backup key, starts the server and probes it.
# Safe to run again after `git pull`: it installs the new build and restarts only once no match is
# in progress. Before running: point <game-host>'s DNS at this instance and open TCP 80 (certbot)
# and the game port in the security group (docs/README.md#deployment).
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "Run with sudo." >&2; exit 1; }
HOST=${1:?usage: sudo deploy/install-server.sh <game-host> <email> [port]}
EMAIL=${2:?usage: sudo deploy/install-server.sh <game-host> <email> [port]}
PORT=${3:-5555}
[[ "$HOST" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "Give the DNS name players connect to, for example play.example.com" >&2; exit 1; }
REPO=$(cd "$(dirname "$0")/.." && pwd)
BIN=/opt/deckduel/bin

echo "== Packages"
export DEBIAN_FRONTEND=noninteractive
need=()
if ! /usr/bin/java -version 2>&1 | grep -Eq 'version "(1[7-9]|[2-9][0-9])'; then
  # Newer Ubuntu releases no longer ship Java 17; the server runs on any Java 17+.
  apt-get update -q
  if apt-cache show openjdk-21-jdk-headless >/dev/null 2>&1; then need+=(openjdk-21-jdk-headless); else need+=(openjdk-17-jdk-headless); fi
fi
command -v mvn >/dev/null || need+=(maven)
command -v certbot >/dev/null || need+=(certbot)
if [ ${#need[@]} -gt 0 ]; then apt-get update -q && apt-get install -y -q "${need[@]}"; fi

echo "== Build"
# The release folder name: the project version plus the commit, so each pull installs as new.
BASE=$(sed -n 's:.*<version>\([0-9][0-9.]*\)\(-SNAPSHOT\)\{0,1\}</version>.*:\1:p' "$REPO/pom.xml" | head -1)
COMMIT=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M)
VERSION="$BASE-git.$COMMIT"
BUILD=$(mktemp -d /tmp/deck-duel-build.XXXXXX)
trap 'rm -rf "$BUILD"' EXIT
cp -R "$REPO"/. "$BUILD"/
rm -rf "$BUILD/target" "$BUILD/.git"
# Tests run on the development computer: the client tests need a graphical session.
# The server jar contains no JavaFX, but the one module still compiles the client against it, and
# OpenJFX publishes Linux ARM64 jars only from 22 on; the Java 17 class files are unchanged.
OWNER=${SUDO_USER:-root}
chown -R "$OWNER" "$BUILD"
sudo -u "$OWNER" -H bash -c "cd '$BUILD' && mvn -q -DskipTests package -Djavafx.version=22.0.2 -Ddeckduel.version='$VERSION'"
BUNDLE=$BUILD/bundle
mkdir -p "$BUNDLE"
cp "$BUILD/target/deck-duel-server.jar" "$BUNDLE/"
echo "$VERSION" > "$BUNDLE/VERSION"
(cd "$BUNDLE" && sha256sum deck-duel-server.jar > SHA256SUMS)

echo "== Service"
"$REPO/deploy/setup-ec2.sh"
"$BIN/deckduel-install-release" "$BUNDLE"

echo "== Configuration"
if [ ! -f /etc/deckduel/server.properties ]; then
  sed "s/^port=.*/port=$PORT/" "$REPO/deploy/config/server.properties.template" > /etc/deckduel/server.properties
  chown root:deckduel /etc/deckduel/server.properties && chmod 0640 /etc/deckduel/server.properties
fi
if [ ! -f /etc/deckduel/backup.key ]; then
  KEY=$(mktemp /tmp/backup.key.XXXXXX) && rm -f "$KEY"
  sudo -u deckduel /usr/bin/java -jar "/opt/deckduel/releases/$VERSION/deck-duel-server.jar" backup-keygen --key-file "$KEY"
  install -o root -g deckduel -m 0640 "$KEY" /etc/deckduel/backup.key && shred -u "$KEY"
  NEW_BACKUP_KEY=1
fi

echo "== Certificate for $HOST"
if [ ! -f "/etc/letsencrypt/live/$HOST/fullchain.pem" ]; then
  certbot certonly --standalone -d "$HOST" -m "$EMAIL" --agree-tos --no-eff-email -n \
    --deploy-hook "$BIN/deckduel-install-certificate \"\$RENEWED_LINEAGE/fullchain.pem\" \"\$RENEWED_LINEAGE/privkey.pem\""
fi
"$BIN/deckduel-install-certificate" "/etc/letsencrypt/live/$HOST/fullchain.pem" "/etc/letsencrypt/live/$HOST/privkey.pem"

echo "== Start"
"$BIN/deckduel-switch-release" "$VERSION"
systemctl enable --now deckduel-backup.timer >/dev/null
/usr/bin/java -jar "/opt/deckduel/current/deck-duel-server.jar" probe --host "$HOST" --port "$(sed -n 's/^port=//p' /etc/deckduel/server.properties)" --connect-address 127.0.0.1

echo
echo "Deck Duel $VERSION is running for $HOST."
echo "Build the game for players with: tools/launcher/build-mac-app.sh <version> $HOST $PORT  (and launcher\\windows\\build-installer.ps1 on Windows)"
if [ -n "${NEW_BACKUP_KEY:-}" ]; then
  echo "New backup key; store it in your password manager (without it backups cannot be restored):"
  cat /etc/deckduel/backup.key
fi
