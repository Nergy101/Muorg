<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <!-- Search bar pinned at the TOP of the screen; results scroll between it
         and the bottom island/keyboard. -->
    <div class="content-col shrink-0 px-4 pb-2 pt-3">
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
      </div>
    </div>

    <div class="scrollbar-overlay min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <!-- Empty query: recent searches + recently selected results. -->
      <template v-if="!query.trim()">
        <div v-if="settings.searchHistory.length > 0" class="content-col px-4 pb-2 pt-1">
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

        <div v-if="recentResultTracks.length > 0" class="content-col pb-2 pt-2">
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
          class="content-col"
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
          class="content-col"
          :title="`No results for &quot;${query.trim()}&quot;`"
          body="Try another spelling or fewer words."
        />
        <div v-else class="content-col pb-4">
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
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useSelectionStore } from "../stores/selection";
import { searchFocusSignal } from "../composables/useSearchFocus";
import { provideVisibleIds } from "../composables/useSelectableList";
import type { CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const selection = useSelectionStore();
const query = ref("");
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

// First arrival stays unfocused — re-tapping the Search tab focuses the
// input so typing can start immediately.
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
