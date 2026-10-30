import { defineStore } from "pinia";
import { patchMetadata, getCoverBlob } from "../api/catalog";
import { api } from "../api/client";
import { bytesToBase64 } from "@shared/utils/imageData";
import { PATH_FIELD_MAP } from "@shared/utils/pathFormat";
import { recordUndoable } from "../composables/useUndo";
import { showToast, showWarning } from "../composables/useToast";
import { useLibraryStore } from "./library";
import { useSettingsStore } from "./settings";
import type { MetadataUpdate } from "@shared/api";

/** localStorage flag for the one-time "tags are written in place" warning (Q1),
 *  migrated out of the player store along with the rest of the write path. */
const TAG_WRITE_WARNED_KEY = "muorg-web-tag-write-warned";
/** Metadata saves debounce the full catalog reload by this long (Q26), so a
 *  burst of edits triggers one reload instead of one per save. */
const LIBRARY_RELOAD_DEBOUNCE_MS = 1500;

/** The nine tag fields the editor writes, in form order. */
export const METADATA_FIELD_KEYS = [
  "title",
  "artist",
  "album",
  "album_artist",
  "featuring",
  "year",
  "genre",
  "track_number",
  "disc_number",
] as const;
export type MetadataFieldKey = (typeof METADATA_FIELD_KEYS)[number];

const NUMERIC_FIELDS: Partial<Record<MetadataFieldKey, true>> = { year: true, track_number: true, disc_number: true };

/** The editor's form state. Every field is a string — number fields parse on
 *  save — so bulk mode's blank-vs-mixed distinction stays a plain `""` check. */
export type MetadataForm = Record<MetadataFieldKey, string>;

export const EMPTY_METADATA_FORM: MetadataForm = {
  title: "",
  artist: "",
  album: "",
  album_artist: "",
  featuring: "",
  year: "",
  genre: "",
  track_number: "",
  disc_number: "",
};

/**
 * Builds the `MetadataUpdate` body for a save.
 *
 * Single-track saves (`isBulk: false`) always write every field — the form
 * already holds the track's full current values, so re-sending them is a
 * no-op on the fields the user didn't touch and simplifies undo to "write the
 * snapshot back". Bulk saves only ever touch what the user explicitly edited
 * or cleared, so an untouched field is never written to other tracks' values.
 */
export function buildFieldUpdate(
  form: MetadataForm,
  touched: ReadonlySet<MetadataFieldKey>,
  cleared: ReadonlySet<MetadataFieldKey>,
  isBulk: boolean,
): MetadataUpdate {
  const update: Record<string, string | number | null> = {};
  for (const key of METADATA_FIELD_KEYS) {
    if (isBulk && !touched.has(key) && !cleared.has(key)) continue;
    if (cleared.has(key)) {
      update[key] = null;
      continue;
    }
    const raw = form[key].trim();
    if (key in NUMERIC_FIELDS) {
      if (raw === "") {
        update[key] = null;
        continue;
      }
      const n = Number(raw);
      update[key] = Number.isNaN(n) ? null : n;
    } else {
      update[key] = raw === "" ? null : raw;
    }
  }
  return update as MetadataUpdate;
}

/** camelCase path-format field name -> the form's snake_case key. */
const CAMEL_TO_FORM: Partial<Record<string, MetadataFieldKey>> = {
  title: "title",
  artist: "artist",
  album: "album",
  albumArtist: "album_artist",
  featuring: "featuring",
  genre: "genre",
  year: "year",
  trackNumber: "track_number",
  discNumber: "disc_number",
};

/**
 * Fills a form from `extractBestFromPath`'s output (Apply from path, single
 * track). Returns a new form plus which keys it touched, so the caller can
 * mark them edited without guessing from a diff.
 */
export function applyExtractedToForm(
  form: MetadataForm,
  extracted: Record<string, string>,
): { form: MetadataForm; touched: MetadataFieldKey[] } {
  const next = { ...form };
  const touched: MetadataFieldKey[] = [];
  for (const [key, value] of Object.entries(extracted)) {
    const normalized = key.toLowerCase().replace(/_/g, "");
    const field = PATH_FIELD_MAP[normalized] ?? PATH_FIELD_MAP[key.toLowerCase()];
    if (!field || field === "pictureBase64") continue;
    const formKey = CAMEL_TO_FORM[field];
    if (!formKey) continue;
    if (formKey in NUMERIC_FIELDS) {
      const n = value.trim() ? parseInt(value, 10) : NaN;
      if (Number.isNaN(n)) continue;
      next[formKey] = String(n);
    } else {
      next[formKey] = value ?? "";
    }
    touched.push(formKey);
  }
  return { form: next, touched };
}

/** The shared value across `values`, or `""` when they differ or the list is empty. */
export function sharedFormValue(values: (string | null | undefined)[]): string {
  if (values.length === 0) return "";
  const first = values[0] ?? "";
  return values.every((v) => (v ?? "") === first) ? first : "";
}

function looksLikeBackupFailure(message: string): boolean {
  const m = message.toLowerCase();
  return m.includes("backup") || m.includes("permission denied") || m.includes("read-only");
}

export const useMetadataStore = defineStore("metadata", () => {
  const settings = useSettingsStore();

  /** Q1: tags are written in place with no backup (the default stays off).
   *  Warn once on the first write, and on every bulk edit. */
  function warnTagWrite(bulk: boolean): void {
    if (bulk) {
      showWarning("Bulk edit: tags are written in place — no backup is made.");
      return;
    }
    try {
      if (localStorage.getItem(TAG_WRITE_WARNED_KEY)) return;
      localStorage.setItem(TAG_WRITE_WARNED_KEY, "1");
    } catch {
      /* storage unavailable — still show the warning */
    }
    showWarning("Tags are written in place — no backup is made.");
  }

  let libraryReloadTimer: ReturnType<typeof setTimeout> | undefined;
  /** Q26: several saves in a row debounce into one catalog reload. */
  function scheduleLibraryReload(): void {
    clearTimeout(libraryReloadTimer);
    libraryReloadTimer = setTimeout(() => {
      libraryReloadTimer = undefined;
      void useLibraryStore().loadLibrary();
    }, LIBRARY_RELOAD_DEBOUNCE_MS);
  }

  /** Writes the same `update` to every id via the batch endpoint (never backs
   *  up — the server ignores `backup_before_write` on this route) and applies
   *  the confirmed fields to the in-memory catalog immediately (Q26). */
  async function writeBatch(items: { id: number; update: MetadataUpdate }[]): Promise<void> {
    if (items.length === 0) return;
    await api.patchMetadataBatch(items.map(({ id, update }) => ({ id, ...update })));
    const lib = useLibraryStore();
    const coverIds: number[] = [];
    for (const { id, update } of items) {
      lib.applyTrackUpdate(id, update as Record<string, string | number | null>);
      if ("picture_base64" in update) coverIds.push(id);
    }
    if (coverIds.length > 0) lib.invalidateCover(coverIds);
    scheduleLibraryReload();
  }

  /** Writes `update` to one track via the single-track endpoint, which is the
   *  only route that honours `backup_before_write`. */
  async function writeOne(trackId: number, update: MetadataUpdate, backupBeforeWrite: boolean): Promise<void> {
    await patchMetadata(trackId, update, backupBeforeWrite);
    const lib = useLibraryStore();
    lib.applyTrackUpdate(trackId, update as Record<string, string | number | null>);
    if ("picture_base64" in update) lib.invalidateCover([trackId]);
    scheduleLibraryReload();
  }

  /**
   * Snapshots each track's current values for the fields `update` touches, so
   * a failed-or-not write can still be undone afterwards. A `picture_base64`
   * key in `update` additionally fetches each track's current cover bytes
   * from the server (not the local object-URL cache, which may be unloaded or
   * stale) — if that fetch fails for any track, `coverCaptureFailed` comes
   * back true and the caller must not offer undo for this write.
   */
  async function captureSnapshot(
    ids: readonly number[],
    update: MetadataUpdate,
  ): Promise<{ prevByTrack: Map<number, MetadataUpdate>; coverCaptureFailed: boolean }> {
    const lib = useLibraryStore();
    const prevByTrack = new Map<number, MetadataUpdate>();
    let coverCaptureFailed = false;
    const hasCover = "picture_base64" in update;
    for (const id of ids) {
      const t = lib.trackById.get(id);
      if (!t) continue;
      const prev: Record<string, string | number | null> = {};
      for (const key of Object.keys(update)) {
        if (key === "picture_base64") continue;
        prev[key] = (t as unknown as Record<string, string | number | null>)[key] ?? null;
      }
      if (hasCover) {
        try {
          const blob = await getCoverBlob(id);
          prev.picture_base64 = blob ? bytesToBase64(new Uint8Array(await blob.arrayBuffer())) : null;
        } catch {
          coverCaptureFailed = true;
        }
      }
      prevByTrack.set(id, prev as MetadataUpdate);
    }
    return { prevByTrack, coverCaptureFailed };
  }

  /** Records the undo step for a completed write, or — when the cover
   *  couldn't be snapshotted — just a plain confirmation toast with no Undo. */
  function finishWrite(
    label: string,
    ids: readonly number[],
    update: MetadataUpdate,
    prevByTrack: Map<number, MetadataUpdate>,
    coverCaptureFailed: boolean,
  ): void {
    if (coverCaptureFailed) {
      showToast(label);
      return;
    }
    const apply = async (byId: (id: number) => MetadataUpdate): Promise<void> => {
      if (ids.length === 1) {
        await writeOne(ids[0], byId(ids[0]), false);
      } else {
        await writeBatch(ids.map((id) => ({ id, update: byId(id) })));
      }
    };
    recordUndoable({
      label,
      undo: () => apply((id) => prevByTrack.get(id) ?? {}),
      redo: () => apply(() => update),
    });
  }

  /** Edits one track. Honours `settings.backupBeforeWrite`; when the write
   *  fails for what looks like a backup/permission reason, `onBackupFailure`
   *  (if given) is asked whether to retry without a backup. */
  async function saveSingle(
    trackId: number,
    update: MetadataUpdate,
    onBackupFailure?: (message: string) => Promise<boolean>,
  ): Promise<boolean> {
    if (Object.keys(update).length === 0) {
      showToast("No changes to save");
      return false;
    }
    warnTagWrite(false);
    const { prevByTrack, coverCaptureFailed } = await captureSnapshot([trackId], update);
    const backup = settings.backupBeforeWrite;
    try {
      await writeOne(trackId, update, backup);
    } catch (e) {
      const msg = (e as Error).message;
      if (backup && onBackupFailure && looksLikeBackupFailure(msg)) {
        const retry = await onBackupFailure(msg);
        if (!retry) {
          showToast(`Failed to save: ${msg}`);
          return false;
        }
        try {
          await writeOne(trackId, update, false);
        } catch (e2) {
          showToast(`Failed to save: ${(e2 as Error).message}`);
          return false;
        }
      } else {
        showToast(`Failed to save: ${msg}`);
        return false;
      }
    }
    finishWrite(`Saved "${useLibraryStore().trackById.get(trackId)?.title ?? "track"}"`, [trackId], update, prevByTrack, coverCaptureFailed);
    return true;
  }

  /** Edits many tracks with the same patch (only the fields the caller put in
   *  `update` — see {@link buildFieldUpdate}). Never backs up (batch route). */
  async function saveBulk(ids: readonly number[], update: MetadataUpdate): Promise<boolean> {
    if (ids.length === 0) return false;
    if (Object.keys(update).length === 0) {
      showToast("No changes to save");
      return false;
    }
    warnTagWrite(true);
    const { prevByTrack, coverCaptureFailed } = await captureSnapshot(ids, update);
    try {
      await writeBatch(ids.map((id) => ({ id, update })));
    } catch (e) {
      showToast(`Failed to save: ${(e as Error).message}`);
      return false;
    }
    finishWrite(`Edited ${ids.length} track${ids.length === 1 ? "" : "s"}`, ids, update, prevByTrack, coverCaptureFailed);
    return true;
  }

  /** Edits many tracks with a different patch each (Apply from path, bulk).
   *  One undo step covers the whole batch. */
  async function saveBulkCustom(items: { id: number; update: MetadataUpdate }[], label: string): Promise<boolean> {
    const withChanges = items.filter((i) => Object.keys(i.update).length > 0);
    if (withChanges.length === 0) {
      showToast("No changes to save");
      return false;
    }
    warnTagWrite(true);
    const lib = useLibraryStore();
    const prevByTrack = new Map<number, MetadataUpdate>();
    for (const { id, update } of withChanges) {
      const t = lib.trackById.get(id);
      if (!t) continue;
      const prev: Record<string, string | number | null> = {};
      for (const key of Object.keys(update)) {
        prev[key] = (t as unknown as Record<string, string | number | null>)[key] ?? null;
      }
      prevByTrack.set(id, prev as MetadataUpdate);
    }
    try {
      await writeBatch(withChanges);
    } catch (e) {
      showToast(`Failed to save: ${(e as Error).message}`);
      return false;
    }
    recordUndoable({
      label,
      undo: () => writeBatch(withChanges.map(({ id }) => ({ id, update: prevByTrack.get(id) ?? {} }))),
      redo: () => writeBatch(withChanges),
    });
    return true;
  }

  /** Sets, clears (`null`) or applies-to-album a cover. One or many ids. */
  async function setCover(
    ids: readonly number[],
    base64: string | null,
    onBackupFailure?: (message: string) => Promise<boolean>,
  ): Promise<boolean> {
    if (ids.length === 0) return false;
    const update: MetadataUpdate = { picture_base64: base64 };
    const label =
      base64 == null
        ? ids.length === 1
          ? "Cleared cover"
          : `Cleared cover on ${ids.length} tracks`
        : ids.length === 1
          ? "Set cover"
          : `Set cover on ${ids.length} tracks`;
    warnTagWrite(ids.length > 1);
    const { prevByTrack, coverCaptureFailed } = await captureSnapshot(ids, update);
    const backup = ids.length === 1 && settings.backupBeforeWrite;
    try {
      if (ids.length === 1) {
        await writeOne(ids[0], update, backup);
      } else {
        await writeBatch(ids.map((id) => ({ id, update })));
      }
    } catch (e) {
      const msg = (e as Error).message;
      if (backup && onBackupFailure && looksLikeBackupFailure(msg)) {
        const retry = await onBackupFailure(msg);
        if (!retry) {
          showToast(`Failed to save: ${msg}`);
          return false;
        }
        try {
          await writeOne(ids[0], update, false);
        } catch (e2) {
          showToast(`Failed to save: ${(e2 as Error).message}`);
          return false;
        }
      } else {
        showToast(`Failed to save: ${msg}`);
        return false;
      }
    }
    finishWrite(label, ids, update, prevByTrack, coverCaptureFailed);
    return true;
  }

  /** Restores a track's file from its latest server-side backup. Not itself
   *  undoable — the restore IS the undo of whatever broke the tags. */
  async function restoreBackup(trackId: number): Promise<boolean> {
    try {
      await api.restoreFromBackup(trackId);
    } catch (e) {
      showToast(`Restore failed: ${(e as Error).message}`);
      return false;
    }
    const lib = useLibraryStore();
    lib.invalidateCover([trackId]);
    await lib.loadLibrary();
    showToast("Restored from backup");
    return true;
  }

  /** Moves a track's file. Caller must pre-flight collisions and confirm an
   *  overwrite — this just performs the move the caller already decided on. */
  async function renameFile(trackId: number, newPath: string, overwrite = false): Promise<boolean> {
    try {
      await api.renameTrackFile(trackId, newPath, overwrite);
    } catch (e) {
      showToast(`Rename failed: ${(e as Error).message}`);
      return false;
    }
    const lib = useLibraryStore();
    lib.applyTrackUpdate(trackId, { path: newPath });
    await lib.loadLibrary();
    showToast("Renamed");
    return true;
  }

  return {
    saveSingle,
    saveBulk,
    saveBulkCustom,
    setCover,
    restoreBackup,
    renameFile,
  };
});
