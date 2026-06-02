<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import * as catalogApi from "../../api/catalog";
import type { LibraryStats } from "../../types";
import GenrePieChart from "@shared/components/stats/GenrePieChart.vue";
import YearLineChart from "@shared/components/stats/YearLineChart.vue";
import RatingChart from "@shared/components/stats/RatingChart.vue";

const store = useCatalogStore();
const { tracks } = storeToRefs(store);

function statsFromTracks(): LibraryStats {
  return {
    track_count: tracks.value.length,
    artist_count: new Set(tracks.value.map((t) => (t.artist ?? "").toLowerCase().trim()).filter(Boolean)).size,
    album_count: new Set(tracks.value.map((t) => `${(t.album ?? "").toLowerCase()}|||${(t.album_artist ?? "").toLowerCase()}`).filter((k) => !k.startsWith("|||"))).size,
    total_duration_secs: tracks.value.reduce((s, t) => s + (t.duration_secs ?? 0), 0),
  };
}

// Pre-initialize so the panel is visible immediately — no flicker while the API call is in-flight.
const stats = ref<LibraryStats>(statsFromTracks());

onMounted(async () => {
  try {
    const s = await catalogApi.getStats();
    stats.value = s;
  } catch {
    stats.value = statsFromTracks();
  }
});

// When tracks finish loading from a remote server (after mount), keep the grid numbers in sync.
watch(tracks, () => {
  if (tracks.value.length > stats.value.track_count) {
    stats.value = statsFromTracks();
  }
});

function formatDuration(secs: number): string {
  if (!secs) return "0 min";
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  if (h >= 24) {
    const d = Math.floor(h / 24);
    const rh = h % 24;
    return rh > 0 ? `${d}d ${rh}h` : `${d}d`;
  }
  return h > 0 ? `${h}h ${m}m` : `${m} min`;
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-xs font-semibold uppercase tracking-wide text-stone-500">Library stats</p>

    <!-- Totals grid -->
    <div class="grid grid-cols-2 gap-2">
      <div class="rounded bg-stone-700/60 px-3 py-2 text-center">
        <div class="text-lg font-semibold tabular-nums text-stone-100">{{ stats.track_count.toLocaleString() }}</div>
        <div class="text-[10px] text-stone-400">tracks</div>
      </div>
      <div class="rounded bg-stone-700/60 px-3 py-2 text-center">
        <div class="text-lg font-semibold tabular-nums text-stone-100">{{ stats.artist_count.toLocaleString() }}</div>
        <div class="text-[10px] text-stone-400">artists</div>
      </div>
      <div class="rounded bg-stone-700/60 px-3 py-2 text-center">
        <div class="text-lg font-semibold tabular-nums text-stone-100">{{ stats.album_count.toLocaleString() }}</div>
        <div class="text-[10px] text-stone-400">albums</div>
      </div>
      <div class="rounded bg-stone-700/60 px-3 py-2 text-center">
        <div class="text-base font-semibold tabular-nums text-stone-100">{{ formatDuration(stats.total_duration_secs) }}</div>
        <div class="text-[10px] text-stone-400">total time</div>
      </div>
    </div>

    <template v-if="tracks.length > 0">
      <!-- Genre breakdown -->
      <div>
        <p class="mb-2 text-[10px] font-semibold uppercase tracking-wide text-stone-500">Genre</p>
        <GenrePieChart :tracks="tracks" />
      </div>

      <!-- Year histogram -->
      <div>
        <p class="mb-2 text-[10px] font-semibold uppercase tracking-wide text-stone-500">By year</p>
        <YearLineChart :tracks="tracks" />
      </div>

      <!-- Rating distribution -->
      <div>
        <p class="mb-2 text-[10px] font-semibold uppercase tracking-wide text-stone-500">Ratings</p>
        <RatingChart :tracks="tracks" />
      </div>
    </template>

    <div v-else class="py-6 text-center text-xs text-stone-500">
      No tracks in library yet.
    </div>
  </div>
</template>
