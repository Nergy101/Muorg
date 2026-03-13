<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef, watch } from "vue";
import { storeToRefs } from "pinia";
import { appConfigDir, join } from "@tauri-apps/api/path";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { open as openShell } from "@tauri-apps/plugin-shell";
import type { Update } from "@tauri-apps/plugin-updater";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { ThemeId, DefaultGroupBy, TableDensity, MissingMetadataField } from "../stores/settings";
import { extractMetadataFromPath } from "../utils/pathFormat";
import { DEFAULT_PATH_FORMAT_EXAMPLE_PATH } from "../stores/settings";

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

const themeOptions: { value: ThemeId; label: string }[] = [
  { value: "auto", label: "Auto" },
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "orkish", label: "Orkish" },
  { value: "doom", label: "DOOM" },
];

const defaultGroupByOptions: { value: DefaultGroupBy; label: string }[] = [
  { value: "album", label: "By album" },
  { value: "artist", label: "By artist" },
  { value: "none", label: "No grouping" },
];

const tableDensityOptions: { value: TableDensity; label: string }[] = [
  { value: "comfortable", label: "Comfortable" },
  { value: "compact", label: "Compact" },
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
  // Simple artist/album/track layout
  "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
  // Year inside album folder
  "<Artist>/Albums/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  // Album artist with disc + track numbers
  "<AlbumArtist>/<Album>/<DiscNumber>-<TrackNumber> <TrackTitle>.<Format>",
  // Genre grouped, year in album folder
  "<Genre>/<Artist>/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  // Flat artist-album structure
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

// Updates
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

// Settings file path
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
    // ignore
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
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-sm text-stone-200 hover:bg-stone-700 disabled:opacity-50"
                  :disabled="updateCheckStatus === 'checking' || updateDownloadProgress != null"
                  @click="checkForUpdates"
                >
                  {{ updateCheckStatus === 'checking' ? 'Checking...' : updateDownloadProgress != null ? 'Downloading...' : 'Check for updates' }}
                </button>
                <p v-if="updateCheckStatus === 'up-to-date'" class="mt-2 text-xs text-stone-500">You're up to date.</p>
                <p v-else-if="updateCheckStatus === 'error'" class="mt-2 text-xs text-red-400">{{ updateError }}</p>
                <div v-if="updateCheckStatus === 'available' && availableUpdate" class="mt-3 space-y-2">
                  <p class="text-xs text-stone-300">
                    <strong>Version {{ availableUpdate.version }}</strong>
                    <span v-if="availableUpdate.date" class="text-stone-500"> · {{ availableUpdate.date }}</span>
                  </p>
                  <p v-if="availableUpdate.body" class="text-xs text-stone-400 whitespace-pre-line">{{ availableUpdate.body }}</p>
                  <div class="flex items-center gap-3">
                    <button
                      type="button"
                      class="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm hover:bg-emerald-500 disabled:opacity-50"
                      :disabled="updateDownloadProgress != null"
                      @click="installUpdate"
                    >
                      {{ updateDownloadProgress != null ? `Downloading ${updateDownloadProgress}%...` : 'Download and install' }}
                    </button>
                    <button
                      type="button"
                      class="text-left text-xs text-stone-400 underline hover:text-stone-300"
                      @click="openReleaseUrl(`${GITHUB_RELEASE_BASE}/tag/v${availableUpdate.version}`)"
                    >
                      See release
                    </button>
                  </div>
                </div>
              </div>

              <div class="settings-section">
                <p class="mb-2 text-xs font-semibold text-stone-400">Settings file</p>
                <div class="flex items-center gap-2">
                  <button
                    v-if="settingsFilePath"
                    type="button"
                    class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-800 text-stone-300 hover:bg-stone-700"
                    aria-label="Copy settings file path"
                    @click="copyPathToClipboard(settingsFilePath)"
                  >
                    <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2" />
                      <path stroke-linecap="round" stroke-linejoin="round" d="M10 8h8a2 2 0 012 2v8a2 2 0 01-2 2h-8a2 2 0 01-2-2v-8a2 2 0 012-2z" />
                    </svg>
                  </button>
                  <p class="min-w-0 text-xs text-stone-500">
                    <span v-if="settingsFilePath" class="break-all font-mono">{{ settingsFilePath }}</span>
                    <span v-else>Settings file path not available.</span>
                  </p>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'theme'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Theme</p>
              <div class="settings-section">
                <p class="mb-1 text-xs font-medium text-stone-500">Select theme</p>
                <div class="mt-1 flex flex-wrap gap-2">
                  <button
                    v-for="opt in themeOptions"
                    :key="opt.value"
                    type="button"
                    class="rounded-full border px-3 py-1 text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500/80 focus-visible:ring-offset-2 focus-visible:ring-offset-stone-900"
                    :class="[
                      opt.value === 'dark'
                        ? (theme === 'dark'
                            ? 'bg-black text-stone-50 border-stone-300'
                            : 'bg-black text-stone-50 border-stone-500 hover:bg-stone-900')
                        : opt.value === 'light'
                          ? (theme === 'light'
                              ? 'bg-stone-50 text-stone-900 border-stone-700'
                              : 'bg-stone-200 text-stone-900 border-stone-400 hover:bg-stone-100')
                          : opt.value === 'orkish'
                            ? (theme === 'orkish'
                                ? 'bg-lime-600 text-stone-950 border-lime-300'
                                : 'bg-lime-700 text-lime-50 border-lime-400 hover:bg-lime-600')
                            : opt.value === 'doom'
                              ? (theme === 'doom'
                                  ? 'bg-red-700 text-stone-50 border-red-300'
                                  : 'bg-red-800 text-red-100 border-red-500 hover:bg-red-700')
                              : theme === 'auto'
                                ? 'bg-sky-700 text-stone-50 border-sky-300'
                                : 'bg-sky-800 text-sky-100 border-sky-400 hover:bg-sky-700',
                    ]"
                    @click="settingsStore.setTheme(opt.value)"
                  >
                    {{ opt.label }}
                  </button>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'playback'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Playback</p>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="autoplayOnSelect"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setAutoplayOnSelect((e.target as HTMLInputElement).checked)"
                  />
                  Auto-play when selecting a single track
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  If enabled, selecting a single track immediately starts playback.
                </p>
              </div>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="continuousPlayback"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setContinuousPlayback((e.target as HTMLInputElement).checked)"
                  />
                  Continuous playback
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, playback automatically advances to the next track when the current track finishes.
                </p>
              </div>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="playbarShowAlbumInMarquee"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setPlaybarShowAlbumInMarquee((e.target as HTMLInputElement).checked)"
                  />
                  Playbar: show album title in marquee
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the small player bar and play screen show album between title and artist.
                </p>
              </div>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="playbarDisableMarquee"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setPlaybarDisableMarquee((e.target as HTMLInputElement).checked)"
                  />
                  Playbar: disable marquee animation
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the title uses ellipsis and shows the full text in a popover on hover.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'keyboard'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Keyboard</p>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="navWrap"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setNavWrap((e.target as HTMLInputElement).checked)"
                  />
                  Wrap focus at ends (↑/↓)
                </label>
              </div>
              <div class="settings-section">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="navFocusFollowsMouse"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setNavFocusFollowsMouse((e.target as HTMLInputElement).checked)"
                  />
                  Focus follows mouse hover
                </label>
              </div>
            </div>

            <div v-show="settingsTab === 'table'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Layout</p>

              <div class="settings-section">
                <label class="block text-xs font-medium text-stone-500">Density</label>
                <select
                  :value="tableDensity"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                  @change="(e) => settingsStore.setTableDensity((e.target as HTMLSelectElement).value as TableDensity)"
                >
                  <option v-for="opt in tableDensityOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <div class="settings-section">
                <p class="text-xs font-semibold text-stone-400">Default bottom panel</p>
                <p class="mt-0.5 text-xs text-stone-500">Which panel shows in the bottom bar when Muorg starts.</p>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    type="button"
                    class="rounded-full border px-3 py-1 text-xs font-medium transition-colors"
                    :class="settingsStore.defaultBottomPanel === 'library'
                      ? (theme === 'orkish'
                          ? 'border-lime-500 bg-lime-700 text-white'
                          : theme === 'doom'
                            ? 'border-red-500 bg-red-700 text-white'
                            : 'border-[#5b7c32] bg-[#5b7c32] text-white')
                      : (theme === 'orkish'
                          ? 'border-lime-500 bg-transparent text-lime-400 hover:bg-lime-900/20'
                          : theme === 'doom'
                            ? 'border-red-500 bg-transparent text-red-400 hover:bg-red-900/20'
                            : 'border-[#5b7c32] bg-transparent text-[#5b7c32] hover:bg-stone-900/10')"
                    @click="settingsStore.setDefaultBottomPanel('library')"
                  >
                    Library
                  </button>
                  <button
                    type="button"
                    class="rounded-full border px-3 py-1 text-xs font-medium transition-colors"
                    :class="settingsStore.defaultBottomPanel === 'metadata'
                      ? (theme === 'orkish'
                          ? 'border-lime-500 bg-lime-700 text-white'
                          : theme === 'doom'
                            ? 'border-red-500 bg-red-700 text-white'
                            : 'border-[#5b7c32] bg-[#5b7c32] text-white')
                      : (theme === 'orkish'
                          ? 'border-lime-500 bg-transparent text-lime-400 hover:bg-lime-900/20'
                          : theme === 'doom'
                            ? 'border-red-500 bg-transparent text-red-400 hover:bg-red-900/20'
                            : 'border-[#5b7c32] bg-transparent text-[#5b7c32] hover:bg-stone-900/10')"
                    @click="settingsStore.setDefaultBottomPanel('metadata')"
                  >
                    Metadata
                  </button>
                  <button
                    type="button"
                    class="rounded-full border px-3 py-1 text-xs font-medium transition-colors"
                    :class="settingsStore.defaultBottomPanel === 'play'
                      ? (theme === 'orkish'
                          ? 'border-lime-500 bg-lime-700 text-white'
                          : theme === 'doom'
                            ? 'border-red-500 bg-red-700 text-white'
                            : 'border-[#5b7c32] bg-[#5b7c32] text-white')
                      : (theme === 'orkish'
                          ? 'border-lime-500 bg-transparent text-lime-400 hover:bg-lime-900/20'
                          : theme === 'doom'
                            ? 'border-red-500 bg-transparent text-red-400 hover:bg-red-900/20'
                            : 'border-[#5b7c32] bg-transparent text-[#5b7c32] hover:bg-stone-900/10')"
                    @click="settingsStore.setDefaultBottomPanel('play')"
                  >
                    Player
                  </button>
                </div>
              </div>

              <div class="settings-section space-y-3">
                <p class="text-xs font-semibold text-stone-400">Grouping</p>
                <div>
                  <label class="block text-xs font-medium text-stone-500">Default grouping</label>
                  <select
                    :value="defaultGroupBy"
                    class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                    @change="(e) => setDefaultGroupBy((e.target as HTMLSelectElement).value as DefaultGroupBy)"
                  >
                    <option v-for="opt in defaultGroupByOptions" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </option>
                  </select>
                  <p class="mt-0.5 text-xs text-stone-500">Applied when the app starts.</p>
                </div>
                <div>
                  <p class="text-xs font-semibold text-stone-400">Grouping headers</p>
                  <label class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="groupHeaderAlbumArt"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setGroupHeaderAlbumArt((e.target as HTMLInputElement).checked)"
                    />
                    Show album art in album group header
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When grouping by album, show the cover in the group row (if all tracks share the same art).
                  </p>
                </div>
                <div>
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="defaultGroupsExpanded"
                      class="rounded border-stone-600"
                      @change="(e) => setDefaultGroupsExpanded((e.target as HTMLInputElement).checked)"
                    />
                    Groups start expanded
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">When grouping is on, expand all groups by default.</p>
                </div>
              </div>

              <div class="settings-section">
                <p class="mb-1 text-xs font-semibold text-stone-400">Columns</p>
                <div class="mt-1 grid grid-cols-2 gap-2">
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="tableColAlbumArt"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setTableColAlbumArt((e.target as HTMLInputElement).checked)"
                    />
                    Album art column
                  </label>
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="tableColYear"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setTableColYear((e.target as HTMLInputElement).checked)"
                    />
                    Year
                  </label>
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="tableColDuration"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setTableColDuration((e.target as HTMLInputElement).checked)"
                    />
                    Duration
                  </label>
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="tableColFormat"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setTableColFormat((e.target as HTMLInputElement).checked)"
                    />
                    Format
                  </label>
                  <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                    <input
                      type="checkbox"
                      :checked="tableColPath"
                      class="rounded border-stone-600"
                      @change="(e) => settingsStore.setTableColPath((e.target as HTMLInputElement).checked)"
                    />
                    Path
                  </label>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'reports'" class="space-y-3">
              <p class="text-xs font-semibold text-stone-400">Reports</p>
              <div class="settings-section">
                <p class="mb-1 text-xs font-medium text-stone-500">Missing metadata fields</p>
                <p class="mt-0.5 text-xs text-stone-500">Fields to consider missing for the "Missing metadata" report:</p>
                <div class="mt-2 grid grid-cols-2 gap-2">
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

              <!-- Path format: patterns + active pattern -->
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

              <!-- Path format: matching examples + try your path + extracted fields -->
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
  border-color: #d6d3d1; /* stone-300/400 */
  background-color: #f5f5f4; /* stone-100/200 */
}

::global(html[data-theme="doom"] .settings-modal .settings-section) {
  border-color: #4a1515; /* deep red border */
  background-color: #1a0505; /* dark crimson panel */
}

::global(html[data-theme="orkish"] .settings-modal .settings-section) {
  border-color: #c5e1a5; /* soft green border */
  background-color: #dcedc8; /* pale green card */
}
</style>

