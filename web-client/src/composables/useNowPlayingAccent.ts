import { computed, type ComputedRef } from "vue";
import { rawMainColor, useDominantColor } from "./useDominantColor";
import { parseRgb } from "./dynamicAccent";
import { usePlayerStore } from "../stores/player";

/**
 * Accent color of the currently playing track's album art as a CSS color.
 * The art's average hue is kept as sampled — even a gray or white cover
 * yields its own (light) tint — with only the lightness clamped into a
 * visible range so the bars never vanish against the dark row overlay.
 * Returns null only when there is no cover color at all; callers then fall
 * back to the theme primary. Shared by every playing indicator
 * (EqualizerBars) so they all follow the playing track's album accent.
 */
export function useNowPlayingAccent(): ComputedRef<string | null> {
  const player = usePlayerStore();
  const glowRgb = useDominantColor(computed(() => player.currentCoverUrl));
  return computed(() => {
    const parsed = parseRgb(rawMainColor(glowRgb.value));
    if (!parsed) return null;
    const [r, g, b] = parsed;
    const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    if (lum <= 0) return "rgb(77,77,77)";
    // Clamp perceived lightness into [0.3, 0.75], preserving the hue.
    const target = Math.min(0.75, Math.max(0.3, lum));
    const f = target / lum;
    const c = (v: number): number => Math.min(255, Math.round(v * f));
    return `rgb(${c(r)},${c(g)},${c(b)})`;
  });
}
