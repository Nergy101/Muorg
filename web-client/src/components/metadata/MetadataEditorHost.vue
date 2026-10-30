<template>
  <Teleport to="body">
    <div
      v-if="isEditorOpen"
      class="fixed inset-0 z-50 flex flex-col overflow-hidden bg-surface pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] lg:inset-y-0 lg:left-auto lg:right-0 lg:w-[420px] lg:border-l lg:border-outline/15 lg:pb-0 lg:pt-0 lg:shadow-2xl"
    >
      <!-- Header -->
      <div class="flex shrink-0 items-center gap-3 border-b border-outline/15 px-4 py-3">
        <h2 class="min-w-0 flex-1 truncate text-title-md text-on-surface">
          {{ isBulk ? `Edit ${ids.length} tracks` : (singleTrack?.title || "Edit metadata") }}
        </h2>
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant lg:hover:bg-surface-container"
          aria-label="Close"
          @click="discard"
        >
          <MageIcon name="multiply" class="h-4 w-4" />
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <!-- ─── Auto-tag lookup ─────────────────────────────────────────── -->
        <AutoTagPanel
          v-if="showAutoTag"
          :track="singleTrack"
          @back="showAutoTag = false"
          @apply="onAutoTagApply"
          @save-and-apply="onAutoTagSaveAndApply"
        />

        <!-- ─── Main form ───────────────────────────────────────────────── -->
        <template v-else>
          <p v-if="isBulk" class="px-4 pb-1 pt-3 text-body-sm text-on-surface-variant">
            Shared values are pre-filled; only the fields you change are written to the other tracks.
          </p>

          <!-- Cover art -->
          <div class="flex flex-col items-center gap-2 px-4 pt-4">
            <input ref="fileInputRef" type="file" accept="image/*" class="hidden" @change="onCoverFileChange" />
            <div
              class="group relative h-32 w-32 shrink-0 overflow-hidden rounded-xl border border-outline/20 bg-surface-variant"
              :class="coverDragOver ? 'ring-2 ring-primary' : ''"
              role="button"
              aria-label="Cover art — drop an image here to replace it"
              tabindex="0"
              @dragenter.prevent="onCoverDragEnter"
              @dragover="onCoverDragOver"
              @dragleave="onCoverDragLeave"
              @drop="onCoverDrop"
            >
              <img
                v-if="displayCoverUrl"
                :src="displayCoverUrl"
                alt="Cover"
                class="h-full w-full cursor-pointer object-cover"
                @click="zoomOpen = true"
              />
              <button
                v-else
                type="button"
                class="flex h-full w-full items-center justify-center"
                aria-label="Add cover"
                @click="fileInputRef?.click()"
              >
                <MageIcon name="compact-disk" class="h-10 w-10 text-on-surface-variant/50" />
              </button>
              <div
                v-if="displayCoverUrl"
                class="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-opacity group-hover:bg-black/40 group-hover:opacity-100"
              >
                <MageIcon name="eye" class="h-7 w-7 text-white" />
              </div>
            </div>
            <p v-if="coverDimensions || coverSizeBytes != null" class="text-body-sm text-on-surface-variant">
              <template v-if="coverDimensions">{{ coverDimensions.width }}×{{ coverDimensions.height }}px</template>
              <template v-if="coverDimensions && coverSizeBytes != null"> · </template>
              <template v-if="coverSizeBytes != null">{{ formatImageSize(coverSizeBytes) }}</template>
            </p>
            <p v-if="largeImageWarning" class="max-w-[220px] text-center text-body-sm text-amber-500">
              Over 1 MB — large artwork bloats every tagged file.
            </p>
            <div class="flex flex-wrap items-center justify-center gap-2">
              <button type="button" class="flex items-center gap-1 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-on-surface" @click="fileInputRef?.click()">
                <MageIcon name="plus" class="h-3.5 w-3.5" />
                {{ displayCoverUrl ? "Change" : "Add image" }}
              </button>
              <button type="button" class="flex items-center gap-1 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-on-surface" @click="openFromWikipedia">
                <MageIcon name="search" class="h-3.5 w-3.5" />
                Wikipedia
              </button>
              <button type="button" class="flex items-center gap-1 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-on-surface" @click="fetchUrlOpen = !fetchUrlOpen">
                <MageIcon name="download" class="h-3.5 w-3.5" />
                From URL
              </button>
              <button v-if="displayCoverUrl" type="button" class="flex items-center gap-1 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-error" @click="clearCover">
                <MageIcon name="trash" class="h-3.5 w-3.5" />
                Clear
              </button>
            </div>
            <div v-if="fetchUrlOpen" class="flex w-full max-w-sm items-center gap-2">
              <input
                v-model="fetchUrlInput"
                type="url"
                placeholder="https://…/cover.jpg"
                class="min-w-0 flex-1 rounded-xl bg-surface-variant px-3 py-2 text-body-md text-on-surface outline-none"
                @keyup.enter="fetchFromUrl"
              />
              <button type="button" class="shrink-0 text-label-lg text-primary disabled:opacity-50" :disabled="fetchUrlLoading" @click="fetchFromUrl">
                {{ fetchUrlLoading ? "…" : "Go" }}
              </button>
            </div>
            <p v-if="fetchUrlError" class="max-w-sm text-center text-body-sm text-error">{{ fetchUrlError }}</p>
            <div v-if="canApplyToAlbum" class="flex items-center gap-2">
              <template v-if="!applyAlbumConfirming">
                <button type="button" class="flex items-center gap-1 text-label-lg text-primary" @click="applyAlbumConfirming = true">
                  <MageIcon name="stack" class="h-3.5 w-3.5" />
                  Apply to whole album
                </button>
              </template>
              <template v-else>
                <span class="text-body-sm text-on-surface-variant">Apply to {{ albumTrackIds.length }} tracks?</span>
                <button type="button" class="text-label-lg text-primary" @click="applyToWholeAlbum">Confirm</button>
                <button type="button" class="text-label-lg text-on-surface-variant" @click="applyAlbumConfirming = false">Cancel</button>
              </template>
            </div>
          </div>

          <!-- Rating -->
          <div class="flex items-center justify-between gap-4 px-4 py-3">
            <span class="text-body-lg text-on-surface">Rating</span>
            <div class="flex items-center gap-2">
              <StarRating :model-value="rating" @update:model-value="onSetRating" />
              <button v-if="rating !== null" type="button" class="text-body-sm text-on-surface-variant" @click="onSetRating(null)">Clear</button>
            </div>
          </div>

          <!-- Fields -->
          <div class="grid grid-cols-2 gap-x-3 px-4">
            <div v-for="key in TEXT_FIELD_KEYS" :key="key" class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">{{ FIELD_LABELS[key] }}</label>
              <div class="relative">
                <input
                  v-model="form[key]"
                  type="text"
                  :placeholder="isBulk ? 'Mixed' : undefined"
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
                  :placeholder="isBulk ? 'Mixed' : undefined"
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

          <!-- ReplayGain (read-only) -->
          <div v-if="!isBulk" class="grid grid-cols-2 gap-x-3 px-4 pt-1">
            <div class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">ReplayGain track</label>
              <p class="rounded-xl bg-surface-variant/50 px-3 py-2.5 text-body-md text-on-surface-variant">
                {{ replayGainText(replayGainMeta?.replaygain_track_gain_db, replayGainMeta?.replaygain_track_peak) }}
              </p>
            </div>
            <div class="py-1.5">
              <label class="mb-1 block text-body-sm text-on-surface-variant">ReplayGain album</label>
              <p class="rounded-xl bg-surface-variant/50 px-3 py-2.5 text-body-md text-on-surface-variant">
                {{ replayGainText(replayGainMeta?.replaygain_album_gain_db, replayGainMeta?.replaygain_album_peak) }}
              </p>
            </div>
          </div>

          <!-- Auto-tag / apply from path entry points -->
          <div class="flex flex-wrap items-center gap-2 px-4 pt-3">
            <button v-if="!isBulk" type="button" class="flex items-center gap-1.5 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-on-surface" @click="showAutoTag = true">
              <MageIcon name="search" class="h-3.5 w-3.5" />
              Look up on MusicBrainz
            </button>
            <button v-if="canApplyFromPath" type="button" class="flex items-center gap-1.5 rounded-full bg-surface-variant px-3 py-1.5 text-label-lg text-on-surface" @click="applyFromPath">
              <MageIcon name="zap" class="h-3.5 w-3.5" />
              Apply from path
            </button>
          </div>

          <!-- Rename (single track) -->
          <div v-if="!isBulk" class="px-4 pt-3">
            <button v-if="!renameOpen" type="button" class="flex items-center gap-1.5 text-label-lg text-on-surface" @click="openRename">
              <MageIcon name="edit" class="h-3.5 w-3.5" />
              Rename file
            </button>
            <div v-else class="flex flex-col gap-2">
              <label class="text-body-sm text-on-surface-variant">New path</label>
              <input
                v-model="renamePath"
                type="text"
                class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-md text-on-surface outline-none"
                @keyup.enter="submitRename"
              />
              <div class="flex justify-end gap-2">
                <button type="button" class="text-label-lg text-on-surface-variant" @click="renameOpen = false">Cancel</button>
                <button type="button" class="text-label-lg text-primary disabled:opacity-50" :disabled="renaming" @click="submitRename">
                  {{ renaming ? "Renaming…" : "Rename" }}
                </button>
              </div>
            </div>
          </div>

          <!-- Backups -->
          <div v-if="!isBulk && latestBackup" class="px-4 pt-3">
            <button type="button" class="flex items-center gap-1.5 text-label-lg text-on-surface" @click="onRestoreBackup">
              <MageIcon name="clock" class="h-3.5 w-3.5" />
              Restore from backup
            </button>
          </div>

          <!-- Delete -->
          <div class="px-4 pt-3">
            <button type="button" class="flex items-center gap-1.5 text-label-lg text-error" @click="deleteConfirmOpen = true">
              <MageIcon name="trash" class="h-3.5 w-3.5" />
              Delete track{{ isBulk ? "s" : "" }}
            </button>
          </div>

          <div class="h-6" />
        </template>
      </div>

      <!-- Footer actions -->
      <div v-if="!showAutoTag" class="flex shrink-0 flex-wrap items-center gap-2 border-t border-outline/15 px-4 py-3">
        <button
          type="button"
          class="flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-label-lg text-on-primary disabled:opacity-50"
          :disabled="saving"
          @click="save"
        >
          <MageIcon name="save-floppy" class="h-4 w-4" />
          {{ saving ? "Saving…" : "Save" }}
        </button>
        <button type="button" class="text-label-lg text-on-surface-variant" @click="discard">Cancel</button>
        <span
          class="text-body-sm"
          :class="settings.backupBeforeWrite ? 'text-primary' : 'text-on-surface-variant'"
        >{{ settings.backupBeforeWrite ? "Backups ON" : "Backups OFF" }}</span>
        <span class="ml-auto flex items-center gap-2">
          <button type="button" class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant disabled:opacity-30 lg:hover:bg-surface-container" title="Undo (Ctrl+Z)" :disabled="!canUndo" @click="undoLast">
            <MageIcon name="reload" class="h-4 w-4" />
          </button>
          <button type="button" class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant disabled:opacity-30 lg:hover:bg-surface-container" title="Redo (Ctrl+Shift+Z)" :disabled="!canRedo" @click="redoLast">
            <MageIcon name="refresh" class="h-4 w-4" />
          </button>
        </span>
      </div>
    </div>
  </Teleport>

  <!-- Zoom preview -->
  <Teleport to="body">
    <div
      v-if="zoomOpen && displayCoverUrl"
      class="fixed inset-0 z-[80] flex items-center justify-center bg-black/80 p-6"
      @click="zoomOpen = false"
    >
      <img :src="displayCoverUrl" alt="Cover" class="max-h-full max-w-full rounded-xl object-contain" />
    </div>
  </Teleport>

  <WikipediaCoverModal
    :open="showWikipediaModal"
    :image-url="wikipediaImageUrl"
    :loading="wikipediaSearchLoading"
    :error="wikipediaError"
    :applying="wikipediaApplying"
    @close="closeWikipediaModal"
    @apply="applyWikipediaImage"
  />

  <ConfirmDialog
    :open="confirmState != null"
    :title="confirmState?.title ?? ''"
    :message="confirmState?.message ?? ''"
    :confirm-label="confirmState?.confirmLabel ?? 'Confirm'"
    :danger="confirmState?.danger"
    @confirm="resolveConfirm(true)"
    @cancel="resolveConfirm(false)"
  />

  <ConfirmDialog
    :open="deleteConfirmOpen"
    title="Delete track"
    :message="`Delete ${ids.length} track${ids.length === 1 ? '' : 's'} from the server? This permanently removes the file${ids.length === 1 ? '' : 's'} and cannot be undone.`"
    confirm-label="Delete"
    danger
    @confirm="onDeleteConfirm"
    @cancel="deleteConfirmOpen = false"
  />
</template>

<script setup lang="ts">
/**
 * The web app's metadata editor: mounted once in App.vue and driven entirely
 * by `composables/useMetadataEditor` (openMetadataEditor/closeMetadataEditor).
 * Mobile gets a full-height sheet; at `lg` it docks as a right-hand panel so
 * the rest of the app stays usable while editing.
 *
 * All writes go through `stores/metadata.ts` — this component only builds the
 * `MetadataUpdate` from form state and asks the store to save it.
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import MageIcon from "../MageIcon.vue";
import ConfirmDialog from "../ConfirmDialog.vue";
import AutoTagPanel from "../AutoTagPanel.vue";
import StarRating from "./StarRating.vue";
import GenreCombobox from "./GenreCombobox.vue";
import WikipediaCoverModal from "./WikipediaCoverModal.vue";
import { closeMetadataEditor, editorIds, isEditorOpen } from "../../composables/useMetadataEditor";
import {
  applyExtractedToForm,
  buildFieldUpdate,
  EMPTY_METADATA_FORM,
  sharedFormValue,
  useMetadataStore,
} from "../../stores/metadata";
import type { MetadataFieldKey, MetadataForm } from "../../stores/metadata";
import { useLibraryStore } from "../../stores/library";
import { useSettingsStore } from "../../stores/settings";
import { canRedo, canUndo, redoLast, undoLast } from "../../composables/useUndo";
import { showToast, showWarning } from "../../composables/useToast";
import { applyCandidate } from "../../composables/useAutoTag";
import { api } from "../../api/client";
import { getCoverBlob, getMetadata } from "../../api/catalog";
import {
  bytesToBase64,
  formatImageSize,
  normalizeMime,
  pngDataUrlToJpegBase64,
  stripDataUrlPrefix,
} from "@shared/utils/imageData";
import { findAlbumCoverUrl } from "@shared/utils/wikipediaCover";
import { buildUpdateFromExtracted, extractBestFromPath } from "@shared/utils/pathFormat";
import type { CatalogTrack, MatchCandidate, MetadataUpdate, TrackBackupRecord, TrackMetadata } from "@shared/api";

const ONE_MB = 1024 * 1024;

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

const metadataStore = useMetadataStore();
const library = useLibraryStore();
const settings = useSettingsStore();

const ids = computed(() => editorIds.value);
const tracks = computed<CatalogTrack[]>(() =>
  ids.value.map((id) => library.trackById.get(id)).filter((t): t is CatalogTrack => t != null),
);
const isBulk = computed(() => tracks.value.length > 1);
const singleTrack = computed<CatalogTrack | null>(() => (tracks.value.length === 1 ? tracks.value[0] : null));

// --- form state --------------------------------------------------------

const form = ref<MetadataForm>({ ...EMPTY_METADATA_FORM });
const touched = ref<Set<MetadataFieldKey>>(new Set());
const cleared = ref<Set<MetadataFieldKey>>(new Set());
const rating = ref<number | null>(null);
const saving = ref(false);
// Hoisted above `watch(ids, syncFromTracks, { immediate: true })` below: that
// watcher runs synchronously at setup and syncFromTracks resets both.
const showAutoTag = ref(false);
const zoomOpen = ref(false);

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
  pendingCoverBase64.value = null;
  coverCleared.value = false;
  applyAlbumConfirming.value = false;
  largeImageWarning.value = false;
  renameOpen.value = false;
  fetchUrlOpen.value = false;
  fetchUrlInput.value = "";
  fetchUrlError.value = null;
  zoomOpen.value = false;
  showAutoTag.value = false;
  if (list.length === 0) {
    form.value = { ...EMPTY_METADATA_FORM };
    rating.value = null;
    return;
  }
  if (list.length === 1) {
    const t = list[0];
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
  } else {
    form.value = {
      // Always blank in bulk mode: writing one title to several tracks is
      // almost always wrong even when they happen to currently share one.
      title: "",
      artist: sharedFormValue(list.map((t) => t.artist)),
      album: sharedFormValue(list.map((t) => t.album)),
      album_artist: sharedFormValue(list.map((t) => t.album_artist)),
      featuring: sharedFormValue(list.map((t) => t.featuring)),
      year: sharedFormValue(list.map((t) => (t.year != null ? String(t.year) : ""))),
      genre: sharedFormValue(list.map((t) => t.genre)),
      track_number: sharedFormValue(list.map((t) => (t.track_number != null ? String(t.track_number) : ""))),
      disc_number: sharedFormValue(list.map((t) => (t.disc_number != null ? String(t.disc_number) : ""))),
    };
  }
  const firstRating = list[0].rating ?? null;
  rating.value = list.every((t) => (t.rating ?? null) === firstRating) ? firstRating : null;
}

// --- generic inline confirm (backup retry, overwrite, bulk counts) ------

interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}
const confirmState = ref<(ConfirmOptions & { resolve: (ok: boolean) => void }) | null>(null);

function confirmAsync(opts: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    confirmState.value = { ...opts, resolve };
  });
}
function resolveConfirm(ok: boolean): void {
  confirmState.value?.resolve(ok);
  confirmState.value = null;
}

async function onBackupFailure(message: string): Promise<boolean> {
  return await confirmAsync({
    title: "Backup failed",
    message: `${message}\n\nContinue saving without a backup?`,
    confirmLabel: "Save without backup",
    danger: true,
  });
}

// --- rating (immediate, Q4 confirm for >1 track) ------------------------

async function onSetRating(value: number | null): Promise<void> {
  if (ids.value.length === 0) return;
  if (ids.value.length > 1) {
    const what = value === null ? "clear the rating" : `set the rating to ${value}★`;
    const ok = await confirmAsync({
      title: "Rate tracks",
      message: `${what[0].toUpperCase()}${what.slice(1)} on all ${ids.value.length} selected tracks?`,
      confirmLabel: "Rate",
    });
    if (!ok) return;
  }
  rating.value = value;
  await library.setRatings(ids.value, value);
}

// --- save ----------------------------------------------------------------

function buildUpdate(): MetadataUpdate {
  return buildFieldUpdate(form.value, touched.value, cleared.value, isBulk.value);
}

async function save(): Promise<void> {
  if (ids.value.length === 0) return;
  const update = buildUpdate();
  const hasCoverChange = pendingCoverBase64.value != null || coverCleared.value;
  if (Object.keys(update).length === 0 && !hasCoverChange) {
    showToast("No changes to save");
    return;
  }
  saving.value = true;
  try {
    if (Object.keys(update).length > 0) {
      if (isBulk.value) await metadataStore.saveBulk(ids.value, update);
      else await metadataStore.saveSingle(ids.value[0], update, onBackupFailure);
    }
    if (hasCoverChange) {
      await metadataStore.setCover(ids.value, coverCleared.value ? null : pendingCoverBase64.value, onBackupFailure);
    }
    await nextTick();
    syncFromTracks();
  } finally {
    saving.value = false;
  }
}

function discard(): void {
  closeMetadataEditor();
}

// --- replaygain + backup (single track only) -----------------------------

const replayGainMeta = ref<TrackMetadata | null>(null);
const latestBackup = ref<TrackBackupRecord | null>(null);
const singleTrackId = computed(() => singleTrack.value?.id ?? null);

watch(
  singleTrackId,
  async (id) => {
    if (id == null) {
      replayGainMeta.value = null;
      latestBackup.value = null;
      return;
    }
    replayGainMeta.value = await getMetadata(id);
    try {
      latestBackup.value = await api.getLatestBackup(id);
    } catch {
      latestBackup.value = null;
    }
  },
  { immediate: true },
);

function replayGainText(gainDb: number | null | undefined, peak: number | null | undefined): string {
  if (gainDb == null && peak == null) return "—";
  const parts: string[] = [];
  if (gainDb != null) parts.push(`${gainDb.toFixed(2)} dB`);
  if (peak != null) parts.push(`peak ${peak.toFixed(3)}`);
  return parts.join(" · ");
}

async function onRestoreBackup(): Promise<void> {
  const t = singleTrack.value;
  if (!t) return;
  const ok = await confirmAsync({
    title: "Restore backup?",
    message: "This replaces the current file with the most recent backup, discarding any edits made since then.",
    confirmLabel: "Restore",
    danger: true,
  });
  if (!ok) return;
  const restored = await metadataStore.restoreBackup(t.id);
  if (restored) syncFromTracks();
}

// --- rename (single track) ------------------------------------------------

const renameOpen = ref(false);
const renamePath = ref("");
const renaming = ref(false);

function openRename(): void {
  const t = singleTrack.value;
  if (!t) return;
  renamePath.value = t.path;
  renameOpen.value = true;
}

async function submitRename(): Promise<void> {
  const t = singleTrack.value;
  if (!t) return;
  const newPath = renamePath.value.trim();
  if (!newPath || newPath === t.path) {
    renameOpen.value = false;
    return;
  }
  renaming.value = true;
  try {
    let overwrite = false;
    const collisions = await api.checkRenameCollisions([{ id: t.id, newPath }]);
    if (collisions.length > 0) {
      const ok = await confirmAsync({
        title: "File already exists",
        message: `"${newPath}" already exists. Overwrite it?`,
        confirmLabel: "Overwrite",
        danger: true,
      });
      if (!ok) return;
      overwrite = true;
    }
    const ok2 = await metadataStore.renameFile(t.id, newPath, overwrite);
    if (ok2) renameOpen.value = false;
  } finally {
    renaming.value = false;
  }
}

// --- delete ----------------------------------------------------------------

const deleteConfirmOpen = ref(false);

async function onDeleteConfirm(): Promise<void> {
  deleteConfirmOpen.value = false;
  const idsToDelete = ids.value;
  const { deleted, failed } = await library.deleteTracks(idsToDelete);
  if (failed.length > 0) {
    showWarning(`Deleted ${deleted.length} track${deleted.length === 1 ? "" : "s"}; ${failed.length} failed`);
  } else {
    showToast(`Deleted ${deleted.length} track${deleted.length === 1 ? "" : "s"}`);
  }
  closeMetadataEditor();
}

// --- auto-tag --------------------------------------------------------------

const AUTOTAG_FIELDS: MetadataFieldKey[] = ["title", "artist", "album", "album_artist", "year", "track_number"];

function fillFromCandidate(candidate: MatchCandidate): void {
  form.value = applyCandidate(form.value, candidate);
  for (const key of AUTOTAG_FIELDS) markEdited(key);
}

function onAutoTagApply(candidate: MatchCandidate): void {
  fillFromCandidate(candidate);
  showAutoTag.value = false;
  showToast("Filled from MusicBrainz — review and save");
}

async function onAutoTagSaveAndApply(candidate: MatchCandidate): Promise<void> {
  fillFromCandidate(candidate);
  showAutoTag.value = false;
  await save();
}

// --- apply from path ---------------------------------------------------

const canApplyFromPath = computed(() => settings.pathFormatTemplates.some((t) => t.trim()) && ids.value.length > 0);

async function applyFromPath(): Promise<void> {
  const templates = settings.pathFormatTemplates;
  if (!templates.some((t) => t.trim())) return;
  if (!isBulk.value) {
    const t = singleTrack.value;
    if (!t) return;
    const extracted = extractBestFromPath(templates, t.path);
    if (!extracted) {
      showToast("No pattern matches this path");
      return;
    }
    const { form: nextForm, touched: touchedKeys } = applyExtractedToForm(form.value, extracted);
    form.value = nextForm;
    for (const key of touchedKeys) markEdited(key);
    showToast("Filled from path — review and save");
    return;
  }
  const items = tracks.value
    .map((t) => {
      const extracted = extractBestFromPath(templates, t.path);
      if (!extracted) return null;
      const update = buildUpdateFromExtracted(extracted);
      return Object.keys(update).length > 0 ? { id: t.id, update } : null;
    })
    .filter((x): x is { id: number; update: MetadataUpdate } => x != null);
  if (items.length === 0) {
    showToast("No tracks matched a path pattern");
    return;
  }
  const ok = await confirmAsync({
    title: "Apply from path",
    message: `Fill tags from file paths for ${items.length} of ${tracks.value.length} selected track${
      tracks.value.length === 1 ? "" : "s"
    }? This cannot be undone as a group, but each write still gets its own undo.`,
    confirmLabel: "Apply",
  });
  if (!ok) return;
  const wrote = await metadataStore.saveBulkCustom(items, `Apply from path (${items.length} tracks)`);
  if (wrote) syncFromTracks();
}

// --- cover art -----------------------------------------------------------

const fileInputRef = ref<HTMLInputElement | null>(null);
const pendingCoverBase64 = ref<string | null>(null);
const coverCleared = ref(false);
const coverDimensions = ref<{ width: number; height: number } | null>(null);
const coverSizeBytes = ref<number | null>(null);
const largeImageWarning = ref(false);
const coverDragOver = ref(false);
const coverDragDepth = ref(0);

const displayCoverUrl = computed<string | null>(() => {
  if (coverCleared.value) return null;
  if (pendingCoverBase64.value) return `data:image/jpeg;base64,${pendingCoverBase64.value}`;
  const t = singleTrack.value;
  if (t?.has_cover) {
    library.requestCover(t.id);
    return library.coverCache.get(t.id) ?? null;
  }
  return null;
});

function loadCoverMeta(dataUrl: string, sizeBytes?: number): void {
  coverDimensions.value = null;
  const base64Part = dataUrl.includes(",") ? stripDataUrlPrefix(dataUrl) : dataUrl;
  coverSizeBytes.value = sizeBytes ?? (base64Part ? Math.round((base64Part.length * 3) / 4) : null);
  const img = new Image();
  img.onload = () => {
    coverDimensions.value = { width: img.naturalWidth, height: img.naturalHeight };
  };
  img.src = dataUrl;
}

watch(displayCoverUrl, (url) => {
  if (!url) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    largeImageWarning.value = false;
    return;
  }
  loadCoverMeta(url);
});

async function applyCoverFile(file: File): Promise<void> {
  if (!file.type.startsWith("image/")) return;
  largeImageWarning.value = file.size > ONE_MB;
  coverCleared.value = false;
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error ?? new Error("Failed to read file"));
    reader.readAsDataURL(file);
  });
  pendingCoverBase64.value =
    normalizeMime(file.type) === "image/png" ? await pngDataUrlToJpegBase64(dataUrl) : stripDataUrlPrefix(dataUrl);
  loadCoverMeta(`data:image/jpeg;base64,${pendingCoverBase64.value}`, file.size);
}

function onCoverFileChange(e: Event): void {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) void applyCoverFile(file);
  input.value = "";
}

function isDraggingImage(e: DragEvent): boolean {
  const dt = e.dataTransfer;
  if (!dt) return false;
  if (dt.files && dt.files.length > 0) return dt.files[0]?.type.startsWith("image/") ?? false;
  return dt.types?.includes?.("Files") ?? false;
}

function onCoverDragEnter(e: DragEvent): void {
  if (!isDraggingImage(e)) return;
  coverDragDepth.value += 1;
  coverDragOver.value = true;
}
function onCoverDragOver(e: DragEvent): void {
  if (!isDraggingImage(e)) return;
  e.preventDefault();
  coverDragOver.value = true;
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
}
function onCoverDragLeave(e: DragEvent): void {
  const current = e.currentTarget as HTMLElement | null;
  const related = e.relatedTarget as Node | null;
  if (current && related && current.contains(related)) return;
  coverDragDepth.value = Math.max(0, coverDragDepth.value - 1);
  if (coverDragDepth.value === 0) coverDragOver.value = false;
}
function onCoverDrop(e: DragEvent): void {
  e.preventDefault();
  coverDragDepth.value = 0;
  coverDragOver.value = false;
  const file = e.dataTransfer?.files?.[0];
  if (file) void applyCoverFile(file);
}

function clearCover(): void {
  coverCleared.value = true;
  pendingCoverBase64.value = null;
  largeImageWarning.value = false;
}

// --- Wikipedia search ------------------------------------------------------

const showWikipediaModal = ref(false);
const wikipediaImageUrl = ref<string | null>(null);
const wikipediaSearchLoading = ref(false);
const wikipediaError = ref<string | null>(null);
const wikipediaApplying = ref(false);

async function openFromWikipedia(): Promise<void> {
  wikipediaError.value = null;
  wikipediaImageUrl.value = null;
  showWikipediaModal.value = true;
  wikipediaSearchLoading.value = true;
  try {
    const found = await findAlbumCoverUrl(form.value.album, form.value.artist);
    if (found.ok) wikipediaImageUrl.value = found.url;
    else wikipediaError.value = found.reason;
  } catch (e) {
    wikipediaError.value = e instanceof Error ? e.message : "Search failed.";
  } finally {
    wikipediaSearchLoading.value = false;
  }
}

function closeWikipediaModal(): void {
  showWikipediaModal.value = false;
  wikipediaImageUrl.value = null;
  wikipediaError.value = null;
}

async function applyWikipediaImage(): Promise<void> {
  const url = wikipediaImageUrl.value;
  if (!url) return;
  wikipediaApplying.value = true;
  try {
    const { base64, mime } = await api.fetchImage(url);
    coverCleared.value = false;
    pendingCoverBase64.value =
      normalizeMime(mime) === "image/png" ? await pngDataUrlToJpegBase64(`data:image/png;base64,${base64}`) : base64;
    loadCoverMeta(`data:image/jpeg;base64,${pendingCoverBase64.value}`);
    closeWikipediaModal();
  } catch (e) {
    wikipediaError.value = e instanceof Error ? e.message : String(e);
  } finally {
    wikipediaApplying.value = false;
  }
}

// --- fetch cover from arbitrary URL -----------------------------------

const fetchUrlOpen = ref(false);
const fetchUrlInput = ref("");
const fetchUrlLoading = ref(false);
const fetchUrlError = ref<string | null>(null);

async function fetchFromUrl(): Promise<void> {
  const url = fetchUrlInput.value.trim();
  if (!url) return;
  fetchUrlLoading.value = true;
  fetchUrlError.value = null;
  try {
    const { base64, mime } = await api.fetchImage(url);
    coverCleared.value = false;
    pendingCoverBase64.value =
      normalizeMime(mime) === "image/png" ? await pngDataUrlToJpegBase64(`data:image/png;base64,${base64}`) : base64;
    loadCoverMeta(`data:image/jpeg;base64,${pendingCoverBase64.value}`);
    fetchUrlOpen.value = false;
    fetchUrlInput.value = "";
  } catch (e) {
    // The server's allowlist rejection (and any other fetch failure) surfaces
    // verbatim — there is nothing more specific to say about it here.
    fetchUrlError.value = e instanceof Error ? e.message : String(e);
  } finally {
    fetchUrlLoading.value = false;
  }
}

// --- apply cover to whole album -----------------------------------------

const applyAlbumConfirming = ref(false);

const albumTrackIds = computed<number[]>(() => {
  const t = singleTrack.value;
  if (!t?.album) return [];
  const albumTracks = library.tracksForAlbum(library.keyForTrack(t));
  return albumTracks.length > 1 ? albumTracks.map((x) => x.id) : [];
});

const canApplyToAlbum = computed(
  () =>
    albumTrackIds.value.length > 0 &&
    (pendingCoverBase64.value != null || (singleTrack.value?.has_cover === true && !coverCleared.value)),
);

async function applyToWholeAlbum(): Promise<void> {
  const idsForAlbum = albumTrackIds.value;
  if (idsForAlbum.length === 0) return;
  let base64 = pendingCoverBase64.value;
  if (!base64 && singleTrack.value?.has_cover) {
    const blob = await getCoverBlob(singleTrack.value.id);
    if (blob) base64 = bytesToBase64(new Uint8Array(await blob.arrayBuffer()));
  }
  if (!base64) {
    showToast("No cover to apply");
    return;
  }
  applyAlbumConfirming.value = false;
  await metadataStore.setCover(idsForAlbum, base64, onBackupFailure);
}

// --- keyboard (Esc discards, Ctrl/Cmd+Z undo, Ctrl/Cmd+Shift+Z redo) ------

function onKeydown(e: KeyboardEvent): void {
  if (!isEditorOpen.value) return;
  if (zoomOpen.value) {
    if (e.key === "Escape") {
      e.preventDefault();
      zoomOpen.value = false;
    }
    return;
  }
  if (showWikipediaModal.value || confirmState.value != null || deleteConfirmOpen.value) return;
  const meta = e.metaKey || e.ctrlKey;
  if (meta && e.key.toLowerCase() === "z" && !e.shiftKey) {
    e.preventDefault();
    void undoLast();
    return;
  }
  if (meta && e.key.toLowerCase() === "z" && e.shiftKey) {
    e.preventDefault();
    void redoLast();
    return;
  }
  if (e.key === "Escape") {
    e.preventDefault();
    discard();
  }
}

onMounted(() => window.addEventListener("keydown", onKeydown));
onUnmounted(() => window.removeEventListener("keydown", onKeydown));

// Runs last: by now every piece of per-selection state above has been
// declared, so the immediate invocation below can safely reset all of it.
watch(ids, syncFromTracks, { immediate: true });
</script>
