import type { AccentColor } from "../types";

/**
 * Dynamic accent: picks the closest of the predefined accent palettes based on
 * the "main color" of the currently playing album art. The main color is the
 * saturation-weighted average the app already computes for the player's glow
 * (`useDominantColor`), so both share one notion of the artwork's dominant
 * hue instead of two competing ones.
 */

/** Swatch colors mirror the accent palettes in style.css (sRGB 0–255). */
export const ACCENT_SWATCHES: Record<Exclude<AccentColor, "dynamic">, [number, number, number]> = {
  green: [91, 124, 50],
  blue: [74, 127, 193],
  purple: [125, 99, 184],
  orange: [185, 122, 42],
  red: [184, 74, 74],
  teal: [61, 143, 133],
};

const ACCENT_ORDER = Object.keys(ACCENT_SWATCHES) as (keyof typeof ACCENT_SWATCHES)[];

export function parseRgb(rgb: string): [number, number, number] | null {
  const parts = rgb.split(",").map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) return null;
  return [parts[0], parts[1], parts[2]];
}

/** Squared Euclidean distance in RGB — cheap and good enough for swatch picking. */
function dist2(a: [number, number, number], b: [number, number, number]): number {
  const dr = a[0] - b[0];
  const dg = a[1] - b[1];
  const db = a[2] - b[2];
  return dr * dr + dg * dg + db * db;
}

function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h: number;
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
  else if (max === g) h = ((b - r) / d + 2) / 6;
  else h = ((r - g) / d + 4) / 6;
  return [h, s, l];
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
  return [
    Math.round(hue(h + 1 / 3) * 255),
    Math.round(hue(h) * 255),
    Math.round(hue(h - 1 / 3) * 255),
  ];
}

/**
 * Turn the album-art main color into an accent usable for UI controls.
 * The glow average is often too dark/desaturated to read against the dark
 * player background, so the hue is kept but lightness is lifted into a band
 * and saturation floored — close enough to the art to feel "from the cover",
 * distinct enough to stay well visible. Returns null when the color is too
 * gray to tint from (caller falls back to the theme default).
 */
export function visibleAccentFromRgb(rgb: string): string | null {
  const parsed = parseRgb(rgb);
  if (!parsed) return null;
  const [h, s, l] = rgbToHsl(parsed[0], parsed[1], parsed[2]);
  if (s < 0.12) return null;
  const out = hslToRgb(h, Math.max(s, 0.45), Math.min(0.72, Math.max(0.55, l < 0.55 ? l + 0.25 : l)));
  return `rgb(${out[0]},${out[1]},${out[2]})`;
}

/**
 * Closest predefined accent to an "r,g,b" string. Falls back to green (the
 * app default) for unparseable input, which matches useDominantColor's
 * fallback behavior.
 */
export function closestAccent(rgb: string | null | undefined): AccentColor {
  const parsed = parseRgb(rgb ?? "");
  if (!parsed) return "green";
  let best: AccentColor = "green";
  let bestD = Infinity;
  for (const name of ACCENT_ORDER) {
    const d = dist2(parsed, ACCENT_SWATCHES[name]);
    if (d < bestD) {
      bestD = d;
      best = name;
    }
  }
  return best;
}