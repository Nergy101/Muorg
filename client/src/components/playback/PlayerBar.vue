<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useCastStore } from "../../stores/cast";
import { usePlaylistStore } from "../../stores/playlists";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import { navigate } from "../../state/navigation";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../../composables/useAlbums";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import StarRating from "../shared/StarRating.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import { needsTranscode } from "@shared/formats";
import CastButton from "./CastButton.vue";
import type { CatalogTrack, TrackMetadataRead } from "../../types";
import * as catalogApi from "../../api/catalog";
import * as castApi from "../../api/cast";
import { streamUrl } from "../../api/client";
import {
  flacSeekOffset,
  isPlayingNow,
  peekPlayFuture,
  playHistory,
  recordPlayHistory,
  stepBackPlayHistory,
} from "../../state/playback";

const emit = defineEmits<{
  (e: "expand"): void;
  (e: "openQueue"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const castStore = useCastStore();
const { isCasting } = storeToRefs(castStore);
const { selectedTracks, tableOrderedTracks, queueTracks } = storeToRefs(store);
const {
  autoplayOnSelect,
  continuousPlayback,
  shuffle,
  repeat,
  playbarShowAlbumInMarquee,
  volume,
} = storeToRefs(settingsStore);

/** List used for next/previous: queue takes priority over shuffle; shuffle applies to table when queue empty. */
const playbackList = computed(() => {
  if (queueTracks.value.length > 0) return queueTracks.value;
  return tableOrderedTracks.value;
});

function getNextTrack(forAutoAdvance = false): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  // Q31: after Previous, Next returns through what was stepped back over.
  const futureId = peekPlayFuture((id) => store.tracks.some((t) => t.id === id));
  if (futureId != null) {
    const futureTrack = store.tracks.find((t) => t.id === futureId);
    if (futureTrack) return futureTrack;
  }
  if (forAutoAdvance && repeat.value === "one") return current;
  // Shuffle only applies when the queue is empty; queue tracks always play in order.
  if (shuffle.value && queueTracks.value.length === 0) {
    const others = list.filter((t) => t.id !== current.id);
    return others.length > 0 ? others[Math.floor(Math.random() * others.length)] : null;
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx >= 0 && idx + 1 < list.length) return list[idx + 1];
  if (idx < 0 && list.length > 0) return list[0];
  if (forAutoAdvance && repeat.value === "all") return list[0];
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

const audioRef = ref<HTMLAudioElement | null>(null);
const audioElementKey = ref(0);
const isPlaying = ref(false);
// Mirror to module state so the library table can animate the playing row.
watch(isPlaying, (v) => {
  isPlayingNow.value = v;
});
const audioSrc = ref("");
const currentTime = ref(0);
const duration = ref(0);
const isSeeking = ref(false);
let shouldAutoplayNextSelection = false;
let playRecordedForCurrentTrack = false;
const hasInitializedAutoplay = ref(false);
let unlistenCastTrackEnded: (() => void) | null = null; // set in onMounted via castStore.onTrackEnded
const replayGainMeta = ref<TrackMetadataRead | null>(null);

/** Small cache of preloaded audio blobs (next track, maybe a couple more). Keyed by track path. */
const audioCache = new Map<string, string>();

const titleMarqueeRef = ref<HTMLDivElement | null>(null);
const artistMarqueeRef = ref<HTMLDivElement | null>(null);
const shouldScrollTitle = ref(false);
const titleMarqueeDistance = ref(0);
const shouldScrollArtist = ref(false);
const artistMarqueeDistance = ref(0);

const titlePopover = ref<{ text: string; x: number; y: number } | null>(null);
let titlePopoverHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTitlePopover(text: string, e: MouseEvent) {
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  titlePopover.value = { text, x: rect.left + rect.width / 2, y: rect.top - 8 };
}

function scheduleHideTitlePopover() {
  titlePopoverHideTimeout = setTimeout(() => {
    titlePopover.value = null;
    titlePopoverHideTimeout = null;
  }, 80);
}

function cancelHideTitlePopover() {
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
}

function hideTitlePopover() {
  titlePopover.value = null;
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
}

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

function onAlbumArtContextMenu(e: MouseEvent) {
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

function viewAlbum() {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  navigate({ name: "album", key: albumIdentityKey(track.album, albumArtistOf(track)) });
}

function viewArtist() {
  const track = singleTrack.value;
  if (!track) return;
  const name = albumArtistOf(track);
  closeContextMenu();
  if (name && name !== "Unknown Artist") navigate({ name: "artist", key: name });
}


async function setRating(rating: number | null) {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  await store.setRating([track.path], rating);
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

let positionSaveTimer: ReturnType<typeof setTimeout> | null = null;

function schedulePositionSave(positionSecs: number) {
  if (positionSaveTimer) clearTimeout(positionSaveTimer);
  positionSaveTimer = setTimeout(() => {
    positionSaveTimer = null;
    settingsStore.setSessionState(
      store.queueTrackIds,
      store.currentPlayingTrackId,
      positionSecs,
    );
  }, 5000);
}

function onTimeUpdate() {
  if (isSeeking.value) return;
  const el = audioRef.value;
  if (!el) return;
  // When casting, only advance the progress bar when cast is confirmed playing
  if (isCasting.value && castStore.castStatus.status !== "playing") return;
  const effectiveTime = el.currentTime + flacSeekOffset.value;
  currentTime.value = effectiveTime;
  if ("mediaSession" in navigator && Number.isFinite(el.duration) && el.duration > 0) {
    navigator.mediaSession.setPositionState({
      duration: el.duration + flacSeekOffset.value,
      position: effectiveTime,
      playbackRate: el.playbackRate,
    });
  }
  // Record play after 30 s of continuous playback.
  if (!playRecordedForCurrentTrack && effectiveTime >= 30 && singleTrack.value) {
    playRecordedForCurrentTrack = true;
    store.recordPlay(singleTrack.value.path);
  }
  // Save position every ~5 s (timer resets on each tick, so only fires after a 5 s idle)
  schedulePositionSave(effectiveTime);
}

function onDurationChange() {
  const el = audioRef.value;
  if (el) duration.value = Number.isFinite(el.duration) ? el.duration : 0;
}

let _seekSeq = 0;

async function seekToFlac(track: CatalogTrack, secs: number) {
  const seq = ++_seekSeq;
  const wasPlaying = !audioRef.value?.paused;

  audioRef.value?.pause();
  flacSeekOffset.value = secs;
  audioSrc.value = "";

  try {
    const token = await catalogApi.issueStreamToken(track.id);
    if (seq !== _seekSeq) return;

    const newSrc = streamUrl(track.id, token, secs);
    audioSrc.value = newSrc;

    // Increment the key to force Vue to destroy the old <audio> element and
    // mount a fresh one. This is the only reliable way to make WKWebView release
    // a chunked HTTP stream — changing src or calling load() alone leaves the
    // old network connection alive in WebKit's internal media pipeline.
    audioElementKey.value++;
    await nextTick();

    const el = audioRef.value;
    if (!el || seq !== _seekSeq) return;

    applyEffectiveVolume();
    el.muted = isCasting.value;
    el.load();

    if (wasPlaying) {
      el.addEventListener("canplay", () => {
        if (seq === _seekSeq) el.play().catch(console.error);
      }, { once: true });
    }
  } catch (e) {
    if (seq !== _seekSeq) return;
    console.error("[seekToFlac]", e);
  }
}

function seekToMp3(secs: number) {
  const el = audioRef.value;
  if (!el) return;
  const seq = ++_seekSeq;
  const wasPlaying = !el.paused;
  // WKWebView enters a "seeking" state when currentTime is set and does not
  // always auto-resume — explicitly call play() and fall back to canplay.
  el.currentTime = secs;
  if (wasPlaying) {
    el.play().catch(() => {
      el.addEventListener("canplay", () => {
        if (seq === _seekSeq) el.play().catch(console.error);
      }, { once: true });
    });
  }
}

function seekTo(secs: number) {
  const el = audioRef.value;
  currentTime.value = secs;

  if (isCasting.value) {
    // Pause local audio, seek both, wait for cast to confirm playing before resuming
    const wasPlaying = el ? !el.paused : false;
    if (el && wasPlaying) el.pause();
    if (el) el.currentTime = secs;
    if (wasPlaying) castStore.setPendingCastResume(true);
    castApi.castSeek(secs, wasPlaying).catch(console.error);
  } else if (singleTrack.value && needsTranscode(singleTrack.value.format)) {
    seekToFlac(singleTrack.value, secs).catch(console.error);
  } else if (el) {
    seekToMp3(secs);
  }
}

// True when the user has dragged (input event fired) since the last mousedown.
// Used to distinguish click-seeks (handled by onProgressBarClick) from
// drag-seeks (handled by onSeekMouseUp) so we don't issue two conflicting
// el.currentTime assignments per click on WKWebView.
let seekHadInput = false;

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
  seekHadInput = false;
}

function onSeekMouseUp() {
  // Only fire seekTo here for drag operations. mouseup fires before click, so
  // for a plain click currentTime.value is still the old position — let
  // onProgressBarClick handle it with the correct clicked position instead.
  if (isSeeking.value && seekHadInput) seekTo(currentTime.value);
  isSeeking.value = false;
  seekHadInput = false;
}

const singleTrack = computed(() => {
  const tracks = selectedTracks.value;
  return tracks.length === 1 ? tracks[0] : null;
});

const marqueeTitle = computed(() => {
  const t = singleTrack.value;
  if (!t) return "";
  return t.title || t.path.split(/[/\\]/).pop() || "Track";
});

const marqueeArtist = computed(() => {
  const t = singleTrack.value;
  if (!t) return "";
  const parts: string[] = [];
  if (t.artist) parts.push(t.artist);
  if (playbarShowAlbumInMarquee.value && t.album) parts.push(t.album);
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

function recomputeMarquee() {
  nextTick(() => {
    const titleEl = titleMarqueeRef.value;
    if (titleEl) {
      const diff = titleEl.scrollWidth - titleEl.clientWidth;
      if (diff > 4) {
        shouldScrollTitle.value = true;
        titleMarqueeDistance.value = diff;
      } else {
        shouldScrollTitle.value = false;
        titleMarqueeDistance.value = 0;
      }
    } else {
      shouldScrollTitle.value = false;
      titleMarqueeDistance.value = 0;
    }
    const artistEl = artistMarqueeRef.value;
    if (artistEl) {
      const diff = artistEl.scrollWidth - artistEl.clientWidth;
      if (diff > 4) {
        shouldScrollArtist.value = true;
        artistMarqueeDistance.value = diff;
      } else {
        shouldScrollArtist.value = false;
        artistMarqueeDistance.value = 0;
      }
    } else {
      shouldScrollArtist.value = false;
      artistMarqueeDistance.value = 0;
    }
  });
}

function revokeUrl(url: string | null) {
  if (!url) return;
  try {
    URL.revokeObjectURL(url);
  } catch {
    // ignore
  }
}

function evictOldCacheEntries(maxEntries = 3) {
  while (audioCache.size > maxEntries) {
    const firstKey = audioCache.keys().next().value as string | undefined;
    if (!firstKey) break;
    const url = audioCache.get(firstKey) ?? null;
    audioCache.delete(firstKey);
    revokeUrl(url);
  }
}

async function updateMediaSession(track: CatalogTrack | null) {
  if (!("mediaSession" in navigator)) return;
  if (!track) {
    navigator.mediaSession.metadata = null;
    navigator.mediaSession.playbackState = "none";
    return;
  }
  await store.fetchCover(track.path);
  const coverUrl = store.getCoverDataUrl(track.path);
  const artwork: MediaImage[] = coverUrl ? [{ src: coverUrl }] : [];
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.title || track.path.split(/[/\\]/).pop() || "Track",
    artist: track.artist || "",
    album: track.album || "",
    artwork,
  });
}

async function loadAudioBlobForCurrent(track: CatalogTrack) {
  const path = track.path;
  const cached = audioCache.get(path);

  if (audioRef.value) {
    audioRef.value.pause();
  }
  // Don't clear audioSrc to "" here — on WKWebView, blanking the src resets the
  // media element's autoplay permission so el.play() fires the play event but
  // stalls silently with no audio output.
  if (audioSrc.value && audioSrc.value !== cached) {
    revokeUrl(audioSrc.value);
  }
  isPlaying.value = false;
  currentTime.value = 0;
  duration.value = 0;
  flacSeekOffset.value = 0;

  if (cached) {
    audioSrc.value = cached;
    audioCache.delete(path);
    return;
  }

  // Issue a short-lived stream token and use it as the audio src.
  try {
    const token = await catalogApi.issueStreamToken(track.id);
    audioSrc.value = streamUrl(track.id, token);
  } catch {
    audioSrc.value = "";
  }
}

async function preloadTrack(track: CatalogTrack | null) {
  if (!track) return;
  const path = track.path;
  if (audioCache.has(path) || path === singleTrack.value?.path) return;
  // Pre-issue a stream token so the next track can start immediately.
  try {
    const token = await catalogApi.issueStreamToken(track.id);
    audioCache.set(path, streamUrl(track.id, token));
    evictOldCacheEntries();
  } catch {
    // ignore preload failures; playback path will issue a fresh token.
  }
}

async function preloadNextTrack() {
  const next = getNextTrack();
  if (!next) return;
  await preloadTrack(next);
}

watch(
  singleTrack,
  (track, oldTrack) => {
    // Reset the play-recorded flag whenever the track changes.
    if (track?.path !== oldTrack?.path) {
      playRecordedForCurrentTrack = false;
      // Q31: remember what was actually played so Previous can step back through it.
      recordPlayHistory(oldTrack?.id ?? null, track?.id ?? null);
    }
    if (!track) {
      if (audioSrc.value) {
        revokeUrl(audioSrc.value);
        audioSrc.value = "";
      }
      isPlaying.value = false;
      store.setCurrentPlaying(null);
      shouldScrollTitle.value = false;
      titleMarqueeDistance.value = 0;
      shouldScrollArtist.value = false;
      artistMarqueeDistance.value = 0;
      updateMediaSession(null);
      return;
    }
    store.setCurrentPlaying(track.id);
    updateMediaSession(track);
    recomputeMarquee();
    catalogApi.getMetadata(track.id)
      .then((m) => {
        replayGainMeta.value = m ?? null;
        applyEffectiveVolume();
      })
      .catch(() => {
        replayGainMeta.value = null;
        applyEffectiveVolume();
      });

    // Same file path means only metadata changed (e.g. after a metadata save + catalog
    // reload). Audio is already loaded and possibly playing — don't interrupt it.
    if (track.path === oldTrack?.path) return;

    // If a cast device is configured, load the new track on it and hold local
    // audio until the cast confirms it's playing (pendingCastResume).
    const castDeviceId = castStore.connectedDeviceId;
    if (castDeviceId) {
      const castDevice = castStore.discoveredDevices.find((d) => d.id === castDeviceId);
      if (castDevice) {
        castStore.setPendingCastResume(true);
        castApi
          .castPlay(track.id, castDevice.address, castDevice.port)
          .then((sessionId) => castStore.setSessionId(sessionId))
          .catch((e: unknown) => {
            console.error("[Cast] cast_play on track change:", e);
            castStore.setPendingCastResume(false);
          });
      }
    }

    loadAudioBlobForCurrent(track).then(() => {
      if (!hasInitializedAutoplay.value) {
        hasInitializedAutoplay.value = true;
        // On startup, restore saved position if this is the previously playing track.
        const savedId = settingsStore.sessionCurrentTrackId;
        const savedPos = settingsStore.sessionCurrentPositionSecs;
        if (savedId === track.id && savedPos > 0) {
          nextTick(() => {
            const el = audioRef.value;
            if (el) el.currentTime = savedPos;
          });
        }
        return;
      }
      const shouldAutoplay =
        autoplayOnSelect.value || shouldAutoplayNextSelection || store.playRequestTrackId === track.id;
      if (store.playRequestTrackId === track.id) store.setPlayRequestTrackId(null);
      if (!shouldAutoplay) return;
      shouldAutoplayNextSelection = false;
      nextTick(() => {
        const el = audioRef.value;
        if (!el) return;
        if (castStore.pendingCastResume) return; // cast will trigger play when it's ready
        // If we're casting and the cast already confirmed playing (pendingCastResume was
        // cleared before the blob finished loading), sync position before starting.
        if (isCasting.value) {
          const s = castStore.castStatus;
          if (s.status === "playing" && s.positionSecs != null) {
            el.currentTime = s.positionSecs;
          }
        }
        // Call load() so WKWebView fully reinitializes the media pipeline after src
        // changes — without it, play() can fire the play event but produce no audio.
        el.load();
        el.addEventListener("canplay", () => el.play().catch(console.error), { once: true });
      });
      // Once the current track is ready, start preloading the upcoming one in the background.
      preloadNextTrack();
    });
  },
  { immediate: true }
);

// Mute local audio while casting — it keeps playing for position tracking
watch(isCasting, (casting) => {
  const el = audioRef.value;
  if (el) el.muted = casting;
});

// Sync local audio state with cast device state — deep watch to catch position updates too
watch(
  () => castStore.castStatus,
  (s) => {
    const el = audioRef.value;
    if (!el) return;
    if (s.status === "playing") {
      if (castStore.pendingCastResume) {
        // Cast confirmed playing after a seek or track change — sync position and resume local tracker
        castStore.setPendingCastResume(false);
        if (s.positionSecs != null) el.currentTime = s.positionSecs;
        el.play().catch(console.error);
      } else if (el.paused) {
        // Cast started playing without a pending resume (e.g. initial device selection) — sync and start tracker
        if (s.positionSecs != null) el.currentTime = s.positionSecs;
        el.play().catch(console.error);
      } else if (s.positionSecs != null) {
        // Periodic position update while playing — re-sync if drift exceeds 2 s
        const drift = Math.abs(el.currentTime - s.positionSecs);
        if (drift > 2) el.currentTime = s.positionSecs;
      }
    } else if (s.status === "paused" && !castStore.pendingCastResume) {
      // Device paused externally (e.g., voice command) — mirror to local and sync position
      if (!el.paused) el.pause();
      if (s.positionSecs != null) el.currentTime = s.positionSecs;
    }
  },
  { deep: true },
);

function togglePlay() {
  const el = audioRef.value;
  if (!el) return;
  // Use isPlaying (not el.paused) as the toggle source — on WKWebView, el.paused can be
  // false while producing no audio, which would send the wrong branch.
  if (!isPlaying.value) {
    el.play().catch(() => {
      el.addEventListener("canplay", () => el.play().catch(console.error), { once: true });
    });
    isPlaying.value = true;
    if (isCasting.value) castApi.castResume().catch(console.error);
  } else {
    el.pause();
    isPlaying.value = false;
    if (isCasting.value) castApi.castPause().catch(console.error);
  }
}

function onAudioPlay() {
  isPlaying.value = true;
  // Cast commands are only sent from explicit user actions (togglePlay/seekTo), not audio events.
  // Audio elements fire play/pause for buffering, loading, and seeking — mirroring those
  // to the cast device causes intermittent pause/resume storms.
  if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "playing";
}

function dbToLinear(db: number): number {
  return Math.pow(10, db / 20);
}

function getReplayGainDb(): number {
  if (settingsStore.replayGainMode === "off" || !replayGainMeta.value) return 0;
  const rg =
    settingsStore.replayGainMode === "album"
      ? replayGainMeta.value.replaygain_album_gain_db
      : replayGainMeta.value.replaygain_track_gain_db;
  const withPreamp = (rg ?? 0) + (settingsStore.replayGainPreampDb ?? 0);
  return Number.isFinite(withPreamp) ? withPreamp : 0;
}

function applyEffectiveVolume() {
  const el = audioRef.value;
  if (!el) return;
  const base = Math.min(1, Math.max(0, volume.value ?? 0.25));
  let effective = base * dbToLinear(getReplayGainDb());
  if (settingsStore.replayGainPreventClipping) effective = Math.min(1, effective);
  el.volume = Math.max(0, Math.min(1, effective));
}

function onAudioPause() {
  isPlaying.value = false;
  if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "paused";
  // Flush position save immediately on pause
  if (positionSaveTimer) {
    clearTimeout(positionSaveTimer);
    positionSaveTimer = null;
  }
  settingsStore.setSessionState(
    store.queueTrackIds,
    store.currentPlayingTrackId,
    (audioRef.value?.currentTime ?? 0) + flacSeekOffset.value,
  );
}

function onAudioEnded() {
  isPlaying.value = false;
  // Stop auto-advance only when queue is empty, shuffle is off, continuous playback is off, and no repeat.
  if (store.queueTrackIds.length === 0 && !shuffle.value && !continuousPlayback.value && repeat.value === "none") return;
  const next = getNextTrack(true);
  if (!next) return;
  if (next.id !== singleTrack.value?.id) {
    const queueIdx = store.queueTrackIds.indexOf(next.id);
    if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
    store.clearSelection();
    store.toggleSelection(next.id);
  } else {
    // repeat one: restart in place
    seekTo(0);
    nextTick(() => audioRef.value?.play().catch(() => {}));
    return;
  }
  shouldAutoplayNextSelection = true;
}

function playNext() {
  const next = getNextTrack();
  if (!next) return;
  const queueIdx = store.queueTrackIds.indexOf(next.id);
  if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
  shouldAutoplayNextSelection = true;
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
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(target.id);
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key !== "Enter" || !singleTrack.value) return;
  const target = e.target as HTMLElement;
  const tag = target.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable) return;
  e.preventDefault();
  togglePlay();
}

function onExternalSeek(e: Event) {
  seekTo((e as CustomEvent<number>).detail);
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalKeydown);
  window.addEventListener("muorg:seek-to", onExternalSeek);
  recomputeMarquee();
  window.addEventListener("resize", recomputeMarquee);
  nextTick(() => {
    const el = audioRef.value;
    if (!el) return;
    applyEffectiveVolume();
    el.muted = isCasting.value;
  });

  // When cast finishes the track naturally, advance the queue just like onAudioEnded
  unlistenCastTrackEnded = castStore.onTrackEnded(() => {
    onAudioEnded();
  });
  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("play", () => audioRef.value?.play().catch(() => {}));
    navigator.mediaSession.setActionHandler("pause", () => audioRef.value?.pause());
    navigator.mediaSession.setActionHandler("previoustrack", () => playPrevious());
    navigator.mediaSession.setActionHandler("nexttrack", () => playNext());
    navigator.mediaSession.setActionHandler("seekto", (details) => {
      if (details.seekTime != null) seekTo(details.seekTime);
    });
  }
});

watch([volume, () => settingsStore.replayGainMode, () => settingsStore.replayGainPreampDb, () => settingsStore.replayGainPreventClipping], () => {
  applyEffectiveVolume();
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  window.removeEventListener("muorg:seek-to", onExternalSeek);
  window.removeEventListener("resize", recomputeMarquee);
  if (unlistenCastTrackEnded) unlistenCastTrackEnded();
  // Cleanup any cached object URLs to avoid leaks.
  revokeUrl(audioSrc.value);
  audioSrc.value = "";
  for (const url of audioCache.values()) {
    revokeUrl(url);
  }
  audioCache.clear();
});
</script>

<template>
  <div
    v-if="singleTrack"
    class="player-bar grid min-h-[72px] shrink-0 items-center gap-3 px-4 py-2"
    style="grid-template-columns: 1fr auto 1fr"
  >
    <!-- Progress as a thin rounded line on the panel's bottom border, in the accent color (click/drag to seek) -->
    <div
      class="absolute inset-x-4 bottom-0 h-[3px] cursor-pointer overflow-hidden rounded-full"
      role="slider"
      aria-label="Seek"
      :aria-valuenow="Math.round(progressPercent)"
      @click="onProgressBarClick"
      @mousedown="onSeekMouseDown"
      @mousemove="onProgressBarClick"
      @mouseup="onSeekMouseUp"
      @mouseleave="onSeekMouseUp"
    >
      <div class="absolute inset-0" :style="{ background: 'color-mix(in srgb, var(--p-on) 22%, transparent)' }" />
      <div
        class="absolute inset-y-0 left-0 rounded-r-full"
        :style="{ width: progressPercent + '%', background: 'color-mix(in srgb, var(--primary) 65%, white)' }"
      />
    </div>
    <audio
      :key="audioElementKey"
      ref="audioRef"
      :src="audioSrc"
      class="hidden"
      data-muorg-player="true"
      @play="onAudioPlay"
      @pause="onAudioPause"
      @ended="onAudioEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onDurationChange"
      @durationchange="onDurationChange"
      @error="isPlaying = false"
    />

    <!-- Left: now playing widget -->
    <div class="flex min-w-0 items-center gap-3">
      <div
        class="flex shrink-0 cursor-pointer items-center gap-2 rounded-lg px-1 py-0.5 transition-colors hover:bg-white/10"
        title="Maximize player"
        @click="emit('expand')"
        @contextmenu.prevent="onAlbumArtContextMenu"
      >
        <TrackAlbumArt :path="singleTrack.path" :size-px="64" />
        <div class="min-w-0 flex flex-col gap-0.5">
          <div
            ref="titleMarqueeRef"
            class="marq w-44 text-[13px] font-semibold leading-tight"
            :class="{ go: shouldScrollTitle }"
            :style="{ '--marq-dur': Math.max(4, titleMarqueeDistance / 12) + 's', '--marq-box': '176px' }"
          >
            <span
              class="marq-in"
              :style="{ color: 'var(--p-on)' }"
              @mouseenter="showTitlePopover(marqueeTitle, $event)"
              @mouseleave="scheduleHideTitlePopover"
            >
              {{ marqueeTitle }}
            </span>
          </div>
          <div
            v-if="marqueeArtist"
            ref="artistMarqueeRef"
            class="marq w-44 text-xs leading-tight"
            :class="{ go: shouldScrollArtist }"
            :style="{ '--marq-dur': Math.max(4, artistMarqueeDistance / 12) + 's', '--marq-box': '176px' }"
          >
            <span
              class="marq-in"
              :style="{ color: 'var(--p-dim)' }"
              @mouseenter="showTitlePopover(marqueeArtist, $event)"
              @mouseleave="scheduleHideTitlePopover"
            >
              {{ marqueeArtist }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Center: transport -->
    <div class="flex h-12 items-center gap-1">
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          :class="{ 'pb-toggle-on': shuffle }"
          aria-label="Shuffle next track"
          :aria-pressed="shuffle"
          @click="settingsStore.setShuffle(!shuffle)"
        >
          <FeatherIcon name="shuffle" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          aria-label="Previous track"
          :disabled="!canPlayPrevious"
          @click="playPrevious"
        >
          <FeatherIcon name="skip-back" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="pb-btn pb-play"
          :aria-label="isPlaying ? 'Pause' : 'Play'"
          @click="togglePlay"
        >
          <FeatherIcon v-if="!isPlaying" name="play" class="ml-0.5 h-5 w-5" />
          <FeatherIcon v-else name="pause" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          aria-label="Next track"
          :disabled="!singleTrack || !getNextTrack()"
          @click="playNext"
        >
          <FeatherIcon name="skip-forward" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="pb-btn relative !h-8 !w-8"
          :class="{ 'pb-toggle-on': repeat !== 'none' }"
          :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'"
          @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')"
        >
          <FeatherIcon name="repeat" class="h-4 w-4" />
          <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
        </button>
    </div>

    <!-- Right: icon row (favorite, queue, cast, expand) -->
    <div class="flex h-12 items-center justify-end gap-1.5">
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          :aria-label="isFavorite ? 'Remove from favorites' : 'Add to favorites'"
          :aria-pressed="isFavorite"
          title="Favorite"
          @click="toggleFavorite"
        >
          <FeatherIcon
            :name="isFavorite ? 'heart-fill' : 'heart'"
            class="h-4 w-4"
            :style="isFavorite ? { color: 'var(--p-strong)' } : {}"
          />
        </button>
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          aria-label="Queue"
          title="Queue"
          @click="emit('openQueue')"
        >
          <FeatherIcon name="layers" class="h-4 w-4" />
        </button>
        <div class="player-bar-cast [&_button]:!text-[var(--p-on)] [&_button:hover]:!bg-white/10">
          <CastButton />
        </div>
        <button
          type="button"
          class="pb-btn !h-8 !w-8"
          aria-label="Expand player"
          title="Expand player"
          @click="emit('expand')"
        >
          <FeatherIcon name="maximize-2" class="h-4 w-4" />
        </button>
    </div>

    <Teleport to="body">
      <div
        v-if="titlePopover"
        class="fixed z-[250] max-w-[420px] whitespace-pre-line glass-strong rounded-2xl px-3 py-2 text-xs text-on-surface shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :style="{ left: titlePopover.x + 'px', top: titlePopover.y + 'px', transform: 'translate(-50%, -100%)' }"
        @mouseenter="cancelHideTitlePopover"
        @mouseleave="hideTitlePopover"
      >
        {{ titlePopover.text }}
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="contextMenu"
        ref="contextMenuRef"
        class="fixed z-[301] min-w-[160px] glass-strong rounded-2xl py-1 shadow-xl"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px', transform: 'translateY(-100%)' }"
        @click.stop
      >
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
          @click="viewSong"
        >
          <FeatherIcon name="list" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          View song
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
          @click="addToQueue"
        >
          <FeatherIcon name="layers" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          Add to queue
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
          @click="toggleFavorite"
        >
          <FeatherIcon
            :name="isFavorite ? 'heart-fill' : 'heart'"
            class="h-4 w-4 shrink-0"
            :class="isFavorite ? 'text-primary' : 'text-on-surface-variant'"
          />
          {{ isFavorite ? "Remove from favorites" : "Add to favorites" }}
        </button>
        <div class="my-1 border-t border-outline" />
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
          @click="viewAlbum"
        >
          <FeatherIcon name="disc" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          View album
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
          @click="viewArtist"
        >
          <FeatherIcon name="user" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          View artist
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
            class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
            @click="playlistSubmenuOpen = !playlistSubmenuOpen"
          >
            <span class="flex items-center gap-2">
              <FeatherIcon name="playlist-add" class="h-4 w-4 shrink-0 text-on-surface-variant" />
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
            <template v-if="playlistsAlreadyIn.length">
              <button
                v-for="pl in playlistsAlreadyIn"
                :key="pl.id"
                type="button"
                class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
                @click="addToPlaylist(pl.id)"
              >
                <PlaylistIcon v-if="pl.icon" :icon="pl.icon" class="h-3.5 w-3.5 shrink-0" />
                <FeatherIcon v-else name="list" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant" />
                <span class="min-w-0 flex-1 truncate">{{ pl.name }}</span>
                <FeatherIcon name="check" class="h-3.5 w-3.5 shrink-0 text-primary" />
              </button>
              <div v-if="playlistsNotYetIn.length" class="my-1 border-t border-outline" />
            </template>
            <button
              v-for="pl in playlistsNotYetIn"
              :key="pl.id"
              type="button"
              class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-on-surface hover:bg-surface-container hover:text-on-primary"
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
  </div>
</template>

<style scoped>
.player-progress-slider {
  accent-color: var(--p-strong, #5b7c32);
  background: linear-gradient(
    to right,
    var(--p-strong, #5b7c32) 0%,
    var(--p-strong, #5b7c32) var(--progress-percent, 0%),
    color-mix(in srgb, var(--p-on, #fff) 25%, transparent) var(--progress-percent, 0%),
    color-mix(in srgb, var(--p-on, #fff) 25%, transparent) 100%
  ) !important;
  border-radius: 9999px;
}
.player-progress-slider::-webkit-slider-thumb {
  background: var(--p-strong, #5b7c32);
}
.player-progress-slider::-webkit-slider-runnable-track {
  background: linear-gradient(
    to right,
    var(--p-strong, #5b7c32) 0%,
    var(--p-strong, #5b7c32) var(--progress-percent, 0%),
    color-mix(in srgb, var(--p-on, #fff) 25%, transparent) var(--progress-percent, 0%),
    color-mix(in srgb, var(--p-on, #fff) 25%, transparent) 100%
  );
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-progress {
  background: var(--p-strong, #5b7c32);
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-track {
  background: color-mix(in srgb, var(--p-on, #fff) 25%, transparent);
  border-radius: 9999px;
}
</style>
