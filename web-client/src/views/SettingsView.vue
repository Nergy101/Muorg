<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div ref="scroller" class="content-col-children min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <!-- Brand mark at the top, centred -->
      <div class="flex justify-center px-4 pt-6 pb-3">
        <img :src="logoUrl" alt="Muorg" class="h-16 w-16" />
      </div>

      <!-- ─── Muorg Info ─────────────────────────────────────────────── -->
      <div :class="SECTION">Muorg Info</div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Version</p>
        </div>
        <button
          v-if="pwaUpdateAvailable"
          type="button"
          class="flex items-center gap-1 px-2 text-label-lg text-primary"
          @click="refreshApp"
        >
          <MageIcon name="reload" class="h-4 w-4" />
          <span>Refresh now</span>
        </button>
        <span class="text-body-md text-on-surface-variant">{{ version }}</span>
      </div>
      <div v-if="canInstall" :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Install app</p>
          <p v-if="isIos" class="text-body-sm text-on-surface-variant">
            Use Share → Add to Home Screen
          </p>
        </div>
        <button type="button" class="flex items-center gap-1 px-2 text-label-lg text-primary" @click="install()">
          <MageIcon name="download" class="h-4 w-4" />
          <span>Install</span>
        </button>
      </div>

      <!-- ─── Playback ───────────────────────────────────────────────── -->
      <div :class="[SECTION, 'flex items-center gap-1.5']">
        <MageIcon name="play" class="h-3.5 w-3.5" />
        <span>Playback</span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Continuous playback</p>
          <p class="text-body-sm text-on-surface-variant">
            Automatically play next track when queue ends
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.continuousPlayback)"
          role="switch"
          :aria-checked="settings.continuousPlayback"
          @click="settings.setContinuousPlayback(!settings.continuousPlayback)"
        >
          <span :class="knobClass(settings.continuousPlayback)" />
        </button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Tap mini player</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.miniPlayerTapOpensPlayer ? "Opens full player screen" : "Plays / pauses" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.miniPlayerTapOpensPlayer)"
          role="switch"
          :aria-checked="settings.miniPlayerTapOpensPlayer"
          @click="settings.setMiniPlayerTapOpensPlayer(!settings.miniPlayerTapOpensPlayer)"
        >
          <span :class="knobClass(settings.miniPlayerTapOpensPlayer)" />
        </button>
      </div>

      <!-- ─── Library ────────────────────────────────────────────────── -->
      <div :class="[SECTION, 'flex items-center gap-1.5']">
        <MageIcon name="music" class="h-3.5 w-3.5" />
        <span>Library</span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Default sort order</p>
        </div>
        <div ref="sortMenuRef" class="relative">
          <button
            type="button"
            class="flex items-center gap-1 px-2 text-label-lg text-primary"
            @click="sortMenuOpen = !sortMenuOpen"
          >
            <span>{{ sortLabel }}</span>
            <MageIcon name="chevron-down" class="h-4 w-4" />
          </button>
          <div
            v-if="sortMenuOpen"
            class="absolute right-0 top-full z-20 mt-1 min-w-[9rem] rounded-xl bg-surface-container py-1 shadow-xl"
          >
            <button
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              type="button"
              class="flex w-full items-center justify-between gap-3 px-4 py-2 text-left text-body-md text-on-surface"
              @click="selectSort(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <MageIcon
                v-if="settings.sortMode === opt.value"
                name="check"
                class="h-4 w-4 text-primary"
              />
            </button>
          </div>
        </div>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Sort direction</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.sortAscending ? "Ascending" : "Descending" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.sortAscending)"
          role="switch"
          :aria-checked="settings.sortAscending"
          @click="settings.setSortAscending(!settings.sortAscending)"
        >
          <span :class="knobClass(settings.sortAscending)" />
        </button>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Layout</p>
        <SegmentedControl
          :model-value="settings.albumViewStyle"
          :options="LAYOUT_OPTIONS"
          @update:model-value="settings.setAlbumViewStyle($event as AlbumViewStyle)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Reduce motion</p>
          <p class="text-body-sm text-on-surface-variant">
            Keep navigation, skip the slide and fade travel
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.reduceMotion)"
          role="switch"
          :aria-checked="settings.reduceMotion"
          @click="settings.setReduceMotion(!settings.reduceMotion)"
        >
          <span :class="knobClass(settings.reduceMotion)" />
        </button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Open on last tab</p>
          <p class="text-body-sm text-on-surface-variant">
            Restore the last visited tab after a reload
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.openOnLastTab)"
          role="switch"
          :aria-checked="settings.openOnLastTab"
          @click="settings.setOpenOnLastTab(!settings.openOnLastTab)"
        >
          <span :class="knobClass(settings.openOnLastTab)" />
        </button>
      </div>

      <!-- ─── Theme ──────────────────────────────────────────────────── -->
      <div :class="[SECTION, 'flex items-center gap-1.5']">
        <MageIcon :name="themeIcon" class="h-3.5 w-3.5" />
        <span>Theme</span>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Mode</p>
        <SegmentedControl
          :model-value="settings.theme"
          :options="THEME_OPTIONS"
          @update:model-value="settings.setTheme($event as ThemeMode)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Use true black</p>
          <p class="text-body-sm text-on-surface-variant">
            OLED-friendly pitch-black background
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.trueBlack)"
          role="switch"
          :aria-checked="settings.trueBlack"
          @click="settings.setTrueBlack(!settings.trueBlack)"
        >
          <span :class="knobClass(settings.trueBlack)" />
        </button>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Accent color</p>
        <div class="flex flex-wrap gap-2.5">
          <button
            v-for="opt in ACCENT_OPTIONS"
            :key="opt.value"
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full transition-transform hover:scale-110"
            :class="settings.accent === opt.value ? 'ring-2 ring-on-surface ring-offset-2 ring-offset-background' : ''"
            :style="{ backgroundColor: opt.color }"
            :aria-label="opt.label"
            :aria-pressed="settings.accent === opt.value"
            @click="settings.setAccent(opt.value)"
          >
            <MageIcon v-if="settings.accent === opt.value" name="check" class="h-4 w-4 text-white" />
          </button>
        </div>
      </div>

      <!-- ─── Server ─────────────────────────────────────────────────── -->
      <div :class="[SECTION, 'flex items-center gap-1.5']">
        <MageIcon name="server" class="h-3.5 w-3.5" />
        <span>Server</span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Server URL</p>
        </div>
        <span class="min-w-0 truncate text-body-md text-on-surface-variant">{{ serverUrl }}</span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Refresh library</p>
        </div>
        <button
          type="button"
          class="px-2 text-label-lg text-primary disabled:opacity-60"
          :disabled="refreshStatus === 'loading'"
          @click="refresh"
        >{{ refreshLabel }}</button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Log out</p>
        </div>
        <button type="button" class="px-2 text-label-lg text-error" @click="logoutOpen = true">
          Log out
        </button>
      </div>

      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Tracks</span>
        <span class="text-body-md text-on-surface-variant">
          {{ trackCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Albums</span>
        <span class="text-body-md text-on-surface-variant">
          {{ albumCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Artists</span>
        <span class="text-body-md text-on-surface-variant">
          {{ artistCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Total duration</span>
        <span class="text-body-md text-on-surface-variant">{{ totalDurationLabel }}</span>
      </div>

      <!-- ─── Development ─────────────────────────────────────────────── -->
      <div :class="[SECTION, 'flex items-center gap-1.5']">
        <MageIcon name="wrench" class="h-3.5 w-3.5" />
        <span>Development</span>
      </div>
      <a
        :class="ROW"
        href="https://github.com/Nergy101/Muorg"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <MageIcon name="github" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <p class="text-body-lg text-on-surface">See github repo</p>
        </div>
      </a>

      <div class="h-6" />
    </div>

    <ConfirmDialog
      :open="logoutOpen"
      title="Log out?"
      message="This will clear your server URL and API key. You'll need to reconnect."
      confirm-label="Log out"
      danger
      @confirm="onLogout"
      @cancel="logoutOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import SegmentedControl from "../components/SegmentedControl.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { pwaUpdateAvailable, refreshApp } from "../composables/usePwaUpdate";
import { disconnect, getServerUrl } from "../api/client";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { useSettingsStore } from "../stores/settings";
import { useInstallPrompt } from "../composables/useInstallPrompt";
import { useScrollMemory } from "../composables/useScrollMemory";
import logoUrl from "../assets/muorg-logo.svg";
import type { AccentColor, AlbumViewStyle, SortMode, ThemeMode } from "../types";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];
const LAYOUT_OPTIONS = [
  { value: "grid", label: "Grid" },
  { value: "list", label: "List" },
  { value: "tracks", label: "Tracks" },
];
const THEME_OPTIONS = [
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "system", label: "Auto" },
];
/** Swatch colors mirror the accent palettes in style.css. */
const ACCENT_OPTIONS: { value: AccentColor; label: string; color: string }[] = [
  { value: "green", label: "Green", color: "#5b7c32" },
  { value: "blue", label: "Blue", color: "#4a7fc1" },
  { value: "purple", label: "Purple", color: "#7d63b8" },
  { value: "orange", label: "Orange", color: "#b97a2a" },
  { value: "red", label: "Red", color: "#b84a4a" },
  { value: "teal", label: "Teal", color: "#3d8f85" },
];

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const settings = useSettingsStore();
const { canInstall, isIos, install } = useInstallPrompt();

const version = __APP_VERSION__;
const serverUrl = computed(() => getServerUrl() || "Not configured");
// Theme icon follows the effective theme: moon when dark, sun when light.
// Auto (system) resolves to whichever the system is currently using, and the
// store's matchMedia listener keeps this reactive to live OS theme changes.
const themeIcon = computed(() => (settings.resolvedTheme === "dark" ? "moon" : "sun"));

function switchClass(on: boolean): string {
  return `flex h-8 w-[52px] shrink-0 items-center rounded-full px-1 transition-colors duration-200 ${
    on ? "bg-primary" : "bg-surface-variant"
  }`;
}

function knobClass(on: boolean): string {
  return `block h-6 w-6 rounded-full bg-white transition-transform duration-200 ${
    on ? "translate-x-5" : "translate-x-0"
  }`;
}

// --- Sort dropdown ---------------------------------------------------------

const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
);

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value) return;
  const el = sortMenuRef.value;
  if (el && !el.contains(e.target as Node)) sortMenuOpen.value = false;
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));

// --- Library refresh -------------------------------------------------------

const refreshStatus = ref<"idle" | "loading" | "success" | "error">("idle");

const refreshLabel = computed(() => {
  switch (refreshStatus.value) {
    case "loading":
      return "Refreshing…";
    case "success":
      return "Updated";
    case "error":
      return "Failed";
    default:
      return "Refresh";
  }
});

let refreshTimer: ReturnType<typeof setTimeout> | undefined;

async function refresh(): Promise<void> {
  refreshStatus.value = "loading";
  await lib.loadLibrary();
  refreshStatus.value = lib.error ? "error" : "success";
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => {
    refreshStatus.value = "idle";
    refreshTimer = undefined;
  }, 2000);
}

onUnmounted(() => clearTimeout(refreshTimer));

// --- Stats -----------------------------------------------------------------

// Prefer the server /api/stats totals; fall back to the loaded catalog so the
// counts always show even before the stats round-trip lands (or if it fails).
const trackCount = computed(() => lib.stats?.track_count ?? lib.tracks.length);
const albumCount = computed(() => {
  if (lib.stats != null) return lib.stats.album_count;
  return new Set(lib.tracks.map((t) => lib.keyForTrack(t))).size;
});
const artistCount = computed(() => {
  if (lib.stats != null) return lib.stats.artist_count;
  return new Set(lib.tracks.map((t) => t.album_artist ?? t.artist ?? "")).size;
});

const totalDurationLabel = computed(() => {
  const secs =
    lib.stats?.total_duration_secs ??
    lib.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
});

// --- Logout ----------------------------------------------------------------

const logoutOpen = ref(false);

async function onLogout(): Promise<void> {
  logoutOpen.value = false;
  disconnect();
  // Player first so audio stops before the library revokes its cover URLs.
  player.reset();
  lib.reset();
  playlistStore.reset();
  await router.replace({ name: "connect" });
}
</script>
