import { computed, watch } from "vue";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore, type AccentColor } from "../stores/settings";
import { useDominantColor, useMostCommonColor } from "./useDominantColor";
import { currentView } from "../state/navigation";
import { albumIdentityKey } from "@shared/utils/albumIdentity";

/**
 * Dynamic accent (design handover §3/§5): when the accent setting is
 * "dynamic" (the default), the whole UI recolors to the accent of the
 * currently playing album — falling back to the opened album page, else the
 * theme default green. The frosted player bar additionally picks light or
 * dark text by computing WCAG contrast of the effective glass tint.
 */

const DEFAULT_ACCENT_HEX = "#5b7c32";

/** Fixed preset accent hexes (mirror the data-accent blocks in style.css). */
const PRESET_HEX: Record<Exclude<AccentColor, "dynamic">, string> = {
  green: "#5b7c32",
  blue: "#4a7fc1",
  purple: "#7d63b8",
  orange: "#b97a2a",
  red: "#b84a4a",
  teal: "#3d8f85",
};

function hexToRgb(hex: string): [number, number, number] | null {
  const m = hex.trim().replace(/^#/, "");
  if (!/^[0-9a-fA-F]{6}$/.test(m)) return null;
  return [parseInt(m.slice(0, 2), 16), parseInt(m.slice(2, 4), 16), parseInt(m.slice(4, 6), 16)];
}

function rgbToHex(r: number, g: number, b: number): string {
  const c = (v: number) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0");
  return `#${c(r)}${c(g)}${c(b)}`;
}

/** WCAG relative luminance of an sRGB channel triple. */
function relativeLuminance(r: number, g: number, b: number): number {
  const f = (c: number) => {
    const s = c / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

const BLACK: [number, number, number] = [0, 0, 0];

function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h: number;
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
  else if (max === g) h = ((b - r) / d + 2) / 6;
  else h = ((r - g) / d + 4) / 6;
  return [h * 360, s, l];
}

function hslToRgb(h: number, s: number, l: number): [number, number, number] {
  if (s === 0) {
    const v = Math.round(l * 255);
    return [v, v, v];
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  const hue = (t: number): number => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  return [Math.round(hue(h / 360 + 1 / 3) * 255), Math.round(hue(h / 360) * 255), Math.round(hue(h / 360 - 1 / 3) * 255)];
}

/**
 * Album accent hex for a cover rgb triplet ("r,g,b"), or null when the color
 * should fall back to the Muorg default green. Mirrors the web-client's
 * visibleAccentFromRgb (keep hue/sat, clamp lightness to [0.3, 0.78], reject
 * saturation < 0.12) plus the desktop's extra guards the user likes: brown,
 * near-white and near-black art also fall back to green.
 */
/** Accent hex from a cover rgb triplet; exported for the maximized player panel. */
export function accentHexFromRgb(rgb: string | null | undefined): string | null {
  if (!rgb) return null;
  const parts = rgb.split(",").map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) return null;
  // Un-darken the glow color (useDominantColor darkens by 0.88) so the accent
  // matches the artwork, like the web-client's rawMainColor.
  const [r, g, b] = parts.map((n) => Math.min(255, Math.round(n / 0.88)));
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const sat = max === 0 ? 0 : (max - min) / max;
  // Only truly gray art falls back to the default green (web-client rule);
  // brown/light/dark hues pass through like the web-client does.
  if (sat < 0.12) return null;
  const [h, s, l] = rgbToHsl(r, g, b);
  // Clamp lightness a bit higher than the web-client so dark art (e.g. Helix)
  // still yields a clearly visible accent instead of near-black.
  const [cr, cg, cb] = hslToRgb(h, s, Math.min(0.78, Math.max(0.4, l)));
  return rgbToHex(cr, cg, cb);
}

/**
 * Install the dynamic-accent effect. Call once from App.vue.
 *
 * - Watches the accent setting: fixed presets set `data-accent` on <html>;
 *   "dynamic" removes it and lets the watchers below drive the vars.
 * - Watches the playing track's album art (and the opened album page as
 *   fallback) and rewrites the accent CSS vars per theme.
 * - Computes the player-bar text palette (light/dark) from the effective
 *   glass tint and sets `data-player-text` on <body>.
 */
export function useDynamicAccent(): void {
  const settings = useSettingsStore();
  const store = useCatalogStore();

  const playingCoverUrl = computed(() => {
    const id = store.currentPlayingTrackId;
    if (id == null) return null;
    const track = store.tracks.find((t) => t.id === id);
    return track ? store.getCoverDataUrl(track.path) : null;
  });
  const glowRgb = useDominantColor(playingCoverUrl);
  // Mode (most-common) color first: it reflects the cover background, while the
  // saturation-weighted average gets pulled toward small saturated details
  // (e.g. red title text on Helix → brownish). Falls back to the average.
  const modeRgb = useMostCommonColor(playingCoverUrl);

  // Fallback: the currently opened album page's accent.
  const openAlbumCoverUrl = computed(() => {
    const v = currentView.value;
    if (v.name !== "album" || !v.key) return null;
    const track = store.tracks.find((t) => albumIdentityKey(t.album, t.album_artist) === v.key);
    return track ? store.getCoverDataUrl(track.path) : null;
  });
  const openAlbumRgb = useDominantColor(openAlbumCoverUrl);
  const openAlbumModeRgb = useMostCommonColor(openAlbumCoverUrl);

  const albumAccentHex = computed<string>(() => {
    return (
      accentHexFromRgb(modeRgb.value) ??
      accentHexFromRgb(glowRgb.value) ??
      accentHexFromRgb(openAlbumModeRgb.value) ??
      accentHexFromRgb(openAlbumRgb.value) ??
      DEFAULT_ACCENT_HEX
    );
  });

  const isLightTheme = () => document.documentElement.dataset.theme === "light";

  function applyAccent(accent: AccentColor, dynamicHex: string) {
    const root = document.documentElement;
    const body = document.body;
    if (!root || !body) return;

    const hex = accent === "dynamic" ? dynamicHex : PRESET_HEX[accent];

    if (accent !== "dynamic") {
      root.dataset.accent = accent;
      root.style.removeProperty("--primary");
      root.style.removeProperty("--primary-container");
      root.style.removeProperty("--on-primary-container");
      root.style.removeProperty("--secondary");
      root.style.removeProperty("--on-primary");
    } else {
      delete root.dataset.accent;
    }

    const B = hexToRgb(hex) ?? hexToRgb(DEFAULT_ACCENT_HEX)!;
    const light = isLightTheme();
    if (accent === "dynamic") {
      if (!light) {
        root.style.setProperty("--primary", `color-mix(in srgb, ${hex} 94%, white)`);
        root.style.setProperty("--primary-container", `color-mix(in srgb, ${hex} 70%, black)`);
        root.style.setProperty("--on-primary-container", `color-mix(in srgb, ${hex} 62%, white)`);
        root.style.setProperty("--secondary", `color-mix(in srgb, ${hex} 68%, white)`);
      } else {
        root.style.setProperty("--primary", `color-mix(in srgb, ${hex} 72%, black)`);
        root.style.setProperty("--primary-container", `color-mix(in srgb, ${hex} 25%, white)`);
        root.style.setProperty("--on-primary-container", `color-mix(in srgb, ${hex} 40%, black)`);
        root.style.setProperty("--secondary", `color-mix(in srgb, ${hex} 78%, black)`);
      }
    }

    // Luminance flip: a very light accent (e.g. The White Album) needs dark text on primary.
    const [r, g, b] = light ? [B[0] * 0.72, B[1] * 0.72, B[2] * 0.72] : B;
    const lum = relativeLuminance(r, g, b);
    if (lum > 0.45) root.style.setProperty("--on-primary", "#1c1917");
    else root.style.removeProperty("--on-primary");

    // Player-bar text palette: effective tint = accent at 78% over the theme background.
    const bg = hexToRgb(getComputedStyle(root).getPropertyValue("--background")) ?? BLACK;
    const tint: [number, number, number] = light
      ? [B[0] * 0.72 * 0.78 + bg[0] * 0.22, B[1] * 0.72 * 0.78 + bg[1] * 0.22, B[2] * 0.72 * 0.78 + bg[2] * 0.22]
      : [B[0] * 0.94 + 255 * 0.06, B[1] * 0.94 + 255 * 0.06, B[2] * 0.94 + 255 * 0.06].map((c, i) => c * 0.78 + bg[i] * 0.22) as [number, number, number];
    const tintLum = relativeLuminance(tint[0], tint[1], tint[2]);
    const contrastLight = (0.92 + 0.05) / (tintLum + 0.05);
    const contrastDark = (tintLum + 0.05) / (0.012 + 0.05);
    body.dataset.playerText = contrastLight >= contrastDark ? "light" : "dark";
  }

  watch(
    () => settings.accentColor,
    (accent) => applyAccent(accent, albumAccentHex.value),
    { immediate: true },
  );
  watch(albumAccentHex, (hex) => {
    if (settings.accentColor === "dynamic") applyAccent("dynamic", hex);
  });
  // Theme switches re-derive both the accent mixes and the player text palette.
  // (dataset mutations are not reactive, so watch the store + the OS media query.)
  watch(
    () => settings.theme,
    () => applyAccent(settings.accentColor, albumAccentHex.value),
  );
  if (typeof window !== "undefined") {
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    mq.addEventListener("change", () => {
      if (settings.theme === "auto") applyAccent(settings.accentColor, albumAccentHex.value);
    });
  }
}
