<script setup lang="ts">
/** Layout tab: table columns, density, and how grouped rows render. */
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../../stores/catalog";
import { useSettingsStore } from "../../../stores/settings";
import type { DefaultGroupBy, TableDensity, BottomPanelId } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const {
  defaultGroupsExpanded,
  defaultGroupBy,
  tableDensity,
  tableColAlbumArt,
  tableColYear,
  tableColDuration,
  tableColFormat,
  tableColPath,
  tableColRating,
  groupHeaderAlbumArt,
  groupHeaderAlbumArtForArtist,
  splitAlbumHeadersByArtist,
  hideAlbumArtColInAlbumGroups,
  hideGroupTrackCount,
  defaultBottomPanel,
} = storeToRefs(settingsStore);

const defaultGroupByOptions: {
  value: DefaultGroupBy;
  label: string;
  description: string;
}[] = [
  { value: "album", label: "By album", description: "Group tracks by album." },
  {
    value: "artist",
    label: "By artist",
    description: "Group tracks by artist.",
  },
  { value: "none", label: "No grouping", description: "Flat list, no groups." },
];

const tableDensityOptions: {
  value: TableDensity;
  label: string;
  description: string;
}[] = [
  {
    value: "comfortable",
    label: "Comfortable",
    description: "More spacing between rows; easier to scan.",
  },
  {
    value: "compact",
    label: "Compact",
    description: "Tighter rows; more tracks visible at once.",
  },
  {
    value: "spacious",
    label: "Spacious",
    description: "Album headers show a large cover; relaxed layout.",
  },
];


const defaultBottomPanelOptions: {
  value: BottomPanelId;
  label: string;
  description: string;
}[] = [
  {
    value: "library",
    label: "Default",
    description: "Track list and grouping.",
  },
  {
    value: "metadata",
    label: "Metadata",
    description: "Edit tags and album art.",
  },
  { value: "player", label: "Player", description: "Now playing and controls." },
  { value: "queue", label: "Queue", description: "Up next and queue." },
];

function setDefaultGroupBy(value: DefaultGroupBy) {
  settingsStore.setDefaultGroupBy(value);
  // Mirror into the live table so the change is visible behind the modal.
  store.groupBy = value;
}

function setDefaultGroupsExpanded(value: boolean) {
  settingsStore.setDefaultGroupsExpanded(value);
}
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="layout"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Layout
              </p>

              <!-- 1: Main panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Main panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="layers"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Library grouping
                  </p>
                  <p class="mb-1.5 text-xs font-medium text-stone-500">
                    Default grouping
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in defaultGroupByOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        defaultGroupBy === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="setDefaultGroupBy(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-medium text-stone-200">
                          {{ opt.label }}
                          <span
                            v-if="defaultGroupBy === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-stone-500">
                          {{ opt.description }}
                        </p>
                      </div>
                    </button>
                  </div>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="defaultGroupsExpanded"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          setDefaultGroupsExpanded(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Expand groups by default
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    Controls how your library is grouped and whether groups
                    start expanded when you open Muorg.
                  </p>
                </div>

                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="grid"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Table density
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in tableDensityOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        tableDensity === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="settingsStore.setTableDensity(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-semibold text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="tableDensity === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-[11px] text-stone-400">
                          {{ opt.description }}
                        </p>
                      </div>
                    </button>
                  </div>
                </div>

                <div class="settings-section">
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="image"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Group header album art
                  </p>
                  <label
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="groupHeaderAlbumArt"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setGroupHeaderAlbumArt(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art in Album group headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="groupHeaderAlbumArtForArtist"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setGroupHeaderAlbumArtForArtist(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art in Artist group headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="splitAlbumHeadersByArtist"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setSplitAlbumHeadersByArtist(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Split album headers by artist
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When off, albums with the same name are merged into one group regardless of artist.
                  </p>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="hideGroupTrackCount"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideGroupTrackCount(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide track count in headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="hideAlbumArtColInAlbumGroups"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideAlbumArtColInAlbumGroups(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide album art column when grouped by Album
                  </label>
                </div>
                <div class="settings-section">
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="columns"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Table columns
                  </p>
                  <label
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColAlbumArt"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColAlbumArt(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColRating"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColRating(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show rating
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColYear"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColYear(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show year
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColDuration"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColDuration(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show duration
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColFormat"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColFormat(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show file format
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColPath"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColPath(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show file path
                  </label>
                </div>
              </div>

              <!-- 2: Bottom panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Bottom panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="layout"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Bottom bar
                  </p>
                  <p class="mb-2 text-xs text-stone-500">
                    Default tab on startup
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in defaultBottomPanelOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        defaultBottomPanel === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="settingsStore.setDefaultBottomPanel(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-semibold text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="defaultBottomPanel === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-[11px] text-stone-400">
                          {{ opt.description }}
                        </p>
                      </div>
                    </button>
                  </div>
                </div>
              </div>
              <!-- 3: Side panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Side panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="sidebar"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Sidebar
                  </p>
                  <p class="mb-1 text-xs font-medium text-stone-500">
                    Default sidebar panel
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in [
                        { value: 'folders', label: 'Folders', desc: 'Show library folders by default.' },
                        { value: 'reports', label: 'Reports', desc: 'Show reports by default.' },
                        { value: 'playlists', label: 'Playlists', desc: 'Show playlists by default.' },
                      ]"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,10rem)] items-center justify-between rounded-lg border px-3 py-1.5 text-left text-xs transition"
                      :class="
                        settingsStore.sidebarDefaultTab === opt.value
                          ? 'settings-option-card--active shadow-inner text-stone-100'
                          : 'border-stone-600 bg-stone-900/60 text-stone-300 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="
                        settingsStore.setSidebarDefaultTab(
                          opt.value as 'folders' | 'reports' | 'playlists',
                        )
                      "
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-medium text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="settingsStore.sidebarDefaultTab === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-[11px] text-stone-400">{{ opt.desc }}</p>
                      </div>
                    </button>
                  </div>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="settingsStore.sidebarClosedOnStartup"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setSidebarClosedOnStartup(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Start with sidebar closed
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When enabled, the library sidebar is collapsed when you open
                    Muorg.
                  </p>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="settingsStore.hideReportsSection"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideReportsSection(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide reports section in sidebar
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When enabled, the reports block (Missing metadata,
                    Duplicates, Missing album cover) is hidden from the main
                    sidebar layout.
                  </p>
                </div>
              </div>
            </div>

</template>
