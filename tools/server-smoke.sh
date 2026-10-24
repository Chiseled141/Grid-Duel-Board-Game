#!/usr/bin/env bash
# Run on your computer after `mvn clean package`: tools/server-smoke.sh
# A local, headless smoke test of target/deck-duel-server.jar with TLS: throwaway OpenSSL test
# certificates (never production), the real server on 127.0.0.1, a certificate check with
# openssl s_client, two bots playing a full match over TLS, drain/wait-safe over the control socket,
# an encrypted backup restored into a temporary database, and a graceful stop.
# JAVA=/path/to/java selects the runtime (for example a Java 17 JDK). Prints evidence lines.
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
JAVA=${JAVA:-java}
PORT=${PORT:-15555}
WORK=$(mktemp -d /tmp/dd-smoke.XXXXXX)
SERVER_JAR=${SERVER_JAR:-"$REPO/target/deck-duel-server.jar"}
CLIENT_JAR="$REPO/target/deck-duel.jar"
SERVER_PID=
cleanup() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true; rm -rf "$WORK"; }
trap cleanup EXIT
echo "== runtime: $("$JAVA" -version 2>&1 | head -1); os: $(uname -sm)"

echo "== server jar contents"
LISTING=$(unzip -l "$SERVER_JAR")
echo "javafx entries: $(grep -c 'javafx' <<<"$LISTING" || true)"
grep -q 'org/sqlite/native/Linux/aarch64/libsqlitejdbc.so' <<<"$LISTING" && echo "Linux aarch64 SQLite native library: present"

echo "== throwaway test certificates (OpenSSL)"
cd "$WORK"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ca.key 2>/dev/null
openssl req -x509 -new -key ca.key -subj "/CN=Deck Duel smoke-test CA" -days 2 -out ca.pem 2>/dev/null
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out privkey.pem 2>/dev/null
openssl req -new -key privkey.pem -subj "/CN=localhost" -out server.csr 2>/dev/null
printf "subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n" > ext.cnf
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 2 -extfile ext.cnf -out server.pem 2>/dev/null
cat server.pem ca.pem > fullchain.pem
head -1 privkey.pem

cat > server.properties <<CONF
bind=127.0.0.1
port=$PORT
database=$WORK/data/deck-duel.db
tls.certificateChain=$WORK/fullchain.pem
tls.privateKey=$WORK/privkey.pem
minimumClientVersion=1.0.0
control.socket=$WORK/control.sock
handshakeTimeoutSeconds=5
CONF

echo "== start (headless)"
"$JAVA" -Djava.awt.headless=true -Xmx256m -jar "$SERVER_JAR" server --config server.properties > server.log 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 60); do grep -q "Deck Duel server ready" server.log && break; sleep 0.25; done
grep "Deck Duel server ready" server.log
grep -E "listening on" server.log

echo "== certificate check"
openssl s_client -connect 127.0.0.1:$PORT -servername localhost -verify_return_error -CAfile ca.pem </dev/null 2>/dev/null \
  | grep -E "Verify return code|Protocol *:|^ *Protocol"
echo "== protocol readiness probe (no login)"
"$JAVA" -jar "$SERVER_JAR" probe --host localhost --port $PORT --trust-cert ca.pem --connect-address 127.0.0.1
if "$JAVA" -jar "$SERVER_JAR" probe --host localhost --port $PORT >/dev/null 2>&1; then echo "default trust accepted a test certificate" >&2; exit 1; fi
echo "default Java trust refuses the test certificate: ok"
ctl() { "$JAVA" -Xmx64m -jar "$SERVER_JAR" ctl "$@" --socket "$WORK/control.sock"; }
ctl status

echo "== two bots play a full match over TLS"
if ! "$JAVA" -Xmx512m -jar "$CLIENT_JAR" bots --host localhost --port $PORT --tls true --trust-cert ca.pem --count 2 --games 1 > bots.log 2>&1; then
  echo "bots failed:" >&2; grep -vE '^WARNING|^$' bots.log | tail -30 >&2; tail -30 server.log >&2; exit 1
fi
grep -E "won|lost|draw" bots.log | sed 's/^.*INFO: //'

echo "== drain and wait until safe"
ctl drain
ctl wait-safe --timeout 30

echo "== encrypted backup and restore into a temporary database"
"$JAVA" -jar "$SERVER_JAR" backup-keygen --key-file "$WORK/backup.key" | head -1
BACKUP=$("$JAVA" -jar "$SERVER_JAR" backup --database "$WORK/data/deck-duel.db" --out-dir "$WORK/backups" --key-file "$WORK/backup.key" --keep 3 | tail -1)
ls -l "$BACKUP" | awk '{print $1, $5, $NF}'
"$JAVA" -jar "$SERVER_JAR" restore --backup "$BACKUP" --key-file "$WORK/backup.key" --database "$WORK/restored/deck-duel.db"

echo "== graceful stop"
kill -TERM "$SERVER_PID"
wait "$SERVER_PID" || true
SERVER_PID=
grep -E "stopping:" server.log | sed 's/^.*INFO: //'
echo "== smoke test passed"
