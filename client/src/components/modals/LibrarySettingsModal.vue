<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef, watch } from "vue";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";
import { storeToRefs } from "pinia";
import { appConfigDir, join } from "@tauri-apps/api/path";
import * as catalogApi from "../../api/catalog";
import {
  getBackendMode,
  getOnlineServerUrl, setOnlineServerUrl, getOnlineApiKey, setOnlineApiKey,
} from "../../api/client";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { open as openShell } from "@tauri-apps/plugin-shell";
import type { Update } from "@tauri-apps/plugin-updater";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import type {
  ThemeId,
  DefaultGroupBy,
  TableDensity,
  BottomPanelId,
  MissingMetadataField,
  PlayerGlowIntensity,
  PlayerGlowMode,
  ReplayGainMode,
} from "../../stores/settings";
import { extractBestFromPath, extractMetadataFromPath, buildUpdateFromExtracted, buildTransformPath } from "../../utils/pathFormat";
import { DEFAULT_PATH_FORMAT_EXAMPLE_PATH } from "../../stores/settings";
import {
  getGlowBlobs,
  getSimpleGlowBlobs,
} from "../../composables/useDominantColor";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import GenrePieChart from "@shared/components/stats/GenrePieChart.vue";
import TopArtistsChart from "../stats/TopArtistsChart.vue";
import MetadataHealthChart from "../stats/MetadataHealthChart.vue";
import YearLineChart from "@shared/components/stats/YearLineChart.vue";
import RatingChart from "@shared/components/stats/RatingChart.vue";
import packageJson from "../../../package.json";

const appVersion = packageJson.version;

function handleViewField(field: MissingMetadataField) {
  store.setReportSingleField(field);
  emit("update:open", false);
}

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const {
  defaultGroupsExpanded,
  theme,
  defaultGroupBy,
  autoplayOnSelect,
  continuousPlayback,
  playbarShowAlbumInMarquee,
  playbarDisableMarquee,
  playbarShowRatingInMaximized,
  navWrap,
  navFocusFollowsMouse,
  tableDensity,
  tableColAlbumArt,
  tableColYear,
  tableColDuration,
  tableColFormat,
  tableColPath,
  tableColRating,
  missingMetadataFields,
  groupHeaderAlbumArt,
  groupHeaderAlbumArtForArtist,
  splitAlbumHeadersByArtist,
  hideAlbumArtColInAlbumGroups,
  hideGroupTrackCount,
  hideWikipediaCoverSearch,
  pathFormatTemplates,
  pathFormatExamplePath,
  openSettingsAtTab,
  playerGlowIntensity,
  playerGlowMode,
  defaultBottomPanel,
  musicRootFolder,
  backupBeforeWrite,
  replayGainMode,
  replayGainPreampDb,
  replayGainPreventClipping,
} = storeToRefs(settingsStore);

const glowSettingsDisabled = computed(
  () => playerGlowIntensity.value === "off",
);
const replayGainOptions: { value: ReplayGainMode; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "track", label: "Track" },
  { value: "album", label: "Album" },
];

/** Default for Music Root Folder when not set: single loaded folder name, else "Music". */
function musicRootFolderBasename(path: string): string {
  const segments = path.split(/[/\\]/).filter(Boolean);
  return segments[segments.length - 1] ?? "Music";
}
const musicRootFolderPlaceholder = computed(() =>
  store.roots.length === 1 ? musicRootFolderBasename(store.roots[0]) : "Music",
);

type SettingsTabId =
  | "general"
  | "theme"
  | "playback"
  | "table"
  | "keyboard"
  | "reports"
  | "exports"
  | "smart_suggestions"
  | "smart_transform"
  | "statistics"
  | "connection";
const settingsTab = ref<SettingsTabId>("general");
const settingsTabs: { id: SettingsTabId; label: string; icon: string }[] = [
  { id: "connection", label: "Connection", icon: "wifi" },
  { id: "general", label: "General", icon: "sliders" },
  { id: "theme", label: "Theme", icon: "sun" },
  { id: "playback", label: "Playback", icon: "play-circle" },
  { id: "table", label: "Layout", icon: "layout" },
  { id: "smart_suggestions", label: "Smart Suggestions", icon: "zap" },
  { id: "smart_transform", label: "Smart Transform", icon: "shuffle" },
  { id: "reports", label: "Reports", icon: "bar-chart-2" },
  { id: "exports", label: "Exports", icon: "download" },
  { id: "statistics", label: "Statistics", icon: "pie-chart" },
  { id: "keyboard", label: "Keyboard", icon: "command" },
];

// --- Connection tab state ---
const connMode = ref<"local" | "online">(getBackendMode());
const connOnlineUrl = ref(getOnlineServerUrl());
const connOnlineApiKey = ref(getOnlineApiKey());
const connOnlineApiKeyVisible = ref(false);
const connStatus = ref<"idle" | "saving" | "ok" | "error">("idle");
const connError = ref("");

async function applyAndReload() {
  connStatus.value = "saving";
  connError.value = "";
  if (connMode.value === "online") {
    setOnlineServerUrl(connOnlineUrl.value.trim());
    setOnlineApiKey(connOnlineApiKey.value.trim());
  }
  settingsStore.setBackendMode(connMode.value);

  await store.loadRoots();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await store.loadTracks();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await playlistStore.loadPlaylists();
  if (playlistStore.error) { connStatus.value = "error"; connError.value = playlistStore.error; return; }

  connStatus.value = "ok";
}

async function switchMode(mode: "local" | "online") {
  connMode.value = mode;
  connStatus.value = "idle";
  connError.value = "";
  // Local needs no credentials — apply immediately
  if (mode === "local") await applyAndReload();
}

const refreshStatus = ref<"idle" | "loading" | "ok" | "error">("idle");
const refreshError = ref("");

async function refreshFromServer() {
  refreshStatus.value = "loading";
  refreshError.value = "";
  store.$patch({ coverCache: {}, albumCoverCache: {} });
  await store.loadRoots();
  if (store.error) { refreshStatus.value = "error"; refreshError.value = store.error; return; }
  await store.loadTracks();
  if (store.error) { refreshStatus.value = "error"; refreshError.value = store.error; return; }
  await playlistStore.loadPlaylists();
  if (playlistStore.error) { refreshStatus.value = "error"; refreshError.value = playlistStore.error; return; }
  refreshStatus.value = "ok";
}


const themeOptions: {
  value: ThemeId;
  label: string;
  description: string;
  swatchClass: string;
}[] = [
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

const playerGlowOptions: { value: PlayerGlowIntensity; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "subdued", label: "Subdued" },
  { value: "default", label: "Default" },
  { value: "vibrant", label: "Vibrant" },
];

const playerGlowModeOptions: {
  value: PlayerGlowMode;
  label: string;
  description: string;
}[] = [
  {
    value: "dynamic",
    label: "Dynamic",
    description: "Muorg picks the best effect based on the album cover",
  },
  {
    value: "vivid",
    label: "Vivid",
    description: "Always use center-color blobs",
  },
  {
    value: "edge-blur",
    label: "Edge blur",
    description: "Always use blurred art bleeding in from edges",
  },
  {
    value: "bland",
    label: "Bland",
    description: "Always use a subtle soft glow",
  },
];

/** Primary green RGB for vivid glow demo. */
const GLOW_DEMO_RGB = "91,124,50";
/** Light gray for bland glow demo (white/gray cover). */
const GLOW_DEMO_BLAND_RGB = "220,220,220";
/** Very dark tint of the demo color for background (not pure black). */
const GLOW_DEMO_NEAR_BLACK = "rgb(10,14,6)";

const glowDemoBlobs = computed(() => {
  const raw = getGlowBlobs(GLOW_DEMO_RGB, "settings-demo");
  const v = playerGlowIntensity.value;
  if (v === "off") return [];
  const scale = v === "subdued" ? 0.4 : v === "vibrant" ? 1.6 : 1.2;
  return raw.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

const blandGlowDemoBlobs = computed(() => {
  const raw = getSimpleGlowBlobs(GLOW_DEMO_BLAND_RGB, "settings-demo-bland");
  if (playerGlowIntensity.value === "off") return [];
  const v = playerGlowIntensity.value;
  const scale = v === "subdued" ? 0.4 : v === "vibrant" ? 1.6 : 1.2;
  return raw.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

/** Edge blur demo: bland center + colorful edges. Simulated with a blurred gradient. */
const edgeBlurDemoOpacity = computed(() => {
  if (playerGlowIntensity.value === "off") return 0;
  const v = playerGlowIntensity.value;
  if (v === "subdued") return 0.45;
  if (v === "vibrant") return 0.85;
  return 0.65;
});

function getGlowDemoBlobStyle(blob: {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  opacity: number;
  rgb: string;
}): Record<string, string> {
  const o = blob.opacity;
  const o2 = (o * 0.6).toFixed(2);
  const o3 = (o * 0.2).toFixed(2);
  const gradient = `radial-gradient(ellipse at center, rgba(${blob.rgb},${o.toFixed(2)}) 0%, rgba(${blob.rgb},${o2}) 25%, rgba(${blob.rgb},${o3}) 45%, rgba(${blob.rgb},0.04) 70%, transparent 90%)`;
  return {
    background: gradient,
    transform: `translate(${blob.cx * 100}%, ${blob.cy * 100}%) translate(-50%, -50%) scale(${blob.rx}, ${blob.ry})`,
    filter: "blur(24px)",
  };
}

const defaultGroupByOptions: {
  value: DefaultGroupBy;
  label: string;
  description: string;
}[] = [
  { value: "album", label: "By album", description: "Group tracks by album." },
  {
    value: "artist",
    label: "By artist",
    description: "Group tracks by artist.",
  },
  { value: "none", label: "No grouping", description: "Flat list, no groups." },
];

const defaultBottomPanelOptions: {
  value: BottomPanelId;
  label: string;
  description: string;
}[] = [
  {
    value: "library",
    label: "Default",
    description: "Track list and grouping.",
  },
  {
    value: "metadata",
    label: "Metadata",
    description: "Edit tags and album art.",
  },
  { value: "player", label: "Player", description: "Now playing and controls." },
  { value: "queue", label: "Queue", description: "Up next and queue." },
];

const tableDensityOptions: {
  value: TableDensity;
  label: string;
  description: string;
}[] = [
  {
    value: "comfortable",
    label: "Comfortable",
    description: "More spacing between rows; easier to scan.",
  },
  {
    value: "compact",
    label: "Compact",
    description: "Tighter rows; more tracks visible at once.",
  },
  {
    value: "spacious",
    label: "Spacious",
    description: "Album headers show a large cover; relaxed layout.",
  },
];

const missingMetadataFieldOptions: {
  value: MissingMetadataField;
  label: string;
}[] = [
  { value: "title", label: "Title" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
  { value: "album_artist", label: "Album artist" },
  { value: "year", label: "Year" },
  { value: "genre", label: "Genre" },
  { value: "track_number", label: "Track #" },
  { value: "disc_number", label: "Disc #" },
  { value: "rating", label: "Rating" },
  { value: "has_cover", label: "Album Cover" },
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
  const templates = pathFormatTemplates.value;
  const examplePath = pathFormatExamplePath.value?.trim();
  if (!templates.some((t) => t.trim()) || !examplePath) return null;
  return extractBestFromPath(templates, examplePath);
});

const pathFormatExampleBestPattern = computed(() => {
  const templates = pathFormatTemplates.value;
  const examplePath = pathFormatExamplePath.value?.trim();
  if (!templates.some((t) => t.trim()) || !examplePath) return null;
  let best: string | null = null;
  let bestScore = -1;
  for (const template of templates) {
    const trimmed = template.trim();
    if (!trimmed) continue;
    const extracted = extractMetadataFromPath(trimmed, examplePath);
    if (!extracted) continue;
    const score = Object.keys(buildUpdateFromExtracted(extracted)).length;
    if (score > bestScore) { best = trimmed; bestScore = score; }
  }
  return best;
});

function addExamplePattern(pattern: string) {
  if (!pathFormatTemplates.value.includes(pattern)) {
    settingsStore.setPathFormatTemplates([...pathFormatTemplates.value, pattern]);
  }
}

function updateTemplate(i: number, value: string) {
  const updated = [...pathFormatTemplates.value];
  updated[i] = value;
  settingsStore.setPathFormatTemplates(updated);
}

function removeTemplate(i: number) {
  settingsStore.setPathFormatTemplates(pathFormatTemplates.value.filter((_, idx) => idx !== i));
}

function addTemplate() {
  settingsStore.setPathFormatTemplates([...pathFormatTemplates.value, ""]);
}

const updateCheckStatus = ref<
  "idle" | "checking" | "up-to-date" | "available" | "error"
>("idle");
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
          updateDownloadProgress.value = Math.min(
            100,
            Math.round((downloaded / contentLength) * 100),
          );
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

const settingsScrollRef = ref<HTMLElement | null>(null);
useOverlayScrollbars(settingsScrollRef);

const linkTooltip = ref<{ text: string; x: number; y: number } | null>(null);
let linkTooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showLinkTooltip(url: string, e: MouseEvent) {
  if (linkTooltipHideTimeout) clearTimeout(linkTooltipHideTimeout);
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  linkTooltip.value = { text: url, x: rect.left + rect.width / 2, y: rect.top - 8 };
}

function hideLinkTooltip() {
  linkTooltipHideTimeout = setTimeout(() => {
    linkTooltip.value = null;
    linkTooltipHideTimeout = null;
  }, 80);
}

async function restartAfterUpdate() {
  closeUpdateCompleteModal();
  await relaunch();
}

const clearCacheStatus = ref<"idle" | "clearing" | "done" | "error">("idle");
const clearCacheError = ref<string | null>(null);

async function clearCache() {
  clearCacheStatus.value = "clearing";
  clearCacheError.value = null;
  try {
    await catalogApi.clearCache();
    clearCacheStatus.value = "done";
  } catch (e) {
    clearCacheError.value = e instanceof Error ? e.message : String(e);
    clearCacheStatus.value = "error";
  }
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
  } catch {}
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

// ── Smart Transform ──────────────────────────────────────────────────────────

const transformOrigin = ref("");
const transformDest = ref("");
const transformSelectedIds = ref<Set<number>>(new Set());
const transformError = ref<string | null>(null);

interface TransformMatch {
  track: (typeof store.tracks)[0];
  newPath: string;
}

const transformMatches = computed<TransformMatch[]>(() => {
  const origin = transformOrigin.value.trim();
  const dest = transformDest.value.trim();
  if (!origin || !dest) return [];
  return store.tracks
    .map((t) => {
      const newPath = buildTransformPath(origin, dest, t.path);
      return newPath && newPath !== t.path ? { track: t, newPath } : null;
    })
    .filter((x): x is TransformMatch => x !== null);
});

const transformAllSelected = computed(
  () =>
    transformMatches.value.length > 0 &&
    transformMatches.value.every((m) => transformSelectedIds.value.has(m.track.id)),
);

function toggleTransformSelectAll() {
  if (transformAllSelected.value) {
    transformSelectedIds.value = new Set();
  } else {
    transformSelectedIds.value = new Set(transformMatches.value.map((m) => m.track.id));
  }
}

function toggleTransformTrack(id: number) {
  const next = new Set(transformSelectedIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  transformSelectedIds.value = next;
}

async function runTransform() {
  const selected = transformSelectedIds.value;
  const toTransform = transformMatches.value.filter((m) => selected.has(m.track.id));
  if (!toTransform.length) return;
  transformError.value = null;
  const total = toTransform.length;
  store.setBulkProgress({ current: 0, total });
  try {
    for (let i = 0; i < toTransform.length; i++) {
      const { track, newPath } = toTransform[i];
      const id = store._trackIdByPath(track.path);
      if (id != null) await catalogApi.renameTrackFile(id, newPath);
      store.setBulkProgress({ current: i + 1, total });
    }
    await store.loadTracks();
    transformSelectedIds.value = new Set();
  } catch (err) {
    transformError.value = String(err);
  } finally {
    store.setBulkProgress(null);
  }
}

// Reset selection when matches change
watch(transformMatches, () => {
  transformSelectedIds.value = new Set();
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
      <div
        class="settings-modal flex h-[85vh] min-h-[450px] w-full max-w-5xl flex-col overflow-hidden rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
        @click.stop
      >
        <div
          class="flex shrink-0 items-center justify-between border-b border-stone-700 px-4 py-3"
        >
          <h2
            id="settings-modal-title"
            class="flex items-center gap-2 text-sm font-semibold text-stone-200"
          >
            <FeatherIcon
              name="settings"
              class="h-4 w-4 shrink-0 text-stone-400"
            />
            Settings
          </h2>
          <button
            type="button"
            class="icon-btn h-7 w-7 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Close"
            @click="close"
          >
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </div>

        <div class="flex min-h-0 flex-1">
          <nav
            class="settings-tab-nav w-36 shrink-0 border-r border-stone-700 bg-stone-800/90 py-2 flex flex-col gap-1"
            aria-label="Settings sections"
          >
            <template v-for="tab in settingsTabs" :key="tab.id">
              <div
                v-if="tab.id === 'general' || tab.id === 'statistics'"
                class="mx-3 my-2 border-t border-stone-700"
              />
              <button
                type="button"
                class="settings-tab-btn mx-2 flex w-[calc(100%-1rem)] items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-medium transition-colors"
                :class="settingsTab === tab.id ? 'settings-tab-btn--active' : undefined"
                @click="settingsTab = tab.id"
              >
                <FeatherIcon :name="tab.icon" class="h-3.5 w-3.5 shrink-0" />
                {{ tab.label }}
              </button>
            </template>
          </nav>

          <div ref="settingsScrollRef" class="min-h-0 min-w-0 flex-1 p-4">
            <div v-show="settingsTab === 'general'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="sliders"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                General
              </p>
              <div class="settings-section">
                <p
                  class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="download-cloud"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Updates
                  <span class="ml-auto font-normal text-stone-500">v{{ appVersion }}</span>
                </p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-xs text-stone-200 hover:bg-stone-700 disabled:opacity-60"
                  :disabled="updateCheckStatus === 'checking'"
                  @click="checkForUpdates"
                >
                  <span v-if="updateCheckStatus === 'checking'">Checking…</span>
                  <span v-else>Check for updates</span>
                </button>
                <div v-if="updateCheckStatus === 'up-to-date'" class="settings-uptodate-notice mt-3 rounded border p-2.5">
                  <p class="text-xs font-medium text-blue-300">Up to date</p>
                  <p class="mt-0.5 text-[11px] text-stone-400">You're running the latest version of Muorg.</p>
                </div>
                <p v-if="updateCheckStatus === 'error'" class="mt-1 text-xs text-amber-400">Check failed</p>
                <p v-if="updateError" class="mt-1 text-xs text-amber-400">
                  {{ updateError }}
                </p>
                <div
                  v-if="availableUpdate"
                  class="settings-update-notice mt-3 rounded border p-2.5"
                >
                  <p class="text-xs font-medium text-stone-200">
                    New version available: {{ availableUpdate.version }}
                  </p>
                  <p class="mt-0.5 text-[11px] text-stone-400">
                    Current version: {{ availableUpdate.currentVersion }}.
                    <button
                      v-if="availableUpdate.body || availableUpdate.date"
                      type="button"
                      class="underline decoration-dotted underline-offset-2 hover:text-stone-200"
                      @click="
                        openReleaseUrl(
                          `${GITHUB_RELEASE_BASE}/tag/v${availableUpdate.version.replace(/^v/, "")}`,
                        )
                      "
                    >
                      View release notes
                    </button>
                  </p>
                  <div class="mt-2 flex items-center gap-3">
                    <button
                      type="button"
                      class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="updateDownloadProgress !== null"
                      @click="installUpdate"
                    >
                      <span v-if="updateDownloadProgress === null"
                        >Download and install</span
                      >
                      <span v-else
                        >Downloading… {{ updateDownloadProgress }}%</span
                      >
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="settingsFilePath" class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="file"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Settings file
                </p>
                <p class="break-all font-mono text-[11px] text-stone-400">
                  {{ settingsFilePath }}
                </p>
                <div class="mt-1 flex gap-2">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 rounded border border-stone-600 px-2.5 py-1 text-[11px] text-stone-300 hover:bg-stone-700"
                    @click="copyPathToClipboard(settingsFilePath)"
                  >
                    <FeatherIcon name="clipboard" class="h-3 w-3 shrink-0" />
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
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="database"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Database cache
                </p>
                <p class="mb-2 text-xs text-stone-500">
                  Removed folders are kept in the database temporarily so
                  playlists can be restored if the folder is re-added. Use this
                  to free that space immediately.
                </p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-xs text-stone-200 hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="clearCacheStatus === 'clearing'"
                  @click="clearCache"
                >
                  <span v-if="clearCacheStatus === 'idle'">Clear cache</span>
                  <span v-else-if="clearCacheStatus === 'clearing'"
                    >Clearing…</span
                  >
                  <span v-else-if="clearCacheStatus === 'done'"
                    >Cache cleared</span
                  >
                  <span v-else-if="clearCacheStatus === 'error'">Failed</span>
                </button>
                <p v-if="clearCacheError" class="mt-1 text-xs text-amber-400">
                  {{ clearCacheError }}
                </p>
              </div>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="compass"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Navigation
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="navWrap"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setNavWrap(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Wrap keyboard navigation
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving past the last item with the keyboard
                  wraps around to the start (and vice versa).
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="navFocusFollowsMouse"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setNavFocusFollowsMouse(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Focus follows mouse
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving the mouse over items also updates the
                  keyboard focus target.
                </p>
              </div>

              <div class="settings-section border-t border-stone-700 pt-4">
                <p
                  class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="heart"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Credits
                </p>
                <p class="text-xs text-stone-500">
                  Made with ❤️ by
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/Nergy101')"
                    @mouseenter="showLinkTooltip('https://github.com/Nergy101', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="user" class="h-3.5 w-3.5 shrink-0" />
                    Nergy101
                  </button>
                </p>
                <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/Nergy101/Muorg')"
                    @mouseenter="showLinkTooltip('https://github.com/Nergy101/Muorg', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="github" class="h-3.5 w-3.5 shrink-0" />
                    GitHub
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://blog.nergy.space/')"
                    @mouseenter="showLinkTooltip('https://blog.nergy.space/', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="book-open" class="h-3.5 w-3.5 shrink-0" />
                    Blog
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://portfolio.nergy.space/')"
                    @mouseenter="showLinkTooltip('https://portfolio.nergy.space/', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="briefcase" class="h-3.5 w-3.5 shrink-0" />
                    Portfolio
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://retroranker.site')"
                    @mouseenter="showLinkTooltip('https://retroranker.site', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="award" class="h-3.5 w-3.5 shrink-0" />
                    RetroRanker
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://ko-fi.com/nergy')"
                    @mouseenter="showLinkTooltip('https://ko-fi.com/nergy', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="coffee" class="h-3.5 w-3.5 shrink-0" />
                    Ko-fi
                  </button>
                </div>
              </div>

              <div class="settings-section mt-3">
                <p class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400">
                  <FeatherIcon name="star" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
                  Thanks to…
                </p>
                <div class="flex flex-col items-start gap-1 text-xs text-stone-500">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://feathericons.com')"
                    @mouseenter="showLinkTooltip('https://feathericons.com', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="feather" class="h-3.5 w-3.5 shrink-0" />
                    Feather Icons
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://tauri.app')"
                    @mouseenter="showLinkTooltip('https://tauri.app', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="box" class="h-3.5 w-3.5 shrink-0" />
                    Tauri, Vue &amp; Rust
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/pdeljanov/Symphonia')"
                    @mouseenter="showLinkTooltip('https://github.com/pdeljanov/Symphonia', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="headphones" class="h-3.5 w-3.5 shrink-0" />
                    Symphonia
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://www.sqlite.org')"
                    @mouseenter="showLinkTooltip('https://www.sqlite.org', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="database" class="h-3.5 w-3.5 shrink-0" />
                    SQLite
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://tailwindcss.com')"
                    @mouseenter="showLinkTooltip('https://tailwindcss.com', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="wind" class="h-3.5 w-3.5 shrink-0" />
                    Tailwind CSS
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://www.rockbox.org')"
                    @mouseenter="showLinkTooltip('https://www.rockbox.org', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="music" class="h-3.5 w-3.5 shrink-0" />
                    Rockbox community
                  </button>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'theme'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="sun"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Theme
              </p>
              <div class="settings-section">
                <p class="mb-1 text-xs font-medium text-stone-500">
                  Choose your palette
                </p>
                <div
                  class="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2 md:grid-cols-3"
                >
                  <button
                    v-for="opt in themeOptions"
                    :key="opt.value"
                    type="button"
                    class="group flex items-center gap-3 rounded-md border px-2.5 py-2 text-left text-xs transition"
                    :class="
                      theme === opt.value
                        ? 'settings-option-card--active shadow-inner'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
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
                          class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
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
                  "Auto" follows your OS preference. "Orkish" uses a
                  parchment-like light theme; "DOOM" is a high-contrast dark
                  theme.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="sunrise"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Maximized player glow
                </p>
                <p class="text-[11px] text-stone-500">
                  Colorful blurry shadows behind the album art in fullscreen
                  mode, derived from the cover.
                </p>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="opt in playerGlowModeOptions"
                    :key="opt.value"
                    type="button"
                    class="flex flex-col gap-0.5 rounded-md border px-2.5 py-2 text-left transition"
                    :class="
                      playerGlowMode === opt.value
                        ? 'settings-option-card--active'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
                    @click="settingsStore.setPlayerGlowMode(opt.value)"
                  >
                    <span class="text-xs font-medium text-stone-200">{{
                      opt.label
                    }}</span>
                    <span class="text-[10px] text-stone-500">{{
                      opt.description
                    }}</span>
                  </button>
                </div>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="opt in playerGlowOptions"
                    :key="opt.value"
                    type="button"
                    class="rounded-md border px-2.5 py-1.5 text-xs font-medium transition"
                    :class="
                      playerGlowIntensity === opt.value
                        ? 'settings-option-card--active'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
                    @click="settingsStore.setPlayerGlowIntensity(opt.value)"
                  >
                    {{ opt.label }}
                  </button>
                </div>
                <div
                  v-if="!glowSettingsDisabled && playerGlowMode === 'dynamic'"
                  class="mt-3 space-y-3"
                >
                  <p class="text-[11px] text-stone-500">
                    Muorg picks one of three effects, in order:
                  </p>
                  <ul
                    class="list-inside list-decimal space-y-0.5 text-[11px] text-stone-500"
                  >
                    <li>
                      <strong class="text-stone-400">Vivid</strong> uses the
                      average album cover color if it's not bland.
                    </li>
                    <li>
                      If the average is bland but the edges are colorful, we use
                      <strong class="text-stone-400">Edge blur</strong>.
                    </li>
                    <li>
                      Otherwise, if it's all bland (e.g. a white cover), we do a
                      <strong class="text-stone-400">Bland</strong> soft glow.
                    </li>
                  </ul>
                  <div class="flex flex-wrap gap-4">
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Vivid
                      </p>
                      <p class="text-[10px] text-stone-600">
                        Average cover color is vivid: procedural blobs from
                        center
                      </p>
                      <div
                        :key="`vivid-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <template v-if="glowDemoBlobs.length">
                            <div
                              v-for="(blob, i) in glowDemoBlobs"
                              :key="`vivid-${i}`"
                              class="pointer-events-none absolute inset-0 origin-top-left"
                              :style="getGlowDemoBlobStyle(blob)"
                            />
                          </template>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Edge blur
                      </p>
                      <p class="text-[10px] text-stone-600">
                        Average bland, edges vivid: blurred album art
                      </p>
                      <div
                        :key="`edge-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <div
                            v-if="edgeBlurDemoOpacity > 0"
                            class="glow-demo-edge-blur pointer-events-none absolute inset-0 flex items-center justify-center"
                            :style="{ opacity: edgeBlurDemoOpacity }"
                          >
                            <div
                              class="h-32 w-32 flex-shrink-0 rounded-lg"
                              style="
                                background: radial-gradient(
                                  ellipse at center,
                                  #d4d4d4 0%,
                                  #3b82f6 25%,
                                  #8b5cf6 45%,
                                  #ec4899 65%,
                                  #f59e0b 85%,
                                  #22c55e 100%
                                );
                                filter: blur(40px);
                              "
                            />
                          </div>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Bland
                      </p>
                      <p class="text-[10px] text-stone-600">
                        All bland (e.g. white cover): soft glow of that color
                      </p>
                      <div
                        :key="`bland-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <template v-if="blandGlowDemoBlobs.length">
                            <div
                              v-for="(blob, i) in blandGlowDemoBlobs"
                              :key="`bland-${i}`"
                              class="pointer-events-none absolute inset-0 origin-top-left"
                              :style="getGlowDemoBlobStyle(blob)"
                            />
                          </template>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- Single-mode previews when not Dynamic -->
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'vivid'"
                  class="mt-3"
                >
                  <div
                    :key="`vivid-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <template v-if="glowDemoBlobs.length">
                        <div
                          v-for="(blob, i) in glowDemoBlobs"
                          :key="`vivid-p-${i}`"
                          class="pointer-events-none absolute inset-0 origin-top-left"
                          :style="getGlowDemoBlobStyle(blob)"
                        />
                      </template>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'edge-blur'"
                  class="mt-3"
                >
                  <div
                    :key="`edge-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <div
                        v-if="edgeBlurDemoOpacity > 0"
                        class="glow-demo-edge-blur pointer-events-none absolute inset-0 flex items-center justify-center"
                        :style="{ opacity: edgeBlurDemoOpacity }"
                      >
                        <div
                          class="h-32 w-32 flex-shrink-0 rounded-lg"
                          style="background: radial-gradient(ellipse at center, #d4d4d4 0%, #3b82f6 25%, #8b5cf6 45%, #ec4899 65%, #f59e0b 85%, #22c55e 100%); filter: blur(40px);"
                        />
                      </div>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'bland'"
                  class="mt-3"
                >
                  <div
                    :key="`bland-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <template v-if="blandGlowDemoBlobs.length">
                        <div
                          v-for="(blob, i) in blandGlowDemoBlobs"
                          :key="`bland-p-${i}`"
                          class="pointer-events-none absolute inset-0 origin-top-left"
                          :style="getGlowDemoBlobStyle(blob)"
                        />
                      </template>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- Glow off: intensity set to Off -->
                <div
                  v-else-if="glowSettingsDisabled"
                  class="mt-3 inline-block overflow-hidden rounded-lg border border-stone-600 shadow-lg opacity-60"
                  :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                >
                  <div
                    class="relative flex h-64 w-56 flex-col items-center justify-center"
                  >
                    <div class="relative z-10 flex flex-col items-center gap-2">
                      <div
                        class="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                      >
                        <span
                          class="inline-flex items-center justify-center text-2xl text-stone-500"
                          aria-hidden="true"
                        >
                          ♪
                        </span>
                      </div>
                      <span
                        class="max-w-[180px] truncate text-center text-xs font-medium text-stone-500 drop-shadow-md"
                      >
                        Glow off
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'playback'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="play-circle"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Playback
              </p>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="play"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Behavior
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="autoplayOnSelect"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setAutoplayOnSelect(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Autoplay on track selection
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, selecting a track immediately starts playback.
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="continuousPlayback"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setContinuousPlayback(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Continuous playback
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, playback continues to the next track
                  automatically.
                </p>
              </div>
              <div class="settings-section space-y-2">
                <p class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400">
                  <FeatherIcon name="activity" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
                  ReplayGain
                </p>
                <p class="text-xs text-stone-500">
                  ReplayGain is loudness metadata stored in audio tags. Muorg reads these values and adjusts playback volume so tracks and albums play at a more consistent perceived level.
                </p>
                <div class="flex flex-wrap gap-1">
                  <button
                    v-for="opt in replayGainOptions"
                    :key="opt.value"
                    type="button"
                    class="rounded border px-2 py-1 text-xs"
                    :class="replayGainMode === opt.value ? 'border-stone-400 bg-stone-700 text-stone-100' : 'border-stone-600 text-stone-400 hover:bg-stone-700'"
                    @click="settingsStore.setReplayGainMode(opt.value)"
                  >
                    {{ opt.label }}
                  </button>
                </div>
                <p class="text-xs text-stone-500">
                  Off: ignore ReplayGain tags. Track: use each track's gain value (best for mixed playlists). Album: use album gain values to preserve loudness differences within an album.
                </p>
                <label class="flex items-center gap-2 text-xs text-stone-500">
                  Preamp (dB)
                  <input
                    type="number"
                    step="0.5"
                    min="-12"
                    max="12"
                    class="w-20 rounded border border-stone-600 bg-stone-800 px-2 py-0.5 text-stone-200"
                    :value="replayGainPreampDb"
                    @input="settingsStore.setReplayGainPreampDb(Number(($event.target as HTMLInputElement).value))"
                  />
                </label>
                <p class="text-xs text-stone-500">
                  Preamp is applied on top of ReplayGain. Positive values make playback louder; negative values add headroom.
                </p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="replayGainPreventClipping"
                    class="rounded border-stone-600"
                    @change="settingsStore.setReplayGainPreventClipping(($event.target as HTMLInputElement).checked)"
                  />
                  Prevent clipping
                </label>
                <p class="text-xs text-stone-500">
                  ReplayGain is applied only during playback and does not edit audio files. Clipping prevention caps output gain to avoid distortion when boosted levels would exceed safe output.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="music"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Playbar
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarShowAlbumInMarquee"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarShowAlbumInMarquee(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Show album in scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the album name is shown next to the track title
                  in the scrolling marquee.
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarDisableMarquee"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarDisableMarquee(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Disable scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the track title is truncated instead of
                  scrolling.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="star"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Ratings
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarShowRatingInMaximized"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarShowRatingInMaximized(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Show rating on maximized player
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, a star rating control is shown above the
                  playback controls in the maximized player.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'table'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="layout"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Layout
              </p>

              <!-- 1: Main panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Main panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="layers"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Library grouping
                  </p>
                  <p class="mb-1.5 text-xs font-medium text-stone-500">
                    Default grouping
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in defaultGroupByOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        defaultGroupBy === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="setDefaultGroupBy(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-medium text-stone-200">
                          {{ opt.label }}
                          <span
                            v-if="defaultGroupBy === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-stone-500">
                          {{ opt.description }}
                        </p>
                      </div>
                    </button>
                  </div>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="defaultGroupsExpanded"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          setDefaultGroupsExpanded(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Expand groups by default
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    Controls how your library is grouped and whether groups
                    start expanded when you open Muorg.
                  </p>
                </div>

                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="grid"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Table density
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in tableDensityOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        tableDensity === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="settingsStore.setTableDensity(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-semibold text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="tableDensity === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
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
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="image"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Group header album art
                  </p>
                  <label
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="groupHeaderAlbumArt"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setGroupHeaderAlbumArt(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art in Album group headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="groupHeaderAlbumArtForArtist"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setGroupHeaderAlbumArtForArtist(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art in Artist group headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="splitAlbumHeadersByArtist"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setSplitAlbumHeadersByArtist(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Split album headers by artist
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When off, albums with the same name are merged into one group regardless of artist.
                  </p>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="hideGroupTrackCount"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideGroupTrackCount(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide track count in headers
                  </label>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="hideAlbumArtColInAlbumGroups"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideAlbumArtColInAlbumGroups(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide album art column when grouped by Album
                  </label>
                </div>
                <div class="settings-section">
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="columns"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Table columns
                  </p>
                  <label
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColAlbumArt"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColAlbumArt(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show album art
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColRating"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColRating(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show rating
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColYear"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColYear(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show year
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColDuration"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColDuration(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show duration
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColFormat"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColFormat(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show file format
                  </label>
                  <label
                    class="mt-1 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="tableColPath"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setTableColPath(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Show file path
                  </label>
                </div>
              </div>

              <!-- 2: Bottom panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Bottom panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="layout"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Bottom bar
                  </p>
                  <p class="mb-2 text-xs text-stone-500">
                    Default tab on startup
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in defaultBottomPanelOptions"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,12rem)] flex-col rounded-lg border px-3 py-2.5 text-left text-xs transition"
                      :class="
                        defaultBottomPanel === opt.value
                          ? 'settings-option-card--active shadow-inner'
                          : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="settingsStore.setDefaultBottomPanel(opt.value)"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-semibold text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="defaultBottomPanel === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
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
              </div>
              <!-- 3: Side panel -->
              <div class="flex items-center gap-3 pt-1">
                <span class="shrink-0 text-[11px] font-semibold uppercase tracking-wide text-stone-500">Side panel</span>
                <div class="flex-1 border-t border-stone-700/70"></div>
              </div>
              <div class="space-y-3">
                <div class="settings-section">
                  <p
                    class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                  >
                    <FeatherIcon
                      name="sidebar"
                      class="h-3.5 w-3.5 shrink-0 text-stone-500"
                    />
                    Sidebar
                  </p>
                  <p class="mb-1 text-xs font-medium text-stone-500">
                    Default sidebar panel
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="opt in [
                        { value: 'folders', label: 'Folders', desc: 'Show library folders by default.' },
                        { value: 'reports', label: 'Reports', desc: 'Show reports by default.' },
                        { value: 'playlists', label: 'Playlists', desc: 'Show playlists by default.' },
                      ]"
                      :key="opt.value"
                      type="button"
                      class="flex min-w-0 flex-1 basis-[min(100%,10rem)] items-center justify-between rounded-lg border px-3 py-1.5 text-left text-xs transition"
                      :class="
                        settingsStore.sidebarDefaultTab === opt.value
                          ? 'settings-option-card--active shadow-inner text-stone-100'
                          : 'border-stone-600 bg-stone-900/60 text-stone-300 hover:border-stone-400 hover:bg-stone-800'
                      "
                      @click="
                        settingsStore.setSidebarDefaultTab(
                          opt.value as 'folders' | 'reports' | 'playlists',
                        )
                      "
                    >
                      <div class="min-w-0 flex-1">
                        <p class="font-medium text-stone-100">
                          {{ opt.label }}
                          <span
                            v-if="settingsStore.sidebarDefaultTab === opt.value"
                            class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                          >
                            Active
                          </span>
                        </p>
                        <p class="mt-0.5 text-[11px] text-stone-400">{{ opt.desc }}</p>
                      </div>
                    </button>
                  </div>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="settingsStore.sidebarClosedOnStartup"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setSidebarClosedOnStartup(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Start with sidebar closed
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When enabled, the library sidebar is collapsed when you open
                    Muorg.
                  </p>
                  <label
                    class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="settingsStore.hideReportsSection"
                      class="rounded border-stone-600"
                      @change="
                        (e) =>
                          settingsStore.setHideReportsSection(
                            (e.target as HTMLInputElement).checked,
                          )
                      "
                    />
                    Hide reports section in sidebar
                  </label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    When enabled, the reports block (Missing metadata,
                    Duplicates, Missing album cover) is hidden from the main
                    sidebar layout.
                  </p>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'keyboard'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="command"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Keyboard
              </p>
              <div class="settings-section space-y-2">
                <p class="text-xs text-stone-400">
                  Keyboard shortcuts are currently fixed. Planned improvements
                  include per-action customization and profile export/import.
                </p>
                <p class="text-[11px] text-stone-500">
                  These are the same shortcuts shown in the key map:
                </p>
                <dl class="mt-1 space-y-2 text-xs">
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+F / ⌘F
                    </dt>
                    <dd class="min-w-0 text-stone-300">Focus search bar</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+R / ⌘R
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Refresh whole library (all folders, all reports)
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+M / ⌘M
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Toggle metadata editor panel for current selection
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+L / ⌘L
                    </dt>
                    <dd class="min-w-0 text-stone-300">Show library panel</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+P / ⌘P
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Toggle full player panel
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+A / ⌘A
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Select all tracks in current view and enable multi-select
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+K / ⌘K
                    </dt>
                    <dd class="min-w-0 text-stone-300">Open key map</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Escape
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Close metadata editor panel or cover popup
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      ↓ Arrow Down
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Move focus down in track list
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      ↑ Arrow Up
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Move focus up in track list
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Space
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      On group row: expand or collapse. On track row: select
                      (add to selection in multi-select).
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Enter
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      With one track selected: start playback or pause if
                      already playing.
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <div v-show="settingsTab === 'reports'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="bar-chart-2"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Reports
              </p>

              <div class="settings-section">
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
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
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="list"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Missing metadata fields
                </p>
                <p class="mb-1 text-xs text-stone-500">
                  Choose which fields must be present for a track to be
                  considered "complete". Tracks missing any of these fields will
                  appear in the "Missing metadata" report.
                </p>
                <div class="grid grid-cols-2 gap-1">
                  <label
                    v-for="opt in missingMetadataFieldOptions"
                    :key="opt.value"
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="missingMetadataFields.includes(opt.value)"
                      class="rounded border-stone-600"
                      @change="
                        (e) => {
                          const checked = (e.target as HTMLInputElement)
                            .checked;
                          const set = new Set(missingMetadataFields);
                          if (checked) set.add(opt.value);
                          else set.delete(opt.value);
                          settingsStore.setMissingMetadataFields(
                            Array.from(set),
                          );
                        }
                      "
                    />
                    {{ opt.label }}
                  </label>
                </div>
              </div>
            </div>

            <div v-show="settingsTab === 'exports'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="download"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Exports
              </p>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="folder"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Music Root Folder
                </p>
                <input
                  :value="musicRootFolder"
                  type="text"
                  :placeholder="musicRootFolderPlaceholder"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-800 px-2.5 py-1.5 text-xs text-stone-200 placeholder:text-stone-500 focus:border-stone-500 focus:outline-none focus:ring-1 focus:ring-stone-500"
                  @input="
                    settingsStore.setMusicRootFolder(
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
                <p class="mt-1.5 text-xs text-stone-500">
                  Enter just the folder name (e.g.
                  <code class="rounded bg-stone-700 px-1 font-mono text-[11px]"
                    >Music</code
                  >), not a full path. This should be the root folder that is
                  scanned into your library. It is used to generate relative
                  paths inside exported playlist files.
                </p>
              </div>
              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="shield"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Metadata Backup
                </p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="backupBeforeWrite"
                    class="rounded border-stone-600"
                    @change="settingsStore.setBackupBeforeWrite(($event.target as HTMLInputElement).checked)"
                  />
                  Backup file before metadata writes
                </label>
                <p class="mt-1.5 text-xs text-stone-500">
                  When enabled, Muorg creates a backup copy before writing tags.
                  This gives you a safety net and enables quick restore from the
                  Metadata panel if an edit goes wrong.
                </p>
              </div>
            </div>

            <div v-show="settingsTab === 'smart_suggestions'" class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="zap"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Smart Suggestions
              </p>

              <div class="settings-section space-y-1.5">
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="hideWikipediaCoverSearch"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setHideWikipediaCoverSearch(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Hide Wikipedia album cover search
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the "From Wikipedia" (globe) button for album
                  art is hidden in the metadata editor and on album group
                  headers.
                </p>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <label class="block text-xs font-medium text-stone-500">Path formats (for metadata suggestions)</label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    Add one pattern per folder structure. When applying from path, all patterns are tried and the one that extracts the most fields wins.
                  </p>

                  <!-- Active patterns list -->
                  <div class="mt-2 space-y-1.5">
                    <div
                      v-for="(template, i) in pathFormatTemplates"
                      :key="i"
                      class="flex items-center gap-1.5"
                    >
                      <input
                        type="text"
                        :value="template"
                        class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200"
                        placeholder="e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"
                        @input="updateTemplate(i, ($event.target as HTMLInputElement).value)"
                      />
                      <button
                        type="button"
                        class="shrink-0 rounded p-1 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
                        title="Remove pattern"
                        @click="removeTemplate(i)"
                      >
                        <FeatherIcon name="x" class="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <button
                      type="button"
                      class="mt-1 flex items-center gap-1 text-xs text-stone-500 hover:text-stone-300"
                      @click="addTemplate"
                    >
                      <FeatherIcon name="plus" class="h-3.5 w-3.5" />
                      Add pattern
                    </button>
                  </div>

                  <!-- Example patterns to add -->
                  <p class="mt-3 text-xs font-medium text-stone-500">
                    Example patterns (click to add):
                  </p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li v-for="(ex, i) in pathFormatExamples" :key="i">
                      <button
                        type="button"
                        class="path-format-example-btn w-full break-all rounded border px-2 py-1 font-mono text-left"
                        :class="pathFormatTemplates.includes(ex) ? 'opacity-40 cursor-default' : ''"
                        :title="pathFormatTemplates.includes(ex) ? 'Already added' : 'Add this pattern'"
                        @click="addExamplePattern(ex)"
                      >
                        {{ i + 1 }}. {{ ex }}
                      </button>
                    </li>
                  </ul>
                </div>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <p class="text-xs font-medium text-stone-500">
                    Matching path examples (click to try):
                  </p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li
                      v-for="(p, i) in pathFormatExamplePaths"
                      :key="'path-' + i"
                    >
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
                  <div v-if="pathFormatTemplates.some(t => t.trim())" class="mt-2 border-t border-stone-700/60 pt-2">
                    <div v-if="pathFormatExampleExtracted">
                      <p class="text-[11px] text-stone-500">
                        Matched by:
                        <span class="font-mono text-stone-400">{{ pathFormatExampleBestPattern }}</span>
                      </p>
                      <p class="mt-1.5 text-xs font-medium text-stone-400">Extracted fields</p>
                      <table class="mt-1.5 w-full border-collapse text-xs">
                        <thead>
                          <tr class="border-b border-stone-600">
                            <th class="py-1.5 pr-3 text-left font-medium text-stone-500">Field</th>
                            <th class="py-1.5 text-left font-medium text-stone-500">Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr
                            v-for="(val, key) in pathFormatExampleExtracted"
                            :key="key"
                            class="border-b border-stone-700/50"
                          >
                            <td class="py-1.5 pr-3 font-mono text-stone-400">{{ key }}</td>
                            <td class="py-1.5 text-stone-300">{{ val || "—" }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <p v-else class="mt-1 text-xs text-amber-500">
                      No pattern matches the example path.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          <!-- ── Smart Transform ───────────────────────────────────── -->
          <div v-show="settingsTab === 'smart_transform'" class="space-y-3 flex flex-col h-full">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400 shrink-0">
              <FeatherIcon name="shuffle" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Smart Transform
            </p>

            <!-- Pattern inputs -->
            <div class="settings-section shrink-0">
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs font-medium text-stone-500 mb-1">Origin pattern</label>
                  <input
                    v-model="transformOrigin"
                    type="text"
                    class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. &lt;Artist&gt;/&lt;Album&gt;/&lt;TrackNumber&gt; - &lt;TrackTitle&gt;.&lt;Format&gt;"
                  />
                </div>
                <div>
                  <label class="block text-xs font-medium text-stone-500 mb-1">Destination pattern</label>
                  <input
                    v-model="transformDest"
                    type="text"
                    class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. &lt;AlbumArtist&gt;/&lt;Year&gt; - &lt;Album&gt;/&lt;TrackNumber&gt; - &lt;TrackTitle&gt;.&lt;Format&gt;"
                  />
                </div>
              </div>
              <p class="mt-2 text-xs text-stone-500">
                Tracks whose path matches the Origin pattern are listed below. The path prefix before the matched segments is preserved; only the matched segments are rewritten using the Destination pattern.
              </p>
            </div>

            <!-- Match list -->
            <div class="settings-section flex-1 min-h-0 flex flex-col overflow-hidden p-0">
              <!-- Header row -->
              <div class="flex items-center gap-2 border-b border-stone-700 px-3 py-2 shrink-0">
                <input
                  type="checkbox"
                  :checked="transformAllSelected"
                  :indeterminate="transformSelectedIds.size > 0 && !transformAllSelected"
                  class="shrink-0"
                  @change="toggleTransformSelectAll"
                />
                <span class="flex-1 text-xs font-medium text-stone-500">Origin path</span>
                <span class="w-5 shrink-0" />
                <span class="flex-1 text-xs font-medium text-stone-500">Transformed path</span>
              </div>

              <!-- No patterns yet -->
              <div v-if="!transformOrigin.trim() || !transformDest.trim()" class="flex-1 flex items-center justify-center">
                <p class="text-xs text-stone-500">Enter an Origin and Destination pattern to see matching tracks.</p>
              </div>

              <!-- No matches -->
              <div v-else-if="transformMatches.length === 0" class="flex-1 flex items-center justify-center">
                <p class="text-xs text-stone-500">No tracks match the Origin pattern.</p>
              </div>

              <!-- Match rows -->
              <div v-else class="flex-1 overflow-y-auto">
                <div
                  v-for="m in transformMatches"
                  :key="m.track.id"
                  class="flex items-start gap-2 border-b border-stone-700/50 px-3 py-1.5 cursor-pointer hover:bg-stone-700/30"
                  :class="transformSelectedIds.has(m.track.id) ? 'bg-stone-700/20' : ''"
                  @click="toggleTransformTrack(m.track.id)"
                >
                  <input
                    type="checkbox"
                    :checked="transformSelectedIds.has(m.track.id)"
                    class="mt-0.5 shrink-0 pointer-events-none"
                  />
                  <span class="flex-1 font-mono text-xs text-stone-400 break-all">{{ m.track.path }}</span>
                  <FeatherIcon name="arrow-right" class="h-3 w-3 shrink-0 mt-0.5 text-stone-500" />
                  <span class="flex-1 font-mono text-xs text-stone-300 break-all">{{ m.newPath }}</span>
                </div>
              </div>
            </div>

            <!-- Bottom bar -->
            <div class="shrink-0 flex items-center justify-between gap-3">
              <p v-if="transformError" class="text-xs text-red-400">{{ transformError }}</p>
              <p v-else-if="transformMatches.length > 0" class="text-xs text-stone-500">
                {{ transformSelectedIds.size }} of {{ transformMatches.length }} selected
              </p>
              <span v-else />
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-40"
                :disabled="transformSelectedIds.size === 0"
                @click="runTransform"
              >
                Transform
              </button>
            </div>
          </div>

          <!-- ── Statistics ──────────────────────────────────────────── -->
          <div v-show="settingsTab === 'statistics'" class="space-y-6">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400">
              <FeatherIcon name="pie-chart" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Library Statistics
            </p>
            <p class="text-xs text-stone-500">
              Insights based on all {{ store.tracks.length }} tracks in your library.
            </p>

            <!-- Metadata health -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">Metadata completeness</p>
              <MetadataHealthChart :tracks="store.tracks" @view-field="handleViewField" />
            </div>

            <!-- Genre distribution -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-4 text-xs font-semibold text-stone-400">Genre distribution</p>
              <GenrePieChart :tracks="store.tracks" />
            </div>

            <!-- Top artists -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-4 text-xs font-semibold text-stone-400">Top artists by track count</p>
              <TopArtistsChart :tracks="store.tracks" />
            </div>

            <!-- Release year line chart -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">Tracks per release year</p>
              <YearLineChart :tracks="store.tracks" />
            </div>

            <!-- User ratings -->
            <div class="rounded-lg border border-stone-700 bg-stone-900/60 p-4">
              <p class="mb-3 text-xs font-semibold text-stone-400">User ratings</p>
              <RatingChart :tracks="store.tracks" />
            </div>
          </div>

          <!-- Connection tab -->
          <div v-show="settingsTab === 'connection'" class="w-full space-y-4">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400">
              <FeatherIcon name="wifi" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Server Connection
            </p>

            <!-- Local / Online segmented control -->
            <div class="flex rounded-lg border border-stone-600 bg-stone-900/60 p-0.5 text-xs font-medium">
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'local' ? 'bg-stone-600 text-stone-100 shadow-sm' : 'text-stone-400 hover:text-stone-200'"
                @click="switchMode('local')"
              >
                Local
              </button>
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'online' ? 'bg-stone-600 text-stone-100 shadow-sm' : 'text-stone-400 hover:text-stone-200'"
                @click="switchMode('online')"
              >
                Online
              </button>
            </div>

            <!-- Local: no config needed -->
            <p v-if="connMode === 'local'" class="text-[11px] text-stone-500 leading-relaxed">
              Connects to MuorgServer running on this machine. No configuration needed.
            </p>

            <!-- Online fields -->
            <div v-else class="settings-section space-y-3">
              <div>
                <label class="mb-1 block text-xs font-medium text-stone-400">Server URL</label>
                <input
                  v-model="connOnlineUrl"
                  type="url"
                  placeholder="https://muorg.example.com"
                  class="w-full rounded border border-stone-600 bg-stone-900 px-3 py-1.5 text-xs text-stone-200 placeholder-stone-600 focus:border-stone-400 focus:outline-none"
                  @input="connStatus = 'idle'"
                />
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-stone-400">API Key</label>
                <div class="flex items-center gap-2">
                  <input
                    v-model="connOnlineApiKey"
                    :type="connOnlineApiKeyVisible ? 'text' : 'password'"
                    placeholder="Enter API key"
                    class="flex-1 rounded border border-stone-600 bg-stone-900 px-3 py-1.5 text-xs text-stone-200 placeholder-stone-600 focus:border-stone-400 focus:outline-none"
                    @input="connStatus = 'idle'"
                  />
                  <button
                    type="button"
                    class="rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-stone-400 hover:bg-stone-700"
                    @click="connOnlineApiKeyVisible = !connOnlineApiKeyVisible"
                  >
                    <FeatherIcon :name="connOnlineApiKeyVisible ? 'eye-off' : 'eye'" class="h-3.5 w-3.5" />
                  </button>
                </div>
                <p class="mt-1 text-[11px] text-stone-500">Folders list will be read-only in online mode</p>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex items-center gap-3">
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                :disabled="connStatus === 'saving'"
                @click="applyAndReload"
              >
                {{ connStatus === 'saving' ? 'Connecting…' : 'Save & reload' }}
              </button>
              <span v-if="connStatus === 'ok'" class="flex items-center gap-1.5 text-xs text-green-400">
                <FeatherIcon name="check-circle" class="h-3.5 w-3.5" /> Connected
              </span>
              <span v-if="connStatus === 'error'" class="flex items-center gap-1.5 text-xs text-red-400">
                <FeatherIcon name="x-circle" class="h-3.5 w-3.5" /> {{ connError || 'Failed' }}
              </span>
            </div>

            <!-- Refresh -->
            <div class="flex items-center gap-3">
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                :disabled="refreshStatus === 'loading'"
                @click="refreshFromServer"
              >
                <span class="flex items-center gap-1.5">
                  <FeatherIcon :name="refreshStatus === 'loading' ? 'loader' : 'refresh-cw'" class="h-3 w-3" :class="refreshStatus === 'loading' ? 'animate-spin' : ''" />
                  {{ refreshStatus === 'loading' ? 'Refreshing…' : 'Refresh data from server' }}
                </span>
              </button>
              <span v-if="refreshStatus === 'ok'" class="flex items-center gap-1.5 text-xs text-green-400">
                <FeatherIcon name="check-circle" class="h-3.5 w-3.5" /> Done
              </span>
              <span v-if="refreshStatus === 'error'" class="flex items-center gap-1.5 text-xs text-red-400">
                <FeatherIcon name="x-circle" class="h-3.5 w-3.5" /> {{ refreshError || 'Failed' }}
              </span>
            </div>
          </div>

          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <Teleport to="body">
    <div
      v-if="linkTooltip"
      class="pointer-events-none fixed z-[500] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-300 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)] whitespace-nowrap"
      :style="{ left: linkTooltip.x + 'px', top: linkTooltip.y + 'px', transform: 'translate(-50%, -100%)' }"
    >
      {{ linkTooltip.text }}
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
      <div
        class="w-full max-w-sm rounded-lg border border-stone-600 bg-stone-800 p-4 shadow-xl"
        @click.stop
      >
        <h2
          id="update-complete-title"
          class="text-sm font-semibold text-stone-200"
        >
          Update installed
        </h2>
        <p class="mt-2 text-xs text-stone-400">
          Version {{ updateCompleteVersion }} has been installed. Restart the
          app to use the new version.
        </p>
        <div class="mt-4 flex justify-end gap-2">
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="closeUpdateCompleteModal"
          >
            Later
          </button>
          <button
            type="button"
            class="settings-action-btn rounded px-3 py-1.5 text-sm font-medium"
            @click="restartAfterUpdate"
          >
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
  background-color: rgba(91, 124, 50, 0.24);
  color: rgb(231 229 228);
}
.settings-tab-btn--active:hover {
  background-color: rgba(91, 124, 50, 0.28);
  color: rgb(231 229 228);
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

/* Glow demos: padding for blur to extend; overflow hidden clips to rounded box */
.glow-demo-container {
  overflow: hidden;
}
</style>
