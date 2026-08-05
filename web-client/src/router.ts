import { createRouter, createWebHashHistory } from "vue-router";
import { isConnected } from "./api/client";
import { navTransition, resolveNavTransition } from "./composables/useNavTransition";
import { useLibraryStore } from "./stores/library";
import { usePlaylistStore } from "./stores/playlists";

import ConnectView from "./views/ConnectView.vue";
import LibraryView from "./views/LibraryView.vue";
import AlbumDetailView from "./views/AlbumDetailView.vue";
import PlaylistsView from "./views/PlaylistsView.vue";
import PlaylistDetailView from "./views/PlaylistDetailView.vue";
import QueueView from "./views/QueueView.vue";
import PlayerView from "./views/PlayerView.vue";
import SettingsView from "./views/SettingsView.vue";

/**
 * Loads the catalog + playlists exactly once per connected session.
 * Owned by the router so cold boot, hash deep-links and the post-connect
 * redirect all funnel through the same place.
 */
export async function loadData(): Promise<void> {
  const lib = useLibraryStore();
  const playlistStore = usePlaylistStore();
  await Promise.all([lib.loadLibrary(), playlistStore.loadPlaylists()]);
}

/**
 * Position in the navigation stack. The transition between two routes is chosen
 * by comparing depths: deeper is a push, shallower a pop, equal a sibling move.
 * `modal` opts out and rises from the bottom instead.
 */
export type NavDepth = number | "modal";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/library" },
    { path: "/connect", name: "connect", component: ConnectView, meta: { depth: 0 } },
    { path: "/library", name: "library", component: LibraryView, meta: { depth: 0 } },
    {
      path: "/album/:albumKey",
      name: "album",
      component: AlbumDetailView,
      props: true,
      meta: { depth: 1 },
    },
    { path: "/playlists", name: "playlists", component: PlaylistsView, meta: { depth: 0 } },
    {
      path: "/playlist/:id",
      name: "playlist",
      component: PlaylistDetailView,
      props: true,
      meta: { depth: 1 },
    },
    { path: "/queue", name: "queue", component: QueueView, meta: { depth: 1 } },
    { path: "/player", name: "player", component: PlayerView, meta: { depth: "modal" } },
    { path: "/settings", name: "settings", component: SettingsView, meta: { depth: 0 } },
    { path: "/:pathMatch(.*)*", redirect: "/library" },
  ],
});

router.beforeEach((to) => {
  const connected = isConnected();
  if (!connected && to.name !== "connect") return { name: "connect" };
  if (connected && to.name === "connect") return { name: "library" };

  if (connected) {
    const lib = useLibraryStore();
    if (lib.tracks.length === 0 && !lib.loading) void loadData();
  }
  return true;
});

// Classify the move before the shell re-renders, so <Transition> gets the name
// that matches the direction of travel. afterEach only fires on a confirmed
// navigation, and the routed component swaps after it, so this is never stale.
router.afterEach((to, from) => {
  navTransition.value = resolveNavTransition(to, from);
});

export default router;
