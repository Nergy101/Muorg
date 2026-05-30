#!/usr/bin/env bash
# Usage: ./scripts/release-android.sh <version>  (e.g. ./scripts/release-android.sh 2.16.5)
# Bumps only the Android version and pushes an android-v* tag, triggering the
# Android-only release pipeline (~8 min) instead of the full 30-min release.
set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>  (e.g. $0 2.16.5)" >&2
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  echo "Error: version must be semver (e.g. 2.16.5)" >&2
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

TAG="android-v${VERSION}"

if git -C "$REPO_ROOT" rev-parse "$TAG" &>/dev/null; then
  echo "Error: tag $TAG already exists." >&2
  exit 1
fi

echo "Bumping Android version to $VERSION..."

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

git -C "$REPO_ROOT" add android-client/app/build.gradle.kts
git -C "$REPO_ROOT" commit -m "🔖 chore(android): bump version to $VERSION"

git -C "$REPO_ROOT" tag "$TAG"

echo "Pushing commit and tag $TAG..."
git -C "$REPO_ROOT" push origin main "$TAG"

echo "Done. Android release $TAG triggered — check GitHub Actions for progress."
