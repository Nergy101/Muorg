<template>
  <div>
    <div :class="[SECTION, 'flex items-center gap-1.5']">
      <MageIcon name="layout-grid" class="h-3.5 w-3.5" />
      <span>Tracks table</span>
    </div>
    <p class="px-4 pb-2 text-body-sm text-on-surface-variant">
      The dense, sortable table shown for the Library's Tracks layout on desktop.
    </p>

    <div class="px-4 py-2">
      <p class="pb-2 text-body-lg text-on-surface">Row density</p>
      <SegmentedControl
        :model-value="tableStore.density"
        :options="DENSITY_OPTIONS"
        @update:model-value="tableStore.setDensity($event as TableDensity)"
      />
    </div>

    <div class="px-4 py-2">
      <p class="pb-2 text-body-lg text-on-surface">Default group by</p>
      <SegmentedControl
        :model-value="tableStore.groupBy"
        :options="GROUP_OPTIONS"
        @update:model-value="tableStore.setGroupBy($event as TableGroupBy)"
      />
    </div>

    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Groups start expanded</p>
        <p class="text-body-sm text-on-surface-variant">Applies the next time a group-by is chosen</p>
      </div>
      <button
        type="button"
        :class="switchClass(tableStore.defaultGroupsExpanded)"
        role="switch"
        aria-label="Groups start expanded"
        :data-tip="`Groups start expanded is ${tableStore.defaultGroupsExpanded ? 'on' : 'off'} — click to turn ${tableStore.defaultGroupsExpanded ? 'off' : 'on'}`"
        :aria-checked="tableStore.defaultGroupsExpanded"
        @click="tableStore.setDefaultGroupsExpanded(!tableStore.defaultGroupsExpanded)"
      >
        <span :class="knobClass(tableStore.defaultGroupsExpanded)" />
      </button>
    </div>

    <div class="px-4 py-2">
      <p class="pb-2 text-body-lg text-on-surface">Columns</p>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="col in TABLE_COLUMNS"
          :key="col.id"
          type="button"
          class="rounded-full px-3 py-1.5 text-label-lg transition-colors"
          :class="
            tableStore.visibleColumns[col.id]
              ? 'bg-primary/[0.15] text-primary'
              : 'bg-surface-variant text-on-surface-variant'
          "
          :aria-pressed="tableStore.visibleColumns[col.id]"
          @click="tableStore.setColumnVisible(col.id, !tableStore.visibleColumns[col.id])"
        >
          {{ col.label }}
        </button>
      </div>
    </div>

    <button type="button" :class="[ROW, 'w-full text-left']" @click="tableStore.resetColumnWidths()">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Reset column widths</p>
        <p class="text-body-sm text-on-surface-variant">Restores every column to its default width</p>
      </div>
      <MageIcon name="reload" class="h-5 w-5 shrink-0 text-on-surface-variant" />
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * Desktop-class library table settings block, mounted inside SettingsView's
 * Library section: density, default group-by + whether new groups start
 * expanded, which columns show, and a reset for drag-resized widths. Sort
 * column/direction and the min-rating filter are left as in-context toolbar
 * controls (LibrarySearchBar, tracks layout) rather than settings — they are
 * filters a user flips often, not a one-time preference.
 */
import MageIcon from "../MageIcon.vue";
import SegmentedControl from "../SegmentedControl.vue";
import { useLibraryTableStore, TABLE_COLUMNS, type TableDensity } from "../../stores/libraryTable";
import type { TableGroupBy } from "../library/tableModel";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const DENSITY_OPTIONS: { value: TableDensity; label: string }[] = [
  { value: "compact", label: "Compact" },
  { value: "comfortable", label: "Comfortable" },
  { value: "spacious", label: "Spacious" },
];

const GROUP_OPTIONS: { value: TableGroupBy; label: string }[] = [
  { value: "none", label: "None" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
];

const tableStore = useLibraryTableStore();

function switchClass(on: boolean): string {
  return `flex h-8 w-[52px] shrink-0 items-center rounded-full px-1 transition-colors duration-200 ${
    on ? "bg-primary" : "bg-surface-variant"
  }`;
}

function knobClass(on: boolean): string {
  return `block h-6 w-6 rounded-full bg-white transition-transform duration-200 ${
    on ? "translate-x-5" : "translate-x-0"
  }`;
}
</script>
