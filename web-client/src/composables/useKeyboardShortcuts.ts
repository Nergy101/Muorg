import { usePlayerStore } from "../stores/player";
import { showToast } from "./useToast";

/**
 * Desktop keyboard shortcuts (2026-08): the desktop layout brought a mouse +
 * keyboard audience to the web client, but nothing listened to the keyboard
 * beyond focused controls. This registers the standard transport keys on
 * `window` once, from App.vue:
 *
 *   Space / K    play or pause
 *   ← / →        seek back / forward 10s (hold to scrub; repeat allowed)
 *   J / L        seek back / forward 10s (Spotify/Vim muscle memory)
 *   Ctrl+← / →   previous / next track
 *   M            mute / unmute (restores the previous level)
 *
 * Everything is ignored while typing into an editable field. Space additionally
 * steps aside for focused controls (button, link, select…): the browser would
 * activate them anyway, and hijacking the key too would double-toggle playback.
 */
const SEEK_STEP_SECS = 10;

/**
 * Typing contexts: keys must never hijack text entry. Real keydown targets are
 * the focused element (input, contenteditable child, …), so closest() covers
 * both the field and anything inside it. Non-element targets (document.body
 * when nothing is focused) are not editable.
 */
function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true;
  if (target.isContentEditable) return true;
  return Boolean(target.closest("input, textarea, select, [contenteditable]"));
}

/**
 * Space-only guard: browsers activate a focused control (button, link, slider)
 * on Space, so hijacking it too would double-toggle playback. K, arrows and M
 * do not activate controls and stay live with a focused button — after clicking
 * the mini player's play button, K must still pause.
 */
function isControlTarget(target: EventTarget | null): boolean {
  if (isEditableTarget(target)) return true;
  if (!(target instanceof HTMLElement)) return false;
  const role = target.getAttribute("role");
  if (role === "button" || role === "slider" || role === "menuitem" || role === "switch") return true;
  return Boolean(target.closest("button, a[href], [role='button'], [role='slider']"));
}

let installed = false;

export function useKeyboardShortcuts(): void {
  if (installed) return;
  installed = true;

  window.addEventListener("keydown", (e) => {
    // Cmd combos are OS-level; Alt combos are browser menu shortcuts. Ctrl is
    // only ours in the exact Ctrl+←/→ skip pair below.
    if (e.metaKey || e.altKey) return;

    const player = usePlayerStore();
    const key = e.key;

    // Ctrl+← / Ctrl+→: skip tracks. Repeat would skip a track per held key
    // repeat, so ignore repeats here (same for the toggle keys below).
    if (e.ctrlKey && (key === "ArrowLeft" || key === "ArrowRight")) {
      if (e.repeat || isEditableTarget(e.target)) return;
      e.preventDefault();
      if (key === "ArrowLeft") player.skipPrevious();
      else player.skipNext();
      return;
    }

    if (key === " " || key === "k" || key === "K" || key === "m" || key === "M") {
      if (e.repeat || (key === " " ? isControlTarget(e.target) : isEditableTarget(e.target))) return;
      e.preventDefault();
      if (key === "m" || key === "M") {
        player.toggleMute();
        showToast(player.volume > 0 ? `Volume ${Math.round(player.volume * 100)}%` : "Muted");
      } else {
        player.playPause();
      }
      return;
    }

    if (key === "ArrowLeft" || key === "ArrowRight" || key === "j" || key === "J" || key === "l" || key === "L") {
      if (isEditableTarget(e.target)) return;
      e.preventDefault();
      const back = key === "ArrowLeft" || key === "j" || key === "J";
      const target = player.positionSecs + (back ? -SEEK_STEP_SECS : SEEK_STEP_SECS);
      void player.seekTo(Math.max(0, Math.min(player.durationSecs, target)));
      return;
    }
  });
}
