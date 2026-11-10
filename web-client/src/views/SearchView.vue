<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <!-- Search bar pinned at the TOP of the screen; results scroll between it
         and the bottom island/keyboard. -->
    <div class="search-col shrink-0 px-4 pb-2 pt-3">
      <div
        class="glass-field flex h-11 items-center gap-2 rounded-full px-4 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
      >
        <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <input
          ref="inputEl"
          v-model="query"
          type="text"
          placeholder="Search for tracks"
          class="search-input flex-1 bg-transparent text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
          @keyup.enter="onSearchEnter"
        />
        <button
          v-if="query"
          type="button"
          class="shrink-0 text-on-surface-variant"
          aria-label="Clear search"
          @click="clearSearch"
        >
          <MageIcon name="multiply" class="h-4 w-4" />
        </button>
        <!-- Layout toggle: flat track list ↔ album cover grid. -->
        <button
          type="button"
          class="shrink-0 text-on-surface-variant"
          :aria-label="layout === 'grid' ? 'Show results as a list' : 'Show results as album covers'"
          :title="layout === 'grid' ? 'Show results as a list' : 'Show results as album covers'"
          @click="toggleLayout"
        >
          <MageIcon :name="layout === 'grid' ? 'arrowlist' : 'layout-grid'" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div ref="scroller" class="scrollbar-overlay min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <!-- Empty query: recent searches + recently selected results. -->
      <template v-if="!query.trim()">
        <div v-if="settings.searchHistory.length > 0" class="search-col px-4 pb-2 pt-1">
          <div class="flex items-center justify-between py-1">
            <div class="flex items-center gap-1.5 text-label-lg text-on-surface-variant">
              <MageIcon name="clock" class="h-4 w-4" />
              <span>Recent</span>
            </div>
            <button
              type="button"
              class="text-label-lg text-primary"
              @click="settings.clearSearchHistory()"
            >
              Clear all
            </button>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="h in settings.searchHistory"
              :key="h"
              type="button"
              class="glass-field rounded-full px-3 py-1.5 text-label-lg text-on-surface"
              @click="selectHistoryChip(h)"
            >
              {{ h }}
            </button>
          </div>
        </div>

        <div v-if="recentResultTracks.length > 0" class="search-col pb-2 pt-2">
          <div class="flex items-center justify-between px-4 py-1">
            <div class="flex items-center gap-1.5 text-label-lg text-on-surface-variant">
              <MageIcon name="search" class="h-4 w-4" />
              <span>Recent results</span>
            </div>
            <button
              type="button"
              class="text-label-lg text-primary"
              @click="settings.clearSearchResults()"
            >
              Clear all
            </button>
          </div>
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
        </div>

        <EmptyState
          v-if="settings.searchHistory.length === 0 && recentResultTracks.length === 0 && !lib.loading"
          class="search-col"
          title="No searches yet"
          body="Type above to search your library."
        />
      </template>

      <!-- Live results as plain track rows, same as the library's search. -->
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
            <button
              v-for="hit in artistHits"
              :key="hit.name"
              type="button"
              class="flex flex-col gap-1.5 text-left"
              :aria-label="`Browse artist ${hit.name}`"
              @click="openArtist(hit.name)"
            >
              <div class="relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10">
                <div v-if="coverUrlsFor(hit).length === 0" class="absolute inset-0 flex items-center justify-center">
                  <MageIcon name="user" class="h-10 w-10 text-on-surface-variant/50" />
                </div>
                <img
                  v-else-if="coverUrlsFor(hit).length === 1"
                  :src="coverUrlsFor(hit)[0] ?? undefined"
                  :alt="hit.name"
                  class="absolute inset-0 h-full w-full object-cover"
                />
                <!-- 2 or 3 covers: vertical split, each cover centered in its
                     own panel instead of a 2x2 grid with empty cells. -->
                <div v-else-if="coverUrlsFor(hit).length < 4" class="flex h-full w-full">
                  <div
                    v-for="(url, i) in coverUrlsFor(hit)"
                    :key="i"
                    class="relative h-full flex-1 bg-surface-variant/50"
                  >
                    <img
                      v-if="url"
                      :src="url"
                      :alt="hit.name"
                      class="absolute inset-0 h-full w-full object-cover"
                    />
                  </div>
                </div>
                <div v-else class="grid h-full w-full grid-cols-2 grid-rows-2">
                  <template v-for="(url, i) in coverUrlsFor(hit)" :key="i">
                    <img v-if="url" :src="url" :alt="hit.name" class="h-full w-full object-cover" />
                    <div v-else class="h-full w-full bg-surface-variant/50" />
                  </template>
                </div>
              </div>
              <span class="truncate px-0.5 text-label-lg font-semibold text-on-surface">{{ hit.name }}</span>
            </button>
          </div>
        </div>

        <!-- Album-cover layout: one album card PER matching track, full-bleed
             like the homepage grid so the cards get the same normal size. -->
        <div v-if="layout === 'grid'" class="px-4 pb-4 pt-1">
          <div
            class="grid gap-3"
            :style="{ gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))` }"
          >
            <template v-for="card in resultCards" :key="card.item.key">
              <AlbumCard
                v-if="card.item.hasCover"
                :item="card.item"
                mode="grid"
                @open="openTrackAlbum(card.track)"
              />
              <!-- Tracks without artwork still get a card, with a placeholder
                   sleeve instead of the cover. -->
              <button
                v-else
                type="button"
                class="relative aspect-square w-full select-none overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10 text-left"
                @click="openTrackAlbum(card.track)"
              >
                <div class="absolute inset-0 flex items-center justify-center">
                  <MageIcon name="music" class="h-10 w-10 text-on-surface-variant/60" />
                </div>
                <div
                  class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[44px]"
                  aria-hidden="true"
                />
                <div class="scrim-label absolute inset-x-0 bottom-0 flex h-[44px] items-center px-2.5">
                  <div class="min-w-0 flex-1">
                    <MarqueeText
                      :text="card.item.album"
                      class="text-label-lg font-semibold text-on-surface"
                    />
                    <div class="truncate text-label-sm text-on-surface-variant">
                      {{ card.item.albumArtist }}
                    </div>
                  </div>
                </div>
              </button>
            </template>
          </div>
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
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import EmptyState from "../components/EmptyState.vue";
import { computed, nextTick, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import MarqueeText from "../components/MarqueeText.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useSelectionStore } from "../stores/selection";
import { loadPref, savePref } from "../stores/prefs";
import { searchFocusSignal } from "../composables/useSearchFocus";
import { provideVisibleIds } from "../composables/useSelectableList";
import { useGridColumns } from "../composables/useGridColumns";
import type { AlbumGridItem, CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const selection = useSelectionStore();
const query = ref("");
/** Result layout: flat track list or album-cover grid, persisted. */
const layout = ref<"list" | "grid">(loadPref("muorg-web-search-layout", "list"));
watch(layout, (v) => savePref("muorg-web-search-layout", v));
function toggleLayout(): void {
  layout.value = layout.value === "grid" ? "list" : "grid";
}
const scroller = ref<HTMLElement | null>(null);
const gridCols = useGridColumns(scroller);
/** Debounced copy of `query` — the results computed reads this, so a full
 *  catalog scan runs at most once per pause in typing, not per keystroke. */
const debouncedQuery = ref("");
let debounceTimer: ReturnType<typeof setTimeout> | undefined;
const inputEl = ref<HTMLInputElement | null>(null);

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

watch(query, (v) => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debouncedQuery.value = v;
  }, 250);
});

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
    .map(([name, entry]) => {
      // First 4 distinct albums that have artwork, in catalog order.
      const seenAlbums = new Set<string>();
      const coverCandidateIds: number[] = [];
      for (const t of entry.tracks) {
        if (!t.has_cover) continue;
        const key = lib.keyForTrack(t);
        if (seenAlbums.has(key)) continue;
        seenAlbums.add(key);
        coverCandidateIds.push(t.id);
        if (coverCandidateIds.length === 4) break;
      }
      return { name, coverCandidateIds };
    });
});

// Warm the cover cache for the collage tiles (requestCover is idempotent).
watch(artistHits, (hits) => {
  for (const h of hits) for (const id of h.coverCandidateIds) lib.requestCover(id);
}, { immediate: true });

/** Loaded cover URLs for an artist's collage, in candidate order. */
function coverUrlsFor(hit: ArtistHit): (string | null)[] {
  return hit.coverCandidateIds.map((id) => lib.coverCache.get(id) ?? null);
}

function openArtist(name: string): void {
  void router.push({ name: "artist", params: { name } });
}

interface SearchResultCard {
  item: AlbumGridItem;
  track: CatalogTrack;
}

/** One album card PER matching track — not grouped by album — so every song
 *  in the results gets its own cover card, labelled with the track title and
 *  artist. Same item shape the library's album grid renders. */
const resultCards = computed<SearchResultCard[]>(() =>
  results.value.map((t) => {
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
  }),
);

function openTrackAlbum(track: CatalogTrack): void {
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(track) } });
}

// Only the first page of results is rendered; "Load more" reveals the next.
const PAGE_SIZE = 10;
const visibleCount = ref(PAGE_SIZE);
watch(debouncedQuery, () => {
  visibleCount.value = PAGE_SIZE;
});
const visibleResults = computed(() => results.value.slice(0, visibleCount.value));
const hasMore = computed(() => results.value.length > visibleCount.value);
function loadMore(): void {
  visibleCount.value = Math.min(visibleCount.value + PAGE_SIZE, results.value.length);
}

/** Recently selected search results, resolved against the catalog and kept
 *  in their recorded order; ids that no longer exist drop out. */
const recentResultTracks = computed(() => {
  const byId = new Map(lib.tracks.map((t) => [t.id, t]));
  return settings.recentSearchResultIds
    .map((id) => byId.get(id))
    .filter((t): t is CatalogTrack => t !== undefined);
});

provideVisibleIds(() =>
  (debouncedQuery.value.trim() ? visibleResults.value : recentResultTracks.value).map((t) => t.id),
);

function focusInput(): void {
  inputEl.value?.focus();
  inputEl.value?.select();
}

// The signal is bumped by the sidebar Search click and the "/" shortcut, so
// the input is focused whenever navigation asks for it and typing can start
// immediately.
watch(searchFocusSignal, async () => {
  await nextTick();
  focusInput();
});

function recordSearch(value: string): void {
  const trimmed = value.trim();
  if (trimmed.length > 0) settings.addSearch(trimmed);
}

function onSearchEnter(): void {
  recordSearch(query.value);
}

function clearSearch(): void {
  query.value = "";
}

function selectHistoryChip(entry: string): void {
  query.value = entry;
  recordSearch(entry);
  focusInput();
}

function playTrack(track: CatalogTrack, queue: CatalogTrack[]): void {
  settings.addSearchResult(track.id);
  recordSearch(query.value);
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
