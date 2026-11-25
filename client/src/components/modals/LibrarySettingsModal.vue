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
import logoUrl from "../../assets/muorg-logo.svg";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { MissingMetadataField } from "../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import ConnectionPanel from "./settings/ConnectionPanel.vue";
import GeneralPanel from "./settings/GeneralPanel.vue";
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
/** Collapsible sections (web SettingsSection pattern); General starts open. */
const openSections = ref<Set<SettingsTabId>>(new Set(["general"]));
function toggleSection(id: SettingsTabId) {
  const next = new Set(openSections.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  openSections.value = next;
  settingsTab.value = id;
}
const settingsTabs: { id: SettingsTabId; label: string; icon: string }[] = [
  { id: "connection", label: "Connection", icon: "wifi" },
  { id: "general", label: "General", icon: "sliders" },
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
  const id = tab as SettingsTabId;
  settingsTab.value = id;
  const next = new Set(openSections.value);
  next.add(id);
  openSections.value = next;
  settingsStore.setOpenSettingsAtTab(null);
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      ref="settingsModalRef"
      class="fixed inset-0 z-[300] flex items-center justify-center bg-background/70 p-4 outline-none"
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-modal-title"
      tabindex="-1"
      @keydown="onSettingsKeydown"
      @click.self="close"
    >
      <div
        class="settings-modal flex h-[85vh] min-h-[450px] w-full max-w-5xl flex-col overflow-hidden glass-strong rounded-2xl shadow-xl"
        @click.stop
      >
        <div
          class="flex shrink-0 items-center justify-between border-b border-outline px-4 py-3"
        >
          <h2
            id="settings-modal-title"
            class="flex items-center gap-2 text-sm font-semibold text-on-surface"
          >
            <FeatherIcon
              name="settings"
              class="h-4 w-4 shrink-0 text-on-surface-variant"
            />
            Settings
          </h2>
          <button
            type="button"
            class="icon-btn h-7 w-7 text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
            aria-label="Close"
            @click="close"
          >
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </div>

        <!-- Web SettingsView pattern: one scrollable column of collapsible section cards -->
        <div ref="settingsScrollRef" class="min-h-0 min-w-0 flex-1 overflow-y-auto p-4">
          <div class="mx-auto flex max-w-2xl flex-col gap-3">
            <div class="flex justify-center pb-2 pt-2">
              <img :src="logoUrl" alt="Muorg" class="h-16 w-16" />
            </div>
            <div
              v-for="tab in settingsTabs"
              :key="tab.id"
              class="rounded-xl bg-surface-container/70 ring-1 ring-outline/10"
            >
              <button
                type="button"
                class="flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left transition-colors hover:bg-surface-container"
                :aria-expanded="openSections.has(tab.id)"
                @click="toggleSection(tab.id)"
              >
                <FeatherIcon :name="tab.icon" class="h-4 w-4 shrink-0 text-primary" />
                <span class="min-w-0 flex-1 text-body-lg font-semibold text-on-surface">{{ tab.label }}</span>
                <FeatherIcon
                  name="chevron-down"
                  class="h-4 w-4 shrink-0 text-on-surface-variant transition-transform"
                  :class="openSections.has(tab.id) ? 'rotate-180' : ''"
                />
              </button>
              <div v-show="openSections.has(tab.id)" class="border-t border-outline/15 px-4 py-3">
                <GeneralPanel v-if="tab.id === 'general'" />
                
                <PlaybackPanel v-else-if="tab.id === 'playback'" />
                <TablePanel v-else-if="tab.id === 'table'" />
                <KeyboardPanel v-else-if="tab.id === 'keyboard'" />
                <ReportsPanel v-else-if="tab.id === 'reports'" />
                <ExportsPanel v-else-if="tab.id === 'exports'" />
                <SmartSuggestionsPanel v-else-if="tab.id === 'smart_suggestions'" />
                <SmartTransformPanel v-else-if="tab.id === 'smart_transform'" />
                <StatisticsPanel v-else-if="tab.id === 'statistics'" @view-field="handleViewField" />
                <ConnectionPanel v-else-if="tab.id === 'connection'" />
              </div>
            </div>
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
:global(html[data-theme="dark"] .settings-modal .text-on-surface-variant),
:global(html[data-theme="dark"] .settings-modal .text-on-surface-variant) {
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
