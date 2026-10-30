import { nextTick } from "vue";
import { router } from "../router";
import { usePlayerStore } from "../stores/player";
import { useSelectionStore } from "../stores/selection";
import { isEditorOpen, openMetadataEditor } from "./useMetadataEditor";
import { searchFocusSignal } from "./useSearchFocus";
import { closeKeyMap, keyMapOpen, toggleKeyMap } from "./useKeyMapModal";
import { visibleIds } from "./useSelectableList";
import { showToast } from "./useToast";

/**
 * Keyboard shortcuts, registered on `window` once from App.vue. The reference
 * list the user sees lives in `../keyMap.ts` — keep the two in step.
 *
 *   Space / K    play or pause
 *   ← / →        seek back / forward 10s (hold to scrub; repeat allowed)
 *   J / L        seek back / forward 10s (Spotify/Vim muscle memory)
 *   Ctrl+← / →   previous / next track
 *   Shift+↑ / ↓  volume up / down
 *   M            mute / unmute (restores the previous level)
 *   S / R        toggle shuffle / cycle repeat
 *   F / Esc      open / leave the full-screen player
 *   /            search
 *   G then H L P Q R S   go to Home / Library / Playlists / Queue / Reports / Settings
 *   ? / Ctrl+K   key map
 *   Ctrl+A       select all tracks in the visible list
 *   E            edit metadata of the selection, or of the playing track
 *
 * Everything is ignored while typing into an editable field. Space additionally
 * steps aside for focused controls (button, link, select…): the browser would
 * activate them anyway, and hijacking the key too would double-toggle playback.
 * A handler that already consumed the event (`defaultPrevented`, e.g. the
 * library table's own row navigation) is never run a second time here.
 */
const SEEK_STEP_SECS = 10;
const VOLUME_STEP = 0.05;
/** How long after `g` the second key of a go-to sequence is still accepted. */
const SEQUENCE_MS = 1200;

const GO_TO: Record<string, string> = {
  h: "home",
  l: "library",
  p: "playlists",
  q: "queue",
  r: "reports",
  s: "settings",
};

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

/** A modal dialog is open: single-key shortcuts must not act on the page behind it. */
function isDialogOpen(): boolean {
  return document.querySelector("[role='dialog'][aria-modal='true']") !== null;
}

async function focusSearch(): Promise<void> {
  if (router.currentRoute.value.name !== "search") await router.push({ name: "search" });
  await nextTick();
  searchFocusSignal.value++;
}

let installed = false;
let pendingGoTo = false;
let goToTimer: ReturnType<typeof setTimeout> | undefined;

export function useKeyboardShortcuts(): void {
  if (installed) return;
  installed = true;

  window.addEventListener("keydown", (e) => {
    if (e.defaultPrevented) return;
    // Alt combos are browser menu shortcuts.
    if (e.altKey) return;

    const player = usePlayerStore();
    const key = e.key;
    const mod = e.ctrlKey || e.metaKey;

    if (key === "Escape" && keyMapOpen.value) {
      e.preventDefault();
      closeKeyMap();
      return;
    }

    // --- Modifier combos -------------------------------------------------

    if (mod && (key === "k" || key === "K")) {
      e.preventDefault();
      toggleKeyMap();
      return;
    }

    if (mod && (key === "a" || key === "A")) {
      if (isEditableTarget(e.target) || isDialogOpen()) return;
      const ids = visibleIds();
      if (ids.length === 0) return;
      e.preventDefault();
      useSelectionStore().selectAll(ids);
      return;
    }

    // Ctrl+← / Ctrl+→: skip tracks. Repeat would skip a track per held key
    // repeat, so ignore repeats here (same for the toggle keys below).
    if (e.ctrlKey && !e.metaKey && (key === "ArrowLeft" || key === "ArrowRight")) {
      if (e.repeat || isEditableTarget(e.target)) return;
      e.preventDefault();
      if (key === "ArrowLeft") player.skipPrevious();
      else player.skipNext();
      return;
    }

    // Cmd combos are OS-level and every other Ctrl combo is the browser's.
    if (mod) return;

    // --- Plain keys ------------------------------------------------------

    if (isEditableTarget(e.target)) return;

    if (key === "?") {
      e.preventDefault();
      toggleKeyMap();
      return;
    }

    if (pendingGoTo) {
      pendingGoTo = false;
      clearTimeout(goToTimer);
      const name = GO_TO[key.toLowerCase()];
      if (name && !e.shiftKey) {
        e.preventDefault();
        void router.push({ name });
        return;
      }
      // Anything else cancels the sequence and is handled normally below.
    }

    if (isDialogOpen()) return;

    if ((key === "g" || key === "G") && !e.shiftKey && !e.repeat) {
      pendingGoTo = true;
      clearTimeout(goToTimer);
      goToTimer = setTimeout(() => {
        pendingGoTo = false;
      }, SEQUENCE_MS);
      return;
    }

    if (key === "/") {
      e.preventDefault();
      void focusSearch();
      return;
    }

    if (key === " " || key === "k" || key === "K" || key === "m" || key === "M") {
      if (e.repeat || (key === " " && isControlTarget(e.target))) return;
      e.preventDefault();
      if (key === "m" || key === "M") {
        player.toggleMute();
        showToast(player.volume > 0 ? `Volume ${Math.round(player.volume * 100)}%` : "Muted");
      } else {
        player.playPause();
      }
      return;
    }

    if (e.shiftKey && (key === "ArrowUp" || key === "ArrowDown")) {
      e.preventDefault();
      const next = Math.min(1, Math.max(0, player.volume + (key === "ArrowUp" ? VOLUME_STEP : -VOLUME_STEP)));
      player.setVolume(next);
      showToast(`Volume ${Math.round(next * 100)}%`);
      return;
    }

    if (key === "ArrowLeft" || key === "ArrowRight" || key === "j" || key === "J" || key === "l" || key === "L") {
      e.preventDefault();
      const back = key === "ArrowLeft" || key === "j" || key === "J";
      const target = player.positionSecs + (back ? -SEEK_STEP_SECS : SEEK_STEP_SECS);
      void player.seekTo(Math.max(0, Math.min(player.durationSecs, target)));
      return;
    }

    if (key === "s" || key === "S") {
      if (e.repeat) return;
      e.preventDefault();
      player.toggleShuffle();
      showToast(player.shuffleEnabled ? "Shuffle on" : "Shuffle off");
      return;
    }

    if (key === "r" || key === "R") {
      if (e.repeat) return;
      e.preventDefault();
      player.cycleRepeatMode();
      showToast(`Repeat ${player.repeatMode}`);
      return;
    }

    if (key === "f" || key === "F") {
      if (e.repeat || !player.currentTrack) return;
      e.preventDefault();
      if (router.currentRoute.value.name === "player") router.back();
      else void router.push({ name: "player" });
      return;
    }

    if (key === "Escape") {
      // Selection and the metadata editor handle their own Escape.
      if (useSelectionStore().hasSelection || isEditorOpen.value) return;
      if (router.currentRoute.value.name === "player") {
        e.preventDefault();
        router.back();
      }
      return;
    }

    if (key === "e" || key === "E") {
      if (e.repeat) return;
      const selection = useSelectionStore();
      const ids = selection.hasSelection ? selection.ids : player.currentTrack ? [player.currentTrack.id] : [];
      if (ids.length === 0) return;
      e.preventDefault();
      openMetadataEditor(ids);
    }
  });
}
