<template>
  <div class="border-t border-outline/10">
    <!-- Search history -->
    <div v-if="!query && settings.searchHistory.length > 0" class="content-col px-4 pb-2 pt-3">
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
          class="rounded-full bg-surface px-3 py-1.5 text-label-lg text-on-surface"
          @click="selectHistoryChip(h)"
        >
          {{ h }}
        </button>
      </div>
    </div>

    <!-- Search field: frosted, same layer as the sort/genre controls -->
    <div class="content-col px-4 pb-2 pt-3">
      <div
        class="flex h-11 items-center gap-2 rounded-full bg-surface/60 px-4 backdrop-blur-2xl focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
      >
        <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <input
          v-model="query"
          type="text"
          placeholder="Search albums, artists…"
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

    <!-- Artist filter chip -->
    <div v-if="artistLabel" class="content-col px-4 pb-2 pt-2">
      <div class="inline-flex items-center gap-1 rounded-full bg-surface px-2 py-1.5">
        <button
          type="button"
          class="flex items-center gap-2 rounded-full px-1 text-on-surface lg:hover:opacity-80"
          aria-label="Browse artist"
          @click="openArtist"
        >
          <MageIcon name="user" class="h-4 w-4 text-on-surface-variant" />
          <span class="text-label-lg text-on-surface">{{ artistLabel }}</span>
        </button>
        <button
          type="button"
          class="text-on-surface-variant"
          aria-label="Clear artist filter"
          @click="clearArtistFilter"
        >
          <MageIcon name="multiply" class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- Toolbar: menus open upward now that the bar sits at the bottom. -->
    <div class="content-col flex items-center gap-1 px-4 py-2">
      <div ref="sortMenuRef" class="relative">
        <button
          ref="sortBtnRef"
          type="button"
          class="text-label-lg text-on-surface"
          @click="toggleSort"
        >
          Sort: {{ sortLabel }}
        </button>
        <!-- Teleported to body so the overflow-hidden collapse shell can't clip it. -->
        <Teleport to="body">
          <div
            v-if="sortMenuOpen"
            class="fixed z-50 w-32 rounded-xl bg-surface-container py-1 shadow-xl"
            :style="sortMenuPos"
          >
            <button
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
              @click="selectSort(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <MageIcon v-if="settings.sortMode === opt.value" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </Teleport>
      </div>

      <button
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
        :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
        @click="settings.setSortAscending(!settings.sortAscending)"
      >
        <MageIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
      </button>

      <div ref="genreMenuRef" class="relative">
        <button
          ref="genreBtnRef"
          type="button"
          class="flex max-w-28 items-center gap-1 text-label-lg text-on-surface"
          @click="toggleGenre"
        >
          <span class="truncate">Genre: {{ genreLabel }}</span>
          <MageIcon :name="genreMenuOpen ? 'chevron-down' : 'chevron-up'" class="h-4 w-4 shrink-0" />
        </button>
        <Teleport to="body">
          <div
            v-if="genreMenuOpen"
            class="fixed z-50 max-h-72 w-48 overflow-y-auto rounded-xl bg-surface-container py-1 shadow-xl"
            :style="genreMenuPos"
          >
            <button
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
              @click="selectGenre(null)"
            >
              <span>All genres</span>
              <MageIcon v-if="!lib.genreFilter" name="check" class="h-4 w-4 text-primary" />
            </button>
            <button
              v-for="g in lib.genres"
              :key="g.value"
              type="button"
              class="flex w-full items-center justify-between gap-2 px-3 py-2 text-label-lg text-on-surface"
              @click="selectGenre(g.value)"
            >
              <span class="truncate">{{ g.label }}</span>
              <MageIcon v-if="lib.genreFilter === g.value" name="check" class="h-4 w-4 shrink-0 text-primary" />
            </button>
          </div>
        </Teleport>
      </div>

      <div class="flex-1" />

      <button
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
        aria-label="Change layout"
        @click="settings.cycleAlbumViewStyle()"
      >
        <MageIcon :name="viewStyleIcon" class="h-4 w-4" />
      </button>

      <button
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-full transition-colors"
        :class="player.shuffleAllActive ? 'text-primary' : 'text-on-surface'"
        aria-label="Shuffle all"
        :aria-pressed="player.shuffleAllActive"
        @click="player.startShuffleAll(lib.filteredTracks)"
      >
        <MageIcon name="exchange" class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import type { SortMode } from "../types";

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];

// --- Search field --------------------------------------------------------

const query = ref(lib.searchQuery);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

/** Applies the query to the live library filter (debounced while typing). */
function commitSearch(value: string): void {
  lib.searchQuery = value;
}

/** Records a committed search in the history — Enter or tapping a history
 *  chip only, never intermediate keystrokes (the debounced watcher just
 *  filters live). */
function recordSearch(value: string): void {
  const trimmed = value.trim();
  if (trimmed.length > 0) settings.addSearch(trimmed);
}

watch(query, (value) => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => commitSearch(value), 300);
});

function onSearchEnter(): void {
  clearTimeout(debounceTimer);
  commitSearch(query.value);
  recordSearch(query.value);
}

function clearSearch(): void {
  clearTimeout(debounceTimer);
  query.value = "";
  commitSearch("");
}

function selectHistoryChip(entry: string): void {
  clearTimeout(debounceTimer);
  query.value = entry;
  commitSearch(entry);
  recordSearch(entry);
}

onUnmounted(() => clearTimeout(debounceTimer));

// --- Artist filter ---------------------------------------------------------

const artistLabel = computed(() =>
  typeof route.query.artist === "string" ? route.query.artist : null,
);

watch(
  () => route.query.artist,
  (value) => {
    lib.artistFilter = (value as string) ?? null;
  },
  { immediate: true },
);

onUnmounted(() => {
  lib.artistFilter = null;
});

function clearArtistFilter(): void {
  void router.replace({ name: "library" });
}

/** Open the artist browse view for the currently filtered artist. */
function openArtist(): void {
  if (artistLabel.value) {
    void router.push({ name: "artist", params: { name: artistLabel.value } });
  }
}

// --- Sort dropdown ---------------------------------------------------------

const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);
const sortBtnRef = ref<HTMLElement | null>(null);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
);

const viewStyleIcon = computed(() => {
  if (settings.albumViewStyle === "list") return "arrowlist";
  if (settings.albumViewStyle === "tracks") return "music";
  return "layout-grid";
});

/** Position the sort menu just above its trigger (bottom edge pinned), since
 *  the search bar sits at the bottom of the shell. */
const sortMenuPos = computed(() => {
  const b = sortBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  const bottom = window.innerHeight - b.top + 4;
  return { bottom: `${bottom}px`, left: `${b.left}px` };
});

function toggleSort(): void {
  sortMenuOpen.value = !sortMenuOpen.value;
}

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}

// --- Genre filter dropdown ----------------------------------------------

const genreMenuOpen = ref(false);
const genreMenuRef = ref<HTMLElement | null>(null);
const genreBtnRef = ref<HTMLElement | null>(null);

const genreLabel = computed(
  () => lib.genres.find((g) => g.value === lib.genreFilter)?.label ?? "All",
);

/** Position the genre menu just above its trigger. Pinning the BOTTOM edge
 *  (not computing `top` from a phantom full list height) keeps it anchored to
 *  the control even when the list is long and the menu's own max-height clips
 *  it — otherwise it flies to the top of the screen. */
const genreMenuPos = computed(() => {
  const b = genreBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  // Bottom edge sits 4px above the button's top edge.
  const bottom = window.innerHeight - b.top + 4;
  return { bottom: `${bottom}px`, left: `${b.left}px` };
});

function toggleGenre(): void {
  genreMenuOpen.value = !genreMenuOpen.value;
}

function selectGenre(value: string | null): void {
  lib.genreFilter = value;
  genreMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value && !genreMenuOpen.value) return;
  const target = e.target as Node;
  if (sortMenuOpen.value && sortMenuRef.value && !sortMenuRef.value.contains(target)) {
    sortMenuOpen.value = false;
  }
  if (genreMenuOpen.value && genreMenuRef.value && !genreMenuRef.value.contains(target)) {
    genreMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));
</script>
