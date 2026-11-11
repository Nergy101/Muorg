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

    <div class="scrollbar-overlay min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <SearchResultsPanel
        ref="panel"
        v-model:query="query"
        v-model:layout="layout"
        @request-focus="focusInput"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { useRoute } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import SearchResultsPanel from "../components/SearchResultsPanel.vue";
import { useSettingsStore } from "../stores/settings";
import { loadPref, savePref } from "../stores/prefs";
import { searchFocusSignal } from "../composables/useSearchFocus";
import { provideVisibleIds } from "../composables/useSelectableList";

const route = useRoute();
const settings = useSettingsStore();

// Pre-filled when arriving from the home page's search bar (`/search?q=…`).
const query = ref(typeof route.query.q === "string" ? route.query.q : "");
/** Result layout: flat track list or album-cover grid, persisted. */
const layout = ref<"list" | "grid">(loadPref("muorg-web-search-layout", "list"));
watch(layout, (v) => savePref("muorg-web-search-layout", v));
function toggleLayout(): void {
  layout.value = layout.value === "grid" ? "list" : "grid";
}

const inputEl = ref<HTMLInputElement | null>(null);
const panel = ref<InstanceType<typeof SearchResultsPanel> | null>(null);

// Repeat visits from the home search bar carry a new `q` — adopt it.
watch(
  () => route.query.q,
  (q) => {
    if (typeof q === "string") query.value = q;
  },
);

// The results panel owns the visible-id list; register it for range selection.
provideVisibleIds(() => panel.value?.visibleIds ?? []);

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
</script>
