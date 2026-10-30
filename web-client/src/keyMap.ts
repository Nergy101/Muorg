/**
 * Single source for the web client's keyboard shortcut reference. The Key Map
 * modal and Settings → Keyboard both render from this list, and the handlers in
 * `composables/useKeyboardShortcuts.ts` implement exactly these entries — keep
 * them in step.
 *
 * Browsers reserve Ctrl/⌘+T, +S, +W, +N and Ctrl/⌘+1…9, so the desktop app's
 * panel-switching shortcuts are replaced by single keys and `g`-sequences here.
 */

export interface WebKeyMapEntry {
  keys: string;
  description: string;
}

export interface WebKeyMapGroup {
  title: string;
  entries: readonly WebKeyMapEntry[];
}

export const WEB_KEY_MAP: readonly WebKeyMapGroup[] = [
  {
    title: "Playback",
    entries: [
      { keys: "Space / K", description: "Play or pause" },
      { keys: "← / → or J / L", description: "Seek back / forward 10 seconds" },
      { keys: "Ctrl + ← / →", description: "Previous / next track" },
      { keys: "Shift + ↑ / ↓", description: "Volume up / down" },
      { keys: "M", description: "Mute / unmute" },
      { keys: "S", description: "Toggle shuffle" },
      { keys: "R", description: "Cycle repeat (off → all → one)" },
      { keys: "F", description: "Open the full-screen player" },
      { keys: "Esc", description: "Leave the full-screen player" },
    ],
  },
  {
    title: "Navigation",
    entries: [
      { keys: "/", description: "Search" },
      { keys: "G then H", description: "Go to Home" },
      { keys: "G then L", description: "Go to Library" },
      { keys: "G then P", description: "Go to Playlists" },
      { keys: "G then Q", description: "Go to the queue" },
      { keys: "G then R", description: "Go to Reports" },
      { keys: "G then S", description: "Go to Settings" },
      { keys: "? or Ctrl/⌘ + K", description: "Show this key map" },
    ],
  },
  {
    title: "Library and selection",
    entries: [
      { keys: "Click · Ctrl/⌘ + click · Shift + click", description: "Select a track · add to selection · select a range" },
      { keys: "Ctrl/⌘ + A", description: "Select all tracks in the current list" },
      { keys: "Esc", description: "Clear the selection" },
      { keys: "E", description: "Edit metadata of the selection (or the playing track)" },
      { keys: "↑ / ↓ · Space · Enter", description: "In the library table: move focus · select or expand · play or pause" },
      { keys: "0 – 5", description: "In the library table: set the star rating of the selection (0 clears it)" },
    ],
  },
];
