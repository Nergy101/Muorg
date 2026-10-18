/**
 * Single source for the desktop keyboard shortcut reference. Both the Key Map
 * modal and Settings → Keyboard render from this list, so they cannot drift.
 * Keep entries in sync with the actual handlers in `App.vue` and
 * `LibraryHeader.vue` (and the Ctrl+Z/Ctrl+Shift+Z handlers in
 * `MetadataEditor.vue`).
 */

export interface KeyMapEntry {
  keys: string;
  description: string;
}

export const KEY_MAP_ENTRIES: readonly KeyMapEntry[] = [
  { keys: "Ctrl+F / ⌘F", description: "Focus search bar" },
  { keys: "Ctrl+A / ⌘A", description: "Select all tracks in current view and enable multi-select" },
  { keys: "Ctrl+M / ⌘M", description: "Open maximized player" },
  { keys: "Ctrl+S / ⌘S", description: "Toggle sidebar" },
  { keys: "Ctrl+1 / ⌘1", description: "Show library panel" },
  { keys: "Ctrl+2 / ⌘2", description: "Toggle metadata editor panel for current selection" },
  { keys: "Ctrl+3 / ⌘3", description: "Toggle full player panel" },
  { keys: "Ctrl+4 / ⌘4", description: "Toggle queue panel" },
  { keys: "Ctrl+K / ⌘K", description: "Open key map" },
  { keys: "Ctrl+T / ⌘T", description: "Toggle dark / light theme" },
  { keys: "Ctrl+Z / ⌘Z", description: "Undo last change (while the metadata editor is open)" },
  { keys: "Ctrl+Shift+Z / ⌘⇧Z", description: "Redo last undone change (while the metadata editor is open)" },
  { keys: "Escape", description: "Discard unsaved changes in the metadata editor, or close the maximized player" },
  { keys: "↓ Arrow Down", description: "Move focus down in track list" },
  { keys: "↑ Arrow Up", description: "Move focus up in track list" },
  { keys: "Space", description: "On group row: expand or collapse. On track row: select (add to selection in multi-select)" },
  { keys: "Enter", description: "With one track selected: start playback or pause if already playing" },
  { keys: "1–5", description: "Set star rating for selected track(s) (no modifier key, focus not in a text field)" },
  { keys: "0", description: "Clear star rating for selected track(s)" },
];
