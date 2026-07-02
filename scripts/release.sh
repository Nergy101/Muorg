#!/usr/bin/env bash
# Usage: ./scripts/release.sh <version>  (e.g. ./scripts/release.sh 2.1.0)
set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>  (e.g. $0 2.1.0)" >&2
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  echo "Error: version must be semver (e.g. 2.1.0)" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! git -C "$REPO_ROOT" diff --quiet || ! git -C "$REPO_ROOT" diff --cached --quiet; then
  echo "Error: working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

BRANCH=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
  echo "Warning: not on main branch (currently on '$BRANCH'). Continue? [y/N]" >&2
  read -r answer
  [[ "$answer" =~ ^[Yy]$ ]] || exit 1
fi

if git -C "$REPO_ROOT" rev-parse "v$VERSION" &>/dev/null; then
  echo "Error: tag v$VERSION already exists." >&2
  exit 1
fi

echo "Bumping version to $VERSION..."

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/client/package.json';
  const pkg = JSON.parse(fs.readFileSync(file, 'utf8'));
  pkg.version = '$VERSION';
  fs.writeFileSync(file, JSON.stringify(pkg, null, 2) + '\n');
"

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/client/src-tauri/tauri.conf.json';
  const conf = JSON.parse(fs.readFileSync(file, 'utf8'));
  conf.version = '$VERSION';
  fs.writeFileSync(file, JSON.stringify(conf, null, 2) + '\n');
"

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/client/src-tauri/Cargo.toml';
  const content = fs.readFileSync(file, 'utf8');
  fs.writeFileSync(file, content.replace(/^version = \".*\"\$/m, 'version = \"$VERSION\"'));
"

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/server/crates/muorg-server/Cargo.toml';
  const content = fs.readFileSync(file, 'utf8');
  fs.writeFileSync(file, content.replace(/^version = \".*\"\$/m, 'version = \"$VERSION\"'));
"

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/android-client/app/build.gradle.kts';
  const content = fs.readFileSync(file, 'utf8');
  const [maj, min, pat] = '$VERSION'.split('.').map(Number);
  const code = maj * 10000 + min * 100 + pat;
  const updated = content
    .replace(/versionCode = \d+/, 'versionCode = ' + code)
    .replace(/versionName = \"[^\"]*\"/, 'versionName = \"$VERSION\"');
  fs.writeFileSync(file, updated);
"

node -e "
  const fs = require('fs');
  const file = '$REPO_ROOT/web-client/package.json';
  const pkg = JSON.parse(fs.readFileSync(file, 'utf8'));
  pkg.version = '$VERSION';
  fs.writeFileSync(file, JSON.stringify(pkg, null, 2) + '\n');
"

git -C "$REPO_ROOT" add \
  client/package.json \
  client/src-tauri/tauri.conf.json \
  client/src-tauri/Cargo.toml \
  server/crates/muorg-server/Cargo.toml \
  android-client/app/build.gradle.kts \
  web-client/package.json

git -C "$REPO_ROOT" commit -m "🔖 chore: bump version to $VERSION"

git -C "$REPO_ROOT" tag "v$VERSION"

echo "Pushing commit and tag v$VERSION..."
git -C "$REPO_ROOT" push origin main "v$VERSION"

echo "Done. Release v$VERSION triggered — check GitHub Actions for progress."
