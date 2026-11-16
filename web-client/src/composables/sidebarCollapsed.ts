import { ref, type Ref } from "vue";

/**
 * Shared collapsed state of the desktop sidebar rail. SidebarNav owns the
 * watch (player route + art-fullscreen collapse it, the brand-row chevron
 * toggles it manually); MiniPlayer reads it to decide whether the bottom
 * bar shows its own volume control (collapsed rail hides the one in the
 * nav, so the mini player carries it instead).
 */
export const sidebarCollapsed = ref(false);

/**
 * Trailing slot at the right end of the collapsed rail (registered by
 * SidebarRail while collapsed). Views teleport their own controls there —
 * e.g. the library and search views move their search field into the
 * collapsed top bar, HomeView its shuffle-all / play-random-mix buttons.
 */
export const collapsedNavTrailing: Ref<HTMLElement | null> = ref(null);
