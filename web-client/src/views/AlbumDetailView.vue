<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden">
    <div class="content-col flex h-14 shrink-0 items-center px-2">
      <button
        type="button"
        class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface transition-colors lg:h-9 lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-6 w-6 lg:h-5 lg:w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>
    </div>

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
      <div class="content-col flex shrink-0 items-start gap-4 px-4 pb-4">
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

        <div class="flex h-[120px] min-w-0 flex-1 flex-col justify-center gap-3">
          <div class="min-w-0">
            <MarqueeText :text="item.album" class="text-title-md text-on-surface" />
            <p class="truncate text-body-md text-on-surface-variant">{{ item.albumArtist }}</p>
            <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
          </div>

          <div class="flex items-center gap-2">
            <button
              type="button"
              class="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-on-primary disabled:opacity-50"
              :disabled="tracks.length === 0"
              aria-label="Play album"
              @click="playAlbum"
            >
              <MageIcon name="play" class="h-6 w-6" />
            </button>
            <button
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant"
              aria-label="Add album to playlist"
              @click="openPicker"
            >
              <MageIcon name="playlist-add" class="h-5 w-5" />
            </button>
            <div class="relative">
              <button
                type="button"
                class="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant"
                aria-label="More album options"
                @click="menuOpen = !menuOpen"
              >
                <MageIcon name="dots" class="h-5 w-5" />
              </button>
              <div v-if="menuOpen" class="fixed inset-0 z-30" @click="menuOpen = false"></div>
              <div
                v-if="menuOpen"
                class="absolute right-0 top-full z-40 mt-1 w-44 rounded-xl bg-surface-container py-1 shadow-xl"
              >
                <button
                  v-if="item?.albumArtist"
                  type="button"
                  class="flex w-full items-center gap-2 px-3 py-2 text-label-lg text-on-surface"
                  @click="viewArtistFromAlbum"
                >
                  <MageIcon name="user" class="h-4 w-4 text-primary" />
                  <span>View artist</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="content-col shrink-0 border-t border-outline/30" />

      <div ref="scroller" class="content-col-children min-h-0 flex-1 overflow-y-auto pb-4">
        <TrackListRow
          v-for="track in tracks"
          :key="track.id"
          :track="track"
          leading="index"
          :is-playing="player.currentTrack?.id === track.id"
          @play="player.playTrack(track, tracks)"
          @actions="sheetTrack = track"
        />
      </div>
    </template>

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
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import PlaylistPickerSheet from "../components/PlaylistPickerSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { useScrollMemory } from "../composables/useScrollMemory";
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

const coverUrl = computed(() => {
  const id = item.value?.coverTrackId;
  if (id == null) return null;
  lib.requestCover(id);
  return lib.coverCache.get(id) ?? null;
});

const metaLine = computed(() => {
  const parts: string[] = [];
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
  if (toAdd.length > 0) await playlistStore.addTracks(p.id, toAdd);
  showToast(`Added to ${p.name}`);
}

async function onPickerRemove(p: Playlist): Promise<void> {
  await playlistStore.removeTracks(
    p.id,
    tracks.value.map((t) => t.id),
  );
  showToast(`Removed from ${p.name}`);
}

async function onPickerCreate(name: string): Promise<void> {
  const p = await playlistStore.createPlaylist(name, "🎵");
  await playlistStore.addTracks(
    p.id,
    tracks.value.map((t) => t.id),
  );
  showToast(`Added to ${p.name}`);
}

// KeepAlive reuses this one instance for every album, so the offset is keyed by
// path — opening a second album starts at the top, returning to the first does
// not.
const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
</script>
