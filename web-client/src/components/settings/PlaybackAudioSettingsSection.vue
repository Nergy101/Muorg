<template>
  <div>
    <div class="px-4 py-2">
      <p class="pb-1 text-body-lg text-on-surface">ReplayGain</p>
      <p class="pb-2 text-body-sm text-on-surface-variant">
        Reads the loudness tags already embedded in your files and adjusts
        playback volume so tracks and albums sound evenly loud — it never
        edits the files themselves.
      </p>
      <SegmentedControl
        :model-value="settings.replayGainMode"
        :options="MODE_OPTIONS"
        @update:model-value="settings.setReplayGainMode($event as ReplayGainMode)"
      />
      <p class="pt-2 text-body-sm text-on-surface-variant">{{ modeDescription }}</p>
    </div>

    <div class="px-4 py-2">
      <div class="flex items-center justify-between pb-2">
        <p class="text-body-lg text-on-surface">Preamp</p>
        <span class="text-body-sm tabular-nums text-on-surface-variant">
          {{ formatDb(settings.replayGainPreampDb) }}
        </span>
      </div>
      <input
        type="range"
        min="-24"
        max="24"
        step="0.5"
        :value="settings.replayGainPreampDb"
        aria-label="ReplayGain preamp"
        class="w-full"
        @input="settings.setReplayGainPreampDb(($event.target as HTMLInputElement).valueAsNumber)"
      />
      <p class="pt-2 text-body-sm text-on-surface-variant">
        Applied on top of the ReplayGain tag. Positive values boost playback;
        negative values add headroom.
      </p>
    </div>

    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Prevent clipping</p>
        <p class="text-body-sm text-on-surface-variant">
          Caps the boost against the track's peak tag so loud passages don't
          distort, instead of just clamping the whole mix at full volume.
        </p>
      </div>
      <button
        type="button"
        :class="switchClass(settings.replayGainPreventClipping)"
        role="switch"
        aria-label="Prevent clipping"
        :data-tip="`Prevent clipping is ${settings.replayGainPreventClipping ? 'on' : 'off'} — click to turn ${settings.replayGainPreventClipping ? 'off' : 'on'}`"
        :aria-checked="settings.replayGainPreventClipping"
        @click="settings.setReplayGainPreventClipping(!settings.replayGainPreventClipping)"
      >
        <span :class="knobClass(settings.replayGainPreventClipping)" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ReplayGain settings block, mounted inside SettingsView's existing Playback
 * section: mode (off/track/album), preamp and clipping prevention. Mirrors
 * the desktop app's Playback settings panel; the math lives in
 * audio/replayGain.ts and is applied by the player store via the <audio>
 * element's volume — see that module's comments for why boosts above unity
 * need Web Audio instead.
 */
import { computed } from "vue";
import SegmentedControl from "../SegmentedControl.vue";
import { useSettingsStore } from "../../stores/settings";
import type { ReplayGainMode } from "../../audio/replayGain";

const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const MODE_OPTIONS: { value: ReplayGainMode; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "track", label: "Track" },
  { value: "album", label: "Album" },
];

const settings = useSettingsStore();

const modeDescription = computed(() => {
  if (settings.replayGainMode === "track") {
    return "Each track plays at its own tagged loudness — best for mixed playlists and shuffle.";
  }
  if (settings.replayGainMode === "album") {
    return "Album gain tags preserve the loudness differences between tracks on the same album.";
  }
  return "ReplayGain tags are ignored; tracks play at their raw volume.";
});

function formatDb(db: number): string {
  return `${db > 0 ? "+" : ""}${db.toFixed(1)} dB`;
}

function switchClass(on: boolean): string {
  return `flex h-8 w-[52px] shrink-0 items-center rounded-full px-1 transition-colors duration-200 ${
    on ? "bg-primary" : "bg-surface-variant"
  }`;
}

function knobClass(on: boolean): string {
  return `block h-6 w-6 rounded-full bg-white transition-transform duration-200 ${
    on ? "translate-x-5" : "translate-x-0"
  }`;
}
</script>
