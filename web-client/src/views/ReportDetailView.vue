<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
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

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">{{ title }}</h1>
        <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>

      <button
        v-if="tracks.length > 0"
        type="button"
        class="flex h-9 shrink-0 items-center gap-1 rounded-full px-3 text-label-lg text-primary transition-colors hover:bg-surface-container/70"
        @click="playAll"
      >
        <MageIcon name="play" class="h-4 w-4" />
        <span class="hidden sm:inline">Play</span>
      </button>

      <div v-if="tracks.length > 0" ref="menuRef" class="relative">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container/70"
          aria-label="Report actions"
          @click="menuOpen = !menuOpen"
        >
          <MageIcon name="dots" class="h-5 w-5" />
        </button>
        <div
          v-if="menuOpen"
          class="absolute right-0 top-full z-20 mt-1 min-w-[13rem] rounded-xl bg-surface-container py-1 shadow-xl"
        >
          <button
            type="button"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-body-md text-on-surface hover:bg-surface/50"
            @click="openSaveAsPlaylist"
          >
            <MageIcon name="playlist-add" class="h-4 w-4 shrink-0" />
            <span>Save as playlist</span>
          </button>
          <button
            v-if="kind === 'missing_metadata'"
            type="button"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-body-md text-on-surface disabled:opacity-40"
            :class="canApplyFromPath ? 'hover:bg-surface/50' : ''"
            :disabled="!canApplyFromPath"
            @click="openApplyFromPathConfirm"
          >
            <MageIcon name="edit" class="h-4 w-4 shrink-0" />
            <span class="min-w-0 flex-1 truncate">Apply from path</span>
            <span v-if="pathFormatTemplatesSet" class="shrink-0 text-label-sm text-on-surface-variant">
              {{ applyFromPathMatches.length }}/{{ tracks.length }}
            </span>
          </button>
          <button
            v-if="kind === 'missing_metadata'"
            type="button"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-body-md text-on-surface disabled:opacity-40"
            :class="!autoTagProgress ? 'hover:bg-surface/50' : ''"
            :disabled="!!autoTagProgress"
            @click="runAutoTagAll"
          >
            <MageIcon name="zap" class="h-4 w-4 shrink-0" />
            <span>Auto-tag all</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Auto-tag progress / review banners -->
    <div
      v-if="autoTagProgress"
      class="content-col flex shrink-0 items-center gap-3 px-4 py-2 text-body-sm text-on-surface-variant"
    >
      <MageIcon name="refresh" class="h-4 w-4 shrink-0 animate-spin" />
      <span class="min-w-0 flex-1 truncate">
        Auto-tagging {{ autoTagProgress.current }}/{{ autoTagProgress.total }}…
      </span>
      <button type="button" class="shrink-0 text-label-lg text-error" @click="cancelAutoTag">Cancel</button>
    </div>
    <div
      v-else-if="autoTagReviewIds && autoTagReviewIds.length > 0"
      class="content-col flex shrink-0 items-center gap-3 px-4 py-2 text-body-sm text-on-surface-variant"
    >
      <MageIcon name="information-circle" class="h-4 w-4 shrink-0 text-tertiary" />
      <span class="min-w-0 flex-1 truncate">
        {{ autoTagReviewIds.length }} track{{ autoTagReviewIds.length === 1 ? "" : "s" }} need manual review
      </span>
      <button type="button" class="shrink-0 text-label-lg text-primary" @click="reviewAutoTagManually">Review</button>
      <button type="button" class="shrink-0 text-label-lg text-on-surface-variant" @click="autoTagReviewIds = null">
        Dismiss
      </button>
    </div>

    <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div
        v-if="tracks.length === 0"
        class="flex flex-col items-center gap-2 px-6 py-12 text-center"
      >
        <MageIcon name="check" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">
          {{ lib.tracks.length === 0 ? "The catalog is still empty." : "Nothing to report here." }}
        </span>
      </div>

      <template v-else>
        <!-- Duplicates read as groups, not a flat list: seeing the copies of
             one recording together is the whole point of the report. -->
        <template v-if="kind === 'duplicates'">
          <div v-for="(group, i) in groups" :key="i" class="pb-2">
            <p class="px-4 pb-1 pt-3 text-label-sm uppercase tracking-[0.8px] text-primary">
              {{ group.length }} copies
            </p>
            <div v-for="track in group" :key="track.id" class="flex items-center">
              <TrackListRow
                class="min-w-0 flex-1"
                :track="track"
                :is-playing="player.currentTrack?.id === track.id"
                show-delete
                :selectable="selection.selecting"
                :selected="selection.has(track.id)"
                @play="player.playTrack(track, tracks)"
                @select="(mods: SelectModifiers) => onSelect(track, mods)"
                @actions="sheetTrack = track"
                @delete="pendingDelete = track"
              />
              <button
                type="button"
                class="-ml-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container/70"
                aria-label="Jump to album"
                @click.stop="jumpToTrack(track)"
              >
                <MageIcon name="compact-disk" class="h-5 w-5" />
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-for="track in tracks" :key="track.id" class="flex items-center">
            <TrackListRow
              class="min-w-0 flex-1"
              :track="track"
              :is-playing="player.currentTrack?.id === track.id"
              :selectable="selection.selecting"
              :selected="selection.has(track.id)"
              @play="player.playTrack(track, tracks)"
              @select="(mods: SelectModifiers) => onSelect(track, mods)"
              @actions="sheetTrack = track"
            />
            <button
              type="button"
              class="-ml-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container/70"
              aria-label="Jump to album"
              @click.stop="jumpToTrack(track)"
            >
              <MageIcon name="compact-disk" class="h-5 w-5" />
            </button>
          </div>
        </template>
      </template>
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
    />

    <ConfirmDialog
      :open="pendingDelete !== null"
      title="Delete from server?"
      :message="deleteMessage"
      confirm-label="Delete"
      danger
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />

    <ConfirmDialog
      :open="applyFromPathConfirmOpen"
      title="Apply path format?"
      :message="applyFromPathMessage"
      confirm-label="Apply"
      @confirm="confirmApplyFromPath"
      @cancel="applyFromPathConfirmOpen = false"
    />

    <PlaylistFormDialog
      :open="saveAsPlaylistOpen"
      title="Save report as playlist"
      confirm-label="Save"
      :initial-name="defaultPlaylistName"
      @confirm="confirmSaveAsPlaylist"
      @cancel="saveAsPlaylistOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import PlaylistFormDialog from "../components/PlaylistFormDialog.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useSelectionStore, type SelectModifiers } from "../stores/selection";
import { usePlaylistStore } from "../stores/playlists";
import { provideVisibleIds } from "../composables/useSelectableList";
import { deleteTrack, type MetadataUpdate } from "../api/catalog";
import { api } from "../api/client";
import { showToast, showWarning } from "../composables/useToast";
import { recordUndoable } from "../composables/useUndo";
import { openMetadataEditor } from "../composables/useMetadataEditor";
import { rankCandidates } from "../composables/useAutoTag";
import { extractBestFromPath, buildUpdateFromExtracted } from "@shared/utils/pathFormat";
import {
  REPORT_KINDS,
  REPORT_LABELS,
  duplicateCount,
  duplicateGroups,
  runReport,
  tracksMissingMetadata,
  type MissingMetadataField,
  type ReportKind,
} from "@shared/reports";
import type { CatalogTrack } from "../types";

const props = defineProps<{ kind: string }>();

const router = useRouter();
const route = useRoute();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const selection = useSelectionStore();
const playlistStore = usePlaylistStore();

const sheetTrack = ref<CatalogTrack | null>(null);
const pendingDelete = ref<CatalogTrack | null>(null);
const deleting = ref(false);

const deleteMessage = computed(() => {
  const t = pendingDelete.value;
  if (!t) return "";
  const name = t.title ?? t.path.split("/").pop() ?? `track ${t.id}`;
  return `"${name}" will be permanently deleted from the server, including its file. This cannot be undone.`;
});

async function confirmDelete(): Promise<void> {
  const track = pendingDelete.value;
  if (!track || deleting.value) return;
  deleting.value = true;
  try {
    await deleteTrack(track.id);
    lib.removeTrack(track.id);
    showToast(`Deleted ${track.title ?? "track"} from the server`);
  } catch (err) {
    showWarning(`Delete failed: ${err instanceof Error ? err.message : String(err)}`);
  } finally {
    deleting.value = false;
    pendingDelete.value = null;
  }
}

/** An unknown `:kind` in the URL falls back rather than rendering nothing. */
const kind = computed<ReportKind>(() =>
  (REPORT_KINDS as readonly string[]).includes(props.kind)
    ? (props.kind as ReportKind)
    : "missing_metadata",
);

const MISSING_FIELD_LABELS: Record<MissingMetadataField, string> = {
  title: "Title",
  artist: "Artist",
  album: "Album",
  album_artist: "Album artist",
  year: "Year",
  genre: "Genre",
  track_number: "Track #",
  disc_number: "Disc #",
  rating: "Rating",
  has_cover: "Album cover",
};

/**
 * A single-field override for the missing-metadata report, reached from the
 * Stats page's Metadata Health chart (`?field=genre`) without touching the
 * app-wide `settings.missingMetadataFields` choice.
 */
const fieldOverride = computed<MissingMetadataField | null>(() => {
  const f = route.query.field;
  const value = typeof f === "string" ? f : null;
  return value && (Object.keys(MISSING_FIELD_LABELS) as string[]).includes(value)
    ? (value as MissingMetadataField)
    : null;
});

const title = computed(() => {
  if (kind.value === "missing_metadata" && fieldOverride.value) {
    return `Missing ${MISSING_FIELD_LABELS[fieldOverride.value]}`;
  }
  return REPORT_LABELS[kind.value];
});

const tracks = computed(() => {
  if (kind.value === "missing_metadata") {
    return tracksMissingMetadata(
      lib.tracks,
      fieldOverride.value ? [fieldOverride.value] : settings.missingMetadataFields,
    );
  }
  return runReport(kind.value, lib.tracks);
});
const groups = computed(() => duplicateGroups(lib.tracks));

const metaLine = computed(() => {
  if (kind.value === "duplicates") {
    const extra = duplicateCount(lib.tracks);
    return `${extra} redundant cop${extra === 1 ? "y" : "ies"} across ${groups.value.length} recording${groups.value.length === 1 ? "" : "s"}`;
  }
  const n = tracks.value.length;
  return `${n} track${n === 1 ? "" : "s"}`;
});

function playAll(): void {
  const first = tracks.value[0];
  if (first) player.playTrack(first, tracks.value);
}

function jumpToTrack(track: CatalogTrack): void {
  router.push({ name: "album", params: { albumKey: lib.keyForTrack(track) } });
}

// ── Selection ────────────────────────────────────────────────────────────

/** `tracks` is already in render order for every kind, including duplicates
 *  (its groups flatten the same way `runReport("duplicates", …)` does). */
const orderedIds = computed(() => tracks.value.map((t) => t.id));
provideVisibleIds(() => orderedIds.value);

function onSelect(track: CatalogTrack, mods: SelectModifiers): void {
  const consumed = selection.click(track.id, orderedIds.value, mods);
  if (!consumed) player.playTrack(track, tracks.value);
}

// ── Report actions menu ──────────────────────────────────────────────────

const menuOpen = ref(false);
const menuRef = ref<HTMLElement | null>(null);

function onDocumentClick(e: MouseEvent): void {
  if (!menuOpen.value) return;
  const el = menuRef.value;
  if (el && !el.contains(e.target as Node)) menuOpen.value = false;
}
onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));

/** The fields a batch write actually touched, snapshotted from the track
 *  *before* the write — exactly what the undo entry needs to restore. */
function snapshotFields(track: CatalogTrack, keys: readonly string[]): MetadataUpdate {
  const out: MetadataUpdate = {};
  for (const key of keys) {
    (out as Record<string, unknown>)[key] = (track as unknown as Record<string, unknown>)[key] ?? null;
  }
  return out;
}

// ── Save as playlist ─────────────────────────────────────────────────────

const saveAsPlaylistOpen = ref(false);
const defaultPlaylistName = computed(() => `${title.value} audit`);

function openSaveAsPlaylist(): void {
  menuOpen.value = false;
  if (tracks.value.length === 0) return;
  saveAsPlaylistOpen.value = true;
}

async function confirmSaveAsPlaylist(name: string): Promise<void> {
  saveAsPlaylistOpen.value = false;
  const trackIds = orderedIds.value;
  if (trackIds.length === 0) return;
  try {
    const playlist = await playlistStore.createPlaylist(name);
    await playlistStore.addTracks(playlist.id, trackIds);
    showToast(`Saved "${name}" with ${trackIds.length} track${trackIds.length === 1 ? "" : "s"}`);
  } catch (e) {
    showToast(`Couldn't save playlist: ${(e as Error).message}`);
  }
}

// ── Apply from path ──────────────────────────────────────────────────────

const pathFormatTemplatesSet = computed(() => settings.pathFormatTemplates.some((t) => t.trim()));

interface PathMatch {
  track: CatalogTrack;
  update: MetadataUpdate;
}

const applyFromPathMatches = computed<PathMatch[]>(() => {
  if (kind.value !== "missing_metadata" || !pathFormatTemplatesSet.value) return [];
  const templates = settings.pathFormatTemplates;
  return tracks.value
    .map((track) => {
      const extracted = extractBestFromPath(templates, track.path);
      if (!extracted) return null;
      const update = buildUpdateFromExtracted(extracted);
      return Object.keys(update).length > 0 ? { track, update } : null;
    })
    .filter((x): x is PathMatch => x !== null);
});

const canApplyFromPath = computed(() => applyFromPathMatches.value.length > 0);

const applyFromPathConfirmOpen = ref(false);
const applyFromPathBusy = ref(false);

const applyFromPathMessage = computed(() => {
  const n = applyFromPathMatches.value.length;
  const backupNote = settings.backupBeforeWrite ? "" : " No backup copy is made for a batch write.";
  return `This rewrites tags from the file path on ${n} track${n === 1 ? "" : "s"}.${backupNote} You can undo it from the toast afterwards.`;
});

function openApplyFromPathConfirm(): void {
  menuOpen.value = false;
  if (!canApplyFromPath.value) return;
  applyFromPathConfirmOpen.value = true;
}

async function confirmApplyFromPath(): Promise<void> {
  applyFromPathConfirmOpen.value = false;
  const matches = applyFromPathMatches.value;
  if (matches.length === 0 || applyFromPathBusy.value) return;
  applyFromPathBusy.value = true;
  showWarning(`Writing tags to ${matches.length} track${matches.length === 1 ? "" : "s"} from their file paths.`);
  const before = new Map(matches.map((m) => [m.track.id, snapshotFields(m.track, Object.keys(m.update))]));
  try {
    await api.patchMetadataBatch(matches.map((m) => ({ id: m.track.id, ...m.update })));
    for (const m of matches) lib.applyTrackUpdate(m.track.id, m.update as Record<string, string | number | null>);
    recordUndoable({
      label: `Applied path format to ${matches.length} track${matches.length === 1 ? "" : "s"}`,
      undo: async () => {
        const items = matches.map((m) => ({ id: m.track.id, ...(before.get(m.track.id) ?? {}) }));
        await api.patchMetadataBatch(items);
        for (const m of matches) {
          lib.applyTrackUpdate(m.track.id, (before.get(m.track.id) ?? {}) as Record<string, string | number | null>);
        }
      },
      redo: async () => {
        await api.patchMetadataBatch(matches.map((m) => ({ id: m.track.id, ...m.update })));
        for (const m of matches) lib.applyTrackUpdate(m.track.id, m.update as Record<string, string | number | null>);
      },
    });
  } catch (e) {
    showToast(`Apply from path failed: ${(e as Error).message}`);
  } finally {
    applyFromPathBusy.value = false;
  }
}

// ── Auto-tag all ─────────────────────────────────────────────────────────

const AUTO_TAG_CONCURRENCY = 3;
/** Matches the desktop threshold: only an unambiguous match is auto-accepted. */
const AUTO_TAG_CONFIDENCE = 0.9;
/** A small per-request stagger keeps a report-sized batch friendly to the
 *  MusicBrainz lookup rate limit even with several workers in flight. */
const AUTO_TAG_STAGGER_MS = 250;

const autoTagProgress = ref<{ current: number; total: number } | null>(null);
const autoTagCancelRequested = ref(false);
const autoTagReviewIds = ref<number[] | null>(null);

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function cancelAutoTag(): void {
  autoTagCancelRequested.value = true;
}

function reviewAutoTagManually(): void {
  const ids = autoTagReviewIds.value;
  if (ids && ids.length > 0) openMetadataEditor(ids);
}

async function runAutoTagAll(): Promise<void> {
  menuOpen.value = false;
  if (kind.value !== "missing_metadata" || autoTagProgress.value) return;
  const candidates = tracks.value;
  if (candidates.length === 0) return;

  autoTagCancelRequested.value = false;
  autoTagReviewIds.value = null;
  const total = candidates.length;
  autoTagProgress.value = { current: 0, total };

  const applied: PathMatch[] = [];
  const needsReview: CatalogTrack[] = [];
  let done = 0;
  let cursor = 0;

  async function worker(): Promise<void> {
    while (!autoTagCancelRequested.value) {
      const i = cursor++;
      if (i >= candidates.length) return;
      const track = candidates[i];
      try {
        const results = rankCandidates(
          await api.autoTagSuggestions(track.id, {
            artist: track.artist,
            title: track.title,
            album: track.album,
            duration_secs: track.duration_secs,
          }),
        );
        const best = results[0];
        if (best && best.confidence >= AUTO_TAG_CONFIDENCE) {
          const update: MetadataUpdate = {};
          if (!track.title?.trim()) update.title = best.title;
          if (!track.artist?.trim()) update.artist = best.artist;
          if (!track.album?.trim() && best.album) update.album = best.album;
          if (!track.album_artist?.trim() && best.album_artist) update.album_artist = best.album_artist;
          if (track.year == null && best.year != null) update.year = best.year;
          if (Object.keys(update).length > 0) applied.push({ track, update });
          else needsReview.push(track);
        } else {
          needsReview.push(track);
        }
      } catch {
        needsReview.push(track);
      } finally {
        done++;
        autoTagProgress.value = { current: done, total };
      }
      await sleep(AUTO_TAG_STAGGER_MS);
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(AUTO_TAG_CONCURRENCY, candidates.length) }, () => worker()),
  );
  autoTagProgress.value = null;

  if (autoTagCancelRequested.value) {
    showToast("Auto-tag cancelled");
    return;
  }

  if (applied.length > 0) {
    const before = new Map(applied.map((a) => [a.track.id, snapshotFields(a.track, Object.keys(a.update))]));
    try {
      await api.patchMetadataBatch(applied.map((a) => ({ id: a.track.id, ...a.update })));
      for (const a of applied) lib.applyTrackUpdate(a.track.id, a.update as Record<string, string | number | null>);
      recordUndoable({
        label: `Auto-tagged ${applied.length} track${applied.length === 1 ? "" : "s"}`,
        undo: async () => {
          const items = applied.map((a) => ({ id: a.track.id, ...(before.get(a.track.id) ?? {}) }));
          await api.patchMetadataBatch(items);
          for (const a of applied) {
            lib.applyTrackUpdate(a.track.id, (before.get(a.track.id) ?? {}) as Record<string, string | number | null>);
          }
        },
        redo: async () => {
          await api.patchMetadataBatch(applied.map((a) => ({ id: a.track.id, ...a.update })));
          for (const a of applied) lib.applyTrackUpdate(a.track.id, a.update as Record<string, string | number | null>);
        },
      });
    } catch (e) {
      showToast(`Auto-tag write failed: ${(e as Error).message}`);
    }
  }

  autoTagReviewIds.value = needsReview.map((t) => t.id);
  const summary = applied.length > 0
    ? `Auto-tagged ${applied.length} of ${total} track${total === 1 ? "" : "s"}`
    : `No confident matches for ${total} track${total === 1 ? "" : "s"}`;
  showToast(needsReview.length > 0 ? `${summary}; ${needsReview.length} need review` : summary);
}
</script>
