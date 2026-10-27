#!/usr/bin/env bash
# Starts the Deck Duel server. Usage: scripts/run-server.sh [port] [db-file]
PORT="${1:-5555}"
DB="${2:-data/deck-duel.db}"
cd "$(dirname "$0")/.." || exit 1
exec java -jar target/deck-duel.jar server --port "$PORT" --db "$DB"
