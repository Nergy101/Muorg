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
        <h1 class="truncate text-title-md text-on-surface">{{ title }}</h1>
        <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>

      <button
        v-if="tracks.length > 0"
        type="button"
        class="flex h-9 shrink-0 items-center gap-1 rounded-full px-3 text-label-lg text-primary transition-colors hover:bg-surface-container/70"
        @click="playAll"
      >
        <MageIcon name="play" class="h-4 w-4" />
        <span class="hidden sm:inline">Play</span>
      </button>
    </div>

    <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div
        v-if="tracks.length === 0"
        class="flex flex-col items-center gap-2 px-6 py-12 text-center"
      >
        <MageIcon name="check" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">
          {{ lib.tracks.length === 0 ? "The catalog is still empty." : "Nothing to report here." }}
        </span>
      </div>

      <template v-else>
        <!-- Duplicates read as groups, not a flat list: seeing the copies of
             one recording together is the whole point of the report. -->
        <template v-if="kind === 'duplicates'">
          <div v-for="(group, i) in groups" :key="i" class="pb-2">
            <p class="px-4 pb-1 pt-3 text-label-sm uppercase tracking-[0.8px] text-primary">
              {{ group.length }} copies
            </p>
            <TrackListRow
              v-for="track in group"
              :key="track.id"
              :track="track"
              :is-playing="player.currentTrack?.id === track.id"
              @play="player.playTrack(track, tracks)"
              @actions="sheetTrack = track"
            />
          </div>
        </template>

        <template v-else>
          <TrackListRow
            v-for="track in tracks"
            :key="track.id"
            :track="track"
            :is-playing="player.currentTrack?.id === track.id"
            @play="player.playTrack(track, tracks)"
            @actions="sheetTrack = track"
          />
        </template>
      </template>
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import {
  REPORT_KINDS,
  REPORT_LABELS,
  duplicateCount,
  duplicateGroups,
  runReport,
  type ReportKind,
} from "@shared/reports";
import type { CatalogTrack } from "../types";

const props = defineProps<{ kind: string }>();

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();

const sheetTrack = ref<CatalogTrack | null>(null);

/** An unknown `:kind` in the URL falls back rather than rendering nothing. */
const kind = computed<ReportKind>(() =>
  (REPORT_KINDS as readonly string[]).includes(props.kind)
    ? (props.kind as ReportKind)
    : "missing_metadata",
);

const title = computed(() => REPORT_LABELS[kind.value]);
const tracks = computed(() => runReport(kind.value, lib.tracks));
const groups = computed(() => duplicateGroups(lib.tracks));

const metaLine = computed(() => {
  if (kind.value === "duplicates") {
    const extra = duplicateCount(lib.tracks);
    return `${extra} redundant cop${extra === 1 ? "y" : "ies"} across ${groups.value.length} recording${groups.value.length === 1 ? "" : "s"}`;
  }
  const n = tracks.value.length;
  return `${n} track${n === 1 ? "" : "s"}`;
});

function playAll(): void {
  const first = tracks.value[0];
  if (first) player.playTrack(first, tracks.value);
}
</script>
