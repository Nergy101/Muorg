<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useAlbums } from "../composables/useAlbums";
import ArtistMosaic from "../components/shared/ArtistMosaic.vue";
import CardHoverActions from "../components/shared/CardHoverActions.vue";
import StarRating from "../components/shared/StarRating.vue";
import TrackAlbumArt from "../components/shared/TrackAlbumArt.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { navigate } from "../state/navigation";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../composables/useAlbums";

const props = defineProps<{ artist: string }>();

const store = useCatalogStore();
const { tracks } = storeToRefs(store);

const artistTracks = computed(() =>
  tracks.value.filter(
    (t) => ((t.album_artist ?? t.artist ?? "").trim() || "Unknown Artist") === props.artist,
  ),
);
const artistAlbums = useAlbums(computed(() => artistTracks.value));

// ── Sorting (Albums + Tracks headers) ───────────────────────────────────────
type SortKey = "date" | "alpha" | "plays";
type SortDir = "asc" | "desc";
interface SortState { key: SortKey; dir: SortDir; }

/** Click cycles: date asc → date desc → alpha asc → alpha desc → plays asc → plays desc. */
const SORT_CYCLE: SortState[] = [
  { key: "date", dir: "asc" },
  { key: "date", dir: "desc" },
  { key: "alpha", dir: "asc" },
  { key: "alpha", dir: "desc" },
  { key: "plays", dir: "asc" },
  { key: "plays", dir: "desc" },
];
const albumSort = ref<SortState>({ key: "date", dir: "asc" });
const trackSort = ref<SortState>({ key: "date", dir: "asc" });

function nextSort(s: SortState): SortState {
  const i = SORT_CYCLE.findIndex((c) => c.key === s.key && c.dir === s.dir);
  return SORT_CYCLE[(i + 1) % SORT_CYCLE.length];
}

const SORT_LABELS: Record<SortKey, string> = { date: "Release date", alpha: "Alphabetical", plays: "Most listened" };
function sortTitle(s: SortState): string {
  return `${SORT_LABELS[s.key]} (${s.dir === "asc" ? "ascending" : "descending"}) — click to change`;
}

const trackById = computed(() => new Map(tracks.value.map((t) => [t.id, t])));
function albumPlayCount(a: (typeof artistAlbums.value)[number]): number {
  return a.trackIds.reduce((sum, id) => sum + (trackById.value.get(id)?.play_count ?? 0), 0);
}

const sortedAlbums = computed(() => {
  const { key, dir } = albumSort.value;
  const mul = dir === "asc" ? 1 : -1;
  return [...artistAlbums.value].sort((a, b) => {
    if (key === "date") return ((a.year ?? 0) - (b.year ?? 0)) * mul || a.album.localeCompare(b.album);
    if (key === "alpha") return a.album.localeCompare(b.album) * mul;
    return (albumPlayCount(a) - albumPlayCount(b)) * mul || a.album.localeCompare(b.album);
  });
});

const sortedTracks = computed(() => {
  const { key, dir } = trackSort.value;
  const mul = dir === "asc" ? 1 : -1;
  return [...artistTracks.value].sort((a, b) => {
    if (key === "date") return ((a.year ?? 0) - (b.year ?? 0)) * mul || (a.title ?? "").localeCompare(b.title ?? "");
    if (key === "alpha") return (a.title ?? "").localeCompare(b.title ?? "") * mul;
    return ((a.play_count ?? 0) - (b.play_count ?? 0)) * mul || (a.title ?? "").localeCompare(b.title ?? "");
  });
});

/** Avatar: 1/2/3/4-album mosaic of the artist's first and last 2 distinct albums. */
const avatarCovers = computed(() => {
  const distinct = [...new Map(artistAlbums.value.map((a) => [a.key, a])).values()]
    .sort((a, b) => (a.year ?? 0) - (b.year ?? 0));
  const picked = [...distinct.slice(0, 2), ...distinct.slice(-2)];
  const seen = new Set<string>();
  const urls: string[] = [];
  for (const a of picked) {
    if (seen.has(a.key)) continue;
    seen.add(a.key);
    const url = store.getCoverDataUrl(a.coverPath);
    if (url) urls.push(url);
    if (urls.length === 4) break;
  }
  return urls;
});

function openAlbum(key: string) {
  navigate({ name: "album", key });
}

// ── Album card context menu ─────────────────────────────────────────────────
const albumMenu = ref<{ x: number; y: number; key: string } | null>(null);
function onAlbumContextMenu(e: MouseEvent, key: string) {
  e.preventDefault();
  albumMenu.value = { x: e.clientX, y: e.clientY, key };
}
function closeAlbumMenu() {
  albumMenu.value = null;
}
const menuAlbum = computed(() => artistAlbums.value.find((a) => a.key === albumMenu.value?.key) ?? null);
function playAlbumCard(a: (typeof artistAlbums.value)[number]) {
  if (!a.trackIds.length) return;
  store.queueTrackIds = [...a.trackIds];
  store.setSelection([a.trackIds[0]]);
  store.setPlayRequestTrackId(a.trackIds[0]);
}
function shuffleAlbumCard(a: (typeof artistAlbums.value)[number]) {
  const ids = [...a.trackIds];
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  if (!ids.length) return;
  store.queueTrackIds = ids;
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}
function menuViewArtist() {
  closeAlbumMenu();
  navigate({ name: "artist", key: props.artist });
}

// ── Track row 3-dots menu ────────────────────────────────────────────────────
const trackMenu = ref<{ x: number; y: number; track: (typeof artistTracks.value)[number] } | null>(null);
function openTrackMenu(e: MouseEvent, track: (typeof artistTracks.value)[number]) {
  e.stopPropagation();
  trackMenu.value = { x: e.clientX, y: e.clientY, track };
}
function closeTrackMenu() {
  trackMenu.value = null;
}
function trackMenuPlay() {
  if (trackMenu.value) playTrack(trackMenu.value.track);
  closeTrackMenu();
}
function trackMenuQueue() {
  if (trackMenu.value) store.addToQueue([trackMenu.value.track.id]);
  closeTrackMenu();
}
function trackMenuAlbum() {
  if (trackMenu.value) navigate({ name: "album", key: albumIdentityKey(trackMenu.value.track.album, albumArtistOf(trackMenu.value.track)) });
  closeTrackMenu();
}

function shufflePlayAll() {
  const ids = artistTracks.value.map((t) => t.id);
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  if (!ids.length) return;
  store.queueTrackIds = ids;
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}

function playTrack(track: (typeof artistTracks.value)[number]) {
  store.queueTrackIds = artistTracks.value.map((t) => t.id);
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}

function fmtDuration(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = Math.round(secs % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="view-pad flex min-h-0 flex-1 flex-col">
      <div class="mb-5 flex items-center gap-4">
        <div class="h-16 w-16 shrink-0 overflow-hidden rounded-full bg-surface-variant">
          <ArtistMosaic :covers="avatarCovers" :fallback="artist" />
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="view-title truncate">{{ artist }}</h1>
          <div class="view-subtitle">{{ artistAlbums.length }} albums · {{ artistTracks.length }} tracks</div>
        </div>
        <button type="button" class="btn-primary" :disabled="!artistTracks.length" @click="shufflePlayAll">
          <FeatherIcon name="shuffle" class="h-4 w-4" /> Shuffle Play
        </button>
      </div>

      <div class="mb-3 flex items-center gap-2">
        <h2 class="text-[15px] font-bold text-on-background">Albums</h2>
        <button
          type="button"
          class="flex items-center gap-1 rounded-full px-2 py-1 text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          :title="sortTitle(albumSort)"
          :aria-label="`Sort albums: ${sortTitle(albumSort)}`"
          @click="albumSort = nextSort(albumSort)"
        >
          <FeatherIcon :name="albumSort.dir === 'asc' ? 'arrow-up' : 'arrow-down'" class="h-3.5 w-3.5" />
          <span class="text-[10px] font-semibold uppercase tracking-wide">{{ SORT_LABELS[albumSort.key] }}</span>
        </button>
      </div>
      <div class="shelf-grid mb-6">
        <button
          v-for="album in sortedAlbums"
          :key="album.key"
          type="button"
          class="album-card"
          @click="openAlbum(album.key)"
          @contextmenu="onAlbumContextMenu($event, album.key)"
        >
          <div class="mb-2 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant">
            <img
              v-if="store.getCoverDataUrl(album.coverPath)"
              :src="store.getCoverDataUrl(album.coverPath)!"
              :alt="album.album"
              class="h-full w-full object-cover"
              draggable="false"
            />
            <div v-else class="flex h-full w-full items-center justify-center text-2xl font-extrabold text-on-surface-variant">
              {{ album.album.slice(0, 2).toUpperCase() }}
            </div>
          </div>
          <div class="truncate text-[13px] font-semibold text-on-surface">{{ album.album }}</div>
          <div class="text-[11.5px] text-on-surface-variant">{{ album.year ?? '—' }} · {{ album.trackCount }} tracks</div>
          <CardHoverActions :cover-path="album.coverPath" @play="playAlbumCard(album)" @shuffle="shuffleAlbumCard(album)" />
        </button>
      </div>

      <!-- Album card context menu -->
      <Teleport to="body">
        <div
          v-if="albumMenu"
          class="fixed z-[300] min-w-[170px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
          :style="{ left: albumMenu.x + 'px', top: albumMenu.y + 'px' }"
          @click.stop
        >
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuAlbum && playAlbumCard(menuAlbum); closeAlbumMenu()">
            <FeatherIcon name="play" class="h-4 w-4 text-on-surface-variant" /> Play
          </button>
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuAlbum && shuffleAlbumCard(menuAlbum); closeAlbumMenu()">
            <FeatherIcon name="shuffle" class="h-4 w-4 text-on-surface-variant" /> Shuffle
          </button>
          <div class="my-1 border-t border-outline" />
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="albumMenu && openAlbum(albumMenu.key); closeAlbumMenu()">
            <FeatherIcon name="disc" class="h-4 w-4 text-on-surface-variant" /> View album
          </button>
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewArtist">
            <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
          </button>
        </div>
        <div v-if="albumMenu" class="fixed inset-0 z-[299]" @click="closeAlbumMenu" @contextmenu.prevent="closeAlbumMenu" />
      </Teleport>

      <div class="mb-3 flex items-center gap-2">
        <h2 class="text-[15px] font-bold text-on-background">Tracks</h2>
        <button
          type="button"
          class="flex items-center gap-1 rounded-full px-2 py-1 text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          :title="sortTitle(trackSort)"
          :aria-label="`Sort tracks: ${sortTitle(trackSort)}`"
          @click="trackSort = nextSort(trackSort)"
        >
          <FeatherIcon :name="trackSort.dir === 'asc' ? 'arrow-up' : 'arrow-down'" class="h-3.5 w-3.5" />
          <span class="text-[10px] font-semibold uppercase tracking-wide">{{ SORT_LABELS[trackSort.key] }}</span>
        </button>
      </div>
      <!-- Track rows fill the remaining height and scroll -->
      <div class="min-h-0 flex-1 overflow-y-auto rounded-2xl pb-24">
        <button
          v-for="track in sortedTracks"
          :key="track.id"
          type="button"
          class="group flex w-full items-center gap-2.5 border-b border-outline/10 px-2 py-2 text-left transition-colors last:border-b-0"
          :class="store.currentPlayingTrackId === track.id
            ? 'bg-[color-mix(in_srgb,var(--primary)_13%,transparent)]'
            : 'hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]'"
          @click="playTrack(track)"
        >
          <TrackAlbumArt :path="track.path" size="small" class="!h-9 !w-9 shrink-0 rounded-md" />
          <span class="min-w-0 flex-[2] truncate text-[13.5px]" :class="store.currentPlayingTrackId === track.id ? 'font-semibold text-on-surface' : 'text-on-surface'">
            {{ track.title ?? "Unknown Title" }}
          </span>
          <span class="hidden min-w-0 flex-1 truncate text-xs text-on-surface-variant md:block">
            {{ track.album ?? "" }}
          </span>
          <span class="shrink-0" @click.stop>
            <StarRating :model-value="track.rating ?? null" @update:model-value="(r) => store.setRating([track.path], r)" />
          </span>
          <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
          <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">
            {{ fmtDuration(track.duration_secs ?? 0) }}
          </span>
          <span
            class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container"
            role="button"
            aria-label="More actions"
            @click.stop="openTrackMenu($event, track)"
          >
            <FeatherIcon name="more-horizontal" class="h-4 w-4" />
          </span>
        </button>
      </div>
    </div>

    <!-- Track row context menu -->
    <Teleport to="body">
      <div
        v-if="trackMenu"
        class="fixed z-[300] min-w-[170px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
        :style="{ left: trackMenu.x + 'px', top: trackMenu.y + 'px' }"
        @click.stop
      >
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="trackMenuPlay">
          <FeatherIcon name="play" class="h-4 w-4 text-on-surface-variant" /> Play
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="trackMenuQueue">
          <FeatherIcon name="layers" class="h-4 w-4 text-on-surface-variant" /> Add to queue
        </button>
        <div class="my-1 border-t border-outline" />
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="trackMenuAlbum">
          <FeatherIcon name="disc" class="h-4 w-4 text-on-surface-variant" /> View album
        </button>
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewArtist(); closeTrackMenu()">
          <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
        </button>
      </div>
      <div v-if="trackMenu" class="fixed inset-0 z-[299]" @click="closeTrackMenu" @contextmenu.prevent="closeTrackMenu" />
    </Teleport>
  </div>
</template>
