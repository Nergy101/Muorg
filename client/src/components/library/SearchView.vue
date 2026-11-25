<script setup lang="ts">
/**
 * Search page (web-client SearchView equivalent): one glass search pill on
 * top, results grouped into Artists / Albums / Tracks sections below.
 */
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { navigate } from "../../state/navigation";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import AlbumGridCard from "./AlbumGridCard.vue";
import ArtistMosaic from "../shared/ArtistMosaic.vue";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import type { AlbumGridItem } from "./AlbumGridView.vue";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../../composables/useAlbums";
import type { CatalogTrack } from "../../types";

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
  (e: "back"): void;
}>();

const store = useCatalogStore();
const settings = useSettingsStore();
// Search results default to "below"; follow the global card layout only when enabled.
const searchCardStyle = computed(() => (settings.searchResultsFollowLayout ? settings.cardStyle : "below"));
const { searchQuery } = storeToRefs(store);

const historyAlbums = computed(() => settings.searchHistory.filter((h) => h.type === "album"));
const historyArtists = computed(() => settings.searchHistory.filter((h) => h.type === "artist"));

/** Album history entries rendered as album cards. */
const historyAlbumCards = computed<AlbumGridItem[]>(() =>
  historyAlbums.value
    .map((h): AlbumGridItem | null => {
      const tracks = store.tracks.filter((t) => albumIdentityKey(t.album, albumArtistOf(t)) === h.value);
      if (!tracks.length) return null;
      const withCover = tracks.find((t) => t.has_cover);
      const years = tracks.map((t) => t.year).filter((y): y is number => y != null);
      // Use the track's original-cased title, not the lowercased identity key.
      const album = (tracks[0].album ?? "").trim() || "Unknown Album";
      return {
        key: h.value,
        album,
        albumArtist: albumArtistOf(tracks[0]),
        year: years.length ? Math.min(...years) : null,
        trackCount: tracks.length,
        totalDurationSecs: tracks.reduce((a, t) => a + (t.duration_secs ?? 0), 0),
        coverPath: withCover?.path ?? tracks[0].path,
        hasCover: Boolean(withCover),
        trackIds: tracks.map((t) => t.id),
      };
    })
    .filter((a): a is AlbumGridItem => a !== null),
);

/** Artist history entries rendered as artist cards (mosaic + name). */
const historyArtistCards = computed(() =>
  historyArtists.value.map((h) => {
    const tracks = store.tracks.filter((t) => albumArtistOf(t) === h.value);
    const keys = [...new Set(tracks.map((t) => albumIdentityKey(t.album, albumArtistOf(t))))];
    const covers: string[] = [];
    for (const key of keys) {
      const t = tracks.find((x) => albumIdentityKey(x.album, albumArtistOf(x)) === key && x.has_cover);
      if (t) {
        const u = store.getCoverDataUrl(t.path);
        if (u) covers.push(u);
      }
      if (covers.length === 4) break;
    }
    return { name: h.value, covers };
  }),
);

function albumCardFor(key: string): AlbumGridItem | null {
  return historyAlbumCards.value.find((a) => a.key === key) ?? null;
}
function artistCardFor(name: string): { name: string; covers: string[] } | null {
  return historyArtistCards.value.find((a) => a.name === name) ?? null;
}

/** Clicked artist result: record it and open the artist page. */
function onArtistClick(artist: string) {
  settings.addArtistSearch(artist);
  navigate({ name: "artist", key: artist });
}

/** Clicked album result: record it, then open the album page. */
function onOpenAlbum(key: string) {
  settings.addAlbumSearch(key);
  emit("openAlbum", key);
}

/** Clicked track result: record the track id (delegated on data-track-id rows). */
function onResultsClick(e: MouseEvent) {
  const row = (e.target as HTMLElement).closest("[data-track-id]");
  if (!row) return;
  const id = Number((row as HTMLElement).dataset.trackId);
  if (Number.isFinite(id)) settings.addSearchResult(id);
}

// ── Recent searches (shown when the query is empty) ────────────────────────
const recentTracks = computed<CatalogTrack[]>(() => {
  const byId = new Map(store.tracks.map((t) => [t.id, t]));
  return settings.recentSearchResultIds
    .map((id) => byId.get(id))
    .filter((t): t is CatalogTrack => Boolean(t));
});
function playRecent(track: CatalogTrack) {
  store.queueTrackIds = recentTracks.value.map((t) => t.id);
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}

// ── Context menu on recent results ──────────────────────────────────────────
type MenuTarget =
  | { kind: "track"; track: CatalogTrack }
  | { kind: "album"; key: string; artist: string }
  | { kind: "artist"; name: string };
const menu = ref<{ x: number; y: number; target: MenuTarget } | null>(null);
function openMenu(e: MouseEvent, target: MenuTarget) {
  menu.value = { x: e.clientX, y: e.clientY, target };
}
function closeMenu() {
  menu.value = null;
}
function menuRemove() {
  const t = menu.value?.target;
  if (!t) return;
  if (t.kind === "track") settings.removeSearchResult(t.track.id);
  else settings.removeSearchHistoryEntry(t.kind, t.kind === "album" ? t.key : t.name);
  closeMenu();
}
function menuViewArtist() {
  const t = menu.value?.target;
  if (!t) return;
  const name = t.kind === "track" ? albumArtistOf(t.track) : t.kind === "album" ? t.artist : t.name;
  closeMenu();
  if (name && name !== "Unknown Artist") navigate({ name: "artist", key: name });
}
function menuViewAlbum() {
  const t = menu.value?.target;
  if (!t || t.kind !== "track") return;
  closeMenu();
  navigate({ name: "album", key: albumIdentityKey(t.track.album, albumArtistOf(t.track)) });
}

function onHistoryClick(entry: { type: string; value: string }) {
  if (entry.type === "artist") {
    navigate({ name: "artist", key: entry.value });
  } else if (entry.type === "album") {
    emit("openAlbum", entry.value);
  } else {
    store.setSearchQuery(entry.value);
  }
}

const query = computed(() => searchQuery.value.trim().toLowerCase());

const matchingTracks = computed<CatalogTrack[]>(() => {
  if (!query.value) return [];
  return store.tracks.filter((t) =>
    (t.title ?? "").toLowerCase().includes(query.value)
    || (t.artist ?? "").toLowerCase().includes(query.value)
    || (t.album ?? "").toLowerCase().includes(query.value)
    || (t.album_artist ?? "").toLowerCase().includes(query.value)
  );
});

const artistHits = computed(() => {
  const map = new Map<string, number>();
  for (const t of matchingTracks.value) {
    const a = (t.artist ?? t.album_artist ?? "").trim();
    if (!a) continue;
    map.set(a, (map.get(a) ?? 0) + 1);
  }
  return [...map.entries()]
    .map(([artist, count]) => ({ artist, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 12);
});

/** Up to 4 album cover URLs per artist hit, for the mosaic tile. */
const artistCovers = computed(() => {
  const map = new Map<string, string[]>();
  for (const t of matchingTracks.value) {
    const a = (t.artist ?? t.album_artist ?? "").trim();
    if (!a || !t.has_cover) continue;
    const urls = map.get(a) ?? [];
    const url = store.getCoverDataUrl(t.path);
    if (url && !urls.includes(url) && urls.length < 4) urls.push(url);
    map.set(a, urls);
  }
  return map;
});
function coversFor(artist: string): string[] {
  return artistCovers.value.get(artist) ?? [];
}

const albumHits = computed<AlbumGridItem[]>(() => {
  const map = new Map<string, { album: string; albumArtist: string; tracks: CatalogTrack[] }>();
  for (const t of matchingTracks.value) {
    const key = albumIdentityKey(t.album, albumArtistOf(t));
    const existing = map.get(key);
    if (existing) { existing.tracks.push(t); continue; }
    map.set(key, {
      album: (t.album ?? "Unknown Album").trim() || "Unknown Album",
      albumArtist: albumArtistOf(t),
      tracks: [t],
    });
  }
  return [...map.entries()].map(([key, d]) => {
    const withCover = d.tracks.find((t) => t.has_cover);
    const years = d.tracks.map((t) => t.year).filter((y): y is number => y != null);
    return {
      key,
      album: d.album,
      albumArtist: d.albumArtist,
      year: years.length ? Math.min(...years) : null,
      trackCount: d.tracks.length,
      totalDurationSecs: d.tracks.reduce((a, t) => a + (t.duration_secs ?? 0), 0),
      coverPath: withCover?.path ?? d.tracks[0].path,
      hasCover: !!withCover,
      trackIds: d.tracks.map((t) => t.id),
    };
  }).sort((a, b) => a.album.localeCompare(b.album)).slice(0, 24);
});

const trackHits = computed(() => matchingTracks.value.slice(0, 100));

function fmtDur(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = Math.round(secs % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function playTrackHit(track: CatalogTrack) {
  settings.addSearchResult(track.id);
  store.queueTrackIds = [track.id];
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <!-- Results (the topbar already has the search bar) — full width -->
    <div class="min-h-0 flex-1 overflow-y-auto p-4">
      <!-- Recent searches / results when the query is empty -->
      <template v-if="!query">
        <section v-if="recentTracks.length" class="pb-4">
          <div class="flex items-center gap-1.5 py-1 text-label-lg text-on-surface-variant">
            <FeatherIcon name="clock" class="h-4 w-4" />
            <span>Recent results</span>
            <button
              type="button"
              class="ml-auto rounded-full px-2 py-0.5 text-xs text-on-surface-variant hover:bg-surface-container"
              @click="settings.clearSearchResults()"
            >
              Clear
            </button>
          </div>
          <div class="overflow-hidden rounded-xl ring-1 ring-outline/10">
            <button
              v-for="track in recentTracks"
              :key="track.id"
              type="button"
              class="flex w-full items-center gap-2.5 border-b border-outline/10 px-2 py-2 text-left transition-colors last:border-b-0 hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]"
              @click="playRecent(track)"
              @contextmenu.prevent="openMenu($event, { kind: 'track', track })"
            >
              <TrackAlbumArt :path="track.path" size="small" class="!h-9 !w-9 shrink-0 rounded-md" />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[13.5px] text-on-surface">{{ track.title ?? "Unknown Title" }}</span>
                <span class="block truncate text-xs text-on-surface-variant">
                  {{ track.artist ?? track.album_artist ?? "" }}<template v-if="track.album"> · {{ track.album }}
    <!-- Context menu on recent results -->
    <Teleport to="body">
      <div
        v-if="menu"
        class="fixed z-[400] min-w-[180px] rounded-2xl bg-surface py-1 shadow-xl ring-1 ring-outline/20"
        :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
        @click.stop
      >
        <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-error hover:bg-surface-container" @click="menuRemove">
          <FeatherIcon name="trash-2" class="h-4 w-4" /> Remove from recent
        </button>
        <div v-if="menu.target.kind === 'album'" class="my-1 border-t border-outline" />
        <button v-if="menu.target.kind === 'album'" type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewArtist">
          <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
        </button>
        <template v-if="menu.target.kind === 'track'">
          <div class="my-1 border-t border-outline" />
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewAlbum">
            <FeatherIcon name="disc" class="h-4 w-4 text-on-surface-variant" /> View album
          </button>
          <button type="button" class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-on-surface hover:bg-surface-container" @click="menuViewArtist">
            <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" /> View artist
          </button>
        </template>
      </div>
      <div v-if="menu" class="fixed inset-0 z-[399]" @click="closeMenu" @contextmenu.prevent="closeMenu" />
    </Teleport>
</template>
                </span>
              </span>
              <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
              <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDur(track.duration_secs ?? 0) }}</span>
              <span
                class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container"
                role="button"
                aria-label="More actions"
                @click.stop="openMenu($event, { kind: 'track', track })"
              >
                <FeatherIcon name="more-horizontal" class="h-4 w-4" />
              </span>
            </button>
          </div>
        </section>

        <section v-if="settings.searchHistory.length" class="pb-4">
          <div class="flex items-center gap-1.5 py-1 text-label-lg text-on-surface-variant">
            <FeatherIcon name="search" class="h-4 w-4" />
            <span>Recent searches</span>
            <button
              type="button"
              class="ml-auto rounded-full px-2 py-0.5 text-xs text-on-surface-variant hover:bg-surface-container"
              @click="settings.clearSearchHistory()"
            >
              Clear
            </button>
          </div>
          <!-- Query chips, most recent first -->
          <div class="mb-3 flex flex-wrap gap-2">
            <button
              v-for="entry in settings.searchHistory.filter((h) => h.type === 'query')"
              :key="`query:${entry.value}`"
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-surface px-3 py-1.5 text-xs text-on-surface hover:bg-surface-variant"
              @click="onHistoryClick(entry)"
            >
              <FeatherIcon name="search" class="h-3.5 w-3.5 text-on-surface-variant" />
              <span class="max-w-52 truncate">{{ entry.value }}</span>
            </button>
          </div>
          <!-- Album + artist cards interleaved, most recent first -->
          <div class="shelf-grid">
            <template v-for="entry in settings.searchHistory.filter((h) => h.type !== 'query')" :key="`${entry.type}:${entry.value}`">
              <button
                v-if="entry.type === 'album' && albumCardFor(entry.value)"
                type="button"
                :class="searchCardStyle === 'overlay' ? 'group relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10' : 'album-card'"
                @click="onOpenAlbum(entry.value)"
                @contextmenu.prevent="openMenu($event, { kind: 'album', key: entry.value, artist: albumCardFor(entry.value)?.albumArtist ?? '' })"
              >
                <div :class="searchCardStyle === 'overlay' ? 'absolute inset-0' : 'mb-2 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant'">
                  <img
                    v-if="store.getCoverDataUrl(albumCardFor(entry.value)!.coverPath)"
                    :src="store.getCoverDataUrl(albumCardFor(entry.value)!.coverPath)!"
                    :alt="albumCardFor(entry.value)!.album"
                    class="h-full w-full object-cover"
                    draggable="false"
                  />
                  <div v-else class="flex h-full w-full items-center justify-center text-2xl font-extrabold text-on-surface-variant">
                    {{ albumCardFor(entry.value)!.album.slice(0, 2).toUpperCase() }}
                  </div>
                  <template v-if="searchCardStyle === 'overlay'">
                    <div class="pointer-events-none absolute inset-x-0 bottom-0 h-[56px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]" aria-hidden="true" />
                    <div class="absolute inset-x-0 bottom-0 flex h-[56px] items-end pl-2.5 pr-1 pb-2">
                      <FeatherIcon name="disc" class="h-4 w-4 shrink-0 text-white/80" />
                      <span class="marq ml-1.5 min-w-0 flex-1 text-[13px] font-semibold text-white"><span class="marq-in">{{ albumCardFor(entry.value)!.album }}</span></span>
                    </div>
                  </template>
                </div>
                <div v-if="searchCardStyle === 'below'" class="flex items-center gap-2">
                  <FeatherIcon name="disc" class="h-4 w-4 shrink-0 self-center text-on-surface-variant" />
                  <span class="marq min-w-0 flex-1 text-[13px] font-semibold text-on-surface"><span class="marq-in">{{ albumCardFor(entry.value)!.album }}</span></span>
                </div>
              </button>
              <button
                v-else-if="entry.type === 'artist' && artistCardFor(entry.value)"
                type="button"
                :class="searchCardStyle === 'overlay' ? 'group relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10' : 'album-card'"
                @click="navigate({ name: 'artist', key: entry.value })"
                @contextmenu.prevent="openMenu($event, { kind: 'artist', name: entry.value })"
              >
                <div :class="searchCardStyle === 'overlay' ? 'absolute inset-0' : 'mb-2 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant'">
                  <ArtistMosaic :covers="artistCardFor(entry.value)!.covers" :fallback="entry.value" />
                  <template v-if="searchCardStyle === 'overlay'">
                    <div class="pointer-events-none absolute inset-x-0 bottom-0 h-[56px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]" aria-hidden="true" />
                    <div class="absolute inset-x-0 bottom-0 flex h-[56px] items-end pl-2.5 pr-1 pb-2">
                      <FeatherIcon name="user" class="h-4 w-4 shrink-0 text-white/80" />
                      <span class="marq ml-1.5 min-w-0 flex-1 text-[13px] font-semibold text-white"><span class="marq-in">{{ entry.value }}</span></span>
                    </div>
                  </template>
                </div>
                <div v-if="searchCardStyle === 'below'" class="flex items-center gap-2">
                  <FeatherIcon name="user" class="h-4 w-4 shrink-0 self-center text-on-surface-variant" />
                  <span class="marq min-w-0 flex-1 text-[13px] font-semibold text-on-surface"><span class="marq-in">{{ entry.value }}</span></span>
                </div>
              </button>
            </template>
          </div>
        </section>

        <p v-if="!recentTracks.length && !settings.searchHistory.length" class="py-8 text-center text-body-md text-on-surface-variant">
          Type to search your library — artists, albums and tracks.
        </p>
      </template>

      <template v-else>
        <!-- Artists -->
        <section v-if="artistHits.length" class="pb-4">
          <div class="flex items-center gap-1.5 py-1 text-label-lg text-on-surface-variant">
            <FeatherIcon name="user" class="h-4 w-4" />
            <span>Artists</span>
          </div>
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            <button
              v-for="hit in artistHits"
              :key="hit.artist"
              type="button"
              class="flex items-center gap-3 rounded-xl bg-surface-container/70 px-3 py-2.5 text-left ring-1 ring-outline/10 transition-colors hover:bg-surface-container"
              @click="onArtistClick(hit.artist)"
            >
              <span class="h-10 w-10 shrink-0 overflow-hidden rounded-lg bg-surface-variant">
                <ArtistMosaic :covers="coversFor(hit.artist)" :fallback="hit.artist" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-body-md font-semibold text-on-surface">{{ hit.artist }}</span>
                <span class="block text-body-sm text-on-surface-variant">{{ hit.count }} tracks</span>
              </span>
            </button>
          </div>
        </section>

        <!-- Albums -->
        <section v-if="albumHits.length" class="pb-4">
          <div class="flex items-center gap-1.5 py-1 text-label-lg text-on-surface-variant">
            <FeatherIcon name="layers" class="h-4 w-4" />
            <span>Albums</span>
          </div>
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
            <AlbumGridCard
              v-for="album in albumHits"
              :style-override="searchCardStyle"
              :key="album.key"
              :album="album"
              @open-album="onOpenAlbum"
              @album-context-menu="() => {}"
            />
          </div>
        </section>

        <!-- Tracks: compact rows, like the playlist view -->
        <section v-if="trackHits.length" class="pb-4">
          <div class="flex items-center gap-1.5 py-1 text-label-lg text-on-surface-variant">
            <FeatherIcon name="music" class="h-4 w-4" />
            <span>Tracks</span>
          </div>
          <div @click="onResultsClick">
            <button
              v-for="track in trackHits"
              :key="track.id"
              :data-track-id="track.id"
              type="button"
              class="flex w-full items-center gap-2.5 border-b border-outline/10 px-2 py-2 text-left transition-colors last:border-b-0 hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]"
              @click="playTrackHit(track)"
            >
              <TrackAlbumArt :path="track.path" size="small" class="!h-9 !w-9 shrink-0 rounded-md" />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[13.5px] text-on-surface">{{ track.title ?? "Unknown Title" }}</span>
                <span class="block truncate text-xs text-on-surface-variant">
                  {{ track.artist ?? track.album_artist ?? "" }}<template v-if="track.album"> · {{ track.album }}</template>
                </span>
              </span>
              <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
              <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDur(track.duration_secs ?? 0) }}</span>
            </button>
          </div>
        </section>

        <p v-if="!matchingTracks.length" class="py-8 text-center text-body-md text-on-surface-variant">
          No results for “{{ searchQuery }}”.
        </p>
      </template>
    </div>
  </div>
</template>
