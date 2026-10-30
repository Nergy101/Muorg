<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-5 w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">Statistics</h1>
        <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>

      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container/70 disabled:opacity-50"
        aria-label="Refresh statistics"
        :disabled="lib.loading"
        @click="refresh"
      >
        <MageIcon name="refresh" class="h-5 w-5" :class="lib.loading ? 'animate-spin' : ''" />
      </button>
    </div>

    <div class="content-col min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3 pb-[var(--bottom-inset)]">
      <div v-if="lib.loading && lib.tracks.length === 0" class="flex items-center justify-center py-12">
        <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <template v-else>
        <!-- Totals -->
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <div class="rounded-2xl bg-surface-container px-3 py-3 text-center">
            <p class="text-title-md tabular-nums text-on-surface">{{ trackCount.toLocaleString() }}</p>
            <p class="text-label-sm text-on-surface-variant">Tracks</p>
          </div>
          <div class="rounded-2xl bg-surface-container px-3 py-3 text-center">
            <p class="text-title-md tabular-nums text-on-surface">{{ artistCount.toLocaleString() }}</p>
            <p class="text-label-sm text-on-surface-variant">Artists</p>
          </div>
          <div class="rounded-2xl bg-surface-container px-3 py-3 text-center">
            <p class="text-title-md tabular-nums text-on-surface">{{ albumCount.toLocaleString() }}</p>
            <p class="text-label-sm text-on-surface-variant">Albums</p>
          </div>
          <div class="rounded-2xl bg-surface-container px-3 py-3 text-center">
            <p class="text-title-md tabular-nums text-on-surface">{{ totalDurationLabel }}</p>
            <p class="text-label-sm text-on-surface-variant">Total time</p>
          </div>
        </div>

        <!-- Metadata health -->
        <section class="rounded-2xl bg-surface-container p-4">
          <p class="mb-3 text-label-lg text-on-surface-variant">Metadata health</p>
          <MetadataHealthChart :tracks="lib.tracks" @view-field="onViewField" />
        </section>

        <!-- Genre distribution -->
        <section class="rounded-2xl bg-surface-container p-4">
          <p class="mb-3 text-label-lg text-on-surface-variant">Genre distribution</p>
          <GenrePieChart :tracks="lib.tracks" />
        </section>

        <!-- Top artists -->
        <section class="rounded-2xl bg-surface-container p-4">
          <p class="mb-3 text-label-lg text-on-surface-variant">Top artists by track count</p>
          <TopArtistsChart :tracks="lib.tracks" />
        </section>

        <!-- Release year -->
        <section class="rounded-2xl bg-surface-container p-4">
          <p class="mb-3 text-label-lg text-on-surface-variant">Tracks per release year</p>
          <YearLineChart :tracks="lib.tracks" />
        </section>

        <!-- Ratings -->
        <section class="rounded-2xl bg-surface-container p-4 pb-6">
          <p class="mb-3 text-label-lg text-on-surface-variant">User ratings</p>
          <RatingChart :tracks="lib.tracks" />
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import GenrePieChart from "@shared/components/stats/GenrePieChart.vue";
import YearLineChart from "@shared/components/stats/YearLineChart.vue";
import RatingChart from "@shared/components/stats/RatingChart.vue";
import TopArtistsChart from "@shared/components/stats/TopArtistsChart.vue";
import MetadataHealthChart from "@shared/components/stats/MetadataHealthChart.vue";
import type { MissingMetadataField } from "@shared/reports";

const router = useRouter();
const lib = useLibraryStore();

const metaLine = computed(() => {
  const n = lib.tracks.length;
  return `${n} track${n === 1 ? "" : "s"} in the catalog`;
});

// Prefer the server /api/stats totals; fall back to the loaded catalog so the
// counts always show even before the stats round-trip lands (or if it fails).
const trackCount = computed(() => lib.stats?.track_count ?? lib.tracks.length);
const albumCount = computed(() => {
  if (lib.stats != null) return lib.stats.album_count;
  return new Set(lib.tracks.map((t) => lib.keyForTrack(t))).size;
});
const artistCount = computed(() => {
  if (lib.stats != null) return lib.stats.artist_count;
  return new Set(lib.tracks.map((t) => t.album_artist ?? t.artist ?? "")).size;
});
const totalDurationLabel = computed(() => {
  const secs =
    lib.stats?.total_duration_secs ?? lib.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
});

async function refresh(): Promise<void> {
  await lib.loadLibrary();
}

/** Metadata Health card click: open the missing-metadata report scoped to
 *  that one field, without touching the app-wide field selection. */
function onViewField(field: MissingMetadataField): void {
  router.push({ name: "report", params: { kind: "missing_metadata" }, query: { field } });
}
</script>
