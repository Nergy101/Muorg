<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useCatalogStore } from "../stores/catalog";
import { REPORT_KINDS, REPORT_LABELS, reportCount } from "@shared/reports";
import { navigate } from "../state/navigation";

const store = useCatalogStore();
const { tracks } = storeToRefs(store);

const cards = computed(() =>
  REPORT_KINDS.map((kind) => ({
    kind,
    label: REPORT_LABELS[kind],
    count: reportCount(kind, tracks.value),
  })),
);

const icons: Record<string, string> = {
  missing_metadata: "alert-circle",
  duplicates: "copy",
  missing_album_cover: "image",
  recently_played: "clock",
  most_played: "trending-up",
};

function open(kind: string) {
  store.setReportFilter(kind as never);
  navigate({ name: "list" });
}
</script>

<template>
  <div class="app-main-scroll">
    <div class="view-pad">
      <div class="mb-4 flex items-baseline gap-3">
        <h1 class="view-title">Reports</h1>
        <span class="view-subtitle">Library health at a glance</span>
      </div>
      <div class="shelf-grid" style="grid-template-columns: repeat(auto-fill, minmax(230px, 1fr))">
        <button
          v-for="card in cards"
          :key="card.kind"
          type="button"
          class="album-card flex items-center gap-3 !text-left"
          @click="open(card.kind)"
        >
          <span
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
            :style="{ background: 'color-mix(in srgb, var(--primary) 18%, transparent)', color: 'var(--on-primary-container)' }"
          >
            <FeatherIcon :name="icons[card.kind] ?? 'file-text'" class="h-5 w-5" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-[13px] font-semibold text-on-surface">{{ card.label }}</span>
            <span class="block text-[11.5px] text-on-surface-variant">
              <b class="text-on-primary-container">{{ card.count }}</b> tracks
            </span>
          </span>
          <FeatherIcon name="chevron-right" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        </button>
      </div>
    </div>
  </div>
</template>
