<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useCatalogStore } from "../stores/catalog";
import { useAlbums } from "../composables/useAlbums";
import { navigate, activeMix } from "../state/navigation";
import { useSettingsStore } from "../stores/settings";
import { useProfilesStore } from "../stores/profiles";

const settings = useSettingsStore();
const profilesStore = useProfilesStore();
const cardStyle = computed(() => settings.cardStyle);
// Mix cards follow the card layout setting only when enabled; otherwise "below".
const mixCardStyle = computed(() => (settings.mixCardsFollowLayout ? cardStyle.value : "below"));
import { reportCount } from "@shared/reports";
import CardHoverActions from "../components/shared/CardHoverActions.vue";
import { vMarquee } from "../composables/useMarquee";
import type { AlbumGridItem } from "../components/library/AlbumGridView.vue";

const store = useCatalogStore();
const { tracks } = storeToRefs(store);
// Home is not affected by the topbar search filter — always the full catalog.
const albums = useAlbums(computed(() => store.tracks));

// ── Grid columns (handover §6: real client's useGridColumns logic) ──────────
const gridRef = ref<HTMLElement | null>(null);
const gridWidth = ref(0);
/** Columns the compact shelf grid actually renders (148px cards + 12px gap),
 *  measured on the grid element itself — the real island width available to
 *  the section — so the visible cap fills exactly 2 rows. */
const cols = computed(() => {
  const w = gridWidth.value;
  if (w <= 0) return 3;
  return Math.max(2, Math.floor((w + 12) / 160));
});
let ro: ResizeObserver | null = null;
onMounted(() => {
  const el = gridRef.value;
  if (!el) return;
  ro = new ResizeObserver(() => { gridWidth.value = el.clientWidth; });
  ro.observe(el);
  gridWidth.value = el.clientWidth;
});
onBeforeUnmount(() => ro?.disconnect());

// ── Hero ────────────────────────────────────────────────────────────────────
const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5) return "Good night";
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
});

function shuffleAll() {
  store.startShuffleAll(store.tracks);
}

function playRandomMix() {
  const pool = mixes.value;
  if (!pool.length) return;
  const mix = pool[Math.floor(Math.random() * pool.length)];
  playIds(mix.trackIds);
}

const missingTags = computed(() => reportCount("missing_metadata", tracks.value));
const missingArt = computed(() => reportCount("missing_album_cover", tracks.value));

// ── Recommended shelf: session-stable shuffled pool, exclude no-art albums ──
const recommendedPool = ref<AlbumGridItem[]>([]);
const recommendedRolled = ref(false);

function rollRecommended() {
  const pool = albums.value.filter((a) => a.hasCover);
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  recommendedPool.value = pool;
  recommendedRolled.value = true;
}

// Recommended fills 2 rows at the current column count (screen looks full),
// independent of the topbar search.
const recommended = computed(() => recommendedPool.value.slice(0, cols.value * 2));

/** Mixes shelf shows exactly 2 rows at the current column count. */
const displayedMixes = computed(() => mixes.value.slice(0, cols.value * 2));

// ── Mixes shelf: genre-cohort mixes with random colored lucide icons, shared
//    with the web/android clients via @shared/composables/useMixes ──────────
import { useMixes, type Mix } from "@shared/composables/useMixes";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import { resolvePlaylistIcon } from "@shared/playlistIcons";

const { mixes, refresh: refreshMixes } = useMixes({
  tracks: () => store.tracks,
  ready: () => !store.loading && !store.loadingMore,
  count: () => 16, // desktop Home shows all cohorts, not just 8
});

/** Cover paths of a mix's first tracks (for the hover play accent + detail page). */
function mixCoverPaths(mix: Mix): string[] {
  const byId = new Map(tracks.value.map((t) => [t.id, t]));
  return mix.trackIds.slice(0, 4).map((id) => byId.get(id)?.path).filter((p): p is string => Boolean(p));
}
function mixArtists(mix: Mix): string {
  const byId = new Map(tracks.value.map((t) => [t.id, t]));
  const artists = [...new Set(mix.trackIds.map((id) => byId.get(id)).filter((t): t is NonNullable<typeof t> => Boolean(t)).map((t) => (t.artist ?? t.album_artist ?? "").trim()).filter(Boolean))];
  return artists.slice(0, 3).join(", ");
}

function coverUrl(path: string): string | null {
  return store.getCoverDataUrl(path);
}

// Covers are fetched lazily — make sure every visible card's art is requested.
watch([recommended, mixes], () => {
  for (const a of recommended.value) store.fetchCover(a.coverPath);
  for (const m of mixes.value) for (const p of mixCoverPaths(m)) store.fetchCover(p);
}, { immediate: true });

function openAlbum(key: string) {
  navigate({ name: "album", key });
}

/** Queue the tracks and start playback — the player bar watches the selection,
 *  so the first track must be selected for playRequestTrackId to take effect. */
function playIds(ids: number[]) {
  if (!ids.length) return;
  store.queueTrackIds = [...ids];
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}

function playAlbum(album: AlbumGridItem) {
  playIds(album.trackIds);
}

function shuffleIds(ids: number[]) {
  const shuffled = [...ids];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  playIds(shuffled);
}

function shuffleAlbum(album: AlbumGridItem) {
  shuffleIds(album.trackIds);
}

function playMix(mix: Mix) {
  playIds(mix.trackIds);
}

/** Clicking a mix card opens its playlist-style page (not direct play). */
function openMix(mix: Mix) {
  activeMix.value = { name: mix.name, trackIds: mix.trackIds, coverPaths: mixCoverPaths(mix), artists: mixArtists(mix) };
  navigate({ name: "mix" });
}

function shuffleMix(mix: Mix) {
  shuffleIds(mix.trackIds);
}

/** The mix's lucide icon color, used to tint the hover Play button. */
function mixIconColor(mix: Mix): string {
  return resolvePlaylistIcon(mix.icon).color;
}

const refreshSpin = ref(false);
function reroll() {
  refreshSpin.value = true;
  rollRecommended();
  refreshMixes();
  setTimeout(() => { refreshSpin.value = false; }, 720);
}

onMounted(() => {
  if (!recommendedRolled.value) rollRecommended();
});
</script>

<template>
  <div class="app-main-scroll view-pad">
    <!-- Hero -->
    <section class="mb-6 flex flex-wrap items-center gap-3">
      <h1 class="view-title mr-auto">{{ greeting }}</h1>
      <div class="flex items-center gap-2">
        <button type="button" class="btn-primary" @click="shuffleAll">
          <FeatherIcon name="shuffle" class="h-4 w-4" /> Shuffle all
        </button>
        <button type="button" class="btn-outline" @click="playRandomMix">
          <FeatherIcon name="play" class="h-4 w-4" /> Play random mix
        </button>
      </div>
      <div class="flex w-full flex-wrap items-center gap-2 pt-1 sm:w-auto">
        <!-- NER-326: the active listening profile is always visible on Home. -->
        <div
          v-if="profilesStore.enabled && profilesStore.activeProfile"
          class="stat-chip"
          :title="'Play history and stats are scoped to this profile — change it in Settings'"
        >
          <FeatherIcon name="user" class="h-3.5 w-3.5 text-primary" />
          <b>Listening as {{ profilesStore.activeProfile.name }}</b>
        </div>
        <button type="button" class="stat-chip" @click="navigate({ name: 'list' })">
          <b>{{ tracks.length }}</b> tracks
        </button>
        <button type="button" class="stat-chip" @click="navigate({ name: 'albums' })">
          <b>{{ albums.length }}</b> albums
        </button>
        <button v-if="missingTags > 0" type="button" class="stat-chip" @click="navigate({ name: 'reports' })">
          <b>{{ missingTags }}</b> missing tags
        </button>
        <button v-if="missingArt > 0" type="button" class="stat-chip" @click="navigate({ name: 'reports' })">
          <b>{{ missingArt }}</b> missing art
        </button>
      </div>
    </section>

    <!-- Recommended -->
    <section class="mb-6">
      <div class="mb-3 flex items-center gap-2">
        <FeatherIcon name="heart" class="h-5 w-5" :style="{ color: 'var(--primary)' }" />
        <h2 class="text-[15px] font-bold text-on-background">Recommended</h2>
        <button
          type="button"
          class="ml-auto flex h-7 w-7 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container"
          title="Refresh"
          @click="reroll"
        >
          <FeatherIcon
            name="refresh-cw"
            class="h-4 w-4"
            :style="refreshSpin ? { animation: 'marq-scroll 0s', transform: 'rotate(720deg)', transition: 'transform 720ms ease' } : {}"
          />
        </button>
      </div>
      <div ref="gridRef" class="shelf-grid-sm">
        <button
          v-for="album in recommended"
          :key="album.key"
          type="button"
          :class="cardStyle === 'overlay' ? 'group relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10' : 'album-card'"
          @click="openAlbum(album.key)"
        >
          <div :class="cardStyle === 'overlay' ? 'absolute inset-0' : 'cover-wrap mb-2 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant'">
            <img
              v-if="coverUrl(album.coverPath)"
              :src="coverUrl(album.coverPath)!"
              :alt="album.album"
              class="h-full w-full object-cover"
              draggable="false"
            />
            <div v-else class="flex h-full w-full items-center justify-center text-2xl font-extrabold text-on-surface-variant">
              {{ album.album.slice(0, 2).toUpperCase() }}
            </div>
            <template v-if="cardStyle === 'overlay'">
              <div class="pointer-events-none absolute inset-x-0 bottom-0 h-[64px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]" aria-hidden="true" />
              <div class="absolute inset-x-0 bottom-0 flex h-[64px] items-end pl-2.5 pr-1 pb-2">
                <div class="min-w-0 flex-1">
                  <div v-marquee class="marq text-[13px] font-semibold text-white"><span class="marq-in">{{ album.album }}</span></div>
                  <div v-marquee class="marq text-[11.5px] text-white/70"><span class="marq-in">{{ album.albumArtist }}<template v-if="album.year"> · {{ album.year }}</template></span></div>
                </div>
              </div>
            </template>
            <CardHoverActions :cover-path="album.coverPath" @play="playAlbum(album)" @shuffle="shuffleAlbum(album)" />
          </div>
          <template v-if="cardStyle === 'below'">
            <div class="mt-0.5 h-[18px] text-[13px] font-semibold leading-[18px] text-on-surface">
              <div v-marquee class="marq h-full">
                <span class="marq-in">{{ album.album }}</span>
              </div>
            </div>
            <div class="h-[15px] truncate text-[11.5px] leading-[15px] text-on-surface-variant">
              {{ album.albumArtist }}<template v-if="album.year"> · {{ album.year }}</template>
            </div>
          </template>
        </button>
      </div>
    </section>

    <!-- Mixes -->
    <section class="border-t border-outline/20 pt-5">
      <div class="mb-3 flex items-center gap-2">
        <FeatherIcon name="shuffle" class="h-5 w-5" :style="{ color: 'var(--primary)' }" />
        <h2 class="text-[15px] font-bold text-on-background">Mixes</h2>
      </div>
      <div class="shelf-grid-sm">
        <button
          v-for="mix in displayedMixes"
          :key="mix.name"
          type="button"
          :class="mixCardStyle === 'overlay' ? 'group relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10' : 'album-card'"
          @click="openMix(mix)"
        >
          <div
            class="mosaic-stitched grid aspect-square w-full grid-cols-2 grid-rows-2 overflow-hidden bg-surface-variant"
            :class="mixCardStyle === 'overlay' ? 'absolute inset-0 rounded-none' : 'cover-wrap relative mb-2 rounded-lg'"
            style="gap: 0"
          >
            <template v-for="(p, i) in mixCoverPaths(mix)" :key="i">
              <img
                v-if="coverUrl(p)"
                :src="coverUrl(p)!"
                alt=""
                class="h-full w-full object-cover"
                draggable="false"
              />
              <div v-else class="h-full w-full bg-surface-container" />
            </template>
            <!-- Colored lucide icon centered over the 4-cover grid -->
            <span class="absolute inset-0 flex items-center justify-center">
              <span class="flex h-14 w-14 items-center justify-center rounded-xl shadow-lg ring-1 ring-white/15" style="background: rgb(0 0 0 / 0.35); -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px)">
                <PlaylistIcon :icon="mix.icon" class="!h-7 !w-7" />
              </span>
            </span>
            <template v-if="mixCardStyle === 'overlay'">
              <div class="pointer-events-none absolute inset-x-0 bottom-0 h-[64px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]" aria-hidden="true" />
              <div class="absolute inset-x-0 bottom-0 flex h-[64px] items-end pl-2.5 pr-1 pb-2">
                <div class="min-w-0 flex-1">
                  <div v-marquee class="marq text-[13px] font-semibold text-white"><span class="marq-in">{{ mix.name }}</span></div>
                  <div v-marquee class="marq text-[11.5px] text-white/70"><span class="marq-in">{{ mixArtists(mix) }}</span></div>
                </div>
              </div>
            </template>
            <CardHoverActions :cover-path="mixCoverPaths(mix)[0] ?? ''" :accent-color="mixIconColor(mix)" @play="playMix(mix)" @shuffle="shuffleMix(mix)" />
          </div>
          <template v-if="mixCardStyle === 'below'">
            <div class="mt-0.5 h-[18px] text-[13px] font-semibold leading-[18px] text-on-surface">
              <div v-marquee class="marq h-full">
                <span class="marq-in">{{ mix.name }}</span>
              </div>
            </div>
            <div class="h-[15px] text-[11.5px] leading-[15px] text-on-surface-variant">
              <div v-marquee class="marq h-full truncate">
                <span class="marq-in">{{ mixArtists(mix) }}</span>
              </div>
            </div>
          </template>
        </button>
      </div>
    </section>
  </div>
</template>
