<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef, watch } from "vue";
import { storeToRefs } from "pinia";
import { appConfigDir, join } from "@tauri-apps/api/path";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { open as openShell } from "@tauri-apps/plugin-shell";
import type { Update } from "@tauri-apps/plugin-updater";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { ThemeId, DefaultGroupBy, TableDensity, MissingMetadataField } from "../../stores/settings";
import { extractMetadataFromPath } from "../../utils/pathFormat";
import { DEFAULT_PATH_FORMAT_EXAMPLE_PATH } from "../../stores/settings";

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const {
  defaultGroupsExpanded,
  theme,
  defaultGroupBy,
  autoplayOnSelect,
  continuousPlayback,
  playbarShowAlbumInMarquee,
  playbarDisableMarquee,
  navWrap,
  navFocusFollowsMouse,
  tableDensity,
  tableColAlbumArt,
  tableColYear,
  tableColDuration,
  tableColFormat,
  tableColPath,
  missingMetadataFields,
  groupHeaderAlbumArt,
  hideWikipediaCoverSearch,
  pathFormatTemplate,
  pathFormatExamplePath,
  openSettingsAtTab,
} = storeToRefs(settingsStore);

type SettingsTabId = "general" | "theme" | "playback" | "keyboard" | "table" | "reports" | "smart_suggestions";
const settingsTab = ref<SettingsTabId>("general");
const settingsTabs: { id: SettingsTabId; label: string }[] = [
  { id: "general", label: "General" },
  { id: "theme", label: "Theme" },
  { id: "playback", label: "Playback" },
  { id: "keyboard", label: "Keyboard" },
  { id: "table", label: "Layout" },
  { id: "reports", label: "Reports" },
  { id: "smart_suggestions", label: "Smart Suggestions" },
];

const themeOptions: { value: ThemeId; label: string; description: string; swatchClass: string }[] = [
  {
    value: "auto",
    label: "Auto",
    description: "Follow system appearance (dark or light).",
    swatchClass: "from-stone-900 via-stone-700 to-stone-400",
  },
  {
    value: "dark",
    label: "Dark",
    description: "High-contrast dark library view.",
    swatchClass: "from-stone-900 via-stone-800 to-stone-600",
  },
  {
    value: "light",
    label: "Light",
    description: "Bright, paper-like library theme.",
    swatchClass: "from-stone-50 via-stone-100 to-stone-300",
  },
  {
    value: "orkish",
    label: "Orkish",
    description: "Parchment-like greenish light theme.",
    swatchClass: "from-lime-800 via-lime-500 to-amber-300",
  },
  {
    value: "doom",
    label: "DOOM",
    description: "High-contrast crimson terminal theme.",
    swatchClass: "from-red-900 via-amber-700 to-yellow-400",
  },
];

const defaultGroupByOptions: { value: DefaultGroupBy; label: string }[] = [
  { value: "album", label: "By album" },
  { value: "artist", label: "By artist" },
  { value: "none", label: "No grouping" },
];

const tableDensityOptions: { value: TableDensity; label: string; description: string }[] = [
  { value: "comfortable", label: "Comfortable", description: "More spacing between rows; easier to scan." },
  { value: "compact", label: "Compact", description: "Tighter rows; more tracks visible at once." },
];

const missingMetadataFieldOptions: { value: MissingMetadataField; label: string }[] = [
  { value: "title", label: "Title" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
  { value: "album_artist", label: "Album artist" },
  { value: "year", label: "Year" },
  { value: "genre", label: "Genre" },
  { value: "track_number", label: "Track #" },
  { value: "disc_number", label: "Disc #" },
];

const pathFormatExamples = [
  "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist>/Albums/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<AlbumArtist>/<Album>/<DiscNumber>-<TrackNumber> <TrackTitle>.<Format>",
  "<Genre>/<Artist>/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
];

const pathFormatExamplePaths = [
  "/music/Linkin Park/Meteora/01 - Foreword.flac",
  "/library/Linkin Park/Albums/2003 - Meteora/04 - Faint.flac",
  "/music/Linkin Park/Meteora/1-04 Faint.flac",
  "/music/Rock/Linkin Park/2003 - Meteora/04 - Faint.flac",
  "/music/Linkin Park - Meteora/04 - Faint.flac",
];

const pathFormatExampleExtracted = computed(() => {
  const fmt = pathFormatTemplate.value?.trim();
  const examplePath = pathFormatExamplePath.value?.trim();
  if (!fmt || !examplePath) return null;
  return extractMetadataFromPath(fmt, examplePath);
});

const updateCheckStatus = ref<"idle" | "checking" | "up-to-date" | "available" | "error">("idle");
const availableUpdate = shallowRef<Update | null>(null);
const updateError = ref<string | null>(null);
const updateDownloadProgress = ref<number | null>(null);
const showUpdateCompleteModal = ref(false);
const updateCompleteVersion = ref("");

const GITHUB_RELEASE_BASE = "https://github.com/Nergy101/Muorg/releases";

async function checkForUpdates() {
  updateCheckStatus.value = "checking";
  updateError.value = null;
  availableUpdate.value = null;
  try {
    const update = await check();
    if (update) {
      availableUpdate.value = update;
      updateCheckStatus.value = "available";
    } else {
      updateCheckStatus.value = "up-to-date";
    }
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateCheckStatus.value = "error";
  }
}

async function installUpdate() {
  const update = availableUpdate.value;
  if (!update) return;
  updateDownloadProgress.value = 0;
  let downloaded = 0;
  let contentLength: number | null = null;
  try {
    await update.downloadAndInstall((event) => {
      if (event.event === "Started" && event.data.contentLength != null) {
        contentLength = event.data.contentLength;
      } else if (event.event === "Progress") {
        downloaded += event.data.chunkLength;
        if (contentLength != null && contentLength > 0) {
          updateDownloadProgress.value = Math.min(100, Math.round((downloaded / contentLength) * 100));
        }
      } else if (event.event === "Finished") {
        updateDownloadProgress.value = 100;
      }
    });
    updateDownloadProgress.value = null;
    updateCompleteVersion.value = update.version;
    showUpdateCompleteModal.value = true;
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateDownloadProgress.value = null;
    updateCheckStatus.value = "error";
  }
}

function closeUpdateCompleteModal() {
  showUpdateCompleteModal.value = false;
  updateCompleteVersion.value = "";
}

function openReleaseUrl(url: string) {
  openShell(url);
}

async function restartAfterUpdate() {
  closeUpdateCompleteModal();
  await relaunch();
}

const settingsFilePath = ref<string | null>(null);
onMounted(async () => {
  try {
    const dir = await appConfigDir();
    settingsFilePath.value = await join(dir, "settings.yml");
  } catch {
    settingsFilePath.value = null;
  }
});

async function copyPathToClipboard(path: string) {
  try {
    await navigator.clipboard.writeText(path);
  } catch {
  }
}

function setDefaultGroupBy(value: DefaultGroupBy) {
  settingsStore.setDefaultGroupBy(value);
  store.groupBy = value;
}

function setDefaultGroupsExpanded(value: boolean) {
  settingsStore.setDefaultGroupsExpanded(value);
}

function close() {
  emit("update:open", false);
}

function onSettingsKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") close();
}

const settingsModalRef = ref<HTMLDivElement | null>(null);

watch(
  () => props.open,
  async (open) => {
    if (!open) return;
    await nextTick();
    settingsModalRef.value?.focus();
  },
);

watch(
  () => ({ open: props.open, tab: settingsTab.value }),
  ({ open, tab }) => {
    if (!open) return;
    if (tab !== "general") return;
    if (updateCheckStatus.value !== "idle") return;
    checkForUpdates();
  },
);

watch(openSettingsAtTab, (tab) => {
  if (!tab) return;
  emit("update:open", true);
  settingsTab.value = tab as SettingsTabId;
  nextTick(() => settingsStore.setOpenSettingsAtTab(null));
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      ref="settingsModalRef"
      class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-modal-title"
      tabindex="-1"
      @keydown="onSettingsKeydown"
      @click.self="close"
    >
      <div class="settings-modal flex h-[85vh] min-h-[450px] w-full max-w-5xl flex-col overflow-hidden rounded-lg border border-stone-600 bg-stone-800 shadow-xl" @click.stop>
        <div class="flex shrink-0 items-center justify-between border-b border-stone-700 px-4 py-3">
          <h2 id="settings-modal-title" class="text-sm font-semibold text-stone-200">Settings</h2>
          <button type="button" class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200" aria-label="Close" @click="close">
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div class="flex min-h-0 flex-1">
          <nav class="settings-tab-nav w-36 shrink-0 border-r border-stone-700 bg-stone-800/90 py-2" aria-label="Settings sections">
            <button
              v-for="tab in settingsTabs"
              :key="tab.id"
              type="button"
              class="settings-tab-btn w-full px-3 py-2 text-left text-xs font-medium transition-colors"
              :class="settingsTab === tab.id ? 'settings-tab-btn--active' : undefined"
              @click="settingsTab = tab.id"
            >
              {{ tab.label }}
            </button>
          </nav>

          <div class="min-h-0 min-w-0 flex-1 overflow-y-auto p-4">
            <div v-show="settingsTab === 'general'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">General</p>
              <div class="settings-section">
                <p class="mb-2 text-xs font-semibold text-stone-400">Updates</p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-xs text-stone-200 hover:bg-stone-700"
                  :disabled="updateCheckStatus === 'checking'"
                  @click="checkForUpdates"
                >
                  <span v-if="updateCheckStatus === 'idle'">Check for updates</span>
                  <span v-else-if="updateCheckStatus === 'checking'">Checking…</span>
                  <span v-else-if="updateCheckStatus === 'up-to-date'">Up to date</span>
                  <span v-else-if="updateCheckStatus === 'available'">Update available</span>
                  <span v-else-if="updateCheckStatus === 'error'">Check failed</span>
                </button>
                <p v-if="updateError" class="mt-1 text-xs text-amber-400">
                  {{ updateError }}
                </p>
                <div v-if="availableUpdate" class="mt-3 rounded border border-emerald-600/60 bg-emerald-900/10 p-2.5">
                  <p class="text-xs font-medium text-emerald-300">
                    New version available: {{ availableUpdate.version }}
                  </p>
                  <p class="mt-0.5 text-[11px] text-emerald-200/90">
                    Current version: {{ availableUpdate.currentVersion }}.
                    <button
                      v-if="availableUpdate.body || availableUpdate.date"
                      type="button"
                      class="underline decoration-dotted underline-offset-2 hover:text-emerald-100"
                      @click="openReleaseUrl(`${GITHUB_RELEASE_BASE}/tag/v${availableUpdate.version}`)"
                    >
                      View release notes
                    </button>
                  </p>
                  <div class="mt-2 flex items-center gap-3">
                    <button
                      type="button"
                      class="rounded bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="updateDownloadProgress !== null"
                      @click="installUpdate"
                    >
                      <span v-if="updateDownloadProgress === null">Download and install</span>
                      <span v-else>Downloading… {{ updateDownloadProgress }}%</span>
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="settingsFilePath" class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Settings file</p>
                <p class="break-all font-mono text-[11px] text-stone-400">
                  {{ settingsFilePath }}
                </p>
                <div class="mt-1 flex gap-2">
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2.5 py-1 text-[11px] text-stone-300 hover:bg-stone-700"
                    @click="copyPathToClipboard(settingsFilePath)"
                  >
                    Copy path
                  </button>
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2.5 py-1 text-[11px] text-stone-300 hover:bg-stone-700"
                    @click="openReleaseUrl(`file://${settingsFilePath}`)"
                  >
                    Open in file manager
                  </button>
                </div>
              </div>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Navigation</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="navWrap"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setNavWrap((e.target as HTMLInputElement).checked)"
                  />
                  Wrap keyboard navigation
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving past the last item with the keyboard wraps around to the start (and vice versa).
                </p>
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="navFocusFollowsMouse"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setNavFocusFollowsMouse((e.target as HTMLInputElement).checked)"
                  />
                  Focus follows mouse
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving the mouse over items also updates the keyboard focus target.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'theme'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Theme</p>
              <div class="settings-section">
                <p class="mb-1 text-xs font-medium text-stone-500">Choose your palette</p>
                <div class="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2 md:grid-cols-3">
                  <button
                    v-for="opt in themeOptions"
                    :key="opt.value"
                    type="button"
                    class="group flex items-center gap-3 rounded-md border px-2.5 py-2 text-left text-xs transition"
                    :class="theme === opt.value
                      ? 'border-emerald-500/80 bg-emerald-900/30 shadow-inner'
                      : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'"
                    @click="settingsStore.setTheme(opt.value)"
                  >
                    <div
                      class="h-8 w-8 shrink-0 rounded-full bg-gradient-to-br shadow-sm ring-1 ring-black/40"
                      :class="opt.swatchClass"
                      aria-hidden="true"
                    />
                    <div class="min-w-0">
                      <p class="text-xs font-semibold text-stone-100">
                        {{ opt.label }}
                        <span
                          v-if="theme === opt.value"
                          class="ml-1 rounded bg-emerald-600/80 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-50"
                        >
                          Active
                        </span>
                      </p>
                      <p class="mt-0.5 text-[11px] text-stone-400 line-clamp-2">
                        {{ opt.description }}
                      </p>
                    </div>
                  </button>
                </div>
                <p class="mt-2 text-[11px] text-stone-500">
                  "Auto" follows your OS preference. "Orkish" uses a parchment-like light theme; "DOOM" is a high-contrast dark
                  theme.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'playback'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Playback</p>

              <div class="settings-section space-y-2">
                <p class="mb-1 text-xs font-semibold text-stone-400">Behavior</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="autoplayOnSelect"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setAutoplayOnSelect((e.target as HTMLInputElement).checked)"
                  />
                  Autoplay on track selection
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, selecting a track immediately starts playback.
                </p>
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="continuousPlayback"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setContinuousPlayback((e.target as HTMLInputElement).checked)"
                  />
                  Continuous playback
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, playback continues to the next track automatically.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p class="mb-1 text-xs font-semibold text-stone-400">Playbar</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="playbarShowAlbumInMarquee"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setPlaybarShowAlbumInMarquee((e.target as HTMLInputElement).checked)"
                  />
                  Show album in scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the album name is shown next to the track title in the scrolling marquee.
                </p>
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="playbarDisableMarquee"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setPlaybarDisableMarquee((e.target as HTMLInputElement).checked)"
                  />
                  Disable scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the track title is truncated instead of scrolling.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'keyboard'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Keyboard</p>
              <div class="settings-section space-y-2">
                <p class="text-xs text-stone-400">
                  Keyboard shortcuts are currently fixed. Planned improvements include per-action customization and profile export/import.
                </p>
                <p class="text-[11px] text-stone-500">
                  These are the same shortcuts shown in the key map:
                </p>
                <dl class="mt-1 space-y-2 text-xs">
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+F / ⌘F</dt>
                    <dd class="min-w-0 text-stone-300">Focus search bar</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+R / ⌘R</dt>
                    <dd class="min-w-0 text-stone-300">Refresh whole library (all folders, all reports)</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+M / ⌘M</dt>
                    <dd class="min-w-0 text-stone-300">Toggle metadata editor panel for current selection</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+L / ⌘L</dt>
                    <dd class="min-w-0 text-stone-300">Show library panel</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+P / ⌘P</dt>
                    <dd class="min-w-0 text-stone-300">Toggle full player panel</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+A / ⌘A</dt>
                    <dd class="min-w-0 text-stone-300">Select all tracks in current view and enable multi-select</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Ctrl+K / ⌘K</dt>
                    <dd class="min-w-0 text-stone-300">Open key map</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Escape</dt>
                    <dd class="min-w-0 text-stone-300">Close metadata editor panel or cover popup</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">↓ Arrow Down</dt>
                    <dd class="min-w-0 text-stone-300">Move focus down in track list</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">↑ Arrow Up</dt>
                    <dd class="min-w-0 text-stone-300">Move focus up in track list</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Space</dt>
                    <dd class="min-w-0 text-stone-300">
                      On group row: expand or collapse. On track row: select (add to selection in multi-select).
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">Enter</dt>
                    <dd class="min-w-0 text-stone-300">
                      With one track selected: start playback or pause if already playing.
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <div v-show="settingsTab === 'table'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Layout</p>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Library grouping</p>
                <label class="block text-xs font-medium text-stone-500">Default grouping</label>
                <select
                  :value="defaultGroupBy"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1 text-xs text-stone-200"
                  @change="(e) => setDefaultGroupBy((e.target as HTMLSelectElement).value as DefaultGroupBy)"
                >
                  <option v-for="opt in defaultGroupByOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="defaultGroupsExpanded"
                    class="rounded border-stone-600"
                    @change="(e) => setDefaultGroupsExpanded((e.target as HTMLInputElement).checked)"
                  />
                  Expand groups by default
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  Controls how your library is grouped and whether groups start expanded when you open Muorg.
                </p>
              </div>

              <div class="settings-section">
                <p class="mb-2 text-xs font-semibold text-stone-400">Table density</p>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="opt in tableDensityOptions"
                    :key="opt.value"
                    type="button"
                    class="flex min-w-0 flex-1 basis-[min(100%,12rem)] items-start gap-3 rounded-lg border px-3 py-2.5 text-left text-xs transition"
                    :class="tableDensity === opt.value
                      ? 'border-emerald-500/80 bg-emerald-900/30 shadow-inner'
                      : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'"
                    @click="settingsStore.setTableDensity(opt.value)"
                  >
                    <div
                      class="mt-0.5 flex shrink-0 flex-col gap-0.5"
                      aria-hidden="true"
                    >
                      <span
                        class="block h-1.5 w-6 rounded-sm"
                        :class="opt.value === 'comfortable' ? 'bg-stone-500' : 'bg-stone-600'"
                      />
                      <span
                        class="block h-1.5 w-6 rounded-sm"
                        :class="opt.value === 'comfortable' ? 'my-1 bg-stone-500' : 'bg-stone-600'"
                      />
                      <span
                        class="block h-1.5 w-6 rounded-sm"
                        :class="opt.value === 'comfortable' ? 'bg-stone-500' : 'bg-stone-600'"
                      />
                    </div>
                    <div class="min-w-0 flex-1">
                      <p class="font-semibold text-stone-100">
                        {{ opt.label }}
                        <span
                          v-if="tableDensity === opt.value"
                          class="ml-1 rounded bg-emerald-600/80 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-50"
                        >
                          Active
                        </span>
                      </p>
                      <p class="mt-0.5 text-[11px] text-stone-400">
                        {{ opt.description }}
                      </p>
                    </div>
                  </button>
                </div>
              </div>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Sidebar</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="settingsStore.hideReportsSection"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setHideReportsSection((e.target as HTMLInputElement).checked)"
                  />
                  Hide reports section in sidebar
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the reports block (Missing metadata, Duplicates, Missing album cover) is hidden from the main sidebar layout.
                </p>
              </div>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Table columns</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColAlbumArt"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColAlbumArt((e.target as HTMLInputElement).checked)"
                  />
                  Show album art
                </label>
                <label class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColYear"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColYear((e.target as HTMLInputElement).checked)"
                  />
                  Show year
                </label>
                <label class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColDuration"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColDuration((e.target as HTMLInputElement).checked)"
                  />
                  Show duration
                </label>
                <label class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColFormat"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColFormat((e.target as HTMLInputElement).checked)"
                  />
                  Show file format
                </label>
                <label class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColPath"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColPath((e.target as HTMLInputElement).checked)"
                  />
                  Show file path
                </label>
              </div>
            </div>

            <div v-show="settingsTab === 'reports'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Reports</p>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Group header album art</p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="groupHeaderAlbumArt"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setGroupHeaderAlbumArt((e.target as HTMLInputElement).checked)"
                  />
                  Show album art on "Missing metadata" groups
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, album art is shown next to each album in the "Missing metadata" report.
                </p>
              </div>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Missing metadata fields</p>
                <p class="mb-1 text-xs text-stone-500">
                  Choose which fields must be present for a track to be considered "complete". Tracks missing any of these fields will appear in the "Missing metadata" report.
                </p>
                <div class="grid grid-cols-2 gap-1">
                  <label v-for="opt in missingMetadataFieldOptions" :key="opt.value" class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="missingMetadataFields.includes(opt.value)"
                      class="rounded border-stone-600"
                      @change="(e) => {
                        const checked = (e.target as HTMLInputElement).checked;
                        const set = new Set(missingMetadataFields);
                        if (checked) set.add(opt.value);
                        else set.delete(opt.value);
                        settingsStore.setMissingMetadataFields(Array.from(set));
                      }"
                    />
                    {{ opt.label }}
                  </label>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'smart_suggestions'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Smart Suggestions</p>

              <div class="settings-section space-y-1.5">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="hideWikipediaCoverSearch"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setHideWikipediaCoverSearch((e.target as HTMLInputElement).checked)"
                  />
                  Hide Wikipedia album cover search
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the "From Wikipedia" (globe) button for album art is hidden in the metadata editor and on album group headers.
                </p>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <label class="block text-xs font-medium text-stone-500">Path format (for metadata suggestions)</label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    Use placeholders in angle brackets to describe how your file paths are structured. Matching starts from the end of the path and works upwards toward parent folders.
                  </p>
                  <p class="mt-1.5 text-xs font-medium text-stone-500">Example patterns (click to apply):</p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li v-for="(ex, i) in pathFormatExamples" :key="i">
                      <button
                        type="button"
                        class="path-format-example-btn w-full break-all rounded border px-2 py-1 font-mono text-left"
                        :title="'Use this format'"
                        @click="settingsStore.setPathFormatTemplate(ex)"
                      >
                        {{ i + 1 }}. {{ ex }}
                      </button>
                    </li>
                  </ul>
                  <div class="mt-2">
                    <label class="block text-xs font-medium text-stone-500">Active pattern</label>
                    <input
                      type="text"
                      :value="pathFormatTemplate"
                      class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 font-mono text-sm text-stone-200"
                      placeholder="Pattern currently used by Apply from path"
                      @input="(e) => settingsStore.setPathFormatTemplate((e.target as HTMLInputElement).value)"
                    />
                  </div>
                </div>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <p class="text-xs font-medium text-stone-500">Matching path examples (click to try):</p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li v-for="(p, i) in pathFormatExamplePaths" :key="'path-'+i">
                      <button
                        type="button"
                        class="path-format-example-btn w-full break-all rounded border px-2 py-1 font-mono text-left"
                        @click="settingsStore.setPathFormatExamplePath(p)"
                      >
                        {{ i + 1 }}. {{ p }}
                      </button>
                    </li>
                  </ul>
                </div>

                <div class="mt-2 rounded border border-stone-600 bg-stone-900/70 p-3">
                  <div class="flex items-center justify-between gap-2">
                    <p class="text-xs font-medium text-stone-400">Try your path</p>
                    <button
                      type="button"
                      class="shrink-0 rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-500 hover:bg-stone-600 hover:text-stone-200"
                      title="Restore default example path"
                      @click="settingsStore.setPathFormatExamplePath(DEFAULT_PATH_FORMAT_EXAMPLE_PATH)"
                    >
                      Reset to default
                    </button>
                  </div>
                  <input
                    type="text"
                    :value="pathFormatExamplePath"
                    class="mt-1.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. /path/to/Artist/Album/01 - Title.flac"
                    @input="(e) => settingsStore.setPathFormatExamplePath((e.target as HTMLInputElement).value)"
                  />
                  <p class="mt-1 text-[11px] text-stone-500">
                    Using pattern:
                    <span class="font-mono text-stone-400">{{ pathFormatTemplate || '—' }}</span>
                  </p>
                  <div v-if="pathFormatTemplate.trim()" class="mt-2 border-t border-stone-700/60 pt-2">
                    <p class="text-xs font-medium text-stone-400">Extracted fields</p>
                    <table v-if="pathFormatExampleExtracted" class="mt-1.5 w-full border-collapse text-xs">
                      <thead>
                        <tr class="border-b border-stone-600">
                          <th class="py-1.5 pr-3 text-left font-medium text-stone-500">Field</th>
                          <th class="py-1.5 text-left font-medium text-stone-500">Value</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(val, key) in pathFormatExampleExtracted" :key="key" class="border-b border-stone-700/50">
                          <td class="py-1.5 pr-3 font-mono text-stone-400">{{ key }}</td>
                          <td class="py-1.5 text-stone-300">{{ val || '—' }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <p v-else class="mt-1 text-xs text-amber-500">Format does not match the example path. Adjust placeholders or path structure.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <Teleport to="body">
    <div
      v-if="showUpdateCompleteModal"
      class="fixed inset-0 z-[305] flex items-center justify-center bg-stone-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="update-complete-title"
      @keydown.escape="closeUpdateCompleteModal"
      @click.self="closeUpdateCompleteModal"
    >
      <div class="w-full max-w-sm rounded-lg border border-stone-600 bg-stone-800 p-4 shadow-xl" @click.stop>
        <h2 id="update-complete-title" class="text-sm font-semibold text-stone-200">Update installed</h2>
        <p class="mt-2 text-xs text-stone-400">
          Version {{ updateCompleteVersion }} has been installed. Restart the app to use the new version.
        </p>
        <div class="mt-4 flex justify-end gap-2">
          <button type="button" class="rounded border border-stone-600 px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200" @click="closeUpdateCompleteModal">
            Later
          </button>
          <button type="button" class="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-500" @click="restartAfterUpdate">
            Restart now
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.settings-tab-btn {
  color: rgb(156 163 175);
}
.settings-tab-btn:hover {
  background-color: rgba(250, 250, 249, 0.06);
  color: rgb(250 250 249);
}
.settings-tab-btn--active {
  background-color: rgba(5, 150, 105, 0.18);
  color: rgb(248 250 252);
}
.settings-tab-btn--active:hover {
  background-color: rgba(5, 150, 105, 0.18);
  color: rgb(248 250 252);
}

.settings-section {
  border: 1px solid rgb(68 64 60);
  background-color: rgba(23, 23, 23, 0.9);
  border-radius: 0.5rem;
  padding: 0.75rem 0.9rem;
}

/* Make settings text a bit lighter in dark theme for readability */
:global(html[data-theme="dark"] .settings-modal .text-stone-500),
:global(html[data-theme="dark"] .settings-modal .text-stone-400) {
  color: rgb(229 231 235);
}
/* Theme-aware settings cards */
::global(html[data-theme="light"] .settings-modal .settings-section) {
  border-color: #d6d3d1;
  background-color: #f5f5f4;
}

::global(html[data-theme="doom"] .settings-modal .settings-section) {
  border-color: #4a1515;
  background-color: #1a0505;
}

::global(html[data-theme="orkish"] .settings-modal .settings-section) {
  border-color: #c5e1a5;
  background-color: #dcedc8;
}
</style>

