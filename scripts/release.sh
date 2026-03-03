#!/usr/bin/env bash
set -e

VERSION="${1:?Usage: $0 <version> (e.g. 0.1.0)}"
export VERSION

# Run from repo root
cd "$(dirname "$0")/.."

# Update package.json
node -e "
const fs = require('fs');
const p = require('./package.json');
p.version = process.env.VERSION;
fs.writeFileSync('package.json', JSON.stringify(p, null, 2));
"

# Update src-tauri/tauri.conf.json
node -e "
const fs = require('fs');
const t = require('./src-tauri/tauri.conf.json');
t.version = process.env.VERSION;
fs.writeFileSync('src-tauri/tauri.conf.json', JSON.stringify(t, null, 2));
"

# Update src-tauri/Cargo.toml
sed "s/^version = \".*\"$/version = \"$VERSION\"/" src-tauri/Cargo.toml > src-tauri/Cargo.toml.tmp && mv src-tauri/Cargo.toml.tmp src-tauri/Cargo.toml

TAG="v${VERSION}"

# Stage all version-bumped and generated files (schemas get regenerated with new version)
git add package.json src-tauri/tauri.conf.json src-tauri/Cargo.toml
git add src-tauri/gen/schemas/*.json 2>/dev/null || true

if git status --porcelain | grep -q .; then
  git commit -m "chore: release v$VERSION"
  echo "Committed version bump for v$VERSION"
else
  echo "No version file changes to commit (already clean?)."
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists. Skipping tag creation."
else
  git tag "$TAG"
  echo "Created tag $TAG"
fi

echo "Pushing branch and $TAG to origin..."
git push origin
git push origin "$TAG"
