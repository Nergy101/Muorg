import { defineStore } from "pinia";
import { ref } from "vue";
import { loadPref, savePref } from "./prefs";
import type { SortDirection, TableGroupBy, TableSortColumn } from "../components/library/tableModel";

export type TableDensity = "compact" | "comfortable" | "spacious";
export type TableColumnId = "art" | "title" | "artist" | "album" | "year" | "duration" | "rating" | "format" | "path";

/** Display order and sortability of every library table column. */
export const TABLE_COLUMNS: { id: TableColumnId; label: string; sortable: boolean }[] = [
  { id: "art", label: "Art", sortable: false },
  { id: "title", label: "Title", sortable: true },
  { id: "artist", label: "Artist", sortable: true },
  { id: "album", label: "Album", sortable: true },
  { id: "year", label: "Year", sortable: true },
  { id: "duration", label: "Duration", sortable: true },
  { id: "rating", label: "Rating", sortable: true },
  { id: "format", label: "Format", sortable: true },
  { id: "path", label: "Path", sortable: false },
];

const DEFAULT_VISIBLE: Record<TableColumnId, boolean> = {
  art: true,
  title: true,
  artist: true,
  album: true,
  year: false,
  duration: false,
  rating: false,
  format: false,
  path: false,
};

const DEFAULT_WIDTHS: Record<TableColumnId, number> = {
  art: 56,
  title: 280,
  artist: 180,
  album: 180,
  year: 72,
  duration: 84,
  rating: 120,
  format: 76,
  path: 280,
};

const MIN_COL_WIDTH = 48;
const MAX_COL_WIDTH = 800;

export const useLibraryTableStore = defineStore("libraryTable", () => {
  const visibleColumns = ref<Record<TableColumnId, boolean>>({
    ...DEFAULT_VISIBLE,
    ...loadPref<Partial<Record<TableColumnId, boolean>>>("muorg-web-table-columns", {}),
  });
  const columnWidths = ref<Record<TableColumnId, number>>({
    ...DEFAULT_WIDTHS,
    ...loadPref<Partial<Record<TableColumnId, number>>>("muorg-web-table-widths", {}),
  });
  const density = ref<TableDensity>(loadPref<TableDensity>("muorg-web-table-density", "comfortable"));
  const groupBy = ref<TableGroupBy>(loadPref<TableGroupBy>("muorg-web-table-group-by", "none"));
  /** Whether newly shown groups start expanded; actual per-group expand state is component-local. */
  const defaultGroupsExpanded = ref(loadPref("muorg-web-table-groups-expanded-default", false));
  const sortColumn = ref<TableSortColumn | null>(
    loadPref<TableSortColumn | null>("muorg-web-table-sort-col", null),
  );
  const sortDirection = ref<SortDirection>(loadPref<SortDirection>("muorg-web-table-sort-dir", "asc"));
  /** 0 = no filter; 1-5 = minimum star rating shown. */
  const minRating = ref(loadPref("muorg-web-table-min-rating", 0));

  function setColumnVisible(id: TableColumnId, visible: boolean): void {
    visibleColumns.value = { ...visibleColumns.value, [id]: visible };
    savePref("muorg-web-table-columns", visibleColumns.value);
  }

  function setColumnWidth(id: TableColumnId, width: number): void {
    const clamped = Math.round(Math.min(MAX_COL_WIDTH, Math.max(MIN_COL_WIDTH, width)));
    columnWidths.value = { ...columnWidths.value, [id]: clamped };
    savePref("muorg-web-table-widths", columnWidths.value);
  }

  function resetColumnWidths(): void {
    columnWidths.value = { ...DEFAULT_WIDTHS };
    savePref("muorg-web-table-widths", columnWidths.value);
  }

  function setDensity(d: TableDensity): void {
    density.value = d;
    savePref("muorg-web-table-density", d);
  }

  function setGroupBy(g: TableGroupBy): void {
    groupBy.value = g;
    savePref("muorg-web-table-group-by", g);
  }

  function setDefaultGroupsExpanded(v: boolean): void {
    defaultGroupsExpanded.value = v;
    savePref("muorg-web-table-groups-expanded-default", v);
  }

  function setSort(column: TableSortColumn | null, direction: SortDirection): void {
    sortColumn.value = column;
    sortDirection.value = direction;
    savePref("muorg-web-table-sort-col", column);
    savePref("muorg-web-table-sort-dir", direction);
  }

  function setMinRating(v: number): void {
    const clamped = Math.max(0, Math.min(5, Math.round(v)));
    minRating.value = clamped;
    savePref("muorg-web-table-min-rating", clamped);
  }

  return {
    visibleColumns,
    columnWidths,
    density,
    groupBy,
    defaultGroupsExpanded,
    sortColumn,
    sortDirection,
    minRating,
    setColumnVisible,
    setColumnWidth,
    resetColumnWidths,
    setDensity,
    setGroupBy,
    setDefaultGroupsExpanded,
    setSort,
    setMinRating,
  };
});
