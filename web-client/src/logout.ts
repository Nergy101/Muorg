import router from "./router";
import { disconnect } from "./api/client";
import { useLibraryStore } from "./stores/library";
import { usePlayerStore } from "./stores/player";
import { usePlaylistStore } from "./stores/playlists";
import { useOfflineStore } from "./stores/offline";
import { useFoldersStore } from "./stores/folders";
import { useSelectionStore } from "./stores/selection";
import { clearUndo } from "./composables/useUndo";

/**
 * Full logout: drop the stored server credentials, reset every store that
 * holds server state, and land on the connect screen so the user can log in
 * again. Used by the Settings "Log out" button and by the transport's
 * onUnauthorized hook when the server rejects our API key (401).
 */
export async function logout(): Promise<void> {
  disconnect();
  useSelectionStore().clear();
  clearUndo();
  // Player first so audio stops before the library revokes its cover URLs.
  usePlayerStore().reset();
  useLibraryStore().reset();
  usePlaylistStore().reset();
  useFoldersStore().reset();
  // The offline cache is derived from this server's tracks — drop it so a
  // different server does not inherit (or pay quota for) stale files.
  await useOfflineStore().clearAll();
  await router.replace({ name: "connect" });
}
