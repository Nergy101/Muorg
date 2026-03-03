#!/usr/bin/env bash
# Build Muorg with updater artifacts (signed).
#
# Required for signed update bundles:
#   export TAURI_SIGNING_PRIVATE_KEY="<path-to-private-key.pem or raw key content>"
# Optional: export TAURI_SIGNING_PRIVATE_KEY_PASSWORD="<password>" if the key is encrypted
#
# Optional: inject public key into tauri.conf.json so you don't commit it:
#   export TAURI_SIGNING_PUBLIC_KEY="<raw public key content from .pub file>"
# If unset, the pubkey already in tauri.conf.json is used.
#
# Usage: ./scripts/build-with-updater.sh [tauri build args...]
# Example: TAURI_SIGNING_PRIVATE_KEY="$(cat ~/.tauri/muorg.key)" ./scripts/build-with-updater.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONF="${ROOT}/src-tauri/tauri.conf.json"

if [ -z "${TAURI_SIGNING_PRIVATE_KEY:-}" ]; then
  echo "Warning: TAURI_SIGNING_PRIVATE_KEY is not set. Update artifacts will not be signed." >&2
  echo "Set it before running this script to produce signed updater bundles." >&2
fi

if [ -n "${TAURI_SIGNING_PUBLIC_KEY:-}" ]; then
  echo "Injecting TAURI_SIGNING_PUBLIC_KEY into tauri.conf.json ..."
  CONFIG_PATH="$CONF" node -e "
    const fs = require('fs');
    const path = process.env.CONFIG_PATH;
    const conf = JSON.parse(fs.readFileSync(path, 'utf8'));
    if (!conf.plugins) conf.plugins = {};
    if (!conf.plugins.updater) conf.plugins.updater = {};
    conf.plugins.updater.pubkey = process.env.TAURI_SIGNING_PUBLIC_KEY;
    fs.writeFileSync(path, JSON.stringify(conf, null, 2));
  "
fi

cd "$ROOT"
pnpm tauri build "$@"
