<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { navigate } from "../state/navigation";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "../composables/useAlbums";
import ArtistMosaic from "../components/shared/ArtistMosaic.vue";
import LibraryControls from "../components/library/LibraryControls.vue";
import { vMarquee } from "../composables/useMarquee";
import { useSettingsStore } from "../stores/settings";

const settings = useSettingsStore();
const cardStyle = computed(() => settings.cardStyle);

const store = useCatalogStore();
const { filteredTracks } = storeToRefs(store);

interface ArtistItem {
  name: string;
  albumKeys: string[];
  trackCount: number;
}

const artists = computed<ArtistItem[]>(() => {
  const map = new Map<string, { albumKeys: Set<string>; trackCount: number; minYear: number }>();
  for (const t of filteredTracks.value) {
    const name = (t.album_artist ?? t.artist ?? "").trim() || "Unknown Artist";
    let entry = map.get(name);
    if (!entry) {
      entry = { albumKeys: new Set(), trackCount: 0, minYear: Infinity };
      map.set(name, entry);
    }
    entry.albumKeys.add(albumIdentityKey(t.album, albumArtistOf(t)));
    entry.trackCount += 1;
    if (t.year != null) entry.minYear = Math.min(entry.minYear, t.year);
  }
  const by = settings.albumGridSortBy;
  return [...map.entries()]
    .map(([name, v]) => ({ name, albumKeys: [...v.albumKeys], trackCount: v.trackCount, minYear: v.minYear }))
    .sort((a, b) => {
      if (by === "year") return (a.minYear === Infinity ? 9999 : a.minYear) - (b.minYear === Infinity ? 9999 : b.minYear) || a.name.localeCompare(b.name);
      return a.name.localeCompare(b.name); // "album" and "artist" both sort artists by name
    });
});

/** Up to 4 album cover URLs for the mosaic. */
function mosaicCovers(artist: ArtistItem): string[] {
  const urls: string[] = [];
  for (const key of artist.albumKeys) {
    const t = filteredTracks.value.find(
      (x) => albumIdentityKey(x.album, albumArtistOf(x)) === key && x.has_cover,
    );
    if (t) {
      const url = store.getCoverDataUrl(t.path);
      if (url) urls.push(url);
    }
    if (urls.length === 4) break;
  }
  return urls;
}

function openArtist(name: string) {
  navigate({ name: "artist", key: name });
}
</script>

<template>
  <div class="app-main-scroll">
    <div class="view-pad">
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <h1 class="view-title">Artists</h1>
        <span class="view-subtitle">{{ artists.length }} artists</span>
        <LibraryControls />
      </div>
      <div class="shelf-grid">
        <button
          v-for="artist in artists"
          :key="artist.name"
          type="button"
          :class="cardStyle === 'overlay' ? 'group relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10' : 'album-card'"
          @click="openArtist(artist.name)"
        >
          <div :class="cardStyle === 'overlay' ? 'absolute inset-0' : 'mb-2 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant'">
            <ArtistMosaic :covers="mosaicCovers(artist)" :fallback="artist.name" />
            <template v-if="cardStyle === 'overlay'">
              <div class="pointer-events-none absolute inset-x-0 bottom-0 h-[64px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]" aria-hidden="true" />
              <div class="absolute inset-x-0 bottom-0 flex h-[64px] items-end px-2.5 pb-2">
                <div class="min-w-0 flex-1">
                  <div v-marquee class="marq text-[13px] font-semibold text-white"><span class="marq-in">{{ artist.name }}</span></div>
                  <div v-marquee class="marq text-[11.5px] text-white/70"><span class="marq-in">{{ artist.albumKeys.length }} albums · {{ artist.trackCount }} tracks</span></div>
                </div>
              </div>
            </template>
          </div>
          <template v-if="cardStyle === 'below'">
            <div class="marq text-[13px] font-semibold text-on-surface">
              <span class="marq-in">{{ artist.name }}</span>
            </div>
            <div class="text-[11.5px] text-on-surface-variant">
              {{ artist.albumKeys.length }} albums · {{ artist.trackCount }} tracks
            </div>
          </template>
        </button>
      </div>
    </div>
  </div>
</template>
