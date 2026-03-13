import { defineStore } from "pinia";
import { readTextFile, writeTextFile, BaseDirectory } from "@tauri-apps/plugin-fs";
import { parse as parseYaml, stringify as stringifyYaml } from "yaml";

export type ThemeId = "dark" | "light" | "doom" | "orkish" | "auto";

export type DefaultGroupBy = "none" | "artist" | "album";

export type TableDensity = "comfortable" | "compact";

export type BottomPanelId = "library" | "metadata" | "play";

export type MissingMetadataField =
  | "title"
  | "artist"
  | "album"
  | "album_artist"
  | "year"
  | "genre"
  | "track_number"
  | "disc_number";

const SETTINGS_FILENAME = "settings.yml";

/** Default example path for Smart Suggestions path-format preview (reset button). */
export const DEFAULT_PATH_FORMAT_EXAMPLE_PATH =
  "/library/music/Linkin Park/Meteora/04 - Faint.flac";

const ALLOWED_THEMES: ThemeId[] = ["dark", "light", "doom", "orkish", "auto"];
const ALLOWED_GROUP_BY: DefaultGroupBy[] = ["none", "artist", "album"];
const ALLOWED_DENSITY: TableDensity[] = ["comfortable", "compact"];
const ALLOWED_BOTTOM_PANELS: BottomPanelId[] = ["library", "metadata", "play"];
const ALLOWED_MISSING_FIELDS: MissingMetadataField[] = [
  "title",
  "artist",
  "album",
  "album_artist",
  "year",
  "genre",
  "track_number",
  "disc_number",
];

/** Resolve theme to the actual value applied to the document (dark/light/doom/orkish). */
function getEffectiveTheme(theme: ThemeId): "dark" | "light" | "doom" | "orkish" {
  if (theme !== "auto") return theme;
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyThemeToDocument(theme: ThemeId) {
  if (typeof document === "undefined") return;
  document.documentElement.dataset.theme = getEffectiveTheme(theme);
}

/** Weakly-typed record from settings file (unknown keys ignored). */
type SettingsRecord = Record<string, unknown>;

function coerceTheme(v: unknown): ThemeId {
  if (typeof v === "string" && ALLOWED_THEMES.includes(v as ThemeId)) return v as ThemeId;
  return "auto";
}
function coerceGroupBy(v: unknown): DefaultGroupBy {
  if (typeof v === "string" && ALLOWED_GROUP_BY.includes(v as DefaultGroupBy)) return v as DefaultGroupBy;
  return "album";
}
function coerceBool(v: unknown, def: boolean): boolean {
  if (v === true || v === false) return v;
  if (v === "true") return true;
  if (v === "false") return false;
  return def;
}
function coerceVolume(v: unknown): number {
  const n = typeof v === "number" ? v : Number(v);
  if (!Number.isFinite(n)) return 0.25;
  return Math.min(1, Math.max(0, n));
}
function coerceString(v: unknown, def: string): string {
  if (typeof v === "string") return v;
  return def;
}
function coerceDensity(v: unknown): TableDensity {
  if (typeof v === "string" && ALLOWED_DENSITY.includes(v as TableDensity)) return v as TableDensity;
  return "comfortable";
}
function coerceMissingFields(v: unknown): MissingMetadataField[] {
  if (!Array.isArray(v)) return ["title", "artist", "album"];
  return v.filter((x): x is MissingMetadataField =>
    typeof x === "string" && ALLOWED_MISSING_FIELDS.includes(x as MissingMetadataField)
  );
}
function coerceBottomPanel(v: unknown): BottomPanelId {
  if (typeof v === "string" && ALLOWED_BOTTOM_PANELS.includes(v as BottomPanelId)) {
    return v as BottomPanelId;
  }
  return "library";
}

const DEFAULT_TABLE_COL_WIDTHS: Record<string, number> = {
  albumArt: 40,
  title: 220,
  artist: 150,
  album: 150,
  year: 80,
  duration: 64,
  format: 64,
  path: 200,
};

function coerceTableColWidths(v: unknown): Record<string, number> {
  if (!v || typeof v !== "object") return { ...DEFAULT_TABLE_COL_WIDTHS };
  const out = { ...DEFAULT_TABLE_COL_WIDTHS };
  const obj = v as Record<string, unknown>;
  for (const key of Object.keys(DEFAULT_TABLE_COL_WIDTHS)) {
    const val = obj[key];
    const n = typeof val === "number" ? val : Number(val);
    if (Number.isFinite(n) && n >= 40 && n <= 800) out[key] = Math.round(n);
  }
  return out;
}

export const useSettingsStore = defineStore("settings", {
  state: () => ({
    theme: "auto" as ThemeId,
    defaultGroupBy: "album" as DefaultGroupBy,
    defaultGroupsExpanded: false,
    autoplayOnSelect: false,
    continuousPlayback: false,
    playbarShowAlbumInMarquee: false,
    playbarDisableMarquee: false,
    shuffle: false,
    navWrap: false,
    navFocusFollowsMouse: false,
    volume: 0.25,
    defaultBottomPanel: "library" as BottomPanelId,
    tableDensity: "comfortable" as TableDensity,
    tableColAlbumArt: true,
    tableColYear: true,
    tableColDuration: true,
    tableColFormat: true,
    tableColPath: true,
    /** Resizable column widths (px). Keys: albumArt, title, artist, album, year, duration, format, path. */
    tableColWidths: { ...DEFAULT_TABLE_COL_WIDTHS },
    missingMetadataFields: ["title", "artist", "album"] as MissingMetadataField[],
    groupHeaderAlbumArt: true,
    hideWikipediaCoverSearch: false,
    hideReportsSection: false,
    pathFormatTemplate: "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
    pathFormatExamplePath: DEFAULT_PATH_FORMAT_EXAMPLE_PATH,
    openSettingsAtTab: null as string | null,
  }),
  actions: {
    /** Load settings from AppConfig/settings.yml; unknown or invalid keys ignored. */
    async loadFromFile() {
      if (typeof window === "undefined") return;
      try {
        const raw = await readTextFile(SETTINGS_FILENAME, { baseDir: BaseDirectory.AppConfig });
        const data = parseYaml(raw) as SettingsRecord | null;
        if (!data || typeof data !== "object") return;
        if ("theme" in data) this.theme = coerceTheme(data.theme);
        if ("defaultGroupBy" in data) this.defaultGroupBy = coerceGroupBy(data.defaultGroupBy);
        if ("defaultGroupsExpanded" in data) this.defaultGroupsExpanded = coerceBool(data.defaultGroupsExpanded, false);
        if ("autoplayOnSelect" in data) this.autoplayOnSelect = coerceBool(data.autoplayOnSelect, false);
        if ("continuousPlayback" in data) this.continuousPlayback = coerceBool(data.continuousPlayback, false);
        if ("playbarShowAlbumInMarquee" in data) {
          this.playbarShowAlbumInMarquee = coerceBool(data.playbarShowAlbumInMarquee, false);
        }
        if ("playbarDisableMarquee" in data) {
          this.playbarDisableMarquee = coerceBool(data.playbarDisableMarquee, false);
        }
        if ("shuffle" in data) this.shuffle = coerceBool(data.shuffle, false);
        if ("navWrap" in data) this.navWrap = coerceBool(data.navWrap, false);
        if ("navFocusFollowsMouse" in data) this.navFocusFollowsMouse = coerceBool(data.navFocusFollowsMouse, false);
        if ("volume" in data) this.volume = coerceVolume(data.volume);
        if ("defaultBottomPanel" in data) this.defaultBottomPanel = coerceBottomPanel(data.defaultBottomPanel);
        if ("tableDensity" in data) this.tableDensity = coerceDensity(data.tableDensity);
        if ("tableColAlbumArt" in data) this.tableColAlbumArt = coerceBool(data.tableColAlbumArt, true);
        if ("tableColYear" in data) this.tableColYear = coerceBool(data.tableColYear, true);
        if ("tableColDuration" in data) this.tableColDuration = coerceBool(data.tableColDuration, true);
        if ("tableColFormat" in data) this.tableColFormat = coerceBool(data.tableColFormat, true);
        if ("tableColPath" in data) this.tableColPath = coerceBool(data.tableColPath, true);
        if ("tableColWidths" in data) this.tableColWidths = coerceTableColWidths(data.tableColWidths);
        if ("missingMetadataFields" in data) this.missingMetadataFields = coerceMissingFields(data.missingMetadataFields);
        if ("groupHeaderAlbumArt" in data) this.groupHeaderAlbumArt = coerceBool(data.groupHeaderAlbumArt, true);
        if ("hideWikipediaCoverSearch" in data) this.hideWikipediaCoverSearch = coerceBool(data.hideWikipediaCoverSearch, false);
        if ("hideReportsSection" in data) this.hideReportsSection = coerceBool(data.hideReportsSection, false);
        if ("pathFormatTemplate" in data) this.pathFormatTemplate = coerceString(data.pathFormatTemplate, this.pathFormatTemplate);
        if ("pathFormatExamplePath" in data) this.pathFormatExamplePath = coerceString(data.pathFormatExamplePath, DEFAULT_PATH_FORMAT_EXAMPLE_PATH);
      } catch {
        // file missing or invalid: keep defaults
      }
    },

    /** Persist whole settings to AppConfig/settings.yml (call after any change). */
    async saveToFile() {
      if (typeof window === "undefined") return;
      try {
        const data: SettingsRecord = {
          theme: this.theme,
          defaultGroupBy: this.defaultGroupBy,
          defaultGroupsExpanded: this.defaultGroupsExpanded,
          autoplayOnSelect: this.autoplayOnSelect,
          continuousPlayback: this.continuousPlayback,
          playbarShowAlbumInMarquee: this.playbarShowAlbumInMarquee,
          playbarDisableMarquee: this.playbarDisableMarquee,
          shuffle: this.shuffle,
          navWrap: this.navWrap,
          navFocusFollowsMouse: this.navFocusFollowsMouse,
          volume: this.volume,
          defaultBottomPanel: this.defaultBottomPanel,
          tableDensity: this.tableDensity,
          tableColAlbumArt: this.tableColAlbumArt,
          tableColYear: this.tableColYear,
          tableColDuration: this.tableColDuration,
          tableColFormat: this.tableColFormat,
          tableColPath: this.tableColPath,
          tableColWidths: this.tableColWidths,
          missingMetadataFields: this.missingMetadataFields,
          groupHeaderAlbumArt: this.groupHeaderAlbumArt,
          hideWikipediaCoverSearch: this.hideWikipediaCoverSearch,
          hideReportsSection: this.hideReportsSection,
          pathFormatTemplate: this.pathFormatTemplate,
          pathFormatExamplePath: this.pathFormatExamplePath,
        };
        const yaml = stringifyYaml(data, { lineWidth: 0 });
        await writeTextFile(SETTINGS_FILENAME, yaml, { baseDir: BaseDirectory.AppConfig });
      } catch {
        // e.g. permission or disk error
      }
    },

    setTheme(theme: ThemeId) {
      this.theme = theme;
      applyThemeToDocument(theme);
      this.saveToFile();
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
      this.saveToFile();
    },
    setDefaultGroupsExpanded(value: boolean) {
      this.defaultGroupsExpanded = value;
      this.saveToFile();
    },
    setAutoplayOnSelect(value: boolean) {
      this.autoplayOnSelect = value;
      this.saveToFile();
    },
    setContinuousPlayback(value: boolean) {
      this.continuousPlayback = value;
      this.saveToFile();
    },
    setPlaybarShowAlbumInMarquee(value: boolean) {
      this.playbarShowAlbumInMarquee = value;
      this.saveToFile();
    },
    setPlaybarDisableMarquee(value: boolean) {
      this.playbarDisableMarquee = value;
      this.saveToFile();
    },
    setShuffle(value: boolean) {
      this.shuffle = value;
      this.saveToFile();
    },
    setNavWrap(value: boolean) {
      this.navWrap = value;
      this.saveToFile();
    },
    setNavFocusFollowsMouse(value: boolean) {
      this.navFocusFollowsMouse = value;
      this.saveToFile();
    },
    setDefaultBottomPanel(value: BottomPanelId) {
      this.defaultBottomPanel = value;
      this.saveToFile();
    },
    setVolume(value: number) {
      const v = Math.min(1, Math.max(0, value));
      this.volume = v;
      this.saveToFile();
    },
    setTableDensity(value: TableDensity) {
      this.tableDensity = value;
      this.saveToFile();
    },
    setTableColAlbumArt(value: boolean) {
      this.tableColAlbumArt = value;
      this.saveToFile();
    },
    setTableColYear(value: boolean) {
      this.tableColYear = value;
      this.saveToFile();
    },
    setTableColDuration(value: boolean) {
      this.tableColDuration = value;
      this.saveToFile();
    },
    setTableColFormat(value: boolean) {
      this.tableColFormat = value;
      this.saveToFile();
    },
    setTableColPath(value: boolean) {
      this.tableColPath = value;
      this.saveToFile();
    },
    setTableColWidth(columnId: string, width: number, persist = true) {
      const clamped = Math.round(Math.min(800, Math.max(40, width)));
      if (Object.prototype.hasOwnProperty.call(this.tableColWidths, columnId)) {
        this.tableColWidths[columnId] = clamped;
        if (persist) this.saveToFile();
      }
    },
    setHideReportsSection(value: boolean) {
      this.hideReportsSection = value;
      this.saveToFile();
    },
    setMissingMetadataFields(fields: MissingMetadataField[]) {
      this.missingMetadataFields = fields;
      this.saveToFile();
    },
    setGroupHeaderAlbumArt(value: boolean) {
      this.groupHeaderAlbumArt = value;
      this.saveToFile();
    },
    setHideWikipediaCoverSearch(value: boolean) {
      this.hideWikipediaCoverSearch = value;
      this.saveToFile();
    },
    setPathFormatTemplate(value: string) {
      this.pathFormatTemplate = value;
      this.saveToFile();
    },
    setPathFormatExamplePath(value: string) {
      this.pathFormatExamplePath = value;
      this.saveToFile();
    },
    setOpenSettingsAtTab(tab: string | null) {
      this.openSettingsAtTab = tab;
    },
  },
});
