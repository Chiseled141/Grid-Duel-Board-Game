#!/usr/bin/env bash
# Run on a Mac (the app is built for that Mac's processor, Apple silicon or Intel):
#   tools/launcher/build-mac-app.sh <version> <server-host> <server-port> [<metadata-url> <public-key-file>]
# Builds the client with the production game server (and, optionally, the update channel)
# packaged in, then writes dist/client-<version>/:
#   deck-duel-<version>-<platform>.zip   the update package (upload it to the release channel)
#   Deck Duel.app and DeckDuel-<version>-<platform>.dmg   the app players download once
# The app bundles a Java runtime linked from this JDK (tools/launcher/runtime-modules.txt). On every
# start it sets up its bundled package in ~/Library/Application Support/DeckDuel, keeping a newer
# version that an automatic update installed, and starts the game. Nothing is signed or uploaded
# here (docs/README.md#windows-installer describes the same flow for Windows).
set -euo pipefail
VERSION=${1:?version}; HOST=${2:?server host (the certificate DNS name)}; PORT=${3:?server port}
URL=${4:-}; PUBLIC_KEY_FILE=${5:-}
[ "$(uname -s)" = Darwin ] || { echo "Run this on macOS." >&2; exit 1; }
[[ "$VERSION" =~ ^[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}$ ]] || { echo "Not a release version: $VERSION" >&2; exit 1; }
if [ -n "$URL" ] || [ -n "$PUBLIC_KEY_FILE" ]; then
    [ -n "$URL" ] && [ -n "$PUBLIC_KEY_FILE" ] || { echo "Pass both the update address and the public key file, or neither." >&2; exit 1; }
    case "$URL" in https://*) ;; *) echo "The update address must use https://" >&2; exit 1;; esac
fi
for tool in mvn jlink jpackage; do command -v "$tool" >/dev/null || { echo "$tool is required (a JDK 17+ and Maven)." >&2; exit 1; }; done
REPO=$(cd "$(dirname "$0")/../.." && pwd)
PUBLIC_KEY=$([ -n "$PUBLIC_KEY_FILE" ] && tr -d '[:space:]' < "$PUBLIC_KEY_FILE" || true)
# Build from a copy outside the repository: iCloud-synced folders gain "name 2.ext" duplicates
# inside target/ even during a clean build, and those must never reach a release.
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/deck-duel-build.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT
rsync -a --delete --exclude target --exclude dist --exclude .git --exclude data --exclude logs --exclude '* [0-9]*' "$REPO"/ "$BUILD"/
cd "$BUILD"
# SKIP_TESTS=1 is for CI runners without audio or a full desktop; run the tests on your computer first.
mvn -q clean verify -DskipTests="${SKIP_TESTS:+true}" -Ddeckduel.version="$VERSION" -Ddeckduel.server.host="$HOST" -Ddeckduel.server.port="$PORT" \
    -Ddeckduel.update.url="$URL" -Ddeckduel.update.publicKey="$PUBLIC_KEY"
PLATFORM=$(java -cp target/classes deckduel.Main release platform | tail -1)
OUT=$REPO/dist/client-$VERSION
mkdir -p "$OUT"
java -cp target/classes deckduel.Main release package --version "$VERSION" --platform "$PLATFORM" \
    --app target/deck-duel.jar --launcher target/deck-duel-launcher.jar --out "$OUT"
PACKAGE=deck-duel-$VERSION-$PLATFORM.zip
unzip -p target/deck-duel.jar deckduel/connection.properties deckduel/update.properties | grep -E '^(host|port|metadataUrl)='

# The runtime: the listed modules that this JDK has (jdk.crypto.ec is gone from newer JDKs).
AVAILABLE=$(java --list-modules | cut -d@ -f1)
MODULES=$(grep -v '^#' "$REPO/tools/launcher/runtime-modules.txt" | grep -Fxf <(echo "$AVAILABLE") | paste -sd, -)
jlink --add-modules "$MODULES" --strip-debug --no-man-pages --no-header-files --output runtime
mkdir input
cp target/deck-duel-launcher.jar "$OUT/$PACKAGE" input/

# The icon: the Windows icon's largest image, converted to an icns set.
mkdir -p icon.iconset
sips -s format png "$REPO/tools/launcher/deck-duel.ico" --out icon.png >/dev/null
for size in 16 32 128 256 512; do
    sips -z $size $size icon.png --out "icon.iconset/icon_${size}x${size}.png" >/dev/null
    sips -z $((size * 2)) $((size * 2)) icon.png --out "icon.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns icon.iconset -o deck-duel.icns

# The bundled runtime must set up the package and load the game's classes.
runtime/bin/java -jar input/deck-duel-launcher.jar setup --root install-check --package "input/$PACKAGE"
runtime/bin/java -cp "install-check/versions/$VERSION/deck-duel.jar" deckduel.Main release platform

APP_OPTIONS=(--name "Deck Duel" --app-version "$VERSION" --vendor "Deck Duel" --icon deck-duel.icns
    --input input --main-jar deck-duel-launcher.jar --main-class deckduel.launcher.LauncherMain
    --arguments start --runtime-image runtime --mac-package-identifier io.github.deckduel.client)
jpackage --type app-image --dest app "${APP_OPTIONS[@]}"
rm -rf "$OUT/Deck Duel.app"
cp -R "app/Deck Duel.app" "$OUT/"
jpackage --type dmg --dest dmg --app-image "app/Deck Duel.app" --name "Deck Duel" --app-version "$VERSION"
mv dmg/*.dmg "$OUT/DeckDuel-$VERSION-$PLATFORM.dmg"
shasum -a 256 "$OUT/$PACKAGE" "$OUT/DeckDuel-$VERSION-$PLATFORM.dmg"
# launcher/ keeps only the downloads players need (ignored by Git).
mkdir -p "$REPO/launcher" && cp "$OUT/DeckDuel-$VERSION-$PLATFORM.dmg" "$REPO/launcher/"
echo "Built $REPO/launcher/DeckDuel-$VERSION-$PLATFORM.dmg"
