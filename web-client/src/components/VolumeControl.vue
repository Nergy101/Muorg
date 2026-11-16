<template>
  <div v-if="supported || cast.isCasting" class="flex items-center gap-2">
    <button
      type="button"
      :class="iconButtonClass"
      :style="accentStyle"
      :aria-label="displayVolume <= 0 ? 'Unmute' : 'Mute'"
      :title="cast.isCasting ? 'Cast device volume' : 'Volume'"
      @click="toggleMute"
    >
      <MageIcon :name="iconName" class="h-5 w-5" />
    </button>
    <input
      type="range"
      min="0"
      max="1"
      step="0.05"
      :value="displayVolume"
      class="h-1 w-28 cursor-pointer"
      :class="variant === 'player' ? 'volume-range--player' : ''"
      :style="{ '--primary': accentCss ?? undefined }"
      :aria-label="cast.isCasting ? 'Cast device volume' : 'Volume'"
      @input="onInput"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * Volume slider + mute toggle, shared by PlayerView and the `lg` MiniPlayer.
 *
 * Drives the local player volume normally; while casting it drives the cast
 * device's volume instead and reflects whatever the device reports (the
 * server owns playback there, same pattern as the desktop app's
 * VolumeControl.vue). Hidden on iOS Safari, where `<audio>.volume` is
 * effectively read-only — the hardware buttons own playback level there, so
 * a slider would just lie about what it controls. It still shows while
 * casting even on iOS, since that volume is the cast device's, not the local
 * element's.
 */
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";
import { usePlayerStore } from "../stores/player";
import { rawMainColor, useDominantColor } from "../composables/useDominantColor";
import { useCastStore } from "../stores/cast";
import { isVolumeControlSupported } from "../audio/volumeSupport";

/** `player`: light-on-dark PlayerView chrome (hardcoded white opacities,
 *  matching its other controls). `bar`: on-surface tokens, for use on a
 *  themed app surface (the default — matches MiniPlayer's other buttons). */
const props = withDefaults(defineProps<{ variant?: "player" | "bar" }>(), {
  variant: "bar",
});

const player = usePlayerStore();
const cast = useCastStore();

// Artwork accent (same source as the player visualizer) so the slider thumb
// and mute icon color-match the playing album even when the theme accent is
// not "dynamic".
const glowRgb = useDominantColor(computed(() => player.currentCoverUrl));
const accentCss = computed(() => (glowRgb.value ? `rgb(${rawMainColor(glowRgb.value)})` : null));
const accentStyle = computed(() => (accentCss.value ? { color: accentCss.value } : undefined));

const supported = isVolumeControlSupported();

const displayVolume = computed(() => (cast.isCasting ? cast.volume : player.volume));

const iconName = computed(() => {
  const v = displayVolume.value;
  if (v <= 0) return "volume-mute";
  if (v < 0.5) return "volume-down";
  return "volume-up";
});

const iconButtonClass = computed(() =>
  props.variant === "player"
    ? "flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-white/75 transition-colors hover:bg-white/10 hover:text-white"
    : "flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface",
);

function onInput(e: Event): void {
  const raw = (e.target as HTMLInputElement).valueAsNumber;
  if (!Number.isFinite(raw)) return;
  const v = Math.min(1, Math.max(0, raw));
  if (cast.isCasting) void cast.setVolume(v);
  else player.setVolume(v);
}

function toggleMute(): void {
  if (cast.isCasting) {
    void cast.setVolume(cast.volume > 0 ? 0 : 0.5);
  } else {
    player.toggleMute();
  }
}
</script>

<style scoped>
/* PlayerView sits over album art and always renders light chrome regardless
   of the app's light/dark theme — the global input[type=range] track (in
   style.css) uses theme tokens, so it needs an explicit override here to stay
   visible on that always-dark backdrop. The `bar` variant (MiniPlayer, an
   actual themed surface) uses the global style unchanged. */
.volume-range--player {
  background: rgba(255, 255, 255, 0.25);
}
</style>
