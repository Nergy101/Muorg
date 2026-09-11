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
        <h1 class="truncate text-title-md text-on-surface">Reports</h1>
        <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>

      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container/70 disabled:opacity-50"
        aria-label="Refresh reports"
        :disabled="lib.loading"
        @click="refresh"
      >
        <MageIcon name="refresh" class="h-5 w-5" :class="lib.loading ? 'animate-spin' : ''" />
      </button>
    </div>

    <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div v-if="lib.loading && lib.tracks.length === 0" class="flex items-center justify-center py-12">
        <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <template v-else>
        <RouterLink
          v-for="report in REPORTS"
          :key="report.kind"
          :to="{ name: 'report', params: { kind: report.kind } }"
          class="flex min-h-14 items-center gap-3 px-4 py-2 transition-colors hover:bg-surface-container/50"
        >
          <MageIcon :name="report.icon" class="h-5 w-5 shrink-0" :class="report.tint" />
          <div class="min-w-0 flex-1">
            <p class="text-body-lg text-on-surface">{{ REPORT_LABELS[report.kind] }}</p>
            <p class="text-body-sm text-on-surface-variant">{{ report.hint }}</p>
          </div>
          <span
            class="shrink-0 rounded-full px-2 py-0.5 text-label-sm tabular-nums"
            :class="counts[report.kind] > 0
              ? 'bg-primary/[0.15] text-primary'
              : 'text-on-surface-variant/70'"
          >{{ counts[report.kind] }}</span>
          <MageIcon name="chevron-right" class="h-4 w-4 shrink-0 text-on-surface-variant/60" />
        </RouterLink>

        <p class="px-4 pb-6 pt-4 text-body-sm text-on-surface-variant/70">
          Reports are computed from the catalog this app has loaded, so they
          cover the whole library rather than the current search.
        </p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import { REPORT_LABELS, reportCounts, type ReportKind } from "@shared/reports";

const router = useRouter();
const lib = useLibraryStore();

/** Presentation for each report; the definitions live in `@shared/reports`. */
const REPORTS: { kind: ReportKind; icon: string; tint: string; hint: string }[] = [
  {
    kind: "missing_metadata",
    icon: "note-text",
    tint: "text-error",
    hint: "No title, artist or album",
  },
  {
    kind: "duplicates",
    icon: "stack",
    tint: "text-error",
    hint: "The same recording filed more than once",
  },
  {
    kind: "missing_album_cover",
    icon: "compact-disk",
    tint: "text-on-surface-variant",
    hint: "No embedded artwork",
  },
  { kind: "recently_played", icon: "clock", tint: "text-tertiary", hint: "Newest first" },
  {
    kind: "most_played",
    icon: "chart-up",
    tint: "text-primary",
    hint: "By play count",
  },
];

const counts = computed(() => reportCounts(lib.tracks));

const metaLine = computed(() => {
  const total = lib.tracks.length;
  return `${total} track${total === 1 ? "" : "s"} in the catalog`;
});

async function refresh(): Promise<void> {
  await lib.loadLibrary();
}
</script>
