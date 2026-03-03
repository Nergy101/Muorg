import { defineStore } from "pinia";

export type ThemeId = "dark" | "light" | "doom" | "orkish" | "auto";

export type DefaultGroupBy = "none" | "artist" | "album";

export type TableDensity = "comfortable" | "compact";

export type MissingMetadataField =
  | "title"
  | "artist"
  | "album"
  | "album_artist"
  | "year"
  | "genre"
  | "track_number"
  | "disc_number";

const THEME_STORAGE_KEY = "muorg-theme";
const DEFAULT_GROUP_BY_KEY = "muorg-default-group-by";
const DEFAULT_GROUPS_EXPANDED_KEY = "muorg-default-groups-expanded";
const AUTOPLAY_ON_SELECT_KEY = "muorg-autoplay-on-select";
const NAV_WRAP_KEY = "muorg-nav-wrap";
const NAV_FOCUS_FOLLOWS_MOUSE_KEY = "muorg-nav-focus-follows-mouse";
const TABLE_DENSITY_KEY = "muorg-table-density";
const TABLE_COL_ALBUM_ART_KEY = "muorg-table-col-album-art";
const TABLE_COL_YEAR_KEY = "muorg-table-col-year";
const TABLE_COL_DURATION_KEY = "muorg-table-col-duration";
const TABLE_COL_FORMAT_KEY = "muorg-table-col-format";
const TABLE_COL_PATH_KEY = "muorg-table-col-path";
const MISSING_METADATA_FIELDS_KEY = "muorg-missing-metadata-fields";

function loadTheme(): ThemeId {
  if (typeof window === "undefined") return "auto";
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "doom" || stored === "orkish" || stored === "dark" || stored === "auto") return stored;
  return "auto";
}

/** Resolve theme to the actual value applied to the document (dark/light/doom/orkish). */
function getEffectiveTheme(theme: ThemeId): "dark" | "light" | "doom" | "orkish" {
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

function loadAutoplayOnSelect(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(AUTOPLAY_ON_SELECT_KEY) === "true";
}

function loadNavWrap(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(NAV_WRAP_KEY) === "true";
}

function loadNavFocusFollowsMouse(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(NAV_FOCUS_FOLLOWS_MOUSE_KEY) === "true";
}

function loadTableDensity(): TableDensity {
  if (typeof window === "undefined") return "comfortable";
  const stored = window.localStorage.getItem(TABLE_DENSITY_KEY);
  return stored === "compact" ? "compact" : "comfortable";
}

function loadBoolWithDefault(key: string, defaultValue: boolean): boolean {
  if (typeof window === "undefined") return defaultValue;
  const stored = window.localStorage.getItem(key);
  if (stored == null) return defaultValue;
  return stored === "true";
}

function loadMissingMetadataFields(): MissingMetadataField[] {
  if (typeof window === "undefined") return ["title", "artist", "album"];
  const stored = window.localStorage.getItem(MISSING_METADATA_FIELDS_KEY);
  if (!stored) return ["title", "artist", "album"];
  try {
    const parsed = JSON.parse(stored);
    if (!Array.isArray(parsed)) return ["title", "artist", "album"];
    const allowed: MissingMetadataField[] = [
      "title",
      "artist",
      "album",
      "album_artist",
      "year",
      "genre",
      "track_number",
      "disc_number",
    ];
    return parsed.filter((v: unknown): v is MissingMetadataField => allowed.includes(v as MissingMetadataField));
  } catch {
    return ["title", "artist", "album"];
  }
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

function persistAutoplayOnSelect(value: boolean) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(AUTOPLAY_ON_SELECT_KEY, String(value));
}

function persistNavWrap(value: boolean) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(NAV_WRAP_KEY, String(value));
}

function persistNavFocusFollowsMouse(value: boolean) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(NAV_FOCUS_FOLLOWS_MOUSE_KEY, String(value));
}

function persistTableDensity(value: TableDensity) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TABLE_DENSITY_KEY, value);
}

function persistBool(key: string, value: boolean) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(key, String(value));
}

function persistMissingMetadataFields(fields: MissingMetadataField[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(MISSING_METADATA_FIELDS_KEY, JSON.stringify(fields));
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
    autoplayOnSelect: loadAutoplayOnSelect(),
    navWrap: loadNavWrap(),
    navFocusFollowsMouse: loadNavFocusFollowsMouse(),
    tableDensity: loadTableDensity() as TableDensity,
    tableColAlbumArt: loadBoolWithDefault(TABLE_COL_ALBUM_ART_KEY, true),
    tableColYear: loadBoolWithDefault(TABLE_COL_YEAR_KEY, true),
    tableColDuration: loadBoolWithDefault(TABLE_COL_DURATION_KEY, true),
    tableColFormat: loadBoolWithDefault(TABLE_COL_FORMAT_KEY, true),
    tableColPath: loadBoolWithDefault(TABLE_COL_PATH_KEY, true),
    missingMetadataFields: loadMissingMetadataFields() as MissingMetadataField[],
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
    setAutoplayOnSelect(value: boolean) {
      this.autoplayOnSelect = value;
      persistAutoplayOnSelect(value);
    },
    setNavWrap(value: boolean) {
      this.navWrap = value;
      persistNavWrap(value);
    },
    setNavFocusFollowsMouse(value: boolean) {
      this.navFocusFollowsMouse = value;
      persistNavFocusFollowsMouse(value);
    },
    setTableDensity(value: TableDensity) {
      this.tableDensity = value;
      persistTableDensity(value);
    },
    setTableColAlbumArt(value: boolean) {
      this.tableColAlbumArt = value;
      persistBool(TABLE_COL_ALBUM_ART_KEY, value);
    },
    setTableColYear(value: boolean) {
      this.tableColYear = value;
      persistBool(TABLE_COL_YEAR_KEY, value);
    },
    setTableColDuration(value: boolean) {
      this.tableColDuration = value;
      persistBool(TABLE_COL_DURATION_KEY, value);
    },
    setTableColFormat(value: boolean) {
      this.tableColFormat = value;
      persistBool(TABLE_COL_FORMAT_KEY, value);
    },
    setTableColPath(value: boolean) {
      this.tableColPath = value;
      persistBool(TABLE_COL_PATH_KEY, value);
    },
    setMissingMetadataFields(fields: MissingMetadataField[]) {
      this.missingMetadataFields = fields;
      persistMissingMetadataFields(fields);
    },
  },
});
