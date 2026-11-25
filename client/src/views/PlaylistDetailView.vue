<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import StarRating from "../components/shared/StarRating.vue";
import TrackAlbumArt from "../components/shared/TrackAlbumArt.vue";
import { usePlaylistStore } from "../stores/playlists";
import { useCatalogStore } from "../stores/catalog";
import * as playlistApi from "../api/playlists";
import type { CatalogTrack } from "../types";

const props = defineProps<{ id: number }>();

const playlistStore = usePlaylistStore();
const store = useCatalogStore();
const { playlists } = storeToRefs(playlistStore);
const { tracks, currentPlayingTrackId } = storeToRefs(store);

const playlist = computed(() => playlists.value.find((p) => p.id === props.id) ?? null);

const entries = ref<{ entryId: number; trackId: number }[]>([]);
const playlistTracks = computed<CatalogTrack[]>(() => {
  const byId = new Map(tracks.value.map((t) => [t.id, t]));
  return entries.value.map((e) => byId.get(e.trackId)).filter((t): t is CatalogTrack => Boolean(t));
});

async function load() {
  if (!playlist.value) return;
  if (playlist.value.smart_rules) {
    // Smart playlists are computed from rules — no stored entries.
    const ids = await playlistApi.getSmartPlaylistTrackIds(playlist.value.id);
    entries.value = ids.map((trackId, i) => ({ entryId: -1 - i, trackId }));
  } else {
    const raw = await playlistApi.getPlaylistEntries(playlist.value.id);
    entries.value = raw.map((e) => ({ entryId: e.entry_id, trackId: e.track_id }));
  }
}

onMounted(load);
watch(() => props.id, load);

function playAll() {
  if (!playlistTracks.value.length) return;
  store.setActivePlaylist(props.id, entries.value, playlist.value?.name);
  store.queueTrackIds = playlistTracks.value.map((t) => t.id);
  store.setSelection([playlistTracks.value[0].id]);
  store.setPlayRequestTrackId(playlistTracks.value[0].id);
}

function playTrack(track: CatalogTrack) {
  store.queueTrackIds = playlistTracks.value.map((t) => t.id);
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}

// ── Row context menu (3-dots) ───────────────────────────────────────────────
const menu = ref<{ x: number; y: number; track: CatalogTrack } | null>(null);
function openMenu(e: MouseEvent, track: CatalogTrack) {
  e.stopPropagation();
  menu.value = { x: e.clientX, y: e.clientY, track };
}
function closeMenu() {
  menu.value = null;
}
function menuPlay() {
  if (menu.value) playTrack(menu.value.track);
  closeMenu();
}
function menuAddToQueue() {
  if (menu.value) store.addToQueue([menu.value.track.id]);
  closeMenu();
}
async function menuRemove() {
  if (!menu.value) return;
  const entry = entries.value.find((e) => e.trackId === menu.value!.track.id);
  if (entry && entry.entryId > 0) {
    await playlistApi.removePlaylistEntry(entry.entryId);
    await load();
  }
  closeMenu();
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div v-if="playlist" class="flex shrink-0 items-center gap-4 px-5 pb-3 pt-4">
      <span class="pl-icon pl-icon-glass !h-14 !w-14 !rounded-xl">
        <PlaylistIcon :icon="playlist.icon" class="!h-7 !w-7" />
      </span>
      <div class="min-w-0 flex-1">
        <h1 class="view-title truncate">{{ playlist.name }}</h1>
        <div class="view-subtitle">{{ playlistTracks.length }} tracks</div>
      </div>
      <button type="button" class="btn-primary" :disabled="!playlistTracks.length" @click="playAll">
        Play
      </button>
    </div>

    <!-- Compact track rows (no table headers) -->
    <div class="min-h-0 flex-1 overflow-y-auto px-3 pb-24">
      <button
        v-for="(track, i) in playlistTracks"
        :key="`${track.id}-${i}`"
        type="button"
        class="group flex w-full items-center gap-2.5 border-b border-outline/10 px-2 py-2 text-left transition-colors last:border-b-0"
        :class="currentPlayingTrackId === track.id
          ? 'bg-[color-mix(in_srgb,var(--primary)_13%,transparent)]'
          : 'hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]'"
        @click="playTrack(track)"
      >
        <FeatherIcon name="menu" class="h-4 w-4 shrink-0 text-on-surface-variant/60" />
        <TrackAlbumArt :path="track.path" size="small" class="!h-9 !w-9 shrink-0 rounded-md" />
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[13.5px]" :class="currentPlayingTrackId === track.id ? 'font-semibold text-on-surface' : 'text-on-surface'">
            {{ track.title ?? "Unknown Title" }}
          </span>
          <span class="block truncate text-xs text-on-surface-variant">
            {{ track.artist ?? track.album_artist ?? "" }}<template v-if="track.album"> · {{ track.album }}</template>
          </span>
        </span>
        <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
        <span class="shrink-0" @click.stop>
          <StarRating :model-value="track.rating ?? null" @update:model-value="(r) => store.setRating([track.path], r)" />
        </span>
        <span
          class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container"
          role="button"
          aria-label="More actions"
          @click.stop="openMenu($event, track)"
        >
          <FeatherIcon name="more-horizontal" class="h-4 w-4" />
        </span>
      </button>
      <p v-if="!playlistTracks.length" class="py-8 text-center text-sm text-on-surface-variant">
        This playlist is empty.
      </p>
    </div>

    <!-- Row context menu -->
    <Teleport to="body">
      <div
        v-if="menu"
        class="fixed z-[300] min-w-[160px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
        :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
        @click.stop
      >
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuPlay">
          <FeatherIcon name="play" class="h-4 w-4 text-on-surface-variant" /> Play
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuAddToQueue">
          <FeatherIcon name="layers" class="h-4 w-4 text-on-surface-variant" /> Add to queue
        </button>
        <button v-if="menu && entries.find((e) => e.trackId === menu!.track.id && e.entryId > 0)" type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-error hover:bg-surface-container" @click="menuRemove">
          <FeatherIcon name="trash-2" class="h-4 w-4" /> Remove from playlist
        </button>
      </div>
      <div v-if="menu" class="fixed inset-0 z-[299]" @click="closeMenu" @contextmenu.prevent="closeMenu" />
    </Teleport>
  </div>
</template>
