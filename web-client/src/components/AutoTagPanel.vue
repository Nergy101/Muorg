<template>
  <div>
    <button type="button" :class="ROW" @click="emit('back')">
      <MageIcon name="arrow-left" class="h-5 w-5 shrink-0" />
      <span>Back</span>
    </button>
    <div :class="LABEL">LOOK UP ON MUSICBRAINZ</div>

    <div class="px-6 pb-1 pt-1">
      <p class="text-body-sm text-on-surface-variant">
        Search MusicBrainz and fill the form from a match. Nothing is saved until
        you save the edit.
      </p>
    </div>

    <div v-for="field in SEARCH_FIELDS" :key="field.key" class="px-6 py-1.5">
      <label class="mb-1 block text-body-sm text-on-surface-variant">{{ field.label }}</label>
      <input
        v-model="query[field.key]"
        type="text"
        class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
        @keyup.enter="search"
      />
    </div>

    <div class="flex justify-end px-6 pb-2 pt-3">
      <button
        type="button"
        class="rounded-full bg-primary px-5 py-2 text-label-lg text-on-primary disabled:opacity-50"
        :disabled="searching"
        @click="search"
      >
        {{ searching ? "Searching…" : "Search" }}
      </button>
    </div>

    <p v-if="error" class="px-6 py-3 text-body-md text-error">{{ error }}</p>

    <p
      v-else-if="searched && !searching && candidates.length === 0"
      class="px-6 py-6 text-center text-body-md text-on-surface-variant"
    >
      No matches. Try a broader search — artist and title alone often work best.
    </p>

    <ul v-else-if="candidates.length > 0" class="pb-2">
      <li v-for="candidate in candidates" :key="candidate.mbid + (candidate.album ?? '')">
        <button
          type="button"
          class="flex w-full items-start gap-3 px-6 py-3 text-left lg:hover:bg-on-surface/5"
          @click="emit('apply', candidate)"
        >
          <span
            class="mt-0.5 shrink-0 rounded-full px-2 py-0.5 text-label-sm tabular-nums"
            :class="confidenceClass(candidate.confidence)"
          >
            {{ Math.round(candidate.confidence * 100) }}%
          </span>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-body-lg text-on-surface">{{ candidate.title }}</span>
            <span class="block truncate text-body-sm text-on-surface-variant">
              {{ candidate.artist }}<template v-if="candidate.album"> · {{ candidate.album }}</template>
              <template v-if="candidate.year"> · {{ candidate.year }}</template>
            </span>
          </span>
        </button>
      </li>
    </ul>

    <div class="pb-6" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import { api } from "../api/client";
import { confidenceClass, rankCandidates } from "../composables/useAutoTag";
import type { CatalogTrack, MatchCandidate } from "../types";

/**
 * MusicBrainz lookup for the web app's metadata editor.
 *
 * The desktop app has had this since it could edit tags; the web app could
 * change metadata but not look anything up, so correcting a badly tagged file
 * meant typing everything by hand. The endpoint and the generated client were
 * already shared — only the UI was missing.
 *
 * Applying fills the edit form rather than writing to the server, so a wrong
 * match costs a glance rather than a save. That matches the desktop.
 */
const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

const SEARCH_FIELDS = [
  { key: "artist", label: "Artist" },
  { key: "title", label: "Title" },
  { key: "album", label: "Album" },
] as const;

type SearchKey = (typeof SEARCH_FIELDS)[number]["key"];

const props = defineProps<{ track: CatalogTrack | null }>();

const emit = defineEmits<{
  back: [];
  apply: [candidate: MatchCandidate];
}>();

const query = ref<Record<SearchKey, string>>({ artist: "", title: "", album: "" });
const candidates = ref<MatchCandidate[]>([]);
const searching = ref(false);
const searched = ref(false);
const error = ref<string | null>(null);

// Seed from the track's own tags: the common case is fixing one field of an
// otherwise-correct row, so the existing values are the best starting query.
watch(
  () => props.track,
  (track) => {
    query.value = {
      artist: track?.artist ?? "",
      title: track?.title ?? "",
      album: track?.album ?? "",
    };
    candidates.value = [];
    searched.value = false;
    error.value = null;
  },
  { immediate: true },
);

async function search(): Promise<void> {
  const track = props.track;
  if (!track || searching.value) return;
  searching.value = true;
  searched.value = true;
  error.value = null;
  try {
    candidates.value = rankCandidates(
      await api.autoTagSuggestions(track.id, {
        artist: query.value.artist || null,
        title: query.value.title || null,
        album: query.value.album || null,
        duration_secs: track.duration_secs,
      }),
    );
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    candidates.value = [];
  } finally {
    searching.value = false;
  }
}
</script>
