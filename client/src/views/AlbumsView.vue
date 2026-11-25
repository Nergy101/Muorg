<script setup lang="ts">
import { computed, ref } from "vue";
import AlbumGridView from "../components/library/AlbumGridView.vue";
import LibraryControls from "../components/library/LibraryControls.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useAlbums } from "../composables/useAlbums";
import { useCatalogStore } from "../stores/catalog";
import { navigate } from "../state/navigation";

const store = useCatalogStore();

const albums = useAlbums();

function openAlbum(key: string) {
  navigate({ name: "album", key });
}

// ── Album card context menu ─────────────────────────────────────────────────
const albumMenu = ref<{ x: number; y: number; key: string } | null>(null);
function onAlbumContextMenu(e: MouseEvent, key: string) {
  albumMenu.value = { x: e.clientX, y: e.clientY, key };
}
function closeAlbumMenu() {
  albumMenu.value = null;
}
const albumMenuItem = computed(() => albums.value.find((a) => a.key === albumMenu.value?.key) ?? null);
function menuPlayAlbum() {
  const a = albumMenuItem.value;
  if (a) {
    store.queueTrackIds = [...a.trackIds];
    store.setSelection([a.trackIds[0]]);
    store.setPlayRequestTrackId(a.trackIds[0]);
  }
  closeAlbumMenu();
}
function menuShuffleAlbum() {
  const a = albumMenuItem.value;
  if (a) {
    const ids = [...a.trackIds];
    for (let i = ids.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [ids[i], ids[j]] = [ids[j], ids[i]];
    }
    store.queueTrackIds = ids;
    store.setSelection([ids[0]]);
    store.setPlayRequestTrackId(ids[0]);
  }
  closeAlbumMenu();
}
function menuViewArtist() {
  const a = albumMenuItem.value;
  if (a) navigate({ name: "artist", key: a.albumArtist });
  closeAlbumMenu();
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex shrink-0 flex-wrap items-center gap-3 px-5 pb-2 pt-4">
      <h1 class="view-title">Library</h1>
      <span class="view-subtitle">{{ albums.length }} albums</span>
      <LibraryControls />
    </div>
    <AlbumGridView :albums="albums" @open-album="openAlbum" @album-context-menu="onAlbumContextMenu" />

    <!-- Album context menu -->
    <Teleport to="body">
      <div
        v-if="albumMenu"
        class="fixed z-[300] min-w-[170px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
        :style="{ left: albumMenu.x + 'px', top: albumMenu.y + 'px' }"
        @click.stop
      >
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuPlayAlbum">
          <FeatherIcon name="play" class="h-4 w-4 text-on-surface-variant" /> Play
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuShuffleAlbum">
          <FeatherIcon name="shuffle" class="h-4 w-4 text-on-surface-variant" /> Shuffle
        </button>
        <div class="my-1 border-t border-outline" />
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="closeAlbumMenu(); openAlbum(albumMenu!.key)">
          <FeatherIcon name="disc" class="h-4 w-4 text-on-surface-variant" /> View album
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewArtist">
          <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
        </button>
      </div>
      <div v-if="albumMenu" class="fixed inset-0 z-[299]" @click="closeAlbumMenu" @contextmenu.prevent="closeAlbumMenu" />
    </Teleport>
  </div>
</template>
