<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <!-- Brand row: the Muorg mark and name on the left (as in the sidebar),
           "Shuffle all" on the right (mirrors the Android app's HomeHeader).
           On desktop the navbar already shows the logo, so the mark is hidden. -->
      <div class="content-col flex items-center gap-2 px-4 pb-2 pt-4">
        <img :src="logoUrl" alt="Muorg" class="h-8 w-8 shrink-0 lg:hidden" />
        <span class="text-title-md font-bold tracking-tight text-on-surface lg:hidden">Muorg</span>
        <div class="flex-1" />
        <button
          type="button"
          class="glass flex h-9 items-center gap-1.5 rounded-full px-3 text-label-lg transition-colors"
          :class="player.shuffleAllActive ? 'text-primary' : 'text-on-surface'"
          aria-label="Shuffle all"
          :aria-pressed="player.shuffleAllActive"
          @click="shuffleAll"
        >
          <MageIcon name="exchange" class="h-4 w-4" />
          <span>Shuffle all</span>
        </button>
      </div>

      <EmptyState
        v-if="libraryEmpty"
        class="content-col"
        title="Your library is empty"
        body="Add music to your server and rescan to see it here."
      />
      <template v-else>
      <!-- Recommended: random albums from the catalog, one full grid row on desktop (refresh re-rolls). -->
      <section v-if="shelfViews[0]" class="px-4 pt-6">
        <!-- Bottom spacing belongs to the row, not the heading: with `pb-2` on
             the <h2> the flex row centres the button against the padded box and
             it lands a few px below the heading's own centre line. -->
        <div class="flex items-center justify-between gap-3 pb-3">
          <h2 class="flex min-w-0 items-center gap-1.5 text-title-md font-semibold text-on-surface">
            <MageIcon :name="shelfViews[0].icon" class="h-5 w-5 shrink-0 text-primary" />
            {{ shelfViews[0].label }}
          </h2>
          <button
            type="button"
            class="glass flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors lg:hover:bg-surface-container lg:hover:text-on-surface"
            aria-label="New recommendations"
            @click="refreshRecommended"
          >
            <MageIcon
              name="refresh"
              class="h-4 w-4"
              :class="recommendedRefreshing ? 'animate-spin' : ''"
            />
          </button>
        </div>

        <!-- Recommended resolves synchronously from the catalog, so its own
             loading flag never paints — also show skeletons while the catalog
             itself is streaming in (same gate the mixes use). -->
        <ShelfSkeleton v-if="shelfViews[0].loading || lib.loading || lib.loadingMore" :count="SHELF_CAP" :cols="gridCols" />

        <div v-else-if="shelfViews[0].error" class="py-4 text-body-sm text-error">
          Couldn't load {{ shelfViews[0].label.toLowerCase() }}.
        </div>

        <EmptyState v-else-if="shelfViews[0].items.length === 0" compact />

        <!-- Same library-style grid, full-width cards, no horizontal scroll. -->
        <div
          v-else
          class="grid gap-3 pb-4"
          :style="{ gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))` }"
        >
          <AlbumCard
            v-for="item in shelfViews[0].items"
            :key="item.key"
            :item="item"
            mode="grid"
            class="w-full"
            @open="openAlbum(item)"
            @actions="albumActions = item"
          />
        </div>
      </section>

      <!-- Mixes: session-stable random ~20-track playlists (two full grid rows on desktop, 8 on
           mobile), never written to the server. Opening one shows its tracks; saving goes
           through the New-playlist flow. -->
      <section class="border-t border-outline/20 px-4 pt-6">
        <div class="flex items-center justify-between gap-3 pb-3">
          <h2 class="flex min-w-0 items-center gap-1.5 text-title-md font-semibold text-on-surface">
            <MageIcon name="color-swatch" class="h-5 w-5 shrink-0 text-primary" />
            Mixes
          </h2>
          <button
            type="button"
            class="glass flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors lg:hover:bg-surface-container lg:hover:text-on-surface"
            aria-label="New mixes"
            @click="refreshMixes"
          >
            <MageIcon
              name="refresh"
              class="h-4 w-4"
              :class="mixesRefreshing ? 'animate-spin' : ''"
            />
          </button>
        </div>

        <ShelfSkeleton
          v-if="mixes.length === 0 && (lib.loading || lib.loadingMore)"
          :count="MIXES_COUNT"
          :cols="gridCols"
        />

        <!-- Preloading the cover collages for all mixes; show skeletons until
             every one is done so the grid appears as a complete set. Note the
             explicit `.value`: mixCoverReady is a plain object (not a store
             proxy), so its ComputedRefs do NOT auto-unwrap in templates. -->
        <ShelfSkeleton
          v-else-if="!mixCoverReady.allReady.value"
          :count="MIXES_COUNT"
          :cols="gridCols"
        />

        <EmptyState v-else-if="mixes.length === 0" compact />

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

      <!-- Remaining shelves: Most Played, Recently Played -->
      <section
        v-for="shelf in shelfViews.slice(1)"
        :key="shelf.key"
        class="border-t border-outline/20 px-4 pt-6"
      >
        <h2 class="flex items-center gap-1.5 pb-3 text-title-md font-semibold text-on-surface">
          <MageIcon :name="shelf.icon" class="h-5 w-5 text-primary" />
          {{ shelf.label }}
        </h2>

        <ShelfSkeleton v-if="shelf.loading" :count="SHELF_CAP" :cols="gridCols" />

        <div v-else-if="shelf.error" class="py-4 text-body-sm text-error">
          Couldn't load {{ shelf.label.toLowerCase() }}.
        </div>

        <EmptyState v-else-if="shelf.items.length === 0" compact />

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
            @actions="albumActions = item"
          />
        </div>
      </section>
      </template>
    </div>

    <!-- Long-press / right-click on any album card on this screen: the same
         album context menu the Library shows (play, playlist, download…). -->
    <AlbumActionsHost :item="albumActions" @close="albumActions = null" />
  </div>
</template>

<script setup lang="ts">
import EmptyState from "../components/EmptyState.vue";
import { computed, onActivated, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import AlbumActionsHost from "../components/AlbumActionsHost.vue";
import MixCard from "../components/MixCard.vue";
import ShelfSkeleton from "../components/ShelfSkeleton.vue";
import logoUrl from "../assets/muorg-logo.svg";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { getRecentPlayHistory, getTopPlayHistory } from "../api/catalog";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useGridColumns } from "../composables/useGridColumns";
import { useIsDesktop } from "../composables/useIsDesktop";
import { useMixes } from "@shared/composables/useMixes";
import { useMixCoverReady } from "../composables/useMixCoverReady";
import type { AlbumGridItem, CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
/** Nothing to browse yet: the catalog finished loading and holds no tracks. */
const libraryEmpty = computed(() => !lib.loading && !lib.loadingMore && !lib.error && lib.tracks.length === 0);
const player = usePlayerStore();

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

const isDesktop = useIsDesktop();

/** How many album cards a home shelf shows: one full grid row on desktop, so
 *  the row exactly fills the viewport width (7 on a full-HD monitor, 5 on a
 *  laptop), and 4 (two rows of 2) on mobile. */
const SHELF_CAP = computed(() => (isDesktop.value ? gridCols.value : 4));

/** Mixes fill two full grid rows on desktop, 8 cards on mobile. */
const MIXES_COUNT = computed(() => (isDesktop.value ? gridCols.value * 2 : 8));

// "Recommended" is just a few randomly chosen albums from the catalog, picked
// once per session so the row stays stable while you browse (a fresh load
// re-rolls).
let recommendedTracksCache: CatalogTrack[] | null = null;
function randomRecommendedTracks(): CatalogTrack[] {
  if (recommendedTracksCache) return recommendedTracksCache;
  // Group the FULL (unfiltered) catalog into albums and pick 4 that have
  // artwork, so the row renders regardless of the Library's search/genre
  // filters. Request their covers up front so the cards appear whole.
  const byKey = new Map<string, { coverId: number | null; trackIds: number[] }>();
  for (const t of lib.tracks) {
    const key = lib.keyForTrack(t);
    let g = byKey.get(key);
    if (!g) {
      g = { coverId: null, trackIds: [] };
      byKey.set(key, g);
    }
    g.trackIds.push(t.id);
    if (t.has_cover && g.coverId === null) g.coverId = t.id;
  }
  const withCover = [...byKey.entries()].filter(([, g]) => g.coverId !== null);
  if (withCover.length === 0) return [];
  const picked = [...withCover];
  const n = Math.min(SHELF_CAP.value, picked.length);
  for (let i = 0; i < n; i++) {
    const j = i + Math.floor(Math.random() * (picked.length - i));
    [picked[i], picked[j]] = [picked[j], picked[i]];
  }
  const chosen = picked.slice(0, SHELF_CAP.value);
  const byId = new Map(lib.tracks.map((t) => [t.id, t]));
  const tracks: CatalogTrack[] = [];
  for (const [, g] of chosen) {
    if (g.coverId !== null && !lib.coverCache.has(g.coverId)) {
      lib.requestCover(g.coverId);
    }
    for (const id of g.trackIds) {
      const t = byId.get(id);
      if (t) tracks.push(t);
    }
  }
  recommendedTracksCache = tracks;
  return tracks;
}

const shelves = reactive<Shelf[]>([
  {
    key: "recommended",
    label: "Recommended",
    icon: "heart",
    load: () => Promise.resolve(randomRecommendedTracks()),
    tracks: [],
    loading: false,
    error: false,
  },
  {
    key: "most-played",
    label: "Most Played",
    icon: "chart-up",
    load: () => getTopPlayHistory(SHELF_CAP.value, 30),
    tracks: [],
    loading: false,
    error: false,
  },
  {
    key: "recently-played",
    label: "Recently Played",
    icon: "clock",
    load: () => getRecentPlayHistory(SHELF_CAP.value),
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
    items: buildShelfItems(s.tracks).slice(0, SHELF_CAP.value),
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

// --- Album context menu ----------------------------------------------------
// Long-press / right-click ("more") on an album card. The menu itself lives
// in AlbumActionsHost, shared with every other screen that shows album cards.

const albumActions = ref<AlbumGridItem | null>(null);

const { mixes, refresh } = useMixes({
  tracks: () => lib.tracks,
  ready: () => !lib.loading && !lib.loadingMore,
  // Two full grid rows on desktop viewports, 8 on mobile.
  count: () => MIXES_COUNT.value,
});
const mixCoverReady = useMixCoverReady(() => mixes.value);
// Live cover-loading state, for probing from devtools while working on the
// mix grid. Dev-only: this is a handle on store internals and there is no
// reason to hand one to every visitor.
if (import.meta.env.DEV) {
  (window as unknown as { __muorg: Record<string, unknown> }).__muorg = {
    get allReady() { return mixCoverReady.allReady.value; },
    get mixCount() { return mixes.value.length; },
    get cacheSize() { return lib.coverCache.size; },
    get pendingSize() { return lib.coverPending.size; },
    get failedSize() { return lib.coverFailed.size; },
  };
}

const mixesRefreshing = ref(false);
function refreshMixes(): void {
  mixesRefreshing.value = true;
  refresh();
  window.setTimeout(() => {
    mixesRefreshing.value = false;
  }, 700);
}

const recommendedRefreshing = ref(false);
function refreshRecommended(): void {
  recommendedTracksCache = null;
  const rec = shelves.find((s) => s.key === "recommended");
  if (!rec) return;
  recommendedRefreshing.value = true;
  void loadShelf(rec).finally(() => {
    recommendedRefreshing.value = false;
  });
}

// The Recommended row samples the catalog on first mount, but the catalog
// streams in page-by-page. Reload it whenever the catalog grows while the row
// is still empty, so it populates as soon as albums are available.
watch(
  () => lib.tracks.length,
  () => {
    const rec = shelves.find((s) => s.key === "recommended");
    if (rec && rec.tracks.length === 0) {
      recommendedTracksCache = null;
      void loadShelf(rec);
    }
  },
);

// The shelf cap follows the viewport (one full grid row on desktop), so a
// window resize re-fetches each shelf with the new amount. The Recommended
// cache is re-rolled because it was sampled with the old cap.
watch(SHELF_CAP, () => {
  recommendedTracksCache = null;
  for (const shelf of shelves) void loadShelf(shelf, true);
});

function openMix(id: number): void {
  void router.push({ name: "mix", params: { id: String(id) } });
}

/** The header's "Shuffle all" (mirrors the Android app's HomeHeader): seeds
 *  the queue with random songs from the catalog. The Library's own shuffle
 *  button passes the filtered catalog; the home header always passes the
 *  whole thing. */
function shuffleAll(): void {
  player.startShuffleAll(lib.tracks);
}
</script>
