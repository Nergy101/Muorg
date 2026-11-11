import { ref } from "vue";

/**
 * Shared collapsed state of the desktop sidebar rail. SidebarNav owns the
 * watch (player route + art-fullscreen collapse it, the brand-row chevron
 * toggles it manually); MiniPlayer reads it to decide whether the bottom
 * bar shows its own volume control (collapsed rail hides the one in the
 * nav, so the mini player carries it instead).
 */
export const sidebarCollapsed = ref(false);
