#!/usr/bin/env bash
# Builds the muorg-server binary and copies it into client/src-tauri/binaries/
# with the Tauri-required target-triple suffix.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"
BINARIES_DIR="$REPO_ROOT/client/src-tauri/binaries"

TRIPLE=$(rustc -vV | grep '^host:' | cut -d' ' -f2)
SRC="$SERVER_DIR/target/release/muorg-server"
DST="$BINARIES_DIR/muorg-server-${TRIPLE}"

echo "Building muorg-server (release)…"
cd "$SERVER_DIR"
cargo build --release

echo "Copying $SRC → $DST"
mkdir -p "$BINARIES_DIR"
cp "$SRC" "$DST"
chmod +x "$DST"
echo "Done. Sidecar ready at $DST"
