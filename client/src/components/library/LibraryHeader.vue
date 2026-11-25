<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import logoUrl from "../../assets/muorg-logo.svg";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import packageJson from "../../../package.json";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import StarRating from "../shared/StarRating.vue";

const props = defineProps<{
  activeTab: "library" | "search" | "metadata" | "player" | "queue";
  sidebarCollapsed: boolean;
  showBack?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "search" | "metadata" | "player" | "queue"): void;
  (e: "expandSidebar"): void;
  (e: "back"): void;
  (e: "openSettings"): void;
  (e: "openKeyMap"): void;
  (e: "expandPlayer"): void;
}>();

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { searchQuery, groupBy, filteredTracks, activePlaylistId, filterMinRating, filterGenre, tracks } = storeToRefs(store);
const { playlists } = storeToRefs(playlistStore);
const { theme, libraryLayoutMode, albumGridSortBy } = storeToRefs(settingsStore);

const activePlaylist = computed(() =>
  activePlaylistId.value != null
    ? playlists.value.find((p) => p.id === activePlaylistId.value) ?? null
    : null
);

const appVersion = packageJson.version;

type TooltipPosition = "left" | "below" | "below-left";
const tooltipPopover = ref<{ text: string; x: number; y: number; position?: TooltipPosition } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent, position: TooltipPosition = "below") {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (position === "left") {
    tooltipPopover.value = { text, x: rect.left - 8, y: rect.top + rect.height / 2, position: "left" };
  } else if (position === "below-left") {
    tooltipPopover.value = { text, x: rect.left, y: rect.bottom + 6, position: "below-left" };
  } else {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => {
    tooltipPopover.value = null;
    tooltipHideTimeout = null;
  }, 100);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function onGlobalKeydown(e: KeyboardEvent) {
  // Rating shortcuts: 0–5 when not in an editable field and no modifier key
  if (!e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey) {
    const target = e.target as HTMLElement | null;
    const tag = target?.tagName;
    const isEditable =
      !!target &&
      (tag === "INPUT" || tag === "TEXTAREA" || (target as HTMLElement).isContentEditable);
    if (!isEditable && store.selectedTrackIds.length > 0) {
      let rating: number | null | undefined;
      if (e.key >= "1" && e.key <= "5") {
        rating = parseInt(e.key, 10);
      } else if (e.key === "0") {
        rating = null;
      }
      if (rating !== undefined) {
        e.preventDefault();
        // Q4: confirm before rating more than one track at once
        if (store.selectedTrackIds.length > 1) {
          const label = rating == null ? "clear the rating" : `set rating to ${rating}`;
          if (!confirm(`Apply to ${store.selectedTrackIds.length} selected tracks: ${label}?`)) return;
        }
        store.setRatingForSelection(rating);
        return;
      }
    }
  }

  if (e.ctrlKey || e.metaKey) {
    const target = e.target as HTMLElement | null;
    const tag = target?.tagName;
    const isEditable =
      !!target &&
      (tag === "INPUT" || tag === "TEXTAREA" || (target as HTMLElement).isContentEditable);

    if (e.key === "f") {
      e.preventDefault();
      searchExpandedLocal.value = true;
      nextTick(() => searchInputRef.value?.focus());
      return;
    }

    if (isEditable) return;

    if (e.key === "a") {
      e.preventDefault();
      const ids = filteredTracks.value.map((t) => t.id);
      store.setSelection(ids);
      store.setMultiSelectMode(true);
      return;
    }

    if (e.key === "m") {
      e.preventDefault();
      emit("expandPlayer");
      return;
    }

    if (e.key === "1") {
      e.preventDefault();
      emit("update:activeTab", "library");
      return;
    }

    if (e.key === "2") {
      e.preventDefault();
      const nextTab = props.activeTab === "metadata" ? "library" : "metadata";
      emit("update:activeTab", nextTab);
      return;
    }

    if (e.key === "3") {
      e.preventDefault();
      const nextTab = props.activeTab === "player" ? "library" : "player";
      emit("update:activeTab", nextTab);
      return;
    }

    if (e.key === "4") {
      e.preventDefault();
      const nextTab = props.activeTab === "queue" ? "library" : "queue";
      emit("update:activeTab", nextTab);
      return;
    }

    if (e.key === "k") {
      e.preventDefault();
      emit("openKeyMap");
      return;
    }

    if (e.key === "t") {
      e.preventDefault();
      settingsStore.setTheme(theme.value === "dark" ? "light" : "dark");
      return;
    }
  }
}

const searchInputRef = ref<HTMLInputElement | null>(null);
const searchExpandedLocal = ref(false);

function onSearchBlur() {
  if (!searchQuery.value) searchExpandedLocal.value = false;
}

onMounted(() => {
  document.addEventListener("keydown", onGlobalKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
});

watch(
  () => props.activeTab,
  () => {
    hideTooltip();
  },
);

const groupByValue = computed(() => groupBy.value);

const groupByLabels: Record<string, string> = {
  album: "Group by album",
  artist: "Group by artist",
  none: "No grouping",
};

const groupByDropdownOpen = ref(false);
const groupByDropdownRef = ref<HTMLElement | null>(null);

function toggleGroupByDropdown() {
  groupByDropdownOpen.value = !groupByDropdownOpen.value;
}

function selectGroupBy(value: "none" | "artist" | "album") {
  store.setGroupBy(value);
  groupByDropdownOpen.value = false;
}

function onGroupByDropdownClickOutside(e: MouseEvent) {
  if (groupByDropdownRef.value && !groupByDropdownRef.value.contains(e.target as Node)) {
    groupByDropdownOpen.value = false;
  }
}

watch(groupByDropdownOpen, (open) => {
  if (open) {
    document.addEventListener("mousedown", onGroupByDropdownClickOutside);
  } else {
    document.removeEventListener("mousedown", onGroupByDropdownClickOutside);
  }
});

const albumGridSortLabels: Record<string, string> = {
  album: "Sort by album",
  artist: "Sort by artist",
  year: "Sort by year",
};

const albumGridSortDropdownOpen = ref(false);
const albumGridSortDropdownRef = ref<HTMLElement | null>(null);

function toggleAlbumGridSortDropdown() {
  albumGridSortDropdownOpen.value = !albumGridSortDropdownOpen.value;
}

function selectAlbumGridSort(value: "album" | "artist" | "year") {
  settingsStore.setAlbumGridSortBy(value);
  albumGridSortDropdownOpen.value = false;
}

function onAlbumGridSortDropdownClickOutside(e: MouseEvent) {
  if (albumGridSortDropdownRef.value && !albumGridSortDropdownRef.value.contains(e.target as Node)) {
    albumGridSortDropdownOpen.value = false;
  }
}

watch(albumGridSortDropdownOpen, (open) => {
  if (open) {
    document.addEventListener("mousedown", onAlbumGridSortDropdownClickOutside);
  } else {
    document.removeEventListener("mousedown", onAlbumGridSortDropdownClickOutside);
  }
});

const allGenres = computed(() => {
  const set = new Set<string>();
  for (const t of tracks.value) {
    if (t.genre) set.add(t.genre);
  }
  return [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
});

const genreDropdownOpen = ref(false);
const genreDropdownRef = ref<HTMLElement | null>(null);

function toggleGenreDropdown() {
  genreDropdownOpen.value = !genreDropdownOpen.value;
}

function selectGenre(genre: string | null) {
  store.setFilterGenre(genre);
  genreDropdownOpen.value = false;
}

function onGenreDropdownClickOutside(e: MouseEvent) {
  if (genreDropdownRef.value && !genreDropdownRef.value.contains(e.target as Node)) {
    genreDropdownOpen.value = false;
  }
}

watch(genreDropdownOpen, (open) => {
  if (open) {
    document.addEventListener("mousedown", onGenreDropdownClickOutside);
  } else {
    document.removeEventListener("mousedown", onGenreDropdownClickOutside);
  }
});

</script>

<template>
  <div class="flex min-w-0 flex-wrap items-center justify-between gap-3 border-b border-outline px-4 py-2">
    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3">
      <!-- Active playlist filter tag -->
      <div
        v-if="activePlaylistId !== null"
        class="flex shrink-0 items-center gap-1 rounded-full border border-outline bg-surface-variant pl-2 pr-1 py-0.5 text-xs text-on-surface"
      >
        <FeatherIcon name="list" class="h-3 w-3 shrink-0 text-on-surface-variant" />
        <span class="max-w-[140px] truncate">{{ activePlaylist?.name ?? store.activePlaylistName ?? "Playlist" }}</span>
        <button
          type="button"
          class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Clear playlist filter"
          @click="store.clearActivePlaylist()"
        >
          <FeatherIcon name="x" class="h-3 w-3" />
        </button>
      </div>
      <Transition name="fade-inline">
        <button
          v-if="props.sidebarCollapsed"
          type="button"
          class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border border-outline bg-surface-container text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Expand sidebar"
          @click="emit('expandSidebar')"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" /><path d="M9 3v18m5-12l3 3l-3 3" /></svg>
        </button>
      </Transition>
      <Transition name="fade-inline">
        <button
          v-if="props.showBack"
          type="button"
          class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border border-outline bg-surface-container text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Back"
          @click="emit('back')"
        >
          <FeatherIcon name="arrow-left" class="h-4 w-4" />
        </button>
      </Transition>
      <div class="flex shrink-0 items-center">
        <!-- Web-style glass search pill (always visible, like the web-client's LibrarySearchBar) -->
        <div class="glass-field flex h-9 w-56 shrink-0 items-center gap-2 rounded-full px-3 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary">
          <FeatherIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          <input
            ref="searchInputRef"
            :value="searchQuery"
            type="text"
            placeholder="Search title, artist, album…"
            class="min-w-0 flex-1 bg-transparent text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none"
            @input="store.setSearchQuery(($event.target as HTMLInputElement).value)"
            @focus="emit('update:activeTab', 'search')"
            @blur="onSearchBlur"
          />
          <button
            v-if="searchQuery"
            type="button"
            class="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
            aria-label="Clear search"
            @mousedown.prevent="store.setSearchQuery('')"
          >
            <FeatherIcon name="x" class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <!-- Shuffle all -->
      <button
        type="button"
        class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-surface-container text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
        :class="store.shuffleAllActive ? 'border-primary text-primary' : 'border-outline'"
        aria-label="Shuffle all"
        :aria-pressed="store.shuffleAllActive"
        title="Shuffle all"
        @click="store.startShuffleAll(filteredTracks)"
      >
        <FeatherIcon name="shuffle" class="h-4 w-4" />
      </button>
      <!-- Min-rating filter -->
      <div class="flex items-center gap-1.5 rounded border border-outline bg-surface-container px-2 py-1" :class="filterMinRating !== null ? 'border-amber-600/60' : ''">
        <StarRating
          :model-value="filterMinRating"
          @update:model-value="store.setFilterMinRating"
        />
        <button
          v-if="filterMinRating !== null"
          type="button"
          class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Clear rating filter"
          @click="store.setFilterMinRating(null)"
        >
          <FeatherIcon name="x" class="h-3 w-3" />
        </button>
      </div>

      <!-- Genre filter dropdown -->
      <div v-if="allGenres.length > 0" ref="genreDropdownRef" class="relative">
        <button
          type="button"
          class="flex items-center gap-1.5 rounded border bg-surface-container px-2 py-1 text-sm hover:border-outline hover:bg-surface-container"
          :class="filterGenre !== null ? 'border-primary text-primary' : 'border-outline text-on-surface'"
          @click="toggleGenreDropdown"
        >
          <span>{{ filterGenre ?? "Genre" }}</span>
          <button
            v-if="filterGenre !== null"
            type="button"
            class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
            aria-label="Clear genre filter"
            @click.stop="store.setFilterGenre(null)"
          >
            <FeatherIcon name="x" class="h-3 w-3" />
          </button>
          <FeatherIcon v-else name="chevron-down" class="h-3.5 w-3.5 text-on-surface-variant transition-transform" :class="genreDropdownOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="genreDropdownOpen"
          class="absolute left-0 top-full z-[300] mt-1 max-h-64 min-w-full overflow-x-hidden overflow-y-auto rounded border border-outline bg-surface-container py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        >
          <button
            v-for="genre in allGenres"
            :key="genre"
            type="button"
            class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-surface-container"
            :class="filterGenre === genre ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="selectGenre(genre)"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="filterGenre === genre ? 'text-on-surface' : 'invisible'"
            />
            {{ genre }}
          </button>
        </div>
      </div>

      <!-- Custom group-by dropdown (table layout only) -->
      <div v-if="libraryLayoutMode === 'table'" ref="groupByDropdownRef" class="relative min-w-max shrink-0">
        <button
          type="button"
          class="flex items-center gap-1.5 whitespace-nowrap rounded border border-outline bg-surface-container px-2 py-1 text-sm text-on-surface hover:border-outline hover:bg-surface-container"
          :class="groupByDropdownOpen ? 'border-outline bg-surface-variant' : ''"
          @click="toggleGroupByDropdown"
        >
          <span class="whitespace-nowrap">{{ groupByLabels[groupByValue] }}</span>
          <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-on-surface-variant transition-transform" :class="groupByDropdownOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="groupByDropdownOpen"
          class="absolute left-0 top-full z-[300] mt-1 min-w-full rounded border border-outline bg-surface-container py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        >
          <button
            v-for="(label, val) in groupByLabels"
            :key="val"
            type="button"
            class="flex w-full items-center gap-2 whitespace-nowrap px-3 py-1.5 text-left text-sm hover:bg-surface-container"
            :class="groupByValue === val ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="selectGroupBy(val as 'none' | 'artist' | 'album')"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="groupByValue === val ? 'text-on-surface' : 'invisible'"
            />
            {{ label }}
          </button>
        </div>
      </div>

      <!-- Sort-by dropdown (album grid layout only) -->
      <div v-if="libraryLayoutMode === 'album_grid'" ref="albumGridSortDropdownRef" class="relative min-w-max shrink-0">
        <button
          type="button"
          class="flex items-center gap-1.5 whitespace-nowrap rounded border border-outline bg-surface-container px-2 py-1 text-sm text-on-surface hover:border-outline hover:bg-surface-container"
          :class="albumGridSortDropdownOpen ? 'border-outline bg-surface-variant' : ''"
          @click="toggleAlbumGridSortDropdown"
        >
          <span class="whitespace-nowrap">{{ albumGridSortLabels[albumGridSortBy] }}</span>
          <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-on-surface-variant transition-transform" :class="albumGridSortDropdownOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="albumGridSortDropdownOpen"
          class="absolute left-0 top-full z-[300] mt-1 min-w-full rounded border border-outline bg-surface-container py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        >
          <button
            v-for="(label, val) in albumGridSortLabels"
            :key="val"
            type="button"
            class="flex w-full items-center gap-2 whitespace-nowrap px-3 py-1.5 text-left text-sm hover:bg-surface-container"
            :class="albumGridSortBy === val ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="selectAlbumGridSort(val as 'album' | 'artist' | 'year')"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="albumGridSortBy === val ? 'text-on-surface' : 'invisible'"
            />
            {{ label }}
          </button>
        </div>
      </div>

      <div class="flex items-center gap-1 rounded border border-outline bg-surface-container p-0.5">
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="libraryLayoutMode === 'table' ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
          aria-label="Table layout"
          @click="settingsStore.setLibraryLayoutMode('table')"
        >
          <FeatherIcon name="list" class="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="libraryLayoutMode === 'album_grid' ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
          aria-label="Album grid layout"
          @click="settingsStore.setLibraryLayoutMode('album_grid')"
        >
          <FeatherIcon name="grid" class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <div class="flex items-center justify-start gap-1">
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-surface-container hover:text-on-surface"
        :class="props.activeTab === 'library' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'library')"
      >
        Default
      </button>
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-surface-container hover:text-on-surface"
        :class="props.activeTab === 'metadata' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'metadata')"
      >
        Metadata
      </button>
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-surface-container hover:text-on-surface"
        :class="props.activeTab === 'queue' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'queue')"
      >
        Queue
      </button>
    </div>

    <div
      class="relative z-[210] flex shrink-0 items-center gap-2"
      @mouseenter="showTooltip('Version ' + appVersion, $event)"
      @mouseleave="scheduleHideTooltip"
    >
      <img :src="logoUrl" alt="" class="h-6 w-6 shrink-0" />
      <span class="text-sm font-semibold text-on-surface">Muorg</span>
      <span
        class="relative z-[220] inline-flex"
        @mouseenter="showTooltip('Key map', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Key map"
          @mousedown.stop="emit('openKeyMap')"
          @click.stop="emit('openKeyMap')"
        >
          <FeatherIcon name="command" class="h-4 w-4" />
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Search page', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          aria-label="Search page"
          @click="emit('update:activeTab', 'search')"
        >
          <FeatherIcon name="search" class="h-4 w-4" />
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Settings', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
<button
        type="button"
        class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
        aria-label="Application settings"
        @click="emit('openSettings')"
      >
        <FeatherIcon name="settings" class="h-4 w-4" />
      </button>
      </span>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[500] rounded-lg border border-outline bg-surface-container px-3 py-2 text-xs text-on-surface shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)] whitespace-nowrap"
      :style="tooltipPopover.position === 'left'
        ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translate(-100%, -50%)' }
        : tooltipPopover.position === 'below-left'
          ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px' }
          : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
</template>

<style scoped>
.fade-inline-enter-active,
.fade-inline-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.fade-inline-enter-from,
.fade-inline-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}

</style>

