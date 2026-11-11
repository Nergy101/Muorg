<template>
  <!-- atTop (desktop library): renders inline as a flex child of the top row,
       next to the filter controls. Otherwise (mobile bottom island) it stacks
       its own rows above the tab bar. Recent-search tags are deliberately not
       shown here — they live on the Search screen only. -->
  <div :class="atTop ? 'flex min-w-0 flex-1 items-center gap-2' : 'border-t border-outline/10'">
    <!-- Search field: frosted, same layer as the sort/genre controls -->
    <div :class="atTop ? 'min-w-0 flex-1' : 'content-col px-4 pb-2 pt-3'">
      <div
        class="glass-field flex h-11 items-center gap-2 rounded-full px-4 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
      >
        <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <input
          v-model="query"
          type="text"
          placeholder="Search albums, artists…"
          class="search-input flex-1 bg-transparent text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
          @keyup.enter="onSearchEnter"
          @focus="emit('focus')"
          @blur="emit('blur')"
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
    <div v-if="artistLabel" :class="atTop ? 'shrink-0' : 'content-col px-4 pb-2 pt-2'">
      <div class="glass-field inline-flex items-center gap-1 rounded-full px-2 py-1.5">
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

  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import { useSettingsStore } from "../stores/settings";

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const settings = useSettingsStore();

/** True when the bar sits at the top of the library view (desktop) rather than
 *  in the floating bottom bar (mobile): flips the divider to the bottom edge. */
withDefaults(defineProps<{ atTop?: boolean }>(), { atTop: false });

/** Focus tracking lets the host swap the content under a focused top bar
 *  (desktop library shows search results while the field is focused). */
const emit = defineEmits<{ focus: []; blur: [] }>();

// --- Search field --------------------------------------------------------

const query = ref(lib.searchQuery);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

/** Applies the query to the live library filter (debounced while typing). */
function commitSearch(value: string): void {
  lib.searchQuery = value;
}

/** Records a committed search in the history — Enter only, never
 *  intermediate keystrokes (the debounced watcher just filters live). */
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

// The host clears the field when the top bar loses focus, so the normal
// library view reappears unfiltered.
defineExpose({ clearSearch });

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

</script>
