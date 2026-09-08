#!/usr/bin/env bash
#
# Regenerate every client-side view of the server API from one source of truth.
#
#   server/crates/muorg-server/src/routes/*.rs   (#[utoipa::path] + ToSchema)
#            |
#            v
#   server/openapi.json                          (snapshot, checked in)
#            |
#            +--> src/api/schema.d.ts                              (TypeScript, shared by client/ and web-client/)
#            +--> android-client/.../data/api/ApiSchema.kt          (Kotlin models)
#
# Run it after changing any route or wire struct, and commit the result.
# `--check` regenerates into a temp dir and diffs instead of writing, which is
# what CI uses to catch a spec that drifted from the handlers.
#
# Usage:
#   ./scripts/generate-api-clients.sh
#   ./scripts/generate-api-clients.sh --check

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CHECK=0
[[ "${1:-}" == "--check" ]] && CHECK=1

SPEC="server/openapi.json"
TS_OUT="src/api/schema.d.ts"
KT_OUT="android-client/app/src/main/java/nl/muorg/android/data/api/schema/ApiSchema.kt"

if [[ $CHECK -eq 1 ]]; then
  STAGE="$(mktemp -d)"
  trap 'rm -rf "$STAGE"' EXIT
  # Preserve the checked-in files, generate over them, diff, restore.
  cp "$SPEC" "$STAGE/openapi.json"
  cp "$TS_OUT" "$STAGE/schema.d.ts"
  cp "$KT_OUT" "$STAGE/ApiSchema.kt"
fi

echo "==> Regenerating $SPEC from the muorg-server handlers"
( cd server && UPDATE_OPENAPI=1 cargo test -p muorg-server --test openapi_snapshot --quiet )

echo "==> Generating $TS_OUT"
pnpm exec openapi-typescript "$SPEC" \
  --output "$TS_OUT" \
  --root-types \
  --alphabetize

# openapi-typescript writes its own banner; prepend ours so the file says which
# command to re-run rather than just "do not edit".
TMP_TS="$(mktemp)"
{
  echo "/**"
  echo " * GENERATED FILE — do not edit."
  echo " *"
  echo " * Source: server/openapi.json, itself derived from the muorg-server route"
  echo " * handlers. Regenerate with ./scripts/generate-api-clients.sh after any API"
  echo " * change; CI fails if this file is stale."
  echo " */"
  cat "$TS_OUT"
} > "$TMP_TS"
mv "$TMP_TS" "$TS_OUT"

echo "==> Generating $KT_OUT"
node scripts/generate-kotlin-models.mjs "$SPEC" "$KT_OUT"

if [[ $CHECK -eq 1 ]]; then
  status=0
  for pair in "$SPEC:$STAGE/openapi.json" "$TS_OUT:$STAGE/schema.d.ts" "$KT_OUT:$STAGE/ApiSchema.kt"; do
    live="${pair%%:*}"; saved="${pair##*:}"
    if ! diff -q "$saved" "$live" >/dev/null; then
      echo "::error::$live is out of date. Run ./scripts/generate-api-clients.sh and commit the result."
      diff -u "$saved" "$live" | head -60 || true
      status=1
    fi
    cp "$saved" "$live"   # leave the working tree exactly as we found it
  done
  [[ $status -eq 0 ]] && echo "==> Generated API clients are up to date."
  exit $status
fi

echo "==> Done. Review and commit:"
echo "      $SPEC"
echo "      $TS_OUT"
echo "      $KT_OUT"
