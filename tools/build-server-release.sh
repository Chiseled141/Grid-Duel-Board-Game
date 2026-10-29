#!/usr/bin/env bash
# Run on your computer: tools/build-server-release.sh <version>
# Builds and tests the headless server, checks it for Linux ARM64 + Java 17, and writes
#   dist/deck-duel-server-<version>.tar.gz          (jar, SHA256SUMS, deploy scripts/units, docs)
#   dist/deck-duel-server-<version>.tar.gz.sha256   (compare this inside EC2 after the transfer)
# Nothing is uploaded. Build with a clean tree: the repository lives in iCloud-synced Documents.
set -euo pipefail
VERSION=${1:?usage: tools/build-server-release.sh <version>}
REPO=$(cd "$(dirname "$0")/.." && pwd)
# Build from a copy outside the repository: iCloud-synced folders gain "name 2.ext" duplicates
# inside target/ even during a clean build, and those must never reach a release.
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/deck-duel-build.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT
rsync -a --delete --exclude target --exclude dist --exclude '.git' --exclude '* [0-9]*' "$REPO"/ "$BUILD"/
mkdir -p "$REPO/dist"
cd "$BUILD"
mvn -q clean verify -Ddeckduel.version="$VERSION"
JAR=target/deck-duel-server.jar
# List once: piping unzip into grep -q trips pipefail when grep stops reading early.
LISTING=$(unzip -l "$JAR")
grep -q 'org/sqlite/native/Linux/aarch64/libsqlitejdbc.so' <<<"$LISTING" || { echo "No Linux aarch64 SQLite library in $JAR" >&2; exit 1; }
if grep -Eq 'javafx|deckduel/client/' <<<"$LISTING"; then echo "$JAR contains client or JavaFX classes" >&2; exit 1; fi
if grep -Eq ' [0-9]+(\.[A-Za-z0-9]+)?$' <<<"$LISTING"; then echo "$JAR contains stray duplicate files" >&2; exit 1; fi
MAJOR=$(unzip -p "$JAR" deckduel/server/ServerMain.class | od -An -tu1 -j6 -N2 | awk 'NF {print $1*256+$2; exit}')
[ "$MAJOR" = 61 ] || { echo "ServerMain.class is class file version $MAJOR, not 61 (Java 17)" >&2; exit 1; }
[ "$(unzip -p "$JAR" deckduel/version.properties | grep '^version=' | cut -d= -f2)" = "$VERSION" ] || { echo "Version stamp mismatch" >&2; exit 1; }

OUT=$REPO/dist/server-$VERSION
rm -rf "$OUT"
mkdir -p "$OUT/docs"
cp "$JAR" "$OUT/deck-duel-server.jar"
echo "$VERSION" > "$OUT/VERSION"
cp -R deploy "$OUT/deploy"
cp docs/README.md "$OUT/docs/"
(cd "$OUT" && shasum -a 256 deck-duel-server.jar > SHA256SUMS)
# macOS tar would embed extended attributes that GNU tar on Ubuntu warns about.
TAR_OPTS=()
if tar --version 2>/dev/null | grep -q bsdtar; then TAR_OPTS=(--no-xattrs --no-mac-metadata); fi
COPYFILE_DISABLE=1 tar "${TAR_OPTS[@]}" -C "$REPO/dist" -czf "$REPO/dist/deck-duel-server-$VERSION.tar.gz" "server-$VERSION"
(cd "$REPO/dist" && shasum -a 256 "deck-duel-server-$VERSION.tar.gz" > "deck-duel-server-$VERSION.tar.gz.sha256")
echo "Built $REPO/dist/deck-duel-server-$VERSION.tar.gz"
cat "$REPO/dist/deck-duel-server-$VERSION.tar.gz.sha256"
