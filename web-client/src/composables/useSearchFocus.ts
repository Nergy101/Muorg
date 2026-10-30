import { ref } from "vue";

/**
 * Bumped when the already-active Search tab is re-tapped, so SearchView can
 * focus its input and open the keyboard (Android's searchFocusSignal).
 */
export const searchFocusSignal = ref(0);
