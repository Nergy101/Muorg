<script setup lang="ts">
/**
 * The settings modal shell: the tab rail, the scroll container, and open/close.
 *
 * Each tab is its own component under `settings/`. They reach for the stores
 * themselves rather than being handed props — the state is global, and threading
 * three dozen refs through here is what made this file 2,951 lines.
 */
import { nextTick, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { MissingMetadataField } from "../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import ConnectionPanel from "./settings/ConnectionPanel.vue";
import GeneralPanel from "./settings/GeneralPanel.vue";
import ThemePanel from "./settings/ThemePanel.vue";
import PlaybackPanel from "./settings/PlaybackPanel.vue";
import TablePanel from "./settings/TablePanel.vue";
import SmartSuggestionsPanel from "./settings/SmartSuggestionsPanel.vue";
import SmartTransformPanel from "./settings/SmartTransformPanel.vue";
import ReportsPanel from "./settings/ReportsPanel.vue";
import ExportsPanel from "./settings/ExportsPanel.vue";
import StatisticsPanel from "./settings/StatisticsPanel.vue";
import KeyboardPanel from "./settings/KeyboardPanel.vue";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ (e: "update:open", value: boolean): void }>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { openSettingsAtTab } = storeToRefs(settingsStore);

type SettingsTabId =
  | "general"
  | "theme"
  | "playback"
  | "table"
  | "keyboard"
  | "reports"
  | "exports"
  | "smart_suggestions"
  | "smart_transform"
  | "statistics"
  | "connection";

const settingsTab = ref<SettingsTabId>("general");
const settingsTabs: { id: SettingsTabId; label: string; icon: string }[] = [
  { id: "connection", label: "Connection", icon: "wifi" },
  { id: "general", label: "General", icon: "sliders" },
  { id: "theme", label: "Theme", icon: "sun" },
  { id: "playback", label: "Playback", icon: "play-circle" },
  { id: "table", label: "Layout", icon: "layout" },
  { id: "smart_suggestions", label: "Smart Suggestions", icon: "zap" },
  { id: "smart_transform", label: "Smart Transform", icon: "shuffle" },
  { id: "reports", label: "Reports", icon: "bar-chart-2" },
  { id: "exports", label: "Exports", icon: "download" },
  { id: "statistics", label: "Statistics", icon: "pie-chart" },
  { id: "keyboard", label: "Keyboard", icon: "command" },
];

/**
 * Jumping to a track's missing field means leaving the modal, so the Statistics
 * panel reports it up rather than closing itself.
 */
function handleViewField(field: MissingMetadataField) {
  store.setReportSingleField(field);
  emit("update:open", false);
}

function close() {
  emit("update:open", false);
}

function onSettingsKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") close();
}

const settingsModalRef = ref<HTMLDivElement | null>(null);
const settingsScrollRef = ref<HTMLElement | null>(null);
useOverlayScrollbars(settingsScrollRef);

watch(
  () => props.open,
  async (open) => {
    if (!open) return;
    await nextTick();
    settingsModalRef.value?.focus();
  },
);

// Another view can ask for a specific tab (the Reports sidebar does).
watch(openSettingsAtTab, (tab) => {
  if (!tab) return;
  settingsTab.value = tab as SettingsTabId;
  settingsStore.setOpenSettingsAtTab(null);
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      ref="settingsModalRef"
      class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-modal-title"
      tabindex="-1"
      @keydown="onSettingsKeydown"
      @click.self="close"
    >
      <div
        class="settings-modal flex h-[85vh] min-h-[450px] w-full max-w-5xl flex-col overflow-hidden rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
        @click.stop
      >
        <div
          class="flex shrink-0 items-center justify-between border-b border-stone-700 px-4 py-3"
        >
          <h2
            id="settings-modal-title"
            class="flex items-center gap-2 text-sm font-semibold text-stone-200"
          >
            <FeatherIcon
              name="settings"
              class="h-4 w-4 shrink-0 text-stone-400"
            />
            Settings
          </h2>
          <button
            type="button"
            class="icon-btn h-7 w-7 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Close"
            @click="close"
          >
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </div>

        <div class="flex min-h-0 flex-1">
          <nav
            class="settings-tab-nav w-36 shrink-0 border-r border-stone-700 bg-stone-800/90 py-2 flex flex-col gap-1"
            aria-label="Settings sections"
          >
            <template v-for="tab in settingsTabs" :key="tab.id">
              <div
                v-if="tab.id === 'general' || tab.id === 'statistics'"
                class="mx-3 my-2 border-t border-stone-700"
              />
              <button
                type="button"
                class="settings-tab-btn mx-2 flex w-[calc(100%-1rem)] items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-medium transition-colors"
                :class="settingsTab === tab.id ? 'settings-tab-btn--active' : undefined"
                @click="settingsTab = tab.id"
              >
                <FeatherIcon :name="tab.icon" class="h-3.5 w-3.5 shrink-0" />
                {{ tab.label }}
              </button>
            </template>
          </nav>

          <div ref="settingsScrollRef" class="min-h-0 min-w-0 flex-1 p-4">
          <GeneralPanel v-show="settingsTab === 'general'" />
          <ThemePanel v-show="settingsTab === 'theme'" />
          <PlaybackPanel v-show="settingsTab === 'playback'" />
          <TablePanel v-show="settingsTab === 'table'" />
          <KeyboardPanel v-show="settingsTab === 'keyboard'" />
          <ReportsPanel v-show="settingsTab === 'reports'" />
          <ExportsPanel v-show="settingsTab === 'exports'" />
          <SmartSuggestionsPanel v-show="settingsTab === 'smart_suggestions'" />
          <SmartTransformPanel v-show="settingsTab === 'smart_transform'" />
          <StatisticsPanel v-show="settingsTab === 'statistics'" @view-field="handleViewField" />
          <ConnectionPanel v-show="settingsTab === 'connection'" />

          </div>
        </div>
      </div>
    </div>
  </Teleport>

</template>

<style scoped>
.settings-tab-btn {
  color: rgb(156 163 175);
}
.settings-tab-btn:hover {
  background-color: rgba(250, 250, 249, 0.06);
  color: rgb(250 250 249);
}
.settings-tab-btn--active {
  background-color: rgba(91, 124, 50, 0.24);
  color: rgb(231 229 228);
}
.settings-tab-btn--active:hover {
  background-color: rgba(91, 124, 50, 0.28);
  color: rgb(231 229 228);
}

.settings-section {
  border: 1px solid rgb(68 64 60);
  background-color: rgba(23, 23, 23, 0.9);
  border-radius: 0.5rem;
  padding: 0.75rem 0.9rem;
}

/* Make settings text a bit lighter in dark theme for readability */
:global(html[data-theme="dark"] .settings-modal .text-stone-500),
:global(html[data-theme="dark"] .settings-modal .text-stone-400) {
  color: rgb(229 231 235);
}
/* Theme-aware settings cards */
::global(html[data-theme="light"] .settings-modal .settings-section) {
  border-color: #d6d3d1;
  background-color: #f5f5f4;
}

::global(html[data-theme="doom"] .settings-modal .settings-section) {
  border-color: #4a1515;
  background-color: #1a0505;
}

::global(html[data-theme="orkish"] .settings-modal .settings-section) {
  border-color: #c5e1a5;
  background-color: #dcedc8;
}

/* Glow demos: padding for blur to extend; overflow hidden clips to rounded box */
.glow-demo-container {
  overflow: hidden;
}
</style>
