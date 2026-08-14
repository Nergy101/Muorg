<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto pb-[calc(9rem+env(safe-area-inset-bottom,0px))]">
      <section
        v-for="(shelf, i) in shelfViews"
        :key="shelf.key"
        :class="i === 0 ? 'px-4 pt-6' : 'border-t border-outline/20 px-4 pt-6'"
      >
        <h2 class="flex items-center gap-1.5 pb-2 text-title-md font-semibold text-on-surface">
          <MageIcon :name="shelf.icon" class="h-5 w-5 text-primary" />
          {{ shelf.label }}
        </h2>

        <div v-if="shelf.loading" class="flex justify-center py-6">
          <MageIcon name="refresh" class="h-6 w-6 animate-spin text-on-surface-variant" />
        </div>

        <div v-else-if="shelf.error" class="py-4 text-body-sm text-error">
          Couldn't load {{ shelf.label.toLowerCase() }}.
        </div>

        <div v-else-if="shelf.items.length === 0" class="py-4 text-body-sm text-on-surface-variant">
          Nothing here yet.
        </div>

        <!-- Same library-style grid, full-width cards, no horizontal scroll. -->
        <div
          v-else
          class="grid gap-3 pb-4"
          :style="{ gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))` }"
        >
          <AlbumCard
            v-for="item in shelf.items"
            :key="item.key"
            :item="item"
            mode="grid"
            class="w-full"
            @open="openAlbum(item)"
          />
        </div>
      </section>

      <!-- Mixes: 8 session-stable random ~20-track playlists, never written
           to the server. Opening one shows its tracks; saving goes through
           the New-playlist flow. -->
      <section class="border-t border-outline/20 px-4 pt-6">
        <div class="flex items-center justify-between pb-2">
          <h2 class="flex items-center gap-1.5 pb-2 text-title-md font-semibold text-on-surface">
            <MageIcon name="color-swatch" class="h-5 w-5 text-primary" />
            Mixes
          </h2>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant transition-colors lg:hover:bg-surface-container lg:hover:text-on-surface"
            aria-label="New mixes"
            @click="refreshMixes"
          >
            <MageIcon
              name="refresh"
              class="h-5 w-5"
              :class="mixesRefreshing ? 'animate-spin' : ''"
            />
          </button>
        </div>

        <div
          v-if="mixes.length === 0 && (lib.loading || lib.loadingMore)"
          class="flex justify-center py-6"
        >
          <span class="dot-loader text-on-surface-variant"><span class="dot" /><span class="dot" /><span class="dot" /></span>
        </div>

        <!-- Preloading the cover collages for all 8 mixes; show nothing until
             every one is done so the grid appears as a complete set. Note the
             explicit `.value`: mixCoverReady is a plain object (not a store
             proxy), so its ComputedRefs do NOT auto-unwrap in templates. -->
        <div v-else-if="!mixCoverReady.allReady.value" class="flex justify-center py-6">
          <span class="dot-loader text-on-surface-variant"><span class="dot" /><span class="dot" /><span class="dot" /></span>
        </div>

        <div v-else-if="mixes.length === 0" class="py-4 text-body-sm text-on-surface-variant">
          Nothing here yet.
        </div>

        <!-- Matches the Library's album grid: same column count (2/3/auto-fill
             200px via useGridColumns) and full-width cards, no horizontal
             scroll. -->
        <div
          v-else
          class="grid gap-3 pb-4"
          :style="{ gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))` }"
        >
          <MixCard
            v-for="mix in mixes"
            :key="mix.id"
            :mix="mix"
            class="w-full"
            @open="openMix(mix.id)"
          />
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
import MixCard from "../components/MixCard.vue";
import { useLibraryStore } from "../stores/library";
import { getRecentPlayHistory, getTopPlayHistory } from "../api/catalog";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useGridColumns } from "../composables/useGridColumns";
import { useMixes } from "../composables/useMixes";
import { useMixCoverReady } from "../composables/useMixCoverReady";
import type { AlbumGridItem, CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
// Same column count as the Library album grid (2/3/auto-fill minmax(200px)).
const gridCols = useGridColumns(scroller);

interface Shelf {
  key: string;
  label: string;
  icon: string;
  load: () => Promise<CatalogTrack[]>;
  tracks: CatalogTrack[];
  loading: boolean;
  error: boolean;
}

/** How many album cards a home shelf shows at most (both layouts). */
const SHELF_CAP = 4;

const shelves = reactive<Shelf[]>([
  {
    key: "recently-played",
    label: "Recently Played",
    icon: "clock",
    load: () => getRecentPlayHistory(20),
    tracks: [],
    loading: false,
    error: false,
  },
  {
    key: "most-played",
    label: "Most Played",
    icon: "chart-up",
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
    icon: s.icon,
    loading: s.loading,
    error: s.error,
    items: buildShelfItems(s.tracks).slice(0, SHELF_CAP),
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

const { mixes, refresh } = useMixes();
const mixCoverReady = useMixCoverReady(() => mixes.value);
// DEBUG: expose live state for CDP probing
(window as any).__muorg = {
  get allReady() { return mixCoverReady.allReady.value; },
  get mixCount() { return mixes.value.length; },
  get cacheSize() { return lib.coverCache.size; },
  get pendingSize() { return lib.coverPending.size; },
  get failedSize() { return lib.coverFailed.size; },
};

const mixesRefreshing = ref(false);
function refreshMixes(): void {
  mixesRefreshing.value = true;
  refresh();
  window.setTimeout(() => {
    mixesRefreshing.value = false;
  }, 700);
}

function openMix(id: number): void {
  void router.push({ name: "mix", params: { id: String(id) } });
}
</script>
