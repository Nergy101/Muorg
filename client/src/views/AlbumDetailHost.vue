<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import StarRating from "../components/shared/StarRating.vue";
import { useCatalogStore } from "../stores/catalog";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../composables/useAlbums";
import { useDominantColor, isColorBland } from "../composables/useDominantColor";
import { navigate, metadataEditorOpen } from "../state/navigation";

const props = defineProps<{ albumKey: string }>();

const store = useCatalogStore();
const { tracks, currentPlayingTrackId } = storeToRefs(store);

const albumTracks = computed(() =>
  tracks.value.filter((t) => albumIdentityKey(t.album, albumArtistOf(t)) === props.albumKey),
);
const first = computed(() => albumTracks.value[0]);
const coverTrack = computed(() => albumTracks.value.find((t) => t.has_cover) ?? first.value);
const coverUrl = computed(() => (coverTrack.value ? store.getCoverDataUrl(coverTrack.value.path) : null));
/** Accent color this album's art would give (for the buttons). */
const albumGlowRgb = useDominantColor(coverUrl);
const albumAccent = computed(() =>
  albumGlowRgb.value && !isColorBland(albumGlowRgb.value) ? `rgb(${albumGlowRgb.value})` : "var(--primary)",
);
/** White or dark text on top of the album accent, by its luminance. */
const onAlbumAccent = computed(() => {
  const m = albumAccent.value.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
  if (!m) return "#fff";
  const lum = (0.299 * +m[1] + 0.587 * +m[2] + 0.114 * +m[3]) / 255;
  return lum > 0.45 ? "#1c1917" : "#fff";
});
const artist = computed(() => (first.value ? albumArtistOf(first.value) : "Unknown Artist"));
const totalDuration = computed(() =>
  albumTracks.value.reduce((a, t) => a + (t.duration_secs ?? 0), 0),
);

// ── 3-dots context menu ─────────────────────────────────────────────────────
const menuOpen = ref(false);
function toggleMenu() {
  menuOpen.value = !menuOpen.value;
}
function closeMenu() {
  menuOpen.value = false;
}
function editMetadata() {
  closeMenu();
  store.setSelection(albumTracks.value.map((t) => t.id));
  metadataEditorOpen.value = true;
}

function colorMix(hex: string, alpha: number): string {
  const m = hex.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
  if (!m) return hex;
  return `rgba(${m[1]}, ${m[2]}, ${m[3]}, ${alpha})`;
}

function fmtDuration(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = Math.round(secs % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function playAlbum() {
  if (!albumTracks.value.length) return;
  store.queueTrackIds = albumTracks.value.map((t) => t.id);
  store.setSelection([albumTracks.value[0].id]);
  store.setPlayRequestTrackId(albumTracks.value[0].id);
}

function addToQueue() {
  store.addTracksToQueue(albumTracks.value);
}

function shuffleAlbum() {
  const ids = albumTracks.value.map((t) => t.id);
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  if (!ids.length) return;
  store.queueTrackIds = ids;
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}

function playTrack(track: (typeof albumTracks.value)[number]) {
  store.queueTrackIds = albumTracks.value.map((t) => t.id);
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}

// ── Per-row 3-dots menu ─────────────────────────────────────────────────────
const rowMenu = ref<{ x: number; y: number; track: (typeof albumTracks.value)[number] } | null>(null);
function openRowMenu(e: MouseEvent, track: (typeof albumTracks.value)[number]) {
  e.stopPropagation();
  rowMenu.value = { x: e.clientX, y: e.clientY, track };
}
function closeRowMenu() {
  rowMenu.value = null;
}
function rowMenuPlay() {
  if (rowMenu.value) playTrack(rowMenu.value.track);
  closeRowMenu();
}
function rowMenuQueue() {
  if (rowMenu.value) store.addToQueue([rowMenu.value.track.id]);
  closeRowMenu();
}
function rowMenuArtist() {
  if (rowMenu.value) navigate({ name: "artist", key: albumArtistOf(rowMenu.value.track) });
  closeRowMenu();
}
function rowMenuEdit() {
  if (rowMenu.value) {
    store.setSelection([rowMenu.value.track.id]);
    metadataEditorOpen.value = true;
  }
  closeRowMenu();
}
</script>

<template>
  <div class="app-main-scroll">
    <div v-if="first" class="relative min-h-full">
      <!-- The blurred album-art backdrop is rendered at island level (App.vue),
           so it also shows translucently behind the integrated topbar. -->
      <div class="view-pad relative z-[1]">
        <!-- Header -->
        <div class="mb-4 flex items-end gap-4">
          <div class="h-32 w-32 shrink-0 overflow-hidden rounded-xl bg-surface-variant shadow-lg">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              :alt="first.album ?? ''"
              class="h-full w-full object-cover"
              draggable="false"
            />
            <div v-else class="flex h-full w-full items-center justify-center text-3xl font-extrabold text-on-surface-variant">
              {{ (first.album ?? "?").slice(0, 2).toUpperCase() }}
            </div>
          </div>
          <div class="min-w-0 flex-1 pb-1">
            <div class="text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">Album</div>
            <h1 class="view-title truncate">{{ first.album ?? "Unknown Album" }}</h1>
            <div class="view-subtitle">
              <button type="button" class="hover:underline" @click="navigate({ name: 'artist', key: artist })">{{ artist }}</button><template v-if="first.year"> · {{ first.year }}</template>
              · {{ albumTracks.length }} tracks · {{ fmtDuration(totalDuration) }}
            </div>
          </div>
        </div>

        <!-- Controls: under the album art, above the track list.
             Order: 3-dots menu, add to queue, shuffle, play (only Play is filled). -->
        <div class="mb-4 flex items-center gap-2">
          <div class="relative">
            <button
              type="button"
              class="topbar-pill-btn !h-9 !min-w-9"
              :style="{ borderColor: albumAccent, color: onAlbumAccent, background: colorMix(albumAccent, 0.12), '--btn-accent': albumAccent, '--btn-on': onAlbumAccent }"
              aria-label="More actions"
              title="More actions"
              @click="toggleMenu"
            >
              <FeatherIcon name="more-horizontal" class="h-4 w-4" />
            </button>
            <div
              v-if="menuOpen"
              class="absolute left-0 z-20 mt-1 min-w-[180px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
              @mouseleave="closeMenu"
            >
              <button
                type="button"
                class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container"
                @click="editMetadata"
              >
                <FeatherIcon name="edit-2" class="h-4 w-4 text-on-surface-variant" />
                Edit metadata
              </button>
              <button
                type="button"
                class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container"
                @click="addToQueue(); closeMenu()"
              >
                <FeatherIcon name="layers" class="h-4 w-4 text-on-surface-variant" />
                Add to queue
              </button>
              <button
                type="button"
                class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container"
                @click="closeMenu(); navigate({ name: 'artist', key: artist })"
              >
                <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" />
                View artist
              </button>
            </div>
          </div>
          <button
            type="button"
            class="btn-outline"
            :style="{ borderColor: albumAccent, color: onAlbumAccent, background: colorMix(albumAccent, 0.12), '--btn-accent': albumAccent, '--btn-on': onAlbumAccent }"
            @click="addToQueue"
          >
            <FeatherIcon name="layers" class="h-4 w-4" /> Add to queue
          </button>
          <button
            type="button"
            class="btn-outline"
            :style="{ borderColor: albumAccent, color: onAlbumAccent, background: colorMix(albumAccent, 0.12), '--btn-accent': albumAccent, '--btn-on': onAlbumAccent }"
            @click="shuffleAlbum"
          >
            <FeatherIcon name="shuffle" class="h-4 w-4" /> Shuffle
          </button>
          <button
            type="button"
            class="btn-primary"
            :style="{ background: albumAccent, color: onAlbumAccent, borderColor: albumAccent, '--btn-accent': albumAccent, '--btn-on': onAlbumAccent }"
            @click="playAlbum"
          >
            <FeatherIcon name="play" class="ml-0.5 h-4 w-4" /> Play
          </button>
        </div>

        <!-- Track rows (web-app style list, not a table) -->
        <div class="overflow-hidden rounded-2xl">
          <button
            v-for="(track, i) in albumTracks"
            :key="track.id"
            type="button"
            class="flex w-full items-center gap-3 px-3 py-2 text-left transition-colors"
            :class="currentPlayingTrackId === track.id
              ? 'bg-[color-mix(in_srgb,var(--primary)_13%,transparent)]'
              : 'hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]'"
            @click="playTrack(track)"
          >
            <span
              class="w-6 shrink-0 text-right text-xs tabular-nums"
              :class="currentPlayingTrackId === track.id ? 'text-on-primary-container' : 'text-on-surface-variant'"
            >
              {{ i + 1 }}
            </span>
            <span
              class="min-w-0 flex-1 truncate text-[13.5px]"
              :class="currentPlayingTrackId === track.id ? 'font-semibold text-on-surface' : 'text-on-surface'"
            >
              {{ track.title ?? "Unknown Title" }}
            </span>
            <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
            <span class="shrink-0" @click.stop>
              <StarRating :model-value="track.rating ?? null" @update:model-value="(r) => store.setRating([track.path], r)" />
            </span>
            <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">
              {{ fmtDuration(track.duration_secs ?? 0) }}
            </span>
            <span
              class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container"
              role="button"
              aria-label="More actions"
              @click.stop="openRowMenu($event, track)"
            >
              <FeatherIcon name="more-horizontal" class="h-4 w-4" />
            </span>
          </button>
        </div>
      </div>
    </div>

    <div v-else class="flex h-full items-center justify-center text-sm text-on-surface-variant">
      Album not found.
    </div>
    <!-- Row context menu -->
    <Teleport to="body">
      <div
        v-if="rowMenu"
        class="fixed z-[300] min-w-[170px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
        :style="{ left: rowMenu.x + 'px', top: rowMenu.y + 'px' }"
        @click.stop
      >
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="rowMenuPlay">
          <FeatherIcon name="play" class="h-4 w-4 text-on-surface-variant" /> Play
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="rowMenuQueue">
          <FeatherIcon name="layers" class="h-4 w-4 text-on-surface-variant" /> Add to queue
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="rowMenuArtist">
          <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="rowMenuEdit">
          <FeatherIcon name="edit-2" class="h-4 w-4 text-on-surface-variant" /> Edit metadata
        </button>
      </div>
      <div v-if="rowMenu" class="fixed inset-0 z-[299]" @click="closeRowMenu" @contextmenu.prevent="closeRowMenu" />
    </Teleport>
  </div>
</template>
