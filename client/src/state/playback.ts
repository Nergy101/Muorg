import { ref } from "vue";

/** Seconds already played before the current FLAC stream chunk started. Added to el.currentTime for display. */
export const flacSeekOffset = ref(0);

/**
 * Whether the shared `<audio>` element is currently playing.
 *
 * `isPlaying` is local to each player component, but the library table needs it
 * too — the now-playing bars freeze rather than disappear when a track is
 * paused. PlayerBar owns the audio element and mirrors its state here.
 */
export const isPlayingNow = ref(false);

// ── Play history (Q31) ──────────────────────────────────────────────────────
// Previous steps back through what was actually played, not the table
// neighbour — which is simply wrong while shuffling. Module state because
// PlayerBar and PlayScreenPlayBar are mounted at the same time and both drive
// the same selection; PlayerBar's watcher is the single recorder.

const PLAY_HISTORY_LIMIT = 100;

/** Track ids in play order, oldest first; the last entry is the most recent. */
export const playHistory = ref<number[]>([]);
/** Tracks stepped back over with Previous, most recent first; Next returns through these. */
export const playFuture = ref<number[]>([]);

/** Set when Previous selects a track, so the change recorder treats the move as a step back. */
let pendingBackTarget: number | null = null;

/**
 * Record a track change: `fromId` was current, `toId` is the new current (null
 * when playback stops). Called from PlayerBar's selection watcher whenever the
 * current track actually changes.
 */
export function recordPlayHistory(fromId: number | null, toId: number | null) {
  if (toId == null || fromId === toId) return;
  if (pendingBackTarget === toId) {
    // Step back: stepBackPlayHistory already moved the stacks.
    pendingBackTarget = null;
    return;
  }
  pendingBackTarget = null;
  if (fromId == null) {
    // Fresh start after a stop: no back-stack to return through.
    playFuture.value = [];
    return;
  }
  if (playFuture.value.length > 0 && playFuture.value[0] === toId) {
    // Forward move (Next after Previous): consume the back-stack entry.
    playFuture.value.shift();
  } else {
    // A fresh forward move abandons the back-stack.
    playFuture.value = [];
  }
  playHistory.value.push(fromId);
  if (playHistory.value.length > PLAY_HISTORY_LIMIT) {
    playHistory.value.splice(0, playHistory.value.length - PLAY_HISTORY_LIMIT);
  }
}

/**
 * Step back through the play history: returns the most recently played track
 * id (skipping entries whose track left the catalog) and moves the current
 * track onto the back-stack so Next can return to it. Returns null when there
 * is no history to step back to.
 */
export function stepBackPlayHistory(currentId: number, isKnown: (id: number) => boolean): number | null {
  while (playHistory.value.length > 0) {
    const id = playHistory.value[playHistory.value.length - 1];
    playHistory.value.pop();
    if (isKnown(id)) {
      playFuture.value.unshift(currentId);
      pendingBackTarget = id;
      return id;
    }
  }
  return null;
}

/** The track id Next should return to after stepping back, if any. */
export function peekPlayFuture(isKnown: (id: number) => boolean): number | null {
  while (playFuture.value.length > 0) {
    const id = playFuture.value[0];
    if (isKnown(id)) return id;
    playFuture.value.shift();
  }
  return null;
}
