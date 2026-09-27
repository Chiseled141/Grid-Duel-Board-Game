#!/usr/bin/env bash
# Runs the load-test bot fleet. Usage: scripts/run-bots.sh [bots] [games] [host] [port]
BOTS="${1:-8}"
GAMES="${2:-4}"
HOST="${3:-127.0.0.1}"
PORT="${4:-5555}"
cd "$(dirname "$0")/.." || exit 1
exec java -jar target/onitama.jar bots --host "$HOST" --port "$PORT" --bots "$BOTS" --games "$GAMES"
