<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import packageJson from "../../../package.json";
import FeatherIcon from "../shared/FeatherIcon.vue";
import StarRating from "../shared/StarRating.vue";

const props = defineProps<{
  activeTab: "library" | "metadata" | "player" | "queue";
  sidebarCollapsed: boolean;
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "metadata" | "player" | "queue"): void;
  (e: "expandSidebar"): void;
  (e: "openSettings"): void;
  (e: "openKeyMap"): void;
  (e: "expandAllGroups"): void;
  (e: "collapseAllGroups"): void;
  (e: "expandPlayer"): void;
}>();

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { searchQuery, groupBy, filteredTracks, activePlaylistId, filterMinRating, filterGenre, tracks } = storeToRefs(store);
const { playlists } = storeToRefs(playlistStore);
const { theme } = storeToRefs(settingsStore);

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
      if (e.key >= "1" && e.key <= "5") {
        e.preventDefault();
        store.setRatingForSelection(parseInt(e.key, 10));
        return;
      }
      if (e.key === "0") {
        e.preventDefault();
        store.setRatingForSelection(null);
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
  <div class="flex min-w-0 flex-wrap items-center justify-between gap-3 border-b border-stone-700 px-4 py-2">
    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3">
      <!-- Active playlist filter tag -->
      <div
        v-if="activePlaylistId !== null"
        class="flex shrink-0 items-center gap-1 rounded-full border border-stone-600 bg-stone-700 pl-2 pr-1 py-0.5 text-xs text-stone-200"
      >
        <FeatherIcon name="list" class="h-3 w-3 shrink-0 text-stone-400" />
        <span class="max-w-[140px] truncate">{{ activePlaylist?.name ?? "Playlist" }}</span>
        <button
          type="button"
          class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-stone-400 hover:bg-stone-600 hover:text-stone-200"
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
          class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-800 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Expand sidebar"
          @click="emit('expandSidebar')"
        >
          <FeatherIcon name="chevrons-right" class="h-4 w-4" />
        </button>
      </Transition>
      <input
        ref="searchInputRef"
        :value="searchQuery"
        type="search"
        placeholder="Search title, artist, album…"
        class="min-w-[200px] rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 placeholder-stone-500"
        @input="store.setSearchQuery(($event.target as HTMLInputElement).value)"
      />
      <!-- Min-rating filter -->
      <div class="flex items-center gap-1.5 rounded border border-stone-600 bg-stone-800 px-2 py-1" :class="filterMinRating !== null ? 'border-amber-600/60' : ''">
        <StarRating
          :model-value="filterMinRating"
          @update:model-value="store.setFilterMinRating"
        />
        <button
          v-if="filterMinRating !== null"
          type="button"
          class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-stone-400 hover:bg-stone-600 hover:text-stone-200"
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
          class="flex items-center gap-1.5 rounded border bg-stone-800 px-2 py-1 text-sm hover:border-stone-500 hover:bg-stone-700"
          :class="filterGenre !== null ? 'border-primary text-[#5b7c32]' : 'border-stone-600 text-stone-200'"
          @click="toggleGenreDropdown"
        >
          <FeatherIcon name="tag" class="h-3.5 w-3.5 shrink-0" :class="filterGenre !== null ? 'text-[#5b7c32]' : 'text-stone-500'" />
          <span>{{ filterGenre ?? "Genre" }}</span>
          <button
            v-if="filterGenre !== null"
            type="button"
            class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Clear genre filter"
            @click.stop="store.setFilterGenre(null)"
          >
            <FeatherIcon name="x" class="h-3 w-3" />
          </button>
          <FeatherIcon v-else name="chevron-down" class="h-3.5 w-3.5 text-stone-400 transition-transform" :class="genreDropdownOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="genreDropdownOpen"
          class="absolute left-0 top-full z-[300] mt-1 max-h-64 min-w-full overflow-x-hidden overflow-y-auto rounded border border-stone-600 bg-stone-800 py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        >
          <button
            v-for="genre in allGenres"
            :key="genre"
            type="button"
            class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-stone-700"
            :class="filterGenre === genre ? 'text-stone-200' : 'text-stone-400'"
            @click="selectGenre(genre)"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="filterGenre === genre ? 'text-stone-200' : 'invisible'"
            />
            {{ genre }}
          </button>
        </div>
      </div>

      <!-- Custom group-by dropdown -->
      <div ref="groupByDropdownRef" class="relative min-w-max shrink-0">
        <button
          type="button"
          class="flex items-center gap-1.5 whitespace-nowrap rounded border border-stone-600 bg-stone-800 px-2.5 py-1.5 text-sm text-stone-200 hover:border-stone-500 hover:bg-stone-700"
          :class="groupByDropdownOpen ? 'border-stone-500 bg-stone-700' : ''"
          @click="toggleGroupByDropdown"
        >
          <span class="whitespace-nowrap">{{ groupByLabels[groupByValue] }}</span>
          <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-stone-400 transition-transform" :class="groupByDropdownOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="groupByDropdownOpen"
          class="absolute left-0 top-full z-[300] mt-1 min-w-full rounded border border-stone-600 bg-stone-800 py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        >
          <button
            v-for="(label, val) in groupByLabels"
            :key="val"
            type="button"
            class="flex w-full items-center gap-2 whitespace-nowrap px-3 py-1.5 text-left text-sm hover:bg-stone-700"
            :class="groupByValue === val ? 'text-stone-200' : 'text-stone-400'"
            @click="selectGroupBy(val as 'none' | 'artist' | 'album')"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="groupByValue === val ? 'text-stone-200' : 'invisible'"
            />
            {{ label }}
          </button>
        </div>
      </div>

      <div
        v-if="groupByValue !== 'none'"
        class="flex items-center gap-1"
      >
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Expand all groups to show every track', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Expand all"
            @click="$emit('expandAllGroups')"
          >
            <FeatherIcon name="plus-square" class="h-3.5 w-3.5" />
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Collapse all groups to show only group headers', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Collapse all"
            @click="$emit('collapseAllGroups')"
          >
            <FeatherIcon name="minus-square" class="h-3.5 w-3.5" />
          </button>
        </span>
      </div>
    </div>

    <div class="flex items-center justify-start gap-1">
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-stone-600 hover:text-stone-200"
        :class="props.activeTab === 'library' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'library')"
      >
        Default
      </button>
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-stone-600 hover:text-stone-200"
        :class="props.activeTab === 'metadata' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'metadata')"
      >
        Metadata
      </button>
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-stone-600 hover:text-stone-200"
        :class="props.activeTab === 'player' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'player')"
      >
        Player
      </button>
      <button
        type="button"
        class="primary-tab rounded px-3 py-1 text-xs font-medium transition-colors hover:bg-stone-600 hover:text-stone-200"
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
      <img src="/favicon.svg" alt="" class="h-6 w-6 shrink-0" />
      <span class="text-sm font-semibold text-stone-200">Muorg</span>
      <span
        class="relative z-[220] inline-flex"
        @mouseenter="showTooltip('Key map', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
          aria-label="Key map"
          @mousedown.stop="emit('openKeyMap')"
          @click.stop="emit('openKeyMap')"
        >
          <FeatherIcon name="command" class="h-4 w-4" />
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Settings', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
<button
        type="button"
        class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
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
      class="fixed z-[500] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)] whitespace-nowrap"
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

