/**
 * Feature-detects whether `HTMLMediaElement.volume` can actually be changed.
 *
 * iOS Safari makes it effectively read-only: the hardware volume buttons own
 * playback level, and a programmatic `.volume = ` assignment is silently
 * ignored (the property reads back unchanged). A volume slider there would
 * just lie about what it controls, so the UI hides it instead.
 *
 * Cached after the first check — the result can't change mid-session.
 */
let cached: boolean | null = null;

export function isVolumeControlSupported(): boolean {
  if (cached != null) return cached;
  try {
    const probe = new Audio();
    probe.volume = 0.37;
    cached = Math.abs(probe.volume - 0.37) < 0.01;
  } catch {
    cached = true;
  }
  return cached;
}
