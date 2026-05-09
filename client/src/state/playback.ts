import { ref } from "vue";

/** Seconds already played before the current FLAC stream chunk started. Added to el.currentTime for display. */
export const flacSeekOffset = ref(0);
