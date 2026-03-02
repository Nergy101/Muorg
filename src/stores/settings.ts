import { defineStore } from "pinia";

export type ThemeId = "dark" | "light" | "doom" | "auto";

export type DefaultGroupBy = "none" | "artist" | "album";

const THEME_STORAGE_KEY = "muorg-theme";
const DEFAULT_GROUP_BY_KEY = "muorg-default-group-by";
const DEFAULT_GROUPS_EXPANDED_KEY = "muorg-default-groups-expanded";

function loadTheme(): ThemeId {
  if (typeof window === "undefined") return "auto";
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "doom" || stored === "dark" || stored === "auto") return stored;
  return "auto";
}

/** Resolve theme to the actual value applied to the document (dark/light/doom). */
function getEffectiveTheme(theme: ThemeId): "dark" | "light" | "doom" {
  if (theme !== "auto") return theme;
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function loadDefaultGroupBy(): DefaultGroupBy {
  if (typeof window === "undefined") return "album";
  const stored = window.localStorage.getItem(DEFAULT_GROUP_BY_KEY);
  if (stored === "none" || stored === "artist" || stored === "album") return stored;
  return "album";
}

function loadDefaultGroupsExpanded(): boolean {
  if (typeof window === "undefined") return false;
  const stored = window.localStorage.getItem(DEFAULT_GROUPS_EXPANDED_KEY);
  return stored === "true";
}

function persistTheme(theme: ThemeId) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(THEME_STORAGE_KEY, theme);
}

function persistDefaultGroupBy(value: DefaultGroupBy) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(DEFAULT_GROUP_BY_KEY, value);
}

function persistDefaultGroupsExpanded(value: boolean) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(DEFAULT_GROUPS_EXPANDED_KEY, String(value));
}

function applyThemeToDocument(theme: ThemeId) {
  if (typeof document === "undefined") return;
  document.documentElement.dataset.theme = getEffectiveTheme(theme);
}

export const useSettingsStore = defineStore("settings", {
  state: () => ({
    theme: loadTheme() as ThemeId,
    defaultGroupBy: loadDefaultGroupBy() as DefaultGroupBy,
    defaultGroupsExpanded: loadDefaultGroupsExpanded(),
  }),
  actions: {
    setTheme(theme: ThemeId) {
      this.theme = theme;
      persistTheme(theme);
      applyThemeToDocument(theme);
    },
    initTheme() {
      applyThemeToDocument(this.theme);
      if (typeof window !== "undefined") {
        const mq = window.matchMedia("(prefers-color-scheme: dark)");
        mq.addEventListener("change", () => {
          if (this.theme === "auto") applyThemeToDocument("auto");
        });
      }
    },
    setDefaultGroupBy(value: DefaultGroupBy) {
      this.defaultGroupBy = value;
      persistDefaultGroupBy(value);
    },
    setDefaultGroupsExpanded(value: boolean) {
      this.defaultGroupsExpanded = value;
      persistDefaultGroupsExpanded(value);
    },
  },
});
