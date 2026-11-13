<template>
  <!-- Format tag of the playing file or live stream (FLAC, MP3, …). Hovering
       shows the bitrate / sample-rate stats via the app-wide data-tip
       popover. Colour and visibility classes come from the caller. -->
  <button
    v-if="formatTag"
    type="button"
    class="flex items-center gap-1 rounded-full px-2 text-label-sm font-semibold tracking-wide tabular-nums transition-colors"
    :data-tip="statsLabel || formatTag"
    :aria-label="`Audio format ${formatTag}${statsLabel ? `, ${statsLabel}` : ''}`"
  >
    <MageIcon name="sound-waves" class="h-3.5 w-3.5 shrink-0" />
    <span>{{ formatTag }}</span>
  </button>
</template>

<script setup lang="ts">
import MageIcon from "./MageIcon.vue";
import { useAudioStats } from "../composables/useAudioStats";

const { formatTag, statsLabel } = useAudioStats();
</script>
