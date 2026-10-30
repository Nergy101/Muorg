<template>
  <div
    class="flex select-none flex-col"
    role="button"
    tabindex="0"
    @click="emit('open')"
    @keydown.enter="emit('open')"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <!-- Cover collage card: up to 4 distinct covers squared together -->
    <div
      ref="cardEl"
      class="relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant shadow-[0_12px_30px_-8px_rgba(0,0,0,0.55),0_2px_6px_rgba(0,0,0,0.35),inset_0_0_0_1px_rgba(255,255,255,0.06)] ring-1 ring-white/10 transition-transform duration-150 active:scale-95 lg:hover:scale-[1.02]"
      :class="dragOver ? 'ring-2 ring-primary' : ''"
    >
      <div v-if="coverCount === 0" class="absolute inset-0 flex items-center justify-center">
        <MageIcon name="music" class="h-10 w-10 text-on-surface-variant/50" />
      </div>
      <img
        v-else-if="coverCount === 1"
        :src="coverUrls[0] ?? undefined"
        :alt="playlist.name"
        class="absolute inset-0 h-full w-full object-cover"
        decoding="async"
      />
      <div v-else class="grid h-full w-full grid-cols-2 grid-rows-2">
        <template v-for="(url, i) in coverUrls" :key="i">
          <img
            v-if="url"
            :src="url"
            :alt="playlist.name"
            class="h-full w-full object-cover"
            decoding="async"
          />
          <div v-else class="h-full w-full bg-surface-variant/50" />
        </template>
      </div>

      <!-- Actions: play / shuffle (lg quick actions) / pin / edit / export / delete.
           The server-owned Favorites playlist cannot be renamed or deleted (403),
           so those two actions are hidden for it (Q21). -->
      <div class="absolute right-0 top-0 flex flex-wrap items-center justify-end gap-1 p-1.5" @click.stop>
        <button
          type="button"
          class="glass-frost hidden h-8 w-8 items-center justify-center rounded-full lg:flex"
          aria-label="Play playlist"
          @click="playPlaylist"
        >
          <MageIcon name="play" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="glass-frost hidden h-8 w-8 items-center justify-center rounded-full lg:flex"
          aria-label="Shuffle play playlist"
          @click="shufflePlaylist"
        >
          <MageIcon name="exchange" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="glass-frost flex h-8 w-8 items-center justify-center rounded-full"
          :class="playlistStore.isPinned(playlist.id) ? 'fill-current text-primary' : ''"
          :aria-label="playlistStore.isPinned(playlist.id) ? 'Unpin playlist' : 'Pin playlist'"
          @click="playlistStore.togglePin(playlist.id)"
        >
          <MageIcon name="pin" class="h-4 w-4" />
        </button>
        <button
          v-if="!isFavorites"
          type="button"
          class="glass-frost flex h-8 w-8 items-center justify-center rounded-full"
          aria-label="Edit playlist"
          @click="emit('edit')"
        >
          <MageIcon name="edit" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="glass-frost flex h-8 w-8 items-center justify-center rounded-full"
          aria-label="Export playlist as M3U"
          @click="exportOpen = true"
        >
          <MageIcon name="export-list" class="h-4 w-4" />
        </button>
        <button
          v-if="!isFavorites"
          type="button"
          class="glass-frost flex h-8 w-8 items-center justify-center rounded-full"
          aria-label="Delete playlist"
          @click="emit('delete')"
        >
          <MageIcon name="trash" class="h-4 w-4" />
        </button>
      </div>

      <!-- Bottom frosted scrim so the title stays legible over artwork -->
      <div
        class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[52px]"
        aria-hidden="true"
      />

      <!-- Title at the bottom of the glass; music icon + count on the right -->
      <div class="scrim-label absolute inset-x-0 bottom-0 flex h-[52px] items-end gap-1.5 px-2.5 pb-2">
        <p class="min-w-0 flex-1 truncate text-label-lg font-semibold text-on-surface">{{ playlist.name }}</p>
        <span class="flex shrink-0 items-center gap-1 text-label-sm text-on-surface-variant">
          <MageIcon v-if="playlist.smart_rules" name="zap" class="h-3 w-3 text-primary" />
          <MageIcon name="music" class="h-3.5 w-3.5" />
          <span class="tabular-nums">{{ playlist.track_count }}</span>
        </span>
      </div>
    </div>

    <PlaylistExportDialog
      :playlist="playlist"
      :open="exportOpen"
      @close="exportOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import PlaylistExportDialog from "./PlaylistExportDialog.vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { usePlayerStore } from "../stores/player";
import { isTrackDrag, readTrackDrag } from "../composables/useTrackDrag";
import type { Playlist, CatalogTrack } from "../types";

const props = defineProps<{ playlist: Playlist }>();
const emit = defineEmits<{ open: []; edit: []; delete: [] }>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const player = usePlayerStore();

/** The server-owned Favorites playlist: rename/delete are hidden (Q21). */
const isFavorites = computed(() => playlistStore.isFavoritesPlaylist(props.playlist));

const cardEl = ref<HTMLElement | null>(null);
const orderedIds = ref<number[]>([]);
const exportOpen = ref(false);
const dragOver = ref(false);

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

/**
 * How many distinct album covers we scan from the playlist's track order
 * before giving up. Scanning beyond the first 4 gives a pool of fallbacks: if
 * one of the first four albums has no artwork (or its fetch fails), we
 * substitute the next distinct cover instead of leaving an empty tile.
 */
const MAX_DISTINCT_COVERS = 16;

/** Distinct album covers in playlist order, capped at MAX_DISTINCT_COVERS. */
const coverCandidates = computed<number[]>(() => {
  const seen = new Set<string>();
  const out: number[] = [];
  for (const id of orderedIds.value) {
    const t = trackById.value.get(id);
    if (!t) continue;
    const key = albumKeyFor(t);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t.id);
    if (out.length === MAX_DISTINCT_COVERS) break;
  }
  return out;
});

/** The first 4 candidate covers that actually loaded, in track order. */
const coverUrls = computed<(string | null)[]>(() => {
  const urls: (string | null)[] = [];
  for (const id of coverCandidates.value) {
    const url = lib.coverCache.get(id);
    if (!url) continue;
    urls.push(url);
    if (urls.length === 4) break;
  }
  return urls;
});
const coverCount = computed(() => coverUrls.value.length);

// Lazy-load the playlist's track order + request covers only when visible.
let observer: IntersectionObserver | null = null;
onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        observer?.disconnect();
        void init();
      }
    },
    { rootMargin: "200px" },
  );
  if (cardEl.value) observer.observe(cardEl.value);
});
onUnmounted(() => observer?.disconnect());

async function init(): Promise<void> {
  orderedIds.value = await playlistStore.loadTrackOrderForPlaylist(props.playlist.id);
  for (const id of coverCandidates.value) {
    if (!lib.coverCache.has(id)) lib.requestCover(id);
  }
}

/** Ordered tracks for the play/shuffle quick actions — loads the order on
 *  demand if the card hasn't scrolled into view yet (`init` is IO-gated). */
async function resolveOrderedTracks(): Promise<CatalogTrack[]> {
  const ids = orderedIds.value.length
    ? orderedIds.value
    : await playlistStore.loadTrackOrderForPlaylist(props.playlist.id);
  return ids.map((id) => trackById.value.get(id)).filter((t): t is CatalogTrack => t != null);
}

async function playPlaylist(): Promise<void> {
  const list = await resolveOrderedTracks();
  if (list.length) void player.playTrack(list[0], list);
}

async function shufflePlaylist(): Promise<void> {
  const list = await resolveOrderedTracks();
  if (!list.length) return;
  if (!player.shuffleEnabled) player.toggleShuffle();
  void player.playTrack(list[Math.floor(Math.random() * list.length)], list);
}

// ── Drag-and-drop: tracks dropped on the card are added to this playlist,
//    through the same duplicate check as the picker sheet. ────────────────
function onDragOver(e: DragEvent): void {
  if (!isTrackDrag(e)) return;
  e.preventDefault();
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
  dragOver.value = true;
}

function onDragLeave(): void {
  dragOver.value = false;
}

async function onDrop(e: DragEvent): Promise<void> {
  dragOver.value = false;
  const ids = readTrackDrag(e);
  if (!ids?.length) return;
  e.preventDefault();
  await playlistStore.addTracksChecked(props.playlist.id, ids);
}
</script>
