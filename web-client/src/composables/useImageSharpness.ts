import { ref, watch } from "vue";
import type { Ref } from "vue";

const SAMPLE = 64;

/**
 * Detail score of the image: variance of the Laplacian on a 64x64 grayscale
 * sample, damped by the image's contrast. Raw Laplacian variance alone lets
 * soft, low-contrast art (smoke, gradients, blurred photos) score high on
 * fine texture while looking mushy, so the score is multiplied by
 * min(1, lapvar/grayvar / 1.5) — low-contrast art is penalized toward the
 * "needs full blur" end. Resolution-independent: an upscaled/pixelated
 * 1200x1200 scores like the small original it came from, while a genuinely
 * detailed 600x600 scores high. Null while loading, on error, or when the
 * canvas is tainted (CORS).
 */
export function useImageSharpness(imageUrl: Ref<string | null>) {
  const sharpness = ref<number | null>(null);

  watch(
    imageUrl,
    (url) => {
      if (!url) {
        sharpness.value = null;
        return;
      }
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        try {
          const c = document.createElement("canvas");
          c.width = SAMPLE;
          c.height = SAMPLE;
          const ctx = c.getContext("2d");
          if (!ctx) {
            sharpness.value = null;
            return;
          }
          ctx.drawImage(img, 0, 0, SAMPLE, SAMPLE);
          const d = ctx.getImageData(0, 0, SAMPLE, SAMPLE).data;
          const g = new Float64Array(SAMPLE * SAMPLE);
          for (let i = 0; i < g.length; i++) {
            g[i] = d[i * 4] * 0.299 + d[i * 4 + 1] * 0.587 + d[i * 4 + 2] * 0.114;
          }
          let gMean = 0;
          for (let i = 0; i < g.length; i++) gMean += g[i];
          gMean /= g.length;
          let gVar = 0;
          for (let i = 0; i < g.length; i++) gVar += (g[i] - gMean) * (g[i] - gMean);
          gVar /= g.length;
          let sum = 0;
          let sum2 = 0;
          let n = 0;
          for (let y = 1; y < SAMPLE - 1; y++) {
            for (let x = 1; x < SAMPLE - 1; x++) {
              const i = y * SAMPLE + x;
              const lap = g[i - 1] + g[i + 1] + g[i - SAMPLE] + g[i + SAMPLE] - 4 * g[i];
              sum += lap;
              sum2 += lap * lap;
              n++;
            }
          }
          const mean = sum / n;
          const lapVar = sum2 / n - mean * mean;
          const contrastRatio = lapVar / (gVar + 1e-6);
          sharpness.value = lapVar * Math.min(1, contrastRatio / 1.5);
        } catch {
          sharpness.value = null;
        }
      };
      img.onerror = () => {
        sharpness.value = null;
      };
      img.src = url;
    },
    { immediate: true },
  );

  return sharpness;
}
