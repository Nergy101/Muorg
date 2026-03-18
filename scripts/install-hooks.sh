#!/bin/sh
set -e

HOOKS_DIR="$(git rev-parse --git-dir)/hooks"

cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/bin/sh
pnpm run check
EOF

chmod +x "$HOOKS_DIR/pre-commit"
echo "Git hooks installed."
