<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import type { CatalogTrack } from "../../types";
import { navigate } from "../../state/navigation";
import { vMarquee } from "../../composables/useMarquee";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../../composables/useAlbums";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useCastStore } from "../../stores/cast";
import { usePlaylistStore } from "../../stores/playlists";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import * as castApi from "../../api/cast";
import { flacSeekOffset, peekPlayFuture, playHistory, stepBackPlayHistory } from "../../state/playback";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import VolumeControl from "./VolumeControl.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import CastButton from "./CastButton.vue";
import StarRating from "../shared/StarRating.vue";
import LyricsPane from "./LyricsPane.vue";

const props = withDefaults(
  defineProps<{
    /** When true, hide the expand (fullscreen) button (e.g. when already in overlay). */
    hideExpand?: boolean;
    /** When true, use fullscreen layout: massive centered art, gradient background, controls at bottom. */
    expandedLayout?: boolean;
    /** CSS vars for accent hues (--player-accent, --player-accent-play, etc.). Bland => primary; vivid => album variants. */
    accentStyle?: Record<string, string>;
    /** When set (Player/Queue tab), bottom panel height in px; used to scale album art with panel. */
    panelHeightPx?: number;
  }>(),
  { panelHeightPx: undefined }
);

const emit = defineEmits<{
  (e: "expand"): void;
  (e: "minimize"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const castStore = useCastStore();
const { selectedTracks, tableOrderedTracks, queueTracks } = storeToRefs(store);
const { shuffle, repeat, continuousPlayback, playbarShowAlbumInMarquee, playbarShowRatingInMaximized } = storeToRefs(settingsStore);
const { isCasting } = storeToRefs(castStore);

/** Album art size in px when panel height is provided; scales with panel up to full available height. */
const playbarAlbumArtSizePx = computed(() => {
  const h = props.panelHeightPx;
  if (h == null || h <= 0) return undefined;
  const reserved = 156; // handle(4) + padding(24) + gaps(16) + title(20) + controls(60) + progress(32)
  return Math.max(128, h - reserved);
});

/** List used for next/previous: queue takes priority over shuffle; shuffle applies to table when queue empty. */
const playbackList = computed(() => {
  if (queueTracks.value.length > 0) return queueTracks.value;
  return tableOrderedTracks.value;
});

function getNextTrack(): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  // Q31: after Previous, Next returns through what was stepped back over.
  const futureId = peekPlayFuture((id) => store.tracks.some((t) => t.id === id));
  if (futureId != null) {
    const futureTrack = store.tracks.find((t) => t.id === futureId);
    if (futureTrack) return futureTrack;
  }
  // Shuffle only applies when the queue is empty; queue tracks always play in order.
  if (shuffle.value && queueTracks.value.length === 0) {
    const others = list.filter((t) => t.id !== current.id);
    return others.length > 0 ? others[Math.floor(Math.random() * others.length)] : null;
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx >= 0 && idx + 1 < list.length) return list[idx + 1];
  if (idx < 0 && list.length > 0) return list[0];
  if (idx >= 0 && idx === list.length - 1 && continuousPlayback.value) {
    const table = tableOrderedTracks.value;
    if (table.length === 0) return null;
    const tableIdx = table.findIndex((t) => t.id === current.id);
    if (tableIdx >= 0 && tableIdx + 1 < table.length) return table[tableIdx + 1];
    return table[0];
  }
  return null;
}

function getPreviousTrack(): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  if (shuffle.value) {
    const idx = list.findIndex((t) => t.id === current.id);
    if (idx <= 0) return null;
    return list[idx - 1];
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx > 0) return list[idx - 1];
  return null;
}

/** Q31: Previous is available with play history to step back to, or a table neighbour. */
const canPlayPrevious = computed(() => {
  if (!singleTrack.value) return false;
  if (playHistory.value.some((id) => store.tracks.some((t) => t.id === id))) return true;
  return getPreviousTrack() != null;
});

const isPlaying = ref(false);
const currentTime = ref(0);

// ── Lyrics ─────────────────────────────────────────────────────────────────
// The pane reports whether the current track actually has lyrics, so the
// toggle only appears when there is something to show. Switching tracks
// resets to the cover.
const hasLyrics = ref(false);
const showLyrics = ref(false);

function onLyricsAvailability(available: boolean) {
  hasLyrics.value = available;
  if (!available) showLyrics.value = false;
}
const duration = ref(0);
const isSeeking = ref(false);

function getAudio(): HTMLAudioElement | null {
  return document.querySelector('audio[data-muorg-player="true"]') as HTMLAudioElement | null;
}

function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function syncFromAudio() {
  const el = getAudio();
  if (!el) return;
  if (!isSeeking.value) {
    // When casting, only advance position if cast is confirmed playing
    if (!isCasting.value || castStore.castStatus.status === "playing") {
      currentTime.value = el.currentTime + flacSeekOffset.value;
    }
  }
  duration.value = Number.isFinite(el.duration) ? el.duration : duration.value;
  isPlaying.value = !el.paused;
}

function seekTo(secs: number) {
  const el = getAudio();
  currentTime.value = secs;
  if (isCasting.value) {
    const wasPlaying = el ? !el.paused : false;
    if (el && wasPlaying) el.pause();
    if (el) el.currentTime = secs;
    if (wasPlaying) castStore.setPendingCastResume(true);
    castApi.castSeek(secs, wasPlaying).catch(console.error);
  } else {
    // Delegate to PlayerBar which handles both MP3 range seeks and FLAC stream reloads.
    window.dispatchEvent(new CustomEvent<number>("muorg:seek-to", { detail: secs }));
  }
}

function onSeekInput(e: Event) {
  // Update position visually while dragging — actual seek fires on mouse-up.
  currentTime.value = parseFloat((e.target as HTMLInputElement).value);
}

const PROGRESS_THUMB_HALF = 6;

function onProgressBarClick(e: MouseEvent) {
  const input = e.currentTarget as HTMLInputElement;
  const rect = input.getBoundingClientRect();
  const trackWidth = rect.width - 2 * PROGRESS_THUMB_HALF;
  if (trackWidth <= 0) return;
  const x = e.clientX - rect.left - PROGRESS_THUMB_HALF;
  const ratio = Math.max(0, Math.min(1, x / trackWidth));
  const d = displayDuration.value;
  if (!d || !Number.isFinite(d)) return;
  seekTo(ratio * d);
}

function onSeekMouseDown() {
  isSeeking.value = true;
}

function onSeekMouseUp() {
  seekTo(currentTime.value);
  isSeeking.value = false;
}

const singleTrack = computed(() => {
  const tracks = selectedTracks.value;
  return tracks.length === 1 ? tracks[0] : null;
});

const playbarTitleLine = computed(() => {
  const t = singleTrack.value;
  if (!t) return "";
  const parts: string[] = [];
  const baseTitle = t.title || t.path.split(/[/\\]/).pop() || "Track";
  if (baseTitle) parts.push(baseTitle);
  if (playbarShowAlbumInMarquee.value && t.album) parts.push(t.album);
  if (t.artist) parts.push(t.artist);
  return parts.join(" · ");
});

const displayDuration = computed(() => {
  const fromTrack = singleTrack.value?.duration_secs;
  if (fromTrack != null && Number.isFinite(fromTrack) && fromTrack >= 0) return fromTrack;
  return duration.value;
});

const progressPercent = computed(() => {
  const d = displayDuration.value;
  if (!d || !Number.isFinite(d)) return 0;
  return Math.min(100, (currentTime.value / d) * 100);
});

function attachAudioListeners() {
  const el = getAudio();
  if (!el) return;
  // Remove first to avoid duplicates if the element was recreated.
  el.removeEventListener("timeupdate", syncFromAudio);
  el.removeEventListener("play", syncFromAudio);
  el.removeEventListener("pause", syncFromAudio);
  el.removeEventListener("loadedmetadata", syncFromAudio);
  el.removeEventListener("durationchange", syncFromAudio);
  el.removeEventListener("volumechange", syncFromAudio);
  el.removeEventListener("ended", onAudioEnded);
  el.addEventListener("timeupdate", syncFromAudio);
  el.addEventListener("play", syncFromAudio);
  el.addEventListener("pause", syncFromAudio);
  el.addEventListener("loadedmetadata", syncFromAudio);
  el.addEventListener("durationchange", syncFromAudio);
  el.addEventListener("volumechange", syncFromAudio);
  el.addEventListener("ended", onAudioEnded);
}

watch(
  singleTrack,
  (track) => {
    if (!track) return;
    // The audio element lives inside PlayerBar's v-if="singleTrack". When the selection
    // changes the element may be destroyed and recreated, so we must re-attach listeners
    // after Vue has flushed the DOM update.
    nextTick(() => {
      attachAudioListeners();
      syncFromAudio();
    });
  },
  { immediate: true },
);

function togglePlay() {
  const el = getAudio();
  if (!el) return;
  if (el.paused) {
    el.play().catch(() => {});
    if (isCasting.value) castApi.castResume().catch(console.error);
  } else {
    el.pause();
    if (isCasting.value) castApi.castPause().catch(console.error);
  }
  isPlaying.value = !el.paused;
}

function onAudioEnded() {
  isPlaying.value = false;
}

function playQueueTrack(t: CatalogTrack) {
  store.setSelection([t.id]);
  store.setPlayRequestTrackId(t.id);
}

/** Queue sidepanel collapsed state (chevron toggles it). */
const queueCollapsed = ref(false);

/** Artist/album titles in the panel are links to those pages. */
function goArtist() {
  const name = (singleTrack.value?.artist ?? singleTrack.value?.album_artist ?? "").trim();
  if (!name) return;
  emit("minimize");
  navigate({ name: "artist", key: name });
}
function goAlbum() {
  const t = singleTrack.value;
  if (!t) return;
  emit("minimize");
  navigate({ name: "album", key: albumIdentityKey(t.album, albumArtistOf(t)) });
}

/** 3-dots / add-to-playlist: open the context menu at the button, with the
 *  playlist submenu open for the add-to-playlist button. */
// ── Format chip specs popover (bitrate from file size / duration) ──────────
const formatPopover = ref<{ x: number; y: number; bitrateKbps: number | null; sizeBytes: number | null } | null>(null);

async function onFormatHover(e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const t = singleTrack.value;
  if (!t) return;
  let sizeBytes: number | null;
  try {
    const { stat } = await import("@tauri-apps/plugin-fs");
    const meta = await stat(t.path);
    sizeBytes = typeof meta.size === "number" ? meta.size : null;
  } catch {
    sizeBytes = null;
  }
  const dur = displayDuration.value;
  const bitrateKbps = sizeBytes != null && dur > 0 ? Math.round((sizeBytes * 8) / dur / 1000) : null;
  formatPopover.value = { x: rect.left + rect.width / 2, y: rect.top, bitrateKbps, sizeBytes };
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function playNext() {
  const next = getNextTrack();
  if (!next) return;
  const queueIdx = store.queueTrackIds.indexOf(next.id);
  if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
  store.setPlayRequestTrackId(next.id);
  store.clearSelection();
  store.toggleSelection(next.id);
}

function playPrevious() {
  const current = singleTrack.value;
  if (!current) return;
  // Q31: step back through what was actually played, not the table neighbour.
  const backId = stepBackPlayHistory(current.id, (id) => store.tracks.some((t) => t.id === id));
  const target = backId != null
    ? store.tracks.find((t) => t.id === backId) ?? null
    : getPreviousTrack();
  if (!target) return;
  store.setPlayRequestTrackId(target.id);
  store.clearSelection();
  store.toggleSelection(target.id);
}

function restart() {
  const el = getAudio();
  if (!el) return;
  el.currentTime = 0;
  if (isPlaying.value) el.play().catch(() => {});
}

async function setRating(rating: number | null) {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  await store.setRating([track.path], rating);
}

// Context menu
const playlistStore = usePlaylistStore();
const { playlists } = storeToRefs(playlistStore);
/** Smart playlists are computed, not stored — never offer them as add targets. */
const menuPlaylists = computed(() => playlists.value.filter((p) => !p.smart_rules));
const { tryAddToPlaylist } = usePlaylistAdd();

const contextMenu = ref<{ x: number; y: number } | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);
const playlistSubmenuOpen = ref(false);
const playlistBtnContainerRef = ref<HTMLElement | null>(null);
const trackPlaylistIds = ref<Set<number>>(new Set());
const playlistSubmenuFlipUp = computed(() => {
  if (!playlistBtnContainerRef.value) return false;
  const rect = playlistBtnContainerRef.value.getBoundingClientRect();
  return rect.bottom + 220 > window.innerHeight;
});
const playlistsAlreadyIn = computed(() => menuPlaylists.value.filter((p) => trackPlaylistIds.value.has(p.id)));
const playlistsNotYetIn = computed(() => menuPlaylists.value.filter((p) => !trackPlaylistIds.value.has(p.id)));

watch(playlistSubmenuOpen, async (open) => {
  if (!open) { trackPlaylistIds.value = new Set(); return; }
  const track = singleTrack.value;
  if (!track) return;
  const ids = await playlistStore.getPlaylistsForTrack(track.id);
  trackPlaylistIds.value = new Set(ids);
});

function onTitleContextMenu(e: MouseEvent) {
  e.preventDefault();
  if (!singleTrack.value) return;
  contextMenu.value = { x: e.clientX, y: e.clientY };
}

function closeContextMenu() {
  contextMenu.value = null;
  playlistSubmenuOpen.value = false;
}

watch(contextMenu, (menu) => {
  if (!menu) return;
  const onOutside = (e: MouseEvent) => {
    if (contextMenuRef.value?.contains(e.target as Node)) return;
    closeContextMenu();
    document.removeEventListener("click", onOutside);
    document.removeEventListener("keydown", onEscapeMenu);
  };
  const onEscapeMenu = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      closeContextMenu();
      document.removeEventListener("click", onOutside);
      document.removeEventListener("keydown", onEscapeMenu);
    }
  };
  nextTick(() => setTimeout(() => {
    document.addEventListener("click", onOutside);
    document.addEventListener("keydown", onEscapeMenu);
  }, 0));
});

function viewSong() {
  const track = singleTrack.value;
  if (!track) return;
  store.setRevealTrackId(track.id);
  closeContextMenu();
}

function addToQueue() {
  const track = singleTrack.value;
  if (!track) return;
  store.addToQueue([track.id]);
  closeContextMenu();
}

/** Q21: favorite state of the current track. */
const isFavorite = computed(() => (singleTrack.value ? playlistStore.isFavorite(singleTrack.value.id) : false));

function toggleFavorite() {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  void playlistStore.toggleFavorite(track.id);
}

async function addToPlaylist(playlistId: number) {
  const track = singleTrack.value;
  if (!track) return;
  const playlist = playlists.value.find((p) => p.id === playlistId);
  closeContextMenu();
  await tryAddToPlaylist(playlistId, [track.id], playlist?.name ?? "");
}

type TooltipPosition = "below" | "top-left";
const tooltipPopover = ref<{ text: string; x: number; y: number; position?: TooltipPosition } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

onMounted(() => {
  nextTick(() => {
    attachAudioListeners();
    syncFromAudio();
  });
});

onUnmounted(() => {
  const el = getAudio();
  if (el) {
    el.removeEventListener("timeupdate", syncFromAudio);
    el.removeEventListener("play", syncFromAudio);
    el.removeEventListener("pause", syncFromAudio);
    el.removeEventListener("loadedmetadata", syncFromAudio);
    el.removeEventListener("durationchange", syncFromAudio);
    el.removeEventListener("volumechange", syncFromAudio);
    el.removeEventListener("ended", onAudioEnded);
  }
});
</script>

<template>
  <!-- Expanded fullscreen layout: uses mpx-* classes only; all styling in #maximized-player-overlay section (dark theme) -->
  <div
    v-if="singleTrack && expandedLayout"
    class="mpx-root"
    :style="accentStyle"
  >
    <div class="mpx-content">
      <!-- Exit fullscreen: chevron-down centered at the top, above the album art -->
      <button
        type="button"
        class="mpx-close"
        aria-label="Minimize player"
        title="Minimize player"
        @click="emit('minimize')"
      >
        <FeatherIcon name="chevron-down" class="h-6 w-6" />
      </button>
      <LyricsPane
        v-show="showLyrics && hasLyrics"
        :track-id="singleTrack?.id ?? null"
        :position-secs="currentTime"
        @availability="onLyricsAvailability"
      />
      <TrackAlbumArt
        v-if="singleTrack"
        v-show="!(showLyrics && hasLyrics)"
        :path="singleTrack.path"
        size="xlarge"
        class="mpx-art"
      />
      <!-- Star rating under the centered album art (setting: show rating on maximized player) -->
      <div v-if="playbarShowRatingInMaximized" class="mpx-rating-under-art">
        <StarRating :model-value="singleTrack?.rating ?? null" @update:model-value="setRating" />
      </div>
      <button
        v-if="hasLyrics"
        type="button"
        class="mpx-lyrics-toggle"
        :class="{ 'mpx-lyrics-toggle--active': showLyrics }"
        :aria-pressed="showLyrics"
        :aria-label="showLyrics ? 'Show album art' : 'Show lyrics'"
        @click="showLyrics = !showLyrics"
      >
        <FeatherIcon name="file-text" class="h-3.5 w-3.5" />
        {{ showLyrics ? "Album art" : "Lyrics" }}
      </button>
    </div>
    <!-- Web-client style: centered glass panel with controls left, queue sidepanel right -->
    <div class="mpx-bar">
      <div class="mpx-panel" :class="{ 'mpx-panel--collapsed': queueCollapsed }">
        <div class="mpx-panel-main">
          <!-- Top row: flat format chip (left), centered title, queue chevron (right) -->
          <div class="mpx-top-row">
            <span
              v-if="singleTrack.format"
              class="mpx-format"
              @mouseenter="onFormatHover"
              @mouseleave="formatPopover = null"
            >
              <FeatherIcon name="activity" class="h-3 w-3" />
              {{ singleTrack.format.toUpperCase() }}
            </span>
            <span v-else />
            <!-- Specs popover on the format chip -->
            <Teleport to="body">
              <div
                v-if="formatPopover"
                class="fixed z-[400] w-64 rounded-2xl bg-surface p-3 text-xs text-on-surface shadow-xl ring-1 ring-outline/30"
                :style="{ left: formatPopover.x + 'px', top: formatPopover.y + 'px', transform: 'translate(-50%, -110%)' }"
              >
                <div class="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">Track specs</div>
                <div class="flex flex-col gap-1">
                  <div class="flex justify-between gap-2"><span class="text-on-surface-variant">Format</span><span class="selectable">{{ singleTrack?.format?.toUpperCase() }}</span></div>
                  <div class="flex justify-between gap-2"><span class="text-on-surface-variant">Duration</span><span>{{ formatTime(displayDuration) }}</span></div>
                  <div v-if="formatPopover.bitrateKbps != null" class="flex justify-between gap-2"><span class="text-on-surface-variant">Bitrate</span><span>{{ formatPopover.bitrateKbps }} kbps</span></div>
                  <div v-if="formatPopover.sizeBytes != null" class="flex justify-between gap-2"><span class="text-on-surface-variant">File size</span><span>{{ formatBytes(formatPopover.sizeBytes) }}</span></div>
                  <div class="flex justify-between gap-2"><span class="text-on-surface-variant">Plays</span><span>{{ singleTrack?.play_count ?? 0 }}</span></div>
                  <div class="selectable mt-1 break-all font-mono text-[10px] text-on-surface-variant">{{ singleTrack?.path }}</div>
                </div>
              </div>
            </Teleport>
            <div class="mpx-panel-title truncate" :title="playbarTitleLine" @contextmenu.prevent="onTitleContextMenu">
              {{ singleTrack.title ?? "Unknown Title" }}
            </div>
            <!-- Expand-only when collapsed (no stack icon next to the title);
                 collapsing happens via the chevron-left + stack button inside
                 the sidepanel header -->
            <button
              v-if="queueCollapsed"
              type="button"
              class="mpx-icon-btn mpx-queue-toggle"
              aria-label="Show queue"
              title="Show queue"
              @click="queueCollapsed = false"
            >
              <FeatherIcon name="layers" class="h-4 w-4" />
              <FeatherIcon name="chevron-right" class="h-4 w-4 -ml-1" />
            </button>
            <span v-else />
          </div>
          <!-- Mid row: artist/album links left, volume centered, action icons right -->
          <div class="mpx-mid-row">
            <div class="mpx-panel-sub">
              <button type="button" class="mpx-link marq" v-marquee @click="goArtist">
                <FeatherIcon name="user" class="inline h-3 w-3 align-[-2px] shrink-0" />
                <span class="marq-in">{{ singleTrack.artist ?? singleTrack.album_artist ?? "" }}</span>
              </button>
              <button v-if="singleTrack.album" type="button" class="mpx-link marq" v-marquee @click="goAlbum">
                <FeatherIcon name="disc" class="inline h-3 w-3 align-[-2px] shrink-0" />
                <span class="marq-in">{{ singleTrack.album }}</span>
              </button>
            </div>
            <div class="mpx-volume-wrap">
              <VolumeControl mode="playscreen" />
            </div>
            <div class="mpx-icon-row">
              <button type="button" class="mpx-icon-btn" :class="{ 'mpx-icon-btn--active': isFavorite }" :aria-label="isFavorite ? 'Remove from favorites' : 'Add to favorites'" :aria-pressed="isFavorite" @click="toggleFavorite">
                <FeatherIcon :name="isFavorite ? 'heart-fill' : 'heart'" class="h-5 w-5" />
              </button>
              <button v-if="hasLyrics" type="button" class="mpx-icon-btn" :class="{ 'mpx-icon-btn--active': showLyrics }" :aria-label="showLyrics ? 'Show album art' : 'Show lyrics'" @click="showLyrics = !showLyrics">
                <FeatherIcon name="file-text" class="h-5 w-5" />
              </button>
              <CastButton />
              <button type="button" class="mpx-icon-btn" aria-label="More actions" title="More actions" @click="onTitleContextMenu">
                <FeatherIcon name="more-horizontal" class="h-5 w-5" />
              </button>
            </div>
          </div>
          <div class="mpx-row">
            <span class="mpx-time mpx-time-current">{{ formatTime(currentTime) }}</span>
            <input type="range" min="0" :max="displayDuration > 0 ? displayDuration : 0.01" step="0.1" :value="currentTime" class="mpx-progress" :style="{ '--progress-percent': progressPercent + '%' }" aria-label="Seek" @click="onProgressBarClick" @input="onSeekInput" @mousedown="onSeekMouseDown" @mouseup="onSeekMouseUp" @mouseleave="onSeekMouseUp" />
            <span class="mpx-time mpx-time-total">-{{ formatTime(Math.max(0, displayDuration - currentTime)) }}</span>
          </div>
          <div class="mpx-actions">
            <button type="button" class="mpx-shuffle-btn" :class="{ 'mpx-shuffle-btn--active': shuffle }" aria-label="Shuffle" :aria-pressed="shuffle" @click="settingsStore.setShuffle(!shuffle)">
              <FeatherIcon name="shuffle" class="h-5 w-5" />
            </button>
            <button type="button" class="mpx-nav-btn" aria-label="Previous track" @click="playPrevious" :disabled="!canPlayPrevious">
              <FeatherIcon name="skip-back" class="h-6 w-6" />
            </button>
            <button type="button" class="mpx-play-btn" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="togglePlay">
              <FeatherIcon v-if="!isPlaying" name="play" class="h-7 w-7" />
              <FeatherIcon v-else name="pause" class="h-7 w-7" />
            </button>
            <button type="button" class="mpx-nav-btn" aria-label="Next track" @click="playNext" :disabled="!singleTrack || !getNextTrack()">
              <FeatherIcon name="skip-forward" class="h-6 w-6" />
            </button>
            <button type="button" class="mpx-shuffle-btn relative" :class="{ 'mpx-shuffle-btn--active': repeat !== 'none' }" :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'" @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')">
              <FeatherIcon name="repeat" class="h-5 w-5" />
              <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
            </button>
          </div>
        </div>
        <!-- Queue sidepanel -->
        <div v-if="!queueCollapsed" class="mpx-queue">
          <div class="mpx-queue-header">
            <button
              type="button"
              class="mpx-icon-btn mpx-queue-collapse"
              aria-label="Hide queue"
              title="Hide queue"
              @click="queueCollapsed = true"
            >
              <FeatherIcon name="chevron-left" class="h-4 w-4" />
              <FeatherIcon name="layers" class="h-4 w-4 -ml-1" />
            </button>
            <span>Queue</span>
            <span class="mpx-queue-count">{{ queueTracks.length }} tracks</span>
          </div>
          <div class="mpx-queue-list">
            <div
              v-for="(t, i) in queueTracks"
              :key="t.id"
              class="mpx-queue-item"
              :class="{ 'mpx-queue-item--playing': t.id === store.currentPlayingTrackId }"
              @click="playQueueTrack(t)"
            >
              <FeatherIcon name="menu" class="mpx-queue-drag h-4 w-4 shrink-0 text-white/40" />
              <TrackAlbumArt :path="t.path" size="small" class="mpx-queue-art" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-[13px] font-medium text-white">{{ t.title ?? "Unknown Title" }}</div>
                <div class="truncate text-xs text-white/60">{{ t.artist ?? t.album_artist ?? "" }}</div>
              </div>
              <button
                type="button"
                class="mpx-queue-remove"
                aria-label="Remove from queue"
                @click.stop="store.removeFromQueueAtIndex(i)"
              >
                <FeatherIcon name="x" class="h-3.5 w-3.5" />
              </button>
            </div>
            <div v-if="!queueTracks.length" class="px-3 py-4 text-center text-xs text-white/50">
              Queue is empty.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Default bottom-panel layout -->
  <div
    v-else-if="singleTrack"
    class="flex shrink-0 flex-col items-center gap-1"
  >
    <div class="flex w-full flex-col items-center gap-1 bg-surface-container/80">
      <div
        class="flex flex-col items-center gap-1 cursor-context-menu rounded-lg px-2 pt-2 transition-colors hover:bg-surface-variant/40"
        @contextmenu.prevent="onTitleContextMenu"
      >
        <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="large" :size-px="playbarAlbumArtSizePx" />
        <div
          class="max-w-2xl truncate text-center text-sm font-semibold text-on-surface"
          :title="playbarTitleLine"
        >
          {{ playbarTitleLine }}
        </div>
      </div>
      <div class="flex items-center justify-center gap-2">
        <button
          type="button"
          class="flex items-center justify-center rounded-full bg-surface-container/80 p-3 text-on-surface hover:bg-surface-variant hover:text-on-surface disabled:opacity-40"
          aria-label="Previous track"
          @click="playPrevious"
          :disabled="!canPlayPrevious"
        >
          <FeatherIcon name="skip-back" class="h-6 w-6" />
        </button>
        <button
          type="button"
          class="player-play-btn flex items-center justify-center rounded-full bg-primary p-4 text-on-primary shadow-lg hover:bg-primary/80"
          :aria-label="isPlaying ? 'Pause' : 'Play'"
          @click="togglePlay"
        >
          <FeatherIcon v-if="!isPlaying" name="play" class="h-7 w-7" />
          <FeatherIcon v-else name="pause" class="h-7 w-7" />
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded-full bg-surface-container/80 p-3 text-on-surface hover:bg-surface-variant hover:text-on-surface disabled:opacity-40"
          aria-label="Next track"
          @click="playNext"
          :disabled="!singleTrack || !getNextTrack()"
        >
          <FeatherIcon name="skip-forward" class="h-6 w-6" />
        </button>
      </div>
      <div class="mt-1 flex w-full items-center gap-3 pl-4">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <div class="flex shrink-0 items-center gap-1">
            <button
              type="button"
              class="flex items-center justify-center rounded bg-surface-container/80 p-2 text-on-surface hover:bg-surface-variant hover:text-on-surface"
              aria-label="Restart from beginning"
              @click="restart"
            >
              <FeatherIcon name="square" class="h-4 w-4" />
            </button>
            <span class="w-10 shrink-0 text-right text-xs text-on-surface-variant tabular-nums">
              {{ formatTime(currentTime) }}
            </span>
          </div>
          <input
            type="range"
            min="0"
            :max="displayDuration > 0 ? displayDuration : 0.01"
            step="0.1"
            :value="currentTime"
            class="player-progress-slider h-1.5 min-w-0 flex-1 cursor-pointer appearance-none rounded-full bg-surface-variant [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
            :style="{ '--progress-percent': progressPercent + '%' }"
            aria-label="Seek"
            @click="onProgressBarClick"
            @input="onSeekInput"
            @mousedown="onSeekMouseDown"
            @mouseup="onSeekMouseUp"
            @mouseleave="onSeekMouseUp"
          />
          <span class="w-10 shrink-0 text-left text-xs text-on-surface-variant tabular-nums">
            {{ formatTime(displayDuration) }}
          </span>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <button
            type="button"
            class="flex shrink-0 items-center justify-center rounded p-2 hover:bg-surface-variant hover:text-on-surface"
            :class="shuffle ? 'shuffle-active-bg text-on-primary' : 'text-on-surface-variant'"
            aria-label="Shuffle next track"
            :aria-pressed="shuffle"
            @click="settingsStore.setShuffle(!shuffle)"
          >
            <FeatherIcon name="shuffle" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="relative flex shrink-0 items-center justify-center rounded p-2 hover:bg-surface-variant hover:text-on-surface"
            :class="repeat !== 'none' ? 'shuffle-active-bg text-on-primary' : 'text-on-surface-variant'"
            :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'"
            @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')"
          >
            <FeatherIcon name="repeat" class="h-4 w-4" />
            <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
          </button>
          <button
            type="button"
            class="flex shrink-0 items-center justify-center rounded p-2 hover:bg-surface-variant hover:text-on-surface"
            :class="isFavorite ? 'text-primary' : 'text-on-surface-variant'"
            :aria-label="isFavorite ? 'Remove from favorites' : 'Add to favorites'"
            :aria-pressed="isFavorite"
            title="Favorite"
            @click="toggleFavorite"
          >
            <FeatherIcon name="heart" class="h-4 w-4" />
          </button>
          <VolumeControl mode="playscreen" />
          <CastButton size="md" />
          <button
            v-if="!hideExpand"
            type="button"
            class="flex items-center justify-center rounded p-2 text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
            aria-label="Expand player"
            @click="emit('expand')"
          >
            <FeatherIcon name="maximize-2" class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
    <Teleport to="body">
      <div
        v-if="contextMenu"
        ref="contextMenuRef"
        class="fixed z-[400] min-w-[160px] glass-strong rounded-2xl py-1 shadow-xl"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px', transform: 'translateY(-100%)' }"
        @click.stop
      >
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
          @click="viewSong"
        >
          <FeatherIcon name="list" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          View song
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
          @click="addToQueue"
        >
          <FeatherIcon name="clock" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          Add to queue
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
          @click="toggleFavorite"
        >
          <FeatherIcon
            name="heart"
            class="h-4 w-4 shrink-0"
            :class="isFavorite ? 'text-primary' : 'text-on-surface-variant'"
          />
          {{ isFavorite ? "Remove from favorites" : "Add to favorites" }}
        </button>
        <div class="my-1 border-t border-outline" />
        <div
          ref="playlistBtnContainerRef"
          class="relative"
          @mouseenter="playlistSubmenuOpen = true"
          @mouseleave="playlistSubmenuOpen = false"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
            @click="playlistSubmenuOpen = !playlistSubmenuOpen"
          >
            <span class="flex items-center gap-2">
              <FeatherIcon name="plus" class="h-4 w-4 shrink-0 text-on-surface-variant" />
              Add to playlist
            </span>
            <FeatherIcon name="chevron-right" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant" />
          </button>
          <div class="absolute right-0 top-0 h-full w-2 translate-x-full" />
          <div
            v-if="playlistSubmenuOpen && menuPlaylists.length"
            class="absolute left-full z-[310] min-w-[160px] max-w-[220px] glass-strong rounded-2xl py-1 shadow-xl"
            :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
            style="margin-left: 2px"
          >
            <!-- Playlists the track is already in -->
            <template v-if="playlistsAlreadyIn.length">
              <button
                v-for="pl in playlistsAlreadyIn"
                :key="pl.id"
                type="button"
                class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
                @click="addToPlaylist(pl.id)"
              >
                <PlaylistIcon v-if="pl.icon" :icon="pl.icon" class="h-3.5 w-3.5 shrink-0" />
                <FeatherIcon v-else name="list" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant" />
                <span class="min-w-0 flex-1 truncate">{{ pl.name }}</span>
                <FeatherIcon name="check" class="h-3.5 w-3.5 shrink-0 text-primary" />
              </button>
              <div v-if="playlistsNotYetIn.length" class="my-1 border-t border-outline" />
            </template>
            <!-- Playlists not yet containing the track -->
            <button
              v-for="pl in playlistsNotYetIn"
              :key="pl.id"
              type="button"
              class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-variant hover:text-on-primary"
              @click="addToPlaylist(pl.id)"
            >
              <PlaylistIcon v-if="pl.icon" :icon="pl.icon" class="h-3.5 w-3.5 shrink-0" />
              <FeatherIcon v-else name="list" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant" />
              <span class="min-w-0 truncate">{{ pl.name }}</span>
            </button>
          </div>
          <div
            v-else-if="playlistSubmenuOpen && !menuPlaylists.length"
            class="absolute left-full z-[310] min-w-[160px] glass-strong rounded-2xl px-3 py-2 shadow-xl text-xs text-on-surface-variant"
            :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
            style="margin-left: 2px"
          >
            No playlists yet.
          </div>
        </div>
        <div class="my-1 border-t border-outline" />
        <div class="flex items-center justify-center px-3 py-2">
          <StarRating
            :model-value="singleTrack?.rating ?? null"
            @update:model-value="setRating"
          />
        </div>
      </div>
    </Teleport>
  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[200] glass-strong rounded-2xl px-3 py-2 text-xs text-on-surface shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
      :style="tooltipPopover.position === 'top-left'
        ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateY(-100%)' }
        : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
</template>

<style scoped>
.player-volume-slider,
.player-progress-slider {
  accent-color: #5b7c32;
}
.player-volume-slider::-webkit-slider-thumb,
.player-progress-slider::-webkit-slider-thumb {
  background: #5b7c32;
}
.player-progress-slider {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--progress-percent, 0%),
    rgb(87 83 78) var(--progress-percent, 0%),
    rgb(87 83 78) 100%
  ) !important;
  border-radius: 9999px;
}
.player-progress-slider::-webkit-slider-runnable-track {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--progress-percent, 0%),
    rgb(87 83 78) var(--progress-percent, 0%),
    rgb(87 83 78) 100%
  );
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-progress {
  background: #5b7c32;
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-track {
  background: rgb(87 83 78);
  border-radius: 9999px;
}

</style>

