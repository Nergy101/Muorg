#!/usr/bin/env bash
#
# Run the API smoke test against a throwaway muorg-server.
#
# Type-checking proves the client matches the spec; this proves the spec matches
# the server that actually runs. It builds the server, starts it on a temporary
# database, runs scripts/smoke-api.ts against it, and tears it down.
#
# Usage:
#   ./scripts/smoke-api.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PORT="${MUORG_SMOKE_PORT:-7719}"
KEY="smoke-key"
WORKDIR="$(mktemp -d)"
SERVER_PID=""

cleanup() {
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

echo "==> Building muorg-server"
cargo build --manifest-path server/Cargo.toml --bin muorg-server

cat > "$WORKDIR/muorg-server.toml" <<EOF
[server]
host = "127.0.0.1"
port = $PORT
api_key = "$KEY"

[library]
content_paths = []
scan_on_startup = false

[storage]
db_path = "$WORKDIR/muorg.db"
backup_dir = "$WORKDIR/backups"

[cors]
allowed_origins = ["*"]
EOF

echo "==> Starting server on 127.0.0.1:$PORT"
./server/target/debug/muorg-server --config "$WORKDIR/muorg-server.toml" \
  > "$WORKDIR/server.log" 2>&1 &
SERVER_PID=$!

for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:$PORT/api/health" > /dev/null; then
    break
  fi
  # A server that died on startup will never answer; fail fast with its log.
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "::error::muorg-server exited during startup"
    cat "$WORKDIR/server.log"
    exit 1
  fi
  sleep 0.5
done

if ! curl -sf "http://127.0.0.1:$PORT/api/health" > /dev/null; then
  echo "::error::muorg-server did not become healthy within 30s"
  cat "$WORKDIR/server.log"
  exit 1
fi

echo "==> Running the smoke test"
MUORG_URL="http://127.0.0.1:$PORT" MUORG_KEY="$KEY" pnpm smoke:api
