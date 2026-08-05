<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-2 px-2">
      <button
        type="button"
        class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-6 w-6" />
      </button>
      <span class="flex-1 text-title-lg text-on-surface">Queue</span>
      <button
        type="button"
        class="px-2 text-label-lg text-primary"
        @click="player.clearQueue()"
      >Clear all</button>
    </div>

    <div class="content-col-children min-h-0 flex-1 overflow-y-auto">
      <template v-if="player.currentTrack">
        <div class="px-4 pb-1 pt-2 text-label-sm uppercase tracking-[0.8px] text-primary">
          Now playing
        </div>
        <div
          class="mx-4 flex items-center gap-3 rounded-xl border border-primary bg-primary/[0.18] p-3"
        >
          <div class="h-[52px] w-[52px] shrink-0 overflow-hidden rounded-md bg-surface-variant">
            <img
              v-if="currentCover"
              :src="currentCover"
              :alt="player.currentTrack.album ?? ''"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-5 w-5 text-on-surface-variant/60" />
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <MarqueeText
              :text="player.currentTrack.title ?? '—'"
              class="text-body-lg text-on-surface"
            />
            <p class="truncate text-body-sm text-on-surface-variant">{{ currentSubtitle }}</p>
          </div>
          <EqualizerBars class="text-primary" :paused="!player.isPlaying" />
        </div>
      </template>

      <div class="flex items-center justify-between px-4 pb-1 pt-4">
        <span class="text-label-sm uppercase tracking-[0.8px] text-on-surface-variant">
          Up next · {{ player.upNext.length }}
        </span>
        <button
          type="button"
          class="flex items-center gap-1"
          :class="player.shuffleEnabled ? 'text-primary' : 'text-on-surface-variant'"
          @click="player.toggleShuffle()"
        >
          <MageIcon name="exchange" class="h-4 w-4" />
          <span class="text-label-lg">Shuffle</span>
        </button>
      </div>

      <p
        v-if="player.upNext.length === 0"
        class="py-12 text-center text-body-md text-on-surface-variant"
      >
        Queue is empty
      </p>

      <div v-else class="pb-4">
        <QueueRow
          v-for="(entry, i) in player.upNext"
          :key="entry.track.id"
          :track="entry.track"
          :index="i"
          :class="rowClass(i)"
          :style="rowStyle(i)"
          @play="player.skipTo(entry.track)"
          @remove="player.removeFromQueue(entry.track)"
          @actions="sheetTrack = entry.track"
          @drag-start="drag.start(i, $event)"
        />
      </div>
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      :can-remove-from-queue="true"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
      @remove-from-queue="onRemoveFromQueue"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import MarqueeText from "../components/MarqueeText.vue";
import EqualizerBars from "../components/EqualizerBars.vue";
import QueueRow from "../components/QueueRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { REORDER_ROW_HEIGHT, useDragReorder } from "../composables/useDragReorder";
import { albumKeyFor, useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import type { CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();

const currentCover = computed(() => {
  const t = player.currentTrack;
  if (!t?.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const currentSubtitle = computed(() => {
  const t = player.currentTrack;
  if (!t) return "";
  const parts = [t.artist ?? t.album_artist, t.album].filter((s): s is string => !!s);
  return parts.join(" · ") || "—";
});

// --- Drag reorder (committed on drop, unlike the playlist screen) ----------

const drag = useDragReorder({
  itemCount: () => player.upNext.length,
  rowHeight: REORDER_ROW_HEIGHT,
  immediate: true,
  onCommit: (fromRow, toRow) => {
    // useDragReorder reports positions within the rendered up-next list;
    // reorderQueue expects positions within playOrder.
    const rows = player.upNext;
    const from = rows[fromRow]?.orderPos;
    const to = rows[toRow]?.orderPos;
    if (from == null || to == null) return;
    player.reorderQueue(from, to);
  },
});

function rowClass(i: number): string {
  return i === drag.draggingIndex.value
    ? "relative z-10 rounded-lg border border-primary"
    : "transition-transform duration-150";
}

function rowStyle(i: number): Record<string, string> {
  if (i === drag.draggingIndex.value) {
    return { transform: `translateY(${drag.offsetY.value}px)` };
  }
  const from = drag.draggingIndex.value;
  const to = drag.dropIndex.value;
  if (from === null || to === null || from === to) return {};
  if (from < to && i > from && i <= to) return { transform: `translateY(-${REORDER_ROW_HEIGHT}px)` };
  if (from > to && i < from && i >= to) return { transform: `translateY(${REORDER_ROW_HEIGHT}px)` };
  return {};
}

// --- Track actions sheet ---------------------------------------------------

const sheetTrack = ref<CatalogTrack | null>(null);

function onRemoveFromQueue(): void {
  const t = sheetTrack.value;
  if (t) player.removeFromQueue(t);
}

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({
    name: "library",
    query: { artist: t.artist ?? t.album_artist ?? undefined },
  });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: albumKeyFor(t) } });
}
</script>
