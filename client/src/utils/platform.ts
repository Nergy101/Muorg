/** True when running on macOS (Tauri desktop build on a Mac). */
export function isMac(): boolean {
  if (typeof navigator === "undefined") return false;
  return /Macintosh|Mac OS X/.test(navigator.userAgent);
}

/** Label for the primary modifier key: "Cmd" on macOS, "Ctrl" elsewhere. */
export function modKeyLabel(): string {
  return isMac() ? "Cmd" : "Ctrl";
}

/** "Cmd+K" on macOS, "Ctrl+K" elsewhere. */
export function modKeyCombo(key: string): string {
  return `${modKeyLabel()}+${key}`;
}
