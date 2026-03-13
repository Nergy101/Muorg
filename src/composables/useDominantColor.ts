import { ref, watch } from "vue";
import type { Ref } from "vue";

const SAMPLE_SIZE = 32;
/** Multiply RGB by this so the glow isn't pure white on bright art (0–1). */
const DARKEN = 0.75;

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

const NUM_BLOBS = 8;
const FALLBACK_RGB_Gradient = "28,25,23";

/**
 * Build a procedural glow background: dark base + several gradient blobs
 * at positions/sizes derived from the seed (e.g. track path). Different seed => different layout.
 * More blobs at random deterministic positions fill the screen with color.
 */
export function buildProceduralGlow(glowRgb: string, seedString: string): string {
  const rgb = glowRgb || FALLBACK_RGB_Gradient;
  const seed = hashString(seedString || "default");
  const rnd = mulberry32(seed);

  const layers: string[] = [];

  for (let i = 0; i < NUM_BLOBS; i++) {
    const cx = 0.1 + rnd() * 0.8;
    const cy = 0.1 + rnd() * 0.8;
    const rx = 0.35 + rnd() * 0.45;
    const ry = 0.3 + rnd() * 0.4;
    const opacity = 0.12 + rnd() * 0.35;
    layers.push(
      `radial-gradient(ellipse ${rx * 100}% ${ry * 100}% at ${cx * 100}% ${cy * 100}%, rgba(${rgb},${opacity.toFixed(2)}) 0%, transparent 70%)`,
    );
  }

  return `${layers.join(", ")}, linear-gradient(black, black)`;
}
