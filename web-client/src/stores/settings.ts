import { defineStore } from "pinia";
import { computed, ref, watchEffect } from "vue";
import { loadPref, savePref } from "./prefs";
import { DEFAULT_MISSING_METADATA_FIELDS, MISSING_METADATA_FIELDS } from "@shared/reports";
import type { MissingMetadataField } from "@shared/reports";
import type {
  AccentColor,
  AlbumViewStyle,
  PlaylistSortMode,
  SortMode,
  ThemeMode,
} from "../types";
import type { ReplayGainMode } from "../audio/replayGain";

const MAX_SEARCH_HISTORY = 10;
const MAX_RECENT_SEARCH_RESULTS = 20;

/**
 * Best-effort "Browser on OS" label for the device-name default (Q19): the
 * server attributes plays to a per-device name, and this is what a device
 * calls itself until the user picks something else.
 */
function defaultDeviceName(): string {
  const ua = navigator.userAgent;
  const os = /Windows/.test(ua)
    ? "Windows"
    : /Android/.test(ua)
      ? "Android"
      : /iPhone|iPad|iPod/.test(ua)
        ? "iOS"
        : /Macintosh|Mac OS X/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : "Unknown OS";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\//.test(ua)
      ? "Opera"
      : /Chrome\//.test(ua)
        ? "Chrome"
        : /Firefox\//.test(ua)
          ? "Firefox"
          : /Safari\//i.test(ua)
            ? "Safari"
            : "Browser";
  return `${browser} on ${os}`;
}

/**
 * Drops keystroke-fragment artifacts recorded by an older build that added
 * every debounced fragment to history: any entry that is a proper prefix of a
 * longer entry (typing "metallica" used to record "m", "me", "met", …).
 */
function sanitizeSearchHistory(history: string[]): string[] {
  return history.filter((h) => !history.some((other) => other.length > h.length && other.startsWith(h)));
}

/** Mirrors the Android app's AppPreferences. */
export const useSettingsStore = defineStore("settings", () => {
  const theme = ref<ThemeMode>(loadPref("muorg-web-theme", "dark"));
  const trueBlack = ref(loadPref("muorg-web-true-black", false));
  const continuousPlayback = ref(loadPref("muorg-web-continuous", true));
  const sortMode = ref<SortMode>(loadPref("muorg-web-sort", "album"));
  const sortAscending = ref(loadPref("muorg-web-sort-asc", true));
  /**
   * The playlist detail screen's order-by field (mirrors the Android app's
   * `playlistSort` preference). The direction is the app-wide `sortAscending`
   * above, shared with the library and the settings screen.
   */
  const playlistSortMode = ref<PlaylistSortMode>(loadPref("muorg-web-playlist-sort", "order"));
  const albumViewStyle = ref<AlbumViewStyle>(loadPref("muorg-web-album-view", "grid"));
  /** Layout for the playlist/mix detail view — separate from the Library's
   *  albumViewStyle so a user can keep the library as an album grid while
   *  opening playlists as a track list. */
  const playlistViewStyle = ref<AlbumViewStyle>(loadPref("muorg-web-playlist-view", "tracks"));
  const miniPlayerTapOpensPlayer = ref(loadPref("muorg-web-miniplayer-tap", true));
  const volume = ref(loadPref("muorg-web-volume", 1));
  /** ReplayGain mode: off (ignore tags), track (each track's own gain — best
   *  for mixed playlists/shuffle), or album (preserve loudness differences
   *  within an album). Mirrors the desktop app's setting. */
  const replayGainMode = ref<ReplayGainMode>(loadPref("muorg-web-replaygain-mode", "off"));
  /** dB applied on top of the tag's gain, positive or negative. */
  const replayGainPreampDb = ref(loadPref("muorg-web-replaygain-preamp", 0));
  const replayGainPreventClipping = ref(loadPref("muorg-web-replaygain-prevent-clipping", true));
  const accent = ref<AccentColor>(loadPref("muorg-web-accent", "green"));
  const reduceMotion = ref(loadPref("muorg-web-reduce-motion", false));
  const openOnLastTab = ref(loadPref("muorg-web-open-last-tab", true));
  /** Offline mode (mirrors the Android app): the player makes no network
   *  calls and only plays tracks held in the offline cache. */
  const offlineMode = ref(loadPref("muorg-web-offline-mode", false));
  /** Per-device name recorded with plays on the server (Q19). Required: an
   *  emptied field falls back to the generated default. */
  const deviceName = ref(loadPref("muorg-web-device-name", "") || defaultDeviceName());
  /** Copy the original file before writing tags (server-side backup). Off by
   *  default (Q1); the first-write and bulk-edit warnings are the mitigation. */
  const backupBeforeWrite = ref(loadPref("muorg-web-backup-before-write", false));
  /** Which fields make a track count as "missing metadata" in Reports. */
  const missingMetadataFields = ref<MissingMetadataField[]>(
    loadPref<string[]>("muorg-web-missing-fields", [...DEFAULT_MISSING_METADATA_FIELDS]).filter(
      (f): f is MissingMetadataField => (MISSING_METADATA_FIELDS as readonly string[]).includes(f),
    ),
  );
  /** "Apply from path" templates, e.g. `<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>`. */
  const pathFormatTemplates = ref<string[]>(
    loadPref("muorg-web-path-formats", ["<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"]),
  );
  /** Music root used to write relative paths when exporting M3U playlists. */
  const musicRootFolder = ref(loadPref("muorg-web-music-root", ""));
  // Sanitize once and persist the cleaned list: an older build recorded every
  // debounced keystroke fragment, and without the write-back the junk would
  // linger in storage and re-filter on every load forever.
  const rawHistory = loadPref<string[]>("muorg-web-search-history", []);
  const searchHistory = ref<string[]>(sanitizeSearchHistory(rawHistory));
  if (searchHistory.value.length !== rawHistory.length) {
    savePref("muorg-web-search-history", searchHistory.value);
  }
  /** Track ids of the most recently selected search results, newest first —
   *  shown as track rows on the Search screen when the query is empty. */
  const recentSearchResultIds = ref<number[]>(loadPref<number[]>("muorg-web-recent-search-results", []));

  const systemPrefersDark = ref(
    typeof matchMedia === "function" && matchMedia("(prefers-color-scheme: dark)").matches,
  );
  if (typeof matchMedia === "function") {
    matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      systemPrefersDark.value = e.matches;
    });
  }

  const resolvedTheme = computed<"dark" | "light">(() =>
    theme.value === "system" ? (systemPrefersDark.value ? "dark" : "light") : theme.value,
  );

  // Drives the CSS variable set in style.css.
  watchEffect(() => {
    document.documentElement.dataset.theme =
      resolvedTheme.value === "dark" && trueBlack.value ? "black" : resolvedTheme.value;
  });

  // Accent palettes are keyed on data-accent in style.css; green is the default
  // (the values in the theme blocks), so no attribute means green. "dynamic"
  // is resolved by the player store from the current album art, so this effect
  // deliberately leaves the attribute alone for it.
  watchEffect(() => {
    const el = document.documentElement;
    if (accent.value === "green" || accent.value === "dynamic") delete el.dataset.accent;
    else el.dataset.accent = accent.value;
  });

  // Mirrors the OS prefers-reduced-motion block: zero the travel, keep the
  // state change. Keyed on data-reduce-motion in style.css.
  watchEffect(() => {
    const el = document.documentElement;
    if (reduceMotion.value) el.dataset.reduceMotion = "true";
    else delete el.dataset.reduceMotion;
  });

  function setTheme(t: ThemeMode): void {
    theme.value = t;
    savePref("muorg-web-theme", t);
  }

  function setTrueBlack(v: boolean): void {
    trueBlack.value = v;
    savePref("muorg-web-true-black", v);
  }

  function setContinuousPlayback(v: boolean): void {
    continuousPlayback.value = v;
    savePref("muorg-web-continuous", v);
  }

  function setSortMode(m: SortMode): void {
    sortMode.value = m;
    savePref("muorg-web-sort", m);
  }

  function setSortAscending(v: boolean): void {
    sortAscending.value = v;
    savePref("muorg-web-sort-asc", v);
  }

  function setPlaylistSortMode(m: PlaylistSortMode): void {
    playlistSortMode.value = m;
    savePref("muorg-web-playlist-sort", m);
  }

  function setAlbumViewStyle(s: AlbumViewStyle): void {
    albumViewStyle.value = s;
    savePref("muorg-web-album-view", s);
  }

  /** grid → list → tracks → grid, matching the Android toolbar toggle. */
  function cycleAlbumViewStyle(): void {
    const next: AlbumViewStyle =
      albumViewStyle.value === "grid" ? "list" : albumViewStyle.value === "list" ? "tracks" : "grid";
    setAlbumViewStyle(next);
  }

  function setPlaylistViewStyle(s: AlbumViewStyle): void {
    playlistViewStyle.value = s;
    savePref("muorg-web-playlist-view", s);
  }

  /** grid → list → tracks → grid, same cycle as the library but persisted
   *  under its own pref so the two views can differ. */
  function cyclePlaylistViewStyle(): void {
    const next: AlbumViewStyle =
      playlistViewStyle.value === "grid" ? "list" : playlistViewStyle.value === "list" ? "tracks" : "grid";
    setPlaylistViewStyle(next);
  }

  function setMiniPlayerTapOpensPlayer(v: boolean): void {
    miniPlayerTapOpensPlayer.value = v;
    savePref("muorg-web-miniplayer-tap", v);
  }

  function setVolume(v: number): void {
    const clamped = Math.min(1, Math.max(0, v));
    volume.value = clamped;
    savePref("muorg-web-volume", clamped);
  }

  function setReplayGainMode(m: ReplayGainMode): void {
    replayGainMode.value = m;
    savePref("muorg-web-replaygain-mode", m);
  }

  /** Clamped to a sane ±24 dB range — the UI slider stays within that too. */
  function setReplayGainPreampDb(db: number): void {
    const clamped = Math.min(24, Math.max(-24, Number.isFinite(db) ? db : 0));
    replayGainPreampDb.value = clamped;
    savePref("muorg-web-replaygain-preamp", clamped);
  }

  function setReplayGainPreventClipping(v: boolean): void {
    replayGainPreventClipping.value = v;
    savePref("muorg-web-replaygain-prevent-clipping", v);
  }

  function setAccent(a: AccentColor): void {
    accent.value = a;
    savePref("muorg-web-accent", a);
  }

  function setBackupBeforeWrite(v: boolean): void {
    backupBeforeWrite.value = v;
    savePref("muorg-web-backup-before-write", v);
  }

  function setMissingMetadataFields(fields: MissingMetadataField[]): void {
    missingMetadataFields.value = fields;
    savePref("muorg-web-missing-fields", fields);
  }

  function setPathFormatTemplates(templates: string[]): void {
    pathFormatTemplates.value = templates;
    savePref("muorg-web-path-formats", templates);
  }

  function setMusicRootFolder(v: string): void {
    musicRootFolder.value = v.trim();
    savePref("muorg-web-music-root", musicRootFolder.value);
  }

  function setReduceMotion(v: boolean): void {
    reduceMotion.value = v;
    savePref("muorg-web-reduce-motion", v);
  }

  function setOpenOnLastTab(v: boolean): void {
    openOnLastTab.value = v;
    savePref("muorg-web-open-last-tab", v);
  }

  function setOfflineMode(v: boolean): void {
    offlineMode.value = v;
    savePref("muorg-web-offline-mode", v);
  }

  function setDeviceName(v: string): void {
    deviceName.value = v.trim() || defaultDeviceName();
    savePref("muorg-web-device-name", deviceName.value);
  }

  function addSearch(q: string): void {
    const trimmed = q.trim();
    if (!trimmed) return;
    const next = [trimmed, ...searchHistory.value.filter((h) => h !== trimmed)].slice(
      0,
      MAX_SEARCH_HISTORY,
    );
    searchHistory.value = next;
    savePref("muorg-web-search-history", next);
  }

  function clearSearchHistory(): void {
    searchHistory.value = [];
    savePref("muorg-web-search-history", []);
  }

  /** Records a track picked from search results (dedupes, caps the list). */
  function addSearchResult(trackId: number): void {
    // Playing a track must not reorder the list: existing entries keep their
    // position, new ones append at the end (oldest drops off past the cap).
    if (recentSearchResultIds.value.includes(trackId)) return;
    const next = [...recentSearchResultIds.value, trackId].slice(-MAX_RECENT_SEARCH_RESULTS);
    recentSearchResultIds.value = next;
    savePref("muorg-web-recent-search-results", next);
  }

  function clearSearchResults(): void {
    recentSearchResultIds.value = [];
    savePref("muorg-web-recent-search-results", []);
  }

  return {
    theme,
    trueBlack,
    continuousPlayback,
    sortMode,
    sortAscending,
    playlistSortMode,
    backupBeforeWrite,
    missingMetadataFields,
    pathFormatTemplates,
    musicRootFolder,
    setBackupBeforeWrite,
    setMissingMetadataFields,
    setPathFormatTemplates,
    setMusicRootFolder,
    albumViewStyle,
    playlistViewStyle,
    miniPlayerTapOpensPlayer,
    volume,
    replayGainMode,
    replayGainPreampDb,
    replayGainPreventClipping,
    accent,
    reduceMotion,
    openOnLastTab,
    offlineMode,
    deviceName,
    searchHistory,
    recentSearchResultIds,
    resolvedTheme,
    setTheme,
    setTrueBlack,
    setContinuousPlayback,
    setSortMode,
    setSortAscending,
    setPlaylistSortMode,
    setAlbumViewStyle,
    cycleAlbumViewStyle,
    setPlaylistViewStyle,
    cyclePlaylistViewStyle,
    setMiniPlayerTapOpensPlayer,
    setVolume,
    setReplayGainMode,
    setReplayGainPreampDb,
    setReplayGainPreventClipping,
    setAccent,
    setReduceMotion,
    setOpenOnLastTab,
    setOfflineMode,
    setDeviceName,
    addSearch,
    clearSearchHistory,
    addSearchResult,
    clearSearchResults,
  };
});
