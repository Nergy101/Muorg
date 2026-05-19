<template>
  <div
    v-if="lib.nowPlaying"
    class="flex flex-col border-t border-stone-800 bg-stone-900"
    style="padding-bottom: max(env(safe-area-inset-bottom, 0px), 0px);"
  >
    <!-- Progress bar (full width, flush to top of bar) -->
    <div class="relative h-1 w-full cursor-pointer bg-stone-700" @click="seekByClick">
      <div
        class="absolute left-0 top-0 h-full bg-accent transition-none"
        :style="{ width: progressPercent + '%' }"
      />
    </div>

    <div class="flex items-center gap-3 px-3 py-2">
      <!-- Cover thumb -->
      <div
        class="h-10 w-10 shrink-0 overflow-hidden rounded cursor-pointer"
        @click="showOverlay = true"
      >
        <img
          v-if="coverUrl"
          :src="coverUrl"
          :alt="lib.nowPlaying.album ?? ''"
          class="h-full w-full object-cover"
        />
        <div v-else class="flex h-full w-full items-center justify-center bg-stone-700">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="text-stone-500">
            <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="3" />
          </svg>
        </div>
      </div>

      <!-- Track info (tap to expand on mobile) -->
      <div class="min-w-0 flex-1 cursor-pointer" @click="showOverlay = true">
        <p class="truncate text-sm font-medium text-stone-200">{{ lib.nowPlaying.title ?? '—' }}</p>
        <p class="truncate text-xs text-stone-500">{{ lib.nowPlaying.artist ?? lib.nowPlaying.album_artist ?? '—' }}</p>
      </div>

      <!-- Controls -->
      <div class="flex shrink-0 items-center gap-1">
        <span class="hidden text-xs tabular-nums text-stone-600 sm:block">{{ currentTimeLabel }}</span>

        <button
          class="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-white hover:bg-[var(--accent-hover)]"
          @click="lib.togglePlayPause()"
        >
          <svg v-if="lib.isPlaying" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="4" width="4" height="16" /><rect x="14" y="4" width="4" height="16" />
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <polygon points="5 3 19 12 5 21 5 3" />
          </svg>
        </button>

        <span class="hidden text-xs tabular-nums text-stone-600 sm:block">{{ durationLabel }}</span>
      </div>

      <!-- Volume (desktop) -->
      <div class="hidden items-center gap-1.5 sm:flex">
        <button class="text-stone-500 hover:text-stone-300" @click="toggleMute">
          <svg v-if="lib.volume === 0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
            <line x1="23" y1="9" x2="17" y2="15" /><line x1="17" y1="9" x2="23" y2="15" />
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
          </svg>
        </button>
        <input
          type="range"
          min="0"
          max="1"
          step="0.02"
          :value="lib.volume"
          class="w-20 progress-input"
          :style="{ '--val': (lib.volume * 100) + '%' }"
          @input="lib.setVolume(parseFloat(($event.target as HTMLInputElement).value))"
        />
      </div>
    </div>
  </div>

  <!-- Full-screen overlay (mobile / click on track info) -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition-transform duration-200"
      enter-from-class="translate-y-full"
      leave-active-class="transition-transform duration-200"
      leave-to-class="translate-y-full"
    >
      <div
        v-if="showOverlay && lib.nowPlaying"
        class="fixed inset-0 z-50 flex flex-col bg-stone-950"
        style="padding-bottom: env(safe-area-inset-bottom, 0px);"
      >
        <!-- Close -->
        <div class="flex justify-end p-4">
          <button class="text-stone-500 hover:text-stone-300" @click="showOverlay = false">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="18 15 12 9 6 15" />
            </svg>
          </button>
        </div>

        <!-- Cover -->
        <div class="flex flex-1 items-center justify-center px-8">
          <div class="aspect-square w-full max-w-sm overflow-hidden rounded-2xl shadow-2xl">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center bg-stone-800">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" class="text-stone-600">
                <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="3" /><line x1="12" y1="9" x2="12" y2="2" />
              </svg>
            </div>
          </div>
        </div>

        <!-- Info + controls -->
        <div class="px-8 pb-8 pt-6 space-y-6">
          <div>
            <p class="text-xl font-bold text-stone-100">{{ lib.nowPlaying.title ?? '—' }}</p>
            <p class="text-stone-400">{{ lib.nowPlaying.artist ?? lib.nowPlaying.album_artist ?? '—' }}</p>
            <p class="text-sm text-stone-600">{{ lib.nowPlaying.album }}</p>
          </div>

          <!-- Seek bar -->
          <div class="space-y-1">
            <input
              type="range"
              min="0"
              :max="lib.durationSecs || 1"
              step="1"
              :value="lib.currentTimeSecs"
              class="w-full progress-input"
              :style="{ '--val': progressPercent + '%' }"
              @change="lib.seekTo(parseFloat(($event.target as HTMLInputElement).value))"
            />
            <div class="flex justify-between text-xs tabular-nums text-stone-600">
              <span>{{ currentTimeLabel }}</span>
              <span>{{ durationLabel }}</span>
            </div>
          </div>

          <!-- Playback controls -->
          <div class="flex items-center justify-center gap-6">
            <button
              class="flex h-14 w-14 items-center justify-center rounded-full bg-accent text-white hover:bg-[var(--accent-hover)]"
              @click="lib.togglePlayPause()"
            >
              <svg v-if="lib.isPlaying" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="4" width="4" height="16" /><rect x="14" y="4" width="4" height="16" />
              </svg>
              <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </button>
          </div>

          <!-- Volume -->
          <div class="flex items-center gap-3">
            <button class="text-stone-500" @click="toggleMute">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
              </svg>
            </button>
            <input
              type="range"
              min="0"
              max="1"
              step="0.02"
              :value="lib.volume"
              class="flex-1 progress-input"
              :style="{ '--val': (lib.volume * 100) + '%' }"
              @input="lib.setVolume(parseFloat(($event.target as HTMLInputElement).value))"
            />
            <button class="text-stone-500">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";

const lib = useLibraryStore();
const showOverlay = ref(false);
let prevVolume = 1;

const coverUrl = computed(() => {
  const t = lib.nowPlaying;
  if (!t || !t.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const progressPercent = computed(() => {
  if (!lib.durationSecs) return 0;
  return Math.min(100, (lib.currentTimeSecs / lib.durationSecs) * 100);
});

const currentTimeLabel = computed(() => formatDuration(lib.currentTimeSecs));
const durationLabel = computed(() => formatDuration(lib.durationSecs));

watch(
  () => lib.nowPlaying?.id,
  () => {
    if (lib.nowPlaying) lib.requestCover(lib.nowPlaying.id);
  },
);

function seekByClick(e: MouseEvent): void {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const ratio = (e.clientX - rect.left) / rect.width;
  lib.seekTo(Math.floor(ratio * lib.durationSecs));
}

function toggleMute(): void {
  if (lib.volume > 0) {
    prevVolume = lib.volume;
    lib.setVolume(0);
  } else {
    lib.setVolume(prevVolume || 1);
  }
}
</script>
