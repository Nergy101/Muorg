<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <template v-if="!track">
      <div class="content-col flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
        <div class="w-40 shrink-0 sm:w-48">
          <OrcIllustration variant="not-found" />
        </div>
        <p class="text-body-md text-on-surface-variant">Track not found</p>
        <button
          type="button"
          class="text-label-lg text-primary"
          @click="router.push({ name: 'library' })"
        >Back to library</button>
      </div>
    </template>

    <template v-else>
      <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
          aria-label="Back"
          @click="router.back()"
        >
          <MageIcon name="chevron-left" class="h-5 w-5" />
          <span class="hidden lg:inline">Back</span>
        </button>
        <!-- Track's cover art, right of the back button, so the form is contextualized. -->
        <div class="h-10 w-10 shrink-0 overflow-hidden rounded-md bg-surface-variant">
          <img
            v-if="coverUrl"
            :src="coverUrl"
            :alt="track.album ?? ''"
            class="h-full w-full object-cover"
            draggable="false"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-title-md text-on-surface">Edit track</h1>
          <p class="truncate text-body-sm text-on-surface-variant">{{ track.title ?? "(untitled)" }}</p>
        </div>
      </div>

      <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
        <div class="px-4 py-3">
          <div class="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
            <div v-for="key in TEXT_FIELD_KEYS" :key="key" class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">{{ FIELD_LABELS[key] }}</label>
              <div class="relative">
                <input
                  v-model="form[key]"
                  type="text"
                  class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
                  :class="form[key] ? 'pr-9' : ''"
                  @input="markEdited(key)"
                />
                <button
                  v-if="form[key]"
                  type="button"
                  tabindex="-1"
                  class="absolute inset-y-0 right-2 my-auto h-fit p-1 text-on-surface-variant"
                  :aria-label="`Clear ${FIELD_LABELS[key]}`"
                  @click="clearField(key)"
                >
                  <MageIcon name="multiply" class="h-4 w-4" />
                </button>
              </div>
            </div>
            <GenreCombobox v-model="form.genre" @edited="markEdited('genre')" @clear="clearField('genre')" />
            <div v-for="key in NUMERIC_FIELD_KEYS" :key="key" class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">{{ FIELD_LABELS[key] }}</label>
              <div class="relative">
                <input
                  v-model="form[key]"
                  type="text"
                  inputmode="numeric"
                  pattern="[0-9]*"
                  class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
                  :class="form[key] ? 'pr-9' : ''"
                  @input="markEdited(key)"
                />
                <button
                  v-if="form[key]"
                  type="button"
                  tabindex="-1"
                  class="absolute inset-y-0 right-2 my-auto h-fit p-1 text-on-surface-variant"
                  :aria-label="`Clear ${FIELD_LABELS[key]}`"
                  @click="clearField(key)"
                >
                  <MageIcon name="multiply" class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2 pt-3">
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-label-lg text-on-primary disabled:opacity-50"
              :disabled="saving"
              @click="save"
            >
              <MageIcon name="save-floppy" class="h-4 w-4" />
              {{ saving ? "Saving…" : "Save" }}
            </button>
            <button type="button" class="text-label-lg text-on-surface-variant" @click="router.back()">Cancel</button>
            <span
              class="text-body-sm"
              :class="settings.backupBeforeWrite ? 'text-primary' : 'text-on-surface-variant'"
            >{{ settings.backupBeforeWrite ? "Backups ON" : "Backups OFF" }}</span>
          </div>

          <div v-if="albumKey" class="pt-4">
            <button
              type="button"
              class="flex items-center gap-1.5 text-label-lg text-primary"
              @click="editAlbum"
            >
              <MageIcon name="edit" class="h-4 w-4" />
              Edit the whole album instead
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * Per-track metadata editing page, reached from the album editor's track list
 * (or anywhere else that links to `track-edit`). Writes through the single-track
 * endpoint via the metadata store, so `backup_before_write` is honoured.
 */
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import OrcIllustration from "../components/OrcIllustration.vue";
import GenreCombobox from "../components/metadata/GenreCombobox.vue";
import { useLibraryStore } from "../stores/library";
import { useSettingsStore } from "../stores/settings";
import {
  buildFieldUpdate,
  EMPTY_METADATA_FORM,
  useMetadataStore,
} from "../stores/metadata";
import type { MetadataFieldKey, MetadataForm } from "../stores/metadata";
import type { CatalogTrack } from "../types";

const TEXT_FIELD_KEYS = ["title", "artist", "album", "album_artist", "featuring"] as const satisfies readonly MetadataFieldKey[];
const NUMERIC_FIELD_KEYS = ["year", "track_number", "disc_number"] as const satisfies readonly MetadataFieldKey[];

const FIELD_LABELS: Record<MetadataFieldKey, string> = {
  title: "Title",
  artist: "Artist",
  album: "Album",
  album_artist: "Album artist",
  featuring: "Featuring",
  year: "Year",
  genre: "Genre",
  track_number: "Track #",
  disc_number: "Disc #",
};

const props = defineProps<{ trackId: string }>();

const router = useRouter();
const lib = useLibraryStore();
const settings = useSettingsStore();
const metadataStore = useMetadataStore();

const track = computed<CatalogTrack | null>(() => lib.trackById.get(Number(props.trackId)) ?? null);

/** The track's cover art (its own embedded picture), for the header. */
const coverUrl = computed(() => {
  const t = track.value;
  if (!t || !t.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});
const albumKey = computed(() => (track.value ? lib.keyForTrack(track.value) : null));

const form = ref<MetadataForm>({ ...EMPTY_METADATA_FORM });
const touched = ref<Set<MetadataFieldKey>>(new Set());
const cleared = ref<Set<MetadataFieldKey>>(new Set());
const saving = ref(false);

function markEdited(key: MetadataFieldKey): void {
  touched.value = new Set(touched.value).add(key);
}

function clearField(key: MetadataFieldKey): void {
  form.value = { ...form.value, [key]: "" };
  cleared.value = new Set(cleared.value).add(key);
  markEdited(key);
}

function syncFromTrack(): void {
  const t = track.value;
  touched.value = new Set();
  cleared.value = new Set();
  if (!t) {
    form.value = { ...EMPTY_METADATA_FORM };
    return;
  }
  form.value = {
    title: t.title ?? "",
    artist: t.artist ?? "",
    album: t.album ?? "",
    album_artist: t.album_artist ?? "",
    featuring: t.featuring ?? "",
    year: t.year != null ? String(t.year) : "",
    genre: t.genre ?? "",
    track_number: t.track_number != null ? String(t.track_number) : "",
    disc_number: t.disc_number != null ? String(t.disc_number) : "",
  };
}

watch(track, syncFromTrack, { immediate: true, deep: true });

async function save(): Promise<void> {
  const t = track.value;
  if (!t) return;
  const update = buildFieldUpdate(form.value, touched.value, cleared.value, false);
  saving.value = true;
  try {
    const ok = await metadataStore.saveSingle(t.id, update);
    if (ok) syncFromTrack();
  } finally {
    saving.value = false;
  }
}

function editAlbum(): void {
  if (albumKey.value) void router.push({ name: "album-edit", params: { albumKey: albumKey.value } });
}
</script>
