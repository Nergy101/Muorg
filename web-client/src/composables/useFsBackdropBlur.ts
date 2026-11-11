import { computed, onBeforeUnmount, ref } from "vue";
import type { Ref } from "vue";
import { useImageSharpness } from "./useImageSharpness";
import { useSettingsStore } from "../stores/settings";

/**
 * FS-mode backdrop blur: an explicit user override (sidebar slider, persisted
 * in settings) wins; otherwise the blur is derived from the cover's detail
 * score (contrast-damped Laplacian variance on a 64x64 sample — see
 * useImageSharpness), not its megapixels. Soft/low-contrast art scores low
 * and gets the full blur; genuinely detailed art scores high and gets little.
 * Linear map from LOW_DETAIL → max blur (12px at lg, 6px below) to
 * HIGH_DETAIL → 0, clamped to a MIN_BLUR floor so even the most detailed art
 * keeps some blur and noisy/artifacted art never gets a fully sharp backdrop.
 * Calibrated against Linkin Park "The Hunting Party" (soft art, score ≈ 900 →
 * 12px), Harakiri for the Sky "Arson" (≈ 2480 → 6px) and Linkin Park
 * "From Zero" (≈ 2880 → 6px). Null when the cover's sharpness is unknown
 * (loading/CORS-tainted) — callers fall back to CSS.
 */
const MIN_BLUR = 6;
const LOW_DETAIL = 1400;
const HIGH_DETAIL = 3250;

export function useFsBackdropBlur(coverUrl: Ref<string | null>) {
  const settings = useSettingsStore();
  const sharpness = useImageSharpness(coverUrl);

  // lg breakpoint matches the CSS media queries that set the FS blur (12px at
  // lg+, 6px below) — the auto blur is capped at those amounts.
  const lgQuery = window.matchMedia("(min-width: 64rem)");
  const isLg = ref(lgQuery.matches);
  const onLgChange = (e: MediaQueryListEvent) => {
    isLg.value = e.matches;
  };
  lgQuery.addEventListener("change", onLgChange);
  onBeforeUnmount(() => lgQuery.removeEventListener("change", onLgChange));

  const blurPx = computed(() => {
    if (settings.fsBackdropBlur !== null) return settings.fsBackdropBlur;
    const s = sharpness.value;
    if (s === null) return null;
    const maxBlur = isLg.value ? 12 : 6;
    const t = Math.min(1, Math.max(0, (HIGH_DETAIL - s) / (HIGH_DETAIL - LOW_DETAIL)));
    return Math.max(MIN_BLUR, Math.round(maxBlur * t));
  });

  return { blurPx };
}
