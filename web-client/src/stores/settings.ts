import { defineStore } from "pinia";
import { computed, ref, watchEffect } from "vue";
import { loadPref, savePref } from "./prefs";
import type { AlbumViewStyle, SortMode, ThemeMode } from "../types";

const MAX_SEARCH_HISTORY = 10;

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
  const albumViewStyle = ref<AlbumViewStyle>(loadPref("muorg-web-album-view", "grid"));
  const miniPlayerTapOpensPlayer = ref(loadPref("muorg-web-miniplayer-tap", true));
  const volume = ref(loadPref("muorg-web-volume", 1));
  // Sanitize once and persist the cleaned list: an older build recorded every
  // debounced keystroke fragment, and without the write-back the junk would
  // linger in storage and re-filter on every load forever.
  const rawHistory = loadPref<string[]>("muorg-web-search-history", []);
  const searchHistory = ref<string[]>(sanitizeSearchHistory(rawHistory));
  if (searchHistory.value.length !== rawHistory.length) {
    savePref("muorg-web-search-history", searchHistory.value);
  }

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

  function setMiniPlayerTapOpensPlayer(v: boolean): void {
    miniPlayerTapOpensPlayer.value = v;
    savePref("muorg-web-miniplayer-tap", v);
  }

  function setVolume(v: number): void {
    const clamped = Math.min(1, Math.max(0, v));
    volume.value = clamped;
    savePref("muorg-web-volume", clamped);
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

  return {
    theme,
    trueBlack,
    continuousPlayback,
    sortMode,
    sortAscending,
    albumViewStyle,
    miniPlayerTapOpensPlayer,
    volume,
    searchHistory,
    resolvedTheme,
    setTheme,
    setTrueBlack,
    setContinuousPlayback,
    setSortMode,
    setSortAscending,
    setAlbumViewStyle,
    cycleAlbumViewStyle,
    setMiniPlayerTapOpensPlayer,
    setVolume,
    addSearch,
    clearSearchHistory,
  };
});
