<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <!-- Search bar pinned at the TOP of the screen; results scroll between it
         and the bottom island/keyboard. On desktop with the rail collapsed,
         the bar moves into the collapsed top bar's right end instead (see the
         Teleport below). -->
    <div
      class="search-col shrink-0 px-4 pb-2 pt-3"
      :class="{ 'lg:hidden': collapsed && isDesktop }"
    >
      <div
        class="glass-field flex h-11 items-center gap-2 rounded-full px-4 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
      >
        <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <!-- Clear button overlays the input's right end, inside the field. -->
        <div class="relative min-w-0 flex-1">
          <input
            ref="inputEl"
            v-model="query"
            type="text"
            placeholder="Search"
            class="search-input w-full bg-transparent pr-8 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
            @keyup.enter="onSearchEnter"
          />
          <button
            v-if="query"
            type="button"
            class="absolute right-0 top-1/2 -translate-y-1/2 text-on-surface-variant"
            aria-label="Clear search"
            @click="clearSearch"
          >
            <MageIcon name="multiply" class="h-4 w-4" />
          </button>
        </div>
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

    <!-- Desktop with the rail collapsed: the search field (and layout toggle)
         move to the right end of the collapsed top bar. -->
    <Teleport
      v-if="collapsed && isDesktop && route.name === 'search' && collapsedNavTrailing"
      :to="collapsedNavTrailing"
    >
      <div class="flex items-center gap-1">
        <div
          class="glass-field flex h-10 w-56 items-center gap-2 rounded-full px-3 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
        >
          <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          <div class="relative min-w-0 flex-1">
            <input
              ref="railInputEl"
              v-model="query"
              type="text"
              placeholder="Search"
              class="search-input w-full bg-transparent pr-6 text-body-md text-on-surface outline-none placeholder:text-on-surface-variant"
              @keyup.enter="onSearchEnter"
            />
            <button
              v-if="query"
              type="button"
              class="absolute right-0 top-1/2 -translate-y-1/2 text-on-surface-variant"
              aria-label="Clear search"
              @click="clearSearch"
            >
              <MageIcon name="multiply" class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
        <button
          type="button"
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          :aria-label="layout === 'grid' ? 'Show results as a list' : 'Show results as album covers'"
          :title="layout === 'grid' ? 'Show results as a list' : 'Show results as album covers'"
          @click="toggleLayout"
        >
          <MageIcon :name="layout === 'grid' ? 'arrowlist' : 'layout-grid'" class="h-5 w-5" />
        </button>
      </div>
    </Teleport>
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
import { useIsDesktop } from "../composables/useIsDesktop";
import { sidebarCollapsed as collapsed, collapsedNavTrailing } from "../composables/sidebarCollapsed";

const route = useRoute();
const settings = useSettingsStore();
const isDesktop = useIsDesktop();

// Pre-filled when arriving from the home page's search bar (`/search?q=…`).
const query = ref(typeof route.query.q === "string" ? route.query.q : "");
/** Result layout: flat track list or album-cover grid, persisted. */
const layout = ref<"list" | "grid">(loadPref("muorg-web-search-layout", "list"));
watch(layout, (v) => savePref("muorg-web-search-layout", v));
function toggleLayout(): void {
  layout.value = layout.value === "grid" ? "list" : "grid";
}

const inputEl = ref<HTMLInputElement | null>(null);
/** The copy of the input teleported into the collapsed top bar (desktop). */
const railInputEl = ref<HTMLInputElement | null>(null);
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
  // Focus whichever copy of the input is visible: the rail's when the
  // collapsed top bar carries the search field, the view's own otherwise.
  const el = collapsed.value && isDesktop.value ? railInputEl.value : inputEl.value;
  el?.focus();
  el?.select();
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
