import { ref } from "vue";

/**
 * Bumped when the already-active Library tab is re-tapped, so LibraryView can
 * scroll the currently-playing album into view (Android's scrollToActiveSignal).
 */
export const scrollToActiveSignal = ref(0);
