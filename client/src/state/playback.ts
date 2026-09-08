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
