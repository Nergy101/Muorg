<script setup lang="ts">
/** Playback tab: autoplay, continuous playback, ReplayGain, player-bar behaviour. */
import { storeToRefs } from "pinia";
import { useSettingsStore } from "../../../stores/settings";
import type { ReplayGainMode } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const settingsStore = useSettingsStore();
const {
  autoplayOnSelect,
  continuousPlayback,
  playbarShowAlbumInMarquee,
  playbarDisableMarquee,
  playbarShowRatingInMaximized,
  replayGainMode,
  replayGainPreampDb,
  replayGainPreventClipping,
} = storeToRefs(settingsStore);

const replayGainOptions: { value: ReplayGainMode; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "track", label: "Track" },
  { value: "album", label: "Album" },
];


</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="play-circle"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Playback
              </p>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="play"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Behavior
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="autoplayOnSelect"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setAutoplayOnSelect(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Autoplay on track selection
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, selecting a track immediately starts playback.
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="continuousPlayback"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setContinuousPlayback(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Continuous playback
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, playback continues to the next track
                  automatically.
                </p>
              </div>
              <div class="settings-section space-y-2">
                <p class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400">
                  <FeatherIcon name="activity" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
                  ReplayGain
                </p>
                <p class="text-xs text-stone-500">
                  ReplayGain is loudness metadata stored in audio tags. Muorg reads these values and adjusts playback volume so tracks and albums play at a more consistent perceived level.
                </p>
                <div class="flex flex-wrap gap-1">
                  <button
                    v-for="opt in replayGainOptions"
                    :key="opt.value"
                    type="button"
                    class="rounded border px-2 py-1 text-xs"
                    :class="replayGainMode === opt.value ? 'border-stone-400 bg-stone-700 text-stone-100' : 'border-stone-600 text-stone-400 hover:bg-stone-700'"
                    @click="settingsStore.setReplayGainMode(opt.value)"
                  >
                    {{ opt.label }}
                  </button>
                </div>
                <p class="text-xs text-stone-500">
                  Off: ignore ReplayGain tags. Track: use each track's gain value (best for mixed playlists). Album: use album gain values to preserve loudness differences within an album.
                </p>
                <label class="flex items-center gap-2 text-xs text-stone-500">
                  Preamp (dB)
                  <input
                    type="number"
                    step="0.5"
                    min="-12"
                    max="12"
                    class="w-20 rounded border border-stone-600 bg-stone-800 px-2 py-0.5 text-stone-200"
                    :value="replayGainPreampDb"
                    @input="settingsStore.setReplayGainPreampDb(Number(($event.target as HTMLInputElement).value))"
                  />
                </label>
                <p class="text-xs text-stone-500">
                  Preamp is applied on top of ReplayGain. Positive values make playback louder; negative values add headroom.
                </p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="replayGainPreventClipping"
                    class="rounded border-stone-600"
                    @change="settingsStore.setReplayGainPreventClipping(($event.target as HTMLInputElement).checked)"
                  />
                  Prevent clipping
                </label>
                <p class="text-xs text-stone-500">
                  ReplayGain is applied only during playback and does not edit audio files. Clipping prevention caps output gain to avoid distortion when boosted levels would exceed safe output.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="music"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Playbar
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarShowAlbumInMarquee"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarShowAlbumInMarquee(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Show album in scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the album name is shown next to the track title
                  in the scrolling marquee.
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarDisableMarquee"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarDisableMarquee(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Disable scrolling title
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the track title is truncated instead of
                  scrolling.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="star"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Ratings
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="playbarShowRatingInMaximized"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setPlaybarShowRatingInMaximized(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Show rating on maximized player
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, a star rating control is shown above the
                  playback controls in the maximized player.
                </p>
              </div>
            </div>

</template>
