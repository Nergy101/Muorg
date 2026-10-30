<template>
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
        @click.self="emit('close')"
      >
        <div
          class="glass-strong w-full max-w-sm rounded-[28px] p-6 shadow-2xl"
          role="dialog"
          aria-modal="true"
        >
          <h2 class="truncate text-title-lg text-on-surface">
            Export &ldquo;{{ playlist.name }}&rdquo;
          </h2>

          <div class="mt-4">
            <SegmentedControl
              :model-value="mode"
              :options="MODE_OPTIONS"
              @update:model-value="mode = $event as M3uMode"
            />
          </div>

          <p v-if="mode === 'stream'" class="mt-3 text-body-sm text-on-surface-variant">
            Each line is a playable link straight to this server. Works anywhere, but the link
            expires after about 8 hours.
          </p>

          <template v-else>
            <p class="mt-3 text-body-sm text-on-surface-variant">
              Each line is the track's path relative to a music root folder, for players that
              have your library mounted locally.
            </p>

            <div class="mt-3">
              <label class="mb-1 block text-label-sm text-on-surface-variant">Music root folder</label>
              <input
                v-model="musicRoot"
                type="text"
                placeholder="e.g. Music"
                class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
              />
            </div>

            <p v-if="preview" class="mt-2 truncate text-body-sm text-on-surface-variant">
              <span class="text-on-surface">Preview:</span> {{ preview }}
            </p>
            <p v-if="musicRoot.trim() && skippedCount > 0" class="mt-1 text-body-sm text-amber-500">
              {{ skippedCount }} track{{ skippedCount === 1 ? "" : "s" }} outside this folder will be skipped.
            </p>
          </template>

          <p v-if="exportError" class="mt-3 text-body-sm text-error">{{ exportError }}</p>

          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="emit('close')"
            >Cancel</button>
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
              :disabled="exporting || tracks.length === 0 || (mode === 'relative' && !musicRoot.trim())"
              @click="doExport"
            >{{ exporting ? "Exporting…" : "Export" }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import SegmentedControl from "./SegmentedControl.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { useSettingsStore } from "../stores/settings";
import { showWarning } from "../composables/useToast";
import {
  buildRelativeM3u,
  buildStreamM3u,
  downloadM3uFile,
  previewRelativePath,
} from "../composables/useM3u";
import type { M3uMode } from "../composables/useM3u";
import type { CatalogTrack, Playlist } from "../types";

/**
 * M3U export, opened from PlaylistCard's menu and PlaylistDetailView's
 * header — two modes mirroring the desktop client's PlaylistExportDialog:
 * token-authorised stream URLs (works anywhere, expires in ~8h) and
 * library-relative paths (for players with the library mounted locally).
 */
const props = defineProps<{ playlist: Playlist; open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const settings = useSettingsStore();

const MODE_OPTIONS: { value: M3uMode; label: string }[] = [
  { value: "stream", label: "Stream URLs" },
  { value: "relative", label: "Relative paths" },
];

const mode = ref<M3uMode>("stream");
const musicRoot = ref("");
const exporting = ref(false);
const exportError = ref("");
const tracks = ref<CatalogTrack[]>([]);

watch(
  () => [props.open, props.playlist.id] as const,
  async ([isOpen]) => {
    if (!isOpen) return;
    mode.value = "stream";
    musicRoot.value = settings.musicRootFolder;
    exportError.value = "";
    exporting.value = false;
    const ids = await playlistStore.loadTrackOrderForPlaylist(props.playlist.id);
    const byId = new Map(lib.tracks.map((t) => [t.id, t]));
    tracks.value = ids.map((id) => byId.get(id)).filter((t): t is CatalogTrack => t != null);
  },
  { immediate: true },
);

const skippedCount = computed(() =>
  mode.value === "relative" ? buildRelativeM3u(tracks.value, musicRoot.value).skippedCount : 0,
);
const preview = computed(() =>
  mode.value === "relative" ? previewRelativePath(tracks.value, musicRoot.value) : null,
);

async function doExport(): Promise<void> {
  exportError.value = "";
  if (tracks.value.length === 0) return;
  if (mode.value === "relative") {
    const root = musicRoot.value.trim();
    if (!root) {
      exportError.value = "Enter a music root folder name.";
      return;
    }
    if (root !== settings.musicRootFolder) settings.setMusicRootFolder(root);
    const { content, skippedCount: skipped } = buildRelativeM3u(tracks.value, root);
    downloadM3uFile(content, props.playlist.name);
    if (skipped > 0) {
      showWarning(`${skipped} track${skipped === 1 ? "" : "s"} outside "${root}" were skipped.`);
    }
    emit("close");
    return;
  }
  exporting.value = true;
  try {
    const { content } = await buildStreamM3u(tracks.value);
    downloadM3uFile(content, props.playlist.name);
    emit("close");
  } catch (e) {
    exportError.value = e instanceof Error ? e.message : String(e);
  } finally {
    exporting.value = false;
  }
}
</script>
