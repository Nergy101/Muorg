<script setup lang="ts">
import { ref, computed } from "vue";
import type { CatalogTrack } from "../types";
import * as api from "../api/catalog";

const props = defineProps<{ track: CatalogTrack | null }>();
const emit = defineEmits<{
  (e: "close"): void;
  (e: "saved"): void;
}>();

const saving = ref(false);
const error = ref<string | null>(null);

// Form fields
const title = ref("");
const artist = ref("");
const album = ref("");
const albumArtist = ref("");
const featuring = ref("");
const year = ref<number | "">("");
const genre = ref("");
const trackNumber = ref<number | "">("");
const discNumber = ref<number | "">("");

// Pre-fill when track changes
const trackId = computed(() => props.track?.id ?? null);
function fillForm() {
  const t = props.track;
  if (!t) return;
  title.value = t.title ?? "";
  artist.value = t.artist ?? "";
  album.value = t.album ?? "";
  albumArtist.value = t.album_artist ?? "";
  featuring.value = t.featuring ?? "";
  year.value = t.year ?? "";
  genre.value = t.genre ?? "";
  trackNumber.value = t.track_number ?? "";
  discNumber.value = t.disc_number ?? "";
}
fillForm();

async function save() {
  if (trackId.value === null) return;
  saving.value = true;
  error.value = null;
  try {
    const update: api.MetadataUpdate = {};
    if (title.value !== (props.track?.title ?? "")) update.title = title.value || null;
    if (artist.value !== (props.track?.artist ?? "")) update.artist = artist.value || null;
    if (album.value !== (props.track?.album ?? "")) update.album = album.value || null;
    if (albumArtist.value !== (props.track?.album_artist ?? "")) update.album_artist = albumArtist.value || null;
    if (featuring.value !== (props.track?.featuring ?? "")) update.featuring = featuring.value || null;
    const y = year.value; if (y !== (props.track?.year ?? "")) update.year = y === "" ? null : Number(y);
    if (genre.value !== (props.track?.genre ?? "")) update.genre = genre.value || null;
    const tn = trackNumber.value; if (tn !== (props.track?.track_number ?? "")) update.track_number = tn === "" ? null : Number(tn);
    const dn = discNumber.value; if (dn !== (props.track?.disc_number ?? "")) update.disc_number = dn === "" ? null : Number(dn);
    await api.patchMetadata(trackId.value, update, true);
    emit("saved");
    emit("close");
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" @click.self="emit('close')">
      <div class="w-full max-w-lg rounded-lg border border-stone-700 bg-stone-900 shadow-2xl">
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
          <h2 class="text-sm font-semibold text-stone-200">Edit metadata</h2>
          <button class="rounded p-1 text-stone-500 hover:bg-stone-700 hover:text-stone-300" @click="emit('close')">✕</button>
        </div>

        <!-- Form -->
        <div class="space-y-3 px-4 py-4">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs text-stone-500">Title</label>
              <input v-model="title" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Artist</label>
              <input v-model="artist" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Album</label>
              <input v-model="album" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Album Artist</label>
              <input v-model="albumArtist" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Featuring</label>
              <input v-model="featuring" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Year</label>
              <input v-model.number="year" type="number" min="0" max="9999" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Genre</label>
              <input v-model="genre" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Track #</label>
              <input v-model.number="trackNumber" type="number" min="0" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
            <div>
              <label class="block text-xs text-stone-500">Disc #</label>
              <input v-model.number="discNumber" type="number" min="0" class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-sm text-stone-200" />
            </div>
          </div>

          <div v-if="error" class="rounded border border-red-700 bg-red-900/30 px-3 py-2 text-sm text-red-300">{{ error }}</div>
        </div>

        <!-- Actions -->
        <div class="flex items-center justify-end gap-2 border-t border-stone-700 px-4 py-3">
          <button class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-700 hover:text-stone-200" @click="emit('close')">Cancel</button>
          <button class="rounded bg-[#5b7c32] px-3 py-1.5 text-xs text-white hover:opacity-90 disabled:opacity-50" :disabled="saving || trackId === null" @click="save">
            {{ saving ? "Saving…" : "Save" }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
