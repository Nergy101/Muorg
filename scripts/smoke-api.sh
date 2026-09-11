#!/usr/bin/env bash
#
# Run the API smoke test against a throwaway muorg-server.
#
# Type-checking proves the client matches the spec; this proves the spec matches
# the server that actually runs. It builds the server, starts it on a temporary
# database, seeds that database by scanning a copy of the audio fixtures, runs
# scripts/smoke-api.ts against it, and tears it down.
#
# The library is seeded rather than left empty because an empty one validates
# every list response as `[]` — which matches any item schema, so the shapes
# that actually travel (CatalogTrack, TrackMetadata, cover bytes, a stream)
# were never checked. The fixtures are the same three files the muorg-core
# tests use.
#
# Usage:
#   ./scripts/smoke-api.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PORT="${MUORG_SMOKE_PORT:-7719}"
KEY="smoke-key"
WORKDIR="$(mktemp -d)"
LIBRARY="$WORKDIR/library"
SERVER_PID=""

cleanup() {
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

echo "==> Building muorg-server"
cargo build --manifest-path server/Cargo.toml --bin muorg-server

# Seed a library from the fixtures. Copied, not referenced, because the smoke
# test writes tags and creates backups — it must not touch the checked-in files.
# The subdirectory exercises the recursive walk as well as the flat case.
FIXTURES="server/crates/muorg-core/tests/fixtures"
mkdir -p "$LIBRARY/Album"
cp "$FIXTURES/cover_front.mp3" "$LIBRARY/with-cover.mp3"
cp "$FIXTURES/cover_other.flac" "$LIBRARY/Album/other-cover.flac"
cp "$FIXTURES/no_cover.flac" "$LIBRARY/Album/no-cover.flac"
echo "==> Seeded $LIBRARY with 3 tracks"

cat > "$WORKDIR/muorg-server.toml" <<EOF
[server]
host = "127.0.0.1"
port = $PORT
api_key = "$KEY"

[library]
content_paths = ["$LIBRARY"]
scan_on_startup = true

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
