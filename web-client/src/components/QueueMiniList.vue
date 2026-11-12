<template>
  <!-- Compact queue list for the maximized player's glass surfaces (the xl
       side panel and the art-fullscreen top-right menu). White text: it only
       ever renders over the player's cover-art backdrop. -->
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex h-11 shrink-0 items-center gap-2 border-b border-white/10 px-4">
      <MageIcon name="stack" class="h-4 w-4 text-primary" />
      <span class="flex-1 text-label-lg text-white">Queue</span>
      <span class="text-label-sm tabular-nums text-white/50">{{ player.upNext.length }}</span>
    </div>
    <div ref="scroller" class="queue-scroll min-h-0 flex-1 overflow-y-auto px-2 pb-2">
      <div
        v-if="player.currentTrack"
        class="mb-1 flex items-center gap-2.5 rounded-lg bg-white/10 px-2 py-1.5"
      >
        <div class="h-9 w-9 shrink-0 overflow-hidden rounded bg-white/10">
          <img
            v-if="coverFor(player.currentTrack)"
            :src="coverFor(player.currentTrack)"
            :alt="player.currentTrack.album ?? ''"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <MageIcon name="music" class="h-4 w-4 text-white/50" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-body-md text-white">{{ player.currentTrack.title ?? "—" }}</p>
          <p class="truncate text-body-sm text-white/55">{{ player.currentTrack.artist ?? player.currentTrack.album_artist ?? "—" }}</p>
        </div>
        <EqualizerBars class="shrink-0 text-primary" :paused="!player.isPlaying" />
      </div>
      <div
        v-for="(entry, i) in player.upNext"
        :key="entry.track.id"
        class="flex w-full items-center gap-1 rounded-lg px-1 py-1.5"
        :class="rowClassFor(i)"
        :style="rowStyleFor(i)"
      >
        <!-- Drag handle: press-and-drag (mouse) or long-press (touch) reorders,
             same gesture as the full queue screen. Stopping pointerdown keeps
             the row's play click from arming. -->
        <span
          class="flex h-8 w-6 shrink-0 cursor-grab items-center justify-center rounded text-white/40 hover:text-white/80"
          style="touch-action: none"
          role="button"
          aria-label="Reorder"
          @pointerdown.stop="drag.start(i, $event)"
          @click.stop.prevent
        >
          <MageIcon name="dash-menu" class="h-4 w-4" />
        </span>
        <button
          type="button"
          class="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg px-1 py-0.5 text-left transition-colors hover:bg-white/10"
          @click="player.skipTo(entry.track)"
        >
          <div class="h-9 w-9 shrink-0 overflow-hidden rounded bg-white/10">
            <img
              v-if="coverFor(entry.track)"
              :src="coverFor(entry.track)"
              :alt="entry.track.album ?? ''"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-4 w-4 text-white/50" />
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-body-md text-white">{{ entry.track.title ?? "—" }}</p>
            <p class="truncate text-body-sm text-white/55">{{ entry.track.artist ?? entry.track.album_artist ?? "—" }}</p>
          </div>
        </button>
        <button
          type="button"
          class="flex h-8 w-7 shrink-0 items-center justify-center rounded-full text-white/40 transition-colors hover:bg-white/10 hover:text-white"
          aria-label="Track actions"
          @click="sheetTrack = entry.track"
        >
          <MageIcon name="dots" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex h-8 w-7 shrink-0 items-center justify-center rounded-full text-white/40 transition-colors hover:bg-white/10 hover:text-white"
          aria-label="Remove from queue"
          @click="player.removeFromQueue(entry.track)"
        >
          <MageIcon name="multiply" class="h-4 w-4" />
        </button>
      </div>
      <p v-if="player.upNext.length === 0" class="px-2 py-1 text-body-sm text-white/45">
        Nothing up next.
      </p>
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      :can-remove-from-queue="true"
      :can-move-to-user-queue="sheetIsSystemUpNext"
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
import EqualizerBars from "@shared/components/EqualizerBars.vue";
import TrackActionsSheet from "./TrackActionsSheet.vue";
import { useDragReorder } from "../composables/useDragReorder";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import type { CatalogTrack } from "../types";

const router = useRouter();
const player = usePlayerStore();
const lib = useLibraryStore();

// --- Drag reorder (same composable + visuals as the full queue screen) ------
// Mini rows are 36px of content + 2x6px padding.
const MINI_ROW_HEIGHT = 48;

/** The up-next list is user-queue entries first, then the system remainder,
 *  so the user section is the leading slice of the flat list. */
const userCount = computed(() => player.upNext.reduce((n, e) => n + (e.origin === "user" ? 1 : 0), 0));

const scroller = ref<HTMLElement | null>(null);

const drag = useDragReorder({
  itemCount: () => player.upNext.length,
  rowHeight: MINI_ROW_HEIGHT,
  immediate: true,
  // The panel is short, so a drag held near its top/bottom edge scrolls the
  // list and keeps advancing the drop index past the visible area.
  autoScrollContainer: () => scroller.value,
  onCommit: (from, to) => {
    const rows = player.upNext;
    const fromEntry = rows[from];
    const toEntry = rows[to];
    if (!fromEntry || !toEntry) return;
    const users = userCount.value;
    if (from < users && to < users) {
      player.reorderUserQueue(fromEntry.orderPos, toEntry.orderPos);
    } else if (from >= users && to >= users) {
      player.reorderQueue(fromEntry.orderPos, toEntry.orderPos);
    } else if (from >= users && to < users) {
      // System row dropped into the user section: promote it, same as the
      // full queue screen's cross-list drop.
      player.moveToUserQueue(fromEntry.track);
    }
    // user → system has no store operation: the user queue always plays first.
  },
});

function rowClassFor(i: number): string {
  return i === drag.draggingIndex.value
    ? "relative z-10 border border-primary bg-white/10"
    : "transition-transform duration-150";
}

function rowStyleFor(i: number): Record<string, string> {
  if (i === drag.draggingIndex.value) {
    return { transform: `translateY(${drag.offsetY.value}px)` };
  }
  const from = drag.draggingIndex.value;
  const to = drag.dropIndex.value;
  if (from === null || to === null || from === to) return {};
  if (from < to && i > from && i <= to) return { transform: `translateY(-${MINI_ROW_HEIGHT}px)` };
  if (from > to && i < from && i >= to) return { transform: `translateY(${MINI_ROW_HEIGHT}px)` };
  return {};
}

/** Cover URL for a queue row, requesting the fetch when the art isn't cached
 *  yet (mirrors QueuePanel's currentCover). */
function coverFor(t: CatalogTrack): string | undefined {
  if (!t.has_cover) return undefined;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id);
}

// --- Track actions sheet (3-dots), same wiring as the full queue screen ------

const sheetTrack = ref<CatalogTrack | null>(null);

/** Whether the sheet's track is a system "Up next" entry — only those get the
 *  "Add to queue" action, since user-queue tracks are already queued. */
const sheetIsSystemUpNext = computed(
  () => !!sheetTrack.value && player.upNext.some((e) => e.origin === "system" && e.track.id === sheetTrack.value!.id),
);

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
</script>

<style scoped>
/* See-through glassy scrollbar for the queue scroller: a translucent white
   thumb floating over a transparent track (scrollbar pseudo-elements can't
   take backdrop-filter, so the glass read comes from the alpha alone). */
.queue-scroll {
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.28) transparent;
}
.queue-scroll::-webkit-scrollbar {
  width: 6px;
}
.queue-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.queue-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 3px;
}
.queue-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.35);
}
</style>
