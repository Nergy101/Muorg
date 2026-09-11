<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import TooltipPopover from "../shared/TooltipPopover.vue";
import { reportCounts } from "@shared/reports";
import { useTooltipPopover } from "../../composables/useTooltipPopover";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { tracks, loading, reportFilter } = storeToRefs(store);
const { missingMetadataFields } = storeToRefs(settingsStore);

// ── Tooltip ────────────────────────────────────────────────────────────────

const {
  tooltip: tooltipPopover,
  show: showTooltip,
  scheduleHide: scheduleHideTooltip,
  cancelHide: cancelHideTooltip,
  hide: hideTooltip,
  styleFor: tooltipStyle,
} = useTooltipPopover();

// ── Data ───────────────────────────────────────────────────────────────────

// Counts come from the shared report module, which the web client and the
// Android app run the same definitions from — a duplicate is the same thing
// on every platform, or the number a user sees moves when they switch device.
const counts = computed(() => reportCounts(tracks.value, missingMetadataFields.value));

const missingMetadataCount = computed(() => counts.value.missing_metadata);
const duplicateCount = computed(() => counts.value.duplicates);
const missingAlbumCoverCount = computed(() => counts.value.missing_album_cover);
const recentlyPlayedCount = computed(() => counts.value.recently_played);
const mostPlayedCount = computed(() => counts.value.most_played);

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

function openRecentlyPlayedReport() {
  const kind = reportFilter.value === "recently_played" ? null : "recently_played";
  store.setReportFilter(kind);
  if (store.currentPlayingTrackId === null) store.clearSelection();
}

function openMostPlayedReport() {
  const kind = reportFilter.value === "most_played" ? null : "most_played";
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
      <button
        type="button"
        class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
        :class="reportFilter === 'recently_played' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
        @click="openRecentlyPlayedReport"
      >
        <span class="flex items-center gap-1.5">
          <FeatherIcon name="clock" class="h-3.5 w-3.5 shrink-0 text-blue-400" />
          <span>Recently played</span>
        </span>
        <span class="text-[0.7rem] text-stone-400">{{ recentlyPlayedCount }}</span>
      </button>
      <button
        type="button"
        class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
        :class="reportFilter === 'most_played' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
        @click="openMostPlayedReport"
      >
        <span class="flex items-center gap-1.5">
          <FeatherIcon name="trending-up" class="h-3.5 w-3.5 shrink-0 text-green-400" />
          <span>Most played</span>
        </span>
        <span class="text-[0.7rem] text-stone-400">{{ mostPlayedCount }}</span>
      </button>
    </div>
  </div>

  <TooltipPopover
    :state="tooltipPopover"
    :style-for="tooltipStyle"
    @enter="cancelHideTooltip"
    @leave="hideTooltip"
  />
</template>
