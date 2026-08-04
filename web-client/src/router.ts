import { createRouter, createWebHashHistory } from "vue-router";
import { isConnected } from "./api/client";
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

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/library" },
    { path: "/connect", name: "connect", component: ConnectView },
    { path: "/library", name: "library", component: LibraryView },
    { path: "/album/:albumKey", name: "album", component: AlbumDetailView, props: true },
    { path: "/playlists", name: "playlists", component: PlaylistsView },
    { path: "/playlist/:id", name: "playlist", component: PlaylistDetailView, props: true },
    { path: "/queue", name: "queue", component: QueueView },
    { path: "/player", name: "player", component: PlayerView },
    { path: "/settings", name: "settings", component: SettingsView },
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

export default router;
