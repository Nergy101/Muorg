#!/usr/bin/env bash
# Starts Muorg with mock data and guides you through taking documentation screenshots.
# Run from anywhere: bash docs/screenshots/screenshots.sh
#
# macOS:  uses screencapture (built-in, no extra deps)
# Linux:  uses scrot (apt install scrot) or ImageMagick import + xdotool
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

VIEWS=(
  "table-library:Library — table view (this is the default view on startup)"
  "album-library:Album view — click Albums layout in the top bar"
  "metadata-editor:Metadata editor — select a track, open the Metadata tab in the bottom panel"
  "player:Player bar — start playback so the player bar is visible"
  "player-maximized:Maximized player — press maximize in the bottom right corner while a track is playing"
  "settings:Settings — click the gear / settings icon in the top right"
)

# ── screenshot helpers ────────────────────────────────────────────────────────

wait_for_muorg_macos() {
  echo -n "Waiting for Muorg window"
  for _ in $(seq 1 90); do
    if osascript -e 'tell application "System Events" to exists (process "Muorg")' 2>/dev/null | grep -q true; then
      # Process exists — wait a moment for the window to be fully rendered
      sleep 3
      echo " ready."
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo ""
  echo "Timed out waiting for Muorg. Check /tmp/muorg-screenshots.log for errors." >&2
  exit 1
}

take_screenshot_macos() {
  local output="$1"
  osascript << 'APPLESCRIPT' 2>/dev/null || true
tell application "System Events"
  set frontmost of (first process whose name is "Muorg") to true
end tell
APPLESCRIPT
  sleep 0.3
  # -o = no drop shadow, -x = no shutter sound, -w = click the target window
  screencapture -o -x -w "$output"
}

wait_for_muorg_linux() {
  echo -n "Waiting for Muorg window"
  for _ in $(seq 1 90); do
    if xdotool search --name "Muorg" &>/dev/null; then
      sleep 3
      echo " ready."
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo ""
  echo "Timed out waiting for Muorg." >&2
  exit 1
}

take_screenshot_linux() {
  local output="$1"
  local wid
  wid=$(xdotool search --name "Muorg" | head -1)
  xdotool windowfocus --sync "$wid"
  sleep 0.3
  if command -v scrot &>/dev/null; then
    scrot -u -z "$output"
  elif command -v import &>/dev/null; then
    import -window "$wid" "$output"
  else
    echo "  No screenshot tool found. Install scrot: sudo apt install scrot" >&2
    return 1
  fi
}

is_skip() {
  case "$1" in
    s|S) return 0 ;;
    *)   return 1 ;;
  esac
}

# ── main ─────────────────────────────────────────────────────────────────────

OS="$(uname)"

echo "Starting Muorg with mock data..."
cd "$REPO_ROOT/client"
pnpm run dev:mock &>/tmp/muorg-screenshots.log &
APP_PID=$!

cleanup() { kill "$APP_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

if [[ "$OS" == "Darwin" ]]; then
  wait_for_muorg_macos
else
  wait_for_muorg_linux
fi

echo ""
echo "Muorg is open. For each view below:"
echo "  - Navigate to the described state"
echo "  - Press Enter to take the screenshot (click the Muorg window when the cursor changes)"
echo "  - Type 's' + Enter to skip"
echo ""

for entry in "${VIEWS[@]}"; do
  name="${entry%%:*}"
  instruction="${entry#*:}"
  output="$OUT_DIR/${name}.png"

  echo "──────────────────────────────────────────"
  echo "  Screenshot : $name"
  echo "  Navigate to: $instruction"
  printf "  Press Enter (or 's' to skip)... "
  read -r response

  if is_skip "$response"; then
    echo "  Skipped."
    continue
  fi

  if [[ "$OS" == "Darwin" ]]; then
    take_screenshot_macos "$output"
  else
    take_screenshot_linux "$output"
  fi

  echo "  Saved → docs/screenshots/${name}.png"
  echo ""
done

echo "Done. All screenshots saved to docs/screenshots/"
