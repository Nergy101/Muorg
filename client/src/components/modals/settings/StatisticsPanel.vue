<script setup lang="ts">
/**
 * Statistics tab: charts over the whole library.
 *
 * `view-field` bubbles up because acting on it means closing the modal and
 * filtering the table behind it, which is the parent's business.
 */
import { useCatalogStore } from "../../../stores/catalog";
import type { MissingMetadataField } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import GenrePieChart from "@shared/components/stats/GenrePieChart.vue";
import YearLineChart from "@shared/components/stats/YearLineChart.vue";
import RatingChart from "@shared/components/stats/RatingChart.vue";
import TopArtistsChart from "../../stats/TopArtistsChart.vue";
import MetadataHealthChart from "../../stats/MetadataHealthChart.vue";

const store = useCatalogStore();

const emit = defineEmits<{ (e: "view-field", field: MissingMetadataField): void }>();

function handleViewField(field: MissingMetadataField) {
  emit("view-field", field);
}
</script>

<template>
          <div class="space-y-6">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400">
              <FeatherIcon name="pie-chart" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Library Statistics
            </p>
            <p class="text-xs text-stone-500">
              Insights based on all {{ store.tracks.length }} tracks in your library.
            </p>

            <!-- Metadata health -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">Metadata completeness</p>
              <MetadataHealthChart :tracks="store.tracks" @view-field="handleViewField" />
            </div>

            <!-- Genre distribution -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-4 text-xs font-semibold text-stone-400">Genre distribution</p>
              <GenrePieChart :tracks="store.tracks" />
            </div>

            <!-- Top artists -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-4 text-xs font-semibold text-stone-400">Top artists by track count</p>
              <TopArtistsChart :tracks="store.tracks" />
            </div>

            <!-- Release year line chart -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">Tracks per release year</p>
              <YearLineChart :tracks="store.tracks" />
            </div>

            <!-- User ratings -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">User ratings</p>
              <RatingChart :tracks="store.tracks" />
            </div>
          </div>

          <!-- Connection tab -->
</template>
