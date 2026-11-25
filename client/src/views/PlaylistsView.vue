<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import { usePlaylistStore, FAVORITES_PLAYLIST_NAME } from "../stores/playlists";
import { useCatalogStore } from "../stores/catalog";
import * as playlistApi from "../api/playlists";
import { navigate } from "../state/navigation";
import CardHoverActions from "../components/shared/CardHoverActions.vue";

const playlistStore = usePlaylistStore();
const store = useCatalogStore();
const { sortedPlaylists } = storeToRefs(playlistStore);

/** Favorites on top, the rest alphabetical. */
const playlistsOrdered = computed(() => {
  const fav = sortedPlaylists.value.filter((p) => p.name === FAVORITES_PLAYLIST_NAME);
  const rest = sortedPlaylists.value
    .filter((p) => p.name !== FAVORITES_PLAYLIST_NAME)
    .sort((a, b) => a.name.localeCompare(b.name));
  return [...fav, ...rest];
});

function open(id: number) {
  navigate({ name: "playlist", id });
}

async function playlistTrackIds(id: number): Promise<number[]> {
  const entries = await playlistApi.getPlaylistEntries(id);
  return entries.map((e) => e.track_id);
}

function playIds(ids: number[]) {
  if (!ids.length) return;
  store.queueTrackIds = [...ids];
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}

async function playPlaylist(id: number) {
  playIds(await playlistTrackIds(id));
}

async function shufflePlaylist(id: number) {
  const ids = await playlistTrackIds(id);
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  playIds(ids);
}
</script>

<template>
  <div class="app-main-scroll">
    <div class="view-pad">
      <div class="mb-4 flex items-baseline gap-3">
        <h1 class="view-title">Playlists</h1>
        <span class="view-subtitle">{{ playlistsOrdered.length }} playlists</span>
      </div>
      <div class="shelf-grid">
        <button
          v-for="pl in playlistsOrdered"
          :key="pl.id"
          type="button"
          class="album-card flex items-center gap-3 !text-left"
          @click="open(pl.id)"
        >
          <span class="pl-icon pl-icon-glass !h-11 !w-11 !rounded-lg">
            <PlaylistIcon :icon="pl.icon" class="!h-5 !w-5" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="marq block text-[13px] font-semibold text-on-surface">
              <span class="marq-in">{{ pl.name }}</span>
            </span>
            <span class="block text-[11.5px] text-on-surface-variant">Playlist</span>
          </span>
          <FeatherIcon name="chevron-right" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          <CardHoverActions @play="playPlaylist(pl.id)" @shuffle="shufflePlaylist(pl.id)" />
        </button>
      </div>
    </div>
  </div>
</template>
