<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <template v-if="!item">
      <div class="content-col flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
        <p class="text-body-md text-on-surface-variant">Album not found</p>
        <button
          type="button"
          class="text-label-lg text-primary"
          @click="router.push({ name: 'library' })"
        >Back to library</button>
      </div>
    </template>

    <template v-else>
      <!-- Same cover-derived backdrop as the maximized player, at every
           viewport size. Only when the album actually has art, so coverless
           albums keep the plain theme background. -->
      <CoverBackdrop v-if="backdropActive" :cover-url="coverUrl" variant="album" />

      <!-- z-20 so the header's dropdown menu paints above the track list
           scroller (z-10) and its row hover backgrounds. -->
      <div class="content-col relative z-20 flex shrink-0 items-center gap-3 px-4 pb-4 pt-4">
        <button
          type="button"
          class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full transition-colors lg:h-9 lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-white/10"
          :class="backdropActive ? 'text-white/85' : 'text-on-surface lg:hover:bg-surface-container'"
          aria-label="Back"
          @click="router.back()"
        >
          <MageIcon name="chevron-left" class="h-6 w-6 lg:h-5 lg:w-5" />
          <span class="hidden lg:inline">Back</span>
        </button>

        <!-- Wrapped so the cover's left edge lines up with the track titles
             below (both start after the w-12 leading gutter). -->
        <div class="flex min-w-0 flex-1 items-start gap-4">
          <div class="h-[120px] w-[120px] shrink-0 overflow-hidden rounded-[10px] bg-surface-variant">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              :alt="item.album"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-8 w-8 text-on-surface-variant/60" />
            </div>
          </div>

          <div class="flex min-w-0 flex-1 flex-col justify-center gap-3 py-1">
            <div class="min-w-0">
              <MarqueeText
                :text="item.album"
                class="text-title-md"
                :class="backdropActive ? 'text-white' : 'text-on-surface'"
              />
              <p
                class="truncate text-body-md"
                :class="backdropActive ? 'text-white/75' : 'text-on-surface-variant'"
              >{{ metaLine }}</p>
            </div>

            <div class="flex items-center gap-2">
              <button
                type="button"
                class="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-on-primary disabled:opacity-50"
                :style="accentCss ? { backgroundColor: accentCss, color: accentOnCss ?? undefined } : undefined"
                :disabled="tracks.length === 0"
                aria-label="Play album"
                @click="playAlbum"
              >
                <MageIcon name="play" class="h-6 w-6" />
              </button>
              <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-full"
                :class="backdropActive ? 'text-white/75 hover:bg-white/10' : 'text-on-surface-variant'"
                aria-label="Add album to playlist"
                @click="openPicker"
              >
                <MageIcon name="playlist-add" class="h-5 w-5" />
              </button>
              <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-full"
                :class="backdropActive ? 'text-white/75 hover:bg-white/10' : 'text-on-surface-variant'"
                aria-label="More album options"
                @click="menuOpen = true"
              >
                <MageIcon name="dots" class="h-5 w-5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div
        class="content-col relative z-10 shrink-0 border-t"
        :class="backdropActive ? 'border-white/15' : 'border-outline/30'"
      />

      <div
        ref="scroller"
        class="content-col-children relative z-10 min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]"
      >
        <!-- The track list is a plain transparent surface over the cover
             backdrop; only the currently playing row carries the glass
             highlight (see TrackListRow). -->
        <div class="tracks-panel m-3 rounded-2xl p-2">
          <TrackListRow
            v-for="track in tracks"
            :key="track.id"
            :track="track"
            leading="index"
            :is-loading="player.pendingTrackId === track.id"
            :on-dark="backdropActive"
            :accent-rgb="accentRgb"
            :is-playing="player.currentTrack?.id === track.id"
            :selectable="selection.selecting"
            :selected="selection.has(track.id)"
            @play="player.playTrack(track, tracks)"
            @select="(mods) => onSelect(track, mods)"
            @actions="sheetTrack = track"
          />
        </div>
      </div>
    </template>

    <!-- Album 3-dots menu: bottom sheet with only the offline download
         toggle and view-artist. -->
    <BottomSheet :open="menuOpen" @close="menuOpen = false">
      <div v-if="item" class="pb-2">
        <div class="px-6 pb-2 pt-1">
          <MarqueeText :text="item.album" class="text-title-md text-on-surface" />
          <p class="truncate text-body-sm text-on-surface-variant">{{ metaLine }}</p>
        </div>
        <div class="my-1 border-t border-outline/30" />
        <button
          type="button"
          class="flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg disabled:opacity-50"
          :class="albumDownloaded ? 'text-error hover:bg-error/10' : 'text-on-surface'"
          :disabled="albumDownloading || tracks.length === 0"
          @click="toggleAlbumDownload"
        >
          <span class="relative flex h-5 w-5 shrink-0 items-center justify-center">
            <svg v-if="albumDownloading" viewBox="0 0 20 20" class="absolute inset-0 h-5 w-5 -rotate-90">
              <circle cx="10" cy="10" r="8.5" fill="none" stroke="currentColor" stroke-opacity="0.25" stroke-width="2.5" />
              <circle
                cx="10" cy="10" r="8.5" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"
                :stroke-dasharray="RING_CIRC" :stroke-dashoffset="ringOffset"
              />
            </svg>
            <MageIcon
              name="download"
              class="h-5 w-5"
              :class="albumDownloaded ? 'text-error' : 'text-on-surface-variant'"
            />
          </span>
          <span>{{ albumDownloaded ? "Remove offline download" : "Download for offline" }}</span>
        </button>
        <button
          v-if="item.albumArtist"
          type="button"
          class="flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface"
          @click="viewArtistFromAlbum"
        >
          <MageIcon name="user" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>View artist</span>
        </button>
      </div>
    </BottomSheet>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />

    <PlaylistPickerSheet
      :open="pickerOpen"
      :playlists="playlistStore.playlists"
      :membership-ids="membershipIds"
      :partial-membership-ids="partialMembershipIds"
      @add="onPickerAdd"
      @remove="onPickerRemove"
      @create="onPickerCreate"
      @close="pickerOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import MarqueeText from "../components/MarqueeText.vue";
import CoverBackdrop from "../components/CoverBackdrop.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import BottomSheet from "../components/BottomSheet.vue";
import PlaylistPickerSheet from "../components/PlaylistPickerSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { useOfflineStore } from "../stores/offline";
import { useSelectionStore } from "../stores/selection";
import { useScrollMemory } from "../composables/useScrollMemory";
import { provideVisibleIds } from "../composables/useSelectableList";
import { isColorBland, useDominantColor } from "../composables/useDominantColor";
import { showToast } from "../composables/useToast";
import type { CatalogTrack, Playlist } from "../types";

// `key` is reserved by Vue and would never reach the component as a prop,
// so the route param is named `albumKey`. vue-router handles the encoding.
const props = defineProps<{ albumKey: string }>();

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const offline = useOfflineStore();
const selection = useSelectionStore();

const albumKey = computed(() => props.albumKey);
const item = computed(() => lib.albumByKey(albumKey.value));

/** Non-null only while the view was reached from a playlist. */
const playlistFilter = ref<Set<number> | null>(null);

watch(
  () => route.query.playlistId,
  async (raw) => {
    const id = Number(raw);
    if (!raw || Number.isNaN(id)) {
      playlistFilter.value = null;
      return;
    }
    playlistFilter.value = await playlistStore.loadTrackIdsForPlaylist(id);
  },
  { immediate: true },
);

const tracks = computed<CatalogTrack[]>(() => {
  const all = lib.tracksForAlbum(albumKey.value);
  const filter = playlistFilter.value;
  return filter ? all.filter((t) => filter.has(t.id)) : all;
});

provideVisibleIds(() => tracks.value.map((t) => t.id));

function onSelect(track: CatalogTrack, mods: { shift: boolean; ctrl: boolean }): void {
  const consumed = selection.click(
    track.id,
    tracks.value.map((t) => t.id),
    mods,
  );
  if (!consumed) player.playTrack(track, tracks.value);
}

const coverUrl = computed(() => {
  const id = item.value?.coverTrackId;
  if (id == null) return null;
  lib.requestCover(id);
  return lib.coverCache.get(id) ?? null;
});

/**
 * The cover-derived backdrop shows at every viewport size, but only when the
 * album actually has art — coverless albums keep the plain theme background.
 */
const backdropActive = computed(() => coverUrl.value != null);

// The accent colour the maximized player would pick for this album: the same
// dominant colour it derives from the playing track's cover art. Used for the
// big play button and the lossless format badges in this album's track list.
// Falls back to the theme primary when there is no art or the colour is bland.
const glowRgb = useDominantColor(coverUrl);
const accentRgb = computed(() =>
  coverUrl.value != null && !isColorBland(glowRgb.value) ? glowRgb.value : null,
);
const accentCss = computed(() => (accentRgb.value ? `rgb(${accentRgb.value})` : null));
const accentOnCss = computed(() => {
  if (!accentRgb.value) return null;
  const [r, g, b] = accentRgb.value.split(",").map(Number);
  const luminance = (r * 0.299 + g * 0.587 + b * 0.114) / 255;
  return luminance > 0.55 ? "#1c1917" : "#ffffff";
});

const metaLine = computed(() => {
  const parts: string[] = [];
  if (item.value?.albumArtist) parts.push(item.value.albumArtist);
  if (item.value?.year) parts.push(String(item.value.year));
  const n = tracks.value.length;
  parts.push(n === 1 ? "1 track" : `${n} tracks`);
  const secs = tracks.value.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
  if (secs > 0) parts.push(`${Math.round(secs / 60)}m`);
  return parts.join(" · ");
});

function playAlbum(): void {
  if (tracks.value.length === 0) return;
  void player.playTrack(tracks.value[0], tracks.value);
}

// --- Album header menu -----------------------------------------------------

const menuOpen = ref(false);

function viewArtistFromAlbum(): void {
  const name = item.value?.albumArtist;
  menuOpen.value = false;
  if (name) void router.push({ name: "artist", params: { name } });
}

// --- Offline download ------------------------------------------------------

const albumDownloading = computed(
  () => offline.activeAlbumKey === albumKey.value,
);
const albumDownloaded = computed(() => offline.isAlbumDownloaded(albumKey.value));

// Circular progress ring around the download icon while this album downloads.
const RING_CIRC = 2 * Math.PI * 8.5;
const ringOffset = computed(() => {
  const { done, total } = offline.downloadProgress;
  const frac = total > 0 ? done / total : 0;
  return RING_CIRC * (1 - frac);
});

/** Same toggle the album context menu's download row performs. */
async function toggleAlbumDownload(): Promise<void> {
  menuOpen.value = false;
  if (offline.isAlbumDownloaded(albumKey.value)) {
    await offline.removeAlbumDownload(albumKey.value);
  } else {
    await offline.downloadAlbum(albumKey.value);
  }
}

// --- Track actions sheet ---------------------------------------------------

const sheetTrack = ref<CatalogTrack | null>(null);

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

// --- Album playlist picker -------------------------------------------------

const pickerOpen = ref(false);

async function openPicker(): Promise<void> {
  await playlistStore.loadAllTrackIds();
  pickerOpen.value = true;
}

const membershipIds = computed(() => {
  const result = new Set<number>();
  const ids = tracks.value.map((t) => t.id);
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (set && ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

const partialMembershipIds = computed(() => {
  const result = new Set<number>();
  const ids = tracks.value.map((t) => t.id);
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (!set) continue;
    if (ids.some((id) => set.has(id)) && !ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

async function onPickerAdd(p: Playlist): Promise<void> {
  const existing = playlistStore.trackIdSets.get(p.id) ?? new Set<number>();
  const toAdd = tracks.value.map((t) => t.id).filter((id) => !existing.has(id));
  try {
    if (toAdd.length > 0) await playlistStore.addTracks(p.id, toAdd);
    showToast(`Added to ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function onPickerRemove(p: Playlist): Promise<void> {
  try {
    await playlistStore.removeTracks(
      p.id,
      tracks.value.map((t) => t.id),
    );
    showToast(`Removed from ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function onPickerCreate(name: string): Promise<void> {
  try {
    const p = await playlistStore.createPlaylist(name, "🎵");
    await playlistStore.addTracks(
      p.id,
      tracks.value.map((t) => t.id),
    );
    showToast(`Added to ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

// KeepAlive reuses this one instance for every album, so the offset is keyed by
// path — opening a second album starts at the top, returning to the first does
// not.
const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
</script>

<style scoped>
/* The row highlight (hover / selected / playing tint) follows the glass
   panel's rounded island instead of running edge-to-edge as a square. */
.tracks-panel :deep(.track-row) {
  border-radius: 0.75rem;
}

/* On mobile the global rule drops the backdrop's edge mask so the player's
   art runs full-bleed; the album view keeps the same radial fade as desktop
   so the background behind the track list is evenly dark at every width. */
@media (max-width: 63.99rem) {
  :deep(.edge-blur-art) {
    -webkit-mask-image: radial-gradient(closest-side at center, black 30%, transparent 100%);
    mask-image: radial-gradient(closest-side at center, black 30%, transparent 100%);
  }
}
</style>
