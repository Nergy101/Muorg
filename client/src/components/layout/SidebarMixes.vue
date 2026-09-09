<script setup lang="ts">
/**
 * The Mixes tab: eight genre-driven, throwaway playlists rebuilt each session.
 *
 * The generator is `@shared/composables/useMixes`, the same one the web client
 * uses — this component only wires it to the desktop catalog store and to the
 * existing playlist-filter machinery. Clicking a mix filters the table the way
 * selecting a playlist does; Play starts it; Save turns it into a real
 * server-side playlist.
 */
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { useMixes, type Mix } from "@shared/composables/useMixes";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { activePlaylistId, loading, loadingMore } = storeToRefs(store);

const { mixes, refresh } = useMixes({
  tracks: () => store.tracks,
  ready: () => !store.loading && !store.loadingMore,
});

/**
 * Mixes are not server rows, so they borrow the playlist filter with negative
 * ids — real playlist ids are always positive, so the two can never collide.
 */
function filterId(mix: Mix): number {
  return -mix.id;
}

const saving = ref<number | null>(null);

function openMix(mix: Mix) {
  store.setActivePlaylist(
    filterId(mix),
    mix.trackIds.map((trackId) => ({ entryId: -1, trackId })),
    `${mix.emoji} ${mix.name}`,
  );
}

function playMix(mix: Mix) {
  if (mix.trackIds.length === 0) return;
  openMix(mix);
  settingsStore.setShuffle(false);
  store.clearSelection();
  store.toggleSelection(mix.trackIds[0]);
  store.setPlayRequestTrackId(mix.trackIds[0]);
}

/** Turn a throwaway mix into a real playlist, tracks and emoji included. */
async function saveMix(mix: Mix) {
  if (mix.trackIds.length === 0 || saving.value != null) return;
  saving.value = mix.id;
  try {
    const existing = new Set(playlistStore.playlists.map((p) => p.name));
    let name = mix.name;
    for (let n = 2; existing.has(name); n++) name = `${mix.name} ${n}`;
    const playlist = await playlistStore.createPlaylistFromTracks(name, mix.trackIds);
    if (playlist?.id != null) {
      await playlistStore.setPlaylistIcon(playlist.id, mix.emoji);
    }
  } finally {
    saving.value = null;
  }
}

const isBuilding = computed(() => loading.value || loadingMore.value);
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="flex items-center justify-between px-1">
      <span class="text-xs font-semibold uppercase tracking-wide text-stone-400">
        Mixes
      </span>
      <button
        type="button"
        class="inline-flex h-6 w-6 items-center justify-center rounded text-stone-400 hover:bg-stone-700 hover:text-stone-200 disabled:opacity-40"
        :disabled="isBuilding"
        aria-label="Shuffle the mix lineup"
        @click="refresh()"
      >
        <FeatherIcon name="refresh-cw" class="h-3.5 w-3.5" />
      </button>
    </div>

    <p v-if="isBuilding" class="px-1 text-xs text-stone-500">
      Waiting for the library to finish loading…
    </p>

    <p v-else-if="mixes.length === 0" class="px-1 text-xs text-stone-500">
      No mixes yet — mixes are built from genre tags, so tag some tracks first.
    </p>

    <ul v-else class="flex flex-col gap-0.5">
      <li v-for="mix in mixes" :key="mix.id">
        <div
          class="group flex items-center gap-2 rounded px-2 py-1.5 text-sm"
          :class="activePlaylistId === filterId(mix)
            ? 'bg-stone-700 text-stone-100'
            : 'text-stone-300 hover:bg-stone-700/60'"
        >
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-2 text-left"
            :disabled="mix.trackIds.length === 0"
            @click="openMix(mix)"
          >
            <span class="shrink-0 text-base leading-none">{{ mix.emoji }}</span>
            <span class="min-w-0 flex-1 truncate">{{ mix.name }}</span>
            <span class="shrink-0 text-xs tabular-nums text-stone-500">
              {{ mix.trackIds.length }}
            </span>
          </button>

          <div class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
            <button
              type="button"
              class="inline-flex h-5 w-5 items-center justify-center rounded text-stone-400 hover:bg-stone-600 hover:text-stone-100 disabled:opacity-40"
              :disabled="mix.trackIds.length === 0"
              :aria-label="`Play ${mix.name}`"
              @click.stop="playMix(mix)"
            >
              <FeatherIcon name="play" class="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              class="inline-flex h-5 w-5 items-center justify-center rounded text-stone-400 hover:bg-stone-600 hover:text-stone-100 disabled:opacity-40"
              :disabled="mix.trackIds.length === 0 || saving != null"
              :aria-label="`Save ${mix.name} as a playlist`"
              @click.stop="saveMix(mix)"
            >
              <FeatherIcon name="save" class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
