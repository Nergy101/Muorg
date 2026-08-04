<template>
  <div
    v-if="player.currentTrack"
    class="absolute inset-0 flex flex-col overflow-hidden"
    :style="{ backgroundColor: glowBgColor }"
  >
    <!-- Dominant-colour backdrop -->
    <div class="pointer-events-none absolute inset-0 z-0" aria-hidden="true">
      <div
        v-if="useEdgeBlurMode && coverUrl"
        class="edge-blur-art absolute"
        :style="{ backgroundImage: `url(${coverUrl})`, opacity: 0.85 }"
      />
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

    <div class="relative z-10 flex min-h-0 flex-1 flex-col">
      <!-- Top row -->
      <div class="flex h-14 shrink-0 items-center px-2">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-white/75"
          aria-label="Close"
          @click="router.back()"
        >
          <FeatherIcon name="chevron-down" class="h-6 w-6" />
        </button>
        <div class="flex-1" />
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full"
          :class="player.sleepTimerActive ? 'text-primary' : 'text-white/55'"
          aria-label="Sleep timer"
          @click="onSleepButton"
        >
          <FeatherIcon name="moon" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-white/75"
          aria-label="Queue"
          @click="router.push({ name: 'queue' })"
        >
          <FeatherIcon name="align-justify" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-white/75"
          aria-label="Track actions"
          @click="showActionsSheet = true"
        >
          <FeatherIcon name="more-vertical" class="h-5 w-5" />
        </button>
      </div>

      <!-- Centre: cover + titles -->
      <div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-6 px-6">
        <div class="relative flex w-[72%] items-center justify-center">
          <div
            class="absolute inset-0 rounded-full"
            :style="{ background: `radial-gradient(circle, rgba(${glowRgb},0.45) 0%, transparent 70%)`, filter: 'blur(32px)' }"
            aria-hidden="true"
          />
          <div class="relative aspect-square w-full overflow-hidden rounded-2xl shadow-2xl">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              :alt="player.currentTrack.album ?? ''"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center bg-black/30">
              <FeatherIcon name="music" class="h-10 w-10 text-white/50" />
            </div>
          </div>
        </div>

        <div class="w-full text-center">
          <MarqueeText :text="player.currentTrack.title ?? '—'" class="text-title-lg text-white" />
          <p class="truncate text-body-md text-white/75">
            {{ player.currentTrack.artist ?? player.currentTrack.album_artist ?? "—" }}
          </p>
          <p class="truncate text-body-sm text-white/55">{{ player.currentTrack.album ?? "—" }}</p>
        </div>
      </div>

      <!-- Seek row -->
      <div class="shrink-0 px-6">
        <div
          ref="seekTrack"
          class="relative h-4 flex items-center"
          @pointerdown="onSeekPointerdown"
        >
          <div class="h-1 w-full rounded-full bg-white/25">
            <div
              class="h-full rounded-full bg-primary"
              :style="{ width: `${displayedFraction * 100}%` }"
            />
          </div>
          <div
            class="absolute h-3.5 w-3.5 -translate-x-1/2 rounded-full bg-primary shadow"
            :style="{ left: `${displayedFraction * 100}%` }"
          />
        </div>
        <div class="flex items-center justify-between pt-1">
          <span class="text-label-md tabular-nums text-white/55">{{ formatDuration(displayedSecs) }}</span>
          <span class="text-label-md tabular-nums text-white/55">
            -{{ formatDuration(Math.max(0, player.durationSecs - displayedSecs)) }}
          </span>
        </div>
      </div>

      <!-- Transport row -->
      <div class="flex shrink-0 items-center justify-center gap-4 px-6 py-4">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center"
          :class="player.shuffleEnabled ? 'text-primary' : 'text-white/75'"
          aria-label="Shuffle"
          @click="player.toggleShuffle()"
        >
          <FeatherIcon name="shuffle" class="h-7 w-7" />
        </button>
        <button
          type="button"
          class="flex h-14 w-14 items-center justify-center text-white"
          aria-label="Previous track"
          @click="player.skipPrevious()"
        >
          <FeatherIcon name="skip-back" class="h-7 w-7" />
        </button>
        <button
          type="button"
          class="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-primary text-on-primary"
          :aria-label="player.isPlaying ? 'Pause' : 'Play'"
          @click="player.playPause()"
        >
          <FeatherIcon :name="player.isPlaying ? 'pause' : 'play'" class="h-8 w-8" />
        </button>
        <button
          type="button"
          class="flex h-14 w-14 items-center justify-center text-white"
          aria-label="Next track"
          @click="player.skipNext()"
        >
          <FeatherIcon name="skip-forward" class="h-7 w-7" />
        </button>
        <button
          type="button"
          class="relative flex h-10 w-10 items-center justify-center"
          :class="player.repeatMode !== 'off' ? 'text-primary' : 'text-white/75'"
          aria-label="Repeat mode"
          @click="player.cycleRepeatMode()"
        >
          <FeatherIcon name="repeat" class="h-7 w-7" />
          <span
            v-if="player.repeatMode === 'one'"
            class="absolute bottom-0.5 right-0.5 text-label-sm font-bold leading-none"
          >1</span>
        </button>
      </div>

      <!-- Volume row -->
      <div class="flex shrink-0 items-center gap-3 px-6 pb-4">
        <FeatherIcon name="volume-1" class="h-4 w-4 shrink-0 text-white/55" />
        <input
          type="range"
          min="0"
          max="1"
          step="0.02"
          class="min-w-0 flex-1"
          :value="player.volume"
          @input="player.setVolume(parseFloat(($event.target as HTMLInputElement).value))"
        />
        <FeatherIcon name="volume-2" class="h-4 w-4 shrink-0 text-white/55" />
      </div>
    </div>

    <BottomSheet :open="showSleepSheet" @close="showSleepSheet = false">
      <div class="px-6 pb-6">
        <h2 class="mb-4 text-title-md text-on-surface">Sleep timer</h2>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="mins in SLEEP_PRESETS"
            :key="mins"
            type="button"
            class="rounded-full bg-surface-variant px-4 py-2 text-label-lg text-on-surface"
            @click="startSleep(mins)"
          >{{ mins }}m</button>
        </div>
      </div>
    </BottomSheet>

    <ConfirmDialog
      :open="showSleepConfirm"
      title="Sleep timer"
      :message="sleepConfirmMessage"
      confirm-label="Turn off"
      cancel-label="Keep"
      @confirm="onSleepTurnOff"
      @cancel="showSleepConfirm = false"
    />

    <TrackActionsSheet
      :open="showActionsSheet"
      :track="player.currentTrack"
      @close="showActionsSheet = false"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import MarqueeText from "../components/MarqueeText.vue";
import BottomSheet from "../components/BottomSheet.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import {
  getGlowBlobs,
  hasOpposingEdgeColors,
  isColorBland,
  useDominantColor,
  useEdgeColors,
} from "../composables/useDominantColor";
import { usePlayerStore } from "../stores/player";
import { albumKeyFor, formatDuration } from "../stores/library";
import type { CatalogTrack } from "../types";

const SLEEP_PRESETS = [5, 10, 15, 20, 30, 45, 60, 90];

const router = useRouter();
const player = usePlayerStore();

onMounted(() => {
  if (!player.currentTrack) void router.replace({ name: "library" });
});

// --- Dominant-colour background (ported from the deleted PlayerBar.vue) ---
const coverUrl = computed(() => player.currentCoverUrl);
const glowRgb = useDominantColor(coverUrl);
const edgeColors = useEdgeColors(coverUrl);
const useEdgeBlurMode = computed(() => {
  if (!coverUrl.value || !edgeColors.value?.colors.length) return false;
  const blandCenterVibrantEdges =
    isColorBland(glowRgb.value) && !isColorBland(edgeColors.value.colors[0]);
  return blandCenterVibrantEdges || hasOpposingEdgeColors(edgeColors.value.bySide);
});
const glowBlobs = computed(() => {
  if (useEdgeBlurMode.value) return [];
  if (isColorBland(glowRgb.value)) return [];
  return getGlowBlobs(glowRgb.value, String(player.currentTrack?.id ?? ""));
});
const glowBgColor = computed(() => {
  const parts = glowRgb.value.split(",").map(Number);
  if (parts.length !== 3) return "#000";
  if (isColorBland(glowRgb.value)) return "#0c0a09";
  const [r, g, b] = parts;
  return `rgb(${Math.round(r * 0.08)},${Math.round(g * 0.08)},${Math.round(b * 0.08)})`;
});

// --- Seek slider ---
const seekTrack = ref<HTMLElement | null>(null);
const seekPreview = ref<number | null>(null);
let seekPointerId: number | null = null;

const displayedFraction = computed(() => seekPreview.value ?? player.progress);
const displayedSecs = computed(() => displayedFraction.value * player.durationSecs);

function fractionFromEvent(e: PointerEvent): number {
  const el = seekTrack.value;
  if (!el) return 0;
  const rect = el.getBoundingClientRect();
  if (rect.width <= 0) return 0;
  return Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
}

function cleanupSeek(): void {
  seekPointerId = null;
  window.removeEventListener("pointermove", onSeekPointermove);
  window.removeEventListener("pointerup", onSeekPointerup);
  window.removeEventListener("pointercancel", onSeekPointerup);
}

function onSeekPointerdown(e: PointerEvent): void {
  seekPointerId = e.pointerId;
  seekPreview.value = fractionFromEvent(e);
  window.addEventListener("pointermove", onSeekPointermove);
  window.addEventListener("pointerup", onSeekPointerup);
  window.addEventListener("pointercancel", onSeekPointerup);
}

function onSeekPointermove(e: PointerEvent): void {
  if (e.pointerId !== seekPointerId) return;
  seekPreview.value = fractionFromEvent(e);
}

function onSeekPointerup(e: PointerEvent): void {
  if (e.pointerId !== seekPointerId) return;
  const fraction = seekPreview.value ?? player.progress;
  cleanupSeek();
  // Hold the preview until the store has moved, otherwise a FLAC seek (which
  // awaits a fresh stream token) flashes back to the old position.
  void player.seekTo(fraction * player.durationSecs).finally(() => {
    seekPreview.value = null;
  });
}

// --- Sleep timer ---
const showSleepSheet = ref(false);
const showSleepConfirm = ref(false);

const sleepConfirmMessage = computed(
  () => `Playback will stop in ${formatDuration(Math.ceil(player.sleepTimerRemainingMs / 1000))}.`,
);

function onSleepButton(): void {
  if (player.sleepTimerActive) showSleepConfirm.value = true;
  else showSleepSheet.value = true;
}

function startSleep(mins: number): void {
  player.startSleepTimer(mins * 60_000);
  showSleepSheet.value = false;
}

function onSleepTurnOff(): void {
  player.cancelSleepTimer();
  showSleepConfirm.value = false;
}

// --- Track actions sheet ---
const showActionsSheet = ref(false);

function onViewArtist(): void {
  const t = player.currentTrack as CatalogTrack | null;
  if (!t) return;
  void router.push({ name: "library", query: { artist: t.artist ?? t.album_artist ?? undefined } });
}

function onViewAlbum(): void {
  const t = player.currentTrack as CatalogTrack | null;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: albumKeyFor(t) } });
}
</script>
