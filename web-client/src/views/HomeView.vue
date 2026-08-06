<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center justify-between px-4">
      <div class="flex min-w-0 items-center gap-2">
        <MageIcon name="home-fill" class="h-5 w-5 text-primary" />
        <span class="truncate text-title-lg text-on-surface">Home</span>
      </div>
    </div>

    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto">
      <section v-for="shelf in shelfViews" :key="shelf.key" class="content-col px-4 pt-4">
        <h2 class="pb-2 text-label-lg font-semibold text-on-surface">{{ shelf.label }}</h2>

        <div v-if="shelf.loading" class="flex justify-center py-6">
          <MageIcon name="refresh" class="h-6 w-6 animate-spin text-on-surface-variant" />
        </div>

        <div v-else-if="shelf.error" class="py-4 text-body-sm text-error">
          Couldn't load {{ shelf.label.toLowerCase() }}.
        </div>

        <div v-else-if="shelf.items.length === 0" class="py-4 text-body-sm text-on-surface-variant">
          Nothing here yet.
        </div>

        <!-- -mx-4 cancels the section padding so the row can scroll edge to edge -->
        <div v-else class="-mx-4 flex gap-3 overflow-x-auto px-4 pb-2">
          <div
            v-for="item in shelf.items"
            :key="item.key"
            class="w-36 shrink-0"
          >
            <AlbumCard :item="item" mode="grid" @open="openAlbum(item)" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import { useLibraryStore } from "../stores/library";
import { getRecentlyAdded, getRecentPlayHistory, getTopPlayHistory } from "../api/catalog";
import { useScrollMemory } from "../composables/useScrollMemory";
import type { AlbumGridItem, CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

interface Shelf {
  key: string;
  label: string;
  load: () => Promise<CatalogTrack[]>;
  tracks: CatalogTrack[];
  loading: boolean;
  error: boolean;
}

const shelves = reactive<Shelf[]>([
  {
    key: "recently-added",
    label: "Recently Added",
    load: () => getRecentlyAdded(20),
    tracks: [],
    loading: false,
    error: false,
  },
  {
    key: "recently-played",
    label: "Recently Played",
    load: () => getRecentPlayHistory(20),
    tracks: [],
    loading: false,
    error: false,
  },
  {
    key: "most-played",
    label: "Most Played",
    load: () => getTopPlayHistory(20, 30),
    tracks: [],
    loading: false,
    error: false,
  },
]);

/** Groups a shelf's tracks into albums, in the endpoint's order (recency /
 *  play count), so the shelf reads like a feed rather than an A–Z list. Keys
 *  resolve through the full-catalog grouping so album detail opens the same
 *  album the library grid would show. */
function buildShelfItems(source: CatalogTrack[]): AlbumGridItem[] {
  const map = new Map<string, AlbumGridItem>();
  for (const t of source) {
    const key = lib.keyForTrack(t);
    let item = map.get(key);
    if (!item) {
      const seed = lib.albumByKey(key);
      item = seed
        ? { ...seed, trackCount: 0, totalDurationSecs: 0, coverTrackId: null, hasCover: false, trackIds: [] }
        : {
            key,
            album: t.album ?? "Unknown Album",
            albumArtist: t.album_artist ?? t.artist ?? "Unknown Artist",
            year: null,
            trackCount: 0,
            totalDurationSecs: 0,
            coverTrackId: null,
            hasCover: false,
            trackIds: [],
          };
      map.set(key, item);
    }
    item.trackCount++;
    item.totalDurationSecs += t.duration_secs ?? 0;
    item.trackIds.push(t.id);
    if (t.year && (item.year === null || t.year < item.year)) item.year = t.year;
    if (t.has_cover && !item.hasCover) {
      item.hasCover = true;
      item.coverTrackId = t.id;
    }
  }
  return [...map.values()];
}

/** What the template renders: the shelves plus their grouped items, recomputed
 *  whenever tracks arrive (or the library's grouping settles, so a shelf
 *  rendered before the catalog finished loading still resolves stable keys). */
const shelfViews = computed(() =>
  shelves.map((s) => ({
    key: s.key,
    label: s.label,
    loading: s.loading,
    error: s.error,
    items: buildShelfItems(s.tracks),
  })),
);

async function loadShelf(shelf: Shelf, background = false): Promise<void> {
  if (shelf.loading) return;
  // A background refresh (re-entering the tab) keeps the current rows visible.
  if (!background) shelf.loading = true;
  try {
    shelf.tracks = await shelf.load();
    shelf.error = false;
  } catch {
    shelf.error = true;
  } finally {
    shelf.loading = false;
  }
}

onMounted(() => {
  for (const shelf of shelves) void loadShelf(shelf);
});

// KeepAlive keeps this view mounted: re-fetch silently when the tab is
// re-entered so "Recently Played" reflects what happened since.
onActivated(() => {
  for (const shelf of shelves) void loadShelf(shelf, true);
});

function openAlbum(item: AlbumGridItem): void {
  void router.push({ name: "album", params: { albumKey: item.key } });
}
</script>
