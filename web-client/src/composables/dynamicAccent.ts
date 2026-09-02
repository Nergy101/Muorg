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