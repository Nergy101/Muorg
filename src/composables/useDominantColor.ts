import { ref, watch } from "vue";
import type { Ref } from "vue";

const SAMPLE_SIZE = 32;
/** Multiply RGB by this so the glow isn't pure white on bright art (0–1). Slightly higher = more saturated/vibrant. */
const DARKEN = 0.88;
/** Edge colors: keep more saturated so orange/blue etc. stay punchy in blobs. */
const EDGE_DARKEN = 0.95;

const FALLBACK_RGB = "28,25,23";

/** Primary green RGB – used when album color is bland. */
export const PRIMARY_RGB = "91,124,50";

/**
 * Returns true if the color is close to white, gray, or brown – bland colors that don't work well
 * for glow blobs. In those cases, hide blobs and use the primary color for controls.
 */
export function isColorBland(rgb: string): boolean {
  const parts = rgb.split(",").map(Number);
  if (parts.length !== 3) return true;
  const [r, g, b] = parts;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const luminance = (r * 0.299 + g * 0.587 + b * 0.114) / 255;
  const saturation = max === 0 ? 0 : (max - min) / max;

  // White: very bright
  if (luminance > 0.72) return true;
  // Gray: low saturation, not near black
  if (saturation < 0.15 && max > 50) return true;
  // Brown: reddish (R ≥ G ≥ B), desaturated – tan, beige, brown, sepia
  if (r >= g && g >= b && r > b && saturation < 0.65) return true;
  return false;
}

/**
 * Estimate a dominant color from an image URL (e.g. album art data URL).
 * Returns RGB as "r,g,b" so you can build gradients in tints of that color (no black).
 */
export function useDominantColor(imageUrl: Ref<string | null>) {
  const glowRgb = ref<string>(FALLBACK_RGB);

  watch(
    imageUrl,
    (url) => {
      if (!url || !url.startsWith("data:")) {
        glowRgb.value = FALLBACK_RGB;
        return;
      }
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        try {
          const canvas = document.createElement("canvas");
          const size = Math.min(SAMPLE_SIZE, img.width, img.height);
          canvas.width = size;
          canvas.height = size;
          const ctx = canvas.getContext("2d");
          if (!ctx) return;
          ctx.drawImage(img, 0, 0, size, size);
          const data = ctx.getImageData(0, 0, size, size).data;
          const margin = Math.floor(size * 0.25);
          let r = 0, g = 0, b = 0, n = 0;
          for (let y = margin; y < size - margin; y++) {
            for (let x = margin; x < size - margin; x++) {
              const i = (y * size + x) * 4;
              r += data[i];
              g += data[i + 1];
              b += data[i + 2];
              n++;
            }
          }
          if (n === 0) return;
          r = Math.round((r / n) * DARKEN);
          g = Math.round((g / n) * DARKEN);
          b = Math.round((b / n) * DARKEN);
          glowRgb.value = `${r},${g},${b}`;
        } catch {
          glowRgb.value = FALLBACK_RGB;
        }
      };
      img.onerror = () => {
        glowRgb.value = FALLBACK_RGB;
      };
      img.src = url;
    },
    { immediate: true },
  );

  return glowRgb;
}

/** Array of 4–6 vibrant RGB strings sampled from the album cover edge. */
export type EdgeColors = string[];

/** Edge colors grouped by side for opposing-color detection. */
export interface EdgeColorsBySide {
  left: string[];
  right: string[];
  top: string[];
  bottom: string[];
}

/** Saturation (0–1): how vibrant a color is. Higher = more "hard" / saturated. */
function colorSaturation(r: number, g: number, b: number): number {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  return max === 0 ? 0 : (max - min) / max;
}

/** Hue in degrees 0–360 from RGB. */
function rgbToHue(r: number, g: number, b: number): number {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  if (max === min) return 0;
  const d = max - min;
  let h = 0;
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
  else if (max === g) h = ((b - r) / d + 2) / 6;
  else h = ((r - g) / d + 4) / 6;
  return h * 360;
}

/** Get the most saturated color from a side's samples, or null if none vibrant. */
function dominantColorForSide(side: string[], minSat: number): string | null {
  let best: { rgb: string; sat: number } | null = null;
  for (const rgb of side) {
    const [r, g, b] = rgb.split(",").map(Number);
    if (!Number.isFinite(r + g + b)) continue;
    const sat = colorSaturation(r, g, b);
    if (sat >= minSat && (!best || sat > best.sat)) best = { rgb, sat };
  }
  return best?.rgb ?? null;
}

/** True when left vs right or top vs bottom have opposing vibrant colors (split design). */
export function hasOpposingEdgeColors(bySide: EdgeColorsBySide | null): boolean {
  if (!bySide) return false;
  const minSat = 0.2;
  const minHueDiff = 50;

  const left = dominantColorForSide(bySide.left, minSat);
  const right = dominantColorForSide(bySide.right, minSat);
  const top = dominantColorForSide(bySide.top, minSat);
  const bottom = dominantColorForSide(bySide.bottom, minSat);

  const hueDiff = (a: string, b: string): number => {
    const [ar, ag, ab] = a.split(",").map(Number);
    const [br, bg, bb] = b.split(",").map(Number);
    let d = Math.abs(rgbToHue(ar, ag, ab) - rgbToHue(br, bg, bb));
    return d > 180 ? 360 - d : d;
  };

  if (left && right && hueDiff(left, right) >= minHueDiff) return true;
  if (top && bottom && hueDiff(top, bottom) >= minHueDiff) return true;
  return false;
}

/** Sample a small region (2x2) around a point a few pixels inside the image. */
function sampleRegion(
  data: Uint8ClampedArray,
  size: number,
  cx: number,
  cy: number,
): string {
  const half = 1;
  let r = 0, g = 0, b = 0, n = 0;
  for (let dy = -half; dy <= half; dy++) {
    for (let dx = -half; dx <= half; dx++) {
      const x = Math.round(cx) + dx;
      const y = Math.round(cy) + dy;
      if (x >= 0 && x < size && y >= 0 && y < size) {
        const idx = (y * size + x) * 4;
        r += data[idx];
        g += data[idx + 1];
        b += data[idx + 2];
        n++;
      }
    }
  }
  if (n === 0) return "0,0,0";
  r = Math.round((r / n) * EDGE_DARKEN);
  g = Math.round((g / n) * EDGE_DARKEN);
  b = Math.round((b / n) * EDGE_DARKEN);
  return `${r},${g},${b}`;
}

export interface EdgeColorsResult {
  colors: EdgeColors;
  bySide: EdgeColorsBySide;
}

/**
 * Sample colors around the perimeter of the album cover, a few pixels inside the edge
 * (not the literal edge pixels). Returns flat top 4–6 vibrant colors + by-side groups
 * for opposing-color detection (left/right, top/bottom).
 */
export function useEdgeColors(imageUrl: Ref<string | null>) {
  const edgeColors = ref<EdgeColorsResult | null>(null);

  watch(
    imageUrl,
    (url) => {
      if (!url || !url.startsWith("data:")) {
        edgeColors.value = null;
        return;
      }
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        try {
          const canvas = document.createElement("canvas");
          const size = Math.min(SAMPLE_SIZE, img.width, img.height);
          canvas.width = size;
          canvas.height = size;
          const ctx = canvas.getContext("2d");
          if (!ctx) return;
          ctx.drawImage(img, 0, 0, size, size);
          const data = ctx.getImageData(0, 0, size, size).data;

          const inset = Math.max(2, Math.floor(size * 0.06));
          const numPerSide = 9;
          const bySide: EdgeColorsBySide = {
            left: [],
            right: [],
            top: [],
            bottom: [],
          };

          for (let i = 0; i < numPerSide; i++) {
            const t = (i + 1) / (numPerSide + 1);
            bySide.left.push(sampleRegion(data, size, inset, inset + t * (size - 2 * inset)));
            bySide.right.push(sampleRegion(data, size, size - 1 - inset, inset + t * (size - 2 * inset)));
            bySide.top.push(sampleRegion(data, size, inset + t * (size - 2 * inset), inset));
            bySide.bottom.push(sampleRegion(data, size, inset + t * (size - 2 * inset), size - 1 - inset));
          }

          const allSamples: { rgb: string; sat: number }[] = [];
          for (const side of Object.values(bySide)) {
            for (const rgb of side) {
              const [r, g, b] = rgb.split(",").map(Number);
              allSamples.push({ rgb, sat: colorSaturation(r, g, b) });
            }
          }

          allSamples.sort((a, b) => b.sat - a.sat);
          const minSat = 0.08;
          const vibrant = allSamples.filter((s) => s.sat >= minSat);
          const top = vibrant.slice(0, 6);
          const count = Math.max(4, Math.min(6, top.length));

          let flatColors: EdgeColors;
          if (count === 0) {
            const n = allSamples.length;
            if (n === 0) {
              flatColors = [];
            } else {
              let r = 0, g = 0, b = 0;
              for (const s of allSamples) {
                const [sr, sg, sb] = s.rgb.split(",").map(Number);
                r += sr;
                g += sg;
                b += sb;
              }
              flatColors = [`${Math.round(r / n)},${Math.round(g / n)},${Math.round(b / n)}`];
            }
          } else {
            flatColors = top.slice(0, count).map((s) => s.rgb);
          }

          edgeColors.value = { colors: flatColors, bySide };
        } catch {
          edgeColors.value = null;
        }
      };
      img.onerror = () => {
        edgeColors.value = null;
      };
      img.src = url;
    },
    { immediate: true },
  );

  return edgeColors;
}

/** Simple string hash for seeding. */
function hashString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (h << 5) - h + s.charCodeAt(i);
    h |= 0;
  }
  return h >>> 0;
}

/** Mulberry32 PRNG – returns 0–1, deterministic for a given seed. */
function mulberry32(seed: number): () => number {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const NUM_BLOBS = 11;
const FALLBACK_RGB_Gradient = "28,25,23";

export interface GlowBlob {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  opacity: number;
  rgb: string;
}

/**
 * Simple glow for uniform colors (e.g. all-white cover): few soft blobs, centered.
 * Higher opacity so white/light colors are visible on dark background.
 */
export function getSimpleGlowBlobs(rgb: string, seedString: string): GlowBlob[] {
  const seed = hashString(seedString || "simple");
  const rnd = mulberry32(seed);
  const color = rgb || FALLBACK_RGB_Gradient;
  return [
    { cx: 0.5, cy: 0.5, rx: 0.9, ry: 0.85, opacity: 0.35 + rnd() * 0.2, rgb: color },
    { cx: 0.48 + rnd() * 0.08, cy: 0.5, rx: 0.6, ry: 0.55, opacity: 0.25 + rnd() * 0.15, rgb: color },
    { cx: 0.5, cy: 0.48 + rnd() * 0.08, rx: 0.55, ry: 0.6, opacity: 0.2 + rnd() * 0.15, rgb: color },
  ];
}

/**
 * Build blob data for procedural glow. Each blob has position (cx, cy), size (rx, ry), opacity, and rgb.
 * Mix of subdued and vibrant blobs for spectacle. Used for morphing when same album.
 */
export function getGlowBlobs(glowRgb: string, seedString: string): GlowBlob[] {
  const rgb = glowRgb || FALLBACK_RGB_Gradient;
  const seed = hashString(seedString || "default");
  const rnd = mulberry32(seed);
  const blobs: GlowBlob[] = [];

  for (let i = 0; i < NUM_BLOBS; i++) {
    // ~40% subdued (soft ambient), ~60% vibrant (spectacle)
    const isSubdued = rnd() < 0.4;
    const opacity = isSubdued
      ? 0.06 + rnd() * 0.12
      : 0.22 + rnd() * 0.42;
    blobs.push({
      cx: 0.05 + rnd() * 0.9,
      cy: 0.05 + rnd() * 0.9,
      rx: 0.4 + rnd() * 0.5,
      ry: 0.35 + rnd() * 0.5,
      opacity,
      rgb,
    });
  }
  return blobs;
}

export type DemoGlowShape =
  | "ellipse"
  | "square"
  | "triangle"
  | "rounded-triangle"
  | "diamond"
  | "wave"
  | "blob";

export interface DemoGlowBlob extends GlowBlob {
  shape: DemoGlowShape;
  /** Optional shade variation (0–1). Slightly darken/lighten the rgb for variety. */
  shade?: number;
}

/**
 * Demo blobs with varied shapes: ellipses, squares, triangles, waves, etc. Shadow/background look.
 */
export function getGlowBlobsForDemo(glowRgb: string, _seedString: string): DemoGlowBlob[] {
  const rgb = glowRgb || FALLBACK_RGB_Gradient;

  return [
    { cx: 0.5, cy: 0.5, rx: 0.85, ry: 0.8, opacity: 0.38, rgb, shape: "ellipse" },
    { cx: 0.48, cy: 0.52, rx: 0.5, ry: 0.5, opacity: 0.2, rgb, shape: "blob", shade: 1.05 },
    { cx: 0.52, cy: 0.48, rx: 0.45, ry: 0.45, opacity: 0.15, rgb, shape: "ellipse", shade: 0.95 },
    { cx: 0.5, cy: 0.5, rx: 0.35, ry: 0.4, opacity: 0.2, rgb, shape: "wave", shade: 1.1 },
    { cx: 0.25, cy: 0.35, rx: 0.7, ry: 0.65, opacity: 0.22, rgb, shape: "blob", shade: 0.9 },
    { cx: 0.75, cy: 0.4, rx: 0.65, ry: 0.7, opacity: 0.28, rgb, shape: "square", shade: 1.1 },
    { cx: 0.4, cy: 0.7, rx: 0.6, ry: 0.55, opacity: 0.18, rgb, shape: "triangle" },
    { cx: 0.65, cy: 0.65, rx: 0.55, ry: 0.6, opacity: 0.25, rgb, shape: "rounded-triangle", shade: 0.85 },
    { cx: 0.2, cy: 0.6, rx: 0.5, ry: 0.5, opacity: 0.2, rgb, shape: "diamond" },
    { cx: 0.8, cy: 0.25, rx: 0.55, ry: 0.5, opacity: 0.22, rgb, shape: "wave", shade: 1.15 },
    { cx: 0.35, cy: 0.2, rx: 0.45, ry: 0.45, opacity: 0.15, rgb, shape: "ellipse", shade: 0.8 },
    { cx: 0.7, cy: 0.75, rx: 0.5, ry: 0.55, opacity: 0.2, rgb, shape: "square", shade: 0.9 },
    { cx: 0.82, cy: 0.78, rx: 0.6, ry: 0.55, opacity: 0.28, rgb, shape: "blob", shade: 1.05 },
    { cx: 0.9, cy: 0.65, rx: 0.5, ry: 0.5, opacity: 0.22, rgb, shape: "triangle", shade: 0.95 },
    { cx: 0.72, cy: 0.88, rx: 0.45, ry: 0.4, opacity: 0.2, rgb, shape: "diamond", shade: 0.88 },
    { cx: 0.88, cy: 0.5, rx: 0.5, ry: 0.55, opacity: 0.18, rgb, shape: "wave" },
    { cx: 0.6, cy: 0.85, rx: 0.5, ry: 0.45, opacity: 0.2, rgb, shape: "rounded-triangle", shade: 1.1 },
    { cx: 0.15, cy: 0.45, rx: 0.4, ry: 0.5, opacity: 0.16, rgb, shape: "ellipse", shade: 0.82 },
    { cx: 0.5, cy: 0.2, rx: 0.55, ry: 0.4, opacity: 0.2, rgb, shape: "wave", shade: 0.9 },
  ];
}

/**
 * Build a procedural glow background: dark base + several gradient blobs
 * at positions/sizes derived from the seed (e.g. track path). Different seed => different layout.
 * More blobs at random deterministic positions fill the screen with color.
 */
export function buildProceduralGlow(glowRgb: string, seedString: string): string {
  const blobs = getGlowBlobs(glowRgb, seedString);
  const layers = blobs.map(
    (b) =>
      `radial-gradient(ellipse ${b.rx * 100}% ${b.ry * 100}% at ${b.cx * 100}% ${b.cy * 100}%, rgba(${b.rgb},${b.opacity.toFixed(2)}) 0%, transparent 70%)`,
  );
  return `${layers.join(", ")}, linear-gradient(black, black)`;
}
