import { ref, watch } from "vue";
import type { Ref } from "vue";

const SAMPLE_SIZE = 32;
/** Glow-only darkening; the dynamic accent uses the raw average (rawMainColor). */
export const GLOW_DARKEN = 0.88;
const EDGE_DARKEN = 0.95;
const FALLBACK_RGB = "28,25,23";

/** Inverse of the glow darkening: the artwork's main color as sampled. */
export function rawMainColor(glowRgb: string): string {
  return glowRgb
    .split(",")
    .map((n) => Math.min(255, Math.round(Number(n) / GLOW_DARKEN)))
    .join(",");
}

export interface GlowBlob {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  opacity: number;
  rgb: string;
}

export interface EdgeColorsBySide {
  left: string[];
  right: string[];
  top: string[];
  bottom: string[];
}

export interface EdgeColorsResult {
  colors: string[];
  bySide: EdgeColorsBySide;
}

export function isColorBland(rgb: string): boolean {
  const parts = rgb.split(",").map(Number);
  if (parts.length !== 3) return true;
  const [r, g, b] = parts;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const luminance = (r * 0.299 + g * 0.587 + b * 0.114) / 255;
  const saturation = max === 0 ? 0 : (max - min) / max;
  if (luminance > 0.72) return true;
  if (saturation < 0.15 && max > 50) return true;
  if (r >= g && g >= b && r > b && saturation < 0.65) return true;
  return false;
}

function colorSaturation(r: number, g: number, b: number): number {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  return max === 0 ? 0 : (max - min) / max;
}

function rgbToHue(r: number, g: number, b: number): number {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  if (max === min) return 0;
  const d = max - min;
  let h: number;
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
  else if (max === g) h = ((b - r) / d + 2) / 6;
  else h = ((r - g) / d + 4) / 6;
  return h * 360;
}

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
    const d = Math.abs(rgbToHue(ar, ag, ab) - rgbToHue(br, bg, bb));
    return d > 180 ? 360 - d : d;
  };
  if (left && right && hueDiff(left, right) >= minHueDiff) return true;
  if (top && bottom && hueDiff(top, bottom) >= minHueDiff) return true;
  return false;
}

function sampleRegion(data: Uint8ClampedArray, size: number, cx: number, cy: number): string {
  let r = 0, g = 0, b = 0, n = 0;
  for (let dy = -1; dy <= 1; dy++) {
    for (let dx = -1; dx <= 1; dx++) {
      const x = Math.round(cx) + dx;
      const y = Math.round(cy) + dy;
      if (x >= 0 && x < size && y >= 0 && y < size) {
        const idx = (y * size + x) * 4;
        r += data[idx]; g += data[idx + 1]; b += data[idx + 2]; n++;
      }
    }
  }
  if (n === 0) return "0,0,0";
  return `${Math.round((r / n) * EDGE_DARKEN)},${Math.round((g / n) * EDGE_DARKEN)},${Math.round((b / n) * EDGE_DARKEN)}`;
}

function hashString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (h << 5) - h + s.charCodeAt(i);
    h |= 0;
  }
  return h >>> 0;
}

function mulberry32(seed: number): () => number {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const NUM_BLOBS = 16;

export function getGlowBlobs(glowRgb: string, seedString: string): GlowBlob[] {
  const rgb = glowRgb || FALLBACK_RGB;
  const seed = hashString(seedString || "default");
  const rnd = mulberry32(seed);
  const blobs: GlowBlob[] = [];
  for (let i = 0; i < NUM_BLOBS; i++) {
    const isSubdued = rnd() < 0.2;
    blobs.push({
      cx: 0.05 + rnd() * 0.9,
      cy: 0.05 + rnd() * 0.9,
      rx: 0.5 + rnd() * 0.6,
      ry: 0.45 + rnd() * 0.6,
      opacity: isSubdued ? 0.08 + rnd() * 0.12 : 0.35 + rnd() * 0.5,
      rgb,
    });
  }
  return blobs;
}

interface SampleResult {
  /** Saturation-weighted average of the centre, glow-darkened ("r,g,b"). */
  glow: string;
  /** Most common quantized color of the whole image, raw ("r,g,b"). */
  mode: string;
  /** Peak hue among saturated pixels, as a vivid "r,g,b" accent. Picks out
   *  small saturated details (e.g. red text on a black/white cover) that
   *  the average and the mode both miss. */
  hue: string;
}

/** Cache of sampled colors by image URL, so grids of album cards don't
 *  re-decode the same cover per card. Bounded; oldest entries evicted. */
const sampleCache = new Map<string, SampleResult>();
const SAMPLE_CACHE_LIMIT = 500;

/** Decode the image once and derive both color estimates from the pixels. */
function sampleImage(url: string): Promise<SampleResult> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        const canvas = document.createElement("canvas");
        const size = Math.min(SAMPLE_SIZE, img.width, img.height);
        canvas.width = size; canvas.height = size;
        const ctx = canvas.getContext("2d");
        if (!ctx) { resolve({ glow: FALLBACK_RGB, mode: FALLBACK_RGB, hue: FALLBACK_RGB }); return; }
        ctx.drawImage(img, 0, 0, size, size);
        const data = ctx.getImageData(0, 0, size, size).data;
        const margin = Math.floor(size * 0.25);
        let wr = 0, wg = 0, wb = 0, wTotal = 0;
        // Quantize each pixel to 4 bits/channel and count bins to find the
        // most common color — robust for dark or low-saturation artwork
        // where the saturation-weighted average comes out bland.
        const bins = new Map<number, { r: number; g: number; b: number; n: number }>();
        // Saturation-weighted hue histogram over the saturated pixels only:
        // near-gray pixels (the usual cover background) are skipped, so a
        // small saturated detail — red text on a black/white sleeve — wins.
        // Each bin also accumulates its pixels' RGB sums, so the winning hue
        // is rendered as the bin's actual average color (matching the
        // artwork's own saturation/lightness), not a synthesized one.
        const HUE_BINS = 24; // 15° each
        const hueBins = Array.from({ length: HUE_BINS }, () => ({
          w: 0, r: 0, g: 0, b: 0, n: 0, first: -1,
        }));
        for (let y = 0; y < size; y++) {
          for (let x = 0; x < size; x++) {
            const i = (y * size + x) * 4;
            const pr = data[i], pg = data[i + 1], pb = data[i + 2];
            const key = ((pr >> 4) << 8) | ((pg >> 4) << 4) | (pb >> 4);
            const bin = bins.get(key);
            if (bin) { bin.r += pr; bin.g += pg; bin.b += pb; bin.n++; }
            else bins.set(key, { r: pr, g: pg, b: pb, n: 1 });
            const max = Math.max(pr, pg, pb), min = Math.min(pr, pg, pb);
            const sat = max === 0 ? 0 : (max - min) / max;
            const lum = (pr * 0.299 + pg * 0.587 + pb * 0.114) / 255;
            if (sat >= 0.3 && lum > 0.08 && lum < 0.92) {
              const hb = hueBins[Math.min(HUE_BINS - 1, Math.floor(rgbToHue(pr, pg, pb) / 15))];
              // Weight by saturation so the bin's average tracks the vivid
              // pixels (e.g. khaki text), not the near-threshold gray ones.
              hb.w += sat;
              hb.r += pr * sat; hb.g += pg * sat; hb.b += pb * sat; hb.n++;
              // Remember where this hue first appears in scan order, so
              // covers with two equally strong colors resolve to the
              // "first" one (top-left) instead of an arbitrary peak.
              if (hb.first < 0) hb.first = y * size + x;
            }
            if (y >= margin && y < size - margin && x >= margin && x < size - margin) {
              const w = sat * sat + 0.05;
              wr += pr * w; wg += pg * w; wb += pb * w; wTotal += w;
            }
          }
        }
        const glow =
          wTotal === 0
            ? FALLBACK_RGB
            : `${Math.round((wr / wTotal) * GLOW_DARKEN)},${Math.round((wg / wTotal) * GLOW_DARKEN)},${Math.round((wb / wTotal) * GLOW_DARKEN)}`;
        let mode = FALLBACK_RGB;
        let bestN = 0;
        for (const bin of bins.values()) {
          if (bin.n > bestN) {
            bestN = bin.n;
            mode = `${Math.round(bin.r / bin.n)},${Math.round(bin.g / bin.n)},${Math.round(bin.b / bin.n)}`;
          }
        }
        let hue = FALLBACK_RGB;
        let bestW = 0;
        for (const hb of hueBins) if (hb.w > bestW) bestW = hb.w;
        // Competing peaks (within 80% of the max) tie-break by scan order:
        // the hue that appears first in the image wins.
        let firstIdx = Infinity;
        for (const hb of hueBins) {
          if (hb.w >= 0.8 * bestW && hb.w > 0 && hb.first >= 0 && hb.first < firstIdx) {
            firstIdx = hb.first;
            hue = `${Math.round(hb.r / hb.w)},${Math.round(hb.g / hb.w)},${Math.round(hb.b / hb.w)}`;
          }
        }
        resolve({ glow, mode, hue });
      } catch {
        resolve({ glow: FALLBACK_RGB, mode: FALLBACK_RGB, hue: FALLBACK_RGB });
      }
    };
    img.onerror = () => resolve({ glow: FALLBACK_RGB, mode: FALLBACK_RGB, hue: FALLBACK_RGB });
    img.src = url;
  });
}

async function sampled(url: string | null): Promise<SampleResult> {
  if (!url) return { glow: FALLBACK_RGB, mode: FALLBACK_RGB, hue: FALLBACK_RGB };
  const cached = sampleCache.get(url);
  if (cached) return cached;
  const result = await sampleImage(url);
  if (sampleCache.size >= SAMPLE_CACHE_LIMIT) {
    const oldest = sampleCache.keys().next().value;
    if (oldest !== undefined) sampleCache.delete(oldest);
  }
  sampleCache.set(url, result);
  return result;
}

export function useDominantColor(imageUrl: Ref<string | null>) {
  const glowRgb = ref<string>(FALLBACK_RGB);

  watch(
    imageUrl,
    (url) => {
      void sampled(url).then((s) => { glowRgb.value = s.glow; });
    },
    { immediate: true },
  );

  return glowRgb;
}

/** Second color estimate: the image's most common (modal) color, raw. Used as
 *  a fallback when the saturation-weighted average is bland. */
export function useMostCommonColor(imageUrl: Ref<string | null>) {
  const modeRgb = ref<string>(FALLBACK_RGB);

  watch(
    imageUrl,
    (url) => {
      void sampled(url).then((s) => { modeRgb.value = s.mode; });
    },
    { immediate: true },
  );

  return modeRgb;
}

/** Third color estimate: the peak hue among the image's saturated pixels,
 *  rendered as a vivid accent. Catches small saturated details (colored
 *  text on a monochrome cover) that the average and the mode both miss. */
export function useProminentHueColor(imageUrl: Ref<string | null>) {
  const hueRgb = ref<string>(FALLBACK_RGB);

  watch(
    imageUrl,
    (url) => {
      void sampled(url).then((s) => { hueRgb.value = s.hue; });
    },
    { immediate: true },
  );

  return hueRgb;
}

export function useEdgeColors(imageUrl: Ref<string | null>) {
  const edgeColors = ref<EdgeColorsResult | null>(null);

  watch(
    imageUrl,
    (url) => {
      if (!url) { edgeColors.value = null; return; }
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        try {
          const canvas = document.createElement("canvas");
          const size = Math.min(SAMPLE_SIZE, img.width, img.height);
          canvas.width = size; canvas.height = size;
          const ctx = canvas.getContext("2d");
          if (!ctx) return;
          ctx.drawImage(img, 0, 0, size, size);
          const data = ctx.getImageData(0, 0, size, size).data;

          const inset = Math.max(2, Math.floor(size * 0.06));
          const numPerSide = 9;
          const bySide: EdgeColorsBySide = { left: [], right: [], top: [], bottom: [] };

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
          const vibrant = allSamples.filter((s) => s.sat >= 0.08);
          const top = vibrant.slice(0, 6);
          const count = Math.max(4, Math.min(6, top.length));

          let flatColors: string[];
          if (count === 0) {
            const n = allSamples.length;
            if (n === 0) { flatColors = []; }
            else {
              let r = 0, g = 0, b = 0;
              for (const s of allSamples) { const [sr, sg, sb] = s.rgb.split(",").map(Number); r += sr; g += sg; b += sb; }
              flatColors = [`${Math.round(r / n)},${Math.round(g / n)},${Math.round(b / n)}`];
            }
          } else {
            flatColors = top.slice(0, count).map((s) => s.rgb);
          }

          edgeColors.value = { colors: flatColors, bySide };
        } catch { edgeColors.value = null; }
      };
      img.onerror = () => { edgeColors.value = null; };
      img.src = url;
    },
    { immediate: true },
  );

  return edgeColors;
}
