#!/usr/bin/env bash
# Demonstrates a real automatic update on this computer (macOS or Linux), with nothing uploaded:
#   1. creates a throwaway Ed25519 signing key outside the repository,
#   2. builds the game twice (1.0.0 and 1.1.0) with a local release channel packaged in,
#   3. packages both for this platform and signs 1.1.0's metadata,
#   4. serves the metadata and package from 127.0.0.1 (standing in for an S3 bucket),
#   5. installs 1.0.0 into a temporary per-user installation and starts it through the launcher,
#   6. updates it to 1.1.0: you press Update (gui), or tools/DemoUpdate.java does what the button
#      does (headless); the game closes, the helper installs 1.1.0 and the game restarts by itself.
# Usage: tools/update-demo.sh [gui|headless]     WORK=/some/dir PORT=8765 may be set.
# It runs `mvn clean package` twice in this repository, so target/ is rebuilt.
set -euo pipefail
MODE=${1:-gui}
REPO=$(cd "$(dirname "$0")/.." && pwd)
WORK=${WORK:-$(mktemp -d "${TMPDIR:-/tmp}/deck-duel-update-demo.XXXXXX")}
PORT=${PORT:-8765}
URL="http://127.0.0.1:$PORT/latest.properties"
cd "$REPO"
echo "Working in $WORK"

mvn -q compile
java -cp target/classes deckduel.Main release keygen --private-key "$WORK/keys/release.key" --public-key "$WORK/keys/release.pub"
PUBLIC_KEY=$(cat "$WORK/keys/release.pub")
PLATFORM=$(java -cp target/classes deckduel.Main release platform)

for VERSION in 1.0.0 1.1.0; do
  echo "Building $VERSION for $PLATFORM"
  mvn -q clean package -DskipTests -Ddeckduel.version="$VERSION" \
      -Ddeckduel.update.url="$URL" -Ddeckduel.update.publicKey="$PUBLIC_KEY"
  mkdir -p "$WORK/build-$VERSION"
  cp target/deck-duel.jar target/deck-duel-launcher.jar "$WORK/build-$VERSION/"
  java -cp target/classes deckduel.Main release package --version "$VERSION" --platform "$PLATFORM" \
      --app "$WORK/build-$VERSION/deck-duel.jar" --launcher "$WORK/build-$VERSION/deck-duel-launcher.jar" --out "$WORK/packages"
done
java -cp target/classes deckduel.Main release sign --private-key "$WORK/keys/release.key" \
    --minimum-version 1.0.0 --minimum-protocol 16 --out "$WORK/site" "$WORK/packages/deck-duel-1.1.0-$PLATFORM.zip"

if command -v jwebserver >/dev/null 2>&1; then
  jwebserver -b 127.0.0.1 -p "$PORT" -d "$WORK/site" >"$WORK/webserver.log" 2>&1 &
else
  # exec, so the recorded process id is Python's own and the cleanup below stops it.
  (cd "$WORK/site" && exec python3 -m http.server "$PORT" --bind 127.0.0.1 >"$WORK/webserver.log" 2>&1) &
fi
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for _ in $(seq 1 50); do curl -sf "$URL" >/dev/null && break; sleep 0.2; done

INSTALL="$WORK/install"
java -jar "$WORK/build-1.0.0/deck-duel-launcher.jar" install --root "$INSTALL" \
    --package "$WORK/packages/deck-duel-1.0.0-$PLATFORM.zip"

if [ "$MODE" = headless ]; then
  java -cp "$INSTALL/versions/1.0.0/deck-duel.jar" tools/DemoUpdate.java "$INSTALL"
else
  java -jar "$INSTALL/launcher/deck-duel-launcher.jar" launch --root "$INSTALL"
  echo "Deck Duel 1.0.0 is starting. On the startup screen, press UPDATE."
fi

echo "Waiting for 1.1.0 to be active and to confirm its start..."
for _ in $(seq 1 600); do
  if grep -q '^active=1.1.0' "$INSTALL/current.properties" 2>/dev/null && [ -f "$INSTALL/versions/1.1.0/.started-ok" ]; then
    echo "Updated: $(grep -E '^(active|previous)=' "$INSTALL/current.properties" | tr '\n' ' ')"
    echo "Logs: $INSTALL/logs (launcher.log, updater.log, client.log). The restarted 1.1.0 window can be closed."
    exit 0
  fi
  sleep 1
done
echo "The update did not complete; see $INSTALL/logs" >&2
exit 1
