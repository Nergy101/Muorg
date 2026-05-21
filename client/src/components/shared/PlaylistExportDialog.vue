<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { open } from "@tauri-apps/plugin-dialog";
import * as playlistApi from "../../api/playlists";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { CatalogTrack, Playlist } from "../../types";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const props = defineProps<{
  playlist: Playlist;
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const catalogStore = useCatalogStore();
const settingsStore = useSettingsStore();

const outputDir = ref("");
const musicRootFolder = ref("");
const tracks = ref<CatalogTrack[]>([]);
const exportError = ref("");
const exporting = ref(false);

/** Default when setting is empty: single root folder name, else "Music". */
function defaultMusicRoot(): string {
  const stored = settingsStore.musicRootFolder.trim();
  if (stored) return stored;
  if (catalogStore.roots.length === 1) {
    const segments = catalogStore.roots[0].split(/[/\\]/).filter(Boolean);
    return segments[segments.length - 1] ?? "Music";
  }
  return "Music";
}

/** Load playlist track IDs in order, then resolve to full tracks from catalog. */
async function loadTracks() {
  const ids = await playlistApi.getPlaylistTracks(props.playlist.id);
  const idToTrack = new Map(catalogStore.tracks.map((t) => [t.id, t]));
  tracks.value = ids.map((id) => idToTrack.get(id)).filter((t): t is CatalogTrack => t != null);
}

watch(
  () => [props.open, props.playlist.id] as const,
  async ([isOpen]) => {
    if (!isOpen) return;
    musicRootFolder.value = defaultMusicRoot();
    outputDir.value = "";
    exportError.value = "";
    await loadTracks();
  },
  { immediate: true }
);

function computeRelativePath(track: CatalogTrack, rootName: string): string {
  const sep = track.path.includes("/") ? "/" : "\\";
  const parts = track.path.split(sep);
  const idx = parts.findIndex((p) => p === rootName);
  if (idx === -1) return track.path;
  return "../" + parts.slice(idx).join("/");
}

const pathPreview = computed(() => {
  const root = musicRootFolder.value.trim();
  if (!root || tracks.value.length === 0) return "";
  const first = tracks.value[0];
  return computeRelativePath(first, root);
});

async function browseOutputDir() {
  const selected = await open({
    directory: true,
    multiple: false,
  });
  if (selected) outputDir.value = Array.isArray(selected) ? selected[0] : selected;
}

function sanitizeFilename(name: string): string {
  return name.replace(/[/\\?*:|"<>]/g, "_").trim() || "playlist";
}

function buildM3uContent(): string {
  const root = musicRootFolder.value.trim();
  const lines: string[] = ["#EXTM3U"];
  for (const track of tracks.value) {
    const duration = track.duration_secs ?? -1;
    const artist = track.artist ?? "Unknown";
    const title = track.title ?? track.path.split(/[/\\]/).pop() ?? "Unknown";
    lines.push(`#EXTINF:${Math.round(duration)},${artist} - ${title}`);
    lines.push(computeRelativePath(track, root || "Music"));
  }
  return lines.join("\n");
}

async function doExport() {
  exportError.value = "";
  if (!outputDir.value.trim()) {
    exportError.value = "Please select an output folder.";
    return;
  }
  exporting.value = true;
  try {
    const content = buildM3uContent();
    const filename = sanitizeFilename(props.playlist.name) + ".m3u";
    // Trigger a browser download instead of writing to a server path.
    const blob = new Blob([content], { type: "audio/x-mpegurl" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    emit("close");
  } catch (e) {
    exportError.value = e instanceof Error ? e.message : String(e);
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[500] flex items-center justify-center bg-black/60"
      @mousedown.self="emit('close')"
    >
      <div
        class="w-[460px] rounded-xl border border-stone-600 bg-stone-800 p-5 shadow-2xl"
        @click.stop
      >
        <div class="mb-3 flex items-center gap-2">
          <FeatherIcon name="download" class="h-4 w-4 shrink-0 text-stone-400" />
          <h3 class="text-sm font-semibold text-stone-100">
            Export: "{{ playlist.name }}"
          </h3>
        </div>

        <div class="space-y-3 text-sm">
          <div>
            <label class="mb-0.5 block text-xs font-medium text-stone-400">Output folder</label>
            <div class="flex gap-2">
              <input
                :value="outputDir"
                type="text"
                readonly
                placeholder="Select a folder…"
                class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-700/60 px-2.5 py-1.5 text-xs text-stone-300 placeholder:text-stone-500"
              />
              <button
                type="button"
                class="shrink-0 rounded border border-stone-600 px-2.5 py-1.5 text-xs text-stone-300 hover:bg-stone-700"
                @click="browseOutputDir"
              >
                Browse
              </button>
            </div>
          </div>

          <div>
            <label class="mb-0.5 block text-xs font-medium text-stone-400">Music root folder</label>
            <input
              v-model="musicRootFolder"
              type="text"
              placeholder="e.g. Music"
              class="w-full rounded border border-stone-600 bg-stone-800 px-2.5 py-1.5 text-xs text-stone-200 placeholder:text-stone-500 focus:border-stone-500 focus:outline-none focus:ring-1 focus:ring-stone-500"
            />
          </div>

          <p v-if="pathPreview" class="flex items-start gap-2 text-xs text-stone-500">
            <FeatherIcon name="info" class="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              Paths in the .m3u will look like:
              <code class="mt-1 block break-all rounded bg-stone-700/80 px-1.5 py-0.5 font-mono text-[11px] text-stone-400">{{ pathPreview }}</code>
            </span>
          </p>

          <p v-if="exportError" class="text-xs text-amber-400">
            {{ exportError }}
          </p>
        </div>

        <div class="mt-5 flex justify-end gap-2">
          <button
            type="button"
            class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="emit('close')"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded bg-stone-600 px-3 py-1.5 text-sm text-stone-100 hover:bg-stone-500 disabled:opacity-50"
            :disabled="exporting"
            @click="doExport"
          >
            {{ exporting ? "Exporting…" : "Export" }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
