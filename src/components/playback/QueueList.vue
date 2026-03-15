<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { CatalogTrack } from "../../types";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { queueTracks, currentPlayingTrackId } = storeToRefs(store);
const { shuffle } = storeToRefs(settingsStore);

const contextMenu = ref<{ x: number; y: number; track: CatalogTrack; index: number } | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);

const draggedIndex = ref<number | null>(null);
const dropSlotIndex = ref<number | null>(null);
const listContainerRef = ref<HTMLElement | null>(null);

const DRAG_THRESHOLD_PX = 5;
const AUTO_SCROLL_ZONE_PX = 48;
const AUTO_SCROLL_SPEED_PX = 10;
let pointerDownIndex: number | null = null;
let pointerDownY = 0;
let hasCrossedThreshold = false;
let lastClientY = 0;
let scrollDirection: "up" | "down" | null = null;
let autoScrollRafId: number | null = null;

function stopAutoScroll() {
  scrollDirection = null;
  if (autoScrollRafId !== null) {
    cancelAnimationFrame(autoScrollRafId);
    autoScrollRafId = null;
  }
}

function autoScrollTick() {
  if (scrollDirection === null || draggedIndex.value === null) {
    autoScrollRafId = null;
    return;
  }
  const el = listContainerRef.value;
  if (!el) {
    autoScrollRafId = requestAnimationFrame(autoScrollTick);
    return;
  }
  const maxScroll = el.scrollHeight - el.clientHeight;
  if (scrollDirection === "down" && maxScroll > 0) {
    el.scrollTop = Math.min(maxScroll, el.scrollTop + AUTO_SCROLL_SPEED_PX);
  } else if (scrollDirection === "up") {
    el.scrollTop = Math.max(0, el.scrollTop - AUTO_SCROLL_SPEED_PX);
  }
  updateDropSlotFromClientY(lastClientY);
  autoScrollRafId = requestAnimationFrame(autoScrollTick);
}

/** Inline style for drop slot. Collapse the slot above the dragged row so no blank gap remains. */
function slotStyle(row: { type: string; index: number }): Record<string, string> {
  if (row.type !== "slot") return {};
  const isActive = dropSlotIndex.value === row.index && draggedIndex.value !== null;
  const isSlotAboveDragged = draggedIndex.value === row.index;
  return {
    minHeight: isActive ? "20px" : isSlotAboveDragged ? "0" : "6px",
    flexShrink: "0",
    borderRadius: "4px",
    border: isActive ? "3px solid #16a34a" : "none",
    backgroundColor: isActive ? "#22c55e" : "transparent",
    boxShadow: isActive ? "0 0 12px rgba(34, 197, 94, 0.5)" : "none",
    transition: "min-height 0.12s ease, background-color 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease",
  };
}

/** Rows for the list: slot 0, track 0, slot 1, track 1, ... slot n. */
type Row = { key: string; type: "slot"; index: number } | { key: string; type: "track"; track: CatalogTrack; index: number };
const queueRows = computed(() => {
  const tracks = queueTracks.value;
  const rows: Row[] = [];
  for (let i = 0; i < tracks.length; i++) {
    rows.push({ key: "slot-" + i, type: "slot", index: i });
    rows.push({ key: "track-" + tracks[i].id, type: "track", track: tracks[i], index: i });
  }
  rows.push({ key: "slot-" + tracks.length, type: "slot", index: tracks.length });
  return rows;
});

function updateDropSlotFromClientY(clientY: number) {
  const el = listContainerRef.value;
  if (!el || !queueTracks.value.length) return;
  const containerRect = el.getBoundingClientRect();
  const mouseY = clientY - containerRect.top;
  const rows = el.querySelectorAll(".queue-item");
  if (!rows.length) {
    dropSlotIndex.value = 0;
    return;
  }
  for (let i = 0; i < rows.length; i++) {
    const rowRect = (rows[i] as HTMLElement).getBoundingClientRect();
    const rowTop = rowRect.top - containerRect.top;
    const rowBottom = rowRect.bottom - containerRect.top;
    const rowMid = rowTop + (rowBottom - rowTop) / 2;
    if (mouseY < rowTop) {
      dropSlotIndex.value = i;
      return;
    }
    if (mouseY <= rowBottom) {
      dropSlotIndex.value = mouseY < rowMid ? i : i + 1;
      return;
    }
  }
  dropSlotIndex.value = rows.length;
}

function finishPointerDrag() {
  const fromIndex = draggedIndex.value;
  const slotIndex = dropSlotIndex.value ?? 0;
  const n = queueTracks.value.length;
  if (fromIndex !== null && n > 0) {
    // slotIndex = "insert before row slotIndex". After removing the dragged item, we must pass the
    // index where the item should land. If we're moving forward (fromIndex < slotIndex), the
    // target position in the shortened array is slotIndex - 1; otherwise it's slotIndex.
    const toIndex =
      fromIndex < slotIndex ? Math.min(slotIndex - 1, n - 1) : Math.min(slotIndex, n - 1);
    if (fromIndex !== toIndex && toIndex >= 0 && toIndex < n) {
      store.reorderQueue(fromIndex, toIndex);
    }
  }
  store.setInternalQueueDrag(false);
  draggedIndex.value = null;
  dropSlotIndex.value = null;
  pointerDownIndex = null;
  hasCrossedThreshold = false;
  stopAutoScroll();
  document.removeEventListener("mousemove", onPointerMove);
  document.removeEventListener("mouseup", onPointerUp);
  document.body.style.userSelect = "";
  document.body.style.cursor = "";
}

function onPointerMove(e: MouseEvent) {
  if (pointerDownIndex === null) return;
  if (!hasCrossedThreshold) {
    if (Math.abs(e.clientY - pointerDownY) >= DRAG_THRESHOLD_PX) {
      hasCrossedThreshold = true;
      store.setInternalQueueDrag(true);
      draggedIndex.value = pointerDownIndex;
      dropSlotIndex.value = null;
      document.body.style.userSelect = "none";
      document.body.style.cursor = "grabbing";
    } else return;
  }
  lastClientY = e.clientY;
  updateDropSlotFromClientY(e.clientY);

  const el = listContainerRef.value;
  if (el) {
    const rect = el.getBoundingClientRect();
    const distFromTop = e.clientY - rect.top;
    const distFromBottom = rect.bottom - e.clientY;
    const nextDirection: "up" | "down" | null =
      distFromBottom < AUTO_SCROLL_ZONE_PX && distFromBottom >= 0
        ? "down"
        : distFromTop < AUTO_SCROLL_ZONE_PX && distFromTop >= 0
          ? "up"
          : null;
    if (nextDirection !== scrollDirection) {
      scrollDirection = nextDirection;
      if (scrollDirection !== null && autoScrollRafId === null) {
        autoScrollRafId = requestAnimationFrame(autoScrollTick);
      }
    }
  } else {
    scrollDirection = null;
  }
}

function onPointerUp() {
  if (pointerDownIndex === null) return;
  if (hasCrossedThreshold && draggedIndex.value !== null) {
    finishPointerDrag();
  } else {
    pointerDownIndex = null;
    hasCrossedThreshold = false;
    stopAutoScroll();
    document.removeEventListener("mousemove", onPointerMove);
    document.removeEventListener("mouseup", onPointerUp);
  }
}

function onRowPointerDown(e: MouseEvent, index: number) {
  if (e.button !== 0) return;
  pointerDownIndex = index;
  pointerDownY = e.clientY;
  hasCrossedThreshold = false;
  document.addEventListener("mousemove", onPointerMove);
  document.addEventListener("mouseup", onPointerUp);
}

onUnmounted(() => {
  document.removeEventListener("mousemove", onPointerMove);
  document.removeEventListener("mouseup", onPointerUp);
  stopAutoScroll();
  if (draggedIndex.value !== null) {
    store.setInternalQueueDrag(false);
    draggedIndex.value = null;
    dropSlotIndex.value = null;
    pointerDownIndex = null;
    hasCrossedThreshold = false;
    document.body.style.userSelect = "";
    document.body.style.cursor = "";
  }
});

function formatDuration(secs: number | null): string {
  if (secs == null) return "—";
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function openContextMenu(e: MouseEvent, track: CatalogTrack, index: number) {
  e.preventDefault();
  contextMenu.value = { x: e.clientX, y: e.clientY, track, index };
}

function closeContextMenu() {
  contextMenu.value = null;
}

function playNow() {
  const menu = contextMenu.value;
  if (!menu) return;
  closeContextMenu();
  const { track } = menu;
  store.setSelection([track.id]);
  store.setCurrentPlaying(track.id);
  store.setPlayRequestTrackId(track.id);
}

function removeFromQueue() {
  const menu = contextMenu.value;
  if (!menu) return;
  closeContextMenu();
  store.removeFromQueueAtIndex(menu.index);
}

watch(contextMenu, (menu) => {
  if (!menu) return;
  const onOutside = (e: MouseEvent) => {
    const target = e.target as Node;
    if (contextMenuRef.value?.contains(target)) return;
    closeContextMenu();
    document.removeEventListener("click", onOutside);
    document.removeEventListener("keydown", onEscape);
  };
  const onEscape = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      closeContextMenu();
      document.removeEventListener("click", onOutside);
      document.removeEventListener("keydown", onEscape);
    }
  };
  nextTick(() => {
    setTimeout(() => {
      document.addEventListener("click", onOutside);
      document.addEventListener("keydown", onEscape);
    }, 0);
  });
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden border-l border-stone-700 bg-stone-900/80">
    <div class="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-stone-700 px-3 py-2">
      <div class="min-w-0 flex-1">
        <h3 class="text-xs font-semibold uppercase tracking-wider text-stone-400">Queue</h3>
        <p v-if="shuffle" class="text-[10px] text-amber-400/90">Shuffle is ON — queue order ignored</p>
      </div>
      <button
        type="button"
        class="rounded border border-stone-600 px-2 py-1 text-xs text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-stone-300"
        :disabled="!queueTracks.length"
        @click="store.clearQueue()"
      >
        Clear queue
      </button>
    </div>
    <div
      ref="listContainerRef"
      class="queue-list-container table-scroll-container min-h-0 flex-1 overflow-auto p-2"
    >
      <TransitionGroup
        v-if="queueTracks.length"
        tag="div"
        name="queue-list"
        class="flex flex-col gap-0"
      >
        <template v-for="row in queueRows" :key="row.key">
          <!-- Drop slot: invisible until dragging, then green bar -->
          <div
            v-if="row.type === 'slot'"
            :data-slot-index="row.index"
            :style="slotStyle(row)"
          />
          <!-- Track row: pointer-based drag (no native DnD, so no macOS green plus) -->
          <div
            v-else
            :data-index="row.index"
            class="queue-item"
            :class="[
              { 'queue-item--current': row.track.id === currentPlayingTrackId },
              { 'queue-item--dragging': draggedIndex === row.index },
            ]"
            @contextmenu.prevent="openContextMenu($event, row.track, row.index)"
            @mousedown.prevent="onRowPointerDown($event, row.index)"
          >
            <TrackAlbumArt :path="row.track.path" class="queue-item__art shrink-0" />
            <span class="queue-item__num">{{ row.index + 1 }}</span>
            <span class="queue-item__title" :title="row.track.title ?? row.track.path">{{ row.track.title ?? "—" }}</span>
            <span class="queue-item__artist" :title="row.track.artist ?? ''">{{ row.track.artist ?? "—" }}</span>
            <span class="queue-item__dur">{{ formatDuration(row.track.duration_secs) }}</span>
          </div>
        </template>
      </TransitionGroup>
      <p v-else class="p-4 text-center text-sm text-stone-500">
        Queue is empty. Right‑click a track or album and choose “Add to queue”.
      </p>
    </div>
  </div>
  <Teleport to="body">
    <div
      v-if="contextMenu"
      ref="contextMenuRef"
      class="fixed z-[300] min-w-[140px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="playNow"
      >
        <svg class="h-4 w-4 shrink-0 text-stone-400" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M8 5v14l11-7z" />
        </svg>
        Play Now
      </button>
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="removeFromQueue"
      >
        <svg class="h-4 w-4 shrink-0 text-stone-400" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2" />
          <line x1="10" y1="11" x2="10" y2="17" />
          <line x1="14" y1="11" x2="14" y2="17" />
        </svg>
        Remove
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.queue-list-container {
  scrollbar-gutter: stable;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2rem;
  padding: 0.375rem 0.5rem;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: grab;
  flex-shrink: 0;
  transition: background-color 0.15s ease, opacity 0.15s ease, min-height 0.15s ease, padding 0.15s ease;
}
.queue-item:hover {
  background-color: rgb(41 37 36 / 0.5);
}
.queue-item:active {
  cursor: grabbing;
}
.queue-item--current {
  background-color: rgb(55 65 81 / 0.5);
  color: rgb(245 245 244);
}
.queue-item:not(.queue-item--current) {
  color: rgb(214 211 209);
}
/* Collapse dragged row so list closes the gap; no placeholder = no snap on drop */
.queue-item--dragging {
  min-height: 0 !important;
  height: 0 !important;
  padding-top: 0 !important;
  padding-bottom: 0 !important;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  border: none;
}
.queue-item__art {
  width: 1.5rem;
  height: 1.5rem;
  flex-shrink: 0;
}
.queue-item__num {
  width: 1.25rem;
  flex-shrink: 0;
  text-align: right;
  font-size: 0.75rem;
  tabular-nums: tabular-nums;
  color: rgb(120 113 108);
}
.queue-item__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-item__artist {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgb(120 113 108);
}
.queue-item__dur {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 0.75rem;
  tabular-nums: tabular-nums;
  color: rgb(120 113 108);
}

/* Animated list: only track rows move when reordering */
.queue-list-move {
  transition: transform 0.2s ease;
}
/* When a track leaves the queue (e.g. started playing), remove it from flow and hide instantly so the list doesn’t look buggy */
.queue-list-leave-active {
  position: absolute;
  left: 0;
  right: 0;
  opacity: 0;
  transition: opacity 0.05s ease;
  pointer-events: none;
}
.queue-list-leave-to {
  opacity: 0;
}
</style>
