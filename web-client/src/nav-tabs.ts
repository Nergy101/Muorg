/**
 * The bottom-nav tabs, in order. Shared because App.vue pages between them on a
 * horizontal swipe and BottomNav renders them — two copies would drift.
 *
 * Queue is deliberately absent: it is reached from the mini player and the
 * player screen, not as a top-level destination.
 */
export const NAV_TABS = [
  { name: "library", label: "Library", icon: "home" },
  { name: "playlists", label: "Playlists", icon: "list" },
  { name: "settings", label: "Settings", icon: "settings" },
] as const;

export type NavTabName = (typeof NAV_TABS)[number]["name"];

/**
 * Index of the tab a route belongs to, or -1 when the route is not under one
 * (the queue screen). Detail screens keep their parent tab lit.
 */
export function tabIndexForRoute(routeName: string): number {
  if (routeName === "album") return 0;
  if (routeName === "playlist") return 1;
  return NAV_TABS.findIndex((t) => t.name === routeName);
}
