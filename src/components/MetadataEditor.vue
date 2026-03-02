<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import type { MetadataUpdate } from "../types";

const store = useCatalogStore();
const { selectedTracks } = storeToRefs(store);

const title = ref("");
const artist = ref("");
const album = ref("");
const albumArtist = ref("");
const year = ref<number | "">("");
const genre = ref("");
const trackNumber = ref<number | "">("");
const discNumber = ref<number | "">("");
const pictureBase64 = ref<string | null>(null);
const saving = ref(false);
const saveError = ref<string | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const showCoverPopup = ref(false);
const coverPopupRef = ref<HTMLDivElement | null>(null);
const coverDimensions = ref<{ width: number; height: number } | null>(null);
const coverSizeBytes = ref<number | null>(null);
const largeImageWarning = ref(false);

const ONE_MB = 1024 * 1024;

function onCoverPopupKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") showCoverPopup.value = false;
}

function formatSize(bytes: number): string {
  if (bytes >= ONE_MB) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(0)} KB`;
}

function loadCoverMeta(base64: string, sizeBytes?: number) {
  coverDimensions.value = null;
  coverSizeBytes.value = sizeBytes ?? Math.round((base64.length * 3) / 4);
  const img = new Image();
  img.onload = () => {
    coverDimensions.value = { width: img.naturalWidth, height: img.naturalHeight };
  };
  img.src = `data:image/jpeg;base64,${base64}`;
}

/** Cover to display: new selection or current track(s) art from store. */
const displayCover = computed(() => {
  if (pictureBase64.value) return pictureBase64.value;
  const tracks = selectedTracks.value;
  if (tracks.length > 0) return store.getCover(tracks[0].path) ?? null;
  return null;
});

/** If all items have the same value for key(), return it; otherwise return "". */
function same<T, V>(arr: T[], key: (t: T) => V | null | undefined): V | "" {
  if (arr.length === 0) return "" as V;
  const first = key(arr[0]);
  if (arr.every((t) => key(t) === first)) return first ?? ("" as V);
  return "" as V;
}

function syncFromTracks() {
  const tracks = selectedTracks.value;
  if (tracks.length === 0) return;
  if (tracks.length === 1) {
    const t = tracks[0];
    title.value = t.title ?? "";
    artist.value = t.artist ?? "";
    album.value = t.album ?? "";
    albumArtist.value = t.album_artist ?? "";
    year.value = t.year ?? "";
    genre.value = t.genre ?? "";
    trackNumber.value = t.track_number ?? "";
    discNumber.value = t.disc_number ?? "";
    pictureBase64.value = null;
    store.fetchCover(t.path);
  } else {
    title.value = "";
    artist.value = same(tracks, (t) => t.artist) || "";
    album.value = same(tracks, (t) => t.album) || "";
    albumArtist.value = same(tracks, (t) => t.album_artist) || "";
    year.value = same(tracks, (t) => t.year) ?? "";
    genre.value = same(tracks, (t) => t.genre) || "";
    const tn = same(tracks, (t) => t.track_number);
    trackNumber.value = tn === "" || tn == null ? "" : tn;
    const dn = same(tracks, (t) => t.disc_number);
    discNumber.value = dn === "" || dn == null ? "" : dn;
    pictureBase64.value = null;
    if (tracks.length > 0) store.fetchCover(tracks[0].path);
  }
}

watch(selectedTracks, syncFromTracks, { immediate: true });

watch(displayCover, (base64) => {
  if (!base64) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    largeImageWarning.value = false;
    return;
  }
  loadCoverMeta(base64);
});

watch(showCoverPopup, async (open) => {
  if (open) {
    await nextTick();
    coverPopupRef.value?.focus();
    document.addEventListener("keydown", onCoverPopupKeydown);
  } else {
    document.removeEventListener("keydown", onCoverPopupKeydown);
  }
});

onUnmounted(() => {
  document.removeEventListener("keydown", onCoverPopupKeydown);
});

function buildUpdate(): MetadataUpdate {
  return {
    title: title.value || undefined,
    artist: artist.value || undefined,
    album: album.value || undefined,
    album_artist: albumArtist.value || undefined,
    year: year.value === "" ? undefined : Number(year.value),
    genre: genre.value || undefined,
    track_number: trackNumber.value === "" ? undefined : Number(trackNumber.value),
    disc_number: discNumber.value === "" ? undefined : Number(discNumber.value),
    picture_base64: pictureBase64.value ?? undefined,
  };
}

async function save() {
  const tracks = selectedTracks.value;
  if (!tracks.length) return;
  saving.value = true;
  saveError.value = null;
  try {
    const update = buildUpdate();
    for (const t of tracks) {
      await store.writeMetadata(t.path, update);
    }
    await store.loadTracks();
    store.clearSelection();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}

function clearCover() {
  pictureBase64.value = null;
  largeImageWarning.value = false;
}

function onCoverFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !file.type.startsWith("image/")) return;
  if (file.size > ONE_MB) largeImageWarning.value = true;
  const reader = new FileReader();
  reader.onload = () => {
    const data = reader.result as string;
    const base64 = data.includes(",") ? data.split(",")[1] : data;
    pictureBase64.value = base64 ?? null;
    loadCoverMeta(base64 ?? "", file.size);
  };
  reader.readAsDataURL(file);
  input.value = "";
}
</script>

<template>
  <div
    v-if="selectedTracks.length"
    class="border-t border-stone-700 bg-stone-800/90 p-4"
  >
    <h3 class="mb-1 text-sm font-medium text-stone-300">
      Edit metadata ({{ selectedTracks.length }} selected)
    </h3>
    <p v-if="selectedTracks.length > 1" class="mb-3 text-xs text-stone-500">
      Set these fields for all selected tracks. Shared values are pre-filled; change only what you want to update.
    </p>
    <div class="flex flex-col gap-4 sm:flex-row sm:items-start">
      <div class="min-w-0 flex-1">
        <div class="grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
          <div>
            <label class="block text-stone-500">Title</label>
            <input
              v-model="title"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Artist</label>
            <input
              v-model="artist"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Album</label>
            <input
              v-model="album"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Album artist</label>
            <input
              v-model="albumArtist"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Year</label>
            <input
              v-model.number="year"
              type="number"
              min="1"
              max="9999"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Genre</label>
            <input
              v-model="genre"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Track #</label>
            <input
              v-model.number="trackNumber"
              type="number"
              min="0"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
          <div>
            <label class="block text-stone-500">Disc #</label>
            <input
              v-model.number="discNumber"
              type="number"
              min="0"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-stone-200"
            />
          </div>
        </div>
        <div class="mt-3 flex items-center gap-2">
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-sm text-white hover:opacity-90"
            style="background-color: #5b7c32"
            :disabled="saving"
            @click="save"
          >
            {{ saving ? "Saving…" : "Save to file(s)" }}
          </button>
          <button
            type="button"
            class="rounded border border-stone-600 px-2 py-1 text-xs text-stone-400 hover:bg-stone-600"
            @click="store.clearSelection()"
          >
            Cancel
          </button>
        </div>
      </div>
      <div class="shrink-0 border-t border-stone-700 pt-4 sm:border-l sm:border-t-0 sm:pl-4 sm:pt-0">
        <label class="block text-stone-500">Album cover</label>
        <div class="mt-1 flex flex-col items-start gap-2">
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="onCoverFile"
          />
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-300 hover:bg-stone-600"
              @click="fileInputRef?.click()"
            >
              Choose image
            </button>
            <button
              v-if="pictureBase64"
              type="button"
              class="rounded border border-stone-600 px-2 py-1 text-xs text-stone-400 hover:bg-stone-600"
              @click="clearCover"
            >
              Clear
            </button>
          </div>
          <div
            v-if="displayCover"
            class="flex flex-col items-start gap-1.5"
          >
            <div
              class="group relative inline-block cursor-pointer"
              role="button"
              tabindex="0"
              @click="showCoverPopup = true"
              @keydown.enter="showCoverPopup = true"
              @keydown.space.prevent="showCoverPopup = true"
            >
              <img
                :src="`data:image/jpeg;base64,${displayCover}`"
                alt="Album cover"
                class="h-40 w-40 rounded object-cover border border-stone-600 shadow-md"
              />
              <div
                class="magnify absolute inset-0 flex items-center justify-center rounded bg-stone-900/60 opacity-0 transition-opacity group-hover:opacity-100"
                aria-hidden="true"
              >
                <svg class="h-10 w-10 text-stone-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
                </svg>
              </div>
            </div>
            <p
              v-if="coverDimensions || coverSizeBytes != null"
              class="text-xs text-stone-500"
            >
              <span v-if="coverDimensions">{{ coverDimensions.width }}×{{ coverDimensions.height }} px</span>
              <span v-if="coverDimensions && coverSizeBytes != null"> · </span>
              <span v-if="coverSizeBytes != null">{{ formatSize(coverSizeBytes) }}</span>
            </p>
            <p
              v-if="largeImageWarning"
              class="max-w-[200px] text-xs text-amber-400"
            >
              This image is over 1 MB. Embedding large artwork can degrade performance in media players and in Muorg, as it is stored inside each file.
            </p>
          </div>
        </div>
      </div>
    </div>
    <p v-if="saveError" class="mt-2 text-xs text-red-400">{{ saveError }}</p>

    <!-- Cover art popup -->
    <Teleport to="body">
      <div
        v-if="showCoverPopup && displayCover"
        ref="coverPopupRef"
        tabindex="-1"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-stone-950/80 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-label="Album cover"
        @click.self="showCoverPopup = false"
      >
        <img
          :src="`data:image/jpeg;base64,${displayCover}`"
          alt="Album cover (enlarged)"
          class="max-h-[90vh] max-w-[90vw] rounded-lg shadow-xl object-contain"
          @click="showCoverPopup = false"
        />
      </div>
    </Teleport>
  </div>
</template>
