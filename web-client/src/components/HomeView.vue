<template>
  <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain">
    <div class="mx-auto max-w-3xl px-4 py-4">
      <!-- Recently played -->
      <section v-if="recent.length">
        <h2 class="px-1 pb-2 text-sm font-semibold text-stone-200">Recently played</h2>
        <div class="scroll-row flex gap-3 overflow-x-auto pb-3">
          <button
            v-for="t in recent"
            :key="'r' + t.id"
            type="button"
            class="w-28 shrink-0 text-left"
            @click="lib.playTrack(t)"
            @contextmenu.prevent="openCtx($event, t)"
          >
            <CoverThumb :track="t" class="h-28 w-28 rounded-lg shadow" />
            <p class="mt-1.5 truncate text-xs font-medium text-stone-200">{{ t.title ?? '—' }}</p>
            <p class="truncate text-[11px] text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</p>
          </button>
        </div>
      </section>

      <!-- Recently added -->
      <section v-if="added.length">
        <h2 class="px-1 pb-2 pt-2 text-sm font-semibold text-stone-200">Recently added</h2>
        <div class="scroll-row flex gap-3 overflow-x-auto pb-3">
          <button
            v-for="t in added"
            :key="'a' + t.id"
            type="button"
            class="w-28 shrink-0 text-left"
            @click="lib.playTrack(t)"
            @contextmenu.prevent="openCtx($event, t)"
          >
            <CoverThumb :track="t" class="h-28 w-28 rounded-lg shadow" />
            <p class="mt-1.5 truncate text-xs font-medium text-stone-200">{{ t.title ?? '—' }}</p>
            <p class="truncate text-[11px] text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</p>
          </button>
        </div>
      </section>

      <!-- Most played -->
      <section v-if="top.length">
        <h2 class="px-1 pb-2 pt-2 text-sm font-semibold text-stone-200">Most played</h2>
        <div class="scroll-row flex gap-3 overflow-x-auto pb-3">
          <button
            v-for="t in top"
            :key="'t' + t.id"
            type="button"
            class="w-28 shrink-0 text-left"
            @click="lib.playTrack(t)"
            @contextmenu.prevent="openCtx($event, t)"
          >
            <CoverThumb :track="t" class="h-28 w-28 rounded-lg shadow" />
            <p class="mt-1.5 truncate text-xs font-medium text-stone-200">{{ t.title ?? '—' }}</p>
            <p class="truncate text-[11px] text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</p>
          </button>
        </div>
      </section>

      <!-- Empty state -->
      <div
        v-if="!loading && !recent.length && !added.length && !top.length"
        class="flex flex-col items-center gap-2 py-16 text-center text-sm text-stone-600"
      >
        <FeatherIcon name="home" class="h-8 w-8 text-stone-700" />
        <p>Your library is empty.</p>
        <p class="text-xs">Add music to your Muorg server and rescan.</p>
      </div>

      <!-- Skeleton -->
      <div v-if="loading" class="space-y-4 pt-1">
        <div v-for="s in 2" :key="s">
          <div class="skeleton h-4 w-32" />
          <div class="mt-3 flex gap-3">
            <div v-for="i in 4" :key="i" class="shrink-0">
              <div class="skeleton h-28 w-28 rounded-lg" />
              <div class="skeleton mt-2 h-3 w-24" />
              <div class="skeleton mt-1 h-2.5 w-16" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Track context menu (sheet on mobile) -->
    <TrackContextMenu
      ref="ctxRef"
      :track-id="ctxTrack?.id ?? null"
      @play="ctxTrack && lib.playTrack(ctxTrack)"
      @add-to-queue="ctxTrack && lib.addToQueue(ctxTrack)"
      @play-next="ctxTrack && lib.playNextTrack(ctxTrack)"
      @add-to-playlist="id => ctxTrack && playlistStore.addTracks(id, [ctxTrack!.id])"
      @remove-from-playlist="id => ctxTrack && playlistStore.removeTracks(id, [ctxTrack!.id])"
      @new-playlist="newPlaylistForTrack"
    />

    <PlaylistModal
      v-model="showNewPlaylist"
      title="New Playlist"
      confirm-label="Create"
      @confirm="confirmNewPlaylist"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { getRecentlyAdded, getPlayHistoryRecent, getPlayHistoryTop } from "../api/catalog";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import CoverThumb from "./CoverThumb.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import PlaylistModal from "./PlaylistModal.vue";
import type { CatalogTrack } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const loading = ref(true);
const recent = ref<CatalogTrack[]>([]);
const added = ref<CatalogTrack[]>([]);
const top = ref<CatalogTrack[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    const [r, a, t] = await Promise.all([
      getPlayHistoryRecent(20),
      getRecentlyAdded(20),
      getPlayHistoryTop(20, 30),
    ]);
    recent.value = r;
    added.value = a;
    top.value = t;
  } catch {
    // Home sections are best-effort; the library still works without them
  } finally {
    loading.value = false;
  }
});

const ctxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);
const showNewPlaylist = ref(false);

function openCtx(event: { clientX: number; clientY: number }, track: CatalogTrack): void {
  ctxTrack.value = track;
  ctxRef.value?.open(event);
}

function newPlaylistForTrack(): void {
  showNewPlaylist.value = true;
}

async function confirmNewPlaylist(name: string, icon: string | null): Promise<void> {
  if (!ctxTrack.value) return;
  await playlistStore.createPlaylist(name, icon ?? undefined);
  const newPl = playlistStore.playlists.at(-1);
  if (newPl) await playlistStore.addTracks(newPl.id, [ctxTrack.value.id]);
}
</script>
