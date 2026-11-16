<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-5 w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">{{ artistName }}</h1>
        <p class="truncate text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>

      <template v-if="playOrder.length > 0">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          aria-label="Shuffle artist"
          @click="shuffleAll"
        >
          <MageIcon name="exchange" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="mr-2 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-on-primary transition-transform hover:scale-105"
          aria-label="Play artist"
          @click="playAll"
        >
          <MageIcon name="play" class="h-5 w-5" />
        </button>
      </template>
    </div>

    <div
      v-if="lib.loading && lib.tracks.length === 0"
      class="content-col flex min-h-0 flex-1 items-center justify-center"
    >
      <MageIcon name="refresh" class="h-6 w-6 animate-spin text-primary" />
    </div>

    <template v-else-if="disco.tracks.length === 0">
      <div class="content-col flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
        <p class="text-body-md text-on-surface-variant">No tracks by this artist</p>
        <button
          type="button"
          class="text-label-lg text-primary"
          @click="router.push({ name: 'library' })"
        >Back to library</button>
      </div>
    </template>

    <template v-else>
      <!-- Not content-col on the scroller: capping it drags the scrollbar
           inwards on desktop. Every section below keeps the same 900px
           measure, so the album covers line up with the track rows. -->
      <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
        <section
          v-for="section in releaseSections"
          :key="section.id"
          :data-section="section.id"
          class="pb-2"
        >
          <h2 class="content-col flex items-center gap-1.5 px-4 pb-2 pt-3 text-label-lg font-semibold text-on-surface">
            <MageIcon :name="section.icon" class="h-4 w-4 text-primary" />
            {{ section.title }}
            <span class="font-normal text-on-surface-variant">· {{ section.items.length }}</span>
          </h2>
          <div class="content-col grid grid-cols-2 gap-3 px-4 md:grid-cols-3 lg:grid-cols-[repeat(auto-fill,minmax(200px,1fr))]">
            <AlbumCard
              v-for="item in section.items"
              :key="item.key"
              :item="item"
              mode="grid"
              :is-active="isAlbumActive(item)"
              @open="openAlbum(item)"
              @actions="albumActions = item"
            />
          </div>
        </section>

        <section v-if="disco.looseTracks.length > 0" data-section="loose" class="pb-2">
          <h2 class="content-col flex items-center gap-1.5 px-4 pb-2 pt-3 text-label-lg font-semibold text-on-surface">
            <MageIcon name="music" class="h-4 w-4 text-primary" />
            Loose tracks
            <span class="font-normal text-on-surface-variant">· {{ disco.looseTracks.length }}</span>
          </h2>
          <div class="content-col-children">
            <TrackListRow
              v-for="track in disco.looseTracks"
              :key="track.id"
              :track="track"
              :is-playing="player.currentTrack?.id === track.id"
              :is-loading="player.pendingTrackId === track.id"
              :selectable="selection.selecting"
              :selected="selection.has(track.id)"
              @play="player.playTrack(track, disco.looseTracks)"
              @select="(mods) => onSelect(track, mods)"
              @actions="sheetTrack = track"
            />
          </div>
        </section>
      </div>
    </template>

    <AlbumActionsHost :item="albumActions" @close="albumActions = null" />

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import AlbumActionsHost from "../components/AlbumActionsHost.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSelectionStore } from "../stores/selection";
import { useScrollMemory } from "../composables/useScrollMemory";
import { provideVisibleIds } from "../composables/useSelectableList";
import { buildDiscography, creditsArtist, normalizeArtist, type Discography } from "../discography";
import type { AlbumGridItem, CatalogTrack } from "../types";

const props = defineProps<{ name: string }>();

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const selection = useSelectionStore();

const artistName = computed(() => props.name);

const disco = computed<Discography>(() => {
  const durations = new Map<number, number>();
  for (const t of lib.tracks) durations.set(t.id, t.duration_secs ?? 0);
  return buildDiscography(
    artistName.value,
    lib.tracks,
    (t) => lib.keyForTrack(t),
    (key) => lib.albumByKey(key),
    (id) => durations.get(id) ?? 0,
  );
});

interface ReleaseSection {
  id: string;
  title: string;
  icon: string;
  items: AlbumGridItem[];
}

const releaseSections = computed<ReleaseSection[]>(() =>
  [
    { id: "albums", title: "Albums", icon: "compact-disk", items: disco.value.albums },
    { id: "eps", title: "EPs", icon: "double-circle", items: disco.value.eps },
    { id: "singles", title: "Singles", icon: "music-fill", items: disco.value.singles },
    { id: "appears-on", title: "Appears on", icon: "users-fill", items: disco.value.appearsOn },
  ].filter((s) => s.items.length > 0),
);

/**
 * "Play artist": the artist's own credited tracks in discography order —
 * each release in section order, in track order within it — then the loose
 * tracks. On someone else's release only the tracks that credit the artist
 * count.
 */
const playOrder = computed<CatalogTrack[]>(() => {
  const norm = normalizeArtist(artistName.value);
  const out: CatalogTrack[] = [];
  for (const section of releaseSections.value) {
    for (const item of section.items) {
      out.push(...lib.tracksForAlbum(item.key).filter((t) => creditsArtist(t, norm)));
    }
  }
  out.push(...disco.value.looseTracks);
  return out;
});

provideVisibleIds(() => disco.value.looseTracks.map((t) => t.id));

function onSelect(track: CatalogTrack, mods: { shift: boolean; ctrl: boolean }): void {
  const consumed = selection.click(
    track.id,
    disco.value.looseTracks.map((t) => t.id),
    mods,
  );
  if (!consumed) player.playTrack(track, disco.value.looseTracks);
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

const metaLine = computed(() => {
  const d = disco.value;
  const parts: string[] = [];
  if (d.albums.length) parts.push(plural(d.albums.length, "album", "albums"));
  if (d.eps.length) parts.push(plural(d.eps.length, "EP", "EPs"));
  if (d.singles.length) parts.push(plural(d.singles.length, "single", "singles"));
  parts.push(plural(d.tracks.length, "track", "tracks"));
  return parts.join(" · ");
});

function playAll(): void {
  const tracks = playOrder.value;
  if (tracks.length > 0) void player.playTrack(tracks[0], tracks);
}

function shuffleAll(): void {
  const tracks = playOrder.value;
  if (tracks.length === 0) return;
  if (!player.shuffleEnabled) player.toggleShuffle();
  void player.playTrack(tracks[Math.floor(Math.random() * tracks.length)], tracks);
}

function isAlbumActive(item: AlbumGridItem): boolean {
  const current = player.currentTrack;
  return current != null && item.trackIds.includes(current.id);
}

function openAlbum(item: AlbumGridItem): void {
  void router.push({ name: "album", params: { albumKey: item.key } });
}

const albumActions = ref<AlbumGridItem | null>(null);
const sheetTrack = ref<CatalogTrack | null>(null);

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
</script>
