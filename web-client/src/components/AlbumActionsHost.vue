<template>
  <AlbumActionsSheet
    :open="album !== null"
    :item="album"
    @close="emit('close')"
    @play="playAlbum"
    @add-to-playlist="openPicker"
    @view-artist="viewArtist"
    @view-album="viewAlbum"
  />

  <PlaylistPickerSheet
    :open="pickerItem !== null"
    :playlists="playlistStore.playlists"
    :membership-ids="membershipIds"
    :partial-membership-ids="partialMembershipIds"
    :track-ids="pickerItem?.trackIds"
    @add="onPickerAdd"
    @remove="onPickerRemove"
    @create="onPickerCreate"
    @close="pickerItem = null"
  />
</template>

<script setup lang="ts">
/**
 * The album context menu with everything it does, for any screen that shows an
 * `AlbumCard`: play, add ALL of the album's tracks to a playlist (the picker
 * shows which playlists already hold the whole album or part of it), view
 * artist, view album.
 *
 * The caller only tracks which album the menu is open for:
 *
 *   <AlbumCard @actions="albumActions = item" />
 *   <AlbumActionsHost :item="albumActions" @close="albumActions = null" />
 */
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import AlbumActionsSheet from "./AlbumActionsSheet.vue";
import PlaylistPickerSheet from "./PlaylistPickerSheet.vue";
import { showToast } from "../composables/useToast";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import type { AlbumGridItem, Playlist } from "../types";

const props = defineProps<{ item: AlbumGridItem | null }>();
const emit = defineEmits<{ close: [] }>();

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();

/**
 * The whole album, whatever the caller was showing. A card on the Library grid
 * under a search, or on a playlist screen, only counts the tracks that matched
 * there — "add the album to a playlist" means every track of it.
 */
const album = computed<AlbumGridItem | null>(() =>
  props.item ? (lib.albumByKey(props.item.key) ?? props.item) : null,
);

function playAlbum(): void {
  const album = props.item;
  if (!album) return;
  const tracks = lib.tracksForAlbum(album.key);
  emit("close");
  if (tracks.length > 0) void player.playTrack(tracks[0], tracks);
}

function viewArtist(): void {
  const name = props.item?.albumArtist;
  emit("close");
  if (name) void router.push({ name: "artist", params: { name } });
}

function viewAlbum(): void {
  const album = props.item;
  emit("close");
  if (album) void router.push({ name: "album", params: { albumKey: album.key } });
}

// --- Add every track of the album to a playlist ----------------------------

/** Kept separately from `item`: the menu closes the moment "Add to playlist"
 *  is chosen, but the picker that follows still needs the album. */
const pickerItem = ref<AlbumGridItem | null>(null);

async function openPicker(): Promise<void> {
  const album = props.item;
  if (!album) return;
  emit("close");
  await playlistStore.loadAllTrackIds();
  pickerItem.value = album;
}

const membershipIds = computed(() => {
  const result = new Set<number>();
  const ids = pickerItem.value?.trackIds ?? [];
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (set && ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

const partialMembershipIds = computed(() => {
  const result = new Set<number>();
  const ids = pickerItem.value?.trackIds ?? [];
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (!set) continue;
    if (ids.some((id) => set.has(id)) && !ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

/** The add itself already happened in PlaylistPickerSheet (duplicate-checked). */
function onPickerAdd(p: Playlist): void {
  const n = pickerItem.value?.trackIds.length ?? 0;
  showToast(`Added ${n === 1 ? "1 track" : `${n} tracks`} to ${p.name}`);
}

async function onPickerRemove(p: Playlist): Promise<void> {
  const album = pickerItem.value;
  if (!album) return;
  try {
    await playlistStore.removeTracks(p.id, album.trackIds);
    showToast(`Removed album from ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function onPickerCreate(name: string): Promise<void> {
  const album = pickerItem.value;
  if (!album) return;
  try {
    const p = await playlistStore.createPlaylist(name, "🎵");
    await playlistStore.addTracks(p.id, album.trackIds);
    showToast(`Added ${album.trackIds.length} tracks to ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}
</script>
