#!/usr/bin/env bash
# Run on your computer (once per platform you release for, on that platform):
#   tools/build-client-release.sh <version> <server-host> <server-port> <metadata-url> <public-key-file>
# Builds the client with the production game server and the direct-S3 update channel packaged in,
# then writes dist/client-<version>/deck-duel-<version>-<platform>.zip. Signing and uploading are
# separate manual steps (docs/deployment.md, part D). The private signing key is never read here.
set -euo pipefail
VERSION=${1:?version}; HOST=${2:?server host (the certificate DNS name)}; PORT=${3:?server port}
URL=${4:?https://<release-bucket>.s3.<region>.amazonaws.com/<channel>/latest.properties}; PUBLIC_KEY_FILE=${5:?public key file}
case "$URL" in https://*) ;; *) echo "The update address must use https://" >&2; exit 1;; esac
REPO=$(cd "$(dirname "$0")/.." && pwd)
# Build from a copy outside the repository: iCloud-synced folders gain "name 2.ext" duplicates
# inside target/ even during a clean build, and those must never reach a release.
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/deck-duel-build.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT
rsync -a --delete --exclude target --exclude dist --exclude '.git' --exclude '* [0-9]*' "$REPO"/ "$BUILD"/
mkdir -p "$REPO/dist"
cd "$BUILD"
mvn -q clean verify -Ddeckduel.version="$VERSION" -Ddeckduel.server.host="$HOST" -Ddeckduel.server.port="$PORT" \
    -Ddeckduel.update.url="$URL" -Ddeckduel.update.publicKey="$(tr -d '[:space:]' < "$PUBLIC_KEY_FILE")"
PLATFORM=$(java -cp target/classes deckduel.Main release platform)
OUT=$REPO/dist/client-$VERSION
mkdir -p "$OUT"
java -cp target/classes deckduel.Main release package --version "$VERSION" --platform "$PLATFORM" \
    --app target/deck-duel.jar --launcher target/deck-duel-launcher.jar --out "$OUT"
unzip -p target/deck-duel.jar deckduel/connection.properties deckduel/update.properties | grep -E '^(host|port|metadataUrl)='
echo "Packaged $OUT/deck-duel-$VERSION-$PLATFORM.zip"
