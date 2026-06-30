<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import * as catalogApi from "../../api/catalog";
import type { AutoTagCandidate } from "../../api/catalog";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const emit = defineEmits<{
  (e: "close"): void;
  (e: "apply", candidate: AutoTagCandidate): void;
  (e: "save-and-apply", candidate: AutoTagCandidate): void;
}>();

const store = useCatalogStore();
const { selectedTracks } = storeToRefs(store);

// Search fields
const searchArtist = ref("");
const searchTitle = ref("");
const searchAlbum = ref("");

// Results state
const candidates = ref<AutoTagCandidate[]>([]);
const searching = ref(false);
const searched = ref(false);
const error = ref<string | null>(null);
const selectedCandidate = ref<AutoTagCandidate | null>(null);

// Pre-fill from selected track
watch(
  selectedTracks,
  (tracks) => {
    if (tracks.length === 1) {
      const t = tracks[0];
      searchArtist.value = t.artist ?? "";
      searchTitle.value = t.title ?? "";
      searchAlbum.value = t.album ?? "";
    }
  },
  { immediate: true },
);

async function doSearch() {
  const track = selectedTracks.value[0];
  if (!track) return;

  searching.value = true;
  error.value = null;
  searched.value = true;
  selectedCandidate.value = null;

  try {
    const result = await catalogApi.getAutoTagSuggestions(track.id, {
      artist: searchArtist.value || undefined,
      title: searchTitle.value || undefined,
      album: searchAlbum.value || undefined,
    });
    candidates.value = result.candidates;
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    candidates.value = [];
  } finally {
    searching.value = false;
  }
}

function selectCandidate(c: AutoTagCandidate) {
  selectedCandidate.value =
    selectedCandidate.value?.mbid === c.mbid && selectedCandidate.value?.album === c.album
      ? null
      : c;
}

function applyToForm() {
  if (!selectedCandidate.value) return;
  emit("apply", selectedCandidate.value);
  emit("close");
}

function saveAndApply() {
  if (!selectedCandidate.value) return;
  emit("save-and-apply", selectedCandidate.value);
  emit("close");
}

function confidenceColor(conf: number): string {
  if (conf >= 0.8) return "text-emerald-400";
  if (conf >= 0.5) return "text-amber-400";
  return "text-red-400";
}

function confidenceBg(conf: number): string {
  if (conf >= 0.8) return "bg-emerald-900/40 border-emerald-700";
  if (conf >= 0.5) return "bg-amber-900/40 border-amber-700";
  return "bg-red-900/40 border-red-700";
}

function confidenceLabel(conf: number): string {
  if (conf >= 0.8) return "High";
  if (conf >= 0.5) return "Medium";
  return "Low";
}

// Get the current track's field value for diff comparison
const currentTrack = computed(() => selectedTracks.value[0] ?? null);

function diffClass(current: string | number | null | undefined, suggested: string | number | null | undefined): string {
  if (current == null || current === "" || current === undefined) {
    if (suggested != null && suggested !== "") return "bg-emerald-900/30"; // new data
    return "";
  }
  if (String(current) !== String(suggested)) return "bg-amber-900/30"; // differs
  return ""; // unchanged
}

onMounted(() => {
  document.addEventListener("keydown", onKeydown);
});
onUnmounted(() => {
  document.removeEventListener("keydown", onKeydown);
});

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") emit("close");
}
</script>

<template>
  <div
    class="fixed inset-0 z-[400] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
    @click.self="emit('close')"
  >
    <div
      class="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-lg border border-stone-700 bg-stone-900 shadow-2xl"
    >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
        <h2 class="flex items-center gap-2 text-sm font-semibold text-stone-200">
          <FeatherIcon name="search" class="h-4 w-4" />
          Find MusicBrainz matches
        </h2>
        <button
          type="button"
          class="rounded p-1 text-stone-500 hover:bg-stone-700 hover:text-stone-300"
          @click="emit('close')"
          title="Close"
        >
          <FeatherIcon name="x" class="h-4 w-4" />
        </button>
      </div>

      <!-- Search form -->
      <div class="border-b border-stone-700 px-4 py-3">
        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="block text-xs text-stone-500">Artist</label>
            <input
              v-model="searchArtist"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200"
              placeholder="Artist name"
              @keydown.enter="doSearch"
            />
          </div>
          <div>
            <label class="block text-xs text-stone-500">Title</label>
            <input
              v-model="searchTitle"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200"
              placeholder="Track title"
              @keydown.enter="doSearch"
            />
          </div>
          <div>
            <label class="block text-xs text-stone-500">Album</label>
            <input
              v-model="searchAlbum"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200"
              placeholder="Album (optional)"
              @keydown.enter="doSearch"
            />
          </div>
        </div>
        <button
          type="button"
          class="accent-btn mt-2 inline-flex items-center gap-1.5 rounded border border-stone-600 px-3 py-1.5 text-sm text-white hover:opacity-90 disabled:opacity-50"
          style="background-color: #5b7c32"
          :disabled="searching || (!searchArtist && !searchTitle)"
          @click="doSearch"
        >
          <FeatherIcon name="search" class="h-4 w-4 shrink-0" />
          {{ searching ? "Searching…" : "Search MusicBrainz" }}
        </button>
      </div>

      <!-- Results -->
      <div class="flex-1 overflow-y-auto px-4 py-3">
        <!-- Loading -->
        <div v-if="searching" class="flex items-center justify-center py-8 text-stone-400">
          <FeatherIcon name="loader" class="mr-2 h-5 w-5 animate-spin" />
          Searching MusicBrainz…
        </div>

        <!-- Error -->
        <div
          v-else-if="error"
          class="rounded border border-red-700 bg-red-900/30 px-3 py-2 text-sm text-red-300"
        >
          {{ error }}
        </div>

        <!-- Empty state -->
        <div
          v-else-if="searched && candidates.length === 0"
          class="py-8 text-center text-sm text-stone-500"
        >
          No matches found. Try different search terms.
        </div>

        <!-- Candidate list -->
        <div v-else-if="candidates.length > 0" class="space-y-2">
          <p class="text-xs text-stone-500">
            {{ candidates.length }} result{{ candidates.length === 1 ? "" : "s" }} — click a row to preview
          </p>
          <div
            v-for="c in candidates"
            :key="c.mbid + (c.album ?? '')"
            class="cursor-pointer rounded border px-3 py-2 text-sm transition-colors"
            :class="[
              selectedCandidate?.mbid === c.mbid && selectedCandidate?.album === c.album
                ? 'border-[#5b7c32] bg-stone-800'
                : 'border-stone-700 hover:bg-stone-800',
            ]"
            @click="selectCandidate(c)"
          >
            <div class="flex items-center gap-2">
              <span
                class="inline-flex items-center rounded-full border px-1.5 py-0.5 text-[10px] font-medium"
                :class="confidenceBg(c.confidence)"
              >
                <span :class="confidenceColor(c.confidence)">{{ confidenceLabel(c.confidence) }}</span>
                <span class="ml-0.5 text-stone-400">({{ (c.confidence * 100).toFixed(0) }}%)</span>
              </span>
              <span class="font-medium text-stone-200">{{ c.title }}</span>
              <span class="text-stone-500">·</span>
              <span class="text-stone-400">{{ c.artist }}</span>
              <span v-if="c.year" class="text-stone-500">· {{ c.year }}</span>
            </div>
            <div v-if="c.album || c.track_number != null" class="mt-0.5 text-xs text-stone-500">
              <span v-if="c.album">Album: {{ c.album }}</span>
              <span v-if="c.track_number != null"> · Track {{ c.track_number }}</span>
            </div>
          </div>
        </div>

        <!-- Side-by-side diff for selected candidate -->
        <div
          v-if="selectedCandidate"
          class="mt-4 rounded border border-stone-700 bg-stone-850 p-3"
        >
          <h3 class="mb-2 text-xs font-semibold text-stone-400">Preview — vs current tags</h3>
          <table class="w-full text-xs">
            <thead>
              <tr class="border-b border-stone-700 text-left text-stone-500">
                <th class="pb-1 pr-4 font-medium">Field</th>
                <th class="pb-1 pr-4 font-medium">Current</th>
                <th class="pb-1 font-medium">Suggested</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in [
                  { label: 'Title', cur: currentTrack?.title ?? '', sug: selectedCandidate.title },
                  { label: 'Artist', cur: currentTrack?.artist ?? '', sug: selectedCandidate.artist },
                  { label: 'Album', cur: currentTrack?.album ?? '', sug: selectedCandidate.album ?? '' },
                  { label: 'Album Artist', cur: currentTrack?.album_artist ?? '', sug: selectedCandidate.album_artist ?? '' },
                  { label: 'Year', cur: currentTrack?.year ?? '', sug: selectedCandidate.year ?? '' },
                  { label: 'Track #', cur: currentTrack?.track_number ?? '', sug: selectedCandidate.track_number ?? '' },
                ]"
                :key="row.label"
                class="border-b border-stone-700/50"
                :class="diffClass(row.cur, row.sug)"
              >
                <td class="py-1 pr-4 text-stone-400">{{ row.label }}</td>
                <td class="py-1 pr-4 text-stone-300">{{ row.cur || '—' }}</td>
                <td class="py-1 text-stone-200">{{ row.sug || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Footer actions -->
      <div class="flex flex-col gap-2 border-t border-stone-700 px-4 py-3">
        <div class="flex items-center justify-end gap-2">
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="emit('close')"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-300 hover:bg-stone-600 disabled:opacity-30 disabled:pointer-events-none"
            :disabled="!selectedCandidate"
            @click="applyToForm"
          >
            Apply to form
          </button>
          <button
            type="button"
            class="accent-btn rounded border px-3 py-1.5 text-xs text-white hover:opacity-90 disabled:opacity-30 disabled:pointer-events-none"
            style="background-color: #5b7c32"
            :disabled="!selectedCandidate"
            @click="saveAndApply"
          >
            Save &amp; apply
          </button>
        </div>
        <p class="text-[10px] leading-tight text-stone-500 text-right">
          Data from <a href="https://musicbrainz.org" target="_blank" rel="noopener noreferrer" class="underline hover:text-stone-300">MusicBrainz</a>
          — licensed under CC BY-NC-SA 3.0
        </p>
      </div>
    </div>
  </div>
</template>
