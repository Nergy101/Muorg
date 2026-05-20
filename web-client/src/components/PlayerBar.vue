<template>
  <div
    v-if="lib.nowPlaying"
    class="flex shrink-0 flex-col border-t border-stone-700 bg-stone-900 px-3 pb-2 pt-2"
  >
    <!-- Mobile-only: progress bar row above main controls -->
    <div class="flex items-center gap-2 pb-1.5 sm:hidden">
      <span class="w-8 shrink-0 text-right text-xs tabular-nums text-stone-500">{{ currentTimeLabel }}</span>
      <div class="relative min-w-0 flex-1 cursor-pointer" @click="seekByClick">
        <div class="h-1.5 w-full overflow-hidden rounded-full bg-stone-600">
          <div class="h-full rounded-full bg-accent transition-none" :style="{ width: progressPercent + '%' }" />
        </div>
      </div>
      <span class="w-8 shrink-0 text-left text-xs tabular-nums text-stone-500">{{ durationLabel }}</span>
    </div>

    <div class="flex w-full items-center gap-3">

      <!-- Left: album art + track info (click to expand, right-click for context menu) -->
      <div
        class="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded px-1 py-0.5 transition-colors hover:bg-stone-800/60 sm:w-56 sm:flex-none sm:shrink-0"
        @click="showOverlay = true"
        @contextmenu.prevent="openNowPlayingCtx($event)"
      >
        <div class="h-9 w-9 shrink-0 overflow-hidden rounded bg-stone-800">
          <img
            v-if="coverUrl"
            :src="coverUrl"
            :alt="lib.nowPlaying.album ?? ''"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FeatherIcon name="music" class="h-4 w-4 text-stone-600" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-xs font-semibold text-stone-100">{{ lib.nowPlaying.title ?? '—' }}</p>
          <p class="truncate text-xs text-stone-400">{{ lib.nowPlaying.artist ?? lib.nowPlaying.album_artist ?? '—' }}</p>
        </div>
      </div>

      <!-- Desktop center: controls + progress in one row -->
      <div class="hidden min-w-0 flex-1 items-center gap-1 sm:flex">
        <button
          type="button"
          class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Previous track"
          @click="playPrevious()"
        >
          <FeatherIcon name="skip-back" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Restart"
          @click="restart"
        >
          <FeatherIcon name="square" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex shrink-0 items-center justify-center rounded bg-accent p-1.5 text-stone-50 hover:bg-[var(--accent-hover)]"
          :aria-label="lib.isPlaying ? 'Pause' : 'Play'"
          @click="lib.togglePlayPause()"
        >
          <FeatherIcon :name="lib.isPlaying ? 'pause' : 'play'" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Next track"
          @click="playNext()"
        >
          <FeatherIcon name="skip-forward" class="h-4 w-4" />
        </button>

        <span class="ml-1 w-8 shrink-0 text-right text-xs tabular-nums text-stone-500">{{ currentTimeLabel }}</span>
        <div class="relative min-w-0 flex-1 cursor-pointer" @click="seekByClick">
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-stone-600">
            <div
              class="h-full rounded-full bg-accent transition-none"
              :style="{ width: progressPercent + '%' }"
            />
          </div>
        </div>
        <span class="w-8 shrink-0 text-left text-xs tabular-nums text-stone-500">{{ durationLabel }}</span>
      </div>

      <!-- Mobile right: core playback controls only -->
      <div class="flex shrink-0 items-center gap-0.5 sm:hidden">
        <button
          type="button"
          class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Previous track"
          @click="playPrevious()"
        >
          <FeatherIcon name="skip-back" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded bg-accent p-2 text-stone-50 hover:bg-[var(--accent-hover)]"
          :aria-label="lib.isPlaying ? 'Pause' : 'Play'"
          @click="lib.togglePlayPause()"
        >
          <FeatherIcon :name="lib.isPlaying ? 'pause' : 'play'" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Next track"
          @click="playNext()"
        >
          <FeatherIcon name="skip-forward" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Expand player"
          @click="showOverlay = true"
        >
          <FeatherIcon name="maximize-2" class="h-4 w-4" />
        </button>
      </div>

      <!-- Desktop right: shuffle + repeat + volume + maximize -->
      <div class="hidden shrink-0 items-center gap-1 sm:flex">
        <button
          type="button"
          class="flex items-center justify-center rounded p-1.5 hover:bg-stone-700 hover:text-stone-200"
          :class="shuffle ? 'text-accent' : 'text-stone-400'"
          aria-label="Shuffle"
          @click="shuffle = !shuffle"
        >
          <FeatherIcon name="shuffle" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="relative flex items-center justify-center rounded p-1.5 hover:bg-stone-700 hover:text-stone-200"
          :class="repeat !== 'none' ? 'text-accent' : 'text-stone-400'"
          aria-label="Repeat"
          @click="cycleRepeat"
        >
          <FeatherIcon name="repeat" class="h-4 w-4" />
          <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Toggle mute"
          @click="toggleMute"
        >
          <FeatherIcon :name="lib.volume === 0 ? 'volume-x' : lib.volume < 0.5 ? 'volume-1' : 'volume-2'" class="h-4 w-4" />
        </button>
        <div class="flex h-7 w-20 items-center">
          <input
            type="range"
            min="0"
            max="1"
            step="0.02"
            :value="lib.volume"
            class="player-volume-slider h-1.5 w-full cursor-pointer appearance-none rounded-full bg-stone-600"
            :style="{ '--volume-percent': (lib.volume * 100) + '%' }"
            @input="lib.setVolume(parseFloat(($event.target as HTMLInputElement).value))"
          />
        </div>
        <button
          type="button"
          class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Expand player"
          title="Expand player"
          @click="showOverlay = true"
        >
          <FeatherIcon name="maximize-2" class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>

  <!-- Full-screen overlay -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition-transform duration-200"
      enter-from-class="translate-y-full"
      leave-active-class="transition-transform duration-200"
      leave-to-class="translate-y-full"
    >
      <div
        v-if="showOverlay && lib.nowPlaying"
        class="fixed inset-0 z-50 flex flex-col overflow-hidden"
        :style="{ backgroundColor: glowBgColor }"
      >
        <!-- Glow layer: edge-blur (blurred art) or procedural blobs -->
        <div class="pointer-events-none fixed inset-0 z-0" aria-hidden="true">
          <!-- Edge-blur: album art scaled + blurred across the background -->
          <div
            v-if="useEdgeBlurMode && overlayCoverUrl"
            class="edge-blur-art absolute"
            :style="{ backgroundImage: `url(${overlayCoverUrl})`, opacity: 0.85 }"
          />
          <!-- Vivid blobs -->
          <template v-else>
            <div
              v-for="(blob, i) in glowBlobs"
              :key="i"
              class="absolute inset-0 origin-top-left"
              :style="{
                background: `radial-gradient(ellipse at center, rgba(${blob.rgb},${(blob.opacity * 1.6).toFixed(2)}) 0%, rgba(${blob.rgb},${(blob.opacity * 0.96).toFixed(2)}) 25%, rgba(${blob.rgb},${(blob.opacity * 0.32).toFixed(2)}) 45%, rgba(${blob.rgb},0.04) 70%, transparent 90%)`,
                transform: `translate(${blob.cx * 100}%, ${blob.cy * 100}%) translate(-50%, -50%) scale(${blob.rx}, ${blob.ry})`,
                filter: 'blur(24px)',
              }"
            />
          </template>
        </div>

        <!-- Cover art -->
        <div class="relative z-10 flex flex-1 items-center justify-center px-8 pt-8">
          <div class="aspect-square w-full max-w-sm overflow-hidden rounded-2xl shadow-2xl">
            <img v-if="coverUrl" :src="coverUrl" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center bg-stone-800">
              <FeatherIcon name="music" class="h-16 w-16 text-stone-600" />
            </div>
          </div>
        </div>

        <!-- Bottom controls -->
        <div class="relative z-10 flex flex-col gap-4 px-8 pt-6" style="padding-bottom: max(env(safe-area-inset-bottom, 0px), 2rem)">
          <!-- Track info -->
          <div class="text-center">
            <p class="truncate text-xl font-bold text-stone-100">{{ lib.nowPlaying.title ?? '—' }}</p>
            <p class="truncate text-stone-400">{{ lib.nowPlaying.artist ?? lib.nowPlaying.album_artist ?? '—' }}</p>
            <p class="truncate text-sm text-stone-600">{{ lib.nowPlaying.album }}</p>
          </div>

          <!-- Primary controls: prev | play/pause | next -->
          <div class="flex items-center justify-center gap-8">
            <button
              class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-800 hover:text-stone-200"
              @click="playPrevious()"
            >
              <FeatherIcon name="skip-back" class="h-8 w-8" />
            </button>
            <button
              class="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-stone-50 shadow-lg hover:bg-[var(--accent-hover)]"
              @click="lib.togglePlayPause()"
            >
              <FeatherIcon :name="lib.isPlaying ? 'pause' : 'play'" class="h-8 w-8" />
            </button>
            <button
              class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-800 hover:text-stone-200"
              @click="playNext()"
            >
              <FeatherIcon name="skip-forward" class="h-8 w-8" />
            </button>
          </div>

          <!-- Secondary row: restart | time | seek | time | shuffle | repeat | volume | minimize -->
          <div class="flex items-center gap-2">
            <button
              class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-500 hover:bg-stone-800 hover:text-stone-300"
              title="Restart"
              @click="restart"
            >
              <FeatherIcon name="square" class="h-4 w-4" />
            </button>
            <span class="w-9 shrink-0 text-right text-xs tabular-nums text-stone-500">{{ currentTimeLabel }}</span>
            <div class="relative min-w-0 flex-1 cursor-pointer" @click="seekByClick">
              <div class="h-1.5 w-full overflow-hidden rounded-full bg-stone-700">
                <div class="h-full rounded-full bg-accent transition-none" :style="{ width: progressPercent + '%' }" />
              </div>
            </div>
            <span class="w-9 shrink-0 text-xs tabular-nums text-stone-500">{{ durationLabel }}</span>
            <button
              class="flex shrink-0 items-center justify-center rounded p-1.5 hover:bg-stone-800 hover:text-stone-200"
              :class="shuffle ? 'text-accent' : 'text-stone-500'"
              title="Shuffle"
              @click="shuffle = !shuffle"
            >
              <FeatherIcon name="shuffle" class="h-4 w-4" />
            </button>
            <button
              class="relative flex shrink-0 items-center justify-center rounded p-1.5 hover:bg-stone-800 hover:text-stone-200"
              :class="repeat !== 'none' ? 'text-accent' : 'text-stone-500'"
              title="Repeat"
              @click="cycleRepeat"
            >
              <FeatherIcon name="repeat" class="h-4 w-4" />
              <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
            </button>
            <button
              class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-500 hover:bg-stone-800 hover:text-stone-300"
              title="Toggle mute"
              @click="toggleMute"
            >
              <FeatherIcon :name="lib.volume === 0 ? 'volume-x' : lib.volume < 0.5 ? 'volume-1' : 'volume-2'" class="h-4 w-4" />
            </button>
            <input
              type="range" min="0" max="1" step="0.02"
              :value="lib.volume"
              class="player-volume-slider w-20 shrink-0"
              :style="{ '--volume-percent': (lib.volume * 100) + '%' }"
              @input="lib.setVolume(parseFloat(($event.target as HTMLInputElement).value))"
            />
            <button
              class="flex shrink-0 items-center justify-center rounded p-1.5 text-stone-500 hover:bg-stone-800 hover:text-stone-300"
              title="Minimize player"
              @click="showOverlay = false"
            >
              <FeatherIcon name="minimize-2" class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- Now-playing context menu -->
  <TrackContextMenu
    ref="nowPlayingCtxRef"
    :track-id="lib.nowPlaying?.id ?? null"
    :show-find="true"
    @play="lib.nowPlaying && lib.playTrack(lib.nowPlaying)"
    @find="lib.nowPlaying && lib.revealTrack(lib.nowPlaying)"
    @add-to-playlist="addNowPlayingToPlaylist"
    @remove-from-playlist="removeNowPlayingFromPlaylist"
    @new-playlist="showNewPlaylistModal = true"
  />

  <PlaylistModal
    v-model="showNewPlaylistModal"
    title="New Playlist"
    confirm-label="Create"
    @confirm="createPlaylistForNowPlaying"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import FeatherIcon from "./FeatherIcon.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import PlaylistModal from "./PlaylistModal.vue";
import { useDominantColor, useEdgeColors, getGlowBlobs, isColorBland, hasOpposingEdgeColors } from "../composables/useDominantColor";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const showOverlay = ref(false);

// Context menu on the now-playing track info
const nowPlayingCtxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const showNewPlaylistModal = ref(false);

function openNowPlayingCtx(event: MouseEvent): void {
  nowPlayingCtxRef.value?.open(event);
}

async function addNowPlayingToPlaylist(playlistId: number): Promise<void> {
  if (!lib.nowPlaying) return;
  await playlistStore.addTracks(playlistId, [lib.nowPlaying.id]);
}

async function removeNowPlayingFromPlaylist(playlistId: number): Promise<void> {
  if (!lib.nowPlaying) return;
  await playlistStore.removeTracks(playlistId, [lib.nowPlaying.id]);
}

async function createPlaylistForNowPlaying(name: string, icon: string | null): Promise<void> {
  if (!lib.nowPlaying) return;
  await playlistStore.createPlaylist(name, icon ?? undefined);
  const newPl = playlistStore.playlists.at(-1);
  if (newPl) await playlistStore.addTracks(newPl.id, [lib.nowPlaying.id]);
}
const shuffle = ref(false);
const repeat = ref<"none" | "all" | "one">("none");
let prevVolume = 1;

// Shuffle history: track IDs in play order so Previous can go back
const shuffleHistory: number[] = [];

watch(shuffle, (on) => {
  if (!on) shuffleHistory.length = 0;
});

// Push to shuffle history whenever the playing track changes
watch(() => lib.nowPlaying?.id, (newId, oldId) => {
  if (shuffle.value && oldId != null && newId !== oldId) {
    shuffleHistory.push(oldId);
  }
});

const coverUrl = computed(() => {
  const t = lib.nowPlaying;
  if (!t || !t.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const overlayCoverUrl = computed(() => showOverlay.value ? coverUrl.value : null);
const glowRgb = useDominantColor(overlayCoverUrl);
const edgeColors = useEdgeColors(overlayCoverUrl);

// Edge-blur mode: use when center is bland but edges are vibrant, or opposing edge colors
const useEdgeBlurMode = computed(() => {
  if (!overlayCoverUrl.value || !edgeColors.value?.colors.length) return false;
  const blandCenterVibrantEdges = isColorBland(glowRgb.value) && !isColorBland(edgeColors.value.colors[0]);
  const opposing = hasOpposingEdgeColors(edgeColors.value.bySide);
  return blandCenterVibrantEdges || opposing;
});

const glowBlobs = computed(() => {
  if (useEdgeBlurMode.value) return [];
  const rgb = glowRgb.value;
  const key = String(lib.nowPlaying?.id ?? "");
  if (isColorBland(rgb)) return [];
  return getGlowBlobs(rgb, key);
});

const glowBgColor = computed(() => {
  const parts = glowRgb.value.split(",").map(Number);
  if (parts.length !== 3) return "#000";
  const [r, g, b] = parts;
  if (isColorBland(glowRgb.value)) return "#0c0a09";
  return `rgb(${Math.round(r * 0.08)},${Math.round(g * 0.08)},${Math.round(b * 0.08)})`;
});

const progressPercent = computed(() => {
  if (!lib.durationSecs) return 0;
  return Math.min(100, (lib.currentTimeSecs / lib.durationSecs) * 100);
});

const currentTimeLabel = computed(() => formatDuration(lib.currentTimeSecs));
const durationLabel = computed(() => formatDuration(lib.durationSecs));

watch(() => lib.nowPlaying?.id, () => {
  if (lib.nowPlaying) lib.requestCover(lib.nowPlaying.id);
});

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

function cycleRepeat(): void {
  repeat.value = repeat.value === "none" ? "all" : repeat.value === "all" ? "one" : "none";
}

function playNext(fromAuto = false): void {
  const tracks = lib.filteredTracks;
  if (!tracks.length) return;

  if (shuffle.value) {
    const others = tracks.filter((t) => t.id !== lib.nowPlaying?.id);
    const pool = others.length > 0 ? others : tracks;
    if (!fromAuto && pool.length === 0) return;
    if (fromAuto && repeat.value === "none" && others.length === 0) return;
    lib.playTrack(pool[Math.floor(Math.random() * pool.length)]);
    return;
  }

  const idx = tracks.findIndex((t) => t.id === lib.nowPlaying?.id);
  if (idx >= 0 && idx + 1 < tracks.length) {
    lib.playTrack(tracks[idx + 1]);
  } else if (fromAuto && repeat.value === "all" && tracks.length > 0) {
    lib.playTrack(tracks[0]);
  }
}

function playPrevious(): void {
  if (shuffle.value && shuffleHistory.length > 0) {
    const prevId = shuffleHistory.pop()!;
    const track = lib.filteredTracks.find((t) => t.id === prevId);
    if (track) lib.playTrack(track);
    return;
  }
  const tracks = lib.filteredTracks;
  const idx = tracks.findIndex((t) => t.id === lib.nowPlaying?.id);
  if (idx > 0) lib.playTrack(tracks[idx - 1]);
}

function restart(): void {
  lib.seekTo(0);
}

function handleTrackEnded(): void {
  if (repeat.value === "one" && lib.nowPlaying) {
    lib.playTrack(lib.nowPlaying);
    return;
  }
  playNext(true);
}

let unlistenEnded: (() => void) | null = null;
onMounted(() => { unlistenEnded = lib.onTrackEnded(handleTrackEnded); });
onUnmounted(() => { if (unlistenEnded) unlistenEnded(); });
</script>

<style scoped>
.edge-blur-art {
  width: min(108vmin, 630px);
  height: min(108vmin, 630px);
  background-size: 170%;
  background-position: center;
  background-repeat: no-repeat;
  filter: blur(72px);
  inset: 0;
  margin: auto;
}

.player-volume-slider {
  appearance: none;
  height: 6px;
  border-radius: 9999px;
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--volume-percent, 0%),
    rgb(87 83 78) var(--volume-percent, 0%),
    rgb(87 83 78) 100%
  );
  cursor: pointer;
}
.player-volume-slider::-webkit-slider-thumb {
  appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #5b7c32;
  cursor: pointer;
}
.player-volume-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #5b7c32;
  border: none;
  cursor: pointer;
}
</style>
