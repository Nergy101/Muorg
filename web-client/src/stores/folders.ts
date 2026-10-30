import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api } from "../api/client";
import { showToast } from "../composables/useToast";
import { useLibraryStore } from "./library";
import type { BrowseResponse, CatalogTrack } from "@shared/api";

/** How often to poll `/api/health` while a rescan is in flight. */
const SCAN_POLL_MS = 1000;
/** Safety cap so a stuck server never leaves the UI polling forever. */
const SCAN_POLL_TIMEOUT_MS = 5 * 60 * 1000;

/**
 * Counts tracks per configured root by path prefix — the server does not
 * report per-root counts. Roots are matched longest-first, so a root nested
 * inside another root is not double-counted. Exported for unit testing.
 */
export function countTracksByRoot(
  tracks: Pick<CatalogTrack, "path">[],
  roots: string[],
): Map<string, number> {
  const sortedRoots = [...roots].sort((a, b) => b.length - a.length);
  const counts = new Map(roots.map((r) => [r, 0]));
  for (const t of tracks) {
    const match = sortedRoots.find(
      (r) => t.path === r || t.path.startsWith(r.endsWith("/") ? r : `${r}/`),
    );
    if (match !== undefined) counts.set(match, (counts.get(match) ?? 0) + 1);
  }
  return counts;
}

/**
 * Library root folders: listing, rescanning, adding (via the server-side
 * folder picker, `FolderPickerSheet`) and removing — plus the maintenance
 * purge action. Mounted from `LibraryFoldersSection` / `MaintenanceSection`
 * in Settings.
 */
export const useFoldersStore = defineStore("folders", () => {
  const roots = ref<string[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  /** Root path currently rescanning/adding, `"*"` for "rescan all", or null. */
  const scanningRoot = ref<string | null>(null);
  const removingRoot = ref<string | null>(null);
  const purging = ref(false);

  async function loadRoots(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      roots.value = await api.getRoots();
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
    } finally {
      loading.value = false;
    }
  }

  /** Track count per configured root, from the already-loaded catalog. */
  const rootTrackCounts = computed<Map<string, number>>(() =>
    countTracksByRoot(useLibraryStore().tracks, roots.value),
  );

  /** Poll `/api/health` until the triggered scan finishes (or the cap trips). */
  async function waitForScanToFinish(): Promise<void> {
    const deadline = Date.now() + SCAN_POLL_TIMEOUT_MS;
    while (Date.now() < deadline) {
      let status: string;
      try {
        status = await api.health();
      } catch {
        return;
      }
      if (status !== "Scanning") return;
      // ES2020 target predates Promise.withResolvers (ES2024) — see playlists.ts.
      await new Promise((resolve) => setTimeout(resolve, SCAN_POLL_MS));
    }
  }

  async function rescanAll(): Promise<void> {
    scanningRoot.value = "*";
    error.value = null;
    try {
      await api.rescan();
      await waitForScanToFinish();
      await useLibraryStore().loadLibrary();
      showToast("Library rescanned");
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      showToast(`Rescan failed: ${error.value}`);
    } finally {
      scanningRoot.value = null;
    }
  }

  async function rescanOne(rootPath: string): Promise<void> {
    scanningRoot.value = rootPath;
    error.value = null;
    try {
      await api.rescan(rootPath);
      await waitForScanToFinish();
      await useLibraryStore().loadLibrary();
      showToast("Folder rescanned");
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      showToast(`Rescan failed: ${error.value}`);
    } finally {
      scanningRoot.value = null;
    }
  }

  /**
   * Add a folder on the server's disk. `POST /api/admin/rescan` upserts the
   * root (adds it if new, resurrects it if soft-deleted) and scans it in the
   * same call, so this is the one request needed.
   */
  async function addFolder(path: string): Promise<void> {
    scanningRoot.value = path;
    error.value = null;
    try {
      await api.rescan(path);
      await waitForScanToFinish();
      await loadRoots();
      await useLibraryStore().loadLibrary();
      showToast("Folder added");
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      showToast(`Could not add folder: ${error.value}`);
      throw e;
    } finally {
      scanningRoot.value = null;
    }
  }

  /** Drops the root and soft-deletes its tracks (grace period before purge). */
  async function removeFolder(rootPath: string): Promise<void> {
    removingRoot.value = rootPath;
    error.value = null;
    try {
      await api.removeFolder(rootPath);
      await loadRoots();
      await useLibraryStore().loadLibrary();
      showToast("Folder removed");
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      showToast(`Could not remove folder: ${error.value}`);
      throw e;
    } finally {
      removingRoot.value = null;
    }
  }

  /** Permanently drops soft-deleted catalog rows, bypassing the grace period. */
  async function purgeDeleted(): Promise<void> {
    purging.value = true;
    error.value = null;
    try {
      await api.purgeDeleted();
      showToast("Removed tracks purged");
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      showToast(`Purge failed: ${error.value}`);
      throw e;
    } finally {
      purging.value = false;
    }
  }

  /** Server-side directory listing for the "Add folder" picker. */
  function browse(path?: string): Promise<BrowseResponse> {
    return api.browse(path);
  }

  function reset(): void {
    roots.value = [];
    loading.value = false;
    error.value = null;
    scanningRoot.value = null;
    removingRoot.value = null;
    purging.value = false;
  }

  return {
    roots,
    loading,
    error,
    scanningRoot,
    removingRoot,
    purging,
    loadRoots,
    rootTrackCounts,
    rescanAll,
    rescanOne,
    addFolder,
    removeFolder,
    purgeDeleted,
    browse,
    reset,
  };
});
