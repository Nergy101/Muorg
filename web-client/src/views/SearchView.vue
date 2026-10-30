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
          placeholder="Search tracks, artists, albums…"
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
            @play="playTrack(track, recentResultTracks)"
            @actions="sheetTrack = track"
          />
        </div>

        <div
          v-if="settings.searchHistory.length === 0 && recentResultTracks.length === 0 && !lib.loading"
          class="content-col flex flex-col items-center gap-2 px-6 py-12 text-center"
        >
          <MageIcon name="search" class="h-12 w-12 text-on-surface-variant/40" />
          <span class="text-body-md text-on-surface-variant">
            Type above to search your library
          </span>
        </div>
      </template>

      <!-- Live results as plain track rows, same as the library's search. -->
      <template v-else>
        <div v-if="lib.loading" class="flex items-center justify-center py-12">
          <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
        </div>
        <div
          v-else-if="results.length === 0"
          class="content-col flex flex-col items-center gap-2 px-6 py-12 text-center"
        >
          <MageIcon name="music" class="h-12 w-12 text-on-surface-variant/40" />
          <span class="text-body-md text-on-surface-variant">No results for "{{ query.trim() }}"</span>
        </div>
        <div v-else class="content-col pb-4">
          <TrackListRow
            v-for="track in results"
            :key="track.id"
            :track="track"
            :is-playing="player.currentTrack?.id === track.id"
            :is-loading="player.pendingTrackId === track.id"
            :subtitle="`${track.artist ?? track.album_artist ?? '—'} • ${track.album ?? '—'}`"
            @play="playTrack(track, results)"
            @actions="sheetTrack = track"
          />
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
import { computed, nextTick, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { searchFocusSignal } from "../composables/useSearchFocus";
import type { CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();

const query = ref("");
const inputEl = ref<HTMLInputElement | null>(null);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

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
  const q = normalize(query.value);
  if (!q) return [];
  return lib.tracks
    .map((t) => ({ t, s: matchScore(t, q) }))
    .filter(({ s }) => s > 0)
    .sort((a, b) => b.s - a.s || normalize(a.t.title).localeCompare(normalize(b.t.title)))
    .map(({ t }) => t);
});

/** Recently selected search results, resolved against the catalog and kept
 *  in their recorded order; ids that no longer exist drop out. */
const recentResultTracks = computed(() => {
  const byId = new Map(lib.tracks.map((t) => [t.id, t]));
  return settings.recentSearchResultIds
    .map((id) => byId.get(id))
    .filter((t): t is CatalogTrack => t !== undefined);
});

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
  clearTimeout(debounceTimer);
  recordSearch(query.value);
}

function clearSearch(): void {
  clearTimeout(debounceTimer);
  query.value = "";
}

function selectHistoryChip(entry: string): void {
  clearTimeout(debounceTimer);
  query.value = entry;
  recordSearch(entry);
  focusInput();
}

function playTrack(track: CatalogTrack, queue: CatalogTrack[]): void {
  settings.addSearchResult(track.id);
  recordSearch(query.value);
  void player.playTrack(track, queue);
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

onUnmounted(() => clearTimeout(debounceTimer));
</script>
