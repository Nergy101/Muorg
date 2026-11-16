<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <template v-if="!albumName">
      <div class="content-col flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
        <div class="w-40 shrink-0 sm:w-48">
          <OrcIllustration variant="not-found" />
        </div>
        <p class="text-body-md text-on-surface-variant">Album not found</p>
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
        <!-- Album art, right of the back button, so the form is contextualized. -->
        <div class="h-10 w-10 shrink-0 overflow-hidden rounded-md bg-surface-variant">
          <img
            v-if="coverUrl"
            :src="coverUrl"
            :alt="albumName ?? ''"
            class="h-full w-full object-cover"
            draggable="false"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-title-md text-on-surface">{{ albumName }}</h1>
          <p class="truncate text-body-sm text-on-surface-variant">{{ albumArtist }}</p>
        </div>
      </div>

      <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
        <!-- ─── Album fields ──────────────────────────────────────────── -->
        <div class="px-4 py-3">
          <p class="pb-2 text-body-sm text-on-surface-variant">
            Shared values are pre-filled; only the fields you change are written to all
            {{ tracks.length }} track{{ tracks.length === 1 ? "" : "s" }} of this album.
          </p>
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
            <div class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">{{ FIELD_LABELS.year }}</label>
              <div class="relative">
                <input
                  v-model="form.year"
                  type="text"
                  inputmode="numeric"
                  pattern="[0-9]*"
                  class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
                  :class="form.year ? 'pr-9' : ''"
                  @input="markEdited('year')"
                />
                <button
                  v-if="form.year"
                  type="button"
                  tabindex="-1"
                  class="absolute inset-y-0 right-2 my-auto h-fit p-1 text-on-surface-variant"
                  aria-label="Clear Year"
                  @click="clearField('year')"
                >
                  <MageIcon name="multiply" class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2 pt-3">
            <button
              type="button"
              class="rounded-full bg-surface-variant px-4 py-2 text-label-lg text-on-surface transition-colors hover:bg-surface-container"
              @click="router.back()"
            >Cancel</button>
            <div class="flex-1" />
            <!-- Backups hint links to the settings section that owns the toggle. -->
            <RouterLink
              :to="{ name: 'settings', hash: '#backup-before-write' }"
              class="text-body-sm underline underline-offset-2"
              :class="settings.backupBeforeWrite ? 'text-primary' : 'text-on-surface-variant'"
            >{{ settings.backupBeforeWrite ? "Backups ON" : "Backups OFF" }}</RouterLink>
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-label-lg text-on-primary disabled:opacity-50"
              :disabled="saving"
              @click="save"
            >
              <MageIcon name="save-floppy" class="h-4 w-4" />
              {{ saving ? "Saving…" : "Save" }}
            </button>
          </div>
        </div>

        <!-- ─── Tracks of this album ──────────────────────────────────── -->
        <div class="border-t border-outline/30" />
        <p class="px-4 pb-1 pt-3 text-label-sm text-on-surface-variant">
          TRACKS — TAP A TRACK TO EDIT IT
        </p>
        <div class="p-2">
          <button
            v-for="(track, i) in tracks"
            :key="track.id"
            type="button"
            class="flex h-14 w-full items-center gap-3 rounded-xl px-3 text-left transition-colors hover:bg-surface-container"
            @click="editTrack(track)"
          >
            <span class="w-6 shrink-0 text-center text-body-sm text-on-surface-variant">{{ i + 1 }}</span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-body-lg text-on-surface">{{ track.title ?? "(untitled)" }}</span>
              <span class="block truncate text-body-sm text-on-surface-variant">{{ track.artist ?? track.album_artist ?? "" }}</span>
            </span>
            <span class="shrink-0 text-body-sm text-on-surface-variant">{{ formatDuration(track.duration_secs) }}</span>
            <MageIcon name="chevron-right" class="h-4 w-4 shrink-0 text-on-surface-variant" />
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * Album-level metadata editing, reached from the album page's 3-dots menu.
 * The album fields (title, album artist, year, genre) are pre-filled with the
 * value shared by every track of the album; saving writes only the touched
 * fields to all of the album's tracks through the batch endpoint. The track
 * list underneath links to the per-track editor page.
 */
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import OrcIllustration from "../components/OrcIllustration.vue";
import GenreCombobox from "../components/metadata/GenreCombobox.vue";
import { recordAlbumKeyRedirect, resolveAlbumKey, useLibraryStore } from "../stores/library";
import { useSettingsStore } from "../stores/settings";
import {
  buildFieldUpdate,
  sharedFormValue,
  useMetadataStore,
} from "../stores/metadata";
import type { MetadataFieldKey, MetadataForm } from "../stores/metadata";
import type { CatalogTrack } from "../types";

const TEXT_FIELD_KEYS = ["album", "album_artist", "featuring"] as const satisfies readonly MetadataFieldKey[];

const FIELD_LABELS: Partial<Record<MetadataFieldKey, string>> = {
  album: "Album title",
  album_artist: "Album artist",
  featuring: "Featuring",
  genre: "Genre",
  year: "Release year",
};

const props = defineProps<{ albumKey: string }>();

const router = useRouter();
const lib = useLibraryStore();
const settings = useSettingsStore();
const metadataStore = useMetadataStore();

/**
 * The album's track ids, resolved from the route key. From then on the album
 * is followed BY ID, not by key: the key is `album|||artist`, so renaming the
 * album or its artist in this very form changes the key, and resolving by key
 * again would report "Album not found" right after a successful save.
 */
const albumTrackIds = ref<number[]>([]);
watch(
  () => props.albumKey,
  (key) => {
    // A URL or history entry holding the pre-rename key forwards to the
    // renamed album's editor instead of showing "Album not found".
    const resolved = resolveAlbumKey(key);
    if (resolved !== key) {
      void router.replace({ name: "album-edit", params: { albumKey: resolved } });
      return;
    }
    albumTrackIds.value = lib.tracksForAlbum(key).map((t) => t.id);
  },
  { immediate: true },
);

const tracks = computed<CatalogTrack[]>(() =>
  albumTrackIds.value
    .map((id) => lib.trackById.get(id))
    .filter((t): t is CatalogTrack => t != null),
);

/** Display name of the album as currently tagged (null when it can't be resolved). */
const albumName = computed(() => tracks.value[0]?.album ?? null);

/** Album artist as currently tagged, shown under the "Editing …" title.
 *  Falls back to the track artist when no album artist tag is set. */
const albumArtist = computed(() => {
  const t = tracks.value[0];
  return t?.album_artist ?? t?.artist ?? "";
});

/** Cover art of the album (first track that has one), for the header. */
const coverUrl = computed(() => {
  const t = tracks.value.find((t) => t.has_cover) ?? tracks.value[0];
  if (!t || !t.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const form = ref<MetadataForm>({
  title: "",
  artist: "",
  album: "",
  album_artist: "",
  featuring: "",
  year: "",
  genre: "",
  track_number: "",
  disc_number: "",
});
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

function syncFromTracks(): void {
  const list = tracks.value;
  touched.value = new Set();
  cleared.value = new Set();
  form.value = {
    ...form.value,
    album: sharedFormValue(list.map((t) => t.album)),
    album_artist: sharedFormValue(list.map((t) => t.album_artist)),
    featuring: sharedFormValue(list.map((t) => t.featuring)),
    year: sharedFormValue(list.map((t) => (t.year != null ? String(t.year) : ""))),
    genre: sharedFormValue(list.map((t) => t.genre)),
  };
}

watch(tracks, syncFromTracks, { immediate: true, deep: true });

async function save(): Promise<void> {
  const ids = tracks.value.map((t) => t.id);
  if (ids.length === 0) return;
  const update = buildFieldUpdate(form.value, touched.value, cleared.value, true);
  saving.value = true;
  try {
    const ok = await metadataStore.saveBulk(ids, update);
    if (ok) {
      syncFromTracks();
      // Renaming the album (or its artist) changed its key — point the URL at
      // the renamed album so a reload lands on this same editor again.
      const first = tracks.value[0];
      const newKey = first ? lib.keyForTrack(first) : null;
      if (newKey && newKey !== props.albumKey) {
        // Remember the rename so stale URLs/history entries (the album page
        // below this editor, old deep links) forward to the new key.
        recordAlbumKeyRedirect(props.albumKey, newKey);
        void router.replace({ name: "album-edit", params: { albumKey: newKey } });
      }
    }
  } finally {
    saving.value = false;
  }
}

function editTrack(track: CatalogTrack): void {
  void router.push({ name: "track-edit", params: { trackId: String(track.id) } });
}

function formatDuration(secs: number | null | undefined): string {
  if (secs == null) return "";
  const m = Math.floor(secs / 60);
  const s = Math.round(secs % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}
</script>
