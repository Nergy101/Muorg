<template>
  <div class="album-edit-root absolute inset-0 flex flex-col overflow-hidden bg-background">
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
      <!-- Landscape desktop: cover editor in a sticky left column, fields and
           actions in the right column, track list full-width below. -->
      <div class="lg:grid lg:grid-cols-[minmax(0,360px)_minmax(0,1fr)] lg:gap-10 lg:px-8 lg:pt-6">
        <!-- ─── Cover art editor ──────────────────────────────────────── -->
        <div class="flex flex-col items-center gap-3 px-4 pt-4 lg:sticky lg:top-6 lg:self-start lg:px-0 lg:pt-0">
          <div class="h-44 w-44 shrink-0 overflow-hidden rounded-xl bg-surface-variant shadow-[0_2px_6px_rgba(0,0,0,0.35)] sm:h-52 sm:w-52 lg:h-72 lg:w-72">
            <img
              v-if="displayCoverUrl"
              :src="displayCoverUrl"
              :alt="albumName ?? 'Album cover'"
              class="h-full w-full object-cover"
              draggable="false"
              @load="onCoverImgLoad"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-10 w-10 text-on-surface-variant/60" />
            </div>
          </div>
          <input
            ref="coverFileRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="onCoverFileChange"
          />
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-surface-variant px-4 py-2 text-label-lg text-on-surface"
              @click="coverFileRef?.click()"
            >
              <MageIcon name="image" class="h-4 w-4" />
              {{ displayCoverUrl ? "Change cover" : "Add cover" }}
            </button>
            <button
              v-if="displayCoverUrl"
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-surface-variant px-4 py-2 text-label-lg text-error"
              @click="clearCover"
            >
              <MageIcon name="trash" class="h-4 w-4" />
              Remove
            </button>
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-full bg-surface-variant px-4 py-2 text-label-lg text-on-surface disabled:opacity-50"
              :disabled="coverSearching"
              @click="searchCoverOnline"
            >
              <span
                v-if="coverSearching"
                class="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
                aria-hidden="true"
              />
              <MageIcon v-else name="search" class="h-4 w-4" />
              {{ coverSearching ? "Searching…" : "Search online" }}
            </button>
          </div>
          <p v-if="coverSearchError" class="text-body-sm text-error">{{ coverSearchError }}</p>
          <p v-if="coverDimensions" class="text-body-sm text-on-surface-variant">
            {{ coverDimensions.width }}×{{ coverDimensions.height }}px
          </p>
          <!-- Online-search candidates: pick one to fill the cover input. -->
          <div v-if="coverCandidates.length" class="flex max-w-full flex-wrap items-center justify-center gap-2">
            <button
              v-for="(c, i) in coverCandidates"
              :key="i"
              type="button"
              class="relative h-16 w-16 overflow-hidden rounded-lg ring-2"
              :class="selectedCandidate === i ? 'ring-primary' : 'ring-transparent hover:ring-on-surface-variant/40'"
              :title="c.width && c.height ? `${c.width}×${c.height}px` : 'Cover candidate'"
              @click="selectCandidate(c, i)"
            >
              <img
                :src="`data:${c.mime};base64,${c.base64}`"
                :alt="c.original ? 'Original cover' : 'Cover candidate'"
                class="h-full w-full object-cover"
                draggable="false"
              />
              <span
                v-if="c.original"
                class="absolute bottom-0.5 left-0.5 rounded bg-black/70 px-1 py-px text-[9px] font-semibold uppercase tracking-wide text-white"
              >Original</span>
            </button>
          </div>
          <p v-if="pendingCover" class="text-body-sm text-primary">New cover selected — saved with the form.</p>
        </div>

        <!-- ─── Album fields ──────────────────────────────────────────── -->
        <div class="px-4 py-3 lg:px-0 lg:py-0">
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
          <!-- Indeterminate bar while the batch write is in flight. -->
          <div v-if="saving" class="mt-2 h-1 w-full overflow-hidden rounded-full bg-surface-variant">
            <div class="h-full w-1/3 animate-[hf-indeterminate_1.2s_ease-in-out_infinite] bg-primary" />
          </div>
        </div>
      </div>

        <!-- ─── Tracks of this album ──────────────────────────────────── -->
        <div class="border-t border-outline/30" />
        <p class="px-4 pb-1 pt-3 text-label-sm text-on-surface-variant lg:px-8">
          TRACKS — TAP A TRACK TO EDIT IT
        </p>
        <div class="p-2 lg:px-8">
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
    <CoverCropModal
      v-if="cropImageUrl"
      :image-url="cropImageUrl"
      @apply="onCropApply"
      @cancel="cropImageUrl = null; selectedCandidate = -1"
    />
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
import CoverCropModal from "../components/CoverCropModal.vue";
import { api } from "../api/client";
import { findAlbumCoverUrls } from "@shared/utils/wikipediaCover";
import { downscaleImageToJpegBase64, normalizeMime, stripDataUrlPrefix } from "@shared/utils/imageData";
import OrcIllustration from "../components/OrcIllustration.vue";
import GenreCombobox from "../components/metadata/GenreCombobox.vue";
import { recordAlbumKeyRedirect, resolveAlbumKey, useLibraryStore } from "../stores/library";
import { useSettingsStore } from "../stores/settings";
import { showToast } from "../composables/useToast";
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
  // Re-resolve when the catalog (re)loads too: on a fresh page load the
  // library is still streaming in at mount, and resolving once against an
  // empty catalog would stick the view on "Album not found".
  [() => props.albumKey, () => lib.tracks.length],
  ([key]) => {
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

// --- Cover art editing ------------------------------------------------------

const coverFileRef = ref<HTMLInputElement | null>(null);
/** base64 data URL of the newly cropped cover, applied on save. */
const pendingCover = ref<string | null>(null);
const coverCleared = ref(false);
const cropImageUrl = ref<string | null>(null);

const displayCoverUrl = computed(() => {
  // pendingCover is raw base64 (what picture_base64 expects) — prefix it for <img>.
  if (pendingCover.value) return `data:image/jpeg;base64,${pendingCover.value}`;
  if (coverCleared.value) return null;
  return coverUrl.value;
});

function onCoverFileChange(e: Event): void {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  cropImageUrl.value = URL.createObjectURL(file);
}

function onCropApply(dataUrl: string): void {
  // The crop canvas emits a full data URL; picture_base64 needs raw base64.
  pendingCover.value = stripDataUrlPrefix(dataUrl);
  coverCleared.value = false;
  cropImageUrl.value = null;
  selectedCandidate.value = -1;
}

function clearCover(): void {
  pendingCover.value = null;
  coverCleared.value = true;
}

const coverSearching = ref(false);
const coverSearchError = ref<string | null>(null);
interface CoverCandidate { base64: string; mime: string; width: number | null; height: number | null; original?: boolean }
const coverCandidates = ref<CoverCandidate[]>([]);

/** Index of the candidate whose crop modal is open (ring highlight). */
const selectedCandidate = ref(-1);

/** Selecting a candidate opens the crop modal on it; the cropped result is
 *  staged via onCropApply, capped at MAX_COVER_EDGE_PX keeping aspect ratio. */
function selectCandidate(c: CoverCandidate, i: number): void {
  selectedCandidate.value = i;
  cropImageUrl.value = `data:${c.mime};base64,${c.base64}`;
}
const coverDimensions = ref<{ width: number; height: number } | null>(null);

function onCoverImgLoad(e: Event): void {
  const img = e.target as HTMLImageElement;
  coverDimensions.value = { width: img.naturalWidth, height: img.naturalHeight };
}

/** Auto-search Wikipedia for this album's cover; shows all candidates as a
 *  selection strip — picking one stages it as the new cover. */
async function searchCoverOnline(): Promise<void> {
  coverSearchError.value = null;
  coverCandidates.value = [];
  coverSearching.value = true;
  try {
    const found = await findAlbumCoverUrls(
      form.value.album || albumName.value || "",
      form.value.album_artist || albumArtist.value || "",
      6,
    );
    const urls = found.filter((f): f is { ok: true; url: string } => f.ok).map((f) => f.url);
    if (urls.length === 0) {
      coverSearchError.value = found[0] && !found[0].ok ? found[0].reason : "No images found.";
      return;
    }
    const candidates: CoverCandidate[] = [];
    // Candidates are downscaled to at most 1400px for the strip preview; the
    // final staged cover is capped at MAX_COVER_EDGE_PX (500px, the
    // Rockbox-friendly default) by the crop modal when a candidate is picked,
    // so the per-track tag write stays far below the server's request body
    // limit (a full-resolution download's base64 would be rejected with 413).
    async function stageCandidate(dataUrl: string): Promise<{ base64: string; width: number; height: number }> {
      const base64 = await downscaleImageToJpegBase64(dataUrl, 1400);
      // Only keep candidates that actually decode — a broken image must not
      // show up in the strip.
      const dims = await new Promise<{ width: number; height: number }>((resolve) => {
        const img = new Image();
        img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
        img.onerror = () => resolve({ width: 0, height: 0 });
        img.src = `data:image/jpeg;base64,${base64}`;
      });
      return { base64, ...dims };
    }
    // The album's current cover is always the first candidate, tagged Original.
    if (coverUrl.value) {
      try {
        const blob = await (await fetch(coverUrl.value)).blob();
        const dataUrl = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result));
          reader.onerror = reject;
          reader.readAsDataURL(blob);
        });
        const { base64, width, height } = await stageCandidate(dataUrl);
        if (width > 0) {
          candidates.push({ base64, mime: "image/jpeg", width, height, original: true });
        }
      } catch {
        // original cover unavailable — search results still show
      }
    }
    for (const url of urls) {
      try {
        const { base64, mime } = await api.fetchImage(url);
        const normMime = normalizeMime(mime);
        if (!normMime.startsWith("image/")) continue;
        const staged = await stageCandidate(`data:${normMime};base64,${base64}`);
        // Keep anything that decodes and is at least 300px on its longest
        // edge — the crop modal keeps the aspect ratio, so non-square images
        // are usable covers too.
        if (staged.width === 0 || staged.height === 0 || Math.max(staged.width, staged.height) < 300) continue;
        candidates.push({ base64: staged.base64, mime: "image/jpeg", width: staged.width, height: staged.height });
      } catch {
        // skip candidates that fail to download
      }
    }
    coverCandidates.value = candidates;
    if (candidates.length === 0) coverSearchError.value = "Could not download any found images.";
  } catch (e) {
    coverSearchError.value = e instanceof Error ? e.message : "Search failed.";
  } finally {
    coverSearching.value = false;
  }
}

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

/** Cover edits are per-album: drop any pending/cleared cover from a previous
 *  album so re-entering the editor (KeepAlive) never shows stale state. Only
 *  called when the album key actually changes — NOT on library reloads, which
 *  would wipe a pending selection mid-edit. */
function resetCoverState(): void {
  pendingCover.value = null;
  coverCleared.value = false;
  cropImageUrl.value = null;
  selectedCandidate.value = -1;
  coverSearchError.value = null;
  coverDimensions.value = null;
  coverCandidates.value = [];
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

// Reset cover state only when the album key actually changes (declared after
// the cover refs so the immediate run doesn't hit TDZ).
watch(
  () => props.albumKey,
  () => resetCoverState(),
  { immediate: true },
);

watch(tracks, syncFromTracks, { immediate: true, deep: true });

async function save(): Promise<void> {
  const ids = tracks.value.map((t) => t.id);
  if (ids.length === 0) return;
  const update = buildFieldUpdate(form.value, touched.value, cleared.value, true);
  const hasFieldChanges = Object.keys(update).length > 0;
  const hasCoverChange = pendingCover.value != null || coverCleared.value;
  if (!hasFieldChanges && !hasCoverChange) {
    showToast("No changes to save");
    return;
  }
  saving.value = true;
  try {
    // One batch call per change set: the batch endpoint writes every id in a
    // single request (and reports one "Edited N tracks" toast), so there is
    // no per-track loop. Field edits and the cover are independent — saveBulk
    // reports "no changes" when only the cover changed — so gate each write on
    // its own change set.
    let ok = true;
    if (hasFieldChanges) ok = await metadataStore.saveBulk(ids, update);
    if (ok && hasCoverChange) {
      ok = await metadataStore.saveBulk(ids, { picture_base64: coverCleared.value ? null : pendingCover.value });
    }
    if (ok && hasCoverChange) {
      pendingCover.value = null;
      coverCleared.value = false;
    }
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
