<template>
  <div
    class="flex select-none flex-col"
    role="button"
    tabindex="0"
    @click="emit('open')"
    @keydown.enter="emit('open')"
  >
    <!-- Cover collage card: up to 4 distinct covers squared together -->
    <div
      ref="cardEl"
      class="relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant transition-transform duration-150 active:scale-95 lg:hover:scale-[1.02]"
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

      <!-- Top scrim so the action buttons stay legible over any artwork -->
      <div
        class="pointer-events-none absolute inset-x-0 top-0 h-[72px] bg-gradient-to-b from-[#111111d9] via-[#11111159] to-transparent"
        aria-hidden="true"
      />

      <!-- Actions: pin / edit / download / delete (download sits between edit and delete) -->
      <div class="absolute right-0 top-0 flex items-center gap-0.5 p-1" @click.stop>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full bg-black/25"
          :class="playlistStore.isPinned(playlist.id) ? 'fill-current text-primary' : 'text-white/90'"
          :aria-label="playlistStore.isPinned(playlist.id) ? 'Unpin playlist' : 'Pin playlist'"
          @click="playlistStore.togglePin(playlist.id)"
        >
          <MageIcon name="pin" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full bg-black/25 text-white/90"
          aria-label="Edit playlist"
          @click="emit('edit')"
        >
          <MageIcon name="edit" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full bg-black/25 text-white/90"
          aria-label="Export playlist as M3U"
          @click="downloadOpen = true"
        >
          <MageIcon name="download" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full bg-black/25 text-white/90"
          aria-label="Delete playlist"
          @click="emit('delete')"
        >
          <MageIcon name="trash" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Emoji + title + count below the card -->
    <div class="flex items-center gap-1.5 px-1 pt-1.5">
      <span class="text-xl leading-none">{{ playlist.icon ?? "🎵" }}</span>
      <div class="min-w-0 flex-1">
        <p class="truncate text-label-lg font-semibold text-on-surface">{{ playlist.name }}</p>
        <p class="flex items-center gap-1 text-label-sm text-on-surface-variant">
          <MageIcon v-if="playlist.smart_rules" name="zap" class="h-3 w-3 text-primary" />
          {{ trackCountText }}
        </p>
      </div>
    </div>

    <ConfirmDialog
      :open="downloadOpen"
      title="Download as M3U?"
      :message="`Download \u201c${playlist.name}\u201d as an .m3u playlist file? It lists the tracks as stream URLs, playable by any M3U-capable app for about 8 hours.`"
      confirm-label="Download"
      @confirm="onDownloadConfirm"
      @cancel="downloadOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import ConfirmDialog from "./ConfirmDialog.vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { issueStreamToken } from "../api/catalog";
import { getServerUrl, streamUrl } from "../api/client";
import { showToast } from "../composables/useToast";
import type { Playlist, CatalogTrack } from "../types";

const props = defineProps<{ playlist: Playlist }>();
const emit = defineEmits<{ open: []; edit: []; delete: [] }>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const cardEl = ref<HTMLElement | null>(null);
const orderedIds = ref<number[]>([]);
const downloadOpen = ref(false);

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

/** First 4 distinct album covers in playlist order (one representative track each). */
const coverTrackIds = computed<number[]>(() => {
  const seen = new Set<string>();
  const out: number[] = [];
  for (const id of orderedIds.value) {
    const t = trackById.value.get(id);
    if (!t) continue;
    const key = albumKeyFor(t);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t.id);
    if (out.length === 4) break;
  }
  return out;
});

const coverUrls = computed<(string | null)[]>(
  () => coverTrackIds.value.map((id) => lib.coverCache.get(id) ?? null),
);
const coverCount = computed(() => coverUrls.value.filter(Boolean).length);

const trackCountText = computed(() => {
  const n = props.playlist.track_count;
  return props.playlist.smart_rules
    ? `Dynamic · ${n} ${n === 1 ? "track" : "tracks"}`
    : n === 1
      ? "1 track"
      : `${n} tracks`;
});

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
  for (const id of coverTrackIds.value) {
    if (!lib.coverCache.has(id)) lib.requestCover(id);
  }
}

async function onDownloadConfirm(): Promise<void> {
  downloadOpen.value = false;
  await exportM3U();
}

/** Export the playlist as a client-side .m3u file (same approach as the detail view). */
async function exportM3U(): Promise<void> {
  const ids = orderedIds.value.length
    ? orderedIds.value
    : await playlistStore.loadTrackOrderForPlaylist(props.playlist.id);
  const tracks = ids
    .map((id) => trackById.value.get(id))
    .filter((t): t is CatalogTrack => t != null);
  if (tracks.length === 0) return;
  try {
    const tokens = await Promise.all(
      tracks.map((t) => issueStreamToken(t.id).catch(() => null)),
    );
    const lines: string[] = ["#EXTM3U"];
    tracks.forEach((t, i) => {
      const title = [t.artist, t.title].filter(Boolean).join(" - ") || `Track ${t.id}`;
      lines.push(`#EXTINF:${t.duration_secs ?? -1},${title}`);
      const tok = tokens[i];
      lines.push(tok ? streamUrl(t.id, tok) : `${getServerUrl()}/stream/${t.id}`);
    });
    const blob = new Blob([lines.join("\n") + "\n"], { type: "audio/x-mpegurl" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const safeName =
      (props.playlist.name || "playlist").replace(/[^\w\- ]+/g, "").trim() || "playlist";
    a.href = url;
    a.download = `${safeName}.m3u`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("Playlist exported");
  } catch {
    showToast("Export failed");
  }
}
</script>
