import { ref, watch } from "vue";
import type { Ref } from "vue";

const SAMPLE_SIZE = 32;
/** Multiply RGB by this so the glow isn't pure white on bright art (0–1). Slightly higher = more saturated/vibrant. */
const DARKEN = 0.88;

const FALLBACK_RGB = "28,25,23";

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

/**
 * Blobs for a small square demo (e.g. settings preview). Larger blobs, more like the real fullscreen glow.
 */
export function getGlowBlobsForDemo(glowRgb: string, _seedString: string): GlowBlob[] {
  const rgb = glowRgb || FALLBACK_RGB_Gradient;

  // Manual layout: larger blobs like the real player, dark sides still visible
  return [
    { cx: 0.5, cy: 0.5, rx: 0.85, ry: 0.8, opacity: 0.38, rgb },
    { cx: 0.25, cy: 0.35, rx: 0.7, ry: 0.65, opacity: 0.22, rgb },
    { cx: 0.75, cy: 0.4, rx: 0.65, ry: 0.7, opacity: 0.28, rgb },
    { cx: 0.4, cy: 0.7, rx: 0.6, ry: 0.55, opacity: 0.18, rgb },
    { cx: 0.65, cy: 0.65, rx: 0.55, ry: 0.6, opacity: 0.25, rgb },
    { cx: 0.2, cy: 0.6, rx: 0.5, ry: 0.5, opacity: 0.2, rgb },
    { cx: 0.8, cy: 0.25, rx: 0.55, ry: 0.5, opacity: 0.22, rgb },
    { cx: 0.35, cy: 0.2, rx: 0.45, ry: 0.45, opacity: 0.15, rgb },
    { cx: 0.7, cy: 0.75, rx: 0.5, ry: 0.55, opacity: 0.2, rgb },
    { cx: 0.82, cy: 0.78, rx: 0.6, ry: 0.55, opacity: 0.28, rgb },
    { cx: 0.9, cy: 0.65, rx: 0.5, ry: 0.5, opacity: 0.22, rgb },
    { cx: 0.72, cy: 0.88, rx: 0.45, ry: 0.4, opacity: 0.2, rgb },
    { cx: 0.88, cy: 0.5, rx: 0.5, ry: 0.55, opacity: 0.18, rgb },
    { cx: 0.6, cy: 0.85, rx: 0.5, ry: 0.45, opacity: 0.2, rgb },
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
