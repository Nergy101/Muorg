<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import TrackAlbumArt from "../components/shared/TrackAlbumArt.vue";
import { useCatalogStore } from "../stores/catalog";
import { activeMix } from "../state/navigation";
import type { CatalogTrack } from "../types";

const store = useCatalogStore();
const { tracks, currentPlayingTrackId } = storeToRefs(store);

const mix = computed(() => activeMix.value);
const mixTracks = computed<CatalogTrack[]>(() => {
  if (!mix.value) return [];
  const byId = new Map(tracks.value.map((t) => [t.id, t]));
  return mix.value.trackIds.map((id) => byId.get(id)).filter((t): t is CatalogTrack => Boolean(t));
});
const coverUrls = computed(() =>
  (mix.value?.coverPaths ?? []).slice(0, 4).map((p) => store.getCoverDataUrl(p)).filter((u): u is string => Boolean(u)),
);

function playAll() {
  if (!mix.value || !mixTracks.value.length) return;
  store.queueTrackIds = [...mix.value.trackIds];
  store.setSelection([mix.value.trackIds[0]]);
  store.setPlayRequestTrackId(mix.value.trackIds[0]);
}

function playTrack(track: CatalogTrack) {
  if (!mix.value) return;
  store.queueTrackIds = [...mix.value.trackIds];
  store.setSelection([track.id]);
  store.setPlayRequestTrackId(track.id);
}

function fmtDur(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = Math.round(secs % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div v-if="mix" class="flex shrink-0 items-center gap-4 px-5 pb-3 pt-4">
      <!-- 4-cover collage -->
      <div class="mosaic-stitched grid h-14 w-14 shrink-0 grid-cols-2 grid-rows-2 overflow-hidden rounded-xl bg-surface-variant" style="gap: 0">
        <template v-for="(u, i) in coverUrls" :key="i">
          <img :src="u" alt="" class="h-full w-full object-cover" draggable="false" />
        </template>
        <div v-if="!coverUrls.length" class="col-span-2 row-span-2 flex h-full w-full items-center justify-center text-lg font-extrabold text-on-surface-variant">
          {{ mix.name.slice(0, 2).toUpperCase() }}
        </div>
      </div>
      <div class="min-w-0 flex-1">
        <h1 class="view-title truncate">{{ mix.name }}</h1>
        <div class="view-subtitle">{{ mixTracks.length }} tracks · {{ mix.artists }}</div>
      </div>
      <button type="button" class="btn-primary" :disabled="!mixTracks.length" @click="playAll">
        <FeatherIcon name="play" class="ml-0.5 h-4 w-4" /> Play
      </button>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-3 pb-24">
      <button
        v-for="(track, i) in mixTracks"
        :key="track.id"
        type="button"
        class="flex w-full items-center gap-2.5 border-b border-outline/10 px-2 py-2 text-left transition-colors last:border-b-0"
        :class="currentPlayingTrackId === track.id
          ? 'bg-[color-mix(in_srgb,var(--primary)_13%,transparent)]'
          : 'hover:bg-[color-mix(in_srgb,var(--primary)_9%,transparent)]'"
        @click="playTrack(track)"
      >
        <span class="w-6 shrink-0 text-right text-xs tabular-nums text-on-surface-variant">{{ i + 1 }}</span>
        <TrackAlbumArt :path="track.path" size="small" class="!h-9 !w-9 shrink-0 rounded-md" />
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[13.5px]" :class="currentPlayingTrackId === track.id ? 'font-semibold text-on-surface' : 'text-on-surface'">
            {{ track.title ?? "Unknown Title" }}
          </span>
          <span class="block truncate text-xs text-on-surface-variant">
            {{ track.artist ?? track.album_artist ?? "" }}<template v-if="track.album"> · {{ track.album }}</template>
          </span>
        </span>
        <span class="shrink-0 text-[10px] font-bold uppercase tracking-wide text-on-surface-variant">{{ track.format }}</span>
        <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDur(track.duration_secs ?? 0) }}</span>
      </button>
      <p v-if="mix && !mixTracks.length" class="py-8 text-center text-sm text-on-surface-variant">
        This mix is empty.
      </p>
    </div>
  </div>
</template>
