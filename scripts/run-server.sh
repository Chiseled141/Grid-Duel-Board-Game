#!/usr/bin/env bash
# Starts the Onitama server. Usage: scripts/run-server.sh [port] [db-file]
PORT="${1:-5555}"
DB="${2:-data/onitama.db}"
cd "$(dirname "$0")/.." || exit 1
exec java -jar target/onitama.jar server --port "$PORT" --db "$DB"
