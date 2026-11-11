<template>
  <!-- Empty query: one combined "Recent" section — recent artists as
       large cards, recently selected tracks as rows or covers (per the
       layout), and past queries as chips. -->
  <template v-if="!query.trim()">
    <div
      v-if="settings.searchHistory.length > 0 || recentResultTracks.length > 0"
      class="search-col px-4 pb-2 pt-1"
    >
      <div class="flex items-center justify-between py-1">
        <div class="flex items-center gap-1.5 text-label-lg text-on-surface-variant">
          <MageIcon name="clock" class="h-4 w-4" />
          <span>Recent</span>
        </div>
        <button
          type="button"
          class="text-label-lg text-primary"
          @click="clearAllRecent"
        >
          Clear all
        </button>
      </div>
      <!-- Artists picked from the results get a proper large artist card,
           same as the artist matches below; plain queries stay chips. -->
      <div v-if="recentArtistHits.length > 0" class="grid grid-cols-2 gap-3 pb-2 sm:grid-cols-3 lg:grid-cols-4">
        <ArtistCard
          v-for="hit in recentArtistHits"
          :key="hit.name"
          :name="hit.name"
          :cover-urls="coverUrlsFor(hit)"
          :track-count="hit.trackCount"
          @open="openArtist"
        />
      </div>
      <!-- Recently selected tracks follow the same layout as live results:
           album covers in grid mode, proper track rows in list mode. -->
      <div v-if="recentResultTracks.length > 0" class="pb-2">
        <SearchResultCardGrid
          v-if="layout === 'grid'"
          :cards="recentResultCards"
          @open="openTrackAlbum"
        />
        <template v-else>
          <TrackListRow
            v-for="track in recentResultTracks"
            :key="track.id"
            :track="track"
            :is-playing="player.currentTrack?.id === track.id"
            :is-loading="player.pendingTrackId === track.id"
            :selectable="selection.selecting"
            :selected="selection.has(track.id)"
            @play="playTrack(track, recentResultTracks)"
            @select="(mods) => onSelect(track, mods, recentResultTracks)"
            @actions="sheetTrack = track"
          />
        </template>
      </div>
      <div v-if="recentQueryHistory.length > 0" class="flex flex-wrap gap-2">
        <button
          v-for="h in recentQueryHistory"
          :key="h.value"
          type="button"
          class="glass-field rounded-full px-3 py-1.5 text-label-lg text-on-surface"
          @click="selectHistoryChip(h.value)"
        >
          {{ h.value }}
        </button>
      </div>
    </div>

    <EmptyState
      v-if="settings.searchHistory.length === 0 && recentResultTracks.length === 0 && !lib.loading"
      class="search-col"
      title="No searches yet"
      body="Type above to search your library."
    />
  </template>

  <!-- Live results: artist matches first, then track rows or album cards
       depending on the layout. -->
  <template v-else>
    <div v-if="lib.loading" class="flex items-center justify-center py-12">
      <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
    </div>
    <EmptyState
      v-else-if="results.length === 0"
      class="search-col"
      :title="`No results for &quot;${query.trim()}&quot;`"
      body="Try another spelling or fewer words."
    />
    <!-- Artist matches first: a 4-block cover collage per artist with the
         name underneath, above the normal track/album results. -->
    <div v-if="artistHits.length > 0" class="search-col px-4 pb-3 pt-1">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        <ArtistCard
          v-for="hit in artistHits"
          :key="hit.name"
          :name="hit.name"
          :cover-urls="coverUrlsFor(hit)"
          :track-count="hit.trackCount"
          @open="openArtist"
        />
      </div>
    </div>

    <!-- Album-cover layout: one album card PER matching track, in the
         centered measure at 4 columns (normal cover size), paged 20 at
         a time so a big result set doesn't load every cover at once. -->
    <div v-if="layout === 'grid'" class="search-col px-4 pb-4 pt-1">
      <SearchResultCardGrid :cards="visibleResultCards" @open="openTrackAlbum" />
      <button
        v-if="hasMoreGrid"
        type="button"
        class="mt-3 flex h-11 w-full items-center justify-center gap-2 rounded-full glass-field text-label-lg text-primary"
        @click="showMoreGrid"
      >
        <MageIcon name="chevron-down" class="h-4 w-4" />
        <span>Show more ({{ resultCards.length - visibleGridCount }} remaining)</span>
      </button>
    </div>
    <div v-else class="search-col pb-4">
      <TrackListRow
        v-for="track in visibleResults"
        :key="track.id"
        :track="track"
        :is-playing="player.currentTrack?.id === track.id"
        :is-loading="player.pendingTrackId === track.id"
        :subtitle="`${track.artist ?? track.album_artist ?? '—'} • ${track.album ?? '—'}`"
        :selectable="selection.selecting"
        :selected="selection.has(track.id)"
        @play="playTrack(track, results)"
        @select="(mods) => onSelect(track, mods, results)"
        @actions="sheetTrack = track"
      />
      <button
        v-if="hasMore"
        type="button"
        class="mx-4 mt-1 flex h-11 w-[calc(100%-2rem)] items-center justify-center gap-2 rounded-full glass-field text-label-lg text-primary"
        @click="loadMore"
      >
        <MageIcon name="chevron-down" class="h-4 w-4" />
        <span>Load more ({{ results.length - visibleCount }} remaining)</span>
      </button>
    </div>
  </template>

  <TrackActionsSheet
    :open="sheetTrack !== null"
    :track="sheetTrack"
    @close="sheetTrack = null"
    @view-artist="onViewArtist"
    @view-album="onViewAlbum"
  />
</template>

<script setup lang="ts">
import EmptyState from "./EmptyState.vue";
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import ArtistCard from "./ArtistCard.vue";
import SearchResultCardGrid from "./SearchResultCardGrid.vue";
import TrackListRow from "./TrackListRow.vue";
import TrackActionsSheet from "./TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useSelectionStore } from "../stores/selection";
import type { CatalogTrack, SearchHistoryEntry, SearchResultCard } from "../types";

const props = defineProps<{
  /** Raw query text; the panel debounces it internally. */
  query: string;
  /** Result layout: flat track list or album-cover grid. */
  layout: "list" | "grid";
}>();
const emit = defineEmits<{
  "update:query": [value: string];
  "update:layout": [value: "list" | "grid"];
  /** A history chip was clicked — the host should focus its search input. */
  requestFocus: [];
}>();

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const selection = useSelectionStore();

/** Debounced copy of the query — the results computed reads this, so a full
 *  catalog scan runs at most once per pause in typing, not per keystroke. */
const debouncedQuery = ref(props.query);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;
watch(
  () => props.query,
  (v) => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debouncedQuery.value = v;
    }, 250);
  },
);

function normalize(s: string | null | undefined): string {
  return (s ?? "").trim().toLowerCase();
}

/**
 * Weighted match: title hits outrank artist hits, which outrank album hits,
 * so a keyword present in both a track and an album title puts the track on
 * top instead of the order looking random. Exact and prefix matches score
 * higher than mid-string contains; ties break on title for stability.
 */
function matchScore(t: CatalogTrack, q: string): number {
  const fieldScore = (text: string | null | undefined): number => {
    const v = normalize(text);
    if (!v) return 0;
    if (v === q) return 100;
    if (v.startsWith(q)) return 60;
    if (v.includes(q)) return 40;
    return 0;
  };
  return (
    fieldScore(t.title) +
    fieldScore(t.artist ?? t.album_artist) / 2 +
    fieldScore(t.album) / 4 +
    fieldScore(t.album_artist) / 8
  );
}

const results = computed(() => {
  const q = normalize(debouncedQuery.value);
  if (!q) return [];
  return lib.tracks
    .map((t) => ({ t, s: matchScore(t, q) }))
    .filter(({ s }) => s > 0)
    .sort((a, b) => b.s - a.s || normalize(a.t.title).localeCompare(normalize(b.t.title)))
    .map(({ t }) => t);
});

// --- Artist matches --------------------------------------------------------

interface ArtistHit {
  name: string;
  /** Up to 4 distinct album-cover track ids for the collage. */
  coverCandidateIds: number[];
  /** Total songs by the artist, shown on the card's scrim. */
  trackCount: number;
}

/** Artists whose name matches the query (exact > prefix > contains), capped
 *  at 4 so the section stays a single row. */
const artistHits = computed<ArtistHit[]>(() => {
  const q = normalize(debouncedQuery.value);
  if (!q) return [];
  const score = (name: string): number => {
    const v = normalize(name);
    if (!v) return 0;
    if (v === q) return 100;
    if (v.startsWith(q)) return 60;
    if (v.includes(q)) return 40;
    return 0;
  };
  const byArtist = new Map<string, { score: number; tracks: CatalogTrack[] }>();
  for (const t of lib.tracks) {
    const name = t.artist ?? t.album_artist;
    if (!name) continue;
    const s = score(name);
    if (s === 0) continue;
    const entry = byArtist.get(name) ?? { score: s, tracks: [] };
    entry.score = Math.max(entry.score, s);
    entry.tracks.push(t);
    byArtist.set(name, entry);
  }
  return [...byArtist.entries()]
    .sort((a, b) => b[1].score - a[1].score || a[0].localeCompare(b[0]))
    .slice(0, 4)
    .map(([name, entry]) => ({
      name,
      coverCandidateIds: artistCoverCandidateIds(name),
      trackCount: entry.tracks.length,
    }));
});

/** First 4 distinct albums with artwork for an artist, in catalog order —
 *  the collage tiles for both search artist matches and recent artists. */
function artistCoverCandidateIds(name: string): number[] {
  const seenAlbums = new Set<string>();
  const ids: number[] = [];
  for (const t of lib.tracks) {
    if ((t.artist ?? t.album_artist) !== name) continue;
    if (!t.has_cover) continue;
    const key = lib.keyForTrack(t);
    if (seenAlbums.has(key)) continue;
    seenAlbums.add(key);
    ids.push(t.id);
    if (ids.length === 4) break;
  }
  return ids;
}

// Warm the cover cache for the collage tiles (requestCover is idempotent).
watch(artistHits, (hits) => {
  for (const h of hits) for (const id of h.coverCandidateIds) lib.requestCover(id);
}, { immediate: true });

/** Loaded cover URLs for an artist's collage, in candidate order. */
function coverUrlsFor(hit: ArtistHit): (string | null)[] {
  return hit.coverCandidateIds.map((id) => lib.coverCache.get(id) ?? null);
}

// --- Recent searches -------------------------------------------------------
// Artist entries render as large artist cards (like the matches above);
// query entries stay chips.

const recentArtistHits = computed<ArtistHit[]>(() =>
  settings.searchHistory
    .filter((e): e is Extract<SearchHistoryEntry, { type: "artist" }> => e.type === "artist")
    .map((e) => ({
      name: e.value,
      coverCandidateIds: artistCoverCandidateIds(e.value),
      trackCount: artistTrackCount(e.value),
    })),
);

/** Total songs by an artist in the catalog. */
function artistTrackCount(name: string): number {
  let n = 0;
  for (const t of lib.tracks) if ((t.artist ?? t.album_artist) === name) n++;
  return n;
}
const recentQueryHistory = computed(() =>
  settings.searchHistory.filter((e) => e.type === "query"),
);

watch(recentArtistHits, (hits) => {
  for (const h of hits) for (const id of h.coverCandidateIds) lib.requestCover(id);
}, { immediate: true });

function openArtist(name: string): void {
  settings.addArtistSearch(name);
  void router.push({ name: "artist", params: { name } });
}

/** One album card PER track — not grouped by album — so every song gets its
 *  own cover card, labelled with the track title and artist. Same item shape
 *  the library's album grid renders. Shared by live and recent results. */
function cardsForTracks(tracks: CatalogTrack[]): SearchResultCard[] {
  return tracks.map((t) => {
    const fileName = t.path.split("/").pop() ?? t.path;
    return {
      track: t,
      item: {
        key: `track-${t.id}`,
        album: t.title ?? fileName.replace(/\.[^.]+$/, ""),
        albumArtist: t.artist ?? t.album_artist ?? "—",
        year: t.year ?? null,
        trackCount: 1,
        totalDurationSecs: t.duration_secs ?? 0,
        coverTrackId: t.has_cover ? t.id : null,
        hasCover: t.has_cover,
        trackIds: [t.id],
      },
    };
  });
}

const resultCards = computed<SearchResultCard[]>(() => cardsForTracks(results.value));

function openTrackAlbum(track: CatalogTrack): void {
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(track) } });
}

// Only the first page of results is rendered; "Load more"/"Show more" reveals
// the next. The cover grid pages 20 at a time — rendering every card at once
// makes the browser fetch every cover in one go and the page lags.
const PAGE_SIZE = 10;
const GRID_PAGE_SIZE = 20;
const visibleCount = ref(PAGE_SIZE);
const visibleGridCount = ref(GRID_PAGE_SIZE);
watch(debouncedQuery, () => {
  visibleCount.value = PAGE_SIZE;
  visibleGridCount.value = GRID_PAGE_SIZE;
});
// Switching layout starts the new layout from its first page.
watch(() => props.layout, () => {
  visibleCount.value = PAGE_SIZE;
  visibleGridCount.value = GRID_PAGE_SIZE;
});
const visibleResults = computed(() => results.value.slice(0, visibleCount.value));
const hasMore = computed(() => results.value.length > visibleCount.value);
function loadMore(): void {
  visibleCount.value = Math.min(visibleCount.value + PAGE_SIZE, results.value.length);
}
const visibleResultCards = computed(() => resultCards.value.slice(0, visibleGridCount.value));
const hasMoreGrid = computed(() => resultCards.value.length > visibleGridCount.value);
function showMoreGrid(): void {
  visibleGridCount.value = Math.min(visibleGridCount.value + GRID_PAGE_SIZE, resultCards.value.length);
}

/** Recently selected search results, resolved against the catalog and kept
 *  in their recorded order; ids that no longer exist drop out. */
const recentResultTracks = computed(() => {
  const byId = new Map(lib.tracks.map((t) => [t.id, t]));
  return settings.recentSearchResultIds
    .map((id) => byId.get(id))
    .filter((t): t is CatalogTrack => t !== undefined);
});
/** Recent results in card form for the grid layout (capped at 20 entries,
 *  so no paging needed — render them all). */
const recentResultCards = computed<SearchResultCard[]>(() => cardsForTracks(recentResultTracks.value));

/** The track ids currently on screen, in displayed order — exposed so the
 *  host view can register them for range selection. */
const visibleIds = computed(() => {
  if (!debouncedQuery.value.trim()) return recentResultTracks.value.map((t) => t.id);
  return props.layout === "grid"
    ? visibleResultCards.value.map((c) => c.track.id)
    : visibleResults.value.map((t) => t.id);
});
defineExpose({ visibleIds });

function recordSearch(value: string): void {
  const trimmed = value.trim();
  if (trimmed.length > 0) settings.addSearch(trimmed);
}

function selectHistoryChip(entry: string): void {
  emit("update:query", entry);
  recordSearch(entry);
  emit("requestFocus");
}

/** The combined Recent section has a single "Clear all" covering both the
 *  search history (queries + artists) and the recently selected results. */
function clearAllRecent(): void {
  settings.clearSearchHistory();
  settings.clearSearchResults();
}

function playTrack(track: CatalogTrack, queue: CatalogTrack[]): void {
  settings.addSearchResult(track.id);
  recordSearch(props.query);
  void player.playTrack(track, queue);
}

function onSelect(
  track: CatalogTrack,
  mods: { shift: boolean; ctrl: boolean },
  ordered: CatalogTrack[],
): void {
  const consumed = selection.click(
    track.id,
    ordered.map((t) => t.id),
    mods,
  );
  if (!consumed) playTrack(track, ordered);
}

// --- Track actions sheet -------------------------------------------------

const sheetTrack = ref<CatalogTrack | null>(null);

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}
</script>
