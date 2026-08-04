<template>
  <Teleport to="body">
    <!-- Backdrop -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-200"
      leave-to-class="opacity-0"
    >
      <div v-if="open" class="fixed inset-0 z-[70] bg-black/50" @click="emit('close')" />
    </Transition>

    <!-- Panel: bottom sheet on mobile, right slide-over on desktop -->
    <Transition
      enter-active-class="transition-transform duration-250 ease-out"
      enter-from-class="translate-y-full md:translate-x-full md:translate-y-0"
      leave-active-class="transition-transform duration-200 ease-in"
      leave-to-class="translate-y-full md:translate-x-full md:translate-y-0"
    >
      <div
        v-if="open"
        class="fixed z-[71] flex flex-col border-stone-700 bg-stone-900 shadow-2xl inset-x-0 bottom-0 max-h-[80vh] rounded-t-2xl border md:inset-x-auto md:inset-y-0 md:right-0 md:max-h-none md:w-[380px] md:rounded-none md:border-y-0 md:border-r-0"
      >
        <!-- Grab handle (mobile) -->
        <div class="flex justify-center pt-2 md:hidden">
          <div class="h-1 w-10 rounded-full bg-stone-700" />
        </div>

        <!-- Header -->
        <div class="flex shrink-0 items-center justify-between border-b border-stone-800 px-4 py-3">
          <h3 class="text-sm font-semibold text-stone-100">
            Queue
            <span v-if="lib.playQueue.length" class="ml-1.5 text-xs font-normal text-stone-500">
              {{ lib.playQueue.length }} track{{ lib.playQueue.length !== 1 ? 's' : '' }}
            </span>
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-if="lib.playQueue.length > 1"
              type="button"
              class="flex h-9 items-center gap-1 rounded px-2 text-xs text-stone-400 hover:bg-stone-800 hover:text-stone-200"
              @click="lib.clearQueue()"
            >
              Clear
            </button>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded text-stone-400 hover:bg-stone-800 hover:text-stone-200"
              aria-label="Close queue"
              @click="emit('close')"
            >
              <FeatherIcon name="x" class="h-4 w-4" />
            </button>
          </div>
        </div>

        <!-- Body -->
        <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-2 py-2" style="padding-bottom: max(env(safe-area-inset-bottom, 0px), 0.5rem)">
          <div v-if="lib.playQueue.length === 0" class="flex flex-col items-center gap-2 py-12 text-sm text-stone-600">
            <FeatherIcon name="list" class="h-6 w-6" />
            Queue is empty — play a track to get started.
          </div>

          <template v-else>
            <!-- Now playing -->
            <div
              v-if="lib.nowPlaying"
              class="flex items-center gap-3 rounded-lg border border-accent/40 bg-accent/10 px-2.5 py-2.5"
            >
              <CoverThumb :track="lib.nowPlaying" class="h-10 w-10 rounded" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-stone-100">{{ lib.nowPlaying.title ?? '—' }}</p>
                <p class="truncate text-xs text-stone-400">{{ lib.nowPlaying.artist ?? lib.nowPlaying.album_artist ?? '—' }}</p>
              </div>
              <span class="text-[10px] font-semibold uppercase tracking-wider text-accent">Playing</span>
            </div>

            <!-- Upcoming -->
            <p v-if="upcoming.length" class="px-2 pb-1 pt-3 text-xs font-semibold uppercase tracking-wide text-stone-500">
              Up next
            </p>
            <div
              v-for="(t, i) in upcoming"
              :key="t.id + '-' + i"
              class="group flex items-center gap-2 rounded-lg px-1.5 py-1.5 hover:bg-stone-800/60"
              :class="dragOverIndex === (upcomingIndexOffset + i) ? 'bg-stone-800' : ''"
              draggable="true"
              @dragstart="onDragStart(i)"
              @dragover.prevent="onDragOver(i)"
              @drop.prevent="onDrop(i)"
              @dragend="onDragEnd"
            >
              <!-- Drag handle (desktop) -->
              <span class="hidden cursor-grab text-stone-600 active:cursor-grabbing md:inline-flex" title="Drag to reorder">
                <FeatherIcon name="grip-vertical" class="h-4 w-4" />
              </span>
              <button
                type="button"
                class="flex min-w-0 flex-1 items-center gap-2.5 rounded text-left"
                @click="lib.playTrack(t)"
              >
                <CoverThumb :track="t" class="h-9 w-9 shrink-0 rounded" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm text-stone-200">{{ t.title ?? '—' }}</span>
                  <span class="block truncate text-xs text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</span>
                </span>
                <span class="shrink-0 text-xs text-stone-500 tabular-nums">{{ durationLabel(t) }}</span>
              </button>
              <!-- Mobile reorder: up/down -->
              <span class="flex shrink-0 flex-col md:hidden">
                <button
                  type="button"
                  class="flex h-7 w-7 items-center justify-center rounded text-stone-500 active:bg-stone-700"
                  :disabled="i === 0"
                  aria-label="Move up"
                  @click="move(i, -1)"
                >
                  <FeatherIcon name="chevron-up" class="h-4 w-4" />
                </button>
                <button
                  type="button"
                  class="flex h-7 w-7 items-center justify-center rounded text-stone-500 active:bg-stone-700"
                  :disabled="i === upcoming.length - 1"
                  aria-label="Move down"
                  @click="move(i, 1)"
                >
                  <FeatherIcon name="chevron-down" class="h-4 w-4" />
                </button>
              </span>
              <button
                type="button"
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-300"
                :aria-label="`Remove ${t.title ?? 'track'} from queue`"
                @click="lib.removeFromQueue(upcomingIndexOffset + i)"
              >
                <FeatherIcon name="x" class="h-4 w-4" />
              </button>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import CoverThumb from "./CoverThumb.vue";
import type { CatalogTrack } from "../types";

defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const lib = useLibraryStore();

// Upcoming = everything after the current queue position
const upcomingIndexOffset = computed(() => Math.max(0, lib.queueIndex + 1));
const upcoming = computed(() => lib.playQueue.slice(upcomingIndexOffset.value));

function durationLabel(t: CatalogTrack): string {
  return t.duration_secs != null ? formatDuration(t.duration_secs) : "";
}

// ── Reorder (desktop: HTML5 drag-and-drop; mobile: up/down buttons) ──────
let dragFrom = -1;
const dragOverIndex = ref<number | null>(null);

function onDragStart(i: number): void {
  dragFrom = i;
  dragOverIndex.value = null;
}

function onDragOver(i: number): void {
  dragOverIndex.value = upcomingIndexOffset.value + i;
}

function onDrop(i: number): void {
  if (dragFrom < 0 || dragFrom === i) {
    dragOverIndex.value = null;
    return;
  }
  reorderLocal(dragFrom, i);
  dragFrom = -1;
  dragOverIndex.value = null;
}

function onDragEnd(): void {
  dragFrom = -1;
  dragOverIndex.value = null;
}

function move(i: number, dir: -1 | 1): void {
  const j = i + dir;
  if (j < 0 || j >= upcoming.value.length) return;
  reorderLocal(i, j);
}

function reorderLocal(from: number, to: number): void {
  const base = upcomingIndexOffset.value;
  const next = [...lib.playQueue];
  const [moved] = next.splice(base + from, 1);
  next.splice(base + to, 0, moved);
  lib.playQueue = next;
}
</script>
