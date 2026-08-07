<template>
  <div
    class="flex h-14 w-full select-none items-center gap-3 px-4 text-left"
    @click="emit('play')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <!-- Leading: equalizer while playing, else cover / index / placeholder -->
    <div class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded">
      <EqualizerBars v-if="isPlaying" class="text-primary" :paused="!player.isPlaying" />
      <img
        v-else-if="coverUrl"
        :src="coverUrl"
        :alt="track.album ?? ''"
        class="h-12 w-12 rounded object-cover"
        decoding="async"
      />
      <span
        v-else-if="leading === 'index'"
        class="text-body-md tabular-nums text-on-surface-variant"
      >{{ track.track_number ?? "·" }}</span>
      <MageIcon v-else name="music" class="h-5 w-5 text-on-surface-variant/60" />
    </div>

    <!-- Title + artist (format tag and duration sit on the artist's line so
         the title row keeps the full width) -->
    <div class="min-w-0 flex-1">
      <MarqueeText
        :text="track.title ?? '—'"
        :class="isPlaying ? 'text-body-lg text-primary' : 'text-body-lg text-on-surface'"
      />
      <div class="flex min-w-0 items-center gap-1.5">
        <div class="min-w-0 flex-1">
          <MarqueeText
            :text="track.artist ?? track.album_artist ?? '—'"
            class="text-body-md text-on-surface-variant"
          />
        </div>
        <span
          v-if="showFormatBadge !== false"
          class="ml-auto shrink-0 rounded-[3px] px-1 py-[1px] text-[10px] leading-none uppercase"
          :class="track.format === 'flac'
            ? 'bg-primary/[0.12] text-primary'
            : 'bg-on-surface-variant/10 text-on-surface-variant'"
        >{{ track.format.toUpperCase() }}</span>
        <span
          v-if="showDuration !== false"
          class="shrink-0 text-[10px] leading-none tabular-nums text-on-surface-variant"
        >{{ duration }}</span>
      </div>
    </div>

    <button
      type="button"
      class="-mr-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
      aria-label="Track actions"
      @click.stop="emit('actions')"
    >
      <MageIcon name="dots" class="h-5 w-5" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";
import EqualizerBars from "./EqualizerBars.vue";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useLongPress } from "../composables/useLongPress";
import type { CatalogTrack } from "../types";

const props = withDefaults(
  defineProps<{
    track: CatalogTrack;
    leading?: "cover" | "index";
    isPlaying?: boolean;
    showFormatBadge?: boolean;
    showDuration?: boolean;
  }>(),
  { leading: "cover", showFormatBadge: true, showDuration: true },
);

const emit = defineEmits<{ play: []; actions: [] }>();

const lib = useLibraryStore();
// `isPlaying` means "this row is the current track"; the store says whether
// audio is actually running, which is what freezes the bars.
const player = usePlayerStore();
const lp = useLongPress(() => emit("actions"));

const coverUrl = computed(() => {
  if (props.leading !== "cover" || !props.track.has_cover) return null;
  lib.requestCover(props.track.id);
  return lib.coverCache.get(props.track.id) ?? null;
});

const duration = computed(() =>
  props.track.duration_secs ? formatDuration(props.track.duration_secs) : "",
);
</script>
