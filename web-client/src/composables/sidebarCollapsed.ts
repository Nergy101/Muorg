import { ref } from "vue";

/**
 * Shared collapsed state of the desktop sidebar rail. SidebarNav toggles it
 * manually via the brand-row chevron; MiniPlayer reads it to decide whether
 * the bottom bar shows its own volume control (collapsed rail hides the one
 * in the nav, so the mini player carries it instead).
 */
export const sidebarCollapsed = ref(false);
