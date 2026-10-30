/**
 * The bottom-nav tabs, in order. Shared because App.vue pages between them on a
 * horizontal swipe and BottomNav renders them — two copies would drift.
 *
 * Queue is deliberately absent: it is reached from the mini player and the
 * player screen, not as a top-level destination.
 */
export const NAV_TABS = [
  { name: "home", label: "Home", icon: "home-2", iconActive: "home-2-fill" },
  // The Mage set has no filled search glyph, so the active tab keeps the same
  // magnifier and is told apart by the pill + colour alone.
  { name: "search", label: "Search", icon: "search", iconActive: "search" },
  { name: "library", label: "Library", icon: "compact-disk", iconActive: "compact-disk-fill" },
  { name: "playlists", label: "Playlists", icon: "library-music", iconActive: "library-music-fill" },
  { name: "settings", label: "Settings", icon: "settings", iconActive: "settings-fill" },
] as const;

export type NavTabName = (typeof NAV_TABS)[number]["name"];

/**
 * Index of the tab a route belongs to, or -1 when the route is not under one
 * (the queue screen). Detail screens keep their parent tab lit.
 */
export function tabIndexForRoute(routeName: string): number {
  if (routeName === "album") return 2;
  if (routeName === "playlist") return 3;
  return NAV_TABS.findIndex((t) => t.name === routeName);
}
