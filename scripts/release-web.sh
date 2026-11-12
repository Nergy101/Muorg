#!/usr/bin/env bash
# Usage: ./scripts/release-web.sh <version>  (e.g. ./scripts/release-web.sh 4.5.1)
# Bumps only the web-client version and pushes a v* tag. The Release workflow
# diffs the changed paths since the previous v* tag, so a web-only bump
# publishes just the muorg-web Docker image — no desktop or server builds.
set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>  (e.g. $0 4.5.1)" >&2
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  echo "Error: version must be semver (e.g. 4.5.1)" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! git -C "$REPO_ROOT" diff --quiet -- . ':(exclude)android-client/gradlew.bat' \
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

TAG="v${VERSION}"

if git -C "$REPO_ROOT" rev-parse "$TAG" &>/dev/null; then
  echo "Error: tag $TAG already exists." >&2
  exit 1
fi

echo "Bumping web-client version to $VERSION..."

node -e "
  const fs = require('fs');
  for (const file of ['$REPO_ROOT/web-client/package.json', '$REPO_ROOT/web-client/package-lock.json']) {
    const j = JSON.parse(fs.readFileSync(file, 'utf8'));
    j.version = '$VERSION';
    if (j.packages && j.packages['']) j.packages[''].version = '$VERSION';
    fs.writeFileSync(file, JSON.stringify(j, null, 2) + '\n');
  }
"

git -C "$REPO_ROOT" add web-client/package.json web-client/package-lock.json
git -C "$REPO_ROOT" commit -m "🔖 chore(web): bump version to $VERSION"

git -C "$REPO_ROOT" tag "$TAG"

echo "Pushing commit and tag $TAG..."
git -C "$REPO_ROOT" push origin main "$TAG"

echo "Done. Web release $TAG triggered — check GitHub Actions for progress."
