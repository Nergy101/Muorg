#!/usr/bin/env bash
# Usage: ./scripts/release-desktop.sh <version>  (e.g. ./scripts/release-desktop.sh 5.0.0)
# Bumps only the desktop app's version and pushes a desktop-v* tag, triggering
# the desktop-only release pipeline (.github/workflows/release-desktop.yml,
# the ~25-min Tauri matrix for macOS/Windows/Linux) instead of the full v*
# release that also rebuilds the Docker images.
set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>  (e.g. $0 5.0.0)" >&2
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  echo "Error: version must be semver (e.g. 5.0.0)" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! git -C "$REPO_ROOT" diff --quiet -- . \
  || ! git -C "$REPO_ROOT" diff --cached --quiet; then
  echo "Error: working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

BRANCH=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
  echo "Warning: not on main branch (currently on '$BRANCH'). Continue? [y/N]" >&2
  read -r answer
  [[ "$answer" =~ ^[Yy]$ ]] || exit 1
fi

TAG="desktop-v${VERSION}"

if git -C "$REPO_ROOT" rev-parse "$TAG" &>/dev/null; then
  echo "Error: tag $TAG already exists." >&2
  exit 1
fi

echo "Bumping desktop app version to $VERSION..."

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

# The desktop crate's own lockfile records its version too, and nothing above
# touches it. `cargo metadata` refreshes it without a build, so the bump does
# not leave the tree dirty for whoever works next.
cargo metadata --manifest-path "$REPO_ROOT/client/src-tauri/Cargo.toml" \
  --format-version 1 >/dev/null

# Server, web and Android are NOT bumped here: they ship on the v* and
# android-v* tags (see docs-site/docs/compatibility.md). Desktop releases are
# cut with this script.

git -C "$REPO_ROOT" add \
  client/package.json \
  client/src-tauri/tauri.conf.json \
  client/src-tauri/Cargo.toml \
  client/src-tauri/Cargo.lock

git -C "$REPO_ROOT" commit -m "🔖 chore(desktop): bump version to $VERSION"

git -C "$REPO_ROOT" tag "$TAG"

echo "Pushing commit and tag $TAG..."
git -C "$REPO_ROOT" push origin main "$TAG"

echo "Done. Desktop release $TAG triggered — check GitHub Actions for progress."
