<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden">
    <div class="content-col flex h-14 shrink-0 items-center gap-2 px-2">
      <button
        type="button"
        class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface transition-colors lg:h-9 lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-6 w-6 lg:h-5 lg:w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>
      <MageIcon name="stack" class="h-5 w-5 text-primary" />
      <span class="flex-1 text-title-lg text-on-surface">Queue</span>
      <button
        type="button"
        class="px-2 text-label-lg text-primary"
        @click="player.clearQueue()"
      >Clear all</button>
    </div>

    <div
      ref="scroller"
      class="content-col-children min-h-0 flex-1 overflow-y-auto"
      :class="safeBottom ? 'pb-[max(env(safe-area-inset-bottom),1.5rem)]' : 'pb-6'"
    >
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

      <template v-if="userUpNext.length > 0">
        <div class="flex items-center justify-between px-4 pb-1 pt-4">
          <span class="text-label-sm uppercase tracking-[0.8px] text-primary">
            Your queue · {{ userUpNext.length }}
          </span>
        </div>
        <div>
          <QueueRow
            v-for="(entry, i) in userUpNext"
            :key="`user-${entry.track.id}`"
            :track="entry.track"
            :index="i"
            :class="rowClassFor(userDrag, i)"
            :style="rowStyleFor(userDrag, i)"
            @play="player.skipTo(entry.track)"
            @remove="player.removeFromQueue(entry.track)"
            @actions="sheetTrack = entry.track"
            @drag-start="userDrag.start(i, $event)"
          />
        </div>
      </template>

      <template v-if="systemUpNext.length > 0 || player.upNext.length === 0">
        <div class="flex items-center justify-between px-4 pb-1 pt-4">
          <span class="text-label-sm uppercase tracking-[0.8px] text-on-surface-variant">
            Up next · {{ systemUpNext.length }}
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

        <div v-else>
          <QueueRow
            v-for="(entry, i) in systemUpNext"
            :key="`system-${entry.track.id}`"
            :track="entry.track"
            :index="i"
            :class="rowClassFor(drag, i)"
            :style="rowStyleFor(drag, i)"
            @play="player.skipTo(entry.track)"
            @remove="player.removeFromQueue(entry.track)"
            @actions="sheetTrack = entry.track"
            @drag-start="drag.start(i, $event)"
          />
        </div>
      </template>
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
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import EqualizerBars from "./EqualizerBars.vue";
import QueueRow from "./QueueRow.vue";
import TrackActionsSheet from "./TrackActionsSheet.vue";
import { REORDER_ROW_HEIGHT, useDragReorder } from "../composables/useDragReorder";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import type { CatalogTrack } from "../types";

/**
 * The queue screen, shared by the tab-level `/queue` page and the full-screen
 * `/player/queue` one. The two differ only in the chrome around them — the
 * full-screen variant has no mini player or bottom nav below it, so it owns the
 * bottom safe area itself.
 */
withDefaults(defineProps<{ safeBottom?: boolean }>(), { safeBottom: false });

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
// The user queue and the system remainder are separate lists with separate
// drag state — reordering only moves entries within their own queue.

const userUpNext = computed(() => player.upNext.filter((e) => e.origin === "user"));
const systemUpNext = computed(() => player.upNext.filter((e) => e.origin === "system"));

const userDrag = useDragReorder({
  itemCount: () => userUpNext.value.length,
  rowHeight: REORDER_ROW_HEIGHT,
  immediate: true,
  onCommit: (fromRow, toRow) => {
    const rows = userUpNext.value;
    const from = rows[fromRow]?.orderPos;
    const to = rows[toRow]?.orderPos;
    if (from == null || to == null) return;
    player.reorderUserQueue(from, to);
  },
});

const drag = useDragReorder({
  itemCount: () => systemUpNext.value.length,
  rowHeight: REORDER_ROW_HEIGHT,
  immediate: true,
  onCommit: (fromRow, toRow) => {
    // useDragReorder reports positions within the rendered up-next list;
    // reorderQueue expects positions within playOrder.
    const rows = systemUpNext.value;
    const from = rows[fromRow]?.orderPos;
    const to = rows[toRow]?.orderPos;
    if (from == null || to == null) return;
    player.reorderQueue(from, to);
  },
});

function rowClassFor(d: ReturnType<typeof useDragReorder>, i: number): string {
  return i === d.draggingIndex.value
    ? "relative z-10 rounded-lg border border-primary"
    : "transition-transform duration-150";
}

function rowStyleFor(d: ReturnType<typeof useDragReorder>, i: number): Record<string, string> {
  if (i === d.draggingIndex.value) {
    return { transform: `translateY(${d.offsetY.value}px)` };
  }
  const from = d.draggingIndex.value;
  const to = d.dropIndex.value;
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
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
</script>
