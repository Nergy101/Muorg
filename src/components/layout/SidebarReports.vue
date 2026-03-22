<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import FeatherIcon from "../shared/FeatherIcon.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { tracks, loading, reportFilter } = storeToRefs(store);
const { missingMetadataFields } = storeToRefs(settingsStore);

// ── Tooltip ────────────────────────────────────────────────────────────────

const tooltipPopover = ref<{
  text: string;
  x: number;
  y: number;
  position: "right" | "below";
} | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent) {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => { tooltipPopover.value = null; tooltipHideTimeout = null; }, 100);
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

// ── Data ───────────────────────────────────────────────────────────────────

function isFieldMissing(
  track: import("../../types").CatalogTrack,
  field: import("../../stores/settings").MissingMetadataField,
): boolean {
  if (field === "has_cover") return !track.has_cover;
  if (field === "rating") return track.rating == null;
  const v = track[field as keyof typeof track];
  if (field === "year" || field === "track_number" || field === "disc_number") {
    return v == null;
  }
  return v == null || String(v).trim() === "";
}

const missingMetadataCount = computed(() => {
  const fields = missingMetadataFields.value;
  if (!fields.length) return 0;
  return tracks.value.filter((t) => fields.some((f) => isFieldMissing(t, f))).length;
});

const duplicateCount = computed(() => {
  const list = tracks.value;
  if (!list.length) return 0;
  const keyFor = (t: import("../../types").CatalogTrack) =>
    `${(t.artist ?? "").toLowerCase()}|${(t.album ?? "").toLowerCase()}|${(t.title ?? "").toLowerCase()}`;
  const map = new Map<string, number>();
  for (const t of list) {
    const key = keyFor(t);
    if (!key.trim()) continue;
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  let total = 0;
  for (const count of map.values()) {
    if (count > 1) total += count - 1;
  }
  return total;
});

const missingAlbumCoverCount = computed(() =>
  tracks.value.filter((t) => !(t.has_cover ?? false)).length,
);

// ── Actions ────────────────────────────────────────────────────────────────

function openMissingMetadataReport() {
  const kind = reportFilter.value === "missing_metadata" ? null : "missing_metadata";
  store.setReportFilter(kind);
  if (store.currentPlayingTrackId === null) store.clearSelection();
}

function openDuplicateReport() {
  const kind = reportFilter.value === "duplicates" ? null : "duplicates";
  store.setReportFilter(kind);
  if (store.currentPlayingTrackId === null) store.clearSelection();
}

function openMissingAlbumCoverReport() {
  const kind = reportFilter.value === "missing_album_cover" ? null : "missing_album_cover";
  store.setReportFilter(kind);
  if (store.currentPlayingTrackId === null) store.clearSelection();
}

async function handleRefreshReports() {
  try {
    await store.loadTracks();
  } catch { /* error shown in store */ }
}
</script>

<template>
  <div>
    <div class="mb-1 flex items-center justify-between">
      <p class="text-xs font-semibold uppercase tracking-wide text-stone-500">Reports</p>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Refresh reports', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-50"
          aria-label="Refresh reports"
          :disabled="loading"
          @click="handleRefreshReports"
        >
          <FeatherIcon name="refresh-cw" class="h-3.5 w-3.5" />
        </button>
      </span>
    </div>
    <div class="space-y-1 text-xs">
      <button
        type="button"
        class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
        :class="reportFilter === 'missing_metadata' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
        @click="openMissingMetadataReport"
      >
        <span class="flex items-center gap-1.5">
          <FeatherIcon name="file-text" class="h-3.5 w-3.5 shrink-0 text-amber-300" />
          <span>Missing metadata</span>
        </span>
        <span class="text-[0.7rem] text-stone-400">{{ missingMetadataCount }}</span>
      </button>
      <button
        type="button"
        class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
        :class="reportFilter === 'duplicates' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
        @click="openDuplicateReport"
      >
        <span class="flex items-center gap-1.5">
          <FeatherIcon name="copy" class="h-3.5 w-3.5 shrink-0 text-red-300" />
          <span>Duplicates</span>
        </span>
        <span class="text-[0.7rem] text-stone-400">{{ duplicateCount }}</span>
      </button>
      <button
        type="button"
        class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
        :class="reportFilter === 'missing_album_cover' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
        @click="openMissingAlbumCoverReport"
      >
        <span class="flex items-center gap-1.5">
          <FeatherIcon name="image" class="h-3.5 w-3.5 shrink-0 text-stone-400" />
          <span>Missing album cover</span>
        </span>
        <span class="text-[0.7rem] text-stone-400">{{ missingAlbumCoverCount }}</span>
      </button>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
      :style="{ left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
</template>
