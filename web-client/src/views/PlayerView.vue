<template>
  <div
    v-if="player.currentTrack"
    class="absolute inset-x-0 bottom-0 -top-[env(safe-area-inset-top)] flex flex-col overflow-hidden pt-[env(safe-area-inset-top)]"
    :style="rootStyle"
    @pointerdown="onPlayerPointerdown"
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

    <!-- The glow backdrop above still spans the full shell; only the controls
         column is centred, so seek and transport stay near the art on desktop
         instead of stretching to the edges. Uncapped below md. -->
    <div
      class="relative z-10 flex min-h-0 w-full flex-1 flex-col md:mx-auto md:max-w-[640px] lg:max-w-5xl"
    >
      <!-- Top row -->
      <div class="flex h-14 shrink-0 items-center px-2">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-white/75"
          aria-label="Back"
          @click="router.back()"
        >
          <MageIcon name="chevron-down" class="h-6 w-6" />
        </button>
        <div class="flex-1" />
        <div class="glass-sheer flex items-center overflow-hidden rounded-full">
          <button
            type="button"
            class="flex h-10 items-center gap-1.5 px-1 text-white transition-colors hover:bg-white/10"
            :class="player.sleepTimerActive ? 'text-primary' : ''"
            aria-label="Sleep timer"
            @click="onSleepButton"
          >
            <MageIcon name="moon" class="h-5 w-5" />
            <span
              v-if="player.sleepTimerActive"
              class="pr-1.5 text-label-md tabular-nums"
              :class="player.sleepTimerActive ? 'text-primary' : ''"
            >{{ sleepLabel }}</span>
          </button>
          <button
            v-if="hasLyrics"
            type="button"
            class="flex h-10 w-10 items-center justify-center border-l border-white/20 text-white transition-colors hover:bg-white/10"
            :class="showLyrics ? 'text-primary' : ''"
            :aria-label="showLyrics ? 'Show cover art' : 'Show lyrics'"
            @click="showLyrics = !showLyrics"
          >
            <MageIcon name="note-text" class="h-5 w-5" />
          </button>
          <button
            type="button"
            class="flex h-10 w-10 items-center justify-center border-l border-white/20 text-white transition-colors hover:bg-white/10"
            aria-label="Track actions"
            @click="openTrackActions"
          >
            <MageIcon name="dots" class="h-5 w-5" />
          </button>
        </div>
      </div>

      <!-- Desktop (lg) is a two-pane layout: art left, info + controls right.
           Mobile keeps the stacked column — art centred, controls below. -->
      <div
        class="flex min-h-0 flex-1 flex-col lg:flex-row lg:items-center lg:gap-12 lg:px-10 lg:pb-6"
      >
        <!-- Centre: cover only. `max-h-full` keeps the square from overflowing a
             short viewport; object-cover absorbs the crop if it ever clamps. -->
        <!-- Lyrics pane (replaces cover art when toggled on) -->
        <div
          v-if="showLyrics && hasLyrics"
          class="flex min-h-0 flex-1 items-center justify-center px-6 lg:w-[44%] lg:flex-none lg:px-0"
        >
          <div class="max-h-full w-full overflow-y-auto py-4 text-center">
            <template v-if="isSynced">
              <p
                v-for="(l, i) in lrcLines"
                :key="i"
                ref="lyricLineEls"
                class="py-1.5 transition-all duration-200"
                :class="
                  i === activeLyricIndex
                    ? 'text-lg font-semibold text-white'
                    : 'text-white/45'
                "
              >{{ l.text }}</p>
            </template>
            <p
              v-else
              class="whitespace-pre-line px-4 text-body-md leading-relaxed text-white/75"
            >{{ lyrics?.lyrics }}</p>
          </div>
        </div>

        <div
          v-else
          class="flex min-h-0 flex-1 items-center justify-center px-6 lg:w-[44%] lg:flex-none lg:px-0"
        >
          <!-- 50vh cap: on a wide shell 86% would be taller than the centre column,
               so the art would clamp to a cropped rectangle. Never binds on phones. -->
          <div
            class="relative flex max-h-full w-[86%] max-w-[50vh] items-center justify-center lg:w-full lg:max-w-[420px]"
          >
            <div
              class="absolute inset-0 rounded-full"
              :style="{ background: `radial-gradient(circle, rgba(${glowRgb},0.45) 0%, transparent 70%)`, filter: 'blur(32px)' }"
              aria-hidden="true"
            />
            <div class="relative aspect-square max-h-full w-full overflow-hidden rounded-2xl shadow-2xl">
              <img
                v-if="coverUrl"
                :src="coverUrl"
                :alt="player.currentTrack.album ?? ''"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center bg-black/30">
                <MageIcon name="music" class="h-10 w-10 text-white/50" />
              </div>
            </div>
          </div>
        </div>

        <!-- Info + controls -->
        <div class="flex shrink-0 flex-col lg:w-[56%] lg:shrink lg:gap-4">
          <!-- Track info bottom-left, quick actions opposite, both above the seek bar -->
          <div class="flex shrink-0 items-end gap-3 px-6 pb-3 lg:items-center lg:px-0 lg:pb-0">
            <div class="min-w-0 flex-1">
              <MarqueeText
                :text="player.currentTrack.title ?? '—'"
                class="text-title-lg text-white lg:text-headline-sm"
              />
              <p class="truncate text-body-md text-white/75">
                {{ player.currentTrack.artist ?? player.currentTrack.album_artist ?? "—" }}
              </p>
              <p class="truncate text-body-sm text-white/55">{{ player.currentTrack.album ?? "—" }}</p>
            </div>
            <div class="flex shrink-0 items-center gap-1">
              <button
                type="button"
                class="flex h-10 w-10 items-center justify-center rounded-full text-white/75 transition-colors hover:bg-white/10 hover:text-white"
                aria-label="Queue"
                @click="router.push({ name: 'player-queue' })"
              >
                <MageIcon name="stack" class="h-6 w-6" />
              </button>
              <button
                type="button"
                class="flex h-10 w-10 items-center justify-center rounded-full text-white/75 transition-colors hover:bg-white/10 hover:text-white"
                aria-label="Add to playlist"
                @click="openAddToPlaylist"
              >
                <MageIcon name="playlist-add" class="h-6 w-6" />
              </button>
              <button
                type="button"
                class="flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-white/10"
                :class="[isFavorite ? 'text-primary' : 'text-white/75 hover:text-white', heartPulse ? 'heart-pop' : '']"
                :aria-label="isFavorite ? 'Remove from favorites' : 'Add to favorites'"
                @click="onToggleFavorite"
                @animationend="heartPulse = false"
              >
                <!-- CSS `fill` beats the presentation attribute feather emits, so the
                     heart reads as solid once favourited. -->
                <MageIcon name="heart" class="h-6 w-6" :class="isFavorite ? 'fill-current' : ''" />
              </button>
            </div>
          </div>

          <!-- Seek row -->
          <div class="shrink-0 px-6 lg:px-0">
            <div
              ref="seekTrack"
              data-no-dismiss
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
          <div
            class="flex shrink-0 items-center justify-center gap-4 px-6 pt-4 pb-[max(env(safe-area-inset-bottom),1rem)] lg:gap-5 lg:px-0 lg:pb-0"
          >
            <button
              type="button"
              class="flex h-10 w-10 items-center justify-center transition-colors hover:text-white"
              :class="player.shuffleEnabled ? 'text-primary' : 'text-white/75'"
              aria-label="Shuffle"
              @click="player.toggleShuffle()"
            >
              <MageIcon name="exchange" class="h-7 w-7" />
            </button>
            <button
              type="button"
              class="flex h-14 w-14 items-center justify-center text-white transition-transform hover:scale-110"
              aria-label="Previous track"
              @click="player.skipPrevious()"
            >
              <MageIcon name="previous" class="h-7 w-7" />
            </button>
            <button
              type="button"
              class="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-primary text-on-primary transition-transform hover:scale-105"
              :aria-label="player.isPlaying ? 'Pause' : 'Play'"
              @click="player.playPause()"
            >
              <MageIcon :name="player.isPlaying ? 'pause' : 'play'" class="h-8 w-8" />
            </button>
            <button
              type="button"
              class="flex h-14 w-14 items-center justify-center text-white transition-transform hover:scale-110"
              aria-label="Next track"
              @click="player.skipNext()"
            >
              <MageIcon name="next" class="h-7 w-7" />
            </button>
            <button
              type="button"
              class="relative flex h-10 w-10 items-center justify-center transition-colors hover:text-white"
              :class="player.repeatMode !== 'off' ? 'text-primary' : 'text-white/75'"
              aria-label="Repeat mode"
              @click="player.cycleRepeatMode()"
            >
              <MageIcon name="reload" class="h-7 w-7" />
              <span
                v-if="player.repeatMode === 'one'"
                class="absolute bottom-0.5 right-0.5 text-label-sm font-bold leading-none"
              >1</span>
            </button>
          </div>
        </div>
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
      :initial-level="actionsInitialLevel"
      :track="player.currentTrack"
      @close="showActionsSheet = false"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import MarqueeText from "../components/MarqueeText.vue";
import BottomSheet from "../components/BottomSheet.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { getTrackLyrics, type TrackLyrics } from "../api/catalog";
import {
  getGlowBlobs,
  hasOpposingEdgeColors,
  isColorBland,
  useDominantColor,
  useEdgeColors,
} from "../composables/useDominantColor";
import { useSwipeDown } from "../composables/useSwipeDown";
import { usePlayerStore } from "../stores/player";
import { formatDuration, useLibraryStore } from "../stores/library";
import type { CatalogTrack } from "../types";

const SLEEP_PRESETS = [5, 10, 15, 20, 30, 45, 60, 90];

const router = useRouter();
const player = usePlayerStore();
const lib = useLibraryStore();

// --- Lyrics ---------------------------------------------------------------
// Embedded lyrics are fetched from the server per track; when the track has
// LRC timing lines the pane scrolls karaoke-style to the active line.
const lyrics = ref<TrackLyrics | null>(null);
const showLyrics = ref(false);
const lyricLineEls = ref<HTMLElement[]>([]);
const lrcLines = ref<{ time: number; text: string }[]>([]);

function parseLrc(text: string): { time: number; text: string }[] {
  const out: { time: number; text: string }[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const m = raw.trim().match(/^\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\](.*)$/);
    if (!m) continue;
    const mins = parseInt(m[1], 10);
    const secs = parseInt(m[2], 10);
    const frac = m[3] ? parseInt(m[3].padEnd(3, "0").slice(0, 3), 10) / 1000 : 0;
    const text = m[4].trim();
    if (text) out.push({ time: mins * 60 + secs + frac, text });
  }
  return out.sort((a, b) => a.time - b.time);
}

const hasLyrics = computed(() => lyrics.value != null);
const isSynced = computed(
  () => lyrics.value?.sync_format === "lrc" && lrcLines.value.length > 0,
);

const activeLyricIndex = computed(() => {
  if (!isSynced.value) return -1;
  const t = player.positionSecs;
  let idx = -1;
  for (let i = 0; i < lrcLines.value.length; i++) {
    if (lrcLines.value[i].time <= t) idx = i;
    else break;
  }
  return idx;
});

watch(activeLyricIndex, (i) => {
  lyricLineEls.value[i]?.scrollIntoView({ block: "center", behavior: "smooth" });
});

watch(
  () => player.currentTrack?.id,
  (id) => {
    showLyrics.value = false;
    lrcLines.value = [];
    lyrics.value = null;
    if (id == null) return;
    void getTrackLyrics(id).then((l) => {
      lyrics.value = l;
      if (l?.sync_format === "lrc") lrcLines.value = parseLrc(l.lyrics);
    });
  },
  { immediate: true },
);

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

// --- Swipe down to dismiss ---
// The sheet is a modal; the shell's edge-back gesture deliberately skips it,
// so the dismiss gesture lives here. The inline transform is only applied
// while a drag is in progress — a persistent one would override the
// modal-in/modal-out transition classes and kill the rise/fall animation.
const swipeDown = useSwipeDown({
  enabled: () => true,
  onCommit: () => router.back(),
});
const swipeProgress = swipeDown.progress;
const rootStyle = computed(() => ({
  ...(swipeProgress.value > 0
    ? { transform: `translateY(${Math.round(swipeProgress.value * 64)}px)` }
    : {}),
  backgroundColor: glowBgColor.value,
}));

function onPlayerPointerdown(e: PointerEvent): void {
  // The seek bar handles its own drags; don't arm a dismiss on top of them.
  if ((e.target as HTMLElement).closest("[data-no-dismiss]")) return;
  swipeDown.onPointerdown(e);
}

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

const sleepLabel = computed(() => {
  const total = Math.ceil(player.sleepTimerRemainingMs / 1000);
  return `${Math.floor(total / 60)}m ${total % 60}s`;
});

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
/** The "+" jumps straight to the sheet's playlist picker. */
const actionsInitialLevel = ref<"main" | "playlists">("main");

function openTrackActions(): void {
  actionsInitialLevel.value = "main";
  showActionsSheet.value = true;
}

function openAddToPlaylist(): void {
  actionsInitialLevel.value = "playlists";
  showActionsSheet.value = true;
}

// --- Favourite ---
const isFavorite = computed(() =>
  player.currentTrack ? player.favorites.has(player.currentTrack.id) : false,
);

/** One-shot pop while the heart is being favourited (cleared by animationend). */
const heartPulse = ref(false);

function onToggleFavorite(): void {
  const t = player.currentTrack;
  if (!t) return;
  // Bounce only on favouriting, not on un-favouriting.
  if (!player.favorites.has(t.id)) heartPulse.value = true;
  // The store owns the optimistic flip and the revert on failure.
  void player.toggleFavorite(t);
}

function onViewArtist(): void {
  const t = player.currentTrack as CatalogTrack | null;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = player.currentTrack as CatalogTrack | null;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}
</script>
