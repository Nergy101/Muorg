<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-150"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
        @click.self="emit('close')"
      >
        <div class="flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-stone-700 bg-stone-900 shadow-2xl">
          <!-- Header -->
          <div class="flex shrink-0 items-center justify-between border-b border-stone-700 px-5 py-3">
            <h2 class="text-sm font-semibold text-stone-100">Library Statistics</h2>
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
              @click="emit('close')"
            >
              <FeatherIcon name="x" class="h-4 w-4" />
            </button>
          </div>

          <!-- Content -->
          <div class="flex-1 overflow-y-auto p-5 space-y-6">
            <!-- Totals grid -->
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <div class="rounded bg-stone-800 px-3 py-3 text-center">
                <div class="text-xl font-semibold tabular-nums text-stone-100">{{ stats.track_count.toLocaleString() }}</div>
                <div class="text-[10px] text-stone-400">tracks</div>
              </div>
              <div class="rounded bg-stone-800 px-3 py-3 text-center">
                <div class="text-xl font-semibold tabular-nums text-stone-100">{{ stats.artist_count.toLocaleString() }}</div>
                <div class="text-[10px] text-stone-400">artists</div>
              </div>
              <div class="rounded bg-stone-800 px-3 py-3 text-center">
                <div class="text-xl font-semibold tabular-nums text-stone-100">{{ stats.album_count.toLocaleString() }}</div>
                <div class="text-[10px] text-stone-400">albums</div>
              </div>
              <div class="rounded bg-stone-800 px-3 py-3 text-center">
                <div class="text-base font-semibold tabular-nums text-stone-100">{{ formatDuration(stats.total_duration_secs) }}</div>
                <div class="text-[10px] text-stone-400">total time</div>
              </div>
            </div>

            <template v-if="lib.tracks.length > 0">
              <!-- Genre breakdown -->
              <div>
                <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">Genre</p>
                <GenrePieChart :tracks="lib.tracks" />
              </div>

              <!-- Year histogram -->
              <div>
                <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">By year</p>
                <YearLineChart :tracks="lib.tracks" />
              </div>

              <!-- Rating distribution -->
              <div>
                <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">Ratings</p>
                <RatingChart :tracks="lib.tracks" />
              </div>
            </template>
            <div v-else class="py-6 text-center text-xs text-stone-500">No tracks in library yet.</div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import GenrePieChart from "@shared/components/stats/GenrePieChart.vue";
import YearLineChart from "@shared/components/stats/YearLineChart.vue";
import RatingChart from "@shared/components/stats/RatingChart.vue";
import { useLibraryStore } from "../stores/library";

defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const lib = useLibraryStore();

const stats = computed(() => lib.stats ?? {
  track_count: lib.tracks.length,
  artist_count: new Set(lib.tracks.map(t => t.artist ?? "").filter(Boolean)).size,
  album_count: new Set(lib.tracks.map(t => t.album ?? "").filter(Boolean)).size,
  total_duration_secs: lib.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0),
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
