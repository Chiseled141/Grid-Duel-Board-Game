#!/usr/bin/env bash
# Starts the Swing client. Usage: scripts/run-client.sh [host] [port]
HOST="${1:-127.0.0.1}"
PORT="${2:-5555}"
cd "$(dirname "$0")/.." || exit 1
exec java -jar target/onitama.jar client --host "$HOST" --port "$PORT"
